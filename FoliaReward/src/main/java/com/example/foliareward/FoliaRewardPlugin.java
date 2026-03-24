package com.example.foliareward;

import com.example.foliareward.command.DailyCommand;
import com.example.foliareward.command.RewardCommand;
import com.example.foliareward.config.ConfigManager;
import com.example.foliareward.database.DatabaseManager;
import com.example.foliareward.gui.DailyGUI;
import com.example.foliareward.gui.RewardGUI;
import com.example.foliareward.gui.TaskGUI;
import com.example.foliareward.listener.BlockBreakListener;
import com.example.foliareward.listener.EntityDeathListener;
import com.example.foliareward.listener.PlayerJoinListener;
import com.example.foliareward.listener.PlayerQuitListener;
import com.example.foliareward.manager.DailyRewardManager;
import com.example.foliareward.manager.GroupRewardManager;
import com.example.foliareward.manager.RewardManager;
import com.example.foliareward.manager.TaskManager;
import com.example.foliareward.util.FoliaScheduler;
import com.example.foliareward.web.WebServer;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

/**
 * FoliaReward 主插件类
 *
 * 启动顺序：
 *   1. 加载配置文件
 *   2. 初始化数据库（同步，失败则禁用插件）
 *   3. 连接软依赖（Vault、LuckPerms）
 *   4. 初始化各 Manager 和 GUI
 *   5. 注册监听器和命令
 *   6. 启动在线时长计时器
 *
 * 【Folia关键】
 *   - plugin.yml 中必须声明 folia-supported: true
 *   - 所有 Bukkit.getScheduler() 替换为 FoliaScheduler 的对应方法
 *   - 在线时长计时器使用 GlobalRegionScheduler（每秒 tick）
 *   - 数据库异步保存使用 AsyncScheduler 线程池
 */
public class FoliaRewardPlugin extends JavaPlugin {

    private static FoliaRewardPlugin instance;

    // 配置
    private ConfigManager configManager;

    // 数据库
    private DatabaseManager databaseManager;

    // Manager 层
    private RewardManager       rewardManager;
    private DailyRewardManager  dailyRewardManager;
    private TaskManager         taskManager;
    private GroupRewardManager  groupRewardManager;

    // GUI 层
    private RewardGUI rewardGUI;
    private DailyGUI  dailyGUI;
    private TaskGUI   taskGUI;

    // 软依赖
    private Economy   economy;
    private LuckPerms luckPerms;

    // Web 管理后台
    private WebServer webServer;

    @Override
    public void onEnable() {
        instance = this;

        // 1. 配置文件
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 2. 数据库（同步初始化，失败直接禁用）
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().severe("数据库初始化失败，插件将被禁用！原因: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. 软依赖连接
        setupVault();
        setupLuckPerms();

        // 4. Manager 初始化（注意顺序：RewardManager 最先，其余 Manager 依赖它）
        rewardManager      = new RewardManager(this);
        dailyRewardManager = new DailyRewardManager(this);
        taskManager        = new TaskManager(this);
        groupRewardManager = new GroupRewardManager(this);

        // 5. GUI 初始化（GUI 类内部会注册自己的 InventoryClickEvent 监听器）
        rewardGUI = new RewardGUI(this);
        dailyGUI  = new DailyGUI(this);
        taskGUI   = new TaskGUI(this);

        // 6. 其余监听器
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this),  this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this),  this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this),  this);
        getServer().getPluginManager().registerEvents(new EntityDeathListener(this), this);

        // 7. 命令
        RewardCommand rewardCommand = new RewardCommand(this);
        getCommand("reward").setExecutor(rewardCommand);
        getCommand("reward").setTabCompleter(rewardCommand);

        DailyCommand dailyCommand = new DailyCommand(this);
        getCommand("daily").setExecutor(dailyCommand);

        getCommand("task").setExecutor(rewardCommand);

        // 8. 在线时长计时器（每秒 tick）
        // 【Folia关键】GlobalRegionScheduler 代替 BukkitScheduler.runTaskTimer()
        // 20L ticks = 1 秒，initialDelay=20, period=20
        FoliaScheduler.runGlobalTimer(this,
                task -> taskManager.tickOnlineTime(),
                20L, 20L);

        // 9. 定时异步保存（每 5 分钟保存一次脏数据，减少关服时写入压力）
        // 【Folia关键】数据库操作使用 AsyncScheduler
        FoliaScheduler.runAsyncTimer(this,
                task -> getServer().getOnlinePlayers().forEach(
                        p -> databaseManager.saveAsync(p.getUniqueId())),
                5, 5, TimeUnit.MINUTES);

        getLogger().info("FoliaReward v" + getDescription().getVersion() + " 已成功启动！");
        getLogger().info("数据库类型: " + getConfig().getString("database.type", "SQLITE"));
        getLogger().info("已加载任务数量: " + configManager.getTasks().size());

        // 10. Web 管理后台（可选，默认关闭）
        if (getConfig().getBoolean("web.enabled", false)) {
            webServer = new WebServer(this);
            try {
                webServer.start();
            } catch (Exception e) {
                getLogger().warning("Web 管理后台启动失败: " + e.getMessage() + "（插件继续运行）");
                webServer = null;
            }
        }
    }

    @Override
    public void onDisable() {
        // 停止 Web 管理后台
        if (webServer != null) {
            webServer.stop();
        }
        // 同步保存所有在线玩家数据（关服时等待完成）
        if (databaseManager != null) {
            getLogger().info("正在保存玩家数据...");
            databaseManager.saveAll();
            databaseManager.close();
        }
        getLogger().info("FoliaReward 已关闭。");
    }

    // ----------------------------------------------------------------
    // 软依赖初始化
    // ----------------------------------------------------------------

    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("未检测到 Vault，经济功能（金币奖励）将不可用！");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault 已加载，但未找到经济插件（如 EssentialsX），经济功能不可用！");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("已连接 Vault 经济系统: " + economy.getName());
    }

    private void setupLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("未检测到 LuckPerms，权限组功能将使用默认组（default）！");
            return;
        }
        RegisteredServiceProvider<LuckPerms> rsp =
                getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (rsp != null) {
            luckPerms = rsp.getProvider();
            getLogger().info("已连接 LuckPerms。");
        }
    }

    // ----------------------------------------------------------------
    // 重载
    // ----------------------------------------------------------------

    public void reload() {
        configManager.loadConfig();
        taskManager.reloadTasks();
        // 重启 Web 服务器（配置可能变化）
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        if (getConfig().getBoolean("web.enabled", false)) {
            webServer = new WebServer(this);
            try {
                webServer.start();
            } catch (Exception e) {
                getLogger().warning("Web 管理后台重启失败: " + e.getMessage());
                webServer = null;
            }
        }
    }

    // ----------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------

    public static FoliaRewardPlugin getInstance()          { return instance; }
    public ConfigManager       getConfigManager()          { return configManager; }
    public DatabaseManager     getDatabaseManager()        { return databaseManager; }
    public RewardManager       getRewardManager()          { return rewardManager; }
    public DailyRewardManager  getDailyRewardManager()     { return dailyRewardManager; }
    public TaskManager         getTaskManager()            { return taskManager; }
    public GroupRewardManager  getGroupRewardManager()     { return groupRewardManager; }
    public RewardGUI           getRewardGUI()              { return rewardGUI; }
    public DailyGUI            getDailyGUI()               { return dailyGUI; }
    public TaskGUI             getTaskGUI()                { return taskGUI; }
    public Economy             getEconomy()                { return economy; }
    public LuckPerms           getLuckPerms()              { return luckPerms; }
    public WebServer           getWebServer()              { return webServer; }
}
