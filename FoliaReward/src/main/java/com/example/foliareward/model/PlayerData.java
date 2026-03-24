package com.example.foliareward.model;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家运行时数据（线程安全）。
 *
 * 【Folia关键】此对象可能被多个 Region 线程并发读写，
 * 所有集合字段使用 ConcurrentHashMap/ConcurrentHashSet，
 * long 字段使用 volatile 保证可见性。
 */
public class PlayerData {

    private final UUID uuid;

    // --- 进服奖励 ---
    /** 是否已领取首次进服奖励 */
    private volatile boolean firstJoinDone;

    // --- 每日签到 ---
    /** 最后一次签到的日期字符串（格式 "yyyy-MM-dd"），空字符串 = 从未签到 */
    private volatile String lastDailyClaimDate;
    /** 连续签到天数 */
    private volatile int dailyStreak;
    /** 今日进服日期（用于判断是否已提示签到） */
    private volatile String lastDailyJoinDate;

    // --- 权限组奖励 ---
    /** 各权限组最后领取时间（epoch 毫秒） */
    private final Map<String, Long> groupRewardLastClaim = new ConcurrentHashMap<>();

    // --- 任务进度 ---
    /** 任务ID -> 当前进度 */
    private final Map<String, Integer> taskProgress = new ConcurrentHashMap<>();
    /** 已完成且已领取奖励的一次性任务（resetDaily=false） */
    private final Set<String> completedTasks = ConcurrentHashMap.newKeySet();
    /** 已完成但尚未领取奖励的任务 */
    private final Set<String> pendingClaim = ConcurrentHashMap.newKeySet();
    /** 每日任务今日已领取集合（每天清零） */
    private final Set<String> dailyClaimedToday = ConcurrentHashMap.newKeySet();

    // --- 脏标记（用于判断是否需要写库） ---
    private volatile boolean dirty = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.firstJoinDone = false;
        this.lastDailyClaimDate = "";
        this.dailyStreak = 0;
        this.lastDailyJoinDate = "";
    }

    // ---- Getters / Setters ----

    public UUID getUuid() { return uuid; }

    public boolean isFirstJoinDone() { return firstJoinDone; }
    public void setFirstJoinDone(boolean v) { this.firstJoinDone = v; markDirty(); }

    public String getLastDailyClaimDate() { return lastDailyClaimDate; }
    public void setLastDailyClaimDate(String date) { this.lastDailyClaimDate = date; markDirty(); }

    public int getDailyStreak() { return dailyStreak; }
    public void setDailyStreak(int streak) { this.dailyStreak = streak; markDirty(); }

    public String getLastDailyJoinDate() { return lastDailyJoinDate; }
    public void setLastDailyJoinDate(String date) { this.lastDailyJoinDate = date; markDirty(); }

    public Map<String, Long> getGroupRewardLastClaim() { return groupRewardLastClaim; }
    public void setGroupRewardClaim(String group, long time) {
        groupRewardLastClaim.put(group, time);
        markDirty();
    }

    public Map<String, Integer> getTaskProgress() { return taskProgress; }
    public int getProgress(String taskId) { return taskProgress.getOrDefault(taskId, 0); }
    public void setProgress(String taskId, int progress) {
        taskProgress.put(taskId, progress);
        markDirty();
    }
    public void incrementProgress(String taskId, int delta) {
        taskProgress.merge(taskId, delta, Integer::sum);
        markDirty();
    }

    public Set<String> getCompletedTasks() { return completedTasks; }
    public boolean isCompleted(String taskId) { return completedTasks.contains(taskId); }
    public void markCompleted(String taskId) { completedTasks.add(taskId); markDirty(); }

    public Set<String> getPendingClaim() { return pendingClaim; }
    public void addPendingClaim(String taskId) { pendingClaim.add(taskId); markDirty(); }
    public void removePendingClaim(String taskId) { pendingClaim.remove(taskId); markDirty(); }

    public Set<String> getDailyClaimedToday() { return dailyClaimedToday; }
    public boolean isDailyClaimedToday(String taskId) { return dailyClaimedToday.contains(taskId); }
    public void markDailyClaimedToday(String taskId) { dailyClaimedToday.add(taskId); markDirty(); }
    public void clearDailyClaimedToday() { dailyClaimedToday.clear(); }

    public boolean isDirty() { return dirty; }
    public void markDirty() { this.dirty = true; }
    public void clearDirty() { this.dirty = false; }
}
