package com.tempo.app.learning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Optional, explicit labels. Titles are never used to guess similarity. */
public final class TaskTypes {
    private TaskTypes() { }
    public static List<String> options(String category, String existing) {
        List<String> result = new ArrayList<>();
        result.add("No type · optional");
        switch (DurationLearning.categoryKey(category)) {
            case "study": result.addAll(Arrays.asList("Assignment", "Revision", "Reading", "Practice")); break;
            case "work": result.addAll(Arrays.asList("Report", "Meeting preparation", "Project", "Administration")); break;
            case "life": result.addAll(Arrays.asList("Exercise", "Errands", "Personal project", "Leisure")); break;
            default: break;
        }
        if (existing != null && !existing.trim().isEmpty() && !result.contains(existing.trim())) result.add(existing.trim());
        return result;
    }
    public static String categoryLabel(String category) {
        String key = DurationLearning.categoryKey(category);
        return key.isEmpty() ? "Uncategorized" : Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
