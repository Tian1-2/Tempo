package com.tempo.app.model;

public class UserProfile {

    private int profileId;           // always 1 (single user)

    private String wakeTime;         // "07:00"
    private String sleepTime;        // "23:30"

    private String breakfastTime;    // "08:00"
    private String lunchTime;        // "12:30"
    private String dinnerTime;       // "18:30"

    private int focusDuration;       // focus block length in minutes, e.g. 25
    private int breakDuration;       // break length in minutes, e.g. 5
    private int dailyCapacity;       // daily task time in minutes, e.g. 240

    private String preferredStartTime; // preferred focus start, e.g. "09:00"

    public UserProfile() {
    }

    // ---- Getters / setters ----

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