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
 * Entry point for pages: runs {@link ScheduleService} serially in the background and posts the result
 * to the main thread. Only "today" is scheduled, because the generator is single-day and scheduling
 * several dates would place the same task more than once.
 */
public final class AutoScheduler {
    private static final String TAG = "AutoScheduler";
    public static final String NO_PROFILE = "Save your daily routine in Profile first to enable auto-scheduling.";

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

    /** Regenerates today's automatic schedule; manually edited and completed slots are kept. Listener may be null. */
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
                post(listener, null, "Auto-scheduling failed: " + e.getMessage());
            }
        });
    }

    /** Minutes of the task that did not fit in this result; 0 when fully scheduled. */
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
