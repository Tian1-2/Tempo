package com.tempo.app.ui;

import com.tempo.app.database.AppDatabaseHelper;
import com.tempo.app.database.CategoryDao;
import com.tempo.app.model.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Cached category list for drawing. Loaded once at startup and refreshed after every change on
 * the Categories page; colour lookups never touch the database.
 */
public final class Categories {
    private static volatile List<Category> cache = defaults();

    private Categories() { }

    private static List<Category> defaults() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("Study", Category.STUDY));
        list.add(new Category("Work", Category.WORK));
        list.add(new Category("Life", Category.LIFE));
        return Collections.unmodifiableList(list);
    }

    /** Re-reads the table. Small; safe on startup, otherwise call off the UI thread. */
    public static void reload(AppDatabaseHelper helper) {
        try {
            List<Category> loaded = new CategoryDao(helper).getAll();
            if (!loaded.isEmpty()) cache = Collections.unmodifiableList(loaded);
        } catch (RuntimeException ignored) {
            // Keep the previous list; drawing must never fail because of this.
        }
    }

    public static List<Category> all() { return cache; }

    public static Category find(String name) {
        if (name == null) return null;
        String key = name.trim();
        for (Category c : cache) if (c.getName().equalsIgnoreCase(key)) return c;
        return null;
    }

    public static int color(String name) {
        Category c = find(name);
        return c != null ? c.getColor() : Category.fallbackColor(name);
    }

    public static int soft(String name) { return Category.soft(color(name)); }

    /** Display name: the stored spelling, or the task's own text for unknown names. */
    public static String name(String value) {
        Category c = find(value);
        if (c != null) return c.getName();
        if (value == null || value.trim().isEmpty()) return "Uncategorized";
        String v = value.trim();
        return v.substring(0, 1).toUpperCase(Locale.ROOT) + v.substring(1);
    }

    /** First category, used as the default for new tasks. */
    public static String first() { return cache.isEmpty() ? "Study" : cache.get(0).getName(); }
}
