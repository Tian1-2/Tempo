package com.tempo.app;

import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.scheduler.ScheduleGenerator.Interval;
import com.tempo.app.scheduler.ScheduleModelAdapter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Mirrors ScheduleService.generateRange: each day only plans what earlier days left over. */
public class MultiDayPlanningUnitTest {
    private static final long M = 60_000L;
    private static final long DAY = 24 * 60 * M;

    @Test public void plannedElsewhereIsSubtractedAndFinishedTasksDropped() {
        List<ScheduleGenerator.Task> tasks = Arrays.asList(
                new ScheduleGenerator.Task(1, 120, null, 3, 3),
                new ScheduleGenerator.Task(2, 30, null, 3, 3),
                new ScheduleGenerator.Task(3, 60, null, 3, 3));
        Map<Long, Integer> elsewhere = new HashMap<>();
        elsewhere.put(1L, 50);
        elsewhere.put(2L, 30);
        elsewhere.put(3L, 60);
        Map<Long, Integer> lockedHere = new HashMap<>();
        lockedHere.put(3L, 20); // locked today, although planned elsewhere: keep it valid
        List<ScheduleGenerator.Task> left = ScheduleModelAdapter.subtractPlanned(tasks, elsewhere, lockedHere);
        assertEquals(2, left.size());
        assertEquals(1, left.get(0).id);
        assertEquals(70, left.get(0).durationMinutes);
        assertEquals(3, left.get(1).id);
        assertEquals(20, left.get(1).durationMinutes);
    }

    @Test public void workThatDoesNotFitTodayContinuesTomorrowWithoutDuplicates() {
        // 180 min task, but only 120 min of task time per day.
        ScheduleGenerator.Task big = new ScheduleGenerator.Task(7, 180, null, 4, 3);
        Map<Long, Integer> planned = new HashMap<>();
        List<ScheduleGenerator.Result> days = new ArrayList<>();
        for (int d = 0; d < 3; d++) {
            long start = d * DAY;
            List<ScheduleGenerator.Task> tasks = ScheduleModelAdapter.subtractPlanned(
                    Collections.singletonList(big), planned, Collections.<Long, Integer>emptyMap());
            if (tasks.isEmpty()) break;
            ScheduleGenerator.Result result = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                    new Interval(start, start + DAY), start, tasks,
                    Collections.singletonList(new Interval(start + 9 * 60 * M, start + 18 * 60 * M)),
                    Collections.<Interval>emptyList(), Collections.<ScheduleGenerator.Session>emptyList(),
                    Collections.<Interval>emptyList(), 60, 10, 120));
            days.add(result);
            for (ScheduleGenerator.Session s : result.sessions) {
                planned.merge(s.taskId, (int) ((s.interval.end - s.interval.start) / M), Integer::sum);
            }
        }
        assertEquals(2, days.size());
        assertEquals(Integer.valueOf(180), planned.get(7L));
        ScheduleGenerator.Result combined = ScheduleGenerator.Result.combine(days);
        assertTrue(combined.unscheduledTasks.isEmpty());
        int total = 0;
        for (ScheduleGenerator.Session s : combined.sessions) total += (int) ((s.interval.end - s.interval.start) / M);
        assertEquals(180, total);
    }
}
