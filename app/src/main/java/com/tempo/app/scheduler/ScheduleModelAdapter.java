package com.tempo.app.scheduler;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.scheduler.ScheduleGenerator.Interval;
import com.tempo.app.scheduler.ScheduleGenerator.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/** Converts the database teammate's models without coupling the algorithm to SQLite. */
public final class ScheduleModelAdapter {
    private ScheduleModelAdapter() {}

    public static List<ScheduleGenerator.Task> pendingTasks(List<Task> source) {
        return pendingTasks(source, Collections.<Integer, Integer>emptyMap());
    }

    public static List<ScheduleGenerator.Task> pendingTasks(List<Task> source, Map<Integer, Integer> remaining) {
        if (source == null) throw new IllegalArgumentException("Tasks are null");
        List<ScheduleGenerator.Task> result = new ArrayList<>();
        for (Task task : source) {
            if (task == null) throw new IllegalArgumentException("Null task");
            if (!"PENDING".equals(task.getStatus()) && !"IN_PROGRESS".equals(task.getStatus())) continue;
            int minutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
            minutes = remaining.getOrDefault(task.getTaskId(), minutes);
            if (minutes <= 0) throw new IllegalArgumentException("Task has no positive duration: " + task.getTaskId());
            result.add(new ScheduleGenerator.Task(task.getTaskId(), minutes,
                    task.getDeadline() > 0 ? task.getDeadline() : null,
                    task.getImportance(), task.getDifficulty()));
        }
        return result;
    }

    /**
     * Multi-day planning: removes minutes a task already has planned on other dates, so the same
     * work is not planned twice. A task keeps at least the minutes locked on this date; a task with
     * nothing left (and nothing locked here) is dropped.
     */
    public static List<ScheduleGenerator.Task> subtractPlanned(List<ScheduleGenerator.Task> tasks,
            Map<Long, Integer> plannedElsewhere, Map<Long, Integer> lockedHere) {
        List<ScheduleGenerator.Task> result = new ArrayList<>();
        for (ScheduleGenerator.Task task : tasks) {
            int elsewhere = plannedElsewhere.getOrDefault(task.id, 0);
            int locked = lockedHere.getOrDefault(task.id, 0);
            int minutes = Math.max(task.durationMinutes - elsewhere, locked);
            if (minutes <= 0) continue;
            // A few minutes left over for a task that is already planned on other days come from
            // estimate rounding; a separate 1–9 minute slot would only clutter the schedule.
            if (elsewhere > 0 && locked == 0 && minutes < ScheduleGenerator.MIN_SESSION_MINUTES) continue;
            result.add(minutes == task.durationMinutes ? task : new ScheduleGenerator.Task(task.id, minutes,
                    task.deadlineMillis, task.importance, task.difficulty));
        }
        return result;
    }

    /** Only manually fixed past slots remain planned after regenerating automatic work. */
    public static Map<Long, Integer> retainedPastMinutes(List<ScheduleItem> existing, long earliest) {
        Map<Long, Integer> minutes = new HashMap<>();
        for (ScheduleItem item : existing) {
            if ("PLANNED".equals(item.getStatus()) && item.isManuallyEdited()
                    && item.getStartTime() < earliest) {
                int duration = (int) ((item.getEndTime() - item.getStartTime()) / 60_000L);
                minutes.merge((long) item.getTaskId(), duration, Integer::sum);
            }
        }
        return minutes;
    }

    public static List<Session> lockedSessions(List<ScheduleItem> existing) { return lockedSessions(existing, Long.MIN_VALUE); }

    public static List<Session> lockedSessions(List<ScheduleItem> existing, long earliest) {
        if (existing == null) throw new IllegalArgumentException("Schedule items are null");
        List<Session> result = new ArrayList<>();
        for (ScheduleItem item : existing) {
            if (item == null) throw new IllegalArgumentException("Null schedule item");
            if (item.isManuallyEdited() && "PLANNED".equals(item.getStatus()) && item.getStartTime() >= earliest) {
                result.add(new Session(item.getTaskId(), new Interval(item.getStartTime(), item.getEndTime()), true));
            }
        }
        return result;
    }

    /**
     * Automatic PLANNED slots that have not started yet, returned as locked sessions so the
     * generator keeps them in place. {@link #newScheduleItems} skips locked sessions, so their
     * existing rows are not inserted twice.
     */
    public static List<Session> upcomingAutoSessions(List<ScheduleItem> existing, long earliest) {
        if (existing == null) throw new IllegalArgumentException("Schedule items are null");
        List<Session> result = new ArrayList<>();
        for (ScheduleItem item : existing) {
            if (item != null && !item.isManuallyEdited() && "PLANNED".equals(item.getStatus())
                    && item.getStartTime() >= earliest && item.getStartTime() < item.getEndTime()) {
                result.add(new Session(item.getTaskId(), new Interval(item.getStartTime(), item.getEndTime()), true));
            }
        }
        return result;
    }

    /** Convert only newly generated sessions. Existing locked rows retain their database IDs. */
    public static List<ScheduleItem> newScheduleItems(String date, ScheduleGenerator.Result result) {
        if (date == null || result == null) throw new IllegalArgumentException("Date or result is null");
        List<ScheduleItem> items = new ArrayList<>();
        for (Session session : result.sessions) {
            if (session.locked) continue;
            ScheduleItem item = new ScheduleItem();
            item.setTaskId(Math.toIntExact(session.taskId));
            item.setScheduledDate(date);
            item.setStartTime(session.interval.start);
            item.setEndTime(session.interval.end);
            item.setManuallyEdited(false);
            item.setStatus("PLANNED");
            items.add(item);
        }
        return items;
    }
}
