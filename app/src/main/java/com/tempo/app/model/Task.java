package com.tempo.app.model;

public class Task {
    private boolean hasFutureSlot;
    public boolean hasFutureSlot() { return hasFutureSlot; }
    public void setHasFutureSlot(boolean value) { hasFutureSlot = value; }

    private boolean archived;
    public boolean isArchived() { return archived; }
    public void setArchived(boolean value) { archived = value; }


    private int taskId;              // primary key, auto-increment
    private String title;            // task name
    private String description;      // description

    private String category;         // Study / Work / Life
    private String taskType;         // e.g. Assignment / Revision

    private int estimatedDuration;   // user estimate (min)
    private int predictedDuration;   // learned prediction (min), written by D

    private int difficulty;          // expected difficulty 1-5
    private int importance;          // importance 1-5

    private long deadline;           // deadline, Unix ms (0 = none)
    private String status;           // PENDING / IN_PROGRESS / COMPLETED

    private long createdAt;          // created at (ms)

    // Empty constructor: used by the DAO when reading a cursor
    public Task() {
    }

    // Convenience constructor for new tasks (status and createdAt filled in)
    public Task(String title, String category,
                int estimatedDuration, int difficulty,
                int importance, long deadline) {
        this.title = title;
        this.category = category;
        this.estimatedDuration = estimatedDuration;
        this.difficulty = difficulty;
        this.importance = importance;
        this.deadline = deadline;
        this.status = "PENDING";
        this.createdAt = System.currentTimeMillis();
    }

    // ---- Getter / Setter ----
    // Getters and setters

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public int getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(int estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public int getPredictedDuration() { return predictedDuration; }
    public void setPredictedDuration(int predictedDuration) { this.predictedDuration = predictedDuration; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public int getImportance() { return importance; }
    public void setImportance(int importance) { this.importance = importance; }

    public long getDeadline() { return deadline; }
    public void setDeadline(long deadline) { this.deadline = deadline; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}