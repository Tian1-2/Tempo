package com.tempo.app.home;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Statistics and score for one day (plain Java, no Android dependency, unit-testable).
 * The home page uses it for yesterday's review and for today's completed count and focus time.
 */
public final class DailyReview {
    static final int WEIGHT_COMPLETION = 50;
    static final int WEIGHT_FOCUS = 30;
    static final int WEIGHT_ACCURACY = 20;

    /** The task whose actual time exceeded its estimate the most. */
    public static final class Overrun {
        public final String title;
        public final int estimatedMinutes;
        public final int actualMinutes;

        Overrun(String title, int estimatedMinutes, int actualMinutes) {
            this.title = title;
            this.estimatedMinutes = estimatedMinutes;
            this.actualMinutes = actualMinutes;
        }
    }

    public final int plannedTasks;
    public final int completedTasks;
    public final int plannedMinutes;
    public final int focusMinutes;
    /** Null when no task ran over its estimate. */
    public final Overrun overrun;
    /** There were comparable estimates and none ran over. */
    public final boolean estimatesAccurate;
    /** 0-100; null when there is nothing to score. */
    public final Integer score;
    /** Score components in percent; null when that part had no data. */
    public final Integer completionPct, focusPct, accuracyPct;

    private DailyReview(int plannedTasks, int completedTasks, int plannedMinutes, int focusMinutes,
                        Overrun overrun, boolean estimatesAccurate, Integer score,
                        Integer completionPct, Integer focusPct, Integer accuracyPct) {
        this.plannedTasks = plannedTasks;
        this.completedTasks = completedTasks;
        this.plannedMinutes = plannedMinutes;
        this.focusMinutes = focusMinutes;
        this.overrun = overrun;
        this.estimatesAccurate = estimatesAccurate;
        this.score = score;
        this.completionPct = completionPct;
        this.focusPct = focusPct;
        this.accuracyPct = accuracyPct;
    }

    /**
     * @param dayStart    local midnight of the day, in ms
     * @param dayEnd      local midnight of the next day, in ms
     * @param daySchedule schedule_items of the day
     * @param tasks       all tasks
     * @param feedback    all feedback rows
     */
    public static DailyReview compute(long dayStart, long dayEnd, List<ScheduleItem> daySchedule,
                                      List<Task> tasks, List<TaskFeedback> feedback) {
        Map<Integer, Task> byId = new HashMap<>();
        if (tasks != null) for (Task t : tasks) if (t != null) byId.put(t.getTaskId(), t);

        Set<Integer> planned = new HashSet<>();
        int plannedMinutes = 0;
        if (daySchedule != null) for (ScheduleItem item : daySchedule) {
            if (item == null || "CANCELLED".equals(item.getStatus())) continue; // cancelled after early completion
            planned.add(item.getTaskId());
            plannedMinutes += (int) Math.max(0, (item.getEndTime() - item.getStartTime()) / 60_000L);
        }
        int completed = 0;
        for (int id : planned) {
            Task t = byId.get(id);
            if (t != null && "COMPLETED".equals(t.getStatus())) completed++;
        }

        int focus = 0;
        Set<Integer> touched = new HashSet<>();                 // tasks with feedback on this day
        Map<Integer, Integer> actualUntilEnd = new HashMap<>(); // accumulated actual minutes up to dayEnd
        if (feedback != null) for (TaskFeedback f : feedback) {
            if (f == null || f.getCompletedAt() >= dayEnd) continue;
            int minutes = Math.max(0, f.getActualDuration());
            Integer sum = actualUntilEnd.get(f.getTaskId());
            actualUntilEnd.put(f.getTaskId(), (sum == null ? 0 : sum) + minutes);
            if (f.getCompletedAt() >= dayStart) {
                focus += minutes;
                touched.add(f.getTaskId());
            }
        }

        Overrun worst = null;
        double accuracySum = 0;
        int accuracySamples = 0;
        for (int id : touched) {
            Task t = byId.get(id);
            if (t == null || t.getEstimatedDuration() <= 0) continue;
            int estimate = t.getEstimatedDuration();
            Integer actualBoxed = actualUntilEnd.get(id);
            int actual = actualBoxed == null ? 0 : actualBoxed;
            int over = actual - estimate;
            if (over > 0 && (worst == null || over > worst.actualMinutes - worst.estimatedMinutes)) {
                worst = new Overrun(t.getTitle(), estimate, actual);
            }
            // Actual time is only comparable once the task is done or already over its estimate.
            if ("COMPLETED".equals(t.getStatus()) || over > 0) {
                accuracySum += Math.max(0.0, 1.0 - Math.abs(actual - estimate) / (double) estimate);
                accuracySamples++;
            }
        }

        double weighted = 0;
        int weights = 0;
        Double completionPart = null, focusPart = null, accuracyPart = null;
        if (!planned.isEmpty()) {
            completionPart = completed / (double) planned.size();
            weighted += WEIGHT_COMPLETION * completionPart;
            weights += WEIGHT_COMPLETION;
        }
        if (plannedMinutes > 0) {
            focusPart = Math.min(1.0, focus / (double) plannedMinutes);
            weighted += WEIGHT_FOCUS * focusPart;
            weights += WEIGHT_FOCUS;
        }
        if (accuracySamples > 0) {
            accuracyPart = accuracySum / accuracySamples;
            weighted += WEIGHT_ACCURACY * accuracyPart;
            weights += WEIGHT_ACCURACY;
        }
        Integer score = weights == 0 ? null : (int) Math.round(100 * weighted / weights);
        return new DailyReview(planned.size(), completed, plannedMinutes, focus, worst,
                worst == null && accuracySamples > 0, score,
                percent(completionPart), percent(focusPart), percent(accuracyPart));
    }

    private static Integer percent(Double part) {
        return part == null ? null : (int) Math.round(part * 100);
    }

    /** Nothing was planned and nothing was timed. */
    public boolean isEmpty() { return plannedTasks == 0 && focusMinutes == 0; }

    /** Encouragement line, shown first. */
    public String encouragement() {
        if (plannedTasks > 0 && completedTasks == plannedTasks) return "You finished everything you planned yesterday. Amazing!";
        if (completedTasks > 0) return "You finished " + completedTasks + (completedTasks == 1 ? " task" : " tasks") + " yesterday. Steady progress!";
        if (plannedTasks > 0) return "Yesterday didn't go as planned, and that's okay. Today is a fresh start.";
        if (focusMinutes > 0) return "You focused for " + formatMinutes(focusMinutes) + " yesterday. Keep it up!";
        return "A brand new day. You've got this!";
    }

    /** Reminder line, shown after the encouragement; null when there is nothing to say. */
    public String reminder() {
        if (overrun != null) {
            return "\"" + overrun.title + "\" was estimated at " + formatMinutes(overrun.estimatedMinutes)
                    + " but took " + formatMinutes(overrun.actualMinutes)
                    + ".\nLeave a little extra time for similar tasks today.";
        }
        if (estimatesAccurate) return "Your time estimates were spot on yesterday. Keep that rhythm.";
        return null;
    }

    public static String scoreLabel(int score) {
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Great";
        if (score >= 60) return "Good";
        return "Keep going";
    }

    public static String formatMinutes(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
