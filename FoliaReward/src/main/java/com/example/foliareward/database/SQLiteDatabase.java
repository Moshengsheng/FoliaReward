package com.example.foliareward.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * SQLite 数据库实现
 * 使用本地文件，适合单服部署，无需额外数据库服务
 */
public class SQLiteDatabase extends AbstractDatabase {

    private final Plugin plugin;

    public SQLiteDatabase(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws Exception {
        // 确保数据目录存在
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        plugin.getDataFolder().mkdirs();

        HikariConfig config = new HikariConfig();
        // SQLite JDBC URL
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        // SQLite 是单文件数据库，连接池大小设为 1（或少量）
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setPoolName("FoliaReward-SQLite");
        // SQLite 性能优化
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "-8000");
        config.addDataSourceProperty("foreign_keys", "true");

        dataSource = new HikariDataSource(config);
        createTables();
        plugin.getLogger().info("SQLite 数据库已初始化: " + dbFile.getAbsolutePath());
    }

    // SQLite 使用 "INSERT OR REPLACE" 语法实现 UPSERT

    @Override
    protected String getUpsertPlayerSQL() {
        return "INSERT OR REPLACE INTO player_data " +
               "(uuid,first_join_done,last_daily_date,daily_streak,last_join_date) " +
               "VALUES (?,?,?,?,?)";
    }

    @Override
    protected String getUpsertTaskProgressSQL() {
        return "INSERT OR REPLACE INTO task_progress (uuid,task_id,progress) VALUES (?,?,?)";
    }

    @Override
    protected String getUpsertCompletedTaskSQL() {
        return "INSERT OR IGNORE INTO completed_tasks (uuid,task_id) VALUES (?,?)";
    }

    @Override
    protected String getUpsertGroupRewardSQL() {
        return "INSERT OR REPLACE INTO group_reward_claims (uuid,group_name,last_claim) VALUES (?,?,?)";
    }
}
