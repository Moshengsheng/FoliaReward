package com.example.foliareward.listener;

import com.example.foliareward.FoliaRewardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * 方块挖掘监听器：将挖掘事件转发给 TaskManager 处理任务进度。
 *
 * 【Folia关键】BlockBreakEvent 在方块所在 Region 的线程上触发，
 * TaskManager.onBlockBreak() 内部只操作 ConcurrentHashMap，线程安全。
 */
public class BlockBreakListener implements Listener {

    private final FoliaRewardPlugin plugin;

    public BlockBreakListener(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        plugin.getTaskManager().onBlockBreak(player, event.getBlock().getType());
    }
}