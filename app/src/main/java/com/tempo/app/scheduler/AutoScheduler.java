package com.tempo.app.scheduler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.tempo.app.model.Task;
import com.tempo.app.model.UserProfile;
import com.tempo.app.planning.GenerationPreflight;
import com.tempo.app.repository.RepositoryProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for pages: runs {@link ScheduleService} serially in the background and posts the result
 * to the main thread. Plans a rolling window of days; see {@link ScheduleService#generateRange}.
 */
public final class AutoScheduler {
    private static final String TAG = "AutoScheduler";
    public static final String NO_PROFILE = "Save your daily routine in Profile first to enable auto-scheduling.";
    public static final String TIMER_ACTIVE = "A focus timer is running. Today's plan will update after you finish it.";
    /** Same key the focus timer (and D's generate entry) uses to mark an active session. */
    private static final String TIMER_PREFS = "tempo_focus_timer";

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

    /**
     * Re-plans today and the next days ({@link ScheduleService#HORIZON_DAYS}); manually edited, past
     * and completed slots are kept, and work that does not fit today continues on later days.
     * Skipped while a focus timer is running (same rule as the schedule page's Generate button).
     * Listener may be null; onScheduled receives null when there was nothing to schedule.
     */
    public static void replan(Context context, Listener listener) {
        if (context.getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE)
                .getInt("taskId", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
            post(listener, null, TIMER_ACTIVE);
            return;
        }
        String date = today();
        EXECUTOR.execute(() -> {
            try {
                UserProfile profile = RepositoryProvider.getUserProfileRepository().getProfileSync();
                if (profile == null) {
                    post(listener, null, NO_PROFILE);
                    return;
                }
                List<Task> pending = RepositoryProvider.getTaskRepository().getPendingTasksSync();
                try {
                    GenerationPreflight.check(profile, pending);
                } catch (GenerationPreflight.Problem problem) {
                    if (problem.action == GenerationPreflight.Action.ADD_TASK) {
                        post(listener, null, null); // nothing pending: nothing to plan
                    } else {
                        post(listener, null, problem.getMessage());
                    }
                    return;
                }
                ScheduleGenerator.Result result = new ScheduleService()
                        .generateRange(ScheduleService.HORIZON_DAYS, new DefaultScheduleConstraints());
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
