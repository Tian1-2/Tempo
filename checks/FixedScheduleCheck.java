import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.*;
import com.tempo.app.model.*;
import java.util.*;

/** task-a → V6 migration (fixed_events into the profile, DELETED into archived) and the keep-upcoming re-plan delete rule, on real SQLite. */
public final class FixedScheduleCheck {
    private static int count;
    private static SQLiteDatabase db;
    private static final long M = 60_000;
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try (AppDatabaseHelper helper = new AppDatabaseHelper(new Context())) {
            db = helper.getWritableDatabase();
            // task-a V4/V5 kept weekly commitments in a fixed_events table; V6 moves them into the profile.
            UserProfile profile = new UserProfile();
            profile.setWakeTime("07:00"); profile.setSleepTime("23:00");
            profile.setFocusDuration(25); profile.setBreakDuration(5); profile.setDailyCapacity(240);
            profile.setFixedBlocks("Fri 09:00-10:00\tStand-up");
            UserProfileDao profiles = new UserProfileDao(helper);
            profiles.saveProfile(profile);
            db.execSQL("CREATE TABLE fixed_events (event_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, "
                    + "day_of_week INTEGER NOT NULL, start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL)");
            db.execSQL("INSERT INTO fixed_events(title, day_of_week, start_minute, end_minute) VALUES "
                    + "('COMP7506 lecture', 2, 840, 960), ('Sunday choir', 7, 600, 660)");
            Task gone = new Task("Old deleted task", "Study", 30, 2, 2, 0);
            gone.setTaskId((int) new TaskDao(helper).insertTask(gone));
            db.execSQL("UPDATE tasks SET status='DELETED' WHERE task_id=" + gone.getTaskId());
            helper.onUpgrade(db, 5, 6);
            List<com.tempo.app.scheduler.FixedBlocks.Entry> blocks =
                    com.tempo.app.scheduler.FixedBlocks.entries(profiles.getProfile().getFixedBlocks());
            check(blocks.size() == 3, "fixed events appended to the profile's existing commitments");
            check(blocks.get(0).day == 2 && blocks.get(0).start == 840 && blocks.get(0).end == 960
                    && "COMP7506 lecture".equals(blocks.get(0).title()), "Tuesday lecture keeps its day, time and name");
            check(blocks.get(blocks.size() - 1).day == 0 && "Sunday choir".equals(blocks.get(blocks.size() - 1).title()),
                    "ISO Sunday (7) maps to FixedBlocks Sunday (0)");
            check(number("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='fixed_events'") == 0, "fixed_events dropped after the move");
            check(number("SELECT archived FROM tasks WHERE task_id=" + gone.getTaskId()) == 1
                    && number("SELECT COUNT(*) FROM tasks WHERE status='DELETED'") == 0, "DELETED task becomes archived");
            helper.onUpgrade(db, 4, 6); // integration V4 → V6 (and a second run) must be harmless
            check(com.tempo.app.scheduler.FixedBlocks.entries(profiles.getProfile().getFixedBlocks()).size() == 3, "upgrade is idempotent");

            Task task = new Task("Essay", "Study", 120, 3, 3, 0);
            task.setTaskId((int) new TaskDao(helper).insertTask(task));
            ScheduleDao schedules = new ScheduleDao(helper);
            long missed = slot(schedules, task, 9 * 60, 10 * 60, false);
            long upcoming = slot(schedules, task, 14 * 60, 15 * 60, false);
            long manual = slot(schedules, task, 8 * 60, 9 * 60, true);
            ScheduleItem fresh = new ScheduleItem();
            fresh.setTaskId(task.getTaskId()); fresh.setScheduledDate("2026-11-17");
            fresh.setStartTime(11 * 60 * M); fresh.setEndTime(12 * 60 * M); fresh.setStatus("PLANNED");
            schedules.replaceMissedGeneratedForDate("2026-11-17", Collections.singletonList(fresh), 10 * 60 * M + M);
            check(number("SELECT COUNT(*) FROM schedule_items WHERE schedule_id=" + missed) == 0, "missed automatic slot removed");
            check(number("SELECT COUNT(*) FROM schedule_items WHERE schedule_id=" + upcoming) == 1, "upcoming automatic slot kept with its id");
            check(number("SELECT COUNT(*) FROM schedule_items WHERE schedule_id=" + manual) == 1, "manual slot kept");
            check(number("SELECT COUNT(*) FROM schedule_items WHERE start_time=" + 11 * 60 * M) == 1, "re-placed work inserted");
            schedules.replaceGeneratedForDate("2026-11-17", Collections.<ScheduleItem>emptyList(), Long.MIN_VALUE);
            check(number("SELECT COUNT(*) FROM schedule_items WHERE is_manually_edited=0 AND status='PLANNED'") == 0
                    && number("SELECT COUNT(*) FROM schedule_items WHERE schedule_id=" + manual) == 1, "full re-plan still clears every automatic slot");
        }
        System.out.println("PASS: " + count + " V6 migration and stable re-plan assertions on real SQLite.");
    }
    private static long slot(ScheduleDao dao, Task task, int from, int to, boolean manual) {
        ScheduleItem item = new ScheduleItem();
        item.setTaskId(task.getTaskId()); item.setScheduledDate("2026-11-17");
        item.setStartTime(from * M); item.setEndTime(to * M); item.setManuallyEdited(manual); item.setStatus("PLANNED");
        return dao.insertScheduleItem(item);
    }
    private static long number(String sql) { try (Cursor c = db.rawQuery(sql, null)) { c.moveToFirst(); return c.getLong(0); } }
    private static void check(boolean ok, String message) { count++; if (!ok) throw new AssertionError(message); }
}
