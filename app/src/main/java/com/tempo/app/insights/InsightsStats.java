package com.tempo.app.insights;

import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Read-only summaries. A feedback row is a session, never a completed task. */
public final class InsightsStats {
    public static final class Category {
        public final String name;
        public final long minutes;
        Category(String name, long minutes) { this.name = name; this.minutes = minutes; }
    }

    public final long[] dayStarts;
    public final long[] dailyMinutes;
    public final int taskCount;
    public final int completedTasks;
    public final long recordedMinutes;
    public final int sessionCount;
    public final int activeDays;
    public final int recordedTaskCount;
    public final int ratedSessions;
    public final double averageDifficulty;
    public final List<Category> categories;

    private InsightsStats(long[] days, long[] minutes, int taskCount, int completedTasks,
                          long recordedMinutes, int sessions, int activeDays, int recordedTasks,
                          int ratedSessions, double averageDifficulty, List<Category> categories) {
        this.dayStarts = days;
        this.dailyMinutes = minutes;
        this.taskCount = taskCount;
        this.completedTasks = completedTasks;
        this.recordedMinutes = recordedMinutes;
        this.sessionCount = sessions;
        this.activeDays = activeDays;
        this.recordedTaskCount = recordedTasks;
        this.ratedSessions = ratedSessions;
        this.averageDifficulty = averageDifficulty;
        this.categories = Collections.unmodifiableList(categories);
    }

    public int completionPercent() {
        return taskCount == 0 ? 0 : (int) Math.round(100.0 * completedTasks / taskCount);
    }

    /** Seven local calendar days including today; future/invalid/orphan rows are excluded. */
    public static InsightsStats calculate(List<Task> tasks, List<TaskFeedback> feedback,
                                         long now, TimeZone zone) {
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        day.add(Calendar.DAY_OF_MONTH, -6);
        long[] bounds = new long[8];
        for (int i = 0; i < bounds.length; i++) {
            bounds[i] = day.getTimeInMillis();
            day.add(Calendar.DAY_OF_MONTH, 1); // Calendar days, including DST transitions.
        }
        long[] starts = new long[7];
        System.arraycopy(bounds, 0, starts, 0, starts.length);
        Map<Integer, Task> byId = new HashMap<>();
        for (Task task : tasks) if (task != null && task.getTaskId() > 0) byId.put(task.getTaskId(), task);
        int completed = 0;
        for (Task task : byId.values()) if ("COMPLETED".equals(task.getStatus())) completed++;
        long[] minutes = new long[7];
        Map<String, Long> totals = new LinkedHashMap<>();
        Set<Integer> recordedTasks = new HashSet<>();
        Set<Integer> seenFeedback = new HashSet<>();
        long total = 0, difficultySum = 0;
        int sessions = 0, ratings = 0;
        for (TaskFeedback row : feedback) {
            if (row == null || !byId.containsKey(row.getTaskId())) continue;
            if (row.getFeedbackId() > 0 && !seenFeedback.add(row.getFeedbackId())) continue;
            long time = row.getCompletedAt();
            if (time <= 0 || time < bounds[0] || time > now) continue;
            int rating = row.getActualDifficulty();
            if (rating >= 1 && rating <= 5) { difficultySum += rating; ratings++; }
            if (row.getActualDuration() <= 0) continue;
            for (int i = 0; i < 7; i++) {
                if (time >= bounds[i] && time < bounds[i + 1]) {
                    minutes[i] += row.getActualDuration();
                    break;
                }
            }
            total += row.getActualDuration();
            sessions++;
            recordedTasks.add(row.getTaskId());
            String category = categoryName(byId.get(row.getTaskId()).getCategory());
            totals.put(category, totals.getOrDefault(category, 0L) + row.getActualDuration());
        }
        List<Category> categories = new ArrayList<>();
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            categories.add(new Category(entry.getKey(), entry.getValue()));
        }
        categories.sort(Comparator.comparingLong((Category c) -> c.minutes).reversed()
                .thenComparing(c -> c.name));
        int active = 0;
        for (long value : minutes) if (value > 0) active++;
        return new InsightsStats(starts, minutes, byId.size(), completed, total, sessions,
                active, recordedTasks.size(), ratings, ratings == 0 ? 0 : (double) difficultySum / ratings,
                categories);
    }

    private static String categoryName(String value) {
        if (value == null || value.trim().isEmpty()) return "Uncategorized";
        value = value.trim();
        // Legacy rows may store Chinese category names; keep these aliases.
        if (value.equalsIgnoreCase("study") || value.equals("学习")) return "Study";
        if (value.equalsIgnoreCase("work") || value.equals("工作")) return "Work";
        if (value.equalsIgnoreCase("life") || value.equals("生活")) return "Life";
        return value;
    }
}
