import com.tempo.app.model.Task;
import com.tempo.app.model.UserProfile;
import com.tempo.app.model.ScheduleItem;
import com.tempo.app.planning.GenerationPreflight;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.scheduler.ScheduleModelAdapter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Plain Java preflight + existing C model/algorithm checks; does not simulate Android/SQLite. */
public final class GenerationEntryCheck {
    private static final long M = 60_000L;
    private static int assertions;

    public static void main(String[] args) {
        expect(GenerationPreflight.Action.TODAY, () -> GenerationPreflight.checkDate(0, 1));
        GenerationPreflight.checkDate(1, 1);
        GenerationPreflight.checkDate(2, 1);
        Task task = new Task("Insights timer test", "Study", 10, 2, 3, 0);
        task.setTaskId(7);
        List<Task> tasks = Arrays.asList(task);
        expect(GenerationPreflight.Action.PROFILE, () -> GenerationPreflight.check(null, tasks));
        UserProfile profile = profile();
        profile.setWakeTime("7:00");
        expect(GenerationPreflight.Action.PROFILE, () -> GenerationPreflight.check(profile, tasks));
        profile.setWakeTime("24:00");
        expect(GenerationPreflight.Action.PROFILE, () -> GenerationPreflight.check(profile, tasks));
        profile.setWakeTime("23:30");
        expect(GenerationPreflight.Action.PROFILE, () -> GenerationPreflight.check(profile, tasks));
        profile.setWakeTime("07:00"); profile.setDailyCapacity(0);
        expect(GenerationPreflight.Action.PROFILE, () -> GenerationPreflight.check(profile, tasks));
        profile.setDailyCapacity(240); profile.setBreakDuration(-1);
        expect(GenerationPreflight.Action.PROFILE, () -> GenerationPreflight.check(profile, tasks));
        profile.setBreakDuration(5); profile.setFocusDuration(0);
        expect(GenerationPreflight.Action.PROFILE, () -> GenerationPreflight.check(profile, tasks));
        profile.setFocusDuration(25);
        expect(GenerationPreflight.Action.ADD_TASK, () -> GenerationPreflight.check(profile, Collections.emptyList()));
        task.setEstimatedDuration(0);
        expect(GenerationPreflight.Action.NONE, () -> GenerationPreflight.check(profile, tasks));
        task.setPredictedDuration(15);
        GenerationPreflight.check(profile, tasks);
        check(ScheduleModelAdapter.pendingTasks(tasks).get(0).durationMinutes == 15, "prediction accepted");
        task.setDifficulty(0);
        expect(GenerationPreflight.Action.NONE, () -> GenerationPreflight.check(profile, tasks));
        task.setDifficulty(2); task.setImportance(6);
        expect(GenerationPreflight.Action.NONE, () -> GenerationPreflight.check(profile, tasks));
        task.setImportance(3); task.setEstimatedDuration(10); task.setPredictedDuration(0);
        GenerationPreflight.check(profile, tasks);

        ScheduleGenerator.Result result = generate(tasks, 240, Collections.emptyList());
        check(result.sessions.size() == 1 && result.unscheduledTasks.isEmpty(), "10-minute task scheduled");
        check(result.sessions.get(0).interval.start == 9 * 60 * M, "starts at available time");
        check(result.sessions.get(0).interval.end == (9 * 60 + 10) * M, "10 minutes long");
        List<ScheduleItem> rows = ScheduleModelAdapter.newScheduleItems("2026-11-17", result);
        check(rows.size() == 1 && rows.get(0).getTaskId() == 7, "correct persistent task ID");
        check("2026-11-17".equals(rows.get(0).getScheduledDate()) && "PLANNED".equals(rows.get(0).getStatus()), "date and status");

        task.setDeadline(8 * 60 * M);
        result = generate(tasks, 240, Collections.emptyList());
        check(result.sessions.isEmpty() && result.unscheduledTasks.size() == 1, "expired task not forced into day");
        check(result.unscheduledTasks.get(0).reason == ScheduleGenerator.Reason.DEADLINE, "deadline reason");
        task.setDeadline(0);
        result = generate(tasks, 5, Collections.emptyList());
        check(result.sessions.size() == 1 && result.unscheduledTasks.get(0).remainingMinutes == 5, "partial capacity");
        check(result.unscheduledTasks.get(0).reason == ScheduleGenerator.Reason.NO_CAPACITY, "capacity reason");

        ScheduleGenerator.Session lock = new ScheduleGenerator.Session(7,
                new ScheduleGenerator.Interval(10 * 60 * M, (10 * 60 + 5) * M), true);
        result = generate(tasks, 240, Arrays.asList(lock));
        check(result.sessions.stream().anyMatch(s -> s.locked && s.interval.start == lock.interval.start), "manual lock preserved");
        check(ScheduleModelAdapter.newScheduleItems("2026-11-17", result).size() == 1, "locked row not duplicated");
        check("PENDING".equals(task.getStatus()), "generation does not complete task");
        System.out.println("PASS: " + assertions + " generation-entry assertions (preflight + existing C algorithm).");
    }

    private static ScheduleGenerator.Result generate(List<Task> tasks, int capacity, List<ScheduleGenerator.Session> locks) {
        return new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new ScheduleGenerator.Interval(0, 24 * 60 * M), 9 * 60 * M,
                ScheduleModelAdapter.pendingTasks(tasks),
                Arrays.asList(new ScheduleGenerator.Interval(7 * 60 * M, 23 * 60 * M)),
                Collections.emptyList(), locks, Collections.emptyList(), 25, 5, capacity));
    }
    private static UserProfile profile() {
        UserProfile p = new UserProfile();
        p.setWakeTime("07:00"); p.setSleepTime("23:30");
        p.setFocusDuration(25); p.setBreakDuration(5); p.setDailyCapacity(240); return p;
    }
    private static void expect(GenerationPreflight.Action action, Runnable runnable) {
        try { runnable.run(); throw new AssertionError("Expected " + action); }
        catch (GenerationPreflight.Problem problem) { check(problem.action == action, "correct recovery action"); }
    }
    private static void check(boolean ok, String description) {
        assertions++; if (!ok) throw new AssertionError(description);
    }
}
