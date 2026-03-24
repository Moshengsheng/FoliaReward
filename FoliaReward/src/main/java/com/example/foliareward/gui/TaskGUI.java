package com.example.foliareward.gui;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.model.PlayerData;
import com.example.foliareward.model.TaskConfig;
import com.example.foliareward.model.TaskType;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务列表 GUI。
 * 展示所有任务的进度、状态，可点击领取已完成任务的奖励。
 */
public class TaskGUI implements Listener {

    private final FoliaRewardPlugin plugin;

    public TaskGUI(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 打开任务列表。
     * 先异步获取数据，再切换到玩家线程开 GUI。
     */
    public void open(Player player) {
        FoliaScheduler.runAsync(plugin, () -> {
            PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
            if (data == null) {
                plugin.getRewardManager().sendRaw(player, "&c数据加载中，请稍候再试...");
                return;
            }

            Map<String, TaskConfig> tasks = plugin.getTaskManager().getTaskConfigs();

            FoliaScheduler.runEntity(plugin, player, () -> {
                String title = plugin.getConfig().getString("gui.task.title", "&b✦ 任务列表 ✦");
                int size = plugin.getConfig().getInt("gui.task.size", 54);
                Inventory inv = Bukkit.createInventory(null, size, MessageUtil.color(title));

                int slot = 10; // 从第二行第二列开始放
                for (TaskConfig task : tasks.values()) {
                    if (slot >= size - 9) break; // 不放进最后一行
                    if ((slot + 1) % 9 == 0) slot += 2; // 跳过两侧边框

                    inv.setItem(slot, buildTaskItem(task, data, player));
                    slot++;
                }

                // 底部中间：领取奖励按钮
                inv.setItem(size - 5, buildItem(Material.EMERALD, "&a领取所有已完成奖励",
                        List.of("&7点击领取所有已完成任务的奖励")));

                fillBorder(inv, size);
                player.openInventory(inv);
            }, null);
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        String guiTitle = MessageUtil.color(
                plugin.getConfig().getString("gui.task.title", "&b✦ 任务列表 ✦"));
        if (!title.equals(guiTitle)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.EMERALD) {
            player.closeInventory();
            plugin.getTaskManager().claimTaskRewards(player);
        }
    }

    private ItemStack buildTaskItem(TaskConfig task, PlayerData data, Player player) {
        boolean isCompleted    = !task.isResetDaily() && data.isCompleted(task.getId());
        boolean isPending      = data.getPendingClaim().contains(task.getId());
        boolean isDailyDone    = task.isResetDaily() && data.isDailyClaimedToday(task.getId());
        int     progress       = data.getProgress(task.getId());
        int     onlineSec      = plugin.getTaskManager().getOnlineSeconds(player.getUniqueId());
        if (task.getType() == TaskType.ONLINE_TIME) progress = onlineSec / 60;

        Material mat;
        String status;
        if (isCompleted || isDailyDone) {
            mat = Material.LIME_STAINED_GLASS_PANE;
            status = "&a已完成 ✔";
        } else if (isPending) {
            mat = Material.GOLD_INGOT;
            status = "&6可领取！";
        } else {
            mat = Material.PAPER;
            status = "&7进度中...";
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7" + task.getDescription());
        lore.add("");
        lore.add("&7进度: &e" + Math.min(progress, task.getAmount()) + " &7/ &e" + task.getAmount());
        lore.add("&7类型: &f" + typeLabel(task.getType()));
        lore.add("&7重置: &f" + (task.isResetDaily() ? "每日" : "一次性"));
        lore.add("");
        lore.add("&7奖励:");
        if (task.getRewards().getMoney() > 0) lore.add("  &e" + (int) task.getRewards().getMoney() + " 金币");
        task.getRewards().getItems().forEach(item ->
                lore.add("  &b" + item.getAmount() + "x " + item.getMaterial().name()));
        lore.add("");
        lore.add(status);

        return buildItem(mat, task.getDisplayName(), lore);
    }

    private String typeLabel(TaskType type) {
        return switch (type) {
            case BLOCK_BREAK -> "挖掘方块";
            case ENTITY_KILL -> "击杀实体";
            case ONLINE_TIME -> "在线时长";
        };
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
