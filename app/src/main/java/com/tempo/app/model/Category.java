package com.tempo.app.model;

/** A task label with its colour, e.g. Study (green). Names are unique, ignoring case. */
public class Category {
    /** Colours offered when creating a category: green, blue, orange, purple, red, teal, pink, slate. */
    public static final int[] PALETTE = {
            0xFF3F8F6B, 0xFF4C6FB5, 0xFFD08A3E, 0xFF8A5CC2,
            0xFFC0504D, 0xFF2E8C9A, 0xFFC2588F, 0xFF66737F};
    public static final int STUDY = PALETTE[0], WORK = PALETTE[1], LIFE = PALETTE[2];
    public static final int MAX_NAME_LENGTH = 20;

    private final String name;
    private final int color;

    public Category(String name, int color) { this.name = name; this.color = color; }

    public String getName() { return name; }
    public int getColor() { return color; }

    /** Light tint of a colour (mixed with 82% white) for backgrounds. */
    public static int soft(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = r + (255 - r) * 82 / 100; g = g + (255 - g) * 82 / 100; b = b + (255 - b) * 82 / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Stable palette colour for a name that has no stored colour (e.g. legacy data). */
    public static int fallbackColor(String name) {
        if (name == null || name.trim().isEmpty()) return STUDY;
        String key = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.equals("study") || key.equals("学习")) return STUDY;
        if (key.equals("work") || key.equals("工作")) return WORK;
        if (key.equals("life") || key.equals("生活")) return LIFE;
        return PALETTE[Math.floorMod(key.hashCode(), PALETTE.length)];
    }

    /** Null when valid, otherwise a message for the user. */
    public static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) return "Give the category a name.";
        if (name.trim().length() > MAX_NAME_LENGTH) return "Keep the name under " + MAX_NAME_LENGTH + " characters.";
        return null;
    }

    public static String hex(int color) { return String.format(java.util.Locale.US, "#%06X", color & 0xFFFFFF); }
}
