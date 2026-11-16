package com.tempo.app.scheduler;

import com.tempo.app.model.ScheduleItem;
import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.scheduler.ScheduleGenerator.Interval;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A 的默认排程约束，在固定日程表和 D 的学习结果出来之前使用：
 * 三餐各占 {@link #MEAL_MINUTES} 分钟；从偏好开始时间起 {@link #PRODUCTIVE_MINUTES} 分钟算高效时段。
 * 与手动锁定时段重叠的用餐块会被跳过，避免排程器因"锁定时段与阻塞时间重叠"而报错。
 * 必须在后台线程调用 {@link #getForDate(String)}。
 */
public final class DefaultScheduleConstraints implements ScheduleConstraintsProvider {
    public static final int MEAL_MINUTES = 30;
    public static final int PRODUCTIVE_MINUTES = 180;
    private static final long MINUTE = 60_000L;

    @Override public DayConstraints getForDate(String date) {
        UserProfile profile = RepositoryProvider.getUserProfileRepository().getProfileSync();
        List<ScheduleItem> existing = RepositoryProvider.getScheduleRepository().getScheduleByDateSync(date);
        long dayStart = dayStartMillis(date);
        return build(dayStart, dayStart + 24 * 60 * MINUTE, profile, existing);
    }

    /** 纯计算，便于单元测试。时刻按 dayStart + 小时分钟 计算（香港无夏令时）。 */
    public static DayConstraints build(long dayStart, long dayEnd, UserProfile profile, List<ScheduleItem> existing) {
        List<Interval> busy = new ArrayList<>();
        List<Interval> productive = new ArrayList<>();
        if (profile == null) return new DayConstraints(busy, productive);
        List<Interval> locked = new ArrayList<>();
        if (existing != null) for (ScheduleItem item : existing) {
            if (item != null && item.isManuallyEdited() && "PLANNED".equals(item.getStatus())
                    && item.getStartTime() < item.getEndTime()) {
                locked.add(new Interval(item.getStartTime(), item.getEndTime()));
            }
        }
        for (String meal : new String[]{profile.getBreakfastTime(), profile.getLunchTime(), profile.getDinnerTime()}) {
            Interval block = window(dayStart, dayEnd, meal, MEAL_MINUTES);
            if (block != null && !overlapsAny(block, locked)) busy.add(block);
        }
        Interval focus = window(dayStart, dayEnd, profile.getPreferredStartTime(), PRODUCTIVE_MINUTES);
        if (focus != null) productive.add(focus);
        return new DayConstraints(busy, productive);
    }

    private static Interval window(long dayStart, long dayEnd, String hhmm, int minutes) {
        Integer offset = minutesOfDay(hhmm);
        if (offset == null) return null;
        long start = dayStart + offset * MINUTE;
        long end = Math.min(start + minutes * MINUTE, dayEnd);
        return start < end ? new Interval(start, end) : null;
    }

    static Integer minutesOfDay(String hhmm) {
        if (hhmm == null) return null;
        String value = hhmm.trim();
        if (!value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return null;
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3, 5));
    }

    private static boolean overlapsAny(Interval block, List<Interval> others) {
        for (Interval other : others) if (block.start < other.end && other.start < block.end) return true;
        return false;
    }

    private static long dayStartMillis(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try {
            Date parsed = format.parse(date);
            if (parsed == null) throw new IllegalArgumentException("Invalid date: " + date);
            return parsed.getTime();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Date must be yyyy-MM-dd", e);
        }
    }
}
