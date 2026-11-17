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

    public static int lockedMinutes(List<ScheduleItem> day, ScheduleItem edited, long start, long end) {
        long total = (end - start) / 60_000L;
        for (ScheduleItem other : day) {
            if (isOtherActive(other, edited) && other.getTaskId() == edited.getTaskId() && other.isManuallyEdited()) {
                total += (other.getEndTime() - other.getStartTime()) / 60_000L;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
}
