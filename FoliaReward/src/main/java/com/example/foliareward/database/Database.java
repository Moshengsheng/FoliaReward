package com.example.foliareward.database;

import com.example.foliareward.model.PlayerData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 数据库接口，所有操作返回 CompletableFuture（异步执行）。
 * 实现类：SQLiteDatabase、MySQLDatabase
 */
public interface Database {

    /** 初始化数据库（建表等），同步调用一次 */
    void initialize() throws Exception;

    /** 关闭数据库连接池 */
    void close();

    /**
     * 从数据库加载玩家数据。
     * 若玩家不存在则返回新建的 PlayerData 对象。
     */
    CompletableFuture<PlayerData> loadPlayer(UUID uuid);

    /** 将玩家数据写入数据库（INSERT OR REPLACE / UPSERT） */
    CompletableFuture<Void> savePlayer(PlayerData data);

    /** 批量保存（服务器关闭时调用） */
    CompletableFuture<Void> saveBatch(Iterable<PlayerData> dataList);
}
