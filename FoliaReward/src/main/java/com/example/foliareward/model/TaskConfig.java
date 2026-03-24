package com.example.foliareward.model;

/** 任务配置（对应 config.yml tasks 节下的每个任务条目） */
public class TaskConfig {

    private final String id;
    private final String displayName;
    private final String description;
    private final TaskType type;
    /** 目标方块（Material名）或目标实体（EntityType名），ONLINE_TIME 任务留空 */
    private final String target;
    /** 目标数量（ONLINE_TIME 单位：分钟） */
    private final int amount;
    /** 是否每日重置进度 */
    private final boolean resetDaily;
    private final RewardConfig rewards;

    public TaskConfig(String id, String displayName, String description,
                      TaskType type, String target, int amount,
                      boolean resetDaily, RewardConfig rewards) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.target = target != null ? target.toUpperCase() : "";
        this.amount = amount;
        this.resetDaily = resetDaily;
        this.rewards = rewards;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public TaskType getType() { return type; }
    public String getTarget() { return target; }
    public int getAmount() { return amount; }
    public boolean isResetDaily() { return resetDaily; }
    public RewardConfig getRewards() { return rewards; }
}
