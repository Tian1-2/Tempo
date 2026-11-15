package com.tempo.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tempo.app.model.TaskFeedback;

import java.util.ArrayList;
import java.util.List;

public class TaskFeedbackDao {

    private final AppDatabaseHelper dbHelper;

    public TaskFeedbackDao(AppDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    private TaskFeedback cursorToFeedback(Cursor cursor) {
        TaskFeedback fb = new TaskFeedback();
        fb.setFeedbackId(cursor.getInt(cursor.getColumnIndexOrThrow("feedback_id")));
        fb.setTaskId(cursor.getInt(cursor.getColumnIndexOrThrow("task_id")));
        fb.setActualDuration(cursor.getInt(cursor.getColumnIndexOrThrow("actual_duration")));
        fb.setActualDifficulty(cursor.getInt(cursor.getColumnIndexOrThrow("actual_difficulty")));
        fb.setCompletionStatus(cursor.getString(cursor.getColumnIndexOrThrow("completion_status")));
        fb.setCompletedAt(cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")));
        fb.setNotes(cursor.getString(cursor.getColumnIndexOrThrow("notes")));
        return fb;
    }

    public long insertFeedback(TaskFeedback fb) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("task_id", fb.getTaskId());
        values.put("actual_duration", fb.getActualDuration());
        values.put("actual_difficulty", fb.getActualDifficulty());
        values.put("completion_status", fb.getCompletionStatus());
        values.put("completed_at", fb.getCompletedAt());
        values.put("notes", fb.getNotes());
        return db.insert("task_feedback", null, values);
    }

    public List<TaskFeedback> getFeedbackByTaskId(int taskId) {
        List<TaskFeedback> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("task_feedback", null,
                "task_id = ?", new String[]{String.valueOf(taskId)},
                null, null, "completed_at DESC")) {
            while (cursor.moveToNext()) {
                list.add(cursorToFeedback(cursor));
            }
        }
        return list;
    }

    public List<TaskFeedback> getAllFeedback() {
        List<TaskFeedback> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("task_feedback", null,
                null, null, null, null, "completed_at DESC")) {
            while (cursor.moveToNext()) {
                list.add(cursorToFeedback(cursor));
            }
        }
        return list;
    }
}