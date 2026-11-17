package com.tempo.app.repository;

import android.os.Handler;
import android.os.Looper;

import com.tempo.app.database.ScheduleDao;
import com.tempo.app.model.ScheduleItem;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleRepository {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ScheduleDao scheduleDao;

    public ScheduleRepository(ScheduleDao scheduleDao) {
        this.scheduleDao = scheduleDao;
    }

    // 复用 TaskRepository.Callback
    public void saveSchedule(List<ScheduleItem> items,
                             TaskRepository.Callback<Void> callback) {
        executor.execute(() -> {
            try {
                scheduleDao.insertScheduleItems(items);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getScheduleByDate(String date,
                                  TaskRepository.Callback<List<ScheduleItem>> callback) {
        executor.execute(() -> {
            try {
                List<ScheduleItem> list = scheduleDao.getScheduleByDate(date);
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void getScheduleByTaskId(int taskId,
                                    TaskRepository.Callback<List<ScheduleItem>> callback) {
        executor.execute(() -> {
            try {
                List<ScheduleItem> list = scheduleDao.getScheduleByTaskId(taskId);
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void deleteScheduleByTaskId(int taskId,
                                       TaskRepository.Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int rows = scheduleDao.deleteScheduleByTaskId(taskId);
                mainHandler.post(() -> callback.onSuccess(rows));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // 给 C 用的同步版本（C 在后台线程调）
    public void saveScheduleSync(List<ScheduleItem> items) {
        scheduleDao.insertScheduleItems(items);
    }

    public List<ScheduleItem> getScheduleByDateSync(String date) {
        return scheduleDao.getScheduleByDate(date);
    }

    public void replaceGeneratedForDateSync(String date, List<ScheduleItem> items) {
        scheduleDao.replaceGeneratedForDate(date, items);
    }

    public void replaceGeneratedForDateSync(String date, List<ScheduleItem> items, long preserveBefore) {
        scheduleDao.replaceGeneratedForDate(date, items, preserveBefore);
    }

    public void updatePlannedTime(int scheduleId, String date, long start, long end,
                                  TaskRepository.Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                int rows = scheduleDao.updatePlannedTime(scheduleId, date, start, end);
                mainHandler.post(() -> callback.onSuccess(rows));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }
}
