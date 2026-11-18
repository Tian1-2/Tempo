package com.tempo.app;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.scheduler.ScheduleGenerator;
import com.tempo.app.scheduler.ScheduleModelAdapter;
import com.tempo.app.scheduler.ScheduleDisplayBlocks;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.*;

public class ScheduleIntegrationUnitTest {
    private static final long M = 60_000L;

    @Test public void databaseModelsGenerateReplannableSchedule() {
        Task task = new Task("Assignment", "Study", 120, 5, 5, 17 * 60 * M);
        task.setTaskId(7);
        ScheduleItem lock = new ScheduleItem();
        lock.setTaskId(7);
        lock.setScheduledDate("2026-11-14");
        lock.setStartTime(14 * 60 * M);
        lock.setEndTime((14 * 60 + 30) * M);
        lock.setManuallyEdited(true);
        lock.setStatus("PLANNED");
        ScheduleGenerator.Request request = new ScheduleGenerator.Request(
                new ScheduleGenerator.Interval(0, 24 * 60 * M), 9 * 60 * M,
                ScheduleModelAdapter.pendingTasks(Collections.singletonList(task)),
                Collections.singletonList(new ScheduleGenerator.Interval(9 * 60 * M, 18 * 60 * M)),
                Collections.singletonList(new ScheduleGenerator.Interval(12 * 60 * M, 13 * 60 * M)),
                ScheduleModelAdapter.lockedSessions(Collections.singletonList(lock)),
                Collections.<ScheduleGenerator.Interval>emptyList(), 50, 10, 180);
        ScheduleGenerator.Result result = new ScheduleGenerator().generate(request);
        assertEquals(0, result.unscheduledTasks.size());
        assertTrue(result.sessions.stream().anyMatch(s -> s.locked && s.interval.start == lock.getStartTime()));
        List<ScheduleItem> fresh = ScheduleModelAdapter.newScheduleItems("2026-11-14", result);
        assertFalse(fresh.isEmpty());
        assertEquals(result.sessions.size() - 1, fresh.size());
        assertTrue(fresh.stream().allMatch(s -> !s.isManuallyEdited() && "PLANNED".equals(s.getStatus())));
        int total = 30;
        for (ScheduleItem item : fresh) total += (item.getEndTime() - item.getStartTime()) / M;
        assertEquals(120, total);
    }

    @Test public void predictedDurationOverridesEstimate() {
        Task task = new Task("Reading", "Study", 60, 2, 3, 0);
        task.setTaskId(1);
        task.setPredictedDuration(90);
        assertEquals(90, ScheduleModelAdapter.pendingTasks(Arrays.asList(task)).get(0).durationMinutes);
    }

    @Test public void priorityBalancesImportanceAndDeadline() {
        long now = 10 * 60 * M;
        ScheduleGenerator.Task tomorrowLow = new ScheduleGenerator.Task(1, 20, now + 24 * 60 * M, 1, 2);
        ScheduleGenerator.Task twoDaysHigh = new ScheduleGenerator.Task(2, 20, now + 48 * 60 * M, 5, 2);
        ScheduleGenerator.Task imminentLow = new ScheduleGenerator.Task(3, 20, now + 60 * M, 1, 2);
        assertTrue(ScheduleGenerator.priorityScore(twoDaysHigh, now) > ScheduleGenerator.priorityScore(tomorrowLow, now));
        assertTrue(ScheduleGenerator.priorityScore(imminentLow, now) > ScheduleGenerator.priorityScore(twoDaysHigh, now));
    }

    @Test public void displayCanIncludeOrHideBreakMinutes() {
        List<ScheduleGenerator.Session> sessions = Arrays.asList(
                new ScheduleGenerator.Session(1, new ScheduleGenerator.Interval(9 * 60 * M, (9 * 60 + 50) * M), false),
                new ScheduleGenerator.Session(1, new ScheduleGenerator.Interval(10 * 60 * M, (10 * 60 + 30) * M), false));
        List<ScheduleDisplayBlocks.Block> blocks = ScheduleDisplayBlocks.group(
                sessions, Collections.<ScheduleGenerator.Interval>emptyList(), 10);
        assertEquals(1, blocks.size());
        assertEquals(80, blocks.get(0).shownMinutes(false));
        assertEquals(90, blocks.get(0).shownMinutes(true));
    }

    @Test public void breakAfterFocusCannotBeFilledByAnotherTask() {
        ScheduleGenerator.Result result = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new ScheduleGenerator.Interval(0, 24 * 60 * M), 9 * 60 * M,
                Arrays.asList(new ScheduleGenerator.Task(1, 60, null, 5, 2),
                        new ScheduleGenerator.Task(2, 45, null, 3, 2)),
                Collections.singletonList(new ScheduleGenerator.Interval(9 * 60 * M, 13 * 60 * M)),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                45, 5, 180));
        assertEquals(3, result.sessions.size());
        assertEquals(1, result.sessions.get(0).taskId);
        assertEquals(1, result.sessions.get(1).taskId);
        assertEquals(2, result.sessions.get(2).taskId);
        assertEquals(5 * M, result.sessions.get(1).interval.start - result.sessions.get(0).interval.end);
        assertEquals(5 * M, result.sessions.get(2).interval.start - result.sessions.get(1).interval.end);
    }

    @Test public void automaticTaskKeepsBreakAroundManualSlot() {
        ScheduleGenerator.Session locked = new ScheduleGenerator.Session(2,
                new ScheduleGenerator.Interval(9 * 60 * M, (9 * 60 + 45) * M), true);
        ScheduleGenerator.Result result = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new ScheduleGenerator.Interval(0, 24 * 60 * M), 9 * 60 * M,
                Arrays.asList(new ScheduleGenerator.Task(1, 20, null, 5, 2),
                        new ScheduleGenerator.Task(2, 45, null, 3, 2)),
                Collections.singletonList(new ScheduleGenerator.Interval(9 * 60 * M, 13 * 60 * M)),
                Collections.emptyList(), Collections.singletonList(locked), Collections.emptyList(),
                45, 5, 180));
        assertEquals(2, result.sessions.size());
        assertEquals((9 * 60 + 50) * M, result.sessions.get(1).interval.start);
    }

    @Test public void replanningDoesNotAssignRetainedPastMinutesAgain() {
        ScheduleItem past = new ScheduleItem();
        past.setTaskId(1);
        past.setStartTime(9 * 60 * M);
        past.setEndTime((9 * 60 + 45) * M);
        past.setStatus("PLANNED");
        past.setManuallyEdited(true);
        ScheduleItem cancelled = new ScheduleItem();
        cancelled.setTaskId(1);
        cancelled.setStartTime(10 * 60 * M);
        cancelled.setEndTime((10 * 60 + 30) * M);
        cancelled.setStatus("CANCELLED");
        Map<Long, Integer> retained = ScheduleModelAdapter.ongoingMinutes(
                Arrays.asList(past, cancelled), (9 * 60 + 1) * M);
        assertEquals(Integer.valueOf(44), retained.get(1L));
        List<ScheduleGenerator.Task> remaining = ScheduleModelAdapter.subtractPlanned(
                Collections.singletonList(new ScheduleGenerator.Task(1, 60, null, 3, 2)),
                retained, new HashMap<Long, Integer>());
        assertEquals(16, remaining.get(0).durationMinutes);
    }

    @Test public void missedAutomaticSlotIsAvailableForFreshPlanning() {
        ScheduleItem missed = new ScheduleItem();
        missed.setTaskId(1);
        missed.setStartTime(9 * 60 * M);
        missed.setEndTime((9 * 60 + 45) * M);
        missed.setStatus("PLANNED");
        assertTrue(ScheduleModelAdapter.ongoingMinutes(
                Collections.singletonList(missed), 10 * 60 * M).isEmpty());
    }

    @Test public void taskWaitsForWindowThatFitsItsWholeFocusBlock() {
        ScheduleGenerator.Result result = new ScheduleGenerator().generate(new ScheduleGenerator.Request(
                new ScheduleGenerator.Interval(0, 24 * 60 * M), 9 * 60 * M,
                Arrays.asList(new ScheduleGenerator.Task(1, 60, null, 5, 2),
                        new ScheduleGenerator.Task(2, 10, null, 3, 2)),
                Collections.singletonList(new ScheduleGenerator.Interval(9 * 60 * M, 12 * 60 * M)),
                Collections.singletonList(new ScheduleGenerator.Interval((9 * 60 + 15) * M,
                        (9 * 60 + 30) * M)), Collections.emptyList(), Collections.emptyList(),
                45, 5, 180));
        List<ScheduleGenerator.Session> firstTask = new java.util.ArrayList<>();
        for (ScheduleGenerator.Session session : result.sessions) if (session.taskId == 1) firstTask.add(session);
        assertEquals(2, firstTask.size());
        assertEquals((9 * 60 + 30) * M, firstTask.get(0).interval.start);
        assertEquals(45 * M, firstTask.get(0).interval.end - firstTask.get(0).interval.start);
        assertEquals(5 * M, firstTask.get(1).interval.start - firstTask.get(0).interval.end);
        assertEquals(15 * M, firstTask.get(1).interval.end - firstTask.get(1).interval.start);
        assertEquals(1, ScheduleDisplayBlocks.group(firstTask, Collections.emptyList(), 5).size());
    }
}
