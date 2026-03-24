package com.example.foliareward.gui;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.util.FoliaScheduler;
import com.example.foliareward.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 奖励中心主界面 GUI。
 * 提供各功能模块的入口按钮。
 */
public class RewardGUI implements Listener {

    private final FoliaRewardPlugin plugin;

    public RewardGUI(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 打开奖励主界面。
     * 【Folia关键】必须在玩家 Region 线程打开 Inventory。
     */
    public void open(Player player) {
        FoliaScheduler.runEntity(plugin, player, () -> {
            String title = plugin.getConfig().getString("gui.main.title", "&6✦ 奖励中心 ✦");
            int size = plugin.getConfig().getInt("gui.main.size", 54);
            Inventory inv = Bukkit.createInventory(null, size, MessageUtil.color(title));

            // 每日签到按钮（槽位 20）
            inv.setItem(20, buildItem(Material.CLOCK, "&e每日签到",
                    List.of("&7每天可以签到一次", "&7连续签到可获得额外奖励", "", "&a点击打开签到界面")));

            // 任务列表按钮（槽位 24）
            inv.setItem(24, buildItem(Material.WRITABLE_BOOK, "&b任务列表",
                    List.of("&7完成各类任务获取奖励", "&7包含挖矿、击杀、在线任务", "", "&a点击查看任务")));

            // 权限组奖励按钮（槽位 31）
            inv.setItem(31, buildItem(Material.CHEST_MINECART, "&6权限组奖励",
                    List.of("&7根据您的权限组领取专属奖励", "&724小时冷却", "", "&a点击领取")));

            // 填充装饰
            fillBorder(inv, size);

            player.openInventory(inv);
        }, null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        String guiTitle = MessageUtil.color(
                plugin.getConfig().getString("gui.main.title", "&6✦ 奖励中心 ✦"));
        if (!title.equals(guiTitle)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        switch (event.getCurrentItem().getType()) {
            case CLOCK -> {
                player.closeInventory();
                plugin.getDailyGUI().open(player);
            }
            case WRITABLE_BOOK -> {
                player.closeInventory();
                plugin.getTaskGUI().open(player);
            }
            case CHEST_MINECART -> {
                player.closeInventory();
                plugin.getGroupRewardManager().claimGroupReward(player);
            }
        }
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(MessageUtil.color(name));
        meta.setLore(lore.stream().map(MessageUtil::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(Inventory inv, int size) {
        ItemStack glass = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) { if (inv.getItem(i) == null) inv.setItem(i, glass); }
        for (int i = size - 9; i < size; i++) { if (inv.getItem(i) == null) inv.setItem(i, glass); }
        for (int i = 0; i < size; i += 9) { if (inv.getItem(i) == null) inv.setItem(i, glass); }
        for (int i = 8; i < size; i += 9) { if (inv.getItem(i) == null) inv.setItem(i, glass); }
    }
}
