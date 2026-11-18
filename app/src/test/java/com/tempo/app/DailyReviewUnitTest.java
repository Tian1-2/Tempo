package com.tempo.app;

import com.tempo.app.home.DailyReview;
import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.Task;
import com.tempo.app.model.TaskFeedback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DailyReviewUnitTest {
    private static final long M = 60_000L;
    private static final long DAY = 24 * 60 * M;
    private static final long START = 10 * DAY; // any midnight
    private static final long END = START + DAY;

    private static Task task(int id, String title, int estimate, String status) {
        Task t = new Task(title, "Study", estimate, 3, 3, 0);
        t.setTaskId(id);
        t.setStatus(status);
        return t;
    }

    private static ScheduleItem slot(int taskId, int fromMinute, int toMinute) {
        ScheduleItem s = new ScheduleItem();
        s.setTaskId(taskId);
        s.setStartTime(START + fromMinute * M);
        s.setEndTime(START + toMinute * M);
        s.setStatus("PLANNED");
        return s;
    }

    private static TaskFeedback feedback(int taskId, int minutes, long at) {
        TaskFeedback f = new TaskFeedback();
        f.setTaskId(taskId);
        f.setActualDuration(minutes);
        f.setCompletedAt(at);
        f.setCompletionStatus("PARTIAL");
        return f;
    }

    @Test public void allDoneAndAccurateScoresHundred() {
        List<Task> tasks = Collections.singletonList(task(1, "Essay", 60, "COMPLETED"));
        List<ScheduleItem> slots = Collections.singletonList(slot(1, 9 * 60, 10 * 60));
        List<TaskFeedback> fb = Collections.singletonList(feedback(1, 60, START + 10 * 60 * M));
        fb.get(0).setCompletionStatus("COMPLETED");
        DailyReview r = DailyReview.compute(START, END, slots, tasks, fb, Collections.singletonMap(1, 60));
        assertEquals(1, r.plannedTasks);
        assertEquals(1, r.completedTasks);
        assertEquals(60, r.focusMinutes);
        assertNull(r.overrun);
        assertTrue(r.estimatesAccurate);
        assertEquals(Integer.valueOf(100), r.score);
        assertEquals("Excellent", DailyReview.scoreLabel(r.score));
    }

    @Test public void overrunUsesAccumulatedActualAndPicksWorst() {
        List<Task> tasks = Arrays.asList(task(1, "Essay", 60, "COMPLETED"), task(2, "Reading", 30, "PENDING"));
        List<ScheduleItem> slots = Arrays.asList(slot(1, 540, 600), slot(2, 600, 630));
        List<TaskFeedback> fb = Arrays.asList(
                feedback(1, 30, START - DAY + 5 * M),     // day before: counts toward accumulated actual only
                feedback(1, 70, START + 11 * 60 * M),     // essay total 100 vs 60 -> +40
                feedback(2, 40, START + 12 * 60 * M),     // reading 40 vs 30 -> +10
                feedback(2, 99, END + M));                // after the day: ignored
        fb.get(1).setCompletionStatus("COMPLETED");
        DailyReview r = DailyReview.compute(START, END, slots, tasks, fb, Collections.singletonMap(1, 60));
        assertEquals(110, r.focusMinutes);
        assertNotNull(r.overrun);
        assertEquals("Essay", r.overrun.title);
        assertEquals(100, r.overrun.actualMinutes);
        assertFalse(r.estimatesAccurate);
        // completion 25/50; focus 30/30; only completed Essay is comparable: (1-40/60)*20.
        assertEquals(Integer.valueOf(62), r.score);
        assertTrue(r.reminder().contains("Essay"));
    }

    @Test public void missingPartsAreReweightedAndEmptyDayHasNoScore() {
        List<Task> tasks = Collections.singletonList(task(1, "Gym", 45, "PENDING"));
        DailyReview planOnly = DailyReview.compute(START, END,
                Collections.singletonList(slot(1, 1000, 1045)), tasks, new ArrayList<TaskFeedback>());
        assertEquals(Integer.valueOf(0), planOnly.score); // nothing done, nothing focused
        assertNull(planOnly.reminder());
        assertTrue(planOnly.encouragement().contains("fresh start"));

        DailyReview empty = DailyReview.compute(START, END, null, tasks, null);
        assertTrue(empty.isEmpty());
        assertNull(empty.score);
    }

    @Test public void formatsMinutes() {
        assertEquals("45 min", DailyReview.formatMinutes(45));
        assertEquals("2 h", DailyReview.formatMinutes(120));
        assertEquals("1 h 40 min", DailyReview.formatMinutes(100));
    }
}
