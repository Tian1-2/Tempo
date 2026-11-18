import com.tempo.app.learning.DurationLearning;
import com.tempo.app.model.Task;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.scheduler.ScheduleModelAdapter;
import java.util.*;

public final class DurationLearningCheck {
    private static final long NOW=1_800_000_000_000L, DAY=86_400_000L, M=60_000L;
    private static int count;
    public static void main(String[] args) {
        check(predict(Collections.emptyList()).minutes==30,"cold start preserves estimate");
        List<DurationLearning.Sample> one=Arrays.asList(sample(1,"Work","Report",30,60,NOW));
        check(predict(one).minutes==38,"one double-duration outcome gives conservative 38, not 60");
        check(predict(one).sampleCount==1,"one task not one sample per session");
        check("Same category".equals(predict(one).scope),"sparse type backs off to category");
        List<DurationLearning.Sample> four=new ArrayList<>(one);
        for(int i=2;i<=4;i++)four.add(sample(i,"Work","Report",30,60,NOW));
        check(predict(four).minutes==47,"more consistent observations increase correction");
        check("Same task type".equals(predict(four).scope),"three or more support fine type");
        List<DurationLearning.Sample> old=Arrays.asList(sample(1,"Work","Report",30,60,NOW-120*DAY));
        check(predict(old).minutes<predict(one).minutes,"old data has less influence");
        List<DurationLearning.Sample> future=Arrays.asList(sample(1,"Work","Report",30,60,NOW+1));
        check(predict(future).minutes==30,"future observation excluded");
        check(DurationLearning.predict(1,"Work","Report",30,one,NOW).minutes==30,"target task cannot train itself");
        check(DurationLearning.predict(9,"Study","Report",30,one,NOW).minutes==30,"unrelated category excluded");
        check(DurationLearning.predict(9," 工作 ","Report",30,one,NOW).minutes==38,"normalized category");
        check(predict(Arrays.asList(sample(1,"Work","Report",0,60,NOW))).minutes==30,"zero estimate excluded");
        check(predict(Arrays.asList(sample(1,"Work","Report",30,0,NOW))).minutes==30,"missing actual excluded");
        check(predict(Arrays.asList(sample(1,"Work","Report",30,30000,NOW))).minutes==45,"outlier influence capped");
        check(predict(Arrays.asList(sample(1,"Work","Report",30,1,NOW))).minutes==26,"very low ratio capped");
        check(DurationLearning.predict(9,"Work","Report",0,one,NOW).minutes==0,"invalid target not fabricated");
        check(DurationLearning.remaining(38,7,null,25)==31,"subtract cumulative work");
        check(DurationLearning.remaining(38,40,null,25)==25,"overrun is not automatic completion");
        check(DurationLearning.remaining(38,40,12,25)==12,"explicit remaining takes precedence");
        check(DurationLearning.remaining(38,40,null,60)==30,"bounded overrun block");
        check(DurationLearning.remaining(38,40,null,1)==5,"minimum overrun block");

        Task task=new Task("Next report","Work",30,3,3,0); task.setTaskId(9);
        task.setPredictedDuration(predict(one).minutes); task.setStatus("IN_PROGRESS");
        Map<Integer,Integer> remainder=Collections.singletonMap(9,DurationLearning.remaining(task.getPredictedDuration(),7,null,25));
        List<ScheduleGenerator.Task> input=ScheduleModelAdapter.pendingTasks(Arrays.asList(task),remainder);
        check(input.get(0).durationMinutes==31,"C receives remaining duration, not total prediction");
        ScheduleGenerator.Result result=new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new ScheduleGenerator.Interval(0,24*60*M),9*60*M,input,
                Arrays.asList(new ScheduleGenerator.Interval(7*60*M,23*60*M)),Collections.emptyList(),
                Collections.emptyList(),Collections.emptyList(),25,5,240));
        long total=0;for(ScheduleGenerator.Session s:result.sessions)total+=(s.interval.end-s.interval.start)/M;
        check(total==31 && result.sessions.size()==2,"31 remaining minutes scheduled across two blocks");
        check(task.getPredictedDuration()==38,"persisted task prediction remains a total");
        task.setStatus("COMPLETED");
        check(ScheduleModelAdapter.pendingTasks(Arrays.asList(task),remainder).isEmpty(),"completed task excluded");
        System.out.println("PASS: "+count+" learning and remaining-work assertions.");
    }
    private static DurationLearning.Prediction predict(List<DurationLearning.Sample> h){return DurationLearning.predict(9,"Work","Report",30,h,NOW);}
    private static DurationLearning.Sample sample(int id,String c,String t,int estimate,long actual,long date){return new DurationLearning.Sample(id,c,t,estimate,estimate,actual,date,false);}
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
}
