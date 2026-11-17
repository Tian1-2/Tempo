import com.tempo.app.insights.InsightsStats;
import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;
import java.util.Arrays;
import java.util.Collections;
import java.util.Calendar;
import java.util.TimeZone;

/** Standalone regression checks; no Android, network, or test dependency required. */
public final class InsightsStatsCheck {
    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final long NOW = at(ZONE, 2026, 11, 17, 12, 0);
    private static int checks;

    public static void main(String[] args) {
        emptyData();
        partialSessionsDoNotCompleteTasks();
        localDateWindowAndFutureRows();
        excludesInvalidAndDuplicateRecords();
        ratingsAreOptional();
        completionUsesTaskStatus();
        categoryNormalizationAndOrdering();
        daylightSavingBoundaries();
        durationDoesNotOverflow();
        exactMidnight();
        System.out.println("PASS: " + checks + " assertions across 10 Insights scenarios.");
    }

    private static void emptyData() {
        InsightsStats s = calculate(Collections.emptyList(), Collections.emptyList());
        check(s.taskCount == 0 && s.recordedMinutes == 0 && s.activeDays == 0, "empty data");
        check(s.categories.isEmpty() && s.completionPercent() == 0 && s.ratedSessions == 0, "empty metrics");
        check(s.dayStarts.length == 7 && s.dailyMinutes.length == 7, "seven days");
    }

    private static void partialSessionsDoNotCompleteTasks() {
        Task t = task(1, "Study", "PENDING");
        InsightsStats s = calculate(Arrays.asList(t), Arrays.asList(
                feedback(1, 1, 30, 0, NOW - 60_000), feedback(2, 1, 45, 0, NOW)));
        check(s.recordedMinutes == 75 && s.sessionCount == 2, "sum sessions");
        check(s.completedTasks == 0 && s.recordedTaskCount == 1, "partial is not completion");
        check(s.dailyMinutes[6] == 75 && s.activeDays == 1, "same day totals");
        check("PENDING".equals(t.getStatus()), "does not mutate source");
    }

    private static void localDateWindowAndFutureRows() {
        long start = at(ZONE, 2026, 11, 11, 0, 0);
        InsightsStats s = calculate(Arrays.asList(task(1, "Study", "PENDING")), Arrays.asList(
                feedback(1, 1, 20, 0, start), feedback(2, 1, 90, 5, start - 1),
                feedback(3, 1, 90, 5, NOW + 1), feedback(4, 1, 10, 0, NOW)));
        check(s.recordedMinutes == 30 && s.sessionCount == 2, "exclude old and future");
        check(s.dailyMinutes[0] == 20 && s.dailyMinutes[6] == 10, "include boundary and now");
        check(s.ratedSessions == 0, "ratings follow date window");
    }

    private static void excludesInvalidAndDuplicateRecords() {
        TaskFeedback row = feedback(1, 1, 20, 0, NOW);
        InsightsStats s = calculate(Arrays.asList(task(1, "Study", "PENDING")), Arrays.asList(
                row, row, feedback(2, 1, -5, 0, NOW), feedback(3, 1, 0, 0, NOW),
                feedback(4, 99, 50, 5, NOW), feedback(5, 1, 40, 0, 0), null));
        check(s.recordedMinutes == 20 && s.sessionCount == 1, "invalid/orphan/duplicates excluded");
        check(s.ratedSessions == 0, "orphan rating excluded");
    }

    private static void ratingsAreOptional() {
        InsightsStats s = calculate(Arrays.asList(task(1, "Study", "PENDING")), Arrays.asList(
                feedback(1, 1, 20, 0, NOW), feedback(2, 1, 20, 4, NOW),
                feedback(3, 1, 20, 2, NOW), feedback(4, 1, 20, 6, NOW)));
        check(s.ratedSessions == 2 && s.averageDifficulty == 3, "missing/out-of-range ratings excluded");
        check(s.sessionCount == 4, "unrated durations included");
    }

    private static void completionUsesTaskStatus() {
        TaskFeedback f = feedback(1, 2, 10, 0, NOW);
        f.setCompletionStatus("COMPLETED");
        InsightsStats s = calculate(Arrays.asList(task(1, "Work", "COMPLETED"),
                task(2, "Study", "PENDING"), task(3, "Life", "IN_PROGRESS")), Arrays.asList(f));
        check(s.taskCount == 3 && s.completedTasks == 1 && s.completionPercent() == 33,
                "all-time task completion, not feedback completion");
    }

    private static void categoryNormalizationAndOrdering() {
        InsightsStats s = calculate(Arrays.asList(task(1, "学习", "PENDING"), task(2, " study ", "PENDING"),
                task(3, "Work", "PENDING"), task(4, null, "PENDING")), Arrays.asList(
                feedback(1, 1, 30, 0, NOW), feedback(2, 2, 20, 0, NOW),
                feedback(3, 3, 40, 0, NOW), feedback(4, 4, 10, 0, NOW)));
        check(s.categories.size() == 3, "normalize study categories");
        check("Study".equals(s.categories.get(0).name) && s.categories.get(0).minutes == 50, "descending totals");
        check("Uncategorized".equals(s.categories.get(2).name), "missing category label");
        long sum = 0;
        for (InsightsStats.Category category : s.categories) sum += category.minutes;
        check(sum == s.recordedMinutes, "category totals reconcile");
    }

    private static void daylightSavingBoundaries() {
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        long now = at(ny, 2026, 11, 3, 12, 0);
        InsightsStats s = InsightsStats.calculate(Arrays.asList(task(1, "Life", "PENDING")), Arrays.asList(
                feedback(1, 1, 30, 0, at(ny, 2026, 11, 1, 23, 30)),
                feedback(2, 1, 20, 0, at(ny, 2026, 11, 2, 0, 0))), now, ny);
        check(s.dayStarts[5] - s.dayStarts[4] == 25 * 60 * 60_000L, "25-hour local day");
        check(s.dailyMinutes[4] == 30 && s.dailyMinutes[5] == 20, "DST grouping");
    }

    private static void durationDoesNotOverflow() {
        InsightsStats s = calculate(Arrays.asList(task(1, "Study", "PENDING")), Arrays.asList(
                feedback(1, 1, Integer.MAX_VALUE, 0, NOW), feedback(2, 1, Integer.MAX_VALUE, 0, NOW)));
        check(s.recordedMinutes == 2L * Integer.MAX_VALUE, "long duration accumulation");
        check(s.dailyMinutes[6] == s.recordedMinutes && s.categories.get(0).minutes == s.recordedMinutes,
                "large totals reconcile");
    }

    private static void exactMidnight() {
        long midnight = at(ZONE, 2026, 11, 17, 0, 0);
        InsightsStats s = calculate(Arrays.asList(task(1, "Study", "PENDING")), Arrays.asList(
                feedback(1, 1, 10, 0, midnight - 1), feedback(2, 1, 20, 0, midnight)));
        check(s.dailyMinutes[5] == 10 && s.dailyMinutes[6] == 20, "midnight is next day");
    }

    private static InsightsStats calculate(java.util.List<Task> tasks, java.util.List<TaskFeedback> rows) {
        return InsightsStats.calculate(tasks, rows, NOW, ZONE);
    }
    private static Task task(int id, String category, String status) {
        Task t = new Task(); t.setTaskId(id); t.setCategory(category); t.setStatus(status); return t;
    }
    private static TaskFeedback feedback(int id, int taskId, int minutes, int rating, long time) {
        TaskFeedback f = new TaskFeedback();
        f.setFeedbackId(id); f.setTaskId(taskId); f.setActualDuration(minutes);
        f.setActualDifficulty(rating); f.setCompletedAt(time); f.setCompletionStatus("PARTIAL"); return f;
    }
    private static long at(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(zone); c.clear(); c.set(year, month - 1, day, hour, minute);
        return c.getTimeInMillis();
    }
    private static void check(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }
}
