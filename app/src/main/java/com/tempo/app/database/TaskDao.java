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
        task.setArchived(cursor.getInt(cursor.getColumnIndexOrThrow("archived")) != 0);
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
    public List<Task> getAllTasks() { return listTasks(false); }

    public List<Task> getTasksWithHistory() { return listTasks(true); }

    private List<Task> listTasks(boolean includeArchived) {
        List<Task> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("tasks", null, includeArchived ? null : "archived=0", null,
                null, null, "deadline ASC")) {
            while (cursor.moveToNext()) {
                tasks.add(cursorToTask(cursor));
            }
        }
        return tasks;
    }

    public List<Task> getTasksForManagement(long now) {
        List<Task> tasks = new ArrayList<>();
        String sql = "SELECT t.*, EXISTS(SELECT 1 FROM schedule_items s WHERE s.task_id=t.task_id "
                + "AND s.status='PLANNED' AND s.end_time>?) AS future_slot FROM tasks t "
                + "ORDER BY t.archived, future_slot, t.title COLLATE NOCASE";
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(now)})) {
            while (c.moveToNext()) {
                Task t = cursorToTask(c);
                t.setHasFutureSlot(c.getInt(c.getColumnIndexOrThrow("future_slot")) != 0);
                tasks.add(t);
            }
        }
        return tasks;
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
                "archived=0 AND status IN (?, ?)", new String[]{"PENDING", "IN_PROGRESS"},
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
                "task_id = ? AND archived=0", new String[]{String.valueOf(task.getTaskId())});
    }

    // ---------- DELETE ----------
    public int deleteTask(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String[] id = {String.valueOf(taskId)};
        db.beginTransaction();
        try {
            db.delete("schedule_items", "task_id=?", id);
            db.delete("task_feedback", "task_id=?", id);
            int changed = db.delete("tasks", "task_id=?", id);
            if (changed > 0) new com.tempo.app.learning.LearningStore(dbHelper).refreshPredictions(System.currentTimeMillis());
            db.setTransactionSuccessful();
            return changed;
        } finally { db.endTransaction(); }
    }

    public int restoreTask(int taskId) {
        ContentValues values = new ContentValues();
        values.put("archived", 0);
        return dbHelper.getWritableDatabase().update("tasks", values, "task_id=? AND archived=1",
                new String[]{String.valueOf(taskId)});
    }

    /** Hide a task and release its slots while retaining feedback and learning history. */
    public int archiveTask(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues task = new ContentValues();
            task.put("archived", 1);
            int changed = db.update("tasks", task, "task_id=? AND archived=0",
                    new String[]{String.valueOf(taskId)});
            if (changed > 0) {
                ContentValues slot = new ContentValues();
                slot.put("status", "CANCELLED");
                long now = System.currentTimeMillis();
                String id = String.valueOf(taskId);
                db.update("schedule_items", slot, "task_id=? AND status='PLANNED' AND start_time>=?",
                        new String[]{id, String.valueOf(now)});
                // Keep elapsed plans for daily review, including the elapsed portion of an open slot.
                long boundary = Math.floorDiv(now + 59_999L, 60_000L) * 60_000L;
                ContentValues shortened = new ContentValues();
                shortened.put("end_time", boundary);
                db.update("schedule_items", shortened, "task_id=? AND status='PLANNED' AND end_time>?",
                        new String[]{id, String.valueOf(boundary)});
                slot.put("status", com.tempo.app.model.ScheduleItem.PAST_PLAN);
                db.update("schedule_items", slot, "task_id=? AND status='PLANNED'", new String[]{id});
            }
            db.setTransactionSuccessful();
            return changed;
        } finally { db.endTransaction(); }
    }
}
