package com.example.foliareward.model;

import java.util.List;

/** 一套奖励配置（金币 + 物品 + 命令） */
public class RewardConfig {

    private final double money;
    private final List<RewardItem> items;
    private final List<String> commands;

    public RewardConfig(double money, List<RewardItem> items, List<String> commands) {
        this.money = money;
        this.items = items;
        this.commands = commands;
    }

    public double getMoney() { return money; }
    public List<RewardItem> getItems() { return items; }
    public List<String> getCommands() { return commands; }

    public boolean isEmpty() {
        return money <= 0 && items.isEmpty() && commands.isEmpty();
    }
}
