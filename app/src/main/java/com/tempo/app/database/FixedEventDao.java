package com.tempo.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tempo.app.model.FixedEvent;

import java.util.ArrayList;
import java.util.List;

/** Weekly fixed commitments (classes, meetings). Call off the UI thread. */
public class FixedEventDao {
    private final AppDatabaseHelper dbHelper;

    public FixedEventDao(AppDatabaseHelper dbHelper) { this.dbHelper = dbHelper; }

    public static void createTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fixed_events ("
                + "event_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "day_of_week INTEGER NOT NULL CHECK(day_of_week BETWEEN 1 AND 7), "
                + "start_minute INTEGER NOT NULL CHECK(start_minute >= 0), "
                + "end_minute INTEGER NOT NULL CHECK(end_minute <= 1440 AND end_minute > start_minute))");
    }

    public long insert(FixedEvent event) {
        String problem = FixedEvent.validate(event.getTitle(), event.getDayOfWeek(),
                event.getStartMinute(), event.getEndMinute());
        if (problem != null) throw new IllegalArgumentException(problem);
        ContentValues values = new ContentValues();
        values.put("title", event.getTitle().trim());
        values.put("day_of_week", event.getDayOfWeek());
        values.put("start_minute", event.getStartMinute());
        values.put("end_minute", event.getEndMinute());
        return dbHelper.getWritableDatabase().insertOrThrow("fixed_events", null, values);
    }

    public int delete(int eventId) {
        return dbHelper.getWritableDatabase().delete("fixed_events", "event_id = ?",
                new String[]{String.valueOf(eventId)});
    }

    public List<FixedEvent> getAll() {
        List<FixedEvent> list = new ArrayList<>();
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT event_id, title, day_of_week, start_minute, end_minute FROM fixed_events "
                        + "ORDER BY day_of_week, start_minute, event_id", null)) {
            while (c.moveToNext()) {
                FixedEvent event = new FixedEvent(c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4));
                event.setEventId(c.getInt(0));
                list.add(event);
            }
        }
        return list;
    }
}
