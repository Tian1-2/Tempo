import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.*;
import com.tempo.app.home.DailyReview;
import com.tempo.app.insights.InsightsStats;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.model.*;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.scheduler.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/** Exercises the production repositories, seven-day service and DAOs against SQLite. */
public final class IntegratedReleaseCheck {
    private static final long MINUTE = 60000L;
    private static int checks;
    private static AppDatabaseHelper helper;
    private static TaskDao tasks;
    private static ScheduleDao slots;
    private static LearningStore learning;
    private static long today;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static String date(long time) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(time)); }
    private static long day(int offset) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(today); c.add(Calendar.DATE,offset); return c.getTimeInMillis();
    }
    private static UserProfile routine(int cap) {
        UserProfile p=new UserProfile(); p.setWakeTime("07:00"); p.setSleepTime("23:00"); p.setFocusDuration(30);
        p.setBreakDuration(5); p.setDailyCapacity(cap); p.setPreferredStartTime("11:45"); return p;
    }
    private static void save(UserProfile p) { new UserProfileDao(helper).saveProfile(p); }
    private static int task(int minutes,int difficulty,long deadline) {
        return (int) tasks.insertTask(new Task("Release test","Study",minutes,difficulty,3,deadline));
    }
    private static int slot(int task,long start,int minutes,boolean locked) {
        ScheduleItem s=new ScheduleItem(); s.setTaskId(task); s.setScheduledDate(date(start)); s.setStartTime(start);
        s.setEndTime(start+minutes*MINUTE); s.setManuallyEdited(locked); s.setStatus("PLANNED");
        return (int)slots.insertScheduleItem(s);
    }
    private static int futureMinutes(int task) {
        int total=0;
        for(int i=0;i<7;i++) for(ScheduleItem item:slots.getScheduleByDate(date(day(i)))) {
            if(item.getTaskId()==task && "PLANNED".equals(item.getStatus()) && item.getStartTime()>System.currentTimeMillis())
                total+=(item.getEndTime()-item.getStartTime())/MINUTE;
        }
        return total;
    }
    private static ScheduleGenerator.Result plan() {
        return new ScheduleService().generateRange(7,new DefaultScheduleConstraints());
    }
    private static void remaining() {
        save(routine(240)); int id=task(120,3,0);
        slot(id,today+60*MINUTE,30,true);
        learning.prepare(id,today+60*MINUTE);
        learning.save("partial",id,30,0,false,null,today+90*MINUTE);
        check(learning.remaining(tasks.getPendingTasks(),30).get(id)==90,"D has 90 minutes left");
        check(plan().unscheduledTasks.isEmpty() && futureMinutes(id)==90,"all 90 minutes planned");
        plan(); check(futureMinutes(id)==90,"repeat planning is stable");
        tasks.deleteTask(id);
    }
    private static void conflict() {
        save(routine(30)); int id=task(180,3,0);
        int locked=slot(id,day(1)+600*MINUTE,60,true), later=slot(id,day(2)+600*MINUTE,30,false);
        int original=slot(id,today+900*MINUTE,30,false);
        try { plan(); throw new AssertionError("expected capacity conflict"); }
        catch(IllegalArgumentException e) { check(e.getMessage().contains(date(day(1))),"conflict has offending date"); }
        check(slots.getScheduleByDate(date(day(1))).get(0).getScheduleId()==locked,"locked slot retained");
        check(slots.getScheduleByDate(date(day(2))).get(0).getScheduleId()==later,"later slot retained");
        check(slots.getScheduleByDate(date(today)).get(0).getScheduleId()==original,"earlier day rolled back too");
        tasks.deleteTask(id);
    }
    private static void deadlineAndFixed() {
        UserProfile p=routine(240); p.setFocusDuration(25);
        Calendar c=Calendar.getInstance();c.setTimeInMillis(today);
        int weekday=c.get(Calendar.DAY_OF_WEEK)-1;
        p.setFixedBlocks(FixedBlocks.add("",weekday,7*60,8*60));
        save(p); int id=task(60,5,today+720*MINUTE);
        check(plan().unscheduledTasks.isEmpty() && futureMinutes(id)==60,"whole difficult task fits before noon");
        List<ScheduleItem> generated=slots.getScheduleByDate(date(today));
        for(ScheduleItem item:generated) check(item.getStartTime()>=today+480*MINUTE && item.getEndTime()<=today+720*MINUTE,"protect commitment and deadline");
        try { slots.updatePlannedTime(generated.get(0).getScheduleId(),date(today),today+430*MINUTE,today+445*MINUTE);
            throw new AssertionError("expected commitment conflict");
        } catch(IllegalArgumentException e) {check(e.getMessage().contains("fixed"),"manual move respects class");}
        check(FixedBlocks.labels(p.getFixedBlocks(),day(1)).isEmpty(),"weekday-specific commitment");
        try {FixedBlocks.add(p.getFixedBlocks(),weekday,450,490);throw new AssertionError("expected overlap");}
        catch(IllegalArgumentException e) {check(e.getMessage().contains("overlaps"),"overlapping commitments rejected");}
        tasks.deleteTask(id);
    }
    private static InsightsStats stats(long now) {
        return InsightsStats.calculate(tasks.getTasksWithHistory(),new TaskFeedbackDao(helper).getAllFeedback(),now,TimeZone.getDefault());
    }
    private static void archiveAndHistory() {
        save(routine(60));long now=System.currentTimeMillis();int id=task(30,3,0);
        learning.prepare(id,now-31*MINUTE);learning.save("done",id,30,3,true,null,now-MINUTE);
        check(tasks.archiveTask(id)==1 && tasks.getPendingTasks().isEmpty(),"archive hides task");
        check(tasks.getTaskById(id).isArchived() && "COMPLETED".equals(tasks.getTaskById(id).getStatus()),"completion survives archive");
        check(stats(now).recordedMinutes==30 && stats(now).completedTasks==1,"archived completion in Insights");
        check(learning.history(now).weekMinutes==30 && learning.summary(now).historyTasks==1,"history and learning retained");
        int next=task(60,3,0); plan();
        int todayMinutes=0;
        for(ScheduleItem item:slots.getScheduleByDate(date(today))) if(item.getTaskId()==next) todayMinutes+=(item.getEndTime()-item.getStartTime())/MINUTE;
        check(todayMinutes==30,"archived work counts towards today's capacity");
        tasks.deleteTask(id);plan();
        check(slots.getScheduleByDate(date(today)).stream().filter(s->s.getTaskId()==next).mapToLong(s->(s.getEndTime()-s.getStartTime())/MINUTE).sum()==60,"deletion restores daily capacity");
        check(learning.summary(now).historyTasks==0 && stats(now).recordedMinutes==0 && learning.history(now).count==0,"permanent deletion removes record and sample");
        tasks.deleteTask(next);
        int partial=task(400,3,0);
        long start=InsightsStats.weekStart(now,TimeZone.getDefault()).getTimeInMillis();
        SQLiteDatabase db=helper.getWritableDatabase();
        for(int i=0;i<201;i++) db.execSQL("INSERT INTO task_feedback(task_id,actual_duration,completed_at) VALUES("+partial+",1,"+(start+i*1000L)+")");
        db.execSQL("INSERT INTO task_feedback(task_id,actual_duration,completed_at) VALUES("+partial+",40,"+(start-1)+")");
        tasks.archiveTask(partial);
        LearningStore.History history=learning.history(now);
        check(history.rows.size()==200 && history.count==202,"list limited independently of totals");
        check(stats(now).recordedMinutes==201 && history.weekMinutes==201,"identical local seven-day boundary beyond 200 rows");
        check(stats(now).taskCount==0 && learning.summary(now).historyTasks==0,"cancelled partial task excluded from completion and learning");
        check(tasks.restoreTask(partial)==1 && tasks.getPendingTasks().stream().anyMatch(t->t.getTaskId()==partial),"restore returns unfinished task to planning");
        check(learning.history(now).count==202 && stats(now).recordedMinutes==201,"restore preserves historical feedback without copies");
        check(tasks.restoreTask(partial)==0,"repeated restore is a no-op");
        tasks.deleteTask(partial);
    }
    private static void dailyReview() {
        Task t=new Task("Yesterday","Study",60,3,3,0); t.setTaskId(1);t.setStatus("COMPLETED");
        ScheduleItem s=new ScheduleItem();s.setTaskId(1);s.setStatus("PLANNED");s.setStartTime(day(-1));s.setEndTime(day(-1)+60*MINUTE);
        TaskFeedback partial=new TaskFeedback();partial.setTaskId(1);partial.setCompletionStatus("PARTIAL");partial.setActualDuration(15);partial.setCompletedAt(day(-1)+60*MINUTE);
        TaskFeedback finish=new TaskFeedback();finish.setTaskId(1);finish.setCompletionStatus("COMPLETED");finish.setCompletedAt(today+MINUTE);
        DailyReview yesterday=DailyReview.compute(day(-1),today,Arrays.asList(s),Arrays.asList(t),Arrays.asList(partial,finish));
        check(yesterday.completedTasks==0 && yesterday.accuracyPct==null,"future completion cannot rewrite yesterday");
        finish.setCompletedAt(day(-1)+90*MINUTE);
        yesterday=DailyReview.compute(day(-1),today,Arrays.asList(s),Arrays.asList(t),Arrays.asList(partial,finish));
        check(yesterday.completedTasks==1 && yesterday.accuracyPct==null && !yesterday.estimatesAccurate,"unknown time cannot earn accuracy");
    }
    private static DailyReview review(long from, long until) {
        return DailyReview.compute(from, until, slots.getScheduleByDate(date(from)),
                tasks.getTasksWithHistory(), new TaskFeedbackDao(helper).getAllFeedback());
    }

    private static void finishEarlyAndArchive() {
        long now = System.currentTimeMillis();
        int finished = task(30, 3, 0), pending = task(60, 3, 0);
        slot(finished, today + 510*MINUTE, 30, false);
        slot(pending, today + 600*MINUTE, 60, false);
        learning.prepare(finished, now - 15*MINUTE);
        learning.save("early", finished, 15, 0, true, null, "MANUAL", "", false, now);
        DailyReview home = review(today, day(1));
        check(home.finishedOnDay == 1 && home.tasksToGo == 1, "finished early plus one to go");
        check(home.focusMinutes == 15 && home.plannedMinutes == 60, "recorded minutes and released slot");
        check(futureMinutes(finished) == 0, "completion frees future occupancy");
        check(review(day(1), day(2)).finishedOnDay == 0, "completion belongs to its date only");
        tasks.deleteTask(finished); tasks.deleteTask(pending);

        int id = task(120, 3, 0);
        slot(id, day(-1) + 600*MINUTE, 60, false);
        slot(id, today + 15*MINUTE, 10, false);
        long roundedNow = Math.floorDiv(now, MINUTE) * MINUTE;
        slot(id, roundedNow - 10*MINUTE, 30, true);
        slot(id, day(1) + 600*MINUTE, 30, true);
        learning.save("archive-history", id, 30, 0, false, null, "MANUAL", "", false, day(-1)+630*MINUTE);
        DailyReview before = review(day(-1), today);
        tasks.archiveTask(id);
        DailyReview after = review(day(-1), today);
        check(before.plannedMinutes == 60 && after.plannedMinutes == 60 && after.focusMinutes == 30,
                "past plan and recorded minutes survive archive");
        check(before.score.equals(after.score), "archiving does not rewrite yesterday's score");
        check(futureMinutes(id) == 0 && review(today, day(1)).tasksToGo == 0, "archived task releases future work");
        plan();
        int elapsed = review(today, day(1)).plannedMinutes;
        check(elapsed >= 20 && elapsed <= 21, "past and elapsed part of open slot survive replan");
        tasks.restoreTask(id); plan();
        check(review(day(-1), today).plannedMinutes == 60, "restore preserves yesterday's original hours");
        tasks.deleteTask(id);
        check(review(day(-1), today).plannedMinutes == 0, "permanent deletion still clears history");
    }

    private static void adjustLockedBudget() {
        save(routine(240));
        long now = System.currentTimeMillis();
        int id = task(120, 3, 0);
        learning.prepare(id, now-100*MINUTE);
        learning.save("spent-90", id, 90, 0, false, null, "MANUAL", "", false, now);
        int edit = slot(id, today+840*MINUTE, 20, false);
        slot(id, day(1)+840*MINUTE, 10, true);
        slot(id, day(-1)+840*MINUTE, 30, true);
        long start = today+840*MINUTE, end = start+50*MINUTE;
        ScheduleDao.RemainingConflict proposal;
        try {
            slots.updatePlannedTime(edit, date(today), start, end);
            throw new AssertionError("expected remaining-work confirmation");
        } catch (ScheduleDao.RemainingConflict conflict) { proposal = conflict; }
        check(proposal.remaining == 30 && proposal.required == 60, "locks across dates exceed remaining, past lock excluded");
        check(futureMinutes(id) == 30 && learning.editContext(id).remaining == 30, "no side effects before confirmation");
        learning.save("extra-progress", id, 5, 0, false, null, "MANUAL", "", false, now);
        try {
            slots.updatePlannedTime(edit, date(today), start, end, proposal);
            throw new AssertionError("expected refreshed confirmation");
        } catch (ScheduleDao.RemainingConflict conflict) {
            check(conflict.remaining == 25 && conflict.required == 60, "stale confirmation rechecked");
            proposal = conflict;
        }
        slots.updatePlannedTime(edit, date(today), start, end, proposal);
        plan();
        check(learning.editContext(id).remaining == 60 && futureMinutes(id) == 60, "confirmed remaining matches schedule");
        check(learning.summary(now).previews.get(0).remaining == 60, "Insights reads confirmed amount");
        check(learning.history(now).count == 2 && learning.editContext(id).recorded == 95, "changing remaining adds no work record");
        learning.save("more-progress", id, 5, 0, false, null, "MANUAL", "", false, now);
        try { plan(); throw new AssertionError("expected conflict after new feedback"); }
        catch (IllegalArgumentException e) { check(e.getMessage().contains("55 min remaining"), "reports existing surplus after new feedback"); }
        check(futureMinutes(id) == 60, "replan conflict preserves existing locked rows");
        slots.updatePlannedTime(edit, date(today), start, end-5*MINUTE);
        plan();
        check(futureMinutes(id) == 55 && learning.editContext(id).remaining == 55, "shortening resolves without changing remaining");
        tasks.deleteTask(id);
    }

    private static void namedCommitments() {
        String old = "Thu 09:00-10:00";
        FixedBlocks.Entry unnamed = FixedBlocks.entries(old).get(0);
        check(unnamed.name.isEmpty() && unnamed.title().equals("Fixed commitment"), "old unnamed commitment is readable");
        String added = FixedBlocks.save(old, null, 3, 420, 480, "Team sync");
        String renamed = FixedBlocks.save(added, unnamed, 4, 540, 600, "Lab");
        check(FixedBlocks.entries(renamed).size() == 2, "editing replaces the original row");
        Calendar wed = Calendar.getInstance(); wed.set(2026, Calendar.NOVEMBER, 18, 0, 0, 0);
        long day = wed.getTimeInMillis();
        List<FixedBlocks.Entry> displayed = FixedBlocks.onDay(renamed, day);
        check(displayed.size() == 1 && displayed.get(0).title().equals("Team sync"), "timeline selects Wednesday's named card");
        ScheduleGenerator.Interval blocked = FixedBlocks.intervals(renamed, day).get(0);
        check(blocked.start == day - Math.floorMod(day, MINUTE) + 420*MINUTE && blocked.end - blocked.start == 60*MINUTE,
                "named card and scheduler use the same interval");
        try { FixedBlocks.save(renamed, null, 3, 425, 450, "Overlap"); throw new AssertionError("expected overlap"); }
        catch (IllegalArgumentException e) { check(e.getMessage().contains("overlaps"), "named commitment still protects occupied time"); }
        String adjacent = FixedBlocks.save(renamed, null, 3, 480, 481, "One minute");
        check(FixedBlocks.onDay(adjacent, day).size() == 2, "back-to-back and short commitments supported");
        List<ScheduleGenerator.Session> sessions = Arrays.asList(
                new ScheduleGenerator.Session(9, new ScheduleGenerator.Interval(blocked.end - 5*MINUTE, blocked.end), false),
                new ScheduleGenerator.Session(9, new ScheduleGenerator.Interval(blocked.end + MINUTE, blocked.end + 5*MINUTE), false));
        String justOneMinute = FixedBlocks.save("", null, 3, 480, 481, "Check-in");
        check(ScheduleDisplayBlocks.group(sessions, FixedBlocks.intervals(justOneMinute, day), 5).size() == 2,
                "fixed minute between adjacent task segments stays visible, not grouped as a break");
        String midnight = FixedBlocks.save(adjacent, null, 3, 1439, 1440, "Close");
        FixedBlocks.Entry late = FixedBlocks.onDay(midnight, day).get(2);
        check(late.timeRange().equals("23:59–24:00") && late.end == 1440, "late commitment keeps the end-of-day boundary");
        UserProfile p = routine(240); p.setFixedBlocks(adjacent); save(p);
        check(adjacent.equals(new UserProfileDao(helper).getProfile().getFixedBlocks()), "names and legacy times persist");
    }

    private static void upgrade(int version) throws Exception {
        try(SQLiteDatabase db=new SQLiteDatabase()) {
            db.setForeignKeyConstraintsEnabled(true);
            for(String line:Files.readAllLines(Paths.get("checks/fixtures/schema-v"+version+".sql"))) db.execSQL(line);
            String status=version==3?"DELETED":"COMPLETED";
            db.execSQL("INSERT INTO tasks(task_id,title,category,status,estimated_duration) VALUES(1,'Old','Study','"+status+"',30)");
            db.execSQL("INSERT INTO task_feedback(task_id,actual_duration,completed_at,completion_status) VALUES(1,30,1000,'COMPLETED')");
            db.execSQL("INSERT INTO user_profile(wake_time,sleep_time) VALUES('07:00','23:30')");
            helper.onUpgrade(db,version,4);
            try(Cursor c=db.rawQuery("SELECT t.archived,t.status,f.actual_duration,p.wake_time,p.fixed_blocks FROM tasks t JOIN task_feedback f ON t.task_id=f.task_id CROSS JOIN user_profile p",null)){
                check(c.moveToFirst() && c.getInt(0)==(version==3?1:0) && "COMPLETED".equals(c.getString(1)) && c.getInt(2)==30 && "07:00".equals(c.getString(3)) && c.isNull(4),"V"+version+" to V4 data retained");
            }
            try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)){check(!c.moveToFirst(),"V"+version+" foreign keys valid");}
        }
    }
    public static void main(String[] args) throws Exception {
        // A reproducible local morning leaves room for the noon deadline on any host.
        int hour=Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.HOUR_OF_DAY);
        TimeZone.setDefault(new SimpleTimeZone((6-hour)*3600000,"TestMorning"));
        RepositoryProvider.init(new Context());
        helper=RepositoryProvider.getDatabaseHelper();tasks=new TaskDao(helper);slots=new ScheduleDao(helper);learning=new LearningStore(helper);
        Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);today=c.getTimeInMillis();
        try {remaining();conflict();deadlineAndFixed();archiveAndHistory();dailyReview();finishEarlyAndArchive();adjustLockedBudget();namedCommitments();upgrade(2);upgrade(3);}
        finally {helper.close();}
        System.out.println("PASS: "+checks+" release integration assertions using the production seven-day service and SQLite.");
    }
}
