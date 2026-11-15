package com.tempo.app.model;

public class UserProfile {

    private int profileId;           // 第一版固定 1

    private String wakeTime;         // "07:00"
    private String sleepTime;        // "23:30"

    private String breakfastTime;    // "08:00"
    private String lunchTime;        // "12:30"
    private String dinnerTime;       // "18:30"

    private int focusDuration;       // 单次专注时长（分钟），如 25
    private int breakDuration;       // 休息时长（分钟），如 5
    private int dailyCapacity;       // 每日可用时间（分钟），如 240

    private String preferredStartTime; // 偏好开始工作时间，如 "09:00"

    public UserProfile() {
    }

    // ---- Getter / Setter（Generate 自动生成）----

    public int getProfileId() { return profileId; }
    public void setProfileId(int profileId) { this.profileId = profileId; }

    public String getWakeTime() { return wakeTime; }
    public void setWakeTime(String wakeTime) { this.wakeTime = wakeTime; }

    public String getSleepTime() { return sleepTime; }
    public void setSleepTime(String sleepTime) { this.sleepTime = sleepTime; }

    public String getBreakfastTime() { return breakfastTime; }
    public void setBreakfastTime(String breakfastTime) { this.breakfastTime = breakfastTime; }

    public String getLunchTime() { return lunchTime; }
    public void setLunchTime(String lunchTime) { this.lunchTime = lunchTime; }

    public String getDinnerTime() { return dinnerTime; }
    public void setDinnerTime(String dinnerTime) { this.dinnerTime = dinnerTime; }

    public int getFocusDuration() { return focusDuration; }
    public void setFocusDuration(int focusDuration) { this.focusDuration = focusDuration; }

    public int getBreakDuration() { return breakDuration; }
    public void setBreakDuration(int breakDuration) { this.breakDuration = breakDuration; }

    public int getDailyCapacity() { return dailyCapacity; }
    public void setDailyCapacity(int dailyCapacity) { this.dailyCapacity = dailyCapacity; }

    public String getPreferredStartTime() { return preferredStartTime; }
    public void setPreferredStartTime(String preferredStartTime) { this.preferredStartTime = preferredStartTime; }
}