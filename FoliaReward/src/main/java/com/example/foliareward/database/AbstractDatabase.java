package com.example.foliareward.database;

import com.example.foliareward.model.PlayerData;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 数据库抽象基类，封装 HikariCP 连接池和公共 SQL 逻辑。
 * 子类只需提供 HikariDataSource 的配置，以及各数据库方言的 UPSERT SQL。
 *
 * 数据库表结构：
 *   player_data         —— 玩家基础数据（签到、首次进服等）
 *   task_progress       —— 任务进度
 *   completed_tasks     —— 已完成的一次性任务
 *   group_reward_claims —— 权限组奖励领取记录
 */
public abstract class AbstractDatabase implements Database {

    /**
     * 【Folia关键】专用于数据库操作的线程池。
     * 数据库 I/O 绝对不能在任何 Region 线程（包括 GlobalRegionScheduler）中执行，
     * 必须在此独立线程池或 AsyncScheduler 中运行。
     */
    protected static final Executor DB_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "FoliaReward-DB");
                t.setDaemon(true);
                return t;
            });

    protected HikariDataSource dataSource;

    // ----------------------------------------------------------------
    // 建表（子类在 initialize() 中调用）
    // ----------------------------------------------------------------

    protected void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS player_data (" +
                "  uuid              VARCHAR(36) PRIMARY KEY," +
                "  first_join_done   BOOLEAN     NOT NULL DEFAULT 0," +
                "  last_daily_date   VARCHAR(10) NOT NULL DEFAULT ''," +
                "  daily_streak      INT         NOT NULL DEFAULT 0," +
                "  last_join_date    VARCHAR(10) NOT NULL DEFAULT ''" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS task_progress (" +
                "  uuid      VARCHAR(36) NOT NULL," +
                "  task_id   VARCHAR(64) NOT NULL," +
                "  progress  INT         NOT NULL DEFAULT 0," +
                "  PRIMARY KEY (uuid, task_id)" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS completed_tasks (" +
                "  uuid      VARCHAR(36) NOT NULL," +
                "  task_id   VARCHAR(64) NOT NULL," +
                "  PRIMARY KEY (uuid, task_id)" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS group_reward_claims (" +
                "  uuid        VARCHAR(36) NOT NULL," +
                "  group_name  VARCHAR(64) NOT NULL," +
                "  last_claim  BIGINT      NOT NULL DEFAULT 0," +
                "  PRIMARY KEY (uuid, group_name)" +
                ")"
            );
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ----------------------------------------------------------------
    // 公共 CRUD 实现
    // ----------------------------------------------------------------

    @Override
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = new PlayerData(uuid);
            String uuidStr = uuid.toString();
            try (Connection conn = dataSource.getConnection()) {

                // 1. 基础数据
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT first_join_done,last_daily_date,daily_streak,last_join_date " +
                        "FROM player_data WHERE uuid=?")) {
                    ps.setString(1, uuidStr);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            data.setFirstJoinDone(rs.getBoolean("first_join_done"));
                            data.setLastDailyClaimDate(rs.getString("last_daily_date"));
                            data.setDailyStreak(rs.getInt("daily_streak"));
                            data.setLastDailyJoinDate(rs.getString("last_join_date"));
                        }
                    }
                }

                // 2. 任务进度
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT task_id,progress FROM task_progress WHERE uuid=?")) {
                    ps.setString(1, uuidStr);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            data.setProgress(rs.getString("task_id"), rs.getInt("progress"));
                        }
                    }
                }

                // 3. 已完成一次性任务
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT task_id FROM completed_tasks WHERE uuid=?")) {
                    ps.setString(1, uuidStr);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            data.markCompleted(rs.getString("task_id"));
                        }
                    }
                }

                // 4. 权限组奖励记录
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT group_name,last_claim FROM group_reward_claims WHERE uuid=?")) {
                    ps.setString(1, uuidStr);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            data.setGroupRewardClaim(
                                    rs.getString("group_name"),
                                    rs.getLong("last_claim"));
                        }
                    }
                }

            } catch (SQLException e) {
                throw new RuntimeException("加载玩家数据失败: " + uuid, e);
            }
            data.clearDirty();
            return data;
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerData data) {
        return CompletableFuture.runAsync(() -> doSave(data), DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> saveBatch(Iterable<PlayerData> dataList) {
        return CompletableFuture.runAsync(() -> {
            for (PlayerData data : dataList) {
                if (data.isDirty()) doSave(data);
            }
        }, DB_EXECUTOR);
    }

    // ----------------------------------------------------------------
    // 内部写库逻辑（批量事务）
    // ----------------------------------------------------------------

    private void doSave(PlayerData data) {
        String uuidStr = data.getUuid().toString();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 基础数据 UPSERT
                try (PreparedStatement ps = conn.prepareStatement(getUpsertPlayerSQL())) {
                    ps.setString(1, uuidStr);
                    ps.setBoolean(2, data.isFirstJoinDone());
                    ps.setString(3, data.getLastDailyClaimDate());
                    ps.setInt(4, data.getDailyStreak());
                    ps.setString(5, data.getLastDailyJoinDate());
                    ps.executeUpdate();
                }

                // 任务进度 UPSERT（批量）
                if (!data.getTaskProgress().isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(getUpsertTaskProgressSQL())) {
                        for (Map.Entry<String, Integer> entry : data.getTaskProgress().entrySet()) {
                            ps.setString(1, uuidStr);
                            ps.setString(2, entry.getKey());
                            ps.setInt(3, entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // 已完成任务 INSERT OR IGNORE（批量）
                if (!data.getCompletedTasks().isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(getUpsertCompletedTaskSQL())) {
                        for (String taskId : data.getCompletedTasks()) {
                            ps.setString(1, uuidStr);
                            ps.setString(2, taskId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // 权限组奖励记录 UPSERT（批量）
                if (!data.getGroupRewardLastClaim().isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(getUpsertGroupRewardSQL())) {
                        for (Map.Entry<String, Long> entry : data.getGroupRewardLastClaim().entrySet()) {
                            ps.setString(1, uuidStr);
                            ps.setString(2, entry.getKey());
                            ps.setLong(3, entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                conn.commit();
                data.clearDirty();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("保存玩家数据失败: " + data.getUuid(), e);
        }
    }

    // ---- 各数据库方言需实现的 UPSERT SQL ----

    /** player_data 表的 UPSERT SQL，参数顺序：uuid, first_join_done, last_daily_date, daily_streak, last_join_date */
    protected abstract String getUpsertPlayerSQL();

    /** task_progress 表的 UPSERT SQL，参数顺序：uuid, task_id, progress */
    protected abstract String getUpsertTaskProgressSQL();

    /** completed_tasks 表的 INSERT OR IGNORE SQL，参数顺序：uuid, task_id */
    protected abstract String getUpsertCompletedTaskSQL();

    /** group_reward_claims 表的 UPSERT SQL，参数顺序：uuid, group_name, last_claim */
    protected abstract String getUpsertGroupRewardSQL();
}
