package com.example.foliareward.listener;

import com.example.foliareward.FoliaRewardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * 实体击杀监听器：将击杀事件转发给 TaskManager 处理任务进度。
 *
 * 【Folia关键】EntityDeathEvent 在实体所在 Region 的线程上触发，
 * TaskManager.onEntityKill() 内部只操作 ConcurrentHashMap，线程安全。
 */
public class EntityDeathListener implements Listener {

    private final FoliaRewardPlugin plugin;

    public EntityDeathListener(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        plugin.getTaskManager().onEntityKill(killer, event.getEntityType());
    }
}