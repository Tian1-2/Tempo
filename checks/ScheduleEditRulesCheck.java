import com.tempo.app.model.ScheduleItem;
import com.tempo.app.planning.ScheduleEditRules;
import java.util.*;

public final class ScheduleEditRulesCheck {
    static final long M=60_000L;
    static int count;
    public static void main(String[] args) {
        ScheduleItem edited=item(10,2,420,424,"PLANNED",false);
        ScheduleItem cancelled=item(11,1,445,450,"CANCELLED",true);
        List<ScheduleItem> day=Arrays.asList(edited,cancelled);
        check(!ScheduleEditRules.overlaps(day,edited,445*M,449*M),"07:25-07:29 reuses a cancelled 07:25-07:30 block");
        for(String status:new String[]{"PLANNED","COMPLETED","SKIPPED"}) {
            ScheduleItem active=item(12,1,445,450,status,false);
            check(ScheduleEditRules.overlaps(Arrays.asList(active),edited,445*M,449*M),status+" still blocks overlap");
            check(!ScheduleEditRules.overlaps(Arrays.asList(active),edited,450*M,454*M),status+" permits adjacent blocks");
        }
        check(!ScheduleEditRules.overlaps(Arrays.asList(edited),edited,420*M,424*M),"a block does not conflict with itself");
        ScheduleItem sameRow=item(10,2,420,424,"PLANNED",true);
        check(!ScheduleEditRules.overlaps(Arrays.asList(sameRow),edited,420*M,424*M),"a separate object for the same database row is excluded");
        ScheduleItem demo=item(0,2,445,450,"PLANNED",false);
        ScheduleItem otherDemo=item(0,3,445,450,"PLANNED",false);
        check(ScheduleEditRules.overlaps(Arrays.asList(otherDemo),demo,445*M,449*M),"distinct unsaved rows with id zero still conflict");
        ScheduleItem sameTaskCancelled=item(13,2,445,470,"CANCELLED",true);
        check(ScheduleEditRules.lockedMinutes(Arrays.asList(sameTaskCancelled),edited,445*M,449*M)==4,"cancelled manual blocks consume no manual capacity");
        ScheduleItem sameTaskActive=item(14,2,480,490,"PLANNED",true);
        check(ScheduleEditRules.lockedMinutes(Arrays.asList(sameTaskCancelled,sameTaskActive),edited,445*M,449*M)==14,"other active manual blocks for this task count");
        check(ScheduleEditRules.lockedMinutes(Arrays.asList(sameRow,cancelled),edited,445*M,449*M)==4,"same row and other tasks are not counted twice");
        System.out.println("PASS: "+count+" page-side manual schedule edit assertions.");
    }
    static ScheduleItem item(int id,int task,int start,int end,String status,boolean manual) {
        ScheduleItem s=new ScheduleItem();s.setScheduleId(id);s.setTaskId(task);s.setStartTime(start*M);s.setEndTime(end*M);s.setStatus(status);s.setManuallyEdited(manual);return s;
    }
    static void check(boolean ok,String msg){count++;if(!ok)throw new AssertionError(msg);}
}
