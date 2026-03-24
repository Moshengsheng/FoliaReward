package com.example.foliareward.manager;
import java.util.Map;
import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.config.ConfigManager;
import com.example.foliareward.model.PlayerData;
import com.example.foliareward.model.RewardConfig;
import com.example.foliareward.util.FoliaScheduler;
import com.example.foliareward.util.MessageUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 每日签到管理器。
 *
 * 签到逻辑：
 *   1. 检查今日是否已签到（last_daily_date == today）
 *   2. 检查是否连续签到（yesterday == last_daily_date → streak+1，否则 streak=1）
 *   3. 根据权限组选择签到奖励
 *   4. 检查是否触发连续签到里程碑奖励
 *   5. 更新数据库
 */
public class DailyRewardManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FoliaRewardPlugin plugin;

    public DailyRewardManager(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家执行 /daily 命令时调用。
     * 此方法可从任意线程调用，内部异步处理数据后切换回玩家线程发放奖励。
     */
    public void claimDaily(Player player) {
        String playerName = player.getName();

        // 【Folia关键】先异步查询数据，然后切换到玩家的 Region 线程发放奖励
        FoliaScheduler.runAsync(plugin, () -> {
            plugin.getDatabaseManager()
                .getPlayerData(player.getUniqueId())
                .thenAccept(data -> processDaily(player, playerName, data));
        });
    }

    private void processDaily(Player player, String playerName, PlayerData data) {
        String today     = LocalDate.now().format(DATE_FMT);
        String yesterday = LocalDate.now().minusDays(1).format(DATE_FMT);

        // 已签到
        if (today.equals(data.getLastDailyClaimDate())) {
            plugin.getRewardManager().sendMessage(player, "daily-already-claimed");
            return;
        }

        // 计算连续签到天数
        int streak;
        if (yesterday.equals(data.getLastDailyClaimDate())) {
            streak = data.getDailyStreak() + 1;
        } else {
            streak = 1; // 断签，从头计
        }

        data.setLastDailyClaimDate(today);
        data.setDailyStreak(streak);

        // 确定签到奖励（按权限组）
        String group = getPrimaryGroup(player);
        ConfigManager cfg = plugin.getConfigManager();
        Map<String, RewardConfig> groupRewards = cfg.getDailyGroupRewards();
        RewardConfig baseReward = groupRewards.getOrDefault(group,
                groupRewards.getOrDefault("default", null));

        // 检查连续签到里程碑（精确匹配当天，如第7、14、30天）
        RewardConfig bonusReward = cfg.getStreakBonuses().get(streak);

        final RewardConfig finalBase  = baseReward;
        final RewardConfig finalBonus = bonusReward;
        final int finalStreak = streak;

        // 【Folia关键】切换到玩家的 Region 线程发放奖励
        FoliaScheduler.runEntity(plugin, player, () -> {
            if (finalBase != null) {
                plugin.getRewardManager().giveReward(player, finalBase, playerName);
            }
            player.sendMessage(MessageUtil.format(
                    cfg.getPrefix() + cfg.getMessage("daily-success"),
                    "{streak}", String.valueOf(finalStreak)));

            if (finalBonus != null) {
                plugin.getRewardManager().giveReward(player, finalBonus, playerName);
                player.sendMessage(MessageUtil.color(
                        cfg.getPrefix() + cfg.getMessage("daily-streak-bonus")));
            }
        }, null);

        // 异步保存
        plugin.getDatabaseManager().saveAsync(player.getUniqueId());
    }

    /**
     * 检查玩家今日是否已签到（用于进服提示）
     */
    public boolean hasClaimedToday(PlayerData data) {
        String today = LocalDate.now().format(DATE_FMT);
        return today.equals(data.getLastDailyClaimDate());
    }

    /**
     * 管理员重置玩家签到记录
     */
    public void resetDailyForPlayer(Player target) {
        FoliaScheduler.runAsync(plugin, () -> {
            plugin.getDatabaseManager()
                .getPlayerData(target.getUniqueId())
                .thenAccept(data -> {
                    data.setLastDailyClaimDate("");
                    data.setDailyStreak(0);
                    plugin.getDatabaseManager().saveAsync(target.getUniqueId());
                });
        });
    }

    /** 通过 LuckPerms 获取玩家主权限组，未安装则返回 "default" */
    private String getPrimaryGroup(Player player) {
        LuckPerms lp = plugin.getLuckPerms();
        if (lp == null) return "default";
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        return user.getPrimaryGroup().toLowerCase();
    }
}
