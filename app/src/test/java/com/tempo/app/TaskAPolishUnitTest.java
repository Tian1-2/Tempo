package com.tempo.app;

import com.tempo.app.home.DailyReview;
import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;
import com.tempo.app.model.UserProfile;
import com.tempo.app.scheduler.DefaultScheduleConstraints;
import com.tempo.app.scheduler.FixedBlocks;
import com.tempo.app.scheduler.ScheduleConstraintsProvider.DayConstraints;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.scheduler.ScheduleGenerator.Interval;
import com.tempo.app.scheduler.ScheduleModelAdapter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Fixed schedule, stable re-planning, no fragment slots and category colours (task-a polish). */
public class TaskAPolishUnitTest {
    private static final long M = 60_000L;
    private static final long DAY = 24 * 60 * M;

    /** Local midnight of a Monday, so weekday matching works in any JVM time zone. */
    private static long monday() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 16, 0, 0, 0); // Monday 16 Nov 2026
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static UserProfile profile() {
        UserProfile p = new UserProfile();
        p.setWakeTime("07:00"); p.setSleepTime("23:00");
        p.setBreakfastTime("08:00"); p.setLunchTime("12:30"); p.setDinnerTime("18:30");
        p.setPreferredStartTime("09:00");
        p.setFocusDuration(25); p.setBreakDuration(5); p.setDailyCapacity(240);
        return p;
    }

    private static ScheduleItem item(int taskId, long start, long end, boolean manual, String status) {
        ScheduleItem s = new ScheduleItem();
        s.setTaskId(taskId); s.setStartTime(start); s.setEndTime(end);
        s.setManuallyEdited(manual); s.setStatus(status);
        return s;
    }

    // ---------- fixed schedule (stored in the profile as FixedBlocks text) ----------

    @Test public void fixedBlockBlocksOnlyItsWeekday() {
        long mon = monday();
        UserProfile p = profile();
        p.setFixedBlocks("Mon 14:00-16:00\tLecture\nTue 10:00-11:00\tTutorial");
        DayConstraints c = DefaultScheduleConstraints.build(mon, mon + DAY, p, Collections.<ScheduleItem>emptyList());
        assertEquals("3 meals + Monday lecture", 4, c.fixedBusyIntervals.size());
        assertTrue(contains(c.fixedBusyIntervals, mon + 14 * 60 * M, mon + 16 * 60 * M));

        long tue = mon + DAY;
        List<Interval> tuesday = FixedBlocks.intervals(p.getFixedBlocks(), tue);
        assertEquals(1, tuesday.size());
        assertEquals(tue + 10 * 60 * M, tuesday.get(0).start);
    }

    @Test public void plannerAvoidsFixedBlock() {
        long mon = monday();
        Task task = new Task("Report", "Work", 120, 3, 3, 0);
        task.setTaskId(5);
        List<Interval> busy = FixedBlocks.intervals("Mon 09:00-12:00\tLecture", mon);
        ScheduleGenerator.Result r = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new Interval(mon, mon + DAY), mon + 9 * 60 * M,
                ScheduleModelAdapter.pendingTasks(Collections.singletonList(task)),
                Collections.singletonList(new Interval(mon + 9 * 60 * M, mon + 18 * 60 * M)), busy,
                Collections.<ScheduleGenerator.Session>emptyList(),
                Collections.<Interval>emptyList(), 50, 10, 240));
        assertFalse(r.sessions.isEmpty());
        for (ScheduleGenerator.Session s : r.sessions) {
            assertTrue("no session inside the lecture", s.interval.start >= mon + 12 * 60 * M);
        }
    }

    // ---------- stable re-planning ----------

    @Test public void upcomingAutoSlotsAreKeptAndMissedOnesAreNot() {
        long mon = monday();
        long now = mon + 10 * 60 * M;
        List<ScheduleItem> existing = Arrays.asList(
                item(1, mon + 9 * 60 * M, mon + 9 * 60 * M + 50 * M, false, "PLANNED"),   // missed
                item(1, mon + 11 * 60 * M, mon + 11 * 60 * M + 50 * M, false, "PLANNED"), // upcoming: keep
                item(2, mon + 13 * 60 * M, mon + 14 * 60 * M, true, "PLANNED"),          // manual: not auto
                item(3, mon + 15 * 60 * M, mon + 16 * 60 * M, false, "CANCELLED"));
        List<ScheduleGenerator.Session> kept = ScheduleModelAdapter.upcomingAutoSessions(existing, now);
        assertEquals(1, kept.size());
        assertEquals(mon + 11 * 60 * M, kept.get(0).interval.start);
        assertTrue(kept.get(0).locked);
    }

    @Test public void keptSlotStaysPutAndRemainderIsPlannedAround() {
        long mon = monday();
        long now = mon + 10 * 60 * M;
        Task task = new Task("Essay", "Study", 100, 3, 3, 0);
        task.setTaskId(1);
        List<ScheduleItem> existing = Collections.singletonList(
                item(1, mon + 14 * 60 * M, mon + 14 * 60 * M + 50 * M, false, "PLANNED"));
        List<ScheduleGenerator.Session> locked = ScheduleModelAdapter.upcomingAutoSessions(existing, now);
        ScheduleGenerator.Result r = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new Interval(mon, mon + DAY), now,
                ScheduleModelAdapter.pendingTasks(Collections.singletonList(task)),
                Collections.singletonList(new Interval(mon + 7 * 60 * M, mon + 23 * 60 * M)),
                Collections.<Interval>emptyList(), locked,
                Collections.<Interval>emptyList(), 50, 10, 240));
        assertTrue(r.unscheduledTasks.isEmpty());
        boolean keptFound = false;
        int fresh = 0;
        for (ScheduleGenerator.Session s : r.sessions) {
            if (s.locked) { keptFound = s.interval.start == mon + 14 * 60 * M; continue; }
            fresh += (int) ((s.interval.end - s.interval.start) / M);
            assertTrue("new work starts after now", s.interval.start >= now);
        }
        assertTrue("the slot the user saw is unchanged", keptFound);
        assertEquals("only the remaining 50 minutes are added", 50, fresh);
        // The kept row is not inserted a second time.
        assertEquals(1, ScheduleModelAdapter.newScheduleItems("2026-11-16", r).size());
    }

    @Test public void tinyGapBeforeAMealIsNotUsed() {
        long mon = monday();
        Task walk = new Task("Evening walk", "Life", 30, 1, 2, 0);
        walk.setTaskId(9);
        long now = mon + 18 * 60 * M + 29 * M; // one free minute before dinner at 18:30
        ScheduleGenerator.Result r = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new Interval(mon, mon + DAY), now,
                ScheduleModelAdapter.pendingTasks(Collections.singletonList(walk)),
                Collections.singletonList(new Interval(mon + 7 * 60 * M, mon + 23 * 60 * M)),
                Collections.singletonList(new Interval(mon + 18 * 60 * M + 30 * M, mon + 19 * 60 * M)),
                Collections.<ScheduleGenerator.Session>emptyList(),
                Collections.<Interval>emptyList(), 25, 5, 240));
        assertTrue(r.unscheduledTasks.isEmpty());
        for (ScheduleGenerator.Session s : r.sessions) {
            assertTrue("no session squeezed before dinner", s.interval.start >= mon + 19 * 60 * M);
        }
    }

    @Test public void shortGapDoesNotLeaveACrumbForLater() {
        long mon = monday();
        Task t = new Task("Write-up", "Work", 25, 3, 3, 0);
        t.setTaskId(4);
        long now = mon + 17 * 60 * M + 35 * M;
        // 22 free minutes before a fixed block at 17:57, then nothing until tomorrow.
        ScheduleGenerator.Result r = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new Interval(mon, mon + DAY), now,
                ScheduleModelAdapter.pendingTasks(Collections.singletonList(t)),
                Collections.singletonList(new Interval(mon + 7 * 60 * M, mon + 23 * 60 * M)),
                Collections.singletonList(new Interval(mon + 17 * 60 * M + 57 * M, mon + 23 * 60 * M)),
                Collections.<ScheduleGenerator.Session>emptyList(),
                Collections.<Interval>emptyList(), 25, 5, 240));
        assertEquals(1, r.sessions.size());
        assertEquals("placed 15 so 10 are left, not 22 + 3", 15, (r.sessions.get(0).interval.end - r.sessions.get(0).interval.start) / M);
        assertEquals(10, r.unscheduledTasks.get(0).remainingMinutes);
    }

    @Test public void shortTailIsSplitEvenlyAndRoundingCrumbIsDropped() {
        long mon = monday();
        Task t = new Task("Report", "Work", 27, 3, 3, 0);
        t.setTaskId(6);
        ScheduleGenerator.Result r = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new Interval(mon, mon + DAY), mon + 9 * 60 * M,
                ScheduleModelAdapter.pendingTasks(Collections.singletonList(t)),
                Collections.singletonList(new Interval(mon + 9 * 60 * M, mon + 18 * 60 * M)),
                Collections.<Interval>emptyList(), Collections.<ScheduleGenerator.Session>emptyList(),
                Collections.<Interval>emptyList(), 25, 5, 240));
        // The 25-minute focus cap holds, and there is no 2-minute crumb: 27 = 14 + 13.
        assertEquals(2, r.sessions.size());
        for (ScheduleGenerator.Session s : r.sessions) {
            long len = (s.interval.end - s.interval.start) / M;
            assertTrue("piece of " + len + " min", len >= 10 && len <= 25);
        }

        // 49 minutes needed, 47 already planned on another day: the 2-minute rest is not a new slot.
        java.util.Map<Long, Integer> elsewhere = java.util.Collections.singletonMap(6L, 47);
        List<ScheduleGenerator.Task> left = ScheduleModelAdapter.subtractPlanned(
                Collections.singletonList(new ScheduleGenerator.Task(6, 49, null, 3, 3)),
                elsewhere, java.util.Collections.<Long, Integer>emptyMap());
        assertTrue(left.isEmpty());
    }

    @Test public void almostUsedDailyLimitDoesNotCreateACrumb() {
        long mon = monday();
        Task t = new Task("Slides", "Study", 30, 3, 3, 0);
        t.setTaskId(7);
        // Only 2 minutes of the daily limit are left: plan nothing today rather than a 2-minute slot.
        ScheduleGenerator.Result r = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new Interval(mon, mon + DAY), mon + 9 * 60 * M,
                ScheduleModelAdapter.pendingTasks(Collections.singletonList(t)),
                Collections.singletonList(new Interval(mon + 9 * 60 * M, mon + 18 * 60 * M)),
                Collections.<Interval>emptyList(), Collections.<ScheduleGenerator.Session>emptyList(),
                Collections.<Interval>emptyList(), 25, 5, 2));
        assertTrue(r.sessions.isEmpty());
        assertEquals(30, r.unscheduledTasks.get(0).remainingMinutes);
    }

    // ---------- categories ----------

    @Test public void categoryColours() {
        assertEquals(com.tempo.app.model.Category.STUDY, com.tempo.app.model.Category.fallbackColor("study"));
        assertEquals(com.tempo.app.model.Category.WORK, com.tempo.app.model.Category.fallbackColor("工作"));
        assertEquals(com.tempo.app.model.Category.STUDY, com.tempo.app.model.Category.fallbackColor(null));
        int club = com.tempo.app.model.Category.fallbackColor("Club");
        assertEquals("stable for the same name", club, com.tempo.app.model.Category.fallbackColor("club"));
        int soft = com.tempo.app.model.Category.soft(0xFF000000);
        assertEquals("82% towards white", 0xFFD1D1D1, soft);
        assertEquals("#3F8F6B", com.tempo.app.model.Category.hex(com.tempo.app.model.Category.STUDY));
        assertNull(com.tempo.app.model.Category.validateName("Club"));
        assertNotNull(com.tempo.app.model.Category.validateName("  "));
        assertNotNull(com.tempo.app.model.Category.validateName("A name that is far too long"));
        assertEquals("Club", com.tempo.app.ui.Categories.name("club"));
        assertEquals("Study", com.tempo.app.ui.Categories.name("study"));
    }

    private static boolean contains(List<Interval> list, long start, long end) {
        for (Interval i : list) if (i.start == start && i.end == end) return true;
        return false;
    }
}
