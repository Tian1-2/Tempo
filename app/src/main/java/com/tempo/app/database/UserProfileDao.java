package com.tempo.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tempo.app.model.UserProfile;

public class UserProfileDao {

    private final AppDatabaseHelper dbHelper;

    public UserProfileDao(AppDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    private UserProfile cursorToProfile(Cursor cursor) {
        UserProfile p = new UserProfile();
        p.setProfileId(cursor.getInt(cursor.getColumnIndexOrThrow("profile_id")));
        p.setWakeTime(cursor.getString(cursor.getColumnIndexOrThrow("wake_time")));
        p.setSleepTime(cursor.getString(cursor.getColumnIndexOrThrow("sleep_time")));
        p.setBreakfastTime(cursor.getString(cursor.getColumnIndexOrThrow("breakfast_time")));
        p.setLunchTime(cursor.getString(cursor.getColumnIndexOrThrow("lunch_time")));
        p.setDinnerTime(cursor.getString(cursor.getColumnIndexOrThrow("dinner_time")));
        p.setFocusDuration(cursor.getInt(cursor.getColumnIndexOrThrow("focus_duration")));
        p.setBreakDuration(cursor.getInt(cursor.getColumnIndexOrThrow("break_duration")));
        p.setDailyCapacity(cursor.getInt(cursor.getColumnIndexOrThrow("daily_capacity")));
        p.setPreferredStartTime(cursor.getString(cursor.getColumnIndexOrThrow("preferred_start_time")));
        return p;
    }

    public UserProfile getProfile() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("user_profile", null,
                null, null, null, null, "profile_id ASC", "1")) {
            if (cursor.moveToFirst()) {
                return cursorToProfile(cursor);
            }
        }
        return null;
    }

    public long saveProfile(UserProfile p) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("wake_time", p.getWakeTime());
        values.put("sleep_time", p.getSleepTime());
        values.put("breakfast_time", p.getBreakfastTime());
        values.put("lunch_time", p.getLunchTime());
        values.put("dinner_time", p.getDinnerTime());
        values.put("focus_duration", p.getFocusDuration());
        values.put("break_duration", p.getBreakDuration());
        values.put("daily_capacity", p.getDailyCapacity());
        values.put("preferred_start_time", p.getPreferredStartTime());

        UserProfile existing = getProfile();
        if (existing == null) {
            return db.insert("user_profile", null, values);
        } else {
            return db.update("user_profile", values,
                    "profile_id = ?", new String[]{String.valueOf(existing.getProfileId())});
        }
    }
}