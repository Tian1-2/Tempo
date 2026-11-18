package com.tempo.app.home;

import com.tempo.app.database.AppDatabaseHelper;
import com.tempo.app.database.TaskDao;
import com.tempo.app.database.UserProfileDao;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Fills an empty app with a realistic week of activity so every page can be seen with data.
 * Sessions are saved through D's LearningStore (the same path as the focus timer), so Insights,
 * History, predictions and the home review all read genuine records. Runs on a background thread.
 */
public final class SampleData {
    private static final long M = 60_000L;

    private final AppDatabaseHelper helper = RepositoryProvider.getDatabaseHelper();
    private final LearningStore learning = new LearningStore(helper);
    private final TaskDao tasks = new TaskDao(helper);
    private final long todayStart;
    private final long now;

    private SampleData(long now) {
        this.now = now;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        todayStart = c.getTimeInMillis();
    }

    /** True when there is nothing to overwrite: sample data is only added to an empty app. */
    public static boolean canSeed() {
        return RepositoryProvider.getTaskRepository().getAllTasksSync().isEmpty();
    }

    /** Background thread only. */
    public static void seed() {
        if (!canSeed()) throw new IllegalStateException("Sample data can only be added to an empty app.");
        new SampleData(System.currentTimeMillis()).run();
    }

    private void run() {
        if (RepositoryProvider.getUserProfileRepository().getProfileSync() == null) {
            UserProfile p = new UserProfile();
            p.setProfileId(1);
            p.setWakeTime("07:00"); p.setSleepTime("23:30");
            p.setBreakfastTime("08:00"); p.setLunchTime("12:30"); p.setDinnerTime("18:30");
            p.setPreferredStartTime("09:00");
            p.setFocusDuration(25); p.setBreakDuration(5); p.setDailyCapacity(300);
            new UserProfileDao(helper).saveProfile(p);
        }

        // A weekly lecture on today's weekday, so the demo shows planning around fixed commitments.
        com.tempo.app.database.FixedEventDao fixed = new com.tempo.app.database.FixedEventDao(helper);
        if (fixed.getAll().isEmpty()) {
            fixed.insert(new com.tempo.app.model.FixedEvent("COMP7506 lecture",
                    com.tempo.app.model.FixedEvent.isoDayOfWeek(now), 14 * 60, 16 * 60));
        }

        // Six days ago → yesterday: completed work with realistic over- and under-runs.
        done(-6, "Linear algebra problem set", "Study", "Assignment", 60, 4, 4, 9, 30, 85, 4);
        done(-6, "Gym session", "Life", "Exercise", 60, 2, 3, 18, 45, 55, 2);
        done(-5, "Read HCI paper", "Study", "Reading", 45, 3, 3, 10, 0, 60, 3);
        done(-5, "Weekly status report", "Work", "Report", 60, 3, 4, 14, 30, 75, 3);
        done(-4, "COMP7506 lecture review", "Study", "Revision", 50, 3, 4, 9, 15, 50, 3);
        done(-4, "Groceries", "Life", "Errands", 30, 1, 2, 17, 0, 40, 1);
        int prototype = partial(-3, "Android UI prototype", "Work", "Project", 90, 4, 5, 13, 0, 70, 4);
        finish(prototype, -2, 10, 0, 50, 4);
        done(-2, "Algorithms practice", "Study", "Practice", 45, 4, 3, 15, 0, 55, 4);
        done(-1, "Essay first draft", "Study", "Assignment", 60, 4, 5, 9, 30, 100, 4);
        done(-1, "Read HCI paper #2", "Study", "Reading", 40, 3, 3, 14, 0, 45, 3);
        done(-1, "Team meeting prep", "Work", "Meeting preparation", 30, 2, 4, 16, 0, 25, 2);
        missed(-1, "Evening run", "Life", "Exercise", 40, 2, 2, 19, 30);

        // Today: one task finished this morning (only if the morning is over), the rest still to do.
        if (now >= at(0, 10, 0)) done(0, "Vocabulary review", "Study", "Revision", 25, 2, 3, 8, 30, 30, 2);
        pending("COMP7506 demo slides", "Study", "Assignment", 90, 4, 5, at(0, 23, 0));
        pending("Insights write-up", "Work", "Report", 45, 3, 3, 0);
        pending("Evening walk", "Life", "Exercise", 30, 1, 2, 0);
        // A user-made category with its own colour.
        new com.tempo.app.database.CategoryDao(helper).insert("Club", com.tempo.app.model.Category.PALETTE[3]);
        pending("Robotics club poster", "Club", null, 40, 2, 3, 0);
        com.tempo.app.ui.Categories.reload(helper);
        // Today's plan itself is generated afterwards by the caller through AutoScheduler.
    }

    // ---------------------------------------------------------------- builders

    private int newTask(String title, String category, String type, int estimate, int difficulty,
                        int importance, long deadline, long createdAt) {
        Task t = new Task(title, category, estimate, difficulty, importance, deadline);
        t.setTaskType(type);
        t.setStatus("PENDING");
        t.setCreatedAt(createdAt);
        return (int) tasks.insertTask(t);
    }

    /** A planned slot on that day plus a timer session that finished the task. */
    private void done(int day, String title, String category, String type, int estimate, int difficulty,
                      int importance, int hour, int minute, int actual, int felt) {
        long start = at(day, hour, minute);
        int id = newTask(title, category, type, estimate, difficulty, importance, 0, start - 2 * 60 * M);
        plan(id, day, start, estimate);
        session(id, start, actual, felt, true, true);
    }

    private int partial(int day, String title, String category, String type, int estimate, int difficulty,
                        int importance, int hour, int minute, int actual, int felt) {
        long start = at(day, hour, minute);
        int id = newTask(title, category, type, estimate, difficulty, importance, 0, start - 2 * 60 * M);
        plan(id, day, start, estimate);
        session(id, start, actual, felt, false, true);
        return id;
    }

    private void finish(int id, int day, int hour, int minute, int actual, int felt) {
        long start = at(day, hour, minute);
        plan(id, day, start, actual);
        session(id, start, actual, felt, true, false);
    }

    /** Planned but never started: it stays pending and is re-planned today. */
    private void missed(int day, String title, String category, String type, int estimate, int difficulty,
                        int importance, int hour, int minute) {
        long start = at(day, hour, minute);
        int id = newTask(title, category, type, estimate, difficulty, importance, 0, start - 3 * 60 * M);
        plan(id, day, start, estimate);
    }

    private void pending(String title, String category, String type, int estimate, int difficulty,
                         int importance, long deadline) {
        newTask(title, category, type, estimate, difficulty, importance, deadline, now - 30 * M);
    }

    private void plan(int taskId, int day, long start, int minutes) {
        ScheduleItem item = new ScheduleItem();
        item.setTaskId(taskId);
        item.setScheduledDate(dateKey(at(day, 0, 0)));
        item.setStartTime(start);
        item.setEndTime(start + minutes * M);
        item.setManuallyEdited(false);
        item.setStatus("PLANNED");
        RepositoryProvider.getScheduleRepository().saveScheduleSync(Collections.singletonList(item));
    }

    /** One continuous timer segment saved exactly like the focus timer does. */
    private void session(int taskId, long start, int minutes, int felt, boolean complete, boolean firstStart) {
        if (firstStart) learning.prepare(taskId, start);
        long end = start + minutes * M;
        learning.save(UUID.randomUUID().toString(), taskId, minutes, felt, complete, null,
                "TIMER", start + "," + end, true, end);
    }

    private long at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(todayStart);
        c.add(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return c.getTimeInMillis();
    }

    private static String dateKey(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }
}
