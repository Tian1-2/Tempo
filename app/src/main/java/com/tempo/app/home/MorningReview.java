package com.tempo.app.home;

/**
 * When Home should open on yesterday's review: the first time Home is shown on a day between 07:00
 * and 09:00, whether the app is started fresh or brought back from the background. Once it has
 * been shown that day it does not pop up again; the "Yesterday" button still opens it.
 */
public final class MorningReview {
    public static final int START_HOUR = 7;
    public static final int END_HOUR = 9; // exclusive: 08:59 still counts, 09:00 does not

    private MorningReview() {}

    /**
     * @param hour     current hour of day, 0–23
     * @param todayKey today's date key (e.g. "2026-11-18")
     * @param shownKey date key of the last day the review was shown, or null/empty if never
     */
    public static boolean due(int hour, String todayKey, String shownKey) {
        if (hour < START_HOUR || hour >= END_HOUR) return false;
        return todayKey != null && !todayKey.equals(shownKey);
    }
}
