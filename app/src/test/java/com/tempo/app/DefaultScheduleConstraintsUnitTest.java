package com.tempo.app;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.UserProfile;
import com.tempo.app.scheduler.DefaultScheduleConstraints;
import com.tempo.app.scheduler.ScheduleConstraintsProvider.DayConstraints;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;

import static org.junit.Assert.*;

public class DefaultScheduleConstraintsUnitTest {
    private static final long M = 60_000L;
    private static final long DAY = 24 * 60 * M;

    private static long midnight() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 18);
        return c.getTimeInMillis();
    }

    private static final long START = midnight();

    private static UserProfile profile() {
        UserProfile p = new UserProfile();
        p.setWakeTime("07:00"); p.setSleepTime("23:30");
        p.setBreakfastTime("08:00"); p.setLunchTime("12:30"); p.setDinnerTime("23:50");
        p.setPreferredStartTime("09:00");
        p.setFocusDuration(25); p.setBreakDuration(5); p.setDailyCapacity(240);
        return p;
    }

    @Test public void mealsAndProductiveWindowUseDefaults() {
        DayConstraints c = DefaultScheduleConstraints.build(START, START + DAY, profile(), Collections.<ScheduleItem>emptyList());
        assertEquals(3, c.fixedBusyIntervals.size());
        assertEquals(START + 8 * 60 * M, c.fixedBusyIntervals.get(0).start);
        assertEquals(START + (8 * 60 + DefaultScheduleConstraints.MEAL_MINUTES) * M, c.fixedBusyIntervals.get(0).end);
        assertEquals(START + DAY, c.fixedBusyIntervals.get(2).end); // 23:50 dinner is clipped to midnight
        assertEquals(1, c.productiveWindows.size());
        assertEquals(START + 9 * 60 * M, c.productiveWindows.get(0).start);
        assertEquals(START + (9 * 60 + DefaultScheduleConstraints.PRODUCTIVE_MINUTES) * M, c.productiveWindows.get(0).end);
    }

    @Test public void mealOverlappingLockedSlotIsSkipped() {
        ScheduleItem lock = new ScheduleItem();
        lock.setTaskId(1);
        lock.setStartTime(START + 12 * 60 * M);
        lock.setEndTime(START + 13 * 60 * M);
        lock.setManuallyEdited(true);
        lock.setStatus("PLANNED");
        DayConstraints c = DefaultScheduleConstraints.build(START, START + DAY, profile(), Collections.singletonList(lock));
        assertEquals(2, c.fixedBusyIntervals.size());
        for (com.tempo.app.scheduler.ScheduleGenerator.Interval i : c.fixedBusyIntervals) {
            assertTrue(i.end <= lock.getStartTime() || i.start >= lock.getEndTime());
        }
    }

    @Test public void invalidOrMissingTimesAreIgnored() {
        UserProfile p = profile();
        p.setLunchTime("noon"); p.setDinnerTime(null); p.setPreferredStartTime("");
        DayConstraints c = DefaultScheduleConstraints.build(START, START + DAY, p, null);
        assertEquals(1, c.fixedBusyIntervals.size());
        assertTrue(c.productiveWindows.isEmpty());
        assertTrue(DefaultScheduleConstraints.build(START, START + DAY, null, null).fixedBusyIntervals.isEmpty());
    }
}
