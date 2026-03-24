package com.example.foliareward.listener;

import com.example.foliareward.FoliaRewardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家离线监听器：清理缓存、保存数据
 */
public class PlayerQuitListener implements Listener {

    private final FoliaRewardPlugin plugin;

    public PlayerQuitListener(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 停止在线计时
        plugin.getTaskManager().onPlayerQuit(player);
        // 异步保存并从缓存中移除
        plugin.getDatabaseManager().unloadAndSave(player.getUniqueId());
    }
}
