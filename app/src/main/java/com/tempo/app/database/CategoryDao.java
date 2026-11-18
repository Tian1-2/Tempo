package com.tempo.app.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.tempo.app.model.Category;

import java.util.ArrayList;
import java.util.List;

/** Task categories and their colours. Call off the UI thread except for the small startup read. */
public class CategoryDao {
    private final AppDatabaseHelper dbHelper;

    public CategoryDao(AppDatabaseHelper dbHelper) { this.dbHelper = dbHelper; }

    /** Creates the table with Study / Work / Life and adopts any other category already used by tasks. */
    public static void createTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS categories ("
                + "name TEXT PRIMARY KEY COLLATE NOCASE, "
                + "color INTEGER NOT NULL, "
                + "position INTEGER NOT NULL DEFAULT 0)");
        add(db, "Study", Category.STUDY, 0);
        add(db, "Work", Category.WORK, 1);
        add(db, "Life", Category.LIFE, 2);
        List<String> used = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT DISTINCT TRIM(category) FROM tasks WHERE category IS NOT NULL AND TRIM(category) <> ''", null)) {
            while (c.moveToNext()) used.add(c.getString(0));
        }
        int position = 3;
        for (String name : used) add(db, name, Category.fallbackColor(name), position++);
    }

    private static void add(SQLiteDatabase db, String name, int color, int position) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("color", color);
        values.put("position", position);
        db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<Category> getAll() {
        List<Category> list = new ArrayList<>();
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT name, color FROM categories ORDER BY position, name", null)) {
            while (c.moveToNext()) list.add(new Category(c.getString(0), c.getInt(1)));
        }
        return list;
    }

    /** Adds a category; returns false when one with the same name (ignoring case) exists. */
    public boolean insert(String name, int color) {
        String problem = Category.validateName(name);
        if (problem != null) throw new IllegalArgumentException(problem);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long next = 0;
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM categories", null)) {
            if (c.moveToFirst()) next = c.getLong(0);
        }
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("color", color);
        values.put("position", next);
        return db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public int setColor(String name, int color) {
        ContentValues values = new ContentValues();
        values.put("color", color);
        return dbHelper.getWritableDatabase().update("categories", values, "name = ?", new String[]{name});
    }

    /** Tasks that use this category, archived ones included (they can be restored and keep their colour in History). */
    public int countTasks(String name) {
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE category = ? COLLATE NOCASE", new String[]{name})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /**
     * Renames a category everywhere it is stored: the list, every task (archived ones too) and the
     * learning snapshots, so predictions keep their history. Returns false when another category
     * already has the new name. A change of case only ("club" → "Club") is allowed.
     */
    public boolean rename(String from, String to) {
        String problem = Category.validateName(to);
        if (problem != null) throw new IllegalArgumentException(problem);
        String name = to.trim();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM categories WHERE name = ? COLLATE NOCASE "
                    + "AND name <> ? COLLATE NOCASE", new String[]{name, from})) {
                if (c.moveToFirst() && c.getInt(0) > 0) return false;
            }
            ContentValues category = new ContentValues();
            category.put("name", name);
            if (db.update("categories", category, "name = ? COLLATE NOCASE", new String[]{from}) == 0) return false;
            ContentValues task = new ContentValues();
            task.put("category", name);
            db.update("tasks", task, "category = ? COLLATE NOCASE", new String[]{from});
            db.update("learning_snapshots", task, "category = ? COLLATE NOCASE", new String[]{from});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** Removes an unused category; returns false while tasks still use it or it is the last one. */
    public boolean delete(String name) {
        if (countTasks(name) > 0 || getAll().size() <= 1) return false;
        return dbHelper.getWritableDatabase().delete("categories", "name = ?", new String[]{name}) > 0;
    }
}
