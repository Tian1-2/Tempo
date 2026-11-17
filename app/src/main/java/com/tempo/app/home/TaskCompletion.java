package com.tempo.app.home;

import android.os.Handler;
import android.os.Looper;

import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.ScheduleGenerator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Marks a task as done: stores a COMPLETED feedback row, sets the task status to COMPLETED and turns its
 * PLANNED slots for today into COMPLETED ones (so re-planning keeps them). Then re-plans today, because
 * finishing early frees time for other tasks.
 */
public final class TaskCompletion {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TaskCompletion() {}

    /** @param minutes focus minutes of the last session; 0 stores no time, only the completion. */
    public static void complete(int taskId, int minutes, TaskRepository.Callback<Void> callback) {
        EXECUTOR.execute(() -> {
            try {
                TaskRepository tasks = RepositoryProvider.getTaskRepository();
                Task task = tasks.getTaskByIdSync(taskId);
                if (task == null) throw new IllegalStateException("Task not found");
                TaskFeedback feedback = new TaskFeedback();
                feedback.setTaskId(taskId);
                feedback.setActualDuration(Math.max(0, minutes));
                feedback.setActualDifficulty(0);
                feedback.setCompletionStatus("COMPLETED");
                feedback.setCompletedAt(System.currentTimeMillis());
                tasks.addFeedbackSync(feedback);
                task.setStatus("COMPLETED");
                tasks.updateTaskSync(task);
                RepositoryProvider.getScheduleRepository()
                        .markTaskCompletedOnDateSync(taskId, AutoScheduler.today());
                // The completion is saved either way; re-planning failures (e.g. no profile) are not fatal here.
                AutoScheduler.regenerateToday(new AutoScheduler.Listener() {
                    @Override public void onScheduled(ScheduleGenerator.Result result) { callback.onSuccess(null); }
                    @Override public void onFailed(String message) { callback.onSuccess(null); }
                });
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e));
            }
        });
    }
}
