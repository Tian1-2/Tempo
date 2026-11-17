package com.tempo.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Presentation-only time axis. Expands short spans without changing saved times. */
public final class TimelineScale {
    public static final class Span {
        public final int start, end;
        public final float minimumHeight;
        public Span(int start, int end, float minimumHeight) {
            if (start < 0 || end > 1440 || end <= start || !Float.isFinite(minimumHeight) || minimumHeight < 0)
                throw new IllegalArgumentException("Invalid timeline span");
            this.start = start; this.end = end; this.minimumHeight = minimumHeight;
        }
    }

    private final List<Integer> minutes;
    private final float[] positions;

    public TimelineScale(float pixelsPerMinute, List<Span> spans) {
        if (!Float.isFinite(pixelsPerMinute) || pixelsPerMinute <= 0 || spans == null)
            throw new IllegalArgumentException("Invalid timeline scale");
        TreeSet<Integer> boundaries = new TreeSet<>();
        boundaries.add(0); boundaries.add(1440);
        for (Span span : spans) { boundaries.add(span.start); boundaries.add(span.end); }
        minutes = new ArrayList<>(boundaries);
        positions = new float[minutes.size()];
        for (int i = 1; i < minutes.size(); i++) {
            int end = minutes.get(i);
            positions[i] = positions[i - 1] + (end - minutes.get(i - 1)) * pixelsPerMinute;
            for (Span span : spans) if (span.end == end) {
                int startIndex = java.util.Collections.binarySearch(minutes, span.start);
                positions[i] = Math.max(positions[i], positions[startIndex] + span.minimumHeight);
            }
        }
    }

    /** Shared coordinates for cards, rest labels, hour rules and the current-time marker. */
    public float y(int minute) {
        int clamped = Math.max(0, Math.min(1440, minute));
        int index = java.util.Collections.binarySearch(minutes, clamped);
        if (index >= 0) return positions[index];
        int right = -index - 1, left = right - 1;
        float fraction = (clamped - minutes.get(left)) / (float) (minutes.get(right) - minutes.get(left));
        return positions[left] + fraction * (positions[right] - positions[left]);
    }
}
