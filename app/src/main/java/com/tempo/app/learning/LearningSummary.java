package com.tempo.app.learning;

import java.util.ArrayList;
import java.util.List;
import com.tempo.app.insights.TimeOfDayStats;

/** Presentation values only; no writes or claims of improvement. */
public final class LearningSummary {
    public int historyTasks, evaluatedTasks, deadlineTasks, onTimeTasks;
    public double estimateMae, predictionMae, predictionBias, difficultyDelta;
    public int difficultyComparisons;
    public int untimedRecords;
    public TimeOfDayStats timeOfDay = new TimeOfDayStats();
    public final List<Preview> previews = new ArrayList<>();
    public static final class Preview {
        public final String title;
        public final int estimate, prediction, samples;
        public final boolean fixed;
        public String basis = "", category = "";
        public int remaining;
        public boolean scopeChanged;
        public Preview(String title, int estimate, int prediction, int samples) {
            this(title, estimate, prediction, samples, false);
        }
        public Preview(String title, int estimate, int prediction, int samples, boolean fixed) {
            this.title = title; this.estimate = estimate; this.prediction = prediction; this.samples = samples;
            this.fixed = fixed;
        }
    }
}
