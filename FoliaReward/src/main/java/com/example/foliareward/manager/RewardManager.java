package com.example.foliareward.manager;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.model.RewardConfig;
import com.example.foliareward.model.RewardItem;
import com.example.foliareward.util.FoliaScheduler;
import com.example.foliareward.util.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 奖励发放核心类。
 *
 * 【Folia关键】所有涉及修改玩家状态（物品栏、消息）的操作，
 * 都必须在玩家所在 Region 的线程上执行，
 * 因此统一使用 FoliaScheduler.runEntity() 包装。
 */
public class RewardManager {

    private final FoliaRewardPlugin plugin;

    public RewardManager(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 向玩家发放一套奖励配置中的所有奖励。
     *
     * 此方法可以从任意线程安全调用（会自动切换到玩家的 Region 线程执行实际操作）。
     *
     * @param player      目标玩家
     * @param reward      奖励配置
     * @param playerName  用于命令中替换 {player} 的名字（与 player.getName() 相同，
     *                    传参是为了在 retired 回调中也能记录日志）
     */
    public void giveReward(Player player, RewardConfig reward, String playerName) {
        if (reward == null || reward.isEmpty()) return;

        // 【Folia关键】使用 EntityScheduler 确保在玩家所在 Region 的线程上执行
        // retired 回调：若玩家在任务执行前已离线，记录日志但不报错
        FoliaScheduler.runEntity(plugin, player, () -> {
            // 发放物品
            for (RewardItem rewardItem : reward.getItems()) {
                ItemStack item = rewardItem.toItemStack();
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                if (!overflow.isEmpty()) {
                    // 背包满了，丢在玩家脚下
                    for (ItemStack drop : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }

            // 发放经济（通过 Vault）
            if (reward.getMoney() > 0) {
                Economy eco = plugin.getEconomy();
                if (eco != null) {
                    eco.depositPlayer(player, reward.getMoney());
                    player.sendMessage(MessageUtil.color(
                            plugin.getConfigManager().getPrefix() +
                            "&a已获得 &e" + reward.getMoney() + " &a金币！"));
                }
            }

            // 执行控制台命令（命令可能影响世界，也在此线程批量分发）
            for (String cmd : reward.getCommands()) {
                String formatted = cmd.replace("{player}", playerName)
                                      .replace("%player%", playerName);
                // 控制台命令需要在主线程执行，Folia 的 GlobalRegionScheduler 相当于主线程
                // 但 EntityScheduler 本身就是 Region 线程，对于不影响特定位置的命令，
                // 可以直接在这里通过 dispatchCommand 执行
                plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(), formatted);
            }

        }, () -> plugin.getLogger().fine("玩家 " + playerName + " 已离线，奖励发放取消"));
    }

    /**
     * 向玩家发送带前缀的彩色消息。
     * 同样需要在玩家的 Region 线程上发送。
     */
    public void sendMessage(Player player, String messageKey, String... replacePairs) {
        String raw = plugin.getConfigManager().getPrefix() +
                     plugin.getConfigManager().getMessage(messageKey);
        String formatted = MessageUtil.format(raw, replacePairs);

        FoliaScheduler.runEntity(plugin, player,
                () -> player.sendMessage(formatted),
                null);
    }

    /**
     * 直接发送任意消息（不经过配置文件 key）。
     */
    public void sendRaw(Player player, String message) {
        FoliaScheduler.runEntity(plugin, player,
                () -> player.sendMessage(MessageUtil.color(message)),
                null);
    }
}
