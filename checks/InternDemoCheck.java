import android.content.Context;
import com.tempo.app.database.*;
import com.tempo.app.home.*;
import com.tempo.app.insights.InsightsStats;
import com.tempo.app.learning.*;
import com.tempo.app.model.*;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.scheduler.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Reproducible fixture verification through real stores and the production planning algorithm.
 * The future-date clock is explicit here; this does not emulate the Activity or Android clock. */
public final class InternDemoCheck {
    static final long M=60000L;
    static int checks;
    static AppDatabaseHelper h;
    static TaskDao tasks;
    static ScheduleDao slots;
    static LearningStore learning;
    static long now,start;
    static String date;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static Task task(String title){for(Task t:tasks.getTasksWithHistory())if(title.equals(t.getTitle()))return t;throw new AssertionError(title);}
    static long at(int hour,int minute){return start+(hour*60+minute)*M;}
    static ScheduleGenerator.Result plan(long clock) {
        learning.refreshPredictions(clock);UserProfile p=new UserProfileDao(h).getProfile();
        ScheduleConstraintsProvider.DayConstraints c=new DefaultScheduleConstraints().getForDate(date);
        List<ScheduleItem> existing=slots.getScheduleByDate(date);
        List<Task> pending=tasks.getPendingTasks();
        int recorded=(int)learning.history(clock).weekMinutes-200;
        ScheduleGenerator.Request r=new ScheduleGenerator.Request(new ScheduleGenerator.Interval(start,start+1440*M),clock,
                ScheduleModelAdapter.pendingTasks(pending,learning.remaining(pending,p.getFocusDuration())),
                Collections.singletonList(new ScheduleGenerator.Interval(at(7,0),at(23,30))),c.fixedBusyIntervals,
                ScheduleModelAdapter.lockedSessions(existing,clock),c.productiveWindows,25,5,240-recorded);
        ScheduleGenerator.Result result=new ScheduleGenerator().generate(r);
        slots.replaceGeneratedForDate(date,ScheduleModelAdapter.newScheduleItems(date,result));return result;
    }
    static void printPlan(String label) {
        System.out.println(label);
        SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",Locale.US);
        for(ScheduleItem s:slots.getScheduleByDate(date))if("PLANNED".equals(s.getStatus()))
            System.out.println("  "+fmt.format(new Date(s.getStartTime()))+"–"+fmt.format(new Date(s.getEndTime()))+" "+tasks.getTaskById(s.getTaskId()).getTitle()+(s.isManuallyEdited()?" [manual]":""));
    }
    public static void main(String[] args) {
        Calendar c=Calendar.getInstance();c.clear();c.set(2026,Calendar.NOVEMBER,23,0,0);start=c.getTimeInMillis();now=at(8,0);date="2026-11-23";
        RepositoryProvider.init(new Context());h=RepositoryProvider.getDatabaseHelper();tasks=new TaskDao(h);slots=new ScheduleDao(h);learning=new LearningStore(h);
        try {
            try {InternDemoData.seed(h,now+1440*M);throw new AssertionError("wrong weekday accepted");}
            catch(IllegalArgumentException e){check(tasks.getTasksWithHistory().isEmpty() && new UserProfileDao(h).getProfile()==null,"wrong weekday makes no changes");}
            InternDemoData.seed(h,now);
            check(tasks.getTasksWithHistory().size()==9 && tasks.getPendingTasks().size()==5,"nine tasks including four completed examples");
            try {InternDemoData.seed(h,now);throw new AssertionError("duplicate seed accepted");}
            catch(IllegalStateException e){check(tasks.getTasksWithHistory().size()==9,"repeated load cannot duplicate data");}
            UserProfile p=new UserProfileDao(h).getProfile();
            check(FixedBlocks.onDay(p.getFixedBlocks(),start).stream().anyMatch(e->e.title().equals("Weekly team meeting") && e.start==540 && e.end==570),"Monday meeting is fixed");
            check(FixedBlocks.onDay(p.getFixedBlocks(),start).stream().anyMatch(e->e.title().equals("Evening class") && e.start==1140 && e.end==1320),"19–22 class is fixed");
            check(FixedBlocks.onDay(p.getFixedBlocks(),start+1440*M).stream().noneMatch(e->e.title().equals("Weekly team meeting")),"meeting does not repeat on Tuesday");
            check(learning.history(now).weekMinutes==200 && learning.history(now).count==4,"historical totals are computed from four saved records");
            LearningSummary summary=learning.summary(now);
            check(summary.historyTasks==4 && summary.evaluatedTasks==4,"seeded samples retain pre-work evaluation evidence");
            check(summary.estimateMae==12.5 && summary.predictionMae==12.0,"documented initial MAE");
            check(task("Weekly sales summary").getPredictedDuration()==45,"40-minute report calibrated to 45");
            check(task("Review lecture notes").getPredictedDuration()==43,"study prediction is 43 minutes");
            check(task("Buy groceries").getPredictedDuration()==21,"groceries prediction is 21 minutes");
            ScheduleGenerator.Result planned=plan(now);check(planned.unscheduledTasks.isEmpty(),"all demo tasks fit Monday");
            List<ScheduleItem> first=slots.getScheduleByDate(date);
            List<ScheduleGenerator.Interval> fixed=FixedBlocks.intervals(p.getFixedBlocks(),start);
            boolean valid=true;int workMinutes=0;
            for(ScheduleItem item:first){if(!"PLANNED".equals(item.getStatus()))continue;
                valid &= item.getEndTime()-item.getStartTime()<=25*M;
                for(ScheduleGenerator.Interval f:fixed)valid &= !(item.getStartTime()<f.end && f.start<item.getEndTime());
                if("Work".equals(tasks.getTaskById(item.getTaskId()).getCategory()))workMinutes+=(item.getEndTime()-item.getStartTime())/M;
            }
            check(valid,"focus blocks respect 25-minute limit and fixed commitments");
            check(workMinutes==101,"work tasks receive 22+45+34 minutes");
            int total=first.size();plan(now);check(slots.getScheduleByDate(date).size()==total,"repeat planning adds no duplicate slots");
            Task online=new Task("Order household essentials","Life",15,1,2,at(18,30));online.setTaskType("Errands");online.setTaskId((int)tasks.insertTask(online));
            check(plan(now).unscheduledTasks.isEmpty() && tasks.getTaskById(online.getTaskId()).getPredictedDuration()==16,"onscreen task is scheduled with a 16-minute estimate");
            printPlan("Demo schedule after adding household order:");
            ScheduleItem onlineSlot=null;for(ScheduleItem item:slots.getScheduleByDate(date))if(item.getTaskId()==online.getTaskId())onlineSlot=item;
            check(onlineSlot!=null,"new online order has a real database slot");
            slots.updatePlannedTime(onlineSlot.getScheduleId(),date,at(12,10),at(12,26));plan(now);
            check(slots.getScheduleByDate(date).stream().anyMatch(s->s.getTaskId()==online.getTaskId() && s.isManuallyEdited() && s.getStartTime()==at(12,10)),"manual lunch-break preference survives planning");
            Task report=task("Weekly sales summary");learning.prepare(report.getTaskId(),now+5*M);
            LearningStore.Saved saved=learning.save("demo-recording",report.getTaskId(),50,4,true,null,"TIMER","",false,now+6*M);
            summary=learning.summary(now+6*M);
            check(summary.evaluatedTasks==5 && summary.estimateMae==12.0 && Math.abs(summary.predictionMae-10.6)<.0001,"documented final MAE after adjusted timer save");
            InsightsStats stats=InsightsStats.calculate(tasks.getTasksWithHistory(),new TaskFeedbackDao(h).getAllFeedback(),now+6*M,TimeZone.getDefault());
            check(stats.recordedMinutes==250 && stats.completedTasks==5 && stats.taskCount==10,"final totals 250 minutes and 5 of 10 tasks");
            check(learning.history(now+6*M).count==5,"save adds exactly one session");
            check(plan(now+6*M).unscheduledTasks.isEmpty(),"feedback leaves a feasible plan");
            check(slots.getScheduleByDate(date).stream().noneMatch(s->s.getTaskId()==report.getTaskId() && "PLANNED".equals(s.getStatus())),"completion releases report's future blocks");
            learning.correctRecord(saved.feedbackId,0,55,4,now+7*M);summary=learning.summary(now+7*M);
            check(learning.history(now+7*M).count==5 && learning.history(now+7*M).weekMinutes==255,"correction changes original record without duplication");
            check(summary.estimateMae==13.0 && Math.abs(summary.predictionMae-11.6)<.0001,"optional history correction values are reproducible");
        } finally {h.close();}
        try(AppDatabaseHelper archived=new AppDatabaseHelper(new Context())) {
            TaskDao dao=new TaskDao(archived);int id=(int)dao.insertTask(new Task("Old task","Life",10,1,1,0));dao.archiveTask(id);
            try{InternDemoData.seed(archived,now);throw new AssertionError("archived user data replaced");}
            catch(IllegalStateException e){check(dao.getTasksWithHistory().size()==1,"archived-only installation remains protected");}
        }
        System.out.println("PASS: "+checks+" internship demo, planning and recording-result assertions.");
    }
}
