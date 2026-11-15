package com.tempo.app;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tempo.app.model.Task;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DB_TEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 先初始化 Provider（必须在使用 Repository 之前）
        RepositoryProvider.init(this);

        // 2. 原来的布局代码保持不动
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 3. 数据库链路测试
        runDatabaseTest();
    }

    private void runDatabaseTest() {
        TaskRepository repo = RepositoryProvider.getTaskRepository();

        // 3.1 先插一条测试任务
        Task test = new Task(
                "Test Task",
                "Study",
                60,                          // estimatedDuration
                3,                           // difficulty
                4,                           // importance
                System.currentTimeMillis() + 86400000L  // 明天
        );

        repo.addTask(test, new TaskRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long id) {
                Log.d(TAG, "inserted id = " + id);

                // 3.2 插入成功后立刻查 pending 列表
                repo.getPendingTasks(new TaskRepository.Callback<List<Task>>() {
                    @Override
                    public void onSuccess(List<Task> tasks) {
                        Log.d(TAG, "pending count = " + tasks.size());
                        for (Task t : tasks) {
                            Log.d(TAG, "  id=" + t.getTaskId()
                                    + " title=" + t.getTitle()
                                    + " status=" + t.getStatus()
                                    + " deadline=" + t.getDeadline());
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "getPendingTasks error", e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "addTask error", e);
            }
        });
    }
}