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

    // ---------- Cursor -> Task (the single conversion point) ----------
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

    // ---------- READ: all ----------
    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", null, "status <> ?", new String[]{"DELETED"},
                null, null, "deadline ASC")) {
            while (cursor.moveToNext()) {
                tasks.add(cursorToTask(cursor));
            }
        }
        return tasks;
    }

    /** Every task including deleted ones, for history views that must still name and colour old records. */
    public List<Task> getAllTasksIncludingDeleted() {
        List<Task> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", null, null, null, null, null, "task_id ASC")) {
            while (cursor.moveToNext()) list.add(cursorToTask(cursor));
        }
        return list;
    }

    // ---------- READ: one ----------
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

    // ---------- READ: pending and in-progress tasks (used by the scheduler) ----------
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
        // created_at is intentionally not updated
        return db.update("tasks", values,
                "task_id = ?", new String[]{String.valueOf(task.getTaskId())});
    }

    // ---------- DELETE ----------
    public int deleteTask(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("tasks", "task_id = ?",
                new String[]{String.valueOf(taskId)});
    }

    /** Hide a task and release its slots while retaining feedback and learning history. */
    public int archiveTask(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues task = new ContentValues();
            task.put("status", "DELETED");
            int changed = db.update("tasks", task, "task_id=? AND status<>?",
                    new String[]{String.valueOf(taskId), "DELETED"});
            if (changed > 0) {
                ContentValues slot = new ContentValues();
                slot.put("status", "CANCELLED");
                db.update("schedule_items", slot, "task_id=? AND status='PLANNED'",
                        new String[]{String.valueOf(taskId)});
            }
            db.setTransactionSuccessful();
            return changed;
        } finally { db.endTransaction(); }
    }
}
