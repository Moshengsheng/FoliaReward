package com.example.foliareward.database;

import com.example.foliareward.model.PlayerData;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 数据库管理器：
 *   1. 根据配置选择 SQLite 或 MySQL
 *   2. 维护内存缓存（ConcurrentHashMap），避免频繁查库
 *   3. 提供统一的 loadPlayer / savePlayer 入口
 */
public class DatabaseManager {

    private final Plugin plugin;
    private final Database database;
    /** 在线玩家的数据缓存，线程安全 */
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        String type = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        if ("MYSQL".equals(type)) {
            database = new MySQLDatabase(plugin);
        } else {
            database = new SQLiteDatabase(plugin);
        }
    }

    /** 初始化数据库（同步，插件启动时调用） */
    public void initialize() throws Exception {
        database.initialize();
    }

    /** 关闭连接池（插件关闭时调用） */
    public void close() {
        database.close();
    }

    /**
     * 获取玩家数据（优先从缓存取）。
     * 首次加载时从数据库异步读取，完成后放入缓存。
     */
    public CompletableFuture<PlayerData> getPlayerData(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.loadPlayer(uuid).thenApply(data -> {
            cache.put(uuid, data);
            return data;
        });
    }

    /**
     * 将玩家数据放入缓存（玩家加入时预加载）
     */
    public CompletableFuture<PlayerData> loadAndCache(UUID uuid) {
        return database.loadPlayer(uuid).thenApply(data -> {
            cache.put(uuid, data);
            return data;
        });
    }

    /** 从缓存中获取（仅内存，不查库），可能返回 null */
    public PlayerData getCached(UUID uuid) {
        return cache.get(uuid);
    }

    /** 从缓存中移除（玩家离线时调用），并异步保存 */
    public void unloadAndSave(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null && data.isDirty()) {
            database.savePlayer(data).exceptionally(e -> {
                plugin.getLogger().severe("保存玩家数据失败 [" + uuid + "]: " + e.getMessage());
                return null;
            });
        }
    }

    /** 异步保存单个玩家数据（保留缓存） */
    public void saveAsync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null && data.isDirty()) {
            database.savePlayer(data).exceptionally(e -> {
                plugin.getLogger().severe("保存玩家数据失败 [" + uuid + "]: " + e.getMessage());
                return null;
            });
        }
    }

    /** 保存所有缓存中的脏数据（服务器关闭时调用） */
    public void saveAll() {
        database.saveBatch(cache.values()).exceptionally(e -> {
            plugin.getLogger().severe("批量保存数据失败: " + e.getMessage());
            return null;
        }).join(); // 关闭时需要等待完成
    }
}
