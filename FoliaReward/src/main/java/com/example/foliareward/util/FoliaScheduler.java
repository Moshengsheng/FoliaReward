package com.example.foliareward.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia 调度器工具类
 *
 * 【Folia关键】Folia 是多线程区域化服务器核心，每个区域（Region）有独立的主线程。
 * 传统的 Bukkit.getScheduler() 在 Folia 上已被废弃，
 * 必须使用以下三种调度器：
 *
 *   1. EntityScheduler    (entity.getScheduler())
 *      - 适用于：操作特定实体/玩家（发放物品、发送消息等）
 *      - 在该实体所在 Region 的线程上执行
 *      - 提供 retired 回调（实体失效时调用，防止空指针）
 *
 *   2. RegionScheduler    (Bukkit.getRegionScheduler())
 *      - 适用于：操作特定位置的世界（放置方块等）
 *      - 在该位置所在 Region 的线程上执行
 *
 *   3. GlobalRegionScheduler (Bukkit.getGlobalRegionScheduler())
 *      - 适用于：全局操作（统计、计时器等不涉及具体位置/实体）
 *      - 在主 Region（spawn 所在区域）线程上执行
 *
 *   4. AsyncScheduler     (Bukkit.getAsyncScheduler())
 *      - 适用于：所有 I/O 操作（数据库读写、文件读写）
 *      - 在异步线程池中执行，不阻塞任何 Region 线程
 *
 * 本类封装以上所有调度方式，禁止在插件其他代码中直接调用
 * Bukkit.getScheduler() 或 new BukkitRunnable()。
 */
public final class FoliaScheduler {

    private FoliaScheduler() {}

    // ============================================================
    // GlobalRegionScheduler —— 全局任务（计时器、统计等）
    // ============================================================

    /**
     * 在全局 Region 线程立即执行一次。
     * 替代: Bukkit.getScheduler().runTask(plugin, task)
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    /**
     * 在全局 Region 线程延迟执行一次。
     * 替代: Bukkit.getScheduler().runTaskLater(plugin, task, delay)
     *
     * @param delayTicks 延迟 tick 数（20 ticks = 1 秒）
     */
    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks);
    }

    /**
     * 在全局 Region 线程周期性执行。
     * 替代: Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelay, period)
     *
     * @param consumer 接收 ScheduledTask，可用于取消任务
     */
    public static void runGlobalTimer(Plugin plugin, Consumer<ScheduledTask> consumer,
                                      long initialDelayTicks, long periodTicks) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, consumer,
                initialDelayTicks, periodTicks);
    }

    // ============================================================
    // RegionScheduler —— 特定位置任务（世界操作）
    // ============================================================

    /**
     * 在指定位置所在 Region 的线程立即执行。
     * 替代: Bukkit.getScheduler().runTask() + 位置检查
     */
    public static void runRegion(Plugin plugin, Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
    }

    /**
     * 在指定位置所在 Region 的线程延迟执行。
     */
    public static void runRegionDelayed(Plugin plugin, Location location,
                                        Runnable task, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), delayTicks);
    }

    // ============================================================
    // EntityScheduler —— 实体/玩家操作（发放物品、消息等）
    // ============================================================

    /**
     * 在实体（玩家）所在 Region 的线程立即执行。
     * 【Folia关键】给玩家发放物品、发送消息必须用此方法。
     *
     * @param task    任务内容
     * @param retired 当实体不再有效（已下线/被卸载）时的回退处理，可传 null
     */
    public static void runEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        entity.getScheduler().run(plugin, t -> task.run(), retired);
    }

    /**
     * 在实体所在 Region 的线程延迟执行。
     */
    public static void runEntityDelayed(Plugin plugin, Entity entity,
                                        Runnable task, Runnable retired, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, t -> task.run(), retired, delayTicks);
    }

    /**
     * 在实体所在 Region 的线程周期性执行。
     */
    public static void runEntityTimer(Plugin plugin, Entity entity,
                                      Consumer<ScheduledTask> consumer, Runnable retired,
                                      long initialDelayTicks, long periodTicks) {
        entity.getScheduler().runAtFixedRate(plugin, consumer, retired,
                initialDelayTicks, periodTicks);
    }

    // ============================================================
    // AsyncScheduler —— 异步任务（数据库、文件 I/O）
    // ============================================================

    /**
     * 在异步线程立即执行（用于数据库操作）。
     * 替代: Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    /**
     * 在异步线程延迟执行。
     */
    public static void runAsyncDelayed(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), delay, unit);
    }

    /**
     * 在异步线程周期性执行。
     */
    public static void runAsyncTimer(Plugin plugin, Consumer<ScheduledTask> consumer,
                                     long initialDelay, long period, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, consumer, initialDelay, period, unit);
    }
}
