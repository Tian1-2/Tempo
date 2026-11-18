package com.tempo.app.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tempo.db";
    private static final int DATABASE_VERSION = 4;

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) com.tempo.app.learning.LearningStore.upgradeTables(db);
        if (oldVersion < 4 && newVersion >= 4) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE user_profile ADD COLUMN fixed_blocks TEXT");
            // V3 optimization stored DELETED in the status column. Recover only documented outcomes.
            db.execSQL("UPDATE tasks SET archived=1, status=CASE "
                    + "WHEN EXISTS(SELECT 1 FROM task_feedback f WHERE f.task_id=tasks.task_id AND f.completion_status='COMPLETED') THEN 'COMPLETED' "
                    + "WHEN EXISTS(SELECT 1 FROM task_feedback f WHERE f.task_id=tasks.task_id) THEN 'IN_PROGRESS' "
                    + "ELSE 'PENDING' END WHERE status='DELETED'");
        }
    }
}
