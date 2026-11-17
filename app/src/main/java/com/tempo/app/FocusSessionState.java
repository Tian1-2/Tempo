package com.tempo.app;

import android.content.SharedPreferences;

import java.util.Calendar;

/** Shared focus-session checks used by the timer and schedule entry points. */
public final class FocusSessionState {
    public static final String PREFS = "tempo_focus_timer";

    private FocusSessionState() { }

    public static boolean isRunning(SharedPreferences prefs) {
        return prefs.getInt("taskId", Integer.MIN_VALUE) != Integer.MIN_VALUE
                && prefs.getLong("started", 0) > 0;
    }

    public static boolean isFromEarlierDay(SharedPreferences prefs, long now) {
        if (prefs.getInt("taskId", Integer.MIN_VALUE) == Integer.MIN_VALUE) return false;
        long started = prefs.getLong("sessionStartWall", 0);
        if (started <= 0) started = prefs.getLong("startedWall", 0);
        if (started <= 0) {
            String segments = prefs.getString("segments", "");
            int comma = segments == null ? -1 : segments.indexOf(',');
            if (comma > 0) try { started = Long.parseLong(segments.substring(0, comma)); }
                catch (NumberFormatException ignored) { }
        }
        // Old paused sessions without a trustworthy start date need review too.
        if (started <= 0) return prefs.getLong("elapsed", 0) > 0;
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return started < day.getTimeInMillis();
    }
}
