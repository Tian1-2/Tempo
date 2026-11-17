package com.tempo.app.scheduler;

import android.os.Looper;
import android.database.Cursor;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.model.Task;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.ScheduleRepository;
import com.tempo.app.scheduler.ScheduleGenerator.Interval;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads the repositories, schedules one day, and saves generated rows. Call off the UI thread. */
public final class ScheduleService {
    private static final long MINUTE = 60_000L;

    public ScheduleGenerator.Result generateAndSave(String date, List<Interval> fixedBusyIntervals) {
        return generateAndSave(date, new ScheduleConstraintsProvider.DayConstraints(
                fixedBusyIntervals, Collections.<Interval>emptyList()));
    }

    public ScheduleGenerator.Result generateAndSave(String date, ScheduleConstraintsProvider provider) {
        if (provider == null) throw new IllegalArgumentException("Constraints provider is null");
        return generateAndSave(date, provider.getForDate(date));
    }

    public ScheduleGenerator.Result generateAndSave(String date, ScheduleConstraintsProvider.DayConstraints constraints) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("ScheduleService must run on a background thread");
        }
        if (constraints == null) throw new IllegalArgumentException("Constraints are null");
        LearningStore learning = new LearningStore(RepositoryProvider.getDatabaseHelper());
        learning.refreshPredictions(System.currentTimeMillis());
        UserProfile profile = RepositoryProvider.getUserProfileRepository().getProfileSync();
        if (profile == null) throw new IllegalStateException("Set a user profile before generating a schedule");
        ScheduleRepository schedules = RepositoryProvider.getScheduleRepository();
        List<ScheduleItem> existing = schedules.getScheduleByDateSync(date);
        Calendar start = parseDate(date);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 1);
        Interval day = new Interval(start.getTimeInMillis(), end.getTimeInMillis());
        long wake = atTime(start, profile.getWakeTime());
        long sleep = atTime(start, profile.getSleepTime());
        if (wake >= sleep) throw new IllegalArgumentException("Wake time must precede sleep time on the same day");
        List<Interval> blocked = new ArrayList<>(constraints.fixedBusyIntervals);
        long now = System.currentTimeMillis();
        long earliest = Math.max(day.start, Math.floorDiv(now + MINUTE - 1, MINUTE) * MINUTE);
        if (day.start > now) earliest = day.start;
        for (ScheduleItem item : existing) {
            if ("CANCELLED".equals(item.getStatus())) continue;
            if ("COMPLETED".equals(item.getStatus()) || "SKIPPED".equals(item.getStatus()) || item.getStartTime() < earliest) {
                blocked.add(new Interval(item.getStartTime(), item.getEndTime()));
            }
        }
        List<Task> pending = RepositoryProvider.getTaskRepository().getPendingTasksSync();
        long usedToday = 0;
        try (Cursor c = RepositoryProvider.getDatabaseHelper().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(actual_duration),0) FROM task_feedback WHERE actual_duration>0 AND completed_at>=? AND completed_at<? AND completed_at<=?",
                new String[]{String.valueOf(day.start), String.valueOf(day.end), String.valueOf(now)})) {
            if (c.moveToFirst()) usedToday = c.getLong(0);
        }
        int capacity = (int) Math.max(0, profile.getDailyCapacity() - usedToday);
        Set<Long> pendingIds = new HashSet<>();
        for (Task task : pending) pendingIds.add((long) task.getTaskId());
        List<ScheduleGenerator.Session> locked = new ArrayList<>();
        for (ScheduleGenerator.Session session : ScheduleModelAdapter.lockedSessions(existing, earliest)) {
            // A locked slot whose task is no longer pending (e.g. completed) still occupies time but is not re-planned.
            if (pendingIds.contains(session.taskId)) locked.add(session);
            else blocked.add(session.interval);
        }
        ScheduleGenerator.Request request = new ScheduleGenerator.Request(day, earliest,
                ScheduleModelAdapter.pendingTasks(pending, learning.remaining(pending, profile.getFocusDuration())),
                Collections.singletonList(new Interval(wake, sleep)), blocked,
                locked, constraints.productiveWindows,
                profile.getFocusDuration(), profile.getBreakDuration(), capacity);
        ScheduleGenerator.Result result = new ScheduleGenerator().generate(request);
        schedules.replaceGeneratedForDateSync(date, ScheduleModelAdapter.newScheduleItems(date, result), earliest);
        return result;
    }

    private static Calendar parseDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Date must be yyyy-MM-dd");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try {
            Date parsed = format.parse(value);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsed);
            return calendar;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid date", e);
        }
    }

    private static long atTime(Calendar day, String hhmm) {
        if (hhmm == null || !hhmm.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException("Invalid profile time: " + hhmm);
        Calendar value = (Calendar) day.clone();
        value.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hhmm.substring(0, 2)));
        value.set(Calendar.MINUTE, Integer.parseInt(hhmm.substring(3, 5)));
        return value.getTimeInMillis();
    }
}
