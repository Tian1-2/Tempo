package com.tempo.app.home;

import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.*;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.model.*;
import com.tempo.app.scheduler.FixedBlocks;
import java.text.SimpleDateFormat;
import java.util.*;

/** Explicit debug-only demo fixture. All figures are simulated; no UI statistics are hard-coded.
 * The caller exposes this only on an empty demo installation, after confirmation. */
public final class InternDemoData {
    private static final long M=60000L;
    private final AppDatabaseHelper helper;
    private final LearningStore learning;
    private final TaskDao tasks;
    private final ScheduleDao schedules;
    private final long now;

    private InternDemoData(AppDatabaseHelper helper,long now) {
        this.helper=helper;this.now=now;learning=new LearningStore(helper);
        tasks=new TaskDao(helper);schedules=new ScheduleDao(helper);
    }

    /** Requires Monday morning so the meeting and all future commitments remain visible. */
    public static void seed(AppDatabaseHelper helper,long now) {
        Calendar date=Calendar.getInstance();date.setTimeInMillis(now);
        int minute=date.get(Calendar.HOUR_OF_DAY)*60+date.get(Calendar.MINUTE);
        if(date.get(Calendar.DAY_OF_WEEK)!=Calendar.MONDAY || minute<420 || minute>505)
            throw new IllegalArgumentException("Set the demo device to Monday, 07:00–08:25, then try again. Suggested: 23 Nov 2026, 08:00.");
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
        try {
            // Includes archived tasks; never mix fixtures with an existing user's activity.
            if(!new TaskDao(helper).getTasksWithHistory().isEmpty())
                throw new IllegalStateException("Use an empty demo installation. Existing tasks, including archived tasks, will not be replaced.");
            new InternDemoData(helper,now).populate();
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    private void populate() {
        UserProfile profile=new UserProfile();profile.setWakeTime("07:00");profile.setSleepTime("23:30");
        profile.setBreakfastTime("07:30");profile.setLunchTime("12:30");profile.setDinnerTime("18:30");
        profile.setPreferredStartTime("10:30");profile.setFocusDuration(25);profile.setBreakDuration(5);
        profile.setDailyCapacity(240);
        String fixed="";
        for(int day=1;day<=5;day++) {
            fixed=FixedBlocks.save(fixed,null,day,480,510,"Commute to internship");
            fixed=FixedBlocks.save(fixed,null,day,day==1?570:540,630,"Internship · routine work");
            fixed=FixedBlocks.save(fixed,null,day,780,1080,"Internship · routine work");
            fixed=FixedBlocks.save(fixed,null,day,1140,1320,"Evening class");
        }
        fixed=FixedBlocks.save(fixed,null,1,540,570,"Weekly team meeting");
        profile.setFixedBlocks(fixed);new UserProfileDao(helper).saveProfile(profile);

        // A small, deliberately labelled fixture, using real prepare/save and timer-segment paths.
        done(-3,10,30,"Last week's sales summary","Work","Report",60,90,3,4);
        done(-2,10,0,"Read the HCI paper","Study","Reading",40,50,3,4);
        done(-1,14,0,"Review database notes","Study","Revision",30,35,2,2);
        done(-1,17,0,"Weekend grocery run","Life","Errands",20,25,1,1);

        task("Prepare Monday team update","Work","Meeting preparation",20,2,5,at(0,9,0));
        task("Weekly sales summary","Work","Report",40,3,4,at(0,12,30));
        task("Update expense tracker","Work","Administration",30,2,3,at(0,18,0));
        int study=task("Review lecture notes","Study","Revision",40,3,3,at(0,23,30));
        int groceries=task("Buy groceries","Life","Errands",20,1,2,at(0,18,30));
        learning.refreshPredictions(now);
        // These two preferences are manual reference slots, not a claim of category-aware scheduling.
        lock(groceries,at(0,18,0),tasks.getTaskById(groceries).getPredictedDuration(),25,5);
        lock(study,at(0,22,5),tasks.getTaskById(study).getPredictedDuration(),25,5);
    }

    private int task(String title,String category,String type,int estimate,int difficulty,int importance,long deadline) {
        Task t=new Task(title,category,estimate,difficulty,importance,deadline);t.setTaskType(type);
        t.setDescription("Simulated demo data · internship and evening study");t.setCreatedAt(now);
        long id=tasks.insertTask(t);
        if(id<=0)throw new IllegalStateException("Could not create demo task.");
        return (int)id;
    }

    private void done(int offset,int hour,int minute,String title,String category,String type,int estimate,int actual,int difficulty,int felt) {
        long start=at(offset,hour,minute);
        Task t=new Task(title,category,estimate,difficulty,3,0);t.setTaskType(type);
        t.setDescription("Simulated historical activity for the recording demo");t.setCreatedAt(start-60*M);
        int id=(int)tasks.insertTask(t);if(id<=0)throw new IllegalStateException("Could not create demo history.");
        slot(id,start,estimate,false);
        learning.prepare(id,start);
        long end=start+actual*M;
        learning.save("intern-demo-"+id,id,actual,felt,true,null,"TIMER",start+","+end,true,end);
    }

    private void lock(int id,long start,int total,int focus,int rest) {
        while(total>0) {
            int minutes=Math.min(total,focus);slot(id,start,minutes,true);
            total-=minutes;start+=(minutes+rest)*M;
        }
    }

    private void slot(int id,long start,int minutes,boolean manual) {
        ScheduleItem item=new ScheduleItem();item.setTaskId(id);item.setStartTime(start);item.setEndTime(start+minutes*M);
        item.setScheduledDate(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(start)));
        item.setManuallyEdited(manual);item.setStatus("PLANNED");
        if(schedules.insertScheduleItem(item)<=0)throw new IllegalStateException("Could not create demo time slot.");
    }

    private long at(int offset,int hour,int minute) {
        Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.add(Calendar.DATE,offset);
        c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
}
