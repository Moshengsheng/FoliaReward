package com.example.foliareward.config;

import com.example.foliareward.model.RewardConfig;
import com.example.foliareward.model.RewardItem;
import com.example.foliareward.model.TaskConfig;
import com.example.foliareward.model.TaskType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 配置管理器：从 config.yml 加载所有配置并提供类型安全的访问接口
 */
public class ConfigManager {

    private final Plugin plugin;

    // 消息
    private String prefix;
    private Map<String, String> messages = new HashMap<>();

    // 任务配置
    private Map<String, TaskConfig> tasks = new LinkedHashMap<>();

    // 连续签到奖励
    private Map<Integer, RewardConfig> streakBonuses = new TreeMap<>();

    // 每日签到各权限组奖励
    private Map<String, RewardConfig> dailyGroupRewards = new LinkedHashMap<>();

    // 权限组专属奖励
    private Map<String, RewardConfig> groupRewards = new LinkedHashMap<>();
    private long groupRewardCooldown;

    // 进服奖励
    private RewardConfig firstJoinReward;
    private RewardConfig dailyJoinReward;
    private boolean dailyJoinAutoGive;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        prefix = plugin.getConfig().getString("messages.prefix", "&6[奖励] &r");

        loadMessages();
        loadFirstJoinReward();
        loadDailyJoinReward();
        loadDailyRewards();
        loadGroupRewards();
        loadTasks();
    }

    // ---- 私有加载方法 ----

    private void loadMessages() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("messages");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            messages.put(key, sec.getString(key, ""));
        }
    }

    private void loadFirstJoinReward() {
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("join-rewards.first-join");
        if (sec == null || !sec.getBoolean("enabled", true)) {
            firstJoinReward = emptyReward();
            return;
        }
        firstJoinReward = parseReward(sec);
    }

    private void loadDailyJoinReward() {
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("join-rewards.daily-join");
        if (sec == null || !sec.getBoolean("enabled", true)) {
            dailyJoinReward = emptyReward();
            dailyJoinAutoGive = false;
            return;
        }
        dailyJoinAutoGive = sec.getBoolean("auto-give", false);
        dailyJoinReward = parseReward(sec);
    }

    private void loadDailyRewards() {
        // 连续签到奖励
        streakBonuses.clear();
        ConfigurationSection bonusSec = plugin.getConfig()
                .getConfigurationSection("daily-rewards.streak-bonuses");
        if (bonusSec != null) {
            for (String key : bonusSec.getKeys(false)) {
                try {
                    int day = Integer.parseInt(key);
                    ConfigurationSection s = bonusSec.getConfigurationSection(key);
                    if (s != null) streakBonuses.put(day, parseReward(s));
                } catch (NumberFormatException ignored) {}
            }
        }

        // 各权限组每日签到奖励
        dailyGroupRewards.clear();
        ConfigurationSection groupSec = plugin.getConfig()
                .getConfigurationSection("daily-rewards.groups");
        if (groupSec != null) {
            for (String group : groupSec.getKeys(false)) {
                ConfigurationSection s = groupSec.getConfigurationSection(group);
                if (s != null) dailyGroupRewards.put(group.toLowerCase(), parseReward(s));
            }
        }
    }

    private void loadGroupRewards() {
        groupRewards.clear();
        groupRewardCooldown = plugin.getConfig().getLong("group-rewards.cooldown", 86400);
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("group-rewards.groups");
        if (sec == null) return;
        for (String group : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(group);
            if (s != null) groupRewards.put(group.toLowerCase(), parseReward(s));
        }
    }

    private void loadTasks() {
        tasks.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("tasks");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            try {
                TaskType type = TaskType.valueOf(s.getString("type", "BLOCK_BREAK").toUpperCase());
                String displayName = s.getString("display-name", id);
                String description = s.getString("description", "");
                String target      = s.getString("target", "");
                int    amount      = s.getInt("amount", 1);
                boolean resetDaily = s.getBoolean("reset-daily", true);
                RewardConfig rewards = parseReward(
                        s.getConfigurationSection("rewards"));
                tasks.put(id, new TaskConfig(id, displayName, description,
                        type, target, amount, resetDaily, rewards));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("任务配置错误 [" + id + "]: " + e.getMessage());
            }
        }
    }

    // ---- 工具方法 ----

    private RewardConfig parseReward(ConfigurationSection sec) {
        if (sec == null) return emptyReward();
        double money = sec.getDouble("money", 0.0);
        List<String> commands = sec.getStringList("commands");
        List<RewardItem> items = new ArrayList<>();
        List<?> itemList = sec.getList("items");
        if (itemList != null) {
            for (Object obj : itemList) {
                if (obj instanceof Map<?,?> map) {
                    String typeName = String.valueOf(map.get("type"));
                    Material mat = Material.matchMaterial(typeName);
                    if (mat == null) {
                        plugin.getLogger().warning("未知的 Material: " + typeName);
                        continue;
                    }
                    int amt = map.containsKey("amount")
                            ? Integer.parseInt(String.valueOf(map.get("amount"))) : 1;
                    String name = map.containsKey("name")
                            ? String.valueOf(map.get("name")) : null;
                    @SuppressWarnings("unchecked")
                    List<String> lore = map.containsKey("lore")
                            ? (List<String>) map.get("lore") : null;
                    items.add(new RewardItem(mat, amt, name, lore));
                }
            }
        }
        return new RewardConfig(money, items, commands);
    }

    private RewardConfig emptyReward() {
        return new RewardConfig(0, Collections.emptyList(), Collections.emptyList());
    }

    // ---- 公共 Getter ----

    public String getMessage(String key) {
        return messages.getOrDefault(key, "&c[缺失消息: " + key + "]");
    }
    public String getPrefix() { return prefix; }

    public RewardConfig getFirstJoinReward()     { return firstJoinReward; }
    public RewardConfig getDailyJoinReward()     { return dailyJoinReward; }
    public boolean isDailyJoinAutoGive()         { return dailyJoinAutoGive; }

    public Map<Integer, RewardConfig> getStreakBonuses()        { return streakBonuses; }
    public Map<String, RewardConfig>  getDailyGroupRewards()    { return dailyGroupRewards; }
    public Map<String, RewardConfig>  getGroupRewards()         { return groupRewards; }
    public long                       getGroupRewardCooldown()  { return groupRewardCooldown; }

    public Map<String, TaskConfig>    getTasks()                { return tasks; }
    public TaskConfig                 getTask(String id)        { return tasks.get(id); }
}
