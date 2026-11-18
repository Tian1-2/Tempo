package com.tempo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tempo.app.model.Category;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.ui.Categories;
import com.tempo.app.ui.CategoryDialogs;
import com.tempo.app.ui.Ui;

/** Lists task categories; add new ones, change colours, remove unused ones. */
public class CategoriesActivity extends Activity {
    private LinearLayout body;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        Ui.window(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(40));
        scroll.addView(body);
        setContentView(scroll);
        Ui.fitSystemBars(this);
        render();
    }

    private void render() {
        body.removeAllViews();
        body.addView(Ui.backRow(this, "Back", v -> finish()), new LinearLayout.LayoutParams(-1, dp(44)));
        Ui.space(body, 6);
        body.addView(Ui.title(this, "Categories"));
        Ui.space(body, 4);
        body.addView(Ui.text(this, "Each task belongs to one category. Its colour marks the task on Home, the schedule and History.",
                14, Ui.MUTED, false));
        Ui.space(body, 18);
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(16), dp(4), dp(8), dp(4));
        boolean firstRow = true;
        for (Category category : Categories.all()) {
            if (!firstRow) card.addView(Ui.divider(this));
            firstRow = false;
            card.addView(row(category));
        }
        body.addView(card);
        Ui.space(body, 22);
        TextView add = Ui.primary(this, "Add category");
        add.setOnClickListener(v -> CategoryDialogs.create(this, name -> {
            Toast.makeText(this, "Added " + name, Toast.LENGTH_SHORT).show();
            render();
        }));
        body.addView(add);
    }

    private LinearLayout row(Category category) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        View dot = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(category.getColor());
        dot.setBackground(shape);
        row.addView(dot, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView name = Ui.text(this, category.getName(), 15, Ui.INK, true);
        name.setPadding(dp(12), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView colour = action("Colour", Ui.GREEN);
        colour.setOnClickListener(v -> CategoryDialogs.recolor(this, category, this::render));
        row.addView(colour);
        TextView remove = action("Remove", Ui.RED);
        remove.setOnClickListener(v -> confirmRemove(category));
        row.addView(remove);
        return row;
    }

    private void confirmRemove(Category category) {
        new Thread(() -> {
            int used = RepositoryProvider.getCategoryDao().countTasks(category.getName());
            int total = Categories.all().size();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (used > 0) {
                    new AlertDialog.Builder(this).setTitle(category.getName() + " is in use")
                            .setMessage(used + (used == 1 ? " task uses" : " tasks use") + " this category (archived tasks count, so History keeps its colour). Move "
                                    + (used == 1 ? "it" : "them") + " to another category first.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                if (total <= 1) {
                    Toast.makeText(this, "Keep at least one category.", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Remove " + category.getName() + "?")
                        .setMessage("Past records keep their name and colour.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (d, w) -> new Thread(() -> {
                            RepositoryProvider.getCategoryDao().delete(category.getName());
                            Categories.reload(RepositoryProvider.getDatabaseHelper());
                            runOnUiThread(() -> { if (!isFinishing()) render(); });
                        }, "category-remove").start()).show();
            });
        }, "category-check").start();
    }

    private TextView action(String label, int color) {
        TextView view = Ui.text(this, label, 13, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setMinHeight(dp(44));
        return view;
    }

    private int dp(int value) { return Ui.dp(this, value); }
}
