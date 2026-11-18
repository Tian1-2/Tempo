package com.tempo.app.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Local weekly commitments. Old weekday/time rows also represent valid unnamed commitments. */
public final class FixedBlocks {
    public static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private FixedBlocks() {}

    public static final class Entry {
        public final int day, start, end;
        public final String name, row;

        private Entry(int day, int start, int end, String name, String row) {
            this.day = day;
            this.start = start;
            this.end = end;
            this.name = name;
            this.row = row;
        }

        public String title() { return name.isEmpty() ? "Fixed commitment" : name; }
        public String timeRange() { return clock(start) + "–" + clock(end); }
        public String weeklyTime() { return DAYS[day] + "  " + timeRange(); }
    }

    public static String clock(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    public static List<String> lines(String value) {
        List<String> rows = new ArrayList<>();
        if (value != null && !value.trim().isEmpty()) Collections.addAll(rows, value.trim().split("\n"));
        return rows;
    }

    public static List<Entry> entries(String text) {
        List<Entry> result = new ArrayList<>();
        for (String row : lines(text)) {
            String[] parts = row.split("\t", 2);
            int weekday = Arrays.asList(DAYS).indexOf(parts[0].substring(0, 3));
            result.add(new Entry(weekday, minute(parts[0].substring(4, 9)), minute(parts[0].substring(10, 15)),
                    parts.length == 2 ? parts[1] : "", row));
        }
        result.sort(Comparator.comparingInt((Entry e) -> (e.day + 6) % 7).thenComparingInt(e -> e.start));
        return result;
    }

    public static String join(List<String> rows) {
        StringBuilder result = new StringBuilder();
        for (String row : rows) {
            if (result.length() > 0) result.append('\n');
            result.append(row);
        }
        return result.toString();
    }

    public static String add(String existing, int day, int start, int end) {
        return save(existing, null, day, start, end, "");
    }

    public static String save(String existing, Entry previous, int day, int start, int end, String title) {
        if (day < 0 || day >= DAYS.length || start < 0 || end > 1440 || start >= end)
            throw new IllegalArgumentException("Choose an end time after the start on the same day.");
        String name = title == null ? "" : title.trim();
        if (name.length() > 60 || name.contains("\t") || name.contains("\r") || name.contains("\n"))
            throw new IllegalArgumentException("Use a single-line name of up to 60 characters.");
        List<String> rows = lines(existing);
        if (previous != null) rows.remove(previous.row);
        for (Entry other : entries(join(rows)))
            if (other.day == day && start < other.end && other.start < end)
                throw new IllegalArgumentException("This overlaps another fixed commitment.");
        rows.add(DAYS[day] + " " + clock(start) + "-" + clock(end) + (name.isEmpty() ? "" : "\t" + name));
        return join(rows);
    }

    public static List<Entry> onDay(String text, long day) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(day);
        int weekday = date.get(Calendar.DAY_OF_WEEK) - 1;
        List<Entry> result = new ArrayList<>();
        for (Entry e : entries(text)) if (e.day == weekday) result.add(e);
        return result;
    }

    public static List<String> labels(String text, long day) {
        List<String> result = new ArrayList<>();
        for (Entry entry : onDay(text, day)) result.add(entry.row);
        return result;
    }

    private static int minute(String clock) {
        return Integer.parseInt(clock.substring(0, 2)) * 60 + Integer.parseInt(clock.substring(3, 5));
    }

    static long at(long day, int minute) {
        Calendar clock = Calendar.getInstance();
        clock.setTimeInMillis(day);
        clock.set(Calendar.HOUR_OF_DAY, minute / 60);
        clock.set(Calendar.MINUTE, minute % 60);
        clock.set(Calendar.SECOND, 0);
        clock.set(Calendar.MILLISECOND, 0);
        return clock.getTimeInMillis();
    }

    public static List<ScheduleGenerator.Interval> intervals(String text, long day) {
        List<ScheduleGenerator.Interval> result = new ArrayList<>();
        for (Entry e : onDay(text, day)) {
            long start = at(day, e.start), end = at(day, e.end);
            if (end > start) result.add(new ScheduleGenerator.Interval(start, end));
        }
        return result;
    }
}
