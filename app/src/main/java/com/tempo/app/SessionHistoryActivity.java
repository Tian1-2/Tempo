package com.tempo.app;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.tempo.app.learning.LearningRepository;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.home.DailyReview;
import com.tempo.app.model.Task;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.ui.Ui;
import com.tempo.app.repository.TaskRepository;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Corrections update the original record; task outcomes and frozen predictions remain unchanged. */
public final class SessionHistoryActivity extends AppCompatActivity {
    private LearningRepository learning;
    private LinearLayout content;
    private int navy,muted,blue,ice;
    private boolean saving;
    private Bundle restoreEdit;
    private LearningStore.Record editing;
    private EditText editingMinutes;
    private CheckBox editingMissing;
    private Spinner editingRating;
    private AlertDialog editDialog;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        restoreEdit=state;
        navy=getColor(R.color.insights_navy);muted=getColor(R.color.insights_muted);blue=getColor(R.color.insights_blue);ice=getColor(R.color.insights_ice);
        RepositoryProvider.init(this);
        learning=new LearningRepository(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(28));content.setClipToPadding(false);
        scroll.addView(content);setContentView(scroll);
        WindowCompat.getInsetsController(getWindow(),scroll).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(),scroll).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{
            Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout()|WindowInsetsCompat.Type.ime());
            v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
        });ViewCompat.requestApplyInsets(scroll);
        load();
    }
    private void header() {
        content.removeAllViews();
        content.addView(Ui.backRow(this, "Back", v -> { if (!saving) finish(); }));
        Ui.space(content, 6);
        content.addView(Ui.title(this, "History"));
        Ui.space(content, 4);
        content.addView(Ui.text(this, "Your latest 200 activities. Totals include all saved records.", 14, Ui.MUTED, false));
        Ui.space(content, 18);
    }

    private void load() {
        header();
        content.addView(Ui.text(this, "Reading your records…", 14, Ui.MUTED, false));
        learning.history(new TaskRepository.Callback<LearningStore.History>() {
            public void onSuccess(LearningStore.History history) { render(history); }
            public void onError(Exception e) {
                header();
                LinearLayout panel = Ui.panel(SessionHistoryActivity.this, Ui.WARM_SOFT);
                panel.addView(Ui.text(SessionHistoryActivity.this, "Could not load your records.", 15, Ui.INK, true));
                content.addView(panel);
                Ui.space(content, 12);
                TextView retry = Ui.secondary(SessionHistoryActivity.this, "Try again");
                retry.setOnClickListener(v -> load());
                content.addView(retry);
            }
        });
    }

    private void render(LearningStore.History history) {
        if (isFinishing()) return;
        header();
        List<LearningStore.Record> rows = history.rows;
        if (rows.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(20), dp(28), dp(20), dp(28));
            TextView title = Ui.text(this, "No activity yet", 17, Ui.INK, true);
            empty.addView(title);
            Ui.space(empty, 6);
            TextView body = Ui.text(this, "Finish a focus session or log an activity in Insights, and it will appear here.", 14, Ui.MUTED, false);
            body.setGravity(Gravity.CENTER);
            empty.addView(body);
            content.addView(empty);
        } else {
            summary(history);
            Ui.space(content, 22);
            SimpleDateFormat dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String today = dayKey.format(new Date());
            String yesterday = dayKey.format(new Date(System.currentTimeMillis() - 86_400_000L));
            SimpleDateFormat dayLabel = new SimpleDateFormat("EEE, MMM d", Locale.US);
            SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.US);
            String currentDay = null;
            LinearLayout dayCard = null;
            for (int i = 0; i < rows.size(); i++) {
                LearningStore.Record row = rows.get(i);
                String key = dayKey.format(new Date(row.savedAt));
                if (!key.equals(currentDay)) {
                    currentDay = key;
                    int dayMinutes = 0;
                    for (LearningStore.Record r : rows) {
                        if (r.minutes != null && key.equals(dayKey.format(new Date(r.savedAt)))) dayMinutes += r.minutes;
                    }
                    if (dayCard != null) Ui.space(content, 18);
                    LinearLayout label = new LinearLayout(this);
                    label.setGravity(Gravity.CENTER_VERTICAL);
                    String name = key.equals(today) ? "Today" : key.equals(yesterday) ? "Yesterday" : dayLabel.format(new Date(row.savedAt));
                    label.addView(Ui.eyebrow(this, name), new LinearLayout.LayoutParams(0, -2, 1));
                    label.addView(Ui.text(this, dayMinutes > 0 ? DailyReview.formatMinutes(dayMinutes) + (history.count > rows.size() && key.equals(dayKey.format(new Date(rows.get(rows.size()-1).savedAt))) ? " shown" : "") : "", 12, Ui.MUTED, true));
                    content.addView(label);
                    Ui.space(content, 8);
                    dayCard = Ui.card(this);
                    dayCard.setPadding(dp(16), dp(4), dp(16), dp(4));
                    content.addView(dayCard);
                } else {
                    dayCard.addView(Ui.divider(this));
                }
                dayCard.addView(recordRow(row, row.category, clock));
            }
        }
        Ui.space(content, 24);
        if (restoreEdit != null && restoreEdit.containsKey("editId")) {
            for (LearningStore.Record row : rows) if (row.id == restoreEdit.getLong("editId") && row.revision == restoreEdit.getInt("editRevision")) { edit(row); break; }
        }
        restoreEdit = null;
    }

    private void summary(LearningStore.History history) {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(8), dp(16), dp(8), dp(16));
        card.addView(stat(String.valueOf(history.count), history.count == 1 ? "record" : "records"), new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(stat(DailyReview.formatMinutes(history.weekMinutes), "last 7 days"), new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(stat(String.valueOf(history.finished), history.finished == 1 ? "task finished" : "tasks finished"), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(card);
    }

    private View stat(String value, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(Ui.text(this, value, 18, Ui.INK, true));
        Ui.space(box, 2);
        box.addView(Ui.text(this, label, 12, Ui.MUTED, false));
        return box;
    }

    private View recordRow(LearningStore.Record row, String category, SimpleDateFormat clock) {
        boolean completed = "COMPLETED".equals(row.outcome);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(14), 0, dp(14));
        line.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.MINT_SOFT), null, Ui.round(Color.WHITE, dp(12))));
        line.setOnClickListener(v -> edit(row));

        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(Ui.categoryColor(category));
        dot.setBackground(dotShape);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.rightMargin = dp(12);
        line.addView(dot, dotParams);

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, row.title, 15, Ui.INK, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        middle.addView(title);
        Ui.space(middle, 3);
        String meta = clock.format(new Date(row.savedAt)) + " · " + sourceLabel(row.source)
                + (row.difficulty > 0 ? " · Difficulty " + row.difficulty + "/5" : "");
        middle.addView(Ui.text(this, meta, 12, Ui.MUTED, false));
        line.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setPadding(dp(10), 0, 0, 0);
        right.addView(Ui.text(this, row.minutes == null ? "No time" : DailyReview.formatMinutes(row.minutes), 15,
                row.minutes == null ? Ui.MUTED : Ui.INK, true));
        Ui.space(right, 4);
        right.addView(Ui.chip(this, completed ? "Done" : "Partial", completed ? Ui.MINT_SOFT : Ui.WARM_SOFT,
                completed ? Ui.GREEN : Ui.WARM));
        line.addView(right);
        return line;
    }

    private String sourceLabel(String source) {
        switch(source) {case "TIMER":return "Focus timer";case "MANUAL":return "Manual entry";case "STATUS":return "Status only";case "BACKFILLED":return "Time added later";case "CORRECTED":case "ADJUSTED":return "Adjusted duration";default:return "Earlier record";}
    }
    private void edit(LearningStore.Record row) {
        if(saving)return;
        if(getSharedPreferences("tempo_focus_timer",MODE_PRIVATE).contains("taskId")) {
            Toast.makeText(this,"Finish the active timer before correcting activity records.",Toast.LENGTH_LONG).show();return;
        }
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(16),dp(22),dp(12));
        form.addView(text(row.title,18,navy,true));gap(form,12);
        form.addView(text("Minutes for this activity",14,navy,true));
        EditText minutes=new EditText(this);minutes.setInputType(InputType.TYPE_CLASS_NUMBER);minutes.setText(row.minutes==null?"":row.minutes.toString());minutes.setTextColor(navy);minutes.setMinHeight(dp(52));form.addView(minutes);
        CheckBox missing=new CheckBox(this);missing.setText("Time not recorded");missing.setTextColor(navy);missing.setMinHeight(dp(48));missing.setChecked(row.minutes==null);minutes.setEnabled(!missing.isChecked());
        missing.setOnCheckedChangeListener((v,checked)->minutes.setEnabled(!checked));form.addView(missing);gap(form,12);
        form.addView(text("Difficulty · optional",14,navy,true));
        Spinner rating=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Not rated","1 · Very easy","2 · Easy","3 · Moderate","4 · Hard","5 · Very hard"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);rating.setAdapter(adapter);rating.setSelection(Math.max(0,Math.min(5,row.difficulty)));
        form.addView(rating,new LinearLayout.LayoutParams(-1,dp(52)));gap(form,12);
        form.addView(text("This corrects the saved record. Task status and the original prediction stay the same. Tempo will update your schedule after saving.",13,muted,false));
        TextView error=text("",13,blue,false);form.addView(error);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Correct activity").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save correction",null).create();
        editing=row;editingMinutes=minutes;editingMissing=missing;editingRating=rating;editDialog=dialog;
        if(restoreEdit!=null && restoreEdit.getLong("editId",-1)==row.id) {
            minutes.setText(restoreEdit.getString("editMinutes",""));missing.setChecked(restoreEdit.getBoolean("editMissing",false));rating.setSelection(restoreEdit.getInt("editRating",0));
        }
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Integer value;
            try {value=missing.isChecked()?null:Integer.valueOf(minutes.getText().toString().trim());if(value!=null&&(value<1||value>1440))throw new NumberFormatException();}
            catch(NumberFormatException e){error.setText("Enter 1–1440 minutes, or choose Time not recorded.");return;}
            saving=true;dialog.setCancelable(false);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            learning.correct(row.id,row.revision,value,rating.getSelectedItemPosition(),new TaskRepository.Callback<Void>() {
                public void onSuccess(Void ignored){saving=false;dialog.dismiss();load();
                    com.tempo.app.scheduler.AutoScheduler.replan(SessionHistoryActivity.this, new com.tempo.app.scheduler.AutoScheduler.Listener() {
                        public void onScheduled(com.tempo.app.scheduler.ScheduleGenerator.Result result) {
                            Toast.makeText(SessionHistoryActivity.this, "Record updated", Toast.LENGTH_SHORT).show();
                        }
                        public void onFailed(String reason) {
                            Toast.makeText(SessionHistoryActivity.this, "Record saved. Schedule unchanged. " + reason, Toast.LENGTH_LONG).show();
                        }
                    });}
                public void onError(Exception e){saving=false;dialog.setCancelable(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);error.setText(e.getMessage());}
            });
        }));dialog.show();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(!saving && editing!=null && editDialog!=null && editDialog.isShowing()) {
            out.putLong("editId",editing.id);out.putInt("editRevision",editing.revision);out.putString("editMinutes",editingMinutes.getText().toString());
            out.putBoolean("editMissing",editingMissing.isChecked());out.putInt("editRating",editingRating.getSelectedItemPosition());
        }
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy(){if(learning!=null)learning.close();super.onDestroy();}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setLineSpacing(dp(3),1);return v;}
    private TextView action(String s){TextView v=text(s,14,blue,true);v.setMinHeight(dp(48));v.setGravity(Gravity.CENTER);v.setPadding(dp(8),dp(8),dp(8),dp(8));v.setFocusable(true);return v;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private void gap(LinearLayout l,int height){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
