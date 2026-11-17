import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.*;
import com.tempo.app.learning.*;
import com.tempo.app.model.*;

/** Regression checks against production persistence and real SQLite, not a duplicate eligibility algorithm. */
public final class EvaluationEligibilityCheck {
    private static final long M=60_000L, NOW=1_800_000_000_000L, END=NOW+120*M;
    private static AppDatabaseHelper helper;
    private static SQLiteDatabase db;
    private static LearningStore store;
    private static int count;

    public static void main(String[] args) {
        fresh(EvaluationEligibilityCheck::timerMissingThenFilled);
        fresh(EvaluationEligibilityCheck::partialMissingThenFilled);
        fresh(EvaluationEligibilityCheck::clearThenRefill);
        fresh(EvaluationEligibilityCheck::legacyMissing);
        fresh(EvaluationEligibilityCheck::legacyAmbiguousCorrection);
        fresh(EvaluationEligibilityCheck::legacyRatingOnly);
        fresh(EvaluationEligibilityCheck::normalCorrection);
        fresh(EvaluationEligibilityCheck::ratingOnlyInitialAdjustment);
        fresh(EvaluationEligibilityCheck::manualAndRecovery);
        fresh(EvaluationEligibilityCheck::rejectedCorrection);
        fresh(EvaluationEligibilityCheck::lateCompletion);
        System.out.println("PASS: "+count+" evaluation eligibility, correction provenance and deadline independence assertions.");
    }

    private static void fresh(Runnable scenario) {
        try(AppDatabaseHelper h=new AppDatabaseHelper(new Context())) {
            helper=h;db=h.getWritableDatabase();store=new LearningStore(h);
            for(int i=0;i<3;i++) {Task t=task("Baseline "+i,0);store.prepare(t.getTaskId(),NOW);store.save("seed-"+i,t.getTaskId(),30,0,true,null,NOW+M);}
            scenario.run();
        }
    }

    private static void timerMissingThenFilled() {
        Task t=started("Untimed completion");long prediction=number("SELECT prediction FROM learning_snapshots WHERE task_id="+t.getTaskId());
        LearningStore.Saved row=store.save("missing",t.getTaskId(),null,0,true,null,"TIMER","",false,NOW+M);
        summary(3,3,1,1,"missing timer time");
        check(number("SELECT COUNT(*) FROM task_feedback WHERE actual_duration IS NULL AND task_id="+t.getTaskId())==1,"untimed duration stays NULL");
        store.correctRecord(row.feedbackId,0,45,0,NOW+2*M);
        summary(4,3,1,1,"filled timer time");
        check(number("SELECT COUNT(*) FROM task_feedback WHERE task_id="+t.getTaskId())==1,"fill updates the same session");
        store=new LearningStore(helper);
        store.correctRecord(row.feedbackId,1,60,3,NOW+3*M);
        summary(4,3,1,1,"repeat edit through a new store instance");
        check(number("SELECT prediction FROM learning_snapshots WHERE task_id="+t.getTaskId())==prediction,"frozen prediction is preserved");
        check(LearningStore.actualMinutes(db,t.getTaskId())==60,"corrected time remains in totals");
        Task next=task("Later estimate",0);store.refreshPredictions(END);
        check(new TaskDao(helper).getTaskById(next.getTaskId()).getPredictedDuration()>30,"complete filled duration still trains later estimates");
        check(store.summary(END).timeOfDay.sessions==0,"filled time does not invent execution intervals");
    }

    private static void partialMissingThenFilled() {
        Task t=started("Partially untimed");
        LearningStore.Saved first=store.save("partial",t.getTaskId(),null,0,false,12,"TIMER","",false,NOW+M);
        store.save("finished",t.getTaskId(),15,0,true,null,NOW+2*M);
        summary(3,3,1,1,"one missing session in a completed task");
        store.correctRecord(first.feedbackId,0,5,0,NOW+3*M);
        summary(4,3,1,1,"one filled session excludes the entire task from MAE");
        store.save("partial",t.getTaskId(),100,0,true,null,NOW+4*M);
        check(LearningStore.actualMinutes(db,t.getTaskId())==20,"replaying a receipt cannot overwrite corrected time");
        check(number("SELECT COUNT(*) FROM task_feedback WHERE task_id="+t.getTaskId())==2,"partial correction and receipt retry add no session");
    }

    private static void clearThenRefill() {
        Task t=started("Clear and refill");LearningStore.Saved row=store.save("timed",t.getTaskId(),30,0,true,null,NOW+M);
        summary(4,4,1,1,"original timed task");
        store.correctRecord(row.feedbackId,0,null,0,NOW+2*M);
        summary(3,3,1,1,"clear duration without losing the completion date");
        store.correctRecord(row.feedbackId,1,45,0,NOW+3*M);
        summary(4,3,1,1,"refill does not restore MAE eligibility");
    }

    private static void legacyMissing() {
        Task t=started("V8 status record");long id=legacyRecord(t,null,"STATUS",0);
        summary(3,3,1,1,"persisted V8 missing record");
        store.correctRecord(id,0,null,4,NOW+2*M);
        store.correctRecord(id,1,45,4,NOW+3*M);
        summary(4,3,1,1,"legacy fill after a rating correction");
    }

    private static void legacyAmbiguousCorrection() {
        Task t=started("V8 already edited");long id=legacyRecord(t,45,"ADJUSTED",1);
        summary(4,3,1,1,"ambiguous V8 positive duration is trainable but not evaluated");
        store.correctRecord(id,1,50,0,NOW+2*M);
        summary(4,3,1,1,"another time edit cannot certify unknown provenance");
        store.correctRecord(id,2,50,3,NOW+3*M);
        summary(4,3,1,1,"rating-only edit cannot certify unknown provenance");
    }

    private static void legacyRatingOnly() {
        Task t=started("V8 timer rating");legacyRecord(t,30,"TIMER",2);
        summary(4,4,1,1,"old timer records with only rating edits stay eligible");
    }

    private static void normalCorrection() {
        Task t=started("Known positive correction");LearningStore.Saved row=store.save("normal",t.getTaskId(),30,0,true,null,NOW+M);
        store.correctRecord(row.feedbackId,0,45,2,NOW+2*M);
        summary(4,4,1,1,"normal positive-to-positive correction stays eligible");
        LearningSummary s=store.summary(END);
        check(Math.abs(s.estimateMae-3.75)<.0001 && Math.abs(s.predictionMae-3.75)<.0001,"MAE uses corrected outcomes against frozen estimates");
        check(Math.abs(s.predictionBias+3.75)<.0001,"signed bias uses corrected outcomes");
        store.correctRecord(row.feedbackId,1,45,4,NOW+3*M);
        summary(4,4,1,1,"later rating edit preserves eligibility");
    }

    private static void ratingOnlyInitialAdjustment() {
        Task t=started("Adjusted at first save");LearningStore.Saved row=store.save("adjusted",t.getTaskId(),30,0,true,null,NOW+M);
        store.correctRecord(row.feedbackId,0,30,4,NOW+2*M);
        summary(4,4,1,1,"first rating edit does not make a known initial adjustment ambiguous");
    }

    private static void manualAndRecovery() {
        Task manual=started("Manual entry");LearningStore.Saved m=store.save("manual",manual.getTaskId(),30,0,true,null,"MANUAL","",false,NOW+M);
        store.correctRecord(m.feedbackId,0,45,0,NOW+2*M);
        summary(4,3,0,0,"manual entry stays excluded after a correction");
        Task recovered=task("No pre-work snapshot",NOW+60*M);
        LearningStore.Saved r=store.save("recovered",recovered.getTaskId(),null,0,true,null,"TIMER","",false,NOW+M);
        store.correctRecord(r.feedbackId,0,45,0,NOW+2*M);
        summary(5,3,0,0,"recovery save does not invent pre-work or deadline evidence");
    }

    private static void rejectedCorrection() {
        Task t=started("Stale editor");LearningStore.Saved row=store.save("stale",t.getTaskId(),30,0,true,null,NOW+M);
        boolean rejected=false;try{store.correctRecord(row.feedbackId,1,null,0,NOW+2*M);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"stale missing-time correction is rejected");
        summary(4,4,1,1,"rejected edit leaves eligibility and completion unchanged");
    }

    private static void lateCompletion() {
        Task t=started("Late untimed completion");LearningStore.Saved row=store.save("late",t.getTaskId(),null,0,true,null,"TIMER","",false,NOW+61*M);
        summary(3,3,1,0,"late completion still counts without a duration");
        store.correctRecord(row.feedbackId,0,45,0,NOW+62*M);
        summary(4,3,1,0,"filling duration cannot turn late completion into on-time completion");
    }

    private static long legacyRecord(Task t,Integer minutes,String source,int revision) {
        // Persist the exact V8/V8.1 row format without invoking V8.2 save/correction logic.
        ContentValues f=new ContentValues();f.put("task_id",t.getTaskId());
        if(minutes==null)f.putNull("actual_duration");else f.put("actual_duration",minutes);
        f.put("actual_difficulty",0);f.put("completion_status","COMPLETED");f.put("completed_at",NOW+M);
        long id=db.insertOrThrow("task_feedback",null,f);
        ContentValues d=new ContentValues();d.put("feedback_id",id);d.put("source",source);d.put("revision",revision);d.put("timing_valid",0);db.insertOrThrow("session_details",null,d);
        ContentValues status=new ContentValues();status.put("status","COMPLETED");db.update("tasks",status,"task_id=?",new String[]{String.valueOf(t.getTaskId())});
        ContentValues s=new ContentValues();s.put("completed_at",NOW+M);db.update("learning_snapshots",s,"task_id=?",new String[]{String.valueOf(t.getTaskId())});return id;
    }
    private static void summary(int history,int evaluated,int deadlines,int onTime,String scenario) {
        LearningSummary s=store.summary(END);
        check(s.historyTasks==history && s.evaluatedTasks==evaluated,scenario+" (learning and MAE sample counts)");
        check(s.deadlineTasks==deadlines && s.onTimeTasks==onTime,scenario+" (independent deadline counts)");
    }
    private static Task task(String title,long deadline) {Task t=new Task(title,"Work",30,3,3,deadline);t.setTaskId((int)new TaskDao(helper).insertTask(t));return t;}
    private static Task started(String title) {Task t=task(title,NOW+60*M);store.prepare(t.getTaskId(),NOW);return t;}
    private static long number(String sql) {try(Cursor c=db.rawQuery(sql,null)){c.moveToFirst();return c.getLong(0);}}
    private static void check(boolean ok,String message) {count++;if(!ok)throw new AssertionError(message);}
}
