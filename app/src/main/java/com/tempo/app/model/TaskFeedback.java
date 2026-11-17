package com.tempo.app.model;

public class TaskFeedback {

    private int feedbackId;          // primary key
    private int taskId;              // foreign key to tasks.task_id

    private int actualDuration;      // actual minutes
    private int actualDifficulty;    // felt difficulty 1-5

    private String completionStatus; // COMPLETED / PARTIAL / NOT_COMPLETED
    private long completedAt;        // saved at (ms)

    private String notes;            // optional notes

    public TaskFeedback() {
    }

    // ---- Getters / setters ----

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