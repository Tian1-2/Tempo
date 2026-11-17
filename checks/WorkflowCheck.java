import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.*;
import com.tempo.app.learning.*;
import com.tempo.app.model.*;
import java.util.*;

/** Runs actual LearningStore/DAOs with a minimal Android shim and a real SQLite engine. No UI assertions. */
public final class WorkflowCheck {
    private static int count;
    private static AppDatabaseHelper helper;
    private static LearningStore store;
    private static SQLiteDatabase db;
    private static final long M=60_000, NOW=1_800_000_000_000L;
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try(AppDatabaseHelper h=new AppDatabaseHelper(new Context())) {
            helper=h;store=new LearningStore(h);db=h.getWritableDatabase();
            remainingAndCorrections();completionReleasesPlans();scopeEdits();untimedAndManual();timingAndReceipts();migration();
        }
        System.out.println("PASS: "+count+" workflow assertions using production LearningStore/DAOs and real SQLite.");
    }
    private static void remainingAndCorrections() {
        Task task=task("Continue report",60);store.prepare(task.getTaskId(),NOW);
        LearningStore.Saved first=save("first",task,30,false,12,NOW+M);
        LearningStore.Saved second=save("second",task,5,false,null,NOW+2*M);
        check(left(task)==7,"explicit 12 followed by 5 minutes leaves 7");
        long rows=number("SELECT COUNT(*) FROM task_feedback");
        save("second",task,5,false,null,NOW+3*M);
        check(left(task)==7 && rows==number("SELECT COUNT(*) FROM task_feedback"),"receipt retry does not deduct or duplicate");
        check(left(task)==7 && left(task)==7,"repeated reads leave remainder unchanged");
        store.correctRecord(second.feedbackId,0,8,4,NOW+4*M);
        check(left(task)==4,"later correction adjusts remaining by duration delta");
        boolean rejected=false;try{store.correctRecord(second.feedbackId,0,9,0,NOW+5*M);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected && left(task)==4,"stale correction rejected without mutation");
        store.correctRecord(first.feedbackId,0,40,0,NOW+6*M);
        check(left(task)==4,"correction before explicit remaining anchor does not reinterpret that estimate");
        check(number("SELECT COUNT(*) FROM task_feedback")==rows,"corrections update original rows");
        check(number("SELECT actual_difficulty FROM task_feedback WHERE feedback_id="+second.feedbackId)==4,"rating correction persisted");
        Task overrun=task("Overrun",20);store.prepare(overrun.getTaskId(),NOW);
        save("overrun1",overrun,1,false,3,NOW+M);
        LearningStore.Saved large=save("overrun2",overrun,10,false,null,NOW+2*M);
        check(left(overrun)==25,"exhausted remaining reserves provisional block without auto-completion");
        store.correctRecord(large.feedbackId,0,2,0,NOW+3*M);
        check(left(overrun)==1,"correction after overrun restores exact remaining work");
    }
    private static void completionReleasesPlans() {
        Task task=task("Early finish",50);store.prepare(task.getTaskId(),NOW);
        long at=NOW+5*M;
        long current=block(task,NOW,NOW+25*M,false),future=block(task,NOW+30*M,NOW+55*M,false),manual=block(task,NOW+24*60*M,NOW+(24*60+20)*M,true);
        LearningStore.Saved result=save("finished",task,5,true,null,at);
        check("COMPLETED".equals(new TaskDao(helper).getTaskById(task.getTaskId()).getStatus()),"task completed");
        check("COMPLETED".equals(string("SELECT status FROM schedule_items WHERE schedule_id="+current)),"started plan kept as completed");
        check(number("SELECT end_time FROM schedule_items WHERE schedule_id="+current)==at,"unused tail of current block released");
        check("CANCELLED".equals(string("SELECT status FROM schedule_items WHERE schedule_id="+future)),"future generated block cancelled");
        check("CANCELLED".equals(string("SELECT status FROM schedule_items WHERE schedule_id="+manual)),"future manual block on another date cancelled");
        save("finished",task,5,true,null,at+60*M);
        check(number("SELECT end_time FROM schedule_items WHERE schedule_id="+current)==at,"receipt replay does not rewrite plan history");
        Task next=task("New work",20);long moved=block(next,NOW+55*M,NOW+75*M,false);
        List<ScheduleItem> displayed=new ScheduleDao(helper).getScheduleByDate("2026-11-17");
        ScheduleItem editing=displayed.stream().filter(s->s.getScheduleId()==moved).findFirst().get();
        check(!com.tempo.app.planning.ScheduleEditRules.overlaps(displayed,editing,NOW+30*M,NOW+50*M),"page precheck permits moving into cancelled time before the DAO is called");
        check(new ScheduleDao(helper).updatePlannedTime((int)moved,"2026-11-17",NOW+30*M,NOW+50*M)==1,"manual move can occupy released future time");
        check(result.totalMinutes==5,"scheduled durations are not counted as actual work");
    }
    private static void scopeEdits() {
        Task task=task("Change workload",60);store.prepare(task.getTaskId(),NOW);
        long frozen=number("SELECT prediction FROM learning_snapshots WHERE task_id="+task.getTaskId());
        task.setEstimatedDuration(120);
        boolean rejected=false;try{store.updateTask(task,null,NOW+M);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected && new TaskDao(helper).getTaskById(task.getTaskId()).getEstimatedDuration()==60,"started scope edit requires remaining work atomically");
        store.updateTask(task,90,NOW+M);
        check(left(task)==90,"confirmed remaining work reaches planning");
        check(number("SELECT prediction FROM learning_snapshots WHERE task_id="+task.getTaskId())==frozen,"scope edit preserves frozen prediction");
        task.setEstimatedDuration(60);store.updateTask(task,80,NOW+2*M);
        check(number("SELECT scope_changed FROM learning_snapshots WHERE task_id="+task.getTaskId())==1,"scope exclusion is sticky after reverting fields");
        int history=store.summary(NOW+60*M).historyTasks;
        save("scopefinish",task,10,true,null,NOW+3*M);
        check(store.summary(NOW+60*M).historyTasks==history,"changed scope excluded from learning after completion");
    }
    private static void untimedAndManual() {
        Task unknown=task("No stopwatch",30);
        int history=store.summary(NOW+60*M).historyTasks, evaluated=store.summary(NOW+60*M).evaluatedTasks;
        store.save("unknown",unknown.getTaskId(),null,4,true,null,"MANUAL","",false,NOW+M);
        check("COMPLETED".equals(new TaskDao(helper).getTaskById(unknown.getTaskId()).getStatus()),"status-only completion works");
        check(LearningStore.actualMinutes(db,unknown.getTaskId())==0,"status-only completion adds no fabricated minute");
        check(number("SELECT COUNT(*) FROM task_feedback WHERE task_id="+unknown.getTaskId()+" AND actual_duration IS NULL")==1,"missing time stays NULL");
        check(store.summary(NOW+60*M).historyTasks==history,"unknown time excluded from total duration training");
        Task manual=task("Manual report",30);
        store.save("manual",manual.getTaskId(),40,0,true,null,"MANUAL","",false,NOW+2*M);
        check(store.summary(NOW+60*M).historyTasks==history+1,"complete manual time can train subsequent estimates");
        check(store.summary(NOW+60*M).evaluatedTasks==evaluated,"retrospective manual entry never claims prior prediction evaluation");
        check(store.summary(NOW+60*M).timeOfDay.sessions==0,"manual entries have no inferred time of day");
    }
    private static void timingAndReceipts() {
        Task timed=task("Actual intervals",10);store.prepare(timed.getTaskId(),NOW);
        String segments=(NOW+M)+","+(NOW+M+30_000)+";"+(NOW+3*M)+","+(NOW+3*M+30_000);
        LearningStore.Saved record=store.save("timed",timed.getTaskId(),1,0,true,null,"TIMER",segments,true,NOW+4*M);
        check(number("SELECT COUNT(*) FROM session_segments WHERE feedback_id="+record.feedbackId)==2,"both active intervals persisted");
        LearningSummary summary=store.summary(NOW+60*M);long total=0;for(long n:summary.timeOfDay.millis)total+=n;
        check(summary.timeOfDay.sessions==1 && total==M,"pause gap excluded from time-of-day totals");
        long frozen=number("SELECT prediction FROM learning_snapshots WHERE task_id="+timed.getTaskId());
        store.correctRecord(record.feedbackId,0,2,2,NOW+5*M);
        check(store.summary(NOW+60*M).timeOfDay.sessions==0,"corrected duration excluded from execution time distribution");
        check(number("SELECT prediction FROM learning_snapshots WHERE task_id="+timed.getTaskId())==frozen,"correction never retrains its frozen prediction");
        check("COMPLETED".equals(new TaskDao(helper).getTaskById(timed.getTaskId()).getStatus()),"correction leaves task outcome unchanged");
        long before=number("SELECT COUNT(*) FROM task_feedback");
        Task other=task("Other task",20);boolean rejected=false;
        try{save("timed",other,10,false,null,NOW+6*M);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected && number("SELECT COUNT(*) FROM task_feedback")==before,"wrong-task token cannot create a record");
        Task adjusted=task("Adjusted timer",30);store.prepare(adjusted.getTaskId(),NOW);
        store.save("adjusted",adjusted.getTaskId(),50,0,true,null,"TIMER",segments,true,NOW+7*M);
        check(store.summary(NOW+60*M).timeOfDay.sessions==0,"edited timer input does not invent execution intervals");
        check(store.records(200).stream().anyMatch(r->r.id==record.feedbackId && r.revision==1 && r.minutes==2),"history reads the corrected record and version");
    }
    private static void migration() {
        long tasks=number("SELECT COUNT(*) FROM tasks"), feedback=number("SELECT COUNT(*) FROM task_feedback");
        db.execSQL("DROP TABLE session_segments");db.execSQL("DROP TABLE session_details");db.execSQL("DROP TABLE learning_snapshots");
        db.execSQL("CREATE TABLE learning_snapshots(task_id INTEGER PRIMARY KEY,estimate INTEGER NOT NULL,prediction INTEGER NOT NULL,category TEXT,task_type TEXT,difficulty INTEGER,deadline INTEGER,captured_at INTEGER NOT NULL,evaluable INTEGER NOT NULL,completed_at INTEGER,remaining_override INTEGER,FOREIGN KEY(task_id) REFERENCES tasks(task_id) ON DELETE CASCADE)");
        db.execSQL("INSERT INTO learning_snapshots(task_id,estimate,prediction,captured_at,evaluable,remaining_override) VALUES(1,60,60,1,1,12)");
        int legacyId=(int)number("SELECT MAX(task_id) FROM tasks");
        Task legacy=new TaskDao(helper).getTaskById(legacyId);
        long legacyFuture=block(legacy,10*M,20*M,false),legacyCurrent=block(legacy,2*M,7*M,false);
        db.execSQL("UPDATE schedule_items SET status='COMPLETED' WHERE schedule_id IN ("+legacyFuture+","+legacyCurrent+")");
        db.execSQL("INSERT INTO learning_snapshots(task_id,estimate,prediction,captured_at,evaluable,completed_at) VALUES("+legacyId+",30,30,1,1,"+6*M+")");
        helper.onUpgrade(db,2,3);
        check(number("SELECT remaining_override FROM learning_snapshots WHERE task_id=1")==12,"V2 override preserved on upgrade");
        check(number("SELECT remaining_anchor_feedback_id FROM learning_snapshots WHERE task_id=1")==number("SELECT MAX(feedback_id) FROM task_feedback WHERE task_id=1"),"legacy override anchored after existing feedback");
        check(tasks==number("SELECT COUNT(*) FROM tasks") && feedback==number("SELECT COUNT(*) FROM task_feedback"),"V2 upgrade preserves task and feedback counts");
        check("CANCELLED".equals(string("SELECT status FROM schedule_items WHERE schedule_id="+legacyFuture)),"V2 future completion blocks repaired from saved completion time");
        check(number("SELECT end_time FROM schedule_items WHERE schedule_id="+legacyCurrent)==6*M,"V2 unused tail released without deleting plan row");
        helper.onUpgrade(db,2,3);
        check(number("SELECT remaining_override FROM learning_snapshots WHERE task_id=1")==12,"migration is idempotent");
        for(String table:new String[]{"session_segments","session_details","feedback_submissions","learning_snapshots"})db.execSQL("DROP TABLE "+table);
        helper.onUpgrade(db,1,3);
        check(tasks==number("SELECT COUNT(*) FROM tasks") && feedback==number("SELECT COUNT(*) FROM task_feedback"),"V1 direct upgrade preserves records");
        check(number("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('learning_snapshots','feedback_submissions','session_details','session_segments')")==4,"V1 upgrade installs all D tables");
    }
    private static Task task(String name,int minutes){Task task=new Task(name,"Work",minutes,3,3,0);task.setTaskId((int)new TaskDao(helper).insertTask(task));return task;}
    private static int left(Task task){return store.remaining(Arrays.asList(new TaskDao(helper).getTaskById(task.getTaskId())),25).get(task.getTaskId());}
    private static LearningStore.Saved save(String token,Task task,int minutes,boolean complete,Integer left,long now){return store.save(token,task.getTaskId(),minutes,0,complete,left,now);}
    private static long block(Task task,long start,long end,boolean manual){ScheduleItem item=new ScheduleItem();item.setTaskId(task.getTaskId());item.setScheduledDate("2026-11-17");item.setStartTime(start);item.setEndTime(end);item.setManuallyEdited(manual);item.setStatus("PLANNED");return new ScheduleDao(helper).insertScheduleItem(item);}
    private static long number(String sql){try(Cursor c=db.rawQuery(sql,null)){c.moveToFirst();return c.getLong(0);}}
    private static String string(String sql){try(Cursor c=db.rawQuery(sql,null)){c.moveToFirst();return c.getString(0);}}
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
}
