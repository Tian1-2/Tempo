package com.tempo.app.scheduler;

import com.tempo.app.scheduler.ScheduleGenerator.Interval;
import com.tempo.app.scheduler.ScheduleGenerator.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Groups adjacent sessions of one task for the Home screen without changing saved schedule data. */
public final class ScheduleDisplayBlocks {
    private ScheduleDisplayBlocks() {}

    public static final class Block {
        public final long taskId;
        public final long startMillis;
        public final long endMillis;
        public final int taskMinutes;
        public final int breakMinutes;

        private Block(long taskId, long startMillis, long endMillis, int taskMinutes, int breakMinutes) {
            this.taskId = taskId;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.taskMinutes = taskMinutes;
            this.breakMinutes = breakMinutes;
        }

        /** Default true: show task time plus the breaks between its sessions. */
        public int shownMinutes(boolean includeBreaks) {
            return taskMinutes + (includeBreaks ? breakMinutes : 0);
        }
    }

    public static List<Block> group(List<Session> sessions, List<Interval> fixedBusyIntervals, int maxBreakMinutes) {
        if (sessions == null || fixedBusyIntervals == null || maxBreakMinutes < 0) {
            throw new IllegalArgumentException("Invalid display inputs");
        }
        List<Session> ordered = new ArrayList<>(sessions);
        ordered.sort((a, b) -> Long.compare(a.interval.start, b.interval.start));
        List<Block> result = new ArrayList<>();
        for (Session session : ordered) {
            int duration = (int) ((session.interval.end - session.interval.start) / 60_000L);
            int last = result.size() - 1;
            if (last >= 0) {
                Block previous = result.get(last);
                long gap = session.interval.start - previous.endMillis;
                if (previous.taskId == session.taskId && gap >= 0 && gap <= maxBreakMinutes * 60_000L
                        && !containsFixedTime(previous.endMillis, session.interval.start, fixedBusyIntervals)) {
                    result.set(last, new Block(session.taskId, previous.startMillis, session.interval.end,
                            previous.taskMinutes + duration, previous.breakMinutes + (int) (gap / 60_000L)));
                    continue;
                }
            }
            result.add(new Block(session.taskId, session.interval.start, session.interval.end, duration, 0));
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean containsFixedTime(long start, long end, List<Interval> fixed) {
        for (Interval interval : fixed) if (start < interval.end && interval.start < end) return true;
        return false;
    }
}
