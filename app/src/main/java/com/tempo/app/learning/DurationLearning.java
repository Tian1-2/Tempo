package com.tempo.app.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Java duration calibration, independent of Android and persistence. */
public final class DurationLearning {
    public static final class Sample {
        public final int taskId, estimate, prediction;
        public final long actual, completedAt;
        public final String category, type;
        public final boolean evaluable;
        public Sample(int taskId, String category, String type, int estimate, int prediction,
                      long actual, long completedAt, boolean evaluable) {
            this.taskId = taskId; this.category = category; this.type = type;
            this.estimate = estimate; this.prediction = prediction; this.actual = actual;
            this.completedAt = completedAt; this.evaluable = evaluable;
        }
    }

    public static final class Prediction {
        public final int minutes, sampleCount;
        public final String scope;
        public Prediction(int minutes, int sampleCount, String scope) {
            this.minutes = minutes; this.sampleCount = sampleCount; this.scope = scope;
        }
    }

    private DurationLearning() { }

    public static Prediction predict(int taskId, String category, String type, int estimate,
                                     List<Sample> history, long now) {
        if (estimate <= 0) return new Prediction(estimate, 0, "Your estimate");
        List<Sample> sameType = new ArrayList<>(), sameCategory = new ArrayList<>();
        for (Sample sample : history) {
            if (sample.taskId == taskId || sample.estimate <= 0 || sample.actual <= 0
                    || sample.completedAt <= 0 || sample.completedAt > now) continue;
            if (!categoryKey(category).equals(categoryKey(sample.category))) continue;
            sameCategory.add(sample);
            if (!key(type).isEmpty() && key(type).equals(key(sample.type))) sameType.add(sample);
        }
        // Use the narrower type only when at least three completed tasks are available.
        List<Sample> selected = sameType.size() >= 3 ? sameType : sameCategory;
        if (selected.isEmpty()) return new Prediction(estimate, 0, "Your estimate");
        double weightSum = 0, ratioSum = 0;
        for (Sample sample : selected) {
            double ageDays = (now - sample.completedAt) / 86_400_000.0;
            double weight = Math.pow(.5, ageDays / 30.0);
            // Keep raw observations; cap only their influence on this estimate.
            double ratio = Math.max(.5, Math.min(3, (double) sample.actual / sample.estimate));
            weightSum += weight;
            ratioSum += weight * ratio;
        }
        // Three neutral pseudo-observations shrink sparse or old data towards the user's estimate.
        double factor = (3 + ratioSum) / (3 + weightSum);
        int minutes = (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(estimate * factor)));
        return new Prediction(minutes, selected.size(), sameType.size() >= 3 ? "Same task type" : "Same category");
    }

    /** An overrun is not completion: reserve a provisional positive block until the user finishes. */
    public static int remaining(int totalPrediction, long actual, Integer userRemaining, int focusMinutes) {
        if (userRemaining != null) return userRemaining > 0 ? userRemaining : provisional(focusMinutes);
        long remainder = (long) totalPrediction - Math.max(0, actual);
        if (remainder > 0) return (int) Math.min(Integer.MAX_VALUE, remainder);
        return provisional(focusMinutes);
    }

    /** Apply once per commit. Retain signed overrun internally so later corrections can restore it exactly. */
    public static Integer advanceRemaining(Integer previous, int newlyRecorded, Integer explicit) {
        if (explicit != null) return explicit;
        return previous == null ? null : (int) Math.max(Integer.MIN_VALUE, (long) previous - Math.max(0, newlyRecorded));
    }

    public static int provisional(int focusMinutes) { return Math.max(5, Math.min(30, focusMinutes)); }

    public static String categoryKey(String value) {
        String result = key(value);
        if (result.equals("学习")) return "study";
        if (result.equals("工作")) return "work";
        if (result.equals("生活")) return "life";
        return result;
    }
    private static String key(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
