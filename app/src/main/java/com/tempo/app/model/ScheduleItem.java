package com.tempo.app.model;

public class ScheduleItem {

    private int scheduleId;          // primary key
    private int taskId;              // foreign key to tasks.task_id

    private String scheduledDate;    // date, e.g. "2026-11-12"

    private long startTime;          // start (ms timestamp)
    private long endTime;            // end (ms timestamp)

    private boolean manuallyEdited;  // whether the user adjusted it
    private String status;           // PLANNED / COMPLETED / SKIPPED

    public ScheduleItem() {
    }

    // ---- Getters / setters ----

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