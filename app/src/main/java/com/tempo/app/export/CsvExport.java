package com.tempo.app.export;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Builds a CSV of every saved activity record (one row per focus session or manual entry), with
 * the task it belongs to. Opens directly in Excel, Numbers or Google Sheets. Call off the UI thread.
 */
public final class CsvExport {
    public static final String[] HEADER = {"Date", "Time", "Task", "Category", "Type", "Minutes",
            "Outcome", "Difficulty (1-5)", "Estimate (min)", "Deadline", "Archived", "Notes"};

    private CsvExport() {}

    public static String build(SQLiteDatabase db) {
        StringBuilder out = new StringBuilder("﻿"); // byte-order mark: Excel then reads UTF-8 (e.g. Chinese) correctly
        line(out, HEADER);
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.US);
        SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        try (Cursor c = db.rawQuery("SELECT f.completed_at, t.title, t.category, t.task_type, f.actual_duration, "
                + "f.completion_status, f.actual_difficulty, t.estimated_duration, t.deadline, t.archived, f.notes "
                + "FROM task_feedback f JOIN tasks t ON t.task_id = f.task_id ORDER BY f.completed_at, f.feedback_id", null)) {
            while (c.moveToNext()) {
                long at = c.getLong(0);
                long deadline = c.isNull(8) ? 0 : c.getLong(8);
                line(out, new String[]{
                        at > 0 ? day.format(new Date(at)) : "", at > 0 ? clock.format(new Date(at)) : "",
                        text(c, 1), text(c, 2), text(c, 3), text(c, 4), outcome(text(c, 5)),
                        c.isNull(6) || c.getInt(6) <= 0 ? "" : String.valueOf(c.getInt(6)), text(c, 7),
                        deadline > 0 ? stamp.format(new Date(deadline)) : "", c.getInt(9) != 0 ? "yes" : "no", text(c, 10)});
            }
        }
        return out.toString();
    }

    private static String text(Cursor c, int i) { return c.isNull(i) ? "" : c.getString(i); }

    private static String outcome(String status) {
        if ("COMPLETED".equals(status)) return "Finished";
        if ("IN_PROGRESS".equals(status) || "PARTIAL".equals(status)) return "In progress";
        return status == null ? "" : status;
    }

    static void line(StringBuilder out, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) out.append(',');
            out.append(escape(cells[i]));
        }
        out.append("\r\n");
    }

    /** RFC 4180: quote cells containing a comma, quote or line break, doubling inner quotes. */
    public static String escape(String value) {
        if (value == null) return "";
        // A leading = + - @ would run as a formula in a spreadsheet; prefix it so it stays text.
        String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
