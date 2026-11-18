package com.tempo.app.planning;

import com.tempo.app.model.ScheduleItem;
import java.util.List;

/** Page-side validation; cancelled history must not reserve time or manual capacity. */
public final class ScheduleEditRules {
    private ScheduleEditRules() { }

    private static boolean isOtherActive(ScheduleItem other, ScheduleItem edited) {
        return !"CANCELLED".equals(other.getStatus()) && other != edited
                && !(edited.getScheduleId() > 0 && other.getScheduleId() == edited.getScheduleId());
    }

    public static boolean overlaps(List<ScheduleItem> day, ScheduleItem edited, long start, long end) {
        for (ScheduleItem other : day) {
            if (isOtherActive(other, edited) && start < other.getEndTime() && other.getStartTime() < end) return true;
        }
        return false;
    }

    public static int futureMinutes(long start, long end, long now) {
        long boundary = Math.floorDiv(now + 59_999L, 60_000L) * 60_000L;
        return (int) Math.max(0L, (end - Math.max(start, boundary)) / 60_000L);
    }

    /** Count the proposed slot and all other future manual reservations for this task on every date. */
    public static int lockedMinutes(List<ScheduleItem> taskSlots, ScheduleItem edited, long start, long end, long now) {
        int total = futureMinutes(start, end, now);
        for (ScheduleItem other : taskSlots) {
            if (isOtherActive(other, edited) && "PLANNED".equals(other.getStatus())
                    && other.getTaskId() == edited.getTaskId() && other.isManuallyEdited())
                total += futureMinutes(other.getStartTime(), other.getEndTime(), now);
        }
        return total;
    }

    public static int lockedMinutes(List<ScheduleItem> day, ScheduleItem edited, long start, long end) {
        return lockedMinutes(day, edited, start, end, Long.MIN_VALUE);
    }
}
