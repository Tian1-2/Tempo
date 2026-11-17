import com.tempo.app.learning.SessionTiming;
import com.tempo.app.learning.DurationLearning;
import com.tempo.app.learning.TaskTypes;
import com.tempo.app.insights.TimeOfDayStats;
import java.util.*;

public final class SessionTimingCheck {
    private static int count;
    private static final long M=60000;
    public static void main(String[] args) {
        check(DurationLearning.advanceRemaining(12,5,null)==7,"carry forward explicit remaining");
        check(DurationLearning.advanceRemaining(null,5,null)==null,"no override uses original total");
        check(DurationLearning.advanceRemaining(12,5,20)==20,"new explicit override wins");
        check(DurationLearning.advanceRemaining(3,10,null)==-7,"signed overrun retained for corrections");
        check(DurationLearning.remaining(60,35,0,25)==25,"zero override cannot restore outdated total remainder");
        check(DurationLearning.remaining(60,35,-7,60)==30,"negative override reserves bounded provisional work");
        check(SessionTiming.clocksAgree(100000,1000,160000,61000),"wall and elapsed clocks agree");
        check(!SessionTiming.clocksAgree(100000,1000,160000,900),"reboot rejects active timing");
        check(!SessionTiming.clocksAgree(100000,1000,260000,61000),"wall-clock jump rejected");
        check(SessionTiming.decode("1000,2000;3000,5000").size()==2,"decode paused intervals");
        check(SessionTiming.totalMillis(SessionTiming.decode("1000,2000;3000,5000"))==3000,"pauses excluded");
        check(SessionTiming.decode("1000,3000;2000,5000").isEmpty(),"overlapping intervals rejected");
        check(SessionTiming.decode("bad").isEmpty(),"invalid interval data rejected");
        check(SessionTiming.matchesRecordedMinutes(SessionTiming.decode("1000,61000"),1,62000),"timer rounding matches stored minute");
        check(!SessionTiming.matchesRecordedMinutes(SessionTiming.decode("1000,61000"),20,62000),"manual adjustment excluded");
        TimeZone zone=TimeZone.getTimeZone("Asia/Shanghai");long now=at(zone,2026,11,17,12,0);
        TimeOfDayStats s=TimeOfDayStats.calculate(Arrays.asList(new TimeOfDayStats.Entry(1,at(zone,2026,11,16,23,50),at(zone,2026,11,17,0,10))),now,zone);
        check(s.millis[3]==10*M && s.millis[0]==10*M && s.sessions==1,"midnight split uses actual clock intervals");
        s=TimeOfDayStats.calculate(Arrays.asList(new TimeOfDayStats.Entry(1,at(zone,2026,11,17,5,50),at(zone,2026,11,17,6,10)),new TimeOfDayStats.Entry(1,at(zone,2026,11,17,7,0),at(zone,2026,11,17,7,5))),now,zone);
        check(s.millis[0]==10*M && s.millis[1]==15*M && s.sessions==1,"slot boundary and multiple segments count one session");
        s=TimeOfDayStats.calculate(Arrays.asList(new TimeOfDayStats.Entry(1,at(zone,2026,11,10,23,50),at(zone,2026,11,11,0,10))),now,zone);
        check(s.millis[0]==10*M,"seven-day boundary clips older execution time");
        s=TimeOfDayStats.calculate(Arrays.asList(new TimeOfDayStats.Entry(2,now+M,now+2*M)),now,zone);
        check(s.sessions==0,"future execution time excluded");
        TimeZone ny=TimeZone.getTimeZone("America/New_York");
        s=TimeOfDayStats.calculate(Arrays.asList(new TimeOfDayStats.Entry(1,at(ny,2026,11,1,0,0),at(ny,2026,11,1,6,0))),at(ny,2026,11,1,12,0),ny);
        check(s.millis[0]==7*60*M,"DST repeated hour counted by elapsed time");
        check(TaskTypes.options("Study",null).contains("Assignment"),"explicit study type options");
        check(TaskTypes.options("Work","Custom").contains("Custom"),"legacy custom type retained");
        System.out.println("PASS: "+count+" remaining, timing, time-of-day and type assertions.");
    }
    private static long at(TimeZone zone,int year,int month,int day,int hour,int minute){Calendar c=Calendar.getInstance(zone);c.clear();c.set(year,month-1,day,hour,minute);return c.getTimeInMillis();}
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
}
