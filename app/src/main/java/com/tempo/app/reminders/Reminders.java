package com.tempo.app.reminders;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;

import com.tempo.app.repository.RepositoryProvider;

/**
 * "Up next" reminders: one alarm for the next planned slot, {@link #leadMinutes} before it starts.
 * Re-armed after every re-plan, after each reminder, on app start and after a reboot.
 */
public final class Reminders {
    public static final String CHANNEL = "task_reminders";
    static final String PREFS = "tempo_reminders";
    static final String ACTION_FIRE = "com.tempo.app.REMINDER";
    static final String EXTRA_SLOT = "scheduleId";
    public static final int[] LEAD_CHOICES = {0, 5, 10, 15};

    private Reminders() { }

    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }
    public static int leadMinutes(Context c) { return prefs(c).getInt("lead", 5); }

    public static void setEnabled(Context c, boolean on) { prefs(c).edit().putBoolean("enabled", on).apply(); refresh(c); }
    public static void setLeadMinutes(Context c, int minutes) { prefs(c).edit().putInt("lead", minutes).apply(); refresh(c); }

    /** Android 13+ needs the runtime permission before notifications can show. */
    public static boolean canNotify(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("A heads-up shortly before a planned task starts");
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** Re-arms the alarm on a background thread. Safe to call often. */
    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> refreshSync(app), "reminders").start();
    }

    static void refreshSync(Context c) {
        RepositoryProvider.init(c);
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent pending = pendingFor(c, -1);
        alarms.cancel(pending);
        if (!enabled(c)) return;
        long now = System.currentTimeMillis();
        long lead = leadMinutes(c) * 60_000L;
        long notifiedSlot = prefs(c).getLong("notifiedSlot", -1), notifiedStart = prefs(c).getLong("notifiedStart", -1);
        try (Cursor cur = RepositoryProvider.getDatabaseHelper().getReadableDatabase().rawQuery(
                "SELECT s.schedule_id, s.start_time FROM schedule_items s JOIN tasks t ON t.task_id = s.task_id "
                        + "WHERE s.status = 'PLANNED' AND t.archived = 0 AND t.status IN ('PENDING','IN_PROGRESS') AND s.start_time > ? "
                        // No second reminder for the next part of the same task right after its break.
                        + "AND NOT EXISTS (SELECT 1 FROM schedule_items p WHERE p.task_id = s.task_id "
                        + "AND p.schedule_id <> s.schedule_id AND p.status IN ('PLANNED','COMPLETED') "
                        + "AND p.end_time <= s.start_time AND p.end_time >= s.start_time - 900000) "
                        + "ORDER BY s.start_time LIMIT 5", new String[]{String.valueOf(now)})) {
            while (cur.moveToNext()) {
                long id = cur.getLong(0), start = cur.getLong(1);
                if (id == notifiedSlot && start == notifiedStart) continue; // already reminded
                long at = Math.max(now + 1_000L, start - lead);
                PendingIntent fire = pendingFor(c, id);
                if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
                } else {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
                }
                return;
            }
        }
    }

    private static PendingIntent pendingFor(Context c, long slotId) {
        Intent intent = new Intent(c, ReminderReceiver.class).setAction(ACTION_FIRE).putExtra(EXTRA_SLOT, slotId);
        return PendingIntent.getBroadcast(c, 7506, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
