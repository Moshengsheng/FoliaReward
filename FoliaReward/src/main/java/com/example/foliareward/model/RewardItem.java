package com.example.foliareward.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static com.example.foliareward.util.MessageUtil.color;

/** 单个奖励物品配置 */
public class RewardItem {

    private final Material material;
    private final int amount;
    private final String displayName;  // null = 使用默认名
    private final List<String> lore;

    public RewardItem(Material material, int amount, String displayName, List<String> lore) {
        this.material = material;
        this.amount = amount;
        this.displayName = displayName;
        this.lore = lore == null ? new ArrayList<>() : lore;
    }

    /** 转换为 ItemStack（已应用颜色代码） */
    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(material, amount);
        if (displayName != null || !lore.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (displayName != null) meta.setDisplayName(color(displayName));
                if (!lore.isEmpty()) {
                    List<String> coloredLore = new ArrayList<>();
                    for (String line : lore) coloredLore.add(color(line));
                    meta.setLore(coloredLore);
                }
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
}
