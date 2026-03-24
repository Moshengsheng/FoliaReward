package com.example.foliareward.gui;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.model.PlayerData;
import com.example.foliareward.model.RewardConfig;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 每日签到 GUI。
 *
 * 【Folia关键】GUI 的打开和点击处理必须在玩家所在 Region 的线程上执行。
 * open() 方法通过 FoliaScheduler.runEntity() 确保这一点。
 * InventoryClickEvent 本身就在玩家的 Region 线程上触发，直接处理即可。
 */
public class DailyGUI implements Listener {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String GUI_TITLE_PREFIX = "\u00A7e\u2756 \u6BCF\u65E5\u7B7E\u5230 \u2756"; // §e✦ 每日签到 ✦

    private final FoliaRewardPlugin plugin;

    public DailyGUI(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 打开每日签到 GUI。
     * 可从任意线程调用，内部自动切换到玩家的 Region 线程。
     */
    public void open(Player player) {
        FoliaScheduler.runAsync(plugin, () -> {
            PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
            if (data == null) {
                plugin.getRewardManager().sendRaw(player, "&c数据加载中，请稍候再试...");
                return;
            }

            String today = LocalDate.now().format(DATE_FMT);
            boolean claimed = today.equals(data.getLastDailyClaimDate());
            int streak = data.getDailyStreak();

            // 构建奖励描述（用于显示今日奖励）
            String group = getPrimaryGroup(player);
            Map<String, RewardConfig> groupRewards = plugin.getConfigManager().getDailyGroupRewards();
            RewardConfig todayReward = groupRewards.getOrDefault(group,
                    groupRewards.getOrDefault("default", null));

            // 切换到玩家 Region 线程打开 GUI
            FoliaScheduler.runEntity(plugin, player, () -> {
                String title = plugin.getConfig().getString("gui.daily.title",
                        GUI_TITLE_PREFIX);
                int size = plugin.getConfig().getInt("gui.daily.size", 27);
                Inventory inv = Bukkit.createInventory(null, size,
                        MessageUtil.color(title));

                // 槽位 13（中间）：签到状态
                inv.setItem(13, claimed
                        ? buildItem(Material.LIME_STAINED_GLASS_PANE,
                            "&a已签到！", List.of("&7连续签到：&e" + streak + " &7天"))
                        : buildItem(Material.CHEST,
                            "&e点击签到",
                            List.of("&7连续签到：&e" + streak + " &7天",
                                    buildRewardLore(todayReward))));

                // 槽位 11（左）：连续签到天数展示
                inv.setItem(11, buildItem(Material.BOOK,
                        "&6签到记录",
                        List.of("&7连续签到：&e" + streak + " &7天",
                                "&7最后签到：&f" + (data.getLastDailyClaimDate().isEmpty()
                                        ? "从未" : data.getLastDailyClaimDate()))));

                // 槽位 15（右）：下一个连续签到里程碑
                int nextMilestone = getNextMilestone(streak);
                inv.setItem(15, buildItem(Material.NETHER_STAR,
                        "&d里程碑奖励",
                        List.of("&7下一里程碑：&e第 " + nextMilestone + " 天",
                                "&7还需连续签到：&e" + (nextMilestone - streak) + " 天")));

                // 填充装饰
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
                plugin.getConfig().getString("gui.daily.title",
                        GUI_TITLE_PREFIX));
        if (!title.equals(guiTitle)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.CHEST) {
            player.closeInventory();
            plugin.getDailyRewardManager().claimDaily(player);
        }
    }

    // ---- 工具方法 ----

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(MessageUtil.color(name));
        meta.setLore(lore.stream().map(MessageUtil::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String buildRewardLore(RewardConfig reward) {
        if (reward == null) return "&7无奖励";
        StringBuilder sb = new StringBuilder("&7今日奖励: ");
        if (reward.getMoney() > 0) sb.append("&e").append((int) reward.getMoney()).append("金币 ");
        if (!reward.getItems().isEmpty()) sb.append("&b").append(reward.getItems().size()).append("件物品");
        return sb.toString();
    }

    private int getNextMilestone(int streak) {
        Map<Integer, RewardConfig> bonuses = plugin.getConfigManager().getStreakBonuses();
        for (int milestone : bonuses.keySet()) {
            if (streak < milestone) return milestone;
        }
        return bonuses.isEmpty() ? 7 : bonuses.keySet().stream().max(Integer::compare).orElse(7);
    }

    private void fillBorder(Inventory inv, int size) {
        ItemStack glass = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        int[] borders = {0,1,2,3,4,5,6,7,8, size-9,size-8,size-7,size-6,size-5,size-4,size-3,size-2,size-1};
        for (int slot : borders) {
            if (slot < size && inv.getItem(slot) == null) inv.setItem(slot, glass);
        }
    }

    private String getPrimaryGroup(Player player) {
        var lp = plugin.getLuckPerms();
        if (lp == null) return "default";
        var user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        return user.getPrimaryGroup().toLowerCase();
    }
}
