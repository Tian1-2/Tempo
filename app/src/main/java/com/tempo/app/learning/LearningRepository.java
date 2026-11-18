package com.tempo.app.learning;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.tempo.app.database.AppDatabaseHelper;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.model.Task;
import com.tempo.app.database.TaskDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small async facade. Unique submission tokens also protect retries after process death. */
public final class LearningRepository {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    public LearningRepository(Context context) { this.context = context.getApplicationContext(); }
    private interface Work<T> { T run(LearningStore store); }
    private <T> void run(Work<T> work, TaskRepository.Callback<T> callback) {
        WORKER.execute(() -> {
            try (AppDatabaseHelper helper = new AppDatabaseHelper(context)) {
                T result = work.run(new LearningStore(helper));
                main.post(() -> { if (!closed) callback.onSuccess(result); });
            } catch (Exception e) { main.post(() -> { if (!closed) callback.onError(e); }); }
        });
    }
    public void prepare(int taskId, TaskRepository.Callback<LearningStore.Prepared> callback) {
        run(store -> store.prepare(taskId, System.currentTimeMillis()), callback);
    }
    public void refresh(TaskRepository.Callback<Void> callback) {
        run(store -> { store.refreshPredictions(System.currentTimeMillis()); return null; }, callback);
    }
    public void receipt(String token, int taskId, TaskRepository.Callback<LearningStore.Saved> callback) {
        run(store -> store.receipt(token, taskId), callback);
    }
    public void save(String token, int taskId, int minutes, int rating, boolean complete, Integer remaining,
                     TaskRepository.Callback<LearningStore.Saved> callback) {
        run(store -> store.save(token, taskId, minutes, rating, complete, remaining, System.currentTimeMillis()), callback);
    }
    public void save(String token,int taskId,Integer minutes,int rating,boolean complete,Integer remaining,
                     String source,String segments,boolean trusted,TaskRepository.Callback<LearningStore.Saved> callback) {
        run(store->store.save(token,taskId,minutes,rating,complete,remaining,source,segments,trusted,System.currentTimeMillis()),callback);
    }
    public void editContext(int taskId,TaskRepository.Callback<LearningStore.EditContext> callback) {run(store->store.editContext(taskId),callback);}
    public void updateTask(Task task,Integer remaining,TaskRepository.Callback<Integer> callback) {
        run(store->store.updateTask(task,remaining,System.currentTimeMillis()),callback);
    }
    public void pendingTasks(TaskRepository.Callback<List<Task>> callback) {run(LearningStore::pendingTasks,callback);}
    public void history(TaskRepository.Callback<LearningStore.History> callback) {
        run(store -> store.history(System.currentTimeMillis()), callback);
    }
    public void correct(long id,int revision,Integer minutes,int rating,TaskRepository.Callback<Void> callback) {
        run(store->{store.correctRecord(id,revision,minutes,rating,System.currentTimeMillis());return null;},callback);
    }
    public void close() { closed = true; main.removeCallbacksAndMessages(null); }
}
