package com.example.foliareward.manager;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.config.ConfigManager;
import com.example.foliareward.model.PlayerData;
import com.example.foliareward.model.TaskConfig;
import com.example.foliareward.model.TaskType;
import com.example.foliareward.util.FoliaScheduler;
import com.example.foliareward.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务系统管理器。
 *
 * 【Folia关键】在线时长计时器由主插件类通过 GlobalRegionScheduler 每秒调用 tickOnlineTime()。
 * 所有数据操作均为线程安全（ConcurrentHashMap + volatile PlayerData字段）。
 */
public class TaskManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FoliaRewardPlugin plugin;

    /**
     * 在线时长累计（秒），key=UUID。
     * 【Folia关键】此 Map 可能被 GlobalRegionScheduler 线程（tickOnlineTime）
     * 和 EntityScheduler 线程（玩家操作事件）并发访问，必须用 ConcurrentHashMap。
     */
    private final Map<UUID, Integer> onlineSeconds = new ConcurrentHashMap<>();

    /** 存储任务配置（从 ConfigManager 加载） */
    private final Map<String, TaskConfig> taskConfigs = new LinkedHashMap<>();

    public TaskManager(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
        reloadTasks();
    }

    /** 重载任务配置 */
    public void reloadTasks() {
        taskConfigs.clear();
        taskConfigs.putAll(plugin.getConfigManager().getTasks());
    }

    // ----------------------------------------------------------------
    // 在线时长计时（每秒由 GlobalRegionScheduler 调用）
    // ----------------------------------------------------------------

    /**
     * 每秒 tick 一次，递增在线玩家的在线时长，检查任务进度。
     * 【Folia关键】由 GlobalRegionScheduler 在全局主线程调用，
     * 访问 onlineSeconds（ConcurrentHashMap）是安全的。
     */
    public void tickOnlineTime() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            int seconds = onlineSeconds.merge(uuid, 1, Integer::sum);

            // 每 60 秒检查一次在线时长任务（避免过于频繁）
            if (seconds % 60 == 0) {
                int minutes = seconds / 60;
                checkOnlineTimeTasks(player, minutes);
            }
        }
    }

    private void checkOnlineTimeTasks(Player player, int totalMinutes) {
        PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
        if (data == null) return;

        for (TaskConfig task : taskConfigs.values()) {
            if (task.getType() != TaskType.ONLINE_TIME) continue;
            if (isTaskBlocked(data, task)) continue;

            // 在线时长任务：直接用分钟数作为进度
            int currentProgress = Math.min(totalMinutes, task.getAmount());
            int stored = data.getProgress(task.getId());
            if (currentProgress > stored) {
                data.setProgress(task.getId(), currentProgress);
                if (currentProgress >= task.getAmount()) {
                    onTaskComplete(player, data, task);
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // 方块挖掘事件
    // ----------------------------------------------------------------

    /**
     * 玩家挖掘方块时调用。
     * 【Folia关键】BlockBreakEvent 在方块所在 Region 的线程上触发，
     * 但玩家数据存在 ConcurrentHashMap 中，访问是线程安全的。
     */
    public void onBlockBreak(Player player, Material material) {
        PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
        if (data == null) return;

        String matName = material.name();
        for (TaskConfig task : taskConfigs.values()) {
            if (task.getType() != TaskType.BLOCK_BREAK) continue;
            if (!task.getTarget().equals(matName)) continue;
            if (isTaskBlocked(data, task)) continue;

            data.incrementProgress(task.getId(), 1);
            int progress = data.getProgress(task.getId());

            if (progress >= task.getAmount()) {
                onTaskComplete(player, data, task);
            }
        }
    }

    // ----------------------------------------------------------------
    // 实体击杀事件
    // ----------------------------------------------------------------

    /**
     * 玩家击杀实体时调用。
     * 【Folia关键】EntityDeathEvent 在实体所在 Region 的线程上触发，线程安全同上。
     */
    public void onEntityKill(Player player, EntityType entityType) {
        PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
        if (data == null) return;

        String typeName = entityType.name();
        for (TaskConfig task : taskConfigs.values()) {
            if (task.getType() != TaskType.ENTITY_KILL) continue;
            if (!task.getTarget().equals(typeName)) continue;
            if (isTaskBlocked(data, task)) continue;

            data.incrementProgress(task.getId(), 1);
            int progress = data.getProgress(task.getId());

            if (progress >= task.getAmount()) {
                onTaskComplete(player, data, task);
            }
        }
    }

    // ----------------------------------------------------------------
    // 任务完成处理
    // ----------------------------------------------------------------

    private void onTaskComplete(Player player, PlayerData data, TaskConfig task) {
        data.addPendingClaim(task.getId());

        // 通知玩家（需要在玩家的 Region 线程）
        ConfigManager cfg = plugin.getConfigManager();
        FoliaScheduler.runEntity(plugin, player, () ->
            player.sendMessage(MessageUtil.format(
                cfg.getPrefix() + cfg.getMessage("task-completed"),
                "{task}", task.getDisplayName())),
            null);
    }

    /**
     * 玩家领取已完成任务的奖励（/reward task claim）
     */
    public void claimTaskRewards(Player player) {
        FoliaScheduler.runAsync(plugin, () -> {
            PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
            if (data == null) return;

            Set<String> pending = new HashSet<>(data.getPendingClaim());
            if (pending.isEmpty()) {
                plugin.getRewardManager().sendMessage(player, "task-no-completed");
                return;
            }

            for (String taskId : pending) {
                TaskConfig task = taskConfigs.get(taskId);
                if (task == null) {
                    data.removePendingClaim(taskId);
                    continue;
                }
                // 发放奖励（切换到玩家线程）
                plugin.getRewardManager().giveReward(player, task.getRewards(), player.getName());
                plugin.getRewardManager().sendMessage(player, "task-claimed",
                        "{task}", task.getDisplayName());

                data.removePendingClaim(taskId);

                if (!task.isResetDaily()) {
                    // 一次性任务：永久标记已完成
                    data.markCompleted(taskId);
                } else {
                    // 每日任务：标记今日已领取
                    data.markDailyClaimedToday(taskId);
                }
            }
            plugin.getDatabaseManager().saveAsync(player.getUniqueId());
        });
    }

    // ----------------------------------------------------------------
    // 玩家加入/离开
    // ----------------------------------------------------------------

    /** 玩家加入时初始化在线计时，并重置每日任务进度（如果需要） */
    public void onPlayerJoin(Player player) {
        onlineSeconds.put(player.getUniqueId(), 0);

        FoliaScheduler.runAsync(plugin, () -> {
            PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
            if (data == null) return;
            resetDailyTasksIfNeeded(data);
        });
    }

    /** 玩家离开时清除在线计时 */
    public void onPlayerQuit(Player player) {
        onlineSeconds.remove(player.getUniqueId());
    }

    /** 每日重置需要重置的任务进度 */
    private void resetDailyTasksIfNeeded(PlayerData data) {
        String today = LocalDate.now().format(DATE_FMT);
        // 利用 lastDailyJoinDate 来判断是否是新的一天
        if (today.equals(data.getLastDailyJoinDate())) return;

        for (TaskConfig task : taskConfigs.values()) {
            if (!task.isResetDaily()) continue;
            data.setProgress(task.getId(), 0);
            data.removePendingClaim(task.getId());
        }
        data.clearDailyClaimedToday();
        // 注意：lastDailyJoinDate 由 PlayerJoinListener.handleJoinRewards() 负责更新
    }

    /** 保存所有玩家数据（关服时调用） */
    public void saveAllData() {
        plugin.getDatabaseManager().saveAll();
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    /**
     * 判断玩家是否因为"已完成一次性任务"或"今日已领取每日任务"而无需再计数
     */
    private boolean isTaskBlocked(PlayerData data, TaskConfig task) {
        if (!task.isResetDaily() && data.isCompleted(task.getId())) return true;
        if (task.isResetDaily() && data.isDailyClaimedToday(task.getId())) return true;
        if (data.getPendingClaim().contains(task.getId())) return true; // 已完成待领取
        return false;
    }

    public Map<String, TaskConfig> getTaskConfigs() { return taskConfigs; }

    public int getOnlineSeconds(UUID uuid) {
        return onlineSeconds.getOrDefault(uuid, 0);
    }
}
