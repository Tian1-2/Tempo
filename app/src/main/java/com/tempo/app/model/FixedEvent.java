package com.tempo.app.model;

import java.util.Calendar;
import java.util.Locale;

/**
 * A weekly commitment the planner must work around, such as a class or a meeting.
 * Days use ISO numbering: 1 = Monday … 7 = Sunday. Times are minutes after local midnight.
 */
public class FixedEvent {
    public static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private int eventId;
    private String title;
    private int dayOfWeek;
    private int startMinute;
    private int endMinute;

    public FixedEvent() { }

    public FixedEvent(String title, int dayOfWeek, int startMinute, int endMinute) {
        this.title = title;
        this.dayOfWeek = dayOfWeek;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    /** Null when valid, otherwise a message for the user. */
    public static String validate(String title, int dayOfWeek, int startMinute, int endMinute) {
        if (title == null || title.trim().isEmpty()) return "Give it a name, such as \"COMP7506 lecture\".";
        if (dayOfWeek < 1 || dayOfWeek > 7) return "Choose a weekday.";
        if (startMinute < 0 || endMinute > 1440) return "Times must be within the day.";
        if (endMinute <= startMinute) return "The end time must be later than the start time.";
        return null;
    }

    /** ISO weekday (1 = Monday … 7 = Sunday) of a local time. */
    public static int isoDayOfWeek(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    }

    public boolean occursOn(long dayStartMillis) { return isoDayOfWeek(dayStartMillis) == dayOfWeek; }

    public String timeText() { return clock(startMinute) + "–" + clock(endMinute); }

    public static String clock(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    public String dayName() { return dayOfWeek >= 1 && dayOfWeek <= 7 ? DAY_NAMES[dayOfWeek - 1] : "?"; }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }
    public int getEndMinute() { return endMinute; }
    public void setEndMinute(int endMinute) { this.endMinute = endMinute; }
}
