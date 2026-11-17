package com.tempo.app.insights;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/** Descriptive active timer time. Never an efficiency score or a scheduling preference. */
public final class TimeOfDayStats {
    public static final String[] LABELS = {"Overnight · 00–06", "Morning · 06–12", "Afternoon · 12–18", "Evening · 18–24"};
    public static final class Entry {
        public final long feedbackId, start, end;
        public Entry(long feedbackId, long start, long end) { this.feedbackId=feedbackId; this.start=start; this.end=end; }
    }
    public final long[] millis = new long[4];
    public int sessions;
    public static TimeOfDayStats calculate(List<Entry> entries, long now, TimeZone zone) {
        TimeOfDayStats result = new TimeOfDayStats();
        Calendar first = Calendar.getInstance(zone); first.setTimeInMillis(now);
        first.set(Calendar.HOUR_OF_DAY,0); first.set(Calendar.MINUTE,0); first.set(Calendar.SECOND,0); first.set(Calendar.MILLISECOND,0);
        first.add(Calendar.DAY_OF_MONTH,-6);
        long lower = first.getTimeInMillis();
        Set<Long> seen = new HashSet<>();
        for (Entry entry : entries) {
            long start = Math.max(lower,entry.start), end=Math.min(now,entry.end);
            if (entry.start<=0 || entry.end<=entry.start || start>=end) continue;
            seen.add(entry.feedbackId);
            // Slice by actual elapsed hours, respecting local clock/DST transitions.
            while (start<end) {
                Calendar c=Calendar.getInstance(zone); c.setTimeInMillis(start);
                int slot=c.get(Calendar.HOUR_OF_DAY)/6;
                c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0); c.add(Calendar.HOUR_OF_DAY,1);
                long next=Math.min(end,c.getTimeInMillis());
                if(next<=start) next=Math.min(end,start+3_600_000L);
                result.millis[slot]+=next-start; start=next;
            }
        }
        result.sessions=seen.size(); return result;
    }
}
