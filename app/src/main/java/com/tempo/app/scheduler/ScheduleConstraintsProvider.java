package com.tempo.app.scheduler;

import com.tempo.app.scheduler.ScheduleGenerator.Interval;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Data contract for A/B/D: return absolute, minute-aligned intervals for one local date. */
public interface ScheduleConstraintsProvider {
    DayConstraints getForDate(String date);

    final class DayConstraints {
        public final List<Interval> fixedBusyIntervals;
        public final List<Interval> productiveWindows;

        public DayConstraints(List<Interval> fixedBusyIntervals, List<Interval> productiveWindows) {
            if (fixedBusyIntervals == null || productiveWindows == null) {
                throw new IllegalArgumentException("Schedule constraints cannot be null");
            }
            this.fixedBusyIntervals = Collections.unmodifiableList(new ArrayList<>(fixedBusyIntervals));
            this.productiveWindows = Collections.unmodifiableList(new ArrayList<>(productiveWindows));
        }
    }
}
