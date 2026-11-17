package com.tempo.app.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tempo.db";
    private static final int DATABASE_VERSION = 3;

    public AppDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // 开启外键约束（SQLite 默认不强制，必须显式打开）
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // 1. tasks 表
        db.execSQL(
                "CREATE TABLE tasks (" +
                        "task_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "title TEXT NOT NULL, " +
                        "description TEXT, " +
                        "category TEXT NOT NULL, " +
                        "task_type TEXT, " +
                        "estimated_duration INTEGER, " +
                        "predicted_duration INTEGER, " +
                        "difficulty INTEGER, " +
                        "importance INTEGER, " +
                        "deadline INTEGER, " +
                        "status TEXT NOT NULL DEFAULT 'PENDING', " +
                        "created_at INTEGER" +
                        ")"
        );

        // 2. task_feedback 表
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

        // 3. user_profile 表
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
                        "preferred_start_time TEXT" +
                        ")"
        );

        // 4. schedule_items 表
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
    }
}
