package com.example.foliareward.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * MySQL 数据库实现
 * 适合多服共享数据，需要外部 MySQL/MariaDB 服务
 */
public class MySQLDatabase extends AbstractDatabase {

    private final Plugin plugin;

    public MySQLDatabase(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("database.mysql");
        if (cfg == null) {
            throw new IllegalStateException("配置文件中缺少 database.mysql 节！");
        }

        String host     = cfg.getString("host", "localhost");
        int    port     = cfg.getInt("port", 3306);
        String database = cfg.getString("database", "foliareward");
        String user     = cfg.getString("username", "root");
        String pass     = cfg.getString("password", "");
        int    poolSize = cfg.getInt("pool-size", 10);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf8&serverTimezone=UTC",
                host, port, database));
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(cfg.getLong("connection-timeout", 30000));
        config.setIdleTimeout(cfg.getLong("idle-timeout", 600000));
        config.setMaxLifetime(cfg.getLong("max-lifetime", 1800000));
        config.setPoolName("FoliaReward-MySQL");
        // MySQL 性能优化
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);
        createTables();
        plugin.getLogger().info("MySQL 数据库已连接: " + host + ":" + port + "/" + database);
    }

    // MySQL 使用 "INSERT INTO ... ON DUPLICATE KEY UPDATE" 实现 UPSERT

    @Override
    protected String getUpsertPlayerSQL() {
        return "INSERT INTO player_data " +
               "(uuid,first_join_done,last_daily_date,daily_streak,last_join_date) " +
               "VALUES (?,?,?,?,?) " +
               "ON DUPLICATE KEY UPDATE " +
               "first_join_done=VALUES(first_join_done)," +
               "last_daily_date=VALUES(last_daily_date)," +
               "daily_streak=VALUES(daily_streak)," +
               "last_join_date=VALUES(last_join_date)";
    }

    @Override
    protected String getUpsertTaskProgressSQL() {
        return "INSERT INTO task_progress (uuid,task_id,progress) VALUES (?,?,?) " +
               "ON DUPLICATE KEY UPDATE progress=VALUES(progress)";
    }

    @Override
    protected String getUpsertCompletedTaskSQL() {
        return "INSERT IGNORE INTO completed_tasks (uuid,task_id) VALUES (?,?)";
    }

    @Override
    protected String getUpsertGroupRewardSQL() {
        return "INSERT INTO group_reward_claims (uuid,group_name,last_claim) VALUES (?,?,?) " +
               "ON DUPLICATE KEY UPDATE last_claim=VALUES(last_claim)";
    }
}
