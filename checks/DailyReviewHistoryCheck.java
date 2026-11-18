import android.content.Context;
import com.tempo.app.database.*;
import com.tempo.app.home.DailyReview;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.model.*;
import java.util.*;

/** Home and D must use the same frozen evidence after task and activity edits. */
public final class DailyReviewHistoryCheck {
    private static final long M=60000L, DAY=1440*M, START=1800000000000L, END=START+DAY;
    private static int checks;
    private static void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);checks++;}
    private static DailyReview review(AppDatabaseHelper h,LearningStore s,long start,long end,long now) {
        return DailyReview.compute(start,end,Collections.emptyList(),new TaskDao(h).getTasksWithHistory(),
                new TaskFeedbackDao(h).getAllFeedback(),s.reviewEstimates(now));
    }
    private static int task(AppDatabaseHelper h,String title,int estimate) {
        return (int)new TaskDao(h).insertTask(new Task(title,"Work",estimate,3,3,0));
    }
    private static void compare(AppDatabaseHelper h,LearningStore s,int eligible,String label) {
        check(s.reviewEstimates(END+DAY).size()==eligible && s.summary(END+DAY).evaluatedTasks==eligible,label);
    }
    public static void main(String[] args) {
        try(AppDatabaseHelper h=new AppDatabaseHelper(new Context())) {
            LearningStore s=new LearningStore(h);TaskDao dao=new TaskDao(h);
            int id=task(h,"Yesterday report",60);s.prepare(id,START);
            LearningStore.Saved saved=s.save("overrun",id,120,3,true,null,START+120*M);
            DailyReview before=review(h,s,START,END,END);
            check(before.accuracyPct==0 && before.overrun.estimatedMinutes==60,"frozen original estimate used");
            check(before.focusMinutes==120 && before.finishedOnDay==1,"recorded time and completion kept");
            compare(h,s,1,"Home and D initially have the same eligible task");
            Task edited=dao.getTaskById(id);edited.setEstimatedDuration(120);s.updateTask(edited,null,END+M);
            DailyReview after=review(h,s,START,END,END+M);
            check(after.accuracyPct==null && !after.estimatesAccurate && after.reminder()==null,"editing 60 to 120 cannot create false accuracy");
            check(after.focusMinutes==120 && after.finishedOnDay==1,"scope edit does not erase activity");
            compare(h,s,0,"scope changed task excluded in both screens");
            edited=dao.getTaskById(id);edited.setEstimatedDuration(60);s.updateTask(edited,null,END+2*M);
            compare(h,s,0,"reverting scope does not restore evidence");
            int good=task(h,"Comparable",30);s.prepare(good,START);
            LearningStore.Saved correct=s.save("good",good,30,0,true,null,START+30*M);
            check(review(h,s,START,END,END).accuracyPct==100,"remaining eligible task is evaluated");
            s.correctRecord(correct.feedbackId,0,45,0,END+3*M);
            DailyReview corrected=review(h,s,START,END,END+3*M);
            check(corrected.accuracyPct==50 && corrected.overrun.estimatedMinutes==30,"normal correction compares with original 30");
            compare(h,s,1,"positive correction eligibility matches D");
            s.correctRecord(correct.feedbackId,1,null,0,END+4*M);
            compare(h,s,0,"missing duration omitted");
            s.correctRecord(correct.feedbackId,2,30,0,END+5*M);
            compare(h,s,0,"backfill cannot recover accuracy eligibility");
            int manual=task(h,"Manual",20);s.prepare(manual,START);
            s.save("manual",manual,20,0,true,null,"MANUAL","",false,START+40*M);
            compare(h,s,0,"manual entry omitted from prediction evaluation");
            int pending=task(h,"Unfinished",10);s.prepare(pending,START);
            s.save("partial",pending,20,0,false,null,START+50*M);
            check(review(h,s,START,END,END).accuracyPct==null,"partial overrun is not a completed prediction result");
            s.save("finish",pending,5,0,true,null,END+60*M);
            check(review(h,s,START,END,END+60*M).accuracyPct==null,"next-day completion is not evaluated yesterday");
            check(review(h,s,END,END+DAY,END+60*M).overrun.actualMinutes==25,"completion day uses cumulative task duration");
            compare(h,s,1,"completed task evaluated in both screens");
            check(s.reviewEstimates(END).isEmpty(),"future completion excluded at read cutoff");
            dao.archiveTask(pending);
            compare(h,s,1,"archive preserves comparable historical evidence");
            dao.deleteTask(pending);
            compare(h,s,0,"permanent deletion removes evidence");
            DailyReview withoutEvidence=DailyReview.compute(START,END,Collections.emptyList(),dao.getTasksWithHistory(),new TaskFeedbackDao(h).getAllFeedback());
            check(withoutEvidence.accuracyPct==null && withoutEvidence.focusMinutes>0,"counts-only overload never guesses accuracy from mutable estimates");
        }
        System.out.println("PASS: "+checks+" Home frozen-history and shared evaluation assertions.");
    }
}
