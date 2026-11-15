package com.tempo.app.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure Java, single-day scheduler. All timestamps are Unix milliseconds. */
public final class ScheduleGenerator {
    private static final long MINUTE = 60_000L;

    public static final class Interval {
        public final long start;
        public final long end;

        public Interval(long start, long end) {
            if (start >= end || start % MINUTE != 0 || end % MINUTE != 0) {
                throw new IllegalArgumentException("Interval must have minute-aligned start < end");
            }
            this.start = start;
            this.end = end;
        }
    }

    public static final class Task {
        public final long id;
        public final int durationMinutes;
        public final Long deadlineMillis; // null means no deadline
        public final int importance;
        public final int difficulty;

        public Task(long id, int durationMinutes, Long deadlineMillis, int importance, int difficulty) {
            if (durationMinutes <= 0 || importance < 1 || importance > 5 || difficulty < 1 || difficulty > 5) {
                throw new IllegalArgumentException("Invalid task values");
            }
            this.id = id;
            this.durationMinutes = durationMinutes;
            this.deadlineMillis = deadlineMillis;
            this.importance = importance;
            this.difficulty = difficulty;
        }
    }

    public static final class Session {
        public final long taskId;
        public final Interval interval;
        public final boolean locked;

        public Session(long taskId, Interval interval, boolean locked) {
            if (interval == null) throw new IllegalArgumentException("Session interval is null");
            this.taskId = taskId;
            this.interval = interval;
            this.locked = locked;
        }
    }

    public static final class Request {
        public final Interval day;
        public final long earliestStartMillis;
        public final List<Task> tasks;
        public final List<Interval> availableWindows;
        public final List<Interval> blockedIntervals;
        public final List<Session> lockedSessions;
        public final List<Interval> productiveWindows;
        public final int focusMinutes;
        public final int breakMinutes;
        public final int maxTaskMinutes;

        public Request(Interval day, long earliestStartMillis, List<Task> tasks,
                       List<Interval> availableWindows, List<Interval> blockedIntervals,
                       List<Session> lockedSessions, List<Interval> productiveWindows,
                       int focusMinutes, int breakMinutes, int maxTaskMinutes) {
            if (day == null || tasks == null || availableWindows == null || blockedIntervals == null
                    || lockedSessions == null || productiveWindows == null || focusMinutes <= 0
                    || breakMinutes < 0 || maxTaskMinutes < 0 || earliestStartMillis % MINUTE != 0) {
                throw new IllegalArgumentException("Invalid schedule request");
            }
            this.day = day;
            this.earliestStartMillis = earliestStartMillis;
            this.tasks = new ArrayList<>(tasks);
            this.availableWindows = new ArrayList<>(availableWindows);
            this.blockedIntervals = new ArrayList<>(blockedIntervals);
            this.lockedSessions = new ArrayList<>(lockedSessions);
            this.productiveWindows = new ArrayList<>(productiveWindows);
            this.focusMinutes = focusMinutes;
            this.breakMinutes = breakMinutes;
            this.maxTaskMinutes = maxTaskMinutes;
        }
    }

    public enum Reason { NO_CAPACITY, DEADLINE }

    public static final class UnscheduledTask {
        public final long taskId;
        public final int remainingMinutes;
        public final Reason reason;

        private UnscheduledTask(long taskId, int remainingMinutes, Reason reason) {
            this.taskId = taskId;
            this.remainingMinutes = remainingMinutes;
            this.reason = reason;
        }
    }

    public static final class Result {
        public final List<Session> sessions;
        public final List<UnscheduledTask> unscheduledTasks;

        private Result(List<Session> sessions, List<UnscheduledTask> unscheduledTasks) {
            this.sessions = Collections.unmodifiableList(sessions);
            this.unscheduledTasks = Collections.unmodifiableList(unscheduledTasks);
        }
    }

    public Result generate(Request request) {
        if (request == null) throw new IllegalArgumentException("Request is null");
        validate(request);
        List<Session> sessions = new ArrayList<>(request.lockedSessions);
        List<UnscheduledTask> unscheduled = new ArrayList<>();
        List<Task> tasks = new ArrayList<>(request.tasks);
        final long referenceTime = Math.max(request.day.start, request.earliestStartMillis);
        tasks.sort(Comparator.comparingDouble((Task t) -> priorityScore(t, referenceTime)).reversed()
                .thenComparingLong(t -> t.deadlineMillis == null ? Long.MAX_VALUE : t.deadlineMillis)
                .thenComparingLong(t -> t.id));
        int used = 0;
        for (Session s : sessions) used += minutes(s.interval);

        for (Task task : tasks) {
            int lockedMinutes = 0;
            for (Session s : request.lockedSessions) {
                if (s.taskId == task.id) {
                    lockedMinutes += minutes(s.interval);
                }
            }
            if (lockedMinutes > task.durationMinutes) throw new IllegalArgumentException("Locked time exceeds task duration: " + task.id);
            int remaining = task.durationMinutes - lockedMinutes;
            long cursor = Math.max(request.day.start, request.earliestStartMillis);
            long deadline = task.deadlineMillis == null ? request.day.end
                    : Math.min(request.day.end, Math.floorDiv(task.deadlineMillis, MINUTE) * MINUTE);
            while (remaining > 0 && used < request.maxTaskMinutes && cursor < deadline) {
                List<Interval> free = freeWindows(request, sessions, task.id, cursor, deadline);
                Interval chosen = chooseWindow(free, request.productiveWindows, task.difficulty);
                if (chosen == null) break;
                int length = Math.min(Math.min(remaining, request.focusMinutes),
                        Math.min(request.maxTaskMinutes - used, minutes(chosen)));
                if (length <= 0) break;
                Interval interval = new Interval(chosen.start, chosen.start + length * MINUTE);
                sessions.add(new Session(task.id, interval, false));
                remaining -= length;
                used += length;
                cursor = interval.end + request.breakMinutes * MINUTE;
            }
            if (remaining > 0) {
                Reason reason = deadline < request.day.end && used < request.maxTaskMinutes ? Reason.DEADLINE : Reason.NO_CAPACITY;
                unscheduled.add(new UnscheduledTask(task.id, remaining, reason));
            }
        }
        sessions.sort(Comparator.comparingLong((Session s) -> s.interval.start).thenComparingLong(s -> s.taskId));
        return new Result(sessions, unscheduled);
    }

    private static void validate(Request r) {
        Set<Long> ids = new HashSet<>();
        for (Task t : r.tasks) {
            if (t == null || !ids.add(t.id)) throw new IllegalArgumentException("Null or duplicate task");
        }
        for (Interval i : r.availableWindows) requireInDay(i, r.day);
        for (Interval i : r.blockedIntervals) requireInDay(i, r.day);
        for (Interval i : r.productiveWindows) requireInDay(i, r.day);
        int lockedTotal = 0;
        for (int n = 0; n < r.lockedSessions.size(); n++) {
            Session s = r.lockedSessions.get(n);
            if (s == null || !s.locked || !ids.contains(s.taskId)) throw new IllegalArgumentException("Invalid locked session");
            requireInDay(s.interval, r.day);
            if (!containedInAny(s.interval, r.availableWindows)) throw new IllegalArgumentException("Locked session outside availability");
            for (Interval blocked : r.blockedIntervals) if (overlaps(s.interval, blocked)) throw new IllegalArgumentException("Locked session overlaps blocked time");
            for (int j = 0; j < n; j++) if (overlaps(s.interval, r.lockedSessions.get(j).interval)) throw new IllegalArgumentException("Locked sessions overlap");
            for (Task t : r.tasks) if (t.id == s.taskId && t.deadlineMillis != null && s.interval.end > t.deadlineMillis)
                throw new IllegalArgumentException("Locked session exceeds deadline");
            lockedTotal += minutes(s.interval);
        }
        if (lockedTotal > r.maxTaskMinutes) throw new IllegalArgumentException("Locked sessions exceed daily capacity");
    }

    private static List<Interval> freeWindows(Request r, List<Session> occupied, long taskId, long from, long until) {
        List<Interval> free = new ArrayList<>();
        for (Interval available : r.availableWindows) {
            long start = Math.max(available.start, from), end = Math.min(available.end, until);
            if (start < end) free.add(new Interval(start, end));
        }
        for (Interval blocked : r.blockedIntervals) free = subtract(free, blocked);
        for (Session session : occupied) {
            free = subtract(free, session.interval);
            if (session.taskId == taskId && r.breakMinutes > 0) {
                long padding = r.breakMinutes * MINUTE;
                free = subtract(free, new Interval(session.interval.start - padding, session.interval.end + padding));
            }
        }
        free.sort(Comparator.comparingLong(i -> i.start));
        return free;
    }

    private static List<Interval> subtract(List<Interval> windows, Interval cut) {
        List<Interval> out = new ArrayList<>();
        for (Interval w : windows) {
            if (!overlaps(w, cut)) { out.add(w); continue; }
            if (w.start < cut.start) out.add(new Interval(w.start, cut.start));
            if (cut.end < w.end) out.add(new Interval(cut.end, w.end));
        }
        return out;
    }

    private static Interval chooseWindow(List<Interval> free, List<Interval> productive, int difficulty) {
        if (free.isEmpty()) return null;
        // Difficult tasks try the first productive window; other tasks use the earliest free time.
        if (difficulty >= 4) {
            Interval best = null;
            for (Interval f : free) for (Interval p : productive) {
                long start = Math.max(f.start, p.start), end = Math.min(f.end, p.end);
                if (start < end && (best == null || start < best.start)) best = new Interval(start, end);
            }
            if (best != null) return best;
        }
        return free.get(0);
    }

    private static boolean containedInAny(Interval value, List<Interval> windows) {
        for (Interval w : windows) if (w.start <= value.start && value.end <= w.end) return true;
        return false;
    }

    private static void requireInDay(Interval value, Interval day) {
        if (value == null || value.start < day.start || value.end > day.end) throw new IllegalArgumentException("Interval outside target day");
    }

    private static boolean overlaps(Interval a, Interval b) {
        return a.start < b.end && b.start < a.end;
    }

    /** Importance contributes 12–60 points; urgency approaches 100 near the deadline. */
    public static double priorityScore(Task task, long referenceTimeMillis) {
        double urgency = 0;
        if (task.deadlineMillis != null) {
            double hoursLeft = Math.max(0, (task.deadlineMillis - referenceTimeMillis) / 3_600_000.0);
            urgency = 100.0 / (1.0 + hoursLeft / 6.0);
        }
        return urgency + task.importance * 12.0;
    }

    private static int minutes(Interval i) { return (int) ((i.end - i.start) / MINUTE); }
}
