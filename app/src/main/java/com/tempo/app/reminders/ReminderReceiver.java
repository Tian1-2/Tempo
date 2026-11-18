package com.tempo.app.reminders;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import com.tempo.app.FocusSessionState;
import com.tempo.app.FocusTimerActivity;
import com.tempo.app.HomeActivity;
import com.tempo.app.R;
import com.tempo.app.SchedulePreviewActivity;
import com.tempo.app.repository.RepositoryProvider;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Posts the "up next" notification for one slot, then arms the next reminder. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Reminders.ACTION_FIRE.equals(intent.getAction())) return;
        long slotId = intent.getLongExtra(Reminders.EXTRA_SLOT, -1);
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try { post(app, slotId); Reminders.refreshSync(app); }
            finally { result.finish(); }
        }, "reminder-fire").start();
    }

    private static void post(Context c, long slotId) {
        if (slotId < 0 || !Reminders.enabled(c)) return;
        RepositoryProvider.init(c);
        int taskId; String title; long start, end; int minutes;
        try (Cursor cur = RepositoryProvider.getDatabaseHelper().getReadableDatabase().rawQuery(
                "SELECT t.task_id, t.title, s.start_time, s.end_time, t.predicted_duration, t.estimated_duration "
                        + "FROM schedule_items s JOIN tasks t ON t.task_id = s.task_id "
                        + "WHERE s.schedule_id = ? AND s.status = 'PLANNED' AND t.status IN ('PENDING','IN_PROGRESS')",
                new String[]{String.valueOf(slotId)})) {
            if (!cur.moveToFirst()) return; // plan changed since the alarm was set
            taskId = cur.getInt(0); title = cur.getString(1); start = cur.getLong(2); end = cur.getLong(3);
            minutes = cur.getInt(4) > 0 ? cur.getInt(4) : cur.getInt(5);
        }
        Reminders.prefs(c).edit().putLong("notifiedSlot", slotId).putLong("notifiedStart", start).apply();
        if (start < System.currentTimeMillis() - 60_000L) return; // too late to be useful
        boolean timerBusy = c.getSharedPreferences(FocusSessionState.PREFS, Context.MODE_PRIVATE)
                .getInt("taskId", Integer.MIN_VALUE) != Integer.MIN_VALUE;
        if (!Reminders.canNotify(c)) return;
        Reminders.ensureChannel(c);

        // Tapping opens the focus timer for this task (Home underneath), or the schedule if
        // another session is still open and must be finished first.
        Intent home = new Intent(c, HomeActivity.class);
        Intent target;
        if (timerBusy) {
            Calendar day = Calendar.getInstance(); day.setTimeInMillis(start);
            target = new Intent(c, SchedulePreviewActivity.class).putExtra("selectedDayMillis", day.getTimeInMillis());
        } else {
            target = new Intent(c, FocusTimerActivity.class)
                    .putExtra("taskId", taskId).putExtra("title", title).putExtra("targetMinutes", minutes);
        }
        PendingIntent open = PendingIntent.getActivities(c, (int) slotId, new Intent[]{
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK), target},
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Notification.Builder builder = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, Reminders.CHANNEL) : new Notification.Builder(c);
        builder.setSmallIcon(R.drawable.ic_stat_tempo)
                .setContentTitle(title)
                // The header already counts down to the start ("in 4m"), so the text gives the slot.
                .setContentText(clock.format(new Date(start)) + "–" + clock.format(new Date(end))
                        + (timerBusy ? " · Finish your open session first" : " · Tap to start focusing"))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setWhen(start).setShowWhen(true);
        c.getSystemService(NotificationManager.class).notify("task-reminder", 1, builder.build());
    }
}
