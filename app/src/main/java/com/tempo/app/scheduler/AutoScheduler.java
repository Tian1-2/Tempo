package com.tempo.app.scheduler;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tempo.app.repository.RepositoryProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 页面调用的排程入口（A 负责的接线）：在后台串行运行 {@link ScheduleService}，结果回到主线程。
 * 目前只排"今天"：排程器是单日算法，同一任务排到多个日期会重复。
 */
public final class AutoScheduler {
    private static final String TAG = "AutoScheduler";
    public static final String NO_PROFILE = "请先在用户档案里保存作息，才能自动排程";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Listener {
        void onScheduled(ScheduleGenerator.Result result);
        void onFailed(String message);
    }

    private AutoScheduler() {}

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** 重新生成今天的自动排程；手动调整过和已完成的时段会保留。listener 可为 null。 */
    public static void regenerateToday(Listener listener) {
        String date = today();
        EXECUTOR.execute(() -> {
            try {
                if (RepositoryProvider.getUserProfileRepository().getProfileSync() == null) {
                    post(listener, null, NO_PROFILE);
                    return;
                }
                ScheduleGenerator.Result result =
                        new ScheduleService().generateAndSave(date, new DefaultScheduleConstraints());
                post(listener, result, null);
            } catch (Exception e) {
                Log.w(TAG, "Schedule generation failed for " + date, e);
                post(listener, null, "自动排程失败：" + e.getMessage());
            }
        });
    }

    /** 本次结果里该任务还差多少分钟没排进去；全部排好返回 0。 */
    public static int remainingMinutes(ScheduleGenerator.Result result, long taskId) {
        if (result == null) return 0;
        for (ScheduleGenerator.UnscheduledTask task : result.unscheduledTasks) {
            if (task.taskId == taskId) return task.remainingMinutes;
        }
        return 0;
    }

    private static void post(Listener listener, ScheduleGenerator.Result result, String error) {
        if (listener == null) return;
        MAIN.post(() -> {
            if (error != null) listener.onFailed(error);
            else listener.onScheduled(result);
        });
    }
}
