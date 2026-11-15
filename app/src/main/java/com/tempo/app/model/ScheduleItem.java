package com.tempo.app.model;

public class ScheduleItem {

    private int scheduleId;          // 主键
    private int taskId;              // 外键，对应 tasks.task_id

    private String scheduledDate;    // 日期，如 "2026-11-12"

    private long startTime;          // 开始时间（毫秒 timestamp）
    private long endTime;            // 结束时间（毫秒 timestamp）

    private boolean manuallyEdited;  // 用户是否手动改过
    private String status;           // PLANNED / COMPLETED / SKIPPED

    public ScheduleItem() {
    }

    // ---- Getter / Setter（Generate 自动生成）----

    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }

    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public boolean isManuallyEdited() { return manuallyEdited; }
    public void setManuallyEdited(boolean manuallyEdited) { this.manuallyEdited = manuallyEdited; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}