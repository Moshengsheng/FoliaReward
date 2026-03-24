package com.example.foliareward.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 消息工具类：颜色处理、占位符替换
 */
public final class MessageUtil {

    private MessageUtil() {}

    /** 将 & 颜色代码转换为 Minecraft 颜色代码 */
    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * 替换基础占位符并着色。
     * 支持: {player}, {streak}, {task}, {current}, {target}, {time}, {reward}
     */
    public static String format(String text, String... pairs) {
        if (text == null) return "";
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace(pairs[i], pairs[i + 1]);
        }
        return color(text);
    }

    /**
     * 替换 PlaceholderAPI 占位符（如果已安装），然后处理基础占位符。
     * 使用反射调用，避免 PAPI 未安装时 NoClassDefFoundError。
     */
    public static String formatPAPI(Player player, String text, String... pairs) {
        if (text == null) return "";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method setPlaceholders = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                text = (String) setPlaceholders.invoke(null, player, text);
            } catch (Exception ignored) {
                // PAPI 调用失败，跳过
            }
        }
        return format(text, pairs);
    }
}
