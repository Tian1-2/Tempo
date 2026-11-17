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
import com.tempo.app.insights.InsightsIcon;
import com.tempo.app.repository.TaskRepository;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        learning=new LearningRepository(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.WHITE);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.WHITE);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(12),dp(20),dp(28));content.setClipToPadding(false);
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
        LinearLayout navigation=new LinearLayout(this);navigation.setOrientation(LinearLayout.HORIZONTAL);navigation.setGravity(Gravity.CENTER_VERTICAL);
        InsightsIcon back=new InsightsIcon(this,InsightsIcon.Kind.BACK,muted);
        back.setContentDescription("Back to insights");back.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);back.setFocusable(true);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(ice),null,shape(Color.WHITE)));
        back.setOnClickListener(v->{if(!saving)finish();});navigation.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        navigation.addView(text("Insights",14,muted,true));
        LinearLayout.LayoutParams navigationParams=new LinearLayout.LayoutParams(-1,-2);
        navigationParams.leftMargin=-dp(18);
        content.addView(navigation,navigationParams);gap(content,8);
        content.addView(text("Activity history",30,navy,true));gap(content,8);
        content.addView(text("Latest 200 records · all dates\nTap a record to correct its time or rating.",14,muted,false));gap(content,20);
    }
    private void load() {
        header();content.addView(text("Reading your records…",14,muted,false));
        learning.records(new TaskRepository.Callback<List<LearningStore.Record>>() {
            public void onSuccess(List<LearningStore.Record> rows) {
                header();if(rows.isEmpty())content.addView(text("No activity saved yet. Log an activity or save a focus session to begin.",15,muted,false));
                SimpleDateFormat date=new SimpleDateFormat("MMM d, yyyy · HH:mm",Locale.ENGLISH);
                for(LearningStore.Record row:rows) {
                    LinearLayout card=new LinearLayout(SessionHistoryActivity.this);card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(shape(ice));
                    card.addView(text(row.title,17,navy,true));gap(card,6);
                    card.addView(text((row.minutes==null?"Time not recorded":row.minutes+" min recorded")+" · "+("COMPLETED".equals(row.outcome)?"Task completed":"Partial progress"),14,navy,false));gap(card,6);
                    card.addView(text("Saved "+date.format(new Date(row.savedAt))+"\n"+sourceLabel(row.source)
                            +(row.difficulty>0?" · Difficulty "+row.difficulty+"/5":" · Unrated"),12,muted,false));
                    TextView edit=action("Edit record");edit.setOnClickListener(v->edit(row));card.addView(edit);
                    content.addView(card);gap(content,12);
                }
                if(restoreEdit!=null && restoreEdit.containsKey("editId")) {
                    for(LearningStore.Record row:rows) if(row.id==restoreEdit.getLong("editId") && row.revision==restoreEdit.getInt("editRevision")) {edit(row);break;}
                }
                restoreEdit=null;
            }
            public void onError(Exception e) {header();content.addView(text("Could not load your records.",15,muted,false));TextView retry=action("Try again");retry.setOnClickListener(v->load());content.addView(retry);}
        });
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
        form.addView(text("This corrects the saved record. Task status and the original prediction stay the same. Updated estimates apply when you next regenerate the schedule.",13,muted,false));
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
                public void onSuccess(Void ignored){saving=false;dialog.dismiss();Toast.makeText(SessionHistoryActivity.this,"Record updated",Toast.LENGTH_SHORT).show();load();}
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
