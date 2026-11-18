package com.tempo.app;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;

import com.tempo.app.ui.Ui;

/**
 * Picks the light or dark palette before any page builds its views, and again when the system
 * setting changes (pages are then recreated by Android and read the new colours).
 */
public class TempoApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Ui.applyTheme(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityPreCreated(Activity activity, Bundle state) { Ui.applyTheme(activity); }
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        Ui.applyTheme(this);
    }
}
