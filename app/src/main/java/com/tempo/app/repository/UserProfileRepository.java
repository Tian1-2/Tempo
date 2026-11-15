package com.tempo.app.repository;

import android.os.Handler;
import android.os.Looper;

import com.tempo.app.database.UserProfileDao;
import com.tempo.app.model.UserProfile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserProfileRepository {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final UserProfileDao userProfileDao;

    public UserProfileRepository(UserProfileDao userProfileDao) {
        this.userProfileDao = userProfileDao;
    }

    public void getProfile(TaskRepository.Callback<UserProfile> callback) {
        executor.execute(() -> {
            try {
                UserProfile profile = userProfileDao.getProfile();
                mainHandler.post(() -> callback.onSuccess(profile));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void saveProfile(UserProfile profile,
                            TaskRepository.Callback<Long> callback) {
        executor.execute(() -> {
            try {
                long id = userProfileDao.saveProfile(profile);
                mainHandler.post(() -> callback.onSuccess(id));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // 给 C 用的同步版本
    public UserProfile getProfileSync() {
        return userProfileDao.getProfile();
    }
}