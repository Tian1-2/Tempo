package com.tempo.app.planning;

import com.tempo.app.model.Task;
import com.tempo.app.model.UserProfile;
import java.util.List;

/** Input checks for the new UI entry point. Does not change C's algorithm or any record. */
public final class GenerationPreflight {
    public enum Action { TODAY, PROFILE, ADD_TASK, NONE }
    public static final class Problem extends IllegalArgumentException {
        public final Action action;
        public Problem(String message, Action action) { super(message); this.action = action; }
    }
    private GenerationPreflight() { }

    public static void checkDate(long selectedDay, long today) {
        if (selectedDay < today) throw new Problem("Plans cannot be generated for past dates. Go back to today or choose a future date.", Action.TODAY);
    }

    public static void check(UserProfile profile, List<Task> pendingTasks) {
        if (profile == null) throw new Problem("Save your routine, focus length, break length and daily task time in Profile first.", Action.PROFILE);
        if (!validTime(profile.getWakeTime()) || !validTime(profile.getSleepTime())) {
            throw new Problem("Wake-up and bedtime must use HH:mm, for example 07:00 and 23:30.", Action.PROFILE);
        }
        if (profile.getWakeTime().compareTo(profile.getSleepTime()) >= 0) {
            throw new Problem("Wake-up must be earlier than bedtime on the same day. Please check your profile.", Action.PROFILE);
        }
        if (profile.getFocusDuration() <= 0 || profile.getBreakDuration() < 0 || profile.getDailyCapacity() <= 0) {
            throw new Problem("Focus length and daily task time must be above 0, and breaks cannot be negative.", Action.PROFILE);
        }
        if (pendingTasks.isEmpty()) {
            throw new Problem("There are no pending tasks to plan. Add a task first, then generate the plan.", Action.ADD_TASK);
        }
        for (Task task : pendingTasks) {
            int minutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
            if (minutes <= 0 || task.getDifficulty() < 1 || task.getDifficulty() > 5
                    || task.getImportance() < 1 || task.getImportance() > 5) {
                throw new Problem("\"" + task.getTitle() + "\" needs a positive duration and a difficulty and importance from 1 to 5. Please edit the task.", Action.NONE);
            }
        }
    }

    private static boolean validTime(String value) {
        return value != null && value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");
    }
}
