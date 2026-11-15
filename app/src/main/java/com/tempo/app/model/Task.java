package com.tempo.app.model;

public class Task {

    private int taskId;              // 主键，数据库自增
    private String title;            // 任务名称
    private String description;      // 任务描述

    private String category;         // Study / Work / Life
    private String taskType;         // Assignment / Revision 等

    private int estimatedDuration;   // 用户预估时间（分钟）
    private int predictedDuration;   // 系统预测时间（分钟）—— D 模块用

    private int difficulty;          // 用户预估难度 1–5
    private int importance;          // 重要程度 1–5

    private long deadline;           // 截止时间，Unix timestamp（毫秒）
    private String status;           // PENDING / IN_PROGRESS / COMPLETED

    private long createdAt;          // 创建时间（毫秒）

    // 无参构造：DAO 从 Cursor 建对象时用这个，再逐个 set
    public Task() {
    }

    // 有参构造：方便快速创建新任务（status 和 createdAt 自动填好）
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
    // 在类内右键 → Generate → Getter and Setter → 全选 → OK 自动生成
    // 下面是生成后的样子，供你对照

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