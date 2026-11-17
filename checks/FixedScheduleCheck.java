import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.*;
import com.tempo.app.model.*;
import java.util.*;

/** Fixed schedule table, V3→V4 migration and the keep-upcoming re-plan delete rule, on real SQLite. */
public final class FixedScheduleCheck {
    private static int count;
    private static SQLiteDatabase db;
    private static final long M = 60_000;
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try (AppDatabaseHelper helper = new AppDatabaseHelper(new Context())) {
            db = helper.getWritableDatabase();
            FixedEventDao fixed = new FixedEventDao(helper);
            long lecture = fixed.insert(new FixedEvent("COMP7506 lecture", 2, 14 * 60, 16 * 60));
            fixed.insert(new FixedEvent("Group meeting", 1, 18 * 60, 19 * 60));
            List<FixedEvent> all = fixed.getAll();
            check(all.size() == 2 && all.get(0).getDayOfWeek() == 1, "fixed events stored and ordered by weekday");
            boolean rejected = false;
            try { fixed.insert(new FixedEvent("Bad", 3, 10 * 60, 9 * 60)); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected && fixed.getAll().size() == 2, "end before start is rejected");
            check(fixed.delete((int) lecture) == 1 && fixed.getAll().size() == 1, "fixed event deleted");

            db.execSQL("DROP TABLE fixed_events");
            helper.onUpgrade(db, 3, 4);
            check(number("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='fixed_events'") == 1, "V3 upgrade creates fixed_events");
            helper.onUpgrade(db, 3, 4);
            check(number("SELECT COUNT(*) FROM fixed_events") == 0, "fixed_events migration is idempotent");

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
        System.out.println("PASS: " + count + " fixed schedule and stable re-plan assertions on real SQLite.");
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
