package com.tempo.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tempo.app.model.ScheduleItem;

import java.util.ArrayList;
import java.util.List;

public class ScheduleDao {

    private final AppDatabaseHelper dbHelper;

    public ScheduleDao(AppDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    private ScheduleItem cursorToItem(Cursor cursor) {
        ScheduleItem item = new ScheduleItem();
        item.setScheduleId(cursor.getInt(cursor.getColumnIndexOrThrow("schedule_id")));
        item.setTaskId(cursor.getInt(cursor.getColumnIndexOrThrow("task_id")));
        item.setScheduledDate(cursor.getString(cursor.getColumnIndexOrThrow("scheduled_date")));
        item.setStartTime(cursor.getLong(cursor.getColumnIndexOrThrow("start_time")));
        item.setEndTime(cursor.getLong(cursor.getColumnIndexOrThrow("end_time")));
        item.setManuallyEdited(cursor.getInt(cursor.getColumnIndexOrThrow("is_manually_edited")) == 1);
        item.setStatus(cursor.getString(cursor.getColumnIndexOrThrow("status")));
        return item;
    }

    public long insertScheduleItem(ScheduleItem item) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("task_id", item.getTaskId());
        values.put("scheduled_date", item.getScheduledDate());
        values.put("start_time", item.getStartTime());
        values.put("end_time", item.getEndTime());
        values.put("is_manually_edited", item.isManuallyEdited() ? 1 : 0);
        values.put("status", item.getStatus());
        return db.insert("schedule_items", null, values);
    }

    // 批量插入（C 生成排程后调用），放在一个事务里，失败整体回滚
    public void insertScheduleItems(List<ScheduleItem> items) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (ScheduleItem item : items) {
                ContentValues values = new ContentValues();
                values.put("task_id", item.getTaskId());
                values.put("scheduled_date", item.getScheduledDate());
                values.put("start_time", item.getStartTime());
                values.put("end_time", item.getEndTime());
                values.put("is_manually_edited", item.isManuallyEdited() ? 1 : 0);
                values.put("status", item.getStatus());
                db.insert("schedule_items", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Replaces only generated planned rows for one date; manual and completed rows survive. */
    public void replaceGeneratedForDate(String date, List<ScheduleItem> items) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("schedule_items", "scheduled_date = ? AND is_manually_edited = 0 AND status = ?",
                    new String[]{date, "PLANNED"});
            for (ScheduleItem item : items) {
                if (!date.equals(item.getScheduledDate()) || item.isManuallyEdited() || !"PLANNED".equals(item.getStatus())) {
                    throw new IllegalArgumentException("Expected generated PLANNED item for " + date);
                }
                ContentValues values = new ContentValues();
                values.put("task_id", item.getTaskId());
                values.put("scheduled_date", item.getScheduledDate());
                values.put("start_time", item.getStartTime());
                values.put("end_time", item.getEndTime());
                values.put("is_manually_edited", 0);
                values.put("status", "PLANNED");
                if (db.insertOrThrow("schedule_items", null, values) < 0) {
                    throw new IllegalStateException("Failed to insert schedule item");
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<ScheduleItem> getScheduleByDate(String date) {
        List<ScheduleItem> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("schedule_items", null,
                "scheduled_date = ?", new String[]{date},
                null, null, "start_time ASC")) {
            while (cursor.moveToNext()) {
                list.add(cursorToItem(cursor));
            }
        }
        return list;
    }

    public List<ScheduleItem> getScheduleByTaskId(int taskId) {
        List<ScheduleItem> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("schedule_items", null,
                "task_id = ?", new String[]{String.valueOf(taskId)},
                null, null, "start_time ASC")) {
            while (cursor.moveToNext()) {
                list.add(cursorToItem(cursor));
            }
        }
        return list;
    }

    /** Move one planned session, rejecting overlaps with any other saved session that day. */
    public int updatePlannedTime(int scheduleId, String date, long start, long end) {
        if (scheduleId <= 0 || date == null || start >= end || start % 60_000L != 0 || end % 60_000L != 0) {
            throw new IllegalArgumentException("Invalid schedule time");
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try (Cursor current = db.query("schedule_items", new String[]{"schedule_id"},
                "schedule_id = ? AND scheduled_date = ? AND status = ?",
                new String[]{String.valueOf(scheduleId), date, "PLANNED"}, null, null, null);
             Cursor conflict = db.query("schedule_items", new String[]{"schedule_id"},
                "scheduled_date = ? AND schedule_id <> ? AND start_time < ? AND end_time > ?",
                new String[]{date, String.valueOf(scheduleId), String.valueOf(end), String.valueOf(start)},
                null, null, null)) {
            if (!current.moveToFirst()) throw new IllegalArgumentException("Planned session not found");
            if (conflict.moveToFirst()) throw new IllegalArgumentException("Time overlaps another session");
            ContentValues values = new ContentValues();
            values.put("start_time", start);
            values.put("end_time", end);
            values.put("is_manually_edited", 1);
            int changed = db.update("schedule_items", values, "schedule_id = ?",
                    new String[]{String.valueOf(scheduleId)});
            db.setTransactionSuccessful();
            return changed;
        } finally {
            db.endTransaction();
        }
    }

    // 重排前清掉某任务的所有旧排程
    public int deleteScheduleByTaskId(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("schedule_items", "task_id = ?",
                new String[]{String.valueOf(taskId)});
    }
}
