package com.tempo.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.tempo.app.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskDao {

    private final AppDatabaseHelper dbHelper;

    public TaskDao(AppDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    // ---------- 私有工具：Cursor → Task（唯一转换入口）----------
    private Task cursorToTask(Cursor cursor) {
        Task task = new Task();
        task.setTaskId(cursor.getInt(cursor.getColumnIndexOrThrow("task_id")));
        task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow("title")));
        task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow("description")));
        task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow("category")));
        task.setTaskType(cursor.getString(cursor.getColumnIndexOrThrow("task_type")));
        task.setEstimatedDuration(cursor.getInt(cursor.getColumnIndexOrThrow("estimated_duration")));
        task.setPredictedDuration(cursor.getInt(cursor.getColumnIndexOrThrow("predicted_duration")));
        task.setDifficulty(cursor.getInt(cursor.getColumnIndexOrThrow("difficulty")));
        task.setImportance(cursor.getInt(cursor.getColumnIndexOrThrow("importance")));
        task.setDeadline(cursor.getLong(cursor.getColumnIndexOrThrow("deadline")));
        task.setStatus(cursor.getString(cursor.getColumnIndexOrThrow("status")));
        task.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
        return task;
    }

    // ---------- CREATE ----------
    public long insertTask(Task task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", task.getTitle());
        values.put("description", task.getDescription());
        values.put("category", task.getCategory());
        values.put("task_type", task.getTaskType());
        values.put("estimated_duration", task.getEstimatedDuration());
        values.put("predicted_duration", task.getPredictedDuration());
        values.put("difficulty", task.getDifficulty());
        values.put("importance", task.getImportance());
        values.put("deadline", task.getDeadline());
        values.put("status", task.getStatus());
        values.put("created_at", task.getCreatedAt());

        long id = db.insert("tasks", null, values);
        Log.d("TaskDao", "insertTask id=" + id);
        return id;
    }

    // ---------- READ: 全部 ----------
    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", null, null, null,
                null, null, "deadline ASC")) {
            while (cursor.moveToNext()) {
                tasks.add(cursorToTask(cursor));
            }
        }
        return tasks;
    }

    // ---------- READ: 单条 ----------
    public Task getTaskById(int taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", null,
                "task_id = ?", new String[]{String.valueOf(taskId)},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursorToTask(cursor);
            }
        }
        return null;
    }

    // ---------- READ: 待完成任务（C 模块最常用）----------
    public List<Task> getPendingTasks() {
        List<Task> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", null,
                "status IN (?, ?)", new String[]{"PENDING", "IN_PROGRESS"},
                null, null, "deadline ASC")) {
            while (cursor.moveToNext()) {
                tasks.add(cursorToTask(cursor));
            }
        }
        return tasks;
    }

    // ---------- UPDATE ----------
    public int updateTask(Task task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", task.getTitle());
        values.put("description", task.getDescription());
        values.put("category", task.getCategory());
        values.put("task_type", task.getTaskType());
        values.put("estimated_duration", task.getEstimatedDuration());
        values.put("predicted_duration", task.getPredictedDuration());
        values.put("difficulty", task.getDifficulty());
        values.put("importance", task.getImportance());
        values.put("deadline", task.getDeadline());
        values.put("status", task.getStatus());
        // 注意：created_at 不在 update 里，创建时间不该被改
        return db.update("tasks", values,
                "task_id = ?", new String[]{String.valueOf(task.getTaskId())});
    }

    // ---------- DELETE ----------
    public int deleteTask(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("tasks", "task_id = ?",
                new String[]{String.valueOf(taskId)});
    }
}
