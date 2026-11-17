package com.tempo.app.learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wall-clock intervals for active timer segments only; pause gaps are never counted. */
public final class SessionTiming {
    public static final class Segment {
        public final long start, end;
        public Segment(long start, long end) { this.start = start; this.end = end; }
    }
    private SessionTiming() { }
    public static boolean clocksAgree(long wallStart, long elapsedStart, long wallEnd, long elapsedEnd) {
        return wallStart > 0 && elapsedStart >= 0 && elapsedEnd >= elapsedStart && wallEnd >= wallStart
                && Math.abs((wallEnd - wallStart) - (elapsedEnd - elapsedStart)) <= 2000;
    }
    public static List<Segment> decode(String value) {
        List<Segment> result = new ArrayList<>();
        if (value == null || value.isEmpty()) return result;
        try {
            long last = 0;
            for (String part : value.split(";")) {
                String[] pair = part.split(",");
                if (pair.length != 2) return Collections.emptyList();
                long start = Long.parseLong(pair[0]), end = Long.parseLong(pair[1]);
                if (start <= 0 || end <= start || start < last) return Collections.emptyList();
                result.add(new Segment(start, end)); last = end;
            }
        } catch (NumberFormatException error) { return Collections.emptyList(); }
        return result;
    }
    public static long totalMillis(List<Segment> segments) {
        long result = 0;
        for (Segment s : segments) result = Math.addExact(result, s.end - s.start);
        return result;
    }
    public static boolean matchesRecordedMinutes(List<Segment> segments, Integer minutes, long now) {
        if (segments.isEmpty() || minutes == null || minutes <= 0) return false;
        for (Segment s : segments) if (s.end > now) return false;
        return (totalMillis(segments) + 59_999) / 60_000 == minutes;
    }
}
