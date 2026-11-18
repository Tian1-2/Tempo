package com.tempo.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.tempo.app.model.Category;
import com.tempo.app.repository.RepositoryProvider;

/** Shared dialogs for creating a category and choosing its colour. */
public final class CategoryDialogs {
    public interface Done { void onDone(String name); }

    private CategoryDialogs() { }

    /** Asks for a name and colour, saves it, refreshes {@link Categories}, then calls back on the UI thread. */
    public static void create(Activity activity, Done done) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 20);
        form.setPadding(pad, Ui.dp(activity, 8), pad, 0);
        EditText name = new EditText(activity);
        name.setHint("Name, e.g. Club or Part-time job");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Category.MAX_NAME_LENGTH)});
        form.addView(name);
        final int[] chosen = {unusedColor()};
        form.addView(Ui.text(activity, "Colour", 12, Ui.MUTED, true));
        form.addView(swatches(activity, chosen));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("New category").setView(form)
                .setNegativeButton("Cancel", null).setPositiveButton("Add", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            String problem = Category.validateName(value);
            if (problem == null && Categories.find(value) != null) problem = "\"" + value + "\" already exists.";
            if (problem != null) { Toast.makeText(activity, problem, Toast.LENGTH_SHORT).show(); return; }
            dialog.dismiss();
            new Thread(() -> {
                boolean ok;
                try { ok = RepositoryProvider.getCategoryDao().insert(value, chosen[0]); }
                catch (RuntimeException e) { ok = false; }
                Categories.reload(RepositoryProvider.getDatabaseHelper());
                final boolean saved = ok;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (!saved) Toast.makeText(activity, "Could not add the category.", Toast.LENGTH_SHORT).show();
                    else done.onDone(value);
                });
            }, "category-add").start();
        }));
        dialog.show();
    }

    /** Asks for a new colour for an existing category. */
    public static void recolor(Activity activity, Category category, Runnable after) {
        final int[] chosen = {category.getColor()};
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 20);
        box.setPadding(pad, Ui.dp(activity, 8), pad, 0);
        box.addView(swatches(activity, chosen));
        new AlertDialog.Builder(activity).setTitle("Colour for " + category.getName()).setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> new Thread(() -> {
                    RepositoryProvider.getCategoryDao().setColor(category.getName(), chosen[0]);
                    Categories.reload(RepositoryProvider.getDatabaseHelper());
                    activity.runOnUiThread(after);
                }, "category-color").start()).show();
    }

    /** Two rows of round colour swatches; the selected one gets a dark ring. */
    static View swatches(Activity activity, int[] chosen) {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        int size = Ui.dp(activity, 40), gap = Ui.dp(activity, 10);
        View[] views = new View[Category.PALETTE.length];
        LinearLayout row = null;
        for (int i = 0; i < Category.PALETTE.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, gap, 0, 0);
                grid.addView(row);
            }
            final int color = Category.PALETTE[i];
            View swatch = new View(activity);
            swatch.setContentDescription("Colour " + (i + 1));
            views[i] = swatch;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = gap;
            row.addView(swatch, lp);
            swatch.setOnClickListener(v -> { chosen[0] = color; paint(activity, views, chosen[0]); });
        }
        paint(activity, views, chosen[0]);
        return grid;
    }

    private static void paint(Activity activity, View[] views, int selected) {
        for (int i = 0; i < views.length; i++) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Category.PALETTE[i]);
            if (Category.PALETTE[i] == selected) dot.setStroke(Ui.dp(activity, 3), Ui.INK);
            views[i].setBackground(dot);
        }
    }

    private static int unusedColor() {
        for (int color : Category.PALETTE) {
            boolean used = false;
            for (Category c : Categories.all()) if (c.getColor() == color) { used = true; break; }
            if (!used) return color;
        }
        return Category.PALETTE[Categories.all().size() % Category.PALETTE.length];
    }
}
