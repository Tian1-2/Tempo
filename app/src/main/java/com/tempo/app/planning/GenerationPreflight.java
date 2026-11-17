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
        if (selectedDay < today) throw new Problem("不能为过去的日期生成新计划。请先回到今天，或选择未来日期。", Action.TODAY);
    }

    public static void check(UserProfile profile, List<Task> pendingTasks) {
        if (profile == null) throw new Problem("请先保存用户档案中的作息、单次专注时长、休息时长和每日可用时间。", Action.PROFILE);
        if (!validTime(profile.getWakeTime()) || !validTime(profile.getSleepTime())) {
            throw new Problem("起床和睡觉时间需要使用 HH:mm 格式，例如 07:00、23:30。", Action.PROFILE);
        }
        if (profile.getWakeTime().compareTo(profile.getSleepTime()) >= 0) {
            throw new Problem("当前排程要求起床时间早于同一天的睡觉时间，请检查用户档案。", Action.PROFILE);
        }
        if (profile.getFocusDuration() <= 0 || profile.getBreakDuration() < 0 || profile.getDailyCapacity() <= 0) {
            throw new Problem("单次专注时长和每日可用时间必须大于 0，休息时长不能为负数。", Action.PROFILE);
        }
        if (pendingTasks.isEmpty()) {
            throw new Problem("当前没有可安排的待办任务。请先添加一个任务，再返回日程点击生成。", Action.ADD_TASK);
        }
        for (Task task : pendingTasks) {
            int minutes = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
            if (minutes <= 0 || task.getDifficulty() < 1 || task.getDifficulty() > 5
                    || task.getImportance() < 1 || task.getImportance() > 5) {
                throw new Problem("任务「" + task.getTitle() + "」需要正数的预计用时，以及 1–5 的难度和重要程度。请先修正任务条件。", Action.NONE);
            }
        }
    }

    private static boolean validTime(String value) {
        return value != null && value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");
    }
}
