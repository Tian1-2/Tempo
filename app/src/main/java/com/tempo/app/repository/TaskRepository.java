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

    // 后台线程池：所有数据库操作都丢到这里执行
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // 主线程 Handler：把结果回调回 UI 线程
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final TaskDao taskDao;
    private final TaskFeedbackDao feedbackDao;

    public TaskRepository(TaskDao taskDao, TaskFeedbackDao feedbackDao) {
        this.taskDao = taskDao;
        this.feedbackDao = feedbackDao;
    }

    // ==================== 回调接口 ====================
    // 因为数据库操作在后台线程，结果不能直接 return，
    // 必须通过 callback 回传。第一版只定义两个通用回调。
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    // ==================== Task 相关 ====================

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

    // ==================== Feedback 相关（转发给 TaskFeedbackDao）====================

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

    // 给 C 用的同步版本（C 的算法本身就在后台线程里跑，不需要再切线程）
    // 注意：只能在非主线程调用，否则会阻塞 UI
    public List<Task> getPendingTasksSync() {
        return taskDao.getPendingTasks();
    }

    // Synchronous helpers for the home page and task completion (background thread only).
    public List<Task> getAllTasksSync() {
        return taskDao.getAllTasks();
    }

    public Task getTaskByIdSync(int taskId) {
        return taskDao.getTaskById(taskId);
    }

    public int updateTaskSync(Task task) {
        return taskDao.updateTask(task);
    }

    public List<TaskFeedback> getAllFeedbackSync() {
        return feedbackDao.getAllFeedback();
    }

    public long addFeedbackSync(TaskFeedback feedback) {
        return feedbackDao.insertFeedback(feedback);
    }
}