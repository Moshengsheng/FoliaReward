package com.example.foliareward.manager;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.model.PlayerData;
import com.example.foliareward.model.RewardConfig;
import com.example.foliareward.util.FoliaScheduler;
import com.example.foliareward.util.MessageUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 权限组专属奖励管理器。
 * 玩家通过 /reward group 手动领取，受冷却时间限制。
 */
public class GroupRewardManager {

    private final FoliaRewardPlugin plugin;

    public GroupRewardManager(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家尝试领取权限组奖励。
     * 可从任意线程调用。
     */
    public void claimGroupReward(Player player) {
        String playerName = player.getName();

        FoliaScheduler.runAsync(plugin, () -> {
            plugin.getDatabaseManager()
                .getPlayerData(player.getUniqueId())
                .thenAccept(data -> processGroupReward(player, playerName, data));
        });
    }

    private void processGroupReward(Player player, String playerName, PlayerData data) {
        String group = getPrimaryGroup(player);
        Map<String, RewardConfig> groupRewards = plugin.getConfigManager().getGroupRewards();
        RewardConfig reward = groupRewards.getOrDefault(group,
                groupRewards.get("default"));

        if (reward == null || reward.isEmpty()) {
            plugin.getRewardManager().sendRaw(player,
                    plugin.getConfigManager().getPrefix() + "&c您的权限组没有对应奖励配置。");
            return;
        }

        long cooldownSec = plugin.getConfigManager().getGroupRewardCooldown();
        long lastClaim   = data.getGroupRewardLastClaim().getOrDefault(group, 0L);
        long now         = System.currentTimeMillis();
        long elapsed     = (now - lastClaim) / 1000L;

        if (elapsed < cooldownSec) {
            long remaining = cooldownSec - elapsed;
            plugin.getRewardManager().sendMessage(player, "group-reward-cooldown",
                    "{time}", String.valueOf(remaining));
            return;
        }

        data.setGroupRewardClaim(group, now);
        plugin.getRewardManager().giveReward(player, reward, playerName);
        plugin.getRewardManager().sendMessage(player, "group-reward-success");
        plugin.getDatabaseManager().saveAsync(player.getUniqueId());
    }

    private String getPrimaryGroup(Player player) {
        LuckPerms lp = plugin.getLuckPerms();
        if (lp == null) return "default";
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        return user.getPrimaryGroup().toLowerCase();
    }
}
