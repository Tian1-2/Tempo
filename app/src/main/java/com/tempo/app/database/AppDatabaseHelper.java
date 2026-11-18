package com.tempo.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.tempo.app.scheduler.FixedBlocks;

public class AppDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tempo.db";
    /**
     * V6 joins two branches that both used version 4: task-a (V4 fixed_events table, V5 categories)
     * and integration (V4 tasks.archived + user_profile.fixed_blocks). Upgrades therefore check the
     * actual schema instead of trusting the version number.
     */
    private static final int DATABASE_VERSION = 8; // V7: indexes; V8: fixed_commitments table

    public AppDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Enable foreign keys (SQLite does not enforce them unless asked)
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // 1. tasks
        db.execSQL(
                "CREATE TABLE tasks (" +
                        "task_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "title TEXT NOT NULL, " +
                        "description TEXT, " +
                        "category TEXT NOT NULL, " +
                        "task_type TEXT, " +
                        "archived INTEGER NOT NULL DEFAULT 0, " +
                        "estimated_duration INTEGER, " +
                        "predicted_duration INTEGER, " +
                        "difficulty INTEGER, " +
                        "importance INTEGER, " +
                        "deadline INTEGER, " +
                        "status TEXT NOT NULL DEFAULT 'PENDING', " +
                        "created_at INTEGER" +
                        ")"
        );

        // 2. task_feedback
        db.execSQL(
                "CREATE TABLE task_feedback (" +
                        "feedback_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "task_id INTEGER NOT NULL, " +
                        "actual_duration INTEGER, " +
                        "actual_difficulty INTEGER, " +
                        "completion_status TEXT, " +
                        "completed_at INTEGER, " +
                        "notes TEXT, " +
                        "FOREIGN KEY(task_id) REFERENCES tasks(task_id)" +
                        ")"
        );

        // 3. user_profile
        db.execSQL(
                "CREATE TABLE user_profile (" +
                        "profile_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "wake_time TEXT, " +
                        "sleep_time TEXT, " +
                        "breakfast_time TEXT, " +
                        "lunch_time TEXT, " +
                        "dinner_time TEXT, " +
                        "focus_duration INTEGER, " +
                        "break_duration INTEGER, " +
                        "daily_capacity INTEGER, " +
                        "fixed_blocks TEXT, " +
                        "preferred_start_time TEXT" +
                        ")"
        );

        // 4. schedule_items
        db.execSQL(
                "CREATE TABLE schedule_items (" +
                        "schedule_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "task_id INTEGER NOT NULL, " +
                        "scheduled_date TEXT NOT NULL, " +
                        "start_time INTEGER NOT NULL, " +
                        "end_time INTEGER NOT NULL, " +
                        "is_manually_edited INTEGER DEFAULT 0, " +
                        "status TEXT DEFAULT 'PLANNED', " +
                        "FOREIGN KEY(task_id) REFERENCES tasks(task_id)" +
                        ")"
        );
        com.tempo.app.learning.LearningStore.createTables(db);
        CategoryDao.createTable(db);
        FixedCommitmentDao.createTable(db);
        createIndexes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) com.tempo.app.learning.LearningStore.upgradeTables(db);
        if (!hasColumn(db, "tasks", "archived")) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        }
        if (!hasColumn(db, "user_profile", "fixed_blocks")) {
            db.execSQL("ALTER TABLE user_profile ADD COLUMN fixed_blocks TEXT");
        }
        // Older builds stored a deleted task as status DELETED. Deleting is now archiving: keep the
        // task's real outcome and mark it archived. Recover only documented outcomes.
        db.execSQL("UPDATE tasks SET archived=1, status=CASE "
                + "WHEN EXISTS(SELECT 1 FROM task_feedback f WHERE f.task_id=tasks.task_id AND f.completion_status='COMPLETED') THEN 'COMPLETED' "
                + "WHEN EXISTS(SELECT 1 FROM task_feedback f WHERE f.task_id=tasks.task_id) THEN 'IN_PROGRESS' "
                + "ELSE 'PENDING' END WHERE status='DELETED'");
        CategoryDao.createTable(db); // custom categories and colours (IF NOT EXISTS)
        moveFixedEventsToProfile(db);
        FixedCommitmentDao.moveFromProfile(db); // V8: profile text → fixed_commitments rows
        createIndexes(db);
    }

    /** V7: the schedule, Home and Insights look rows up by date, task and time; avoid full scans. */
    static void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_date_status ON schedule_items(scheduled_date, status)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_task ON schedule_items(task_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_task ON task_feedback(task_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_completed ON task_feedback(completed_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_archived_status ON tasks(archived, status)");
    }

    /** task-a V4/V5 kept weekly commitments in a fixed_events table; they now live in the profile. */
    public static void moveFixedEventsToProfile(SQLiteDatabase db) {
        boolean exists;
        try (Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='fixed_events'", null)) {
            exists = c.moveToFirst();
        }
        if (!exists) return;
        StringBuilder rows = new StringBuilder();
        try (Cursor c = db.rawQuery("SELECT title, day_of_week, start_minute, end_minute FROM fixed_events "
                + "ORDER BY day_of_week, start_minute, event_id", null)) {
            while (c.moveToNext()) {
                // fixed_events: ISO day 1 = Monday … 7 = Sunday; FixedBlocks.DAYS starts with Sunday.
                String day = FixedBlocks.DAYS[c.getInt(1) % 7];
                String title = c.getString(0) == null ? "" : c.getString(0).replaceAll("[\\t\\r\\n]", " ").trim();
                if (title.length() > 60) title = title.substring(0, 60);
                if (rows.length() > 0) rows.append('\n');
                rows.append(day).append(' ').append(FixedBlocks.clock(c.getInt(2))).append('-')
                        .append(FixedBlocks.clock(c.getInt(3)));
                if (!title.isEmpty()) rows.append('\t').append(title);
            }
        }
        if (rows.length() > 0) {
            // Append to whatever the profile already has; with no profile yet there is nowhere to put them.
            java.util.Map<Integer, String> profiles = new java.util.HashMap<>();
            try (Cursor c = db.rawQuery("SELECT profile_id, fixed_blocks FROM user_profile", null)) {
                while (c.moveToNext()) profiles.put(c.getInt(0), c.isNull(1) ? "" : c.getString(1));
            }
            for (java.util.Map.Entry<Integer, String> profile : profiles.entrySet()) {
                String existing = profile.getValue().trim();
                ContentValues values = new ContentValues();
                values.put("fixed_blocks", existing.isEmpty() ? rows.toString() : existing + "\n" + rows);
                db.update("user_profile", values, "profile_id = ?", new String[]{String.valueOf(profile.getKey())});
            }
        }
        db.execSQL("DROP TABLE fixed_events");
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) if (column.equalsIgnoreCase(c.getString(1))) return true;
        }
        return false;
    }
}
