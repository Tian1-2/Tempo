package com.tempo.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tempo.app.scheduler.FixedBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Weekly fixed commitments (classes, meetings), one row each, since database V8. Screens still
 * exchange them as {@link FixedBlocks} text through {@link UserProfileDao}; this class converts
 * between that text and the table, so the stored data is typed and checked by SQLite.
 */
public final class FixedCommitmentDao {
    private FixedCommitmentDao() {}

    public static void createTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fixed_commitments ("
                + "commitment_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "day_of_week INTEGER NOT NULL CHECK(day_of_week BETWEEN 0 AND 6), " // 0 = Sunday
                + "start_minute INTEGER NOT NULL CHECK(start_minute >= 0), "
                + "end_minute INTEGER NOT NULL CHECK(end_minute <= 1440 AND end_minute > start_minute), "
                + "title TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fixed_day ON fixed_commitments(day_of_week, start_minute)");
    }

    /** All commitments as FixedBlocks text (empty string when there are none). */
    public static String read(SQLiteDatabase db) {
        List<String> rows = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT day_of_week, start_minute, end_minute, title FROM fixed_commitments "
                + "ORDER BY commitment_id", null)) { // the order the user entered them; screens sort for display
            while (c.moveToNext()) rows.add(FixedBlocks.row(c.getInt(0), c.getInt(1), c.getInt(2), c.getString(3)));
        }
        return FixedBlocks.join(rows);
    }

    /** Replaces every commitment with the ones in {@code text}. Call inside the caller's transaction. */
    public static void replace(SQLiteDatabase db, String text) {
        List<FixedBlocks.Entry> entries = new ArrayList<>();
        // Line by line, so the stored order is the order the user entered (entries() sorts).
        for (String line : FixedBlocks.lines(text)) entries.addAll(FixedBlocks.entries(line));
        db.delete("fixed_commitments", null, null);
        for (FixedBlocks.Entry e : entries) {
            ContentValues values = new ContentValues();
            values.put("day_of_week", e.day);
            values.put("start_minute", e.start);
            values.put("end_minute", e.end);
            values.put("title", e.name);
            db.insertOrThrow("fixed_commitments", null, values);
        }
    }

    /** V8 upgrade: move text kept in user_profile.fixed_blocks into the table, then clear the column. */
    public static void moveFromProfile(SQLiteDatabase db) {
        createTable(db);
        String text = null;
        try (Cursor c = db.rawQuery("SELECT fixed_blocks FROM user_profile ORDER BY profile_id LIMIT 1", null)) {
            if (c.moveToFirst() && !c.isNull(0)) text = c.getString(0);
        }
        if (text == null || text.trim().isEmpty()) return;
        String existing = read(db);
        // Running the upgrade twice must not duplicate rows.
        replace(db, existing.isEmpty() ? text : existing + "\n" + text);
        ContentValues cleared = new ContentValues();
        cleared.putNull("fixed_blocks");
        db.update("user_profile", cleared, null, null);
    }
}
