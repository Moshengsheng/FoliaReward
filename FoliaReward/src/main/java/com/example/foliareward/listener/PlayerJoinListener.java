package com.example.foliareward.listener;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.model.PlayerData;
import com.example.foliareward.model.RewardConfig;
import com.example.foliareward.util.FoliaScheduler;
import com.example.foliareward.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 玩家进服监听器。
 *
 * 【Folia关键】PlayerJoinEvent 在玩家所在 Region 的线程上触发，
 * 可以直接操作该玩家，但跨 Region 操作需要用对应的调度器。
 *
 * 在此事件中执行数据库操作（异步加载）时，
 * 必须用 FoliaScheduler.runAsync() 切换到异步线程，
 * 数据加载完成后再通过 FoliaScheduler.runEntity() 切回玩家线程发放奖励。
 */
public class PlayerJoinListener implements Listener {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FoliaRewardPlugin plugin;

    public PlayerJoinListener(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 初始化在线时长计时
        plugin.getTaskManager().onPlayerJoin(player);

        // 【Folia关键】数据库操作必须异步执行
        FoliaScheduler.runAsync(plugin, () -> {
            plugin.getDatabaseManager()
                .loadAndCache(player.getUniqueId())
                .thenAccept(data -> handleJoinRewards(player, data));
        });
    }

    private void handleJoinRewards(Player player, PlayerData data) {
        String playerName = player.getName();
        String today = LocalDate.now().format(DATE_FMT);

        // 1. 首次进服奖励
        if (!data.isFirstJoinDone()) {
            data.setFirstJoinDone(true);
            RewardConfig reward = plugin.getConfigManager().getFirstJoinReward();

            // 【Folia关键】给玩家物品必须切换到玩家的 Region 线程
            FoliaScheduler.runEntity(plugin, player, () -> {
                plugin.getRewardManager().giveReward(player, reward, playerName);
                player.sendMessage(MessageUtil.color(
                        plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("first-join")));
            }, null);
        }

        // 2. 每日进服提示/奖励
        boolean alreadyJoinedToday = today.equals(data.getLastDailyJoinDate());
        if (!alreadyJoinedToday) {
            data.setLastDailyJoinDate(today);

            if (plugin.getConfigManager().isDailyJoinAutoGive()) {
                // 自动发放每日进服奖励
                RewardConfig reward = plugin.getConfigManager().getDailyJoinReward();
                plugin.getRewardManager().giveReward(player, reward, playerName);
            } else {
                // 仅提示，等玩家手动 /daily
                boolean alreadyDailyClaimed = plugin.getDailyRewardManager().hasClaimedToday(data);
                if (!alreadyDailyClaimed) {
                    FoliaScheduler.runEntityDelayed(plugin, player, () ->
                        player.sendMessage(MessageUtil.color(
                                plugin.getConfigManager().getPrefix() +
                                plugin.getConfigManager().getMessage("daily-join-notify"))),
                        null, 40L); // 延迟 2 秒发送，避免与欢迎信息重叠
                }
            }
        }

        // 3. 保存数据（如果有变化）
        if (data.isDirty()) {
            plugin.getDatabaseManager().saveAsync(player.getUniqueId());
        }
    }
}
