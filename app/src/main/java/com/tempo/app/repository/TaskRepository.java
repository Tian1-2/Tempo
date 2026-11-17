package com.tempo.app.repository;

import android.os.Handler;
import android.os.Looper;

import com.tempo.app.database.TaskDao;
import com.tempo.app.database.TaskFeedbackDao;
import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskRepository {

    // Background executor: every database call runs here
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Main-thread handler: delivers results back to the UI thread
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final TaskDao taskDao;
    private final TaskFeedbackDao feedbackDao;

    public TaskRepository(TaskDao taskDao, TaskFeedbackDao feedbackDao) {
        this.taskDao = taskDao;
        this.feedbackDao = feedbackDao;
    }

    // ==================== Callback ====================
    // Database work happens off the UI thread, so results cannot be returned directly;
    // they are delivered through this callback.
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    // ==================== Tasks ====================

    public void addTask(Task task, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                long id = taskDao.insertTask(task);
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void updateTask(Task task, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int rows = taskDao.updateTask(task);
                mainHandler.post(() -> callback.onSuccess(rows));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void deleteTask(int taskId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int rows = taskDao.deleteTask(taskId);
                mainHandler.post(() -> callback.onSuccess(rows));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getAllTasks(Callback<List<Task>> callback) {
        executor.execute(() -> {
            try {
                List<Task> list = taskDao.getAllTasks();
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getPendingTasks(Callback<List<Task>> callback) {
        executor.execute(() -> {
            try {
                List<Task> list = taskDao.getPendingTasks();
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getTaskById(int taskId, Callback<Task> callback) {
        executor.execute(() -> {
            try {
                Task task = taskDao.getTaskById(taskId);
                mainHandler.post(() -> callback.onSuccess(task));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // ==================== Feedback (delegates to TaskFeedbackDao) ====================

    public void addFeedback(TaskFeedback fb, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                long id = feedbackDao.insertFeedback(fb);
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getFeedbackByTaskId(int taskId, Callback<List<TaskFeedback>> callback) {
        executor.execute(() -> {
            try {
                List<TaskFeedback> list = feedbackDao.getFeedbackByTaskId(taskId);
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // Synchronous version for the scheduler, which already runs on a background thread.
    // Never call it on the main thread: it would block the UI.
    public List<Task> getPendingTasksSync() {
        return taskDao.getPendingTasks();
    }

    // Synchronous helpers for the home page (background thread only).
    public List<Task> getAllTasksSync() {
        return taskDao.getAllTasks();
    }

    public Task getTaskByIdSync(int taskId) {
        return taskDao.getTaskById(taskId);
    }

    public List<TaskFeedback> getAllFeedbackSync() {
        return feedbackDao.getAllFeedback();
    }
}