package com.tempo.app.insights;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import com.tempo.app.database.AppDatabaseHelper;
import com.tempo.app.database.TaskDao;
import com.tempo.app.database.TaskFeedbackDao;

import java.util.TimeZone;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.learning.LearningSummary;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reuses B's DAOs without changing their API, schema or write behavior. */
public final class InsightsRepository {
    public interface Callback {
        void onLoaded(InsightsStats stats, LearningSummary learning);
        void onError();
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    public InsightsRepository(Context context) { this.context = context.getApplicationContext(); }

    public void load(Callback callback) {
        if (closed) return;
        executor.execute(() -> {
            try (AppDatabaseHelper helper = new AppDatabaseHelper(context)) {
                SQLiteDatabase db = helper.getReadableDatabase();
                InsightsStats stats;
                LearningSummary learning;
                // A consistent snapshot of both tables. No records are inserted or updated.
                db.beginTransactionNonExclusive();
                try {
                    stats = InsightsStats.calculate(new TaskDao(helper).getTasksWithHistory(),
                            new TaskFeedbackDao(helper).getAllFeedback(),
                            System.currentTimeMillis(), TimeZone.getDefault());
                    learning = new LearningStore(helper).summary(System.currentTimeMillis());
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                main.post(() -> { if (!closed) callback.onLoaded(stats, learning); });
            } catch (Exception error) {
                main.post(() -> { if (!closed) callback.onError(); });
            }
        });
    }

    public void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        executor.shutdown();
    }
}
