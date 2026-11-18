package com.tempo.app.scheduler;

import android.os.Looper;
import android.database.Cursor;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.planning.ScheduleEditRules;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        return generateAndSave(date, constraints, false);
    }

    /**
     * @param keepUpcoming when true, automatic slots that have not started yet stay where they are
     *                     and only missed slots and unplanned work are placed again. Used when the
     *                     schedule page is opened, so suggested times do not move under the user.
     */
    public ScheduleGenerator.Result generateAndSave(String date, ScheduleConstraintsProvider.DayConstraints constraints,
                                                   boolean keepUpcoming) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("ScheduleService must run on a background thread");
        }
        if (constraints == null) throw new IllegalArgumentException("Constraints are null");
        LearningStore learning = new LearningStore(RepositoryProvider.getDatabaseHelper());
        long now = System.currentTimeMillis();
        learning.refreshPredictions(now);
        checkFutureLocks(learning, now);
        return planDay(date, constraints, learning, now, keepUpcoming);
    }

    private ScheduleGenerator.Result planDay(String date, ScheduleConstraintsProvider.DayConstraints constraints,
            LearningStore learning, long now, boolean keepUpcoming) {
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
        long earliest = Math.max(day.start, Math.floorDiv(now + MINUTE - 1, MINUTE) * MINUTE);
        if (day.start > now) earliest = day.start;
        for (ScheduleItem item : existing) {
            if (ScheduleItem.PAST_PLAN.equals(item.getStatus()) || "CANCELLED".equals(item.getStatus())
                    || ("PLANNED".equals(item.getStatus()) && !item.isManuallyEdited())) continue;
            if ("COMPLETED".equals(item.getStatus()) || "SKIPPED".equals(item.getStatus()) || item.getStartTime() < earliest) {
                // Completed and manual past slots remain; reserve their following break too.
                long endWithBreak = Math.min(day.end,
                        item.getEndTime() + profile.getBreakDuration() * MINUTE);
                blocked.add(new Interval(item.getStartTime(), endWithBreak));
            }
        }
        List<Task> pending = RepositoryProvider.getTaskRepository().getPendingTasksSync();
        long usedToday = 0;
        try (Cursor c = RepositoryProvider.getDatabaseHelper().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(actual_duration),0) FROM task_feedback WHERE actual_duration>0 AND completed_at>=? AND completed_at<? AND completed_at<=?",
                new String[]{String.valueOf(day.start), String.valueOf(day.end), String.valueOf(now)})) {
            if (c.moveToFirst()) usedToday = c.getLong(0);
        }
        Map<Long, Integer> ongoingMinutes = ScheduleModelAdapter.ongoingMinutes(existing, earliest);
        for (int minutes : ongoingMinutes.values()) usedToday += minutes;
        int capacity = (int) Math.max(0, profile.getDailyCapacity() - usedToday);
        List<ScheduleGenerator.Session> lockedHere = ScheduleModelAdapter.lockedSessions(existing, earliest);
        // A slot only counts as missed once it is MISSED_AFTER_MINUTES past its start; until then
        // the user may simply be starting late, and moving it every minute would look jumpy.
        long keepFrom = earliest - MISSED_AFTER_MINUTES * MINUTE;
        if (keepUpcoming) lockedHere.addAll(ScheduleModelAdapter.upcomingAutoSessions(existing, keepFrom));
        Map<Long, Integer> lockedMinutes = new HashMap<>();
        for (ScheduleGenerator.Session s : lockedHere) {
            lockedMinutes.merge(s.taskId, (int) ((s.interval.end - s.interval.start) / MINUTE), Integer::sum);
        }
        // Only future work in an ongoing manual slot or on another date is still reserved.
        Map<Long, Integer> alreadyPlanned = plannedOnOtherDates(date, now);
        for (Map.Entry<Long, Integer> entry : ongoingMinutes.entrySet()) {
            alreadyPlanned.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        List<ScheduleGenerator.Task> tasks = ScheduleModelAdapter.subtractPlanned(
                ScheduleModelAdapter.pendingTasks(pending, learning.remaining(pending, profile.getFocusDuration())),
                alreadyPlanned, lockedMinutes);
        Set<Long> pendingIds = new HashSet<>();
        for (ScheduleGenerator.Task task : tasks) pendingIds.add(task.id);
        List<ScheduleGenerator.Session> locked = new ArrayList<>();
        for (ScheduleGenerator.Session session : lockedHere) {
            // A locked slot whose task is no longer pending (e.g. completed) still occupies time but is not re-planned.
            if (pendingIds.contains(session.taskId)) locked.add(session);
            else blocked.add(session.interval);
        }
        ScheduleGenerator.Request request = new ScheduleGenerator.Request(day, earliest,
                tasks,
                Collections.singletonList(new Interval(wake, sleep)), blocked,
                locked, constraints.productiveWindows,
                profile.getFocusDuration(), profile.getBreakDuration(), capacity);
        ScheduleGenerator.Result result = new ScheduleGenerator().generate(request);
        List<ScheduleItem> fresh = ScheduleModelAdapter.newScheduleItems(date, result);
        if (keepUpcoming) schedules.replaceMissedGeneratedForDateSync(date, fresh, keepFrom);
        else schedules.replaceGeneratedForDateSync(date, fresh, Long.MIN_VALUE);
        return result;
    }

    /** On a light refresh, an untouched automatic slot is re-placed this long after its start. */
    public static final int MISSED_AFTER_MINUTES = 15;

    /** Days covered by {@link #generateRange}: today plus the next six days. */
    public static final int HORIZON_DAYS = 7;

    /**
     * Plans today and the following days. Incomplete automatic slots are cleared first,
     * then each day is generated in order; every day only plans what earlier days left over, so a
     * task that does not fit today continues tomorrow instead of being planned twice. Manually
     * adjusted and completed slots are kept. Call off the UI thread.
     */
    public ScheduleGenerator.Result generateRange(int days, ScheduleConstraintsProvider provider) {
        return generateRange(days, provider, false);
    }

    /**
     * Like {@link #generateRange(int, ScheduleConstraintsProvider)}; with {@code keepUpcoming} the
     * automatic slots that have not started stay put (see {@link #generateAndSave(String,
     * ScheduleConstraintsProvider.DayConstraints, boolean)}). Throws IllegalArgumentException when
     * a kept slot no longer fits the constraints; callers then run a full re-plan.
     */
    public ScheduleGenerator.Result generateRange(int days, ScheduleConstraintsProvider provider, boolean keepUpcoming) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("ScheduleService must run on a background thread");
        }
        if (days < 1) throw new IllegalArgumentException("days must be at least 1");
        if (provider == null) throw new IllegalArgumentException("Constraints provider is null");
        long now = System.currentTimeMillis();
        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(now);
        List<String> dates = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (int i = 0; i < days; i++) {
            dates.add(format.format(cursor.getTime()));
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        ScheduleRepository schedules = RepositoryProvider.getScheduleRepository();
        // One transaction: a conflict on any day leaves the previous plan untouched.
        android.database.sqlite.SQLiteDatabase db = RepositoryProvider.getDatabaseHelper().getWritableDatabase();
        db.beginTransaction();
        try {
            LearningStore learning = new LearningStore(RepositoryProvider.getDatabaseHelper());
            learning.refreshPredictions(now);
            checkFutureLocks(learning, now);
            if (!keepUpcoming) for (String date : dates) {
                schedules.replaceGeneratedForDateSync(date, Collections.<ScheduleItem>emptyList(), Long.MIN_VALUE);
            }
            List<ScheduleGenerator.Result> results = new ArrayList<>();
            for (String date : dates) {
                try {
                    results.add(planDay(date, provider.getForDate(date), learning, now, keepUpcoming));
                } catch (IllegalArgumentException conflict) {
                    throw new IllegalArgumentException(date + " · " + conflict.getMessage(), conflict);
                }
            }
            ScheduleGenerator.Result combined = ScheduleGenerator.Result.combine(results);
            db.setTransactionSuccessful();
            return combined;
        } finally { db.endTransaction(); }
    }

    /** Feedback or an older installation can leave more time locked than the task still needs. */
    private static void checkFutureLocks(LearningStore learning, long now) {
        Map<Integer, Integer> locked = new HashMap<>();
        try (Cursor c = RepositoryProvider.getDatabaseHelper().getReadableDatabase().rawQuery(
                "SELECT task_id,start_time,end_time FROM schedule_items WHERE status='PLANNED' "
                        + "AND is_manually_edited=1 AND end_time>?", new String[]{String.valueOf(now)})) {
            while (c.moveToNext()) locked.merge(c.getInt(0),
                    ScheduleEditRules.futureMinutes(c.getLong(1), c.getLong(2), now), Integer::sum);
        }
        if (locked.isEmpty()) return;
        List<Task> pending = RepositoryProvider.getTaskRepository().getPendingTasksSync();
        UserProfile profile = RepositoryProvider.getUserProfileRepository().getProfileSync();
        Map<Integer, Integer> remaining = learning.remaining(pending, profile == null ? 25 : profile.getFocusDuration());
        for (Task task : pending) {
            int minutes = locked.getOrDefault(task.getTaskId(), 0);
            int left = remaining.get(task.getTaskId());
            if (minutes > left) throw new IllegalArgumentException(task.getTitle() + " has " + left
                    + " min remaining and " + minutes + " min locked. Open a locked slot to shorten it or update the remaining estimate.");
        }
    }

    /** Minutes per task still planned (not started) on other dates from today on. */
    private static Map<Long, Integer> plannedOnOtherDates(String date, long now) {
        Map<Long, Integer> planned = new HashMap<>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now));
        // "now" is a long, inlined as a number: a bound string would compare as text inside MAX().
        try (Cursor c = RepositoryProvider.getDatabaseHelper().getReadableDatabase().rawQuery(
                "SELECT task_id, SUM((end_time - MAX(start_time, " + now + ")) / 60000) FROM schedule_items "
                        + "WHERE status = 'PLANNED' AND scheduled_date <> ? AND scheduled_date >= ? AND end_time > " + now
                        + " GROUP BY task_id",
                new String[]{date, today})) {
            while (c.moveToNext()) planned.put(c.getLong(0), (int) Math.max(0, c.getLong(1)));
        }
        return planned;
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
