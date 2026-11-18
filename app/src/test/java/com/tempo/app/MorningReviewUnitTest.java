package com.tempo.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tempo.app.home.MorningReview;

import org.junit.Test;

public class MorningReviewUnitTest {
    private static final String TODAY = "2026-11-18";
    private static final String YESTERDAY = "2026-11-17";

    @Test public void showsBetweenSevenAndNine() {
        assertTrue(MorningReview.due(7, TODAY, ""));
        assertTrue(MorningReview.due(8, TODAY, YESTERDAY)); // seen yesterday, not yet today
        assertTrue(MorningReview.due(8, TODAY, null));
    }

    @Test public void notOutsideTheMorningWindow() {
        assertFalse(MorningReview.due(6, TODAY, ""));
        assertFalse(MorningReview.due(9, TODAY, ""));
        assertFalse(MorningReview.due(22, TODAY, ""));
    }

    @Test public void onlyOncePerDay() {
        assertFalse(MorningReview.due(8, TODAY, TODAY));
    }
}
