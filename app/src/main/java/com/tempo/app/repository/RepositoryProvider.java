package com.tempo.app.repository;

import android.content.Context;

import com.tempo.app.database.AppDatabaseHelper;
import com.tempo.app.database.FixedEventDao;
import com.tempo.app.database.ScheduleDao;
import com.tempo.app.database.TaskDao;
import com.tempo.app.database.TaskFeedbackDao;
import com.tempo.app.database.UserProfileDao;

public class RepositoryProvider {

    private static AppDatabaseHelper helper;

    public static void init(Context context) {
        if (helper == null) {
            helper = new AppDatabaseHelper(context.getApplicationContext());
        }
    }

    public static AppDatabaseHelper getDatabaseHelper() { return helper; }

    public static TaskRepository getTaskRepository() {
        return new TaskRepository(
                new TaskDao(helper),
                new TaskFeedbackDao(helper)
        );
    }

    public static ScheduleRepository getScheduleRepository() {
        return new ScheduleRepository(new ScheduleDao(helper));
    }

    /** Weekly classes and meetings; DAO calls must run off the UI thread. */
    public static FixedEventDao getFixedEventDao() { return new FixedEventDao(helper); }

    public static UserProfileRepository getUserProfileRepository() {
        return new UserProfileRepository(new UserProfileDao(helper));
    }

}
