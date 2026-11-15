package com.tempo.app.model;

public class TaskFeedback {

    private int feedbackId;          // 主键
    private int taskId;              // 外键，对应 tasks.task_id

    private int actualDuration;      // 实际耗时（分钟）
    private int actualDifficulty;    // 实际体感难度 1–5

    private String completionStatus; // COMPLETED / PARTIAL / NOT_COMPLETED
    private long completedAt;        // 完成时间（毫秒）

    private String notes;            // 可选备注

    public TaskFeedback() {
    }

    // ---- Getter / Setter（Generate 自动生成）----

    public int getFeedbackId() { return feedbackId; }
    public void setFeedbackId(int feedbackId) { this.feedbackId = feedbackId; }

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }

    public int getActualDuration() { return actualDuration; }
    public void setActualDuration(int actualDuration) { this.actualDuration = actualDuration; }

    public int getActualDifficulty() { return actualDifficulty; }
    public void setActualDifficulty(int actualDifficulty) { this.actualDifficulty = actualDifficulty; }

    public String getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(String completionStatus) { this.completionStatus = completionStatus; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}