package com.tempo.app.scheduler;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.scheduler.ScheduleGenerator.Interval;
import com.tempo.app.scheduler.ScheduleGenerator.Session;

import java.util.ArrayList;
import java.util.List;

/** Converts the database teammate's models without coupling the algorithm to SQLite. */
public final class ScheduleModelAdapter {
    private ScheduleModelAdapter() {}

    public static List<ScheduleGenerator.Task> pendingTasks(List<Task> source) {
        if (source == null) throw new IllegalArgumentException("Tasks are null");
        List<ScheduleGenerator.Task> result = new ArrayList<>();
        for (Task task : source) {
            if (task == null) throw new IllegalArgumentException("Null task");
            if (!"PENDING".equals(task.getStatus()) && !"IN_PROGRESS".equals(task.getStatus())) continue;
            int minutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
            if (minutes <= 0) throw new IllegalArgumentException("Task has no positive duration: " + task.getTaskId());
            result.add(new ScheduleGenerator.Task(task.getTaskId(), minutes,
                    task.getDeadline() > 0 ? task.getDeadline() : null,
                    task.getImportance(), task.getDifficulty()));
        }
        return result;
    }

    public static List<Session> lockedSessions(List<ScheduleItem> existing) {
        if (existing == null) throw new IllegalArgumentException("Schedule items are null");
        List<Session> result = new ArrayList<>();
        for (ScheduleItem item : existing) {
            if (item == null) throw new IllegalArgumentException("Null schedule item");
            if (item.isManuallyEdited() && "PLANNED".equals(item.getStatus())) {
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
