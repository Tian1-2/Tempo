package com.tempo.app.scheduler;

import com.tempo.app.model.FixedEvent;
import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.scheduler.ScheduleGenerator.Interval;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Day constraints: each meal blocks {@link #MEAL_MINUTES} minutes, weekly fixed events (classes,
 * meetings) block their time, and {@link #PRODUCTIVE_MINUTES} minutes from the preferred start time
 * form the productive window. Meal and fixed blocks overlapping a manually locked slot are skipped,
 * otherwise the generator rejects the request. Call {@link #getForDate(String)} off the UI thread.
 */
public final class DefaultScheduleConstraints implements ScheduleConstraintsProvider {
    public static final int MEAL_MINUTES = 30;
    public static final int PRODUCTIVE_MINUTES = 180;
    private static final long MINUTE = 60_000L;

    @Override public DayConstraints getForDate(String date) {
        UserProfile profile = RepositoryProvider.getUserProfileRepository().getProfileSync();
        List<ScheduleItem> existing = RepositoryProvider.getScheduleRepository().getScheduleByDateSync(date);
        List<FixedEvent> fixed = RepositoryProvider.getFixedEventDao().getAll();
        long dayStart = dayStartMillis(date);
        return build(dayStart, dayStart + 24 * 60 * MINUTE, profile, existing, fixed);
    }

    public static DayConstraints build(long dayStart, long dayEnd, UserProfile profile, List<ScheduleItem> existing) {
        return build(dayStart, dayEnd, profile, existing, Collections.<FixedEvent>emptyList());
    }

    /** Pure computation for unit tests. Times are dayStart + hh:mm (Hong Kong has no DST). */
    public static DayConstraints build(long dayStart, long dayEnd, UserProfile profile, List<ScheduleItem> existing,
                                       List<FixedEvent> fixedEvents) {
        List<Interval> busy = new ArrayList<>();
        List<Interval> productive = new ArrayList<>();
        if (profile == null) return new DayConstraints(busy, productive);
        List<Interval> locked = new ArrayList<>();
        if (existing != null) for (ScheduleItem item : existing) {
            if (item != null && item.isManuallyEdited() && "PLANNED".equals(item.getStatus())
                    && item.getStartTime() < item.getEndTime()) {
                locked.add(new Interval(item.getStartTime(), item.getEndTime()));
            }
        }
        for (String meal : new String[]{profile.getBreakfastTime(), profile.getLunchTime(), profile.getDinnerTime()}) {
            Interval block = window(dayStart, dayEnd, meal, MEAL_MINUTES);
            if (block != null && !overlapsAny(block, locked)) busy.add(block);
        }
        if (fixedEvents != null) for (Interval block : fixedIntervals(dayStart, dayEnd, fixedEvents)) {
            if (!overlapsAny(block, locked)) busy.add(block);
        }
        Interval focus = window(dayStart, dayEnd, profile.getPreferredStartTime(), PRODUCTIVE_MINUTES);
        if (focus != null) productive.add(focus);
        return new DayConstraints(busy, productive);
    }

    /** Fixed events that fall on this day, as absolute intervals clipped to the day. */
    public static List<Interval> fixedIntervals(long dayStart, long dayEnd, List<FixedEvent> events) {
        List<Interval> result = new ArrayList<>();
        if (events == null) return result;
        int weekday = FixedEvent.isoDayOfWeek(dayStart);
        for (FixedEvent event : events) {
            if (event == null || event.getDayOfWeek() != weekday) continue;
            long start = dayStart + event.getStartMinute() * MINUTE;
            long end = Math.min(dayStart + event.getEndMinute() * MINUTE, dayEnd);
            if (start < end) result.add(new Interval(start, end));
        }
        return result;
    }

    private static Interval window(long dayStart, long dayEnd, String hhmm, int minutes) {
        Integer offset = minutesOfDay(hhmm);
        if (offset == null) return null;
        long start = dayStart + offset * MINUTE;
        long end = Math.min(start + minutes * MINUTE, dayEnd);
        return start < end ? new Interval(start, end) : null;
    }

    static Integer minutesOfDay(String hhmm) {
        if (hhmm == null) return null;
        String value = hhmm.trim();
        if (!value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return null;
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3, 5));
    }

    private static boolean overlapsAny(Interval block, List<Interval> others) {
        for (Interval other : others) if (block.start < other.end && other.start < block.end) return true;
        return false;
    }

    private static long dayStartMillis(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try {
            Date parsed = format.parse(date);
            if (parsed == null) throw new IllegalArgumentException("Invalid date: " + date);
            return parsed.getTime();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Date must be yyyy-MM-dd", e);
        }
    }
}
