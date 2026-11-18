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

    // Batch insert (used after C generates a plan) in one transaction; all-or-nothing
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
    public void replaceGeneratedForDate(String date, List<ScheduleItem> items) { replaceGeneratedForDate(date, items, Long.MIN_VALUE); }

    public void replaceGeneratedForDate(String date, List<ScheduleItem> items, long preserveBefore) {
        replaceGenerated(date, items, "scheduled_date = ? AND is_manually_edited = 0 AND status = ? AND start_time >= ?", preserveBefore);
    }

    /** Deletes automatic PLANNED slots that started before {@code keepFrom}; later ones stay. */
    public void replaceMissedGeneratedForDate(String date, List<ScheduleItem> items, long keepFrom) {
        replaceGenerated(date, items, "scheduled_date = ? AND is_manually_edited = 0 AND status = ? AND start_time < ?", keepFrom);
    }

    private void replaceGenerated(String date, List<ScheduleItem> items, String where, long time) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("schedule_items", where,
                    new String[]{date, "PLANNED", String.valueOf(time)});
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

    /** A retry may update remaining work only if the same amounts are still current. */
    public static final class RemainingConflict extends IllegalArgumentException {
        public final int remaining;
        public final int required;
        public RemainingConflict(int remaining, int required) {
            super("This task has " + remaining + " min remaining. Its locked slots would reserve "
                    + required + " min across the schedule.");
            this.remaining = remaining;
            this.required = required;
        }
    }

    /** Move a slot and optionally confirm a corresponding increase in remaining work atomically. */
    public int updatePlannedTime(int scheduleId, String date, long start, long end) {
        return updatePlannedTime(scheduleId, date, start, end, null);
    }

    public int updatePlannedTime(int scheduleId, String date, long start, long end, RemainingConflict confirmation) {
        if (scheduleId <= 0 || date == null || start >= end || start % 60_000L != 0 || end % 60_000L != 0) {
            throw new IllegalArgumentException("Invalid schedule time");
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try (Cursor current = db.query("schedule_items", null,
                "schedule_id = ? AND scheduled_date = ? AND status = ?",
                new String[]{String.valueOf(scheduleId), date, "PLANNED"}, null, null, null);
             Cursor conflict = db.query("schedule_items", new String[]{"schedule_id"},
                "scheduled_date = ? AND schedule_id <> ? AND status <> 'CANCELLED' AND start_time < ? AND end_time > ?",
                new String[]{date, String.valueOf(scheduleId), String.valueOf(end), String.valueOf(start)},
                null, null, null)) {
            com.tempo.app.model.UserProfile routine = new UserProfileDao(dbHelper).getProfile();
            if (routine != null) {
                for (com.tempo.app.scheduler.ScheduleGenerator.Interval block :
                        com.tempo.app.scheduler.FixedBlocks.intervals(routine.getFixedBlocks(), start)) {
                    if (start < block.end && block.start < end)
                        throw new IllegalArgumentException("This time overlaps a fixed weekly commitment.");
                }
            }
            if (!current.moveToFirst()) throw new IllegalArgumentException("Planned session not found");
            if (conflict.moveToFirst()) throw new IllegalArgumentException("Time overlaps another session");
            ScheduleItem edited = cursorToItem(current);
            int taskId = edited.getTaskId();
            com.tempo.app.model.Task task = new TaskDao(dbHelper).getTaskById(taskId);
            if (task == null || task.isArchived() || "COMPLETED".equals(task.getStatus()))
                throw new IllegalArgumentException("This task is no longer available for planning.");
            if (task.getDeadline() > 0 && end > task.getDeadline())
                throw new IllegalArgumentException("The end time cannot be after the task deadline.");
            long now = System.currentTimeMillis();
            int required = com.tempo.app.planning.ScheduleEditRules.lockedMinutes(
                    getScheduleByTaskId(taskId), edited, start, end, now);
            com.tempo.app.learning.LearningStore learning = new com.tempo.app.learning.LearningStore(dbHelper);
            int remaining = learning.editContext(taskId).remaining;
            if (required > remaining) {
                if (confirmation == null || confirmation.remaining != remaining || confirmation.required != required)
                    throw new RemainingConflict(remaining, required);
                learning.setPlanningRemaining(taskId, required, now);
            }
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

    // Removes every slot of a task before re-planning it
    public int deleteScheduleByTaskId(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("schedule_items", "task_id = ?",
                new String[]{String.valueOf(taskId)});
    }
}
