package com.tempo.app;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.tempo.app.insights.InsightsIcon;
import com.tempo.app.learning.LearningRepository;
import com.tempo.app.learning.LearningStore;
import com.tempo.app.repository.TaskRepository;
import com.tempo.app.scheduler.AutoScheduler;
import com.tempo.app.scheduler.ScheduleGenerator;

/** Explicit session outcome; the timer is paused while the user reviews this form. */
public final class TaskFeedbackActivity extends AppCompatActivity {
    private static final int PRIMARY = 0, SECONDARY = 1, TERTIARY = 2;
    private int NAVY, MUTED, BLUE;
    private int taskId;
    private String token;
    private long previousMinutes;
    private LearningRepository learning;
    private EditText minutes, remaining;
    private RadioButton complete;
    private Spinner rating;
    private TextView total, save, discard, cancel, error;
    private boolean saving;
    private boolean manual, expanded;
    private CheckBox noTime;
    private LinearLayout optionalDetails;
    private LinearLayout remainingGroup;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        NAVY = getColor(R.color.insights_navy);
        MUTED = getColor(R.color.insights_muted);
        BLUE = getColor(R.color.insights_blue);
        taskId = getIntent().getIntExtra("taskId", -1);
        token = getIntent().getStringExtra("sessionToken");
        previousMinutes = getIntent().getLongExtra("previousMinutes", 0);
        manual=getIntent().getBooleanExtra("manual",false);
        if (taskId <= 0 || token == null) { finish(); return; }
        if(manual && getSharedPreferences("tempo_focus_timer",MODE_PRIVATE).contains("taskId")) {
            Toast.makeText(this,"Finish the active timer before logging another activity.",Toast.LENGTH_LONG).show();finish();return;
        }
        learning = new LearningRepository(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (!saving) finish(); }
        });
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? Color.TRANSPARENT : NAVY);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(getColor(R.color.insights_white));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(12), dp(20), dp(28)); page.setClipToPadding(false); scroll.addView(page); setContentView(scroll);
        WindowCompat.getInsetsController(getWindow(), scroll).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), scroll).setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v,insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(safe.left,safe.top,safe.right,safe.bottom); return insets;
        });
        ViewCompat.requestApplyInsets(scroll);
        InsightsIcon back = new InsightsIcon(this, InsightsIcon.Kind.BACK, MUTED);
        back.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        back.setFocusable(true);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(getColor(R.color.insights_ice)), null, shape(Color.WHITE)));
        back.setContentDescription(manual?"Back to insights":"Back to paused timer"); back.setOnClickListener(v -> { if (!saving) finish(); });
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView timerLabel = text(manual?"Insights":"Timer",14,MUTED,false);
        timerLabel.setGravity(Gravity.CENTER_VERTICAL); timerLabel.setMinHeight(dp(48));
        timerLabel.setContentDescription(manual?"Back to insights":"Back to paused timer");
        timerLabel.setOnClickListener(v -> { if (!saving) finish(); });
        navigation.addView(timerLabel,new LinearLayout.LayoutParams(-2,-2));
        // A 48dp button centers a 24dp glyph whose visible left edge is about 18dp inside the button.
        // Extend the navigation into the page gutter, aligning the chevron with the title while keeping its hit area.
        LinearLayout.LayoutParams navigationParams=new LinearLayout.LayoutParams(-1,-2);
        navigationParams.leftMargin=-dp(18);
        page.addView(navigation,navigationParams); gap(page,12);
        page.addView(text(manual?"Log activity":"Session review",30,NAVY,true)); gap(page,8);
        page.addView(text(getIntent().getStringExtra("title"),17,MUTED,false)); gap(page,20);
        LinearLayout timeCard = card();
        TextView sessionLabel=text("THIS SESSION",11,BLUE,true);sessionLabel.setLetterSpacing(0.14f);timeCard.addView(sessionLabel); gap(timeCard,8);
        minutes = number(manual?"":String.valueOf(getIntent().getIntExtra("minutes",1)), "Minutes spent this session");
        timeCard.addView(minutes);
        timeCard.addView(text(manual?"Minutes · enter the time spent on this activity":"Minutes · adjust if needed",13,MUTED,false));
        noTime=new CheckBox(this);noTime.setText("Time not recorded");noTime.setTextColor(NAVY);noTime.setMinHeight(dp(48));
        timeCard.addView(noTime);gap(timeCard,8);
        total = text("",14,NAVY,false); timeCard.addView(total);
        timeCard.addView(text(manual?"Saved under today. No execution time is inferred.":"The timer is paused while you review.",13,MUTED,false));
        page.addView(timeCard); gap(page,24);
        page.addView(text("How did the task go?",18,NAVY,true)); gap(page,8);
        RadioGroup outcome = new RadioGroup(this); outcome.setOrientation(RadioGroup.VERTICAL);
        RadioButton partial = new RadioButton(this); partial.setId(View.generateViewId()); partial.setText("Task still in progress");
        complete = new RadioButton(this); complete.setId(View.generateViewId()); complete.setText("Task completed");
        for (RadioButton item : new RadioButton[]{partial, complete}) { item.setTextColor(NAVY); item.setTextSize(15); item.setMinHeight(dp(48)); outcome.addView(item); }
        partial.setChecked(true); page.addView(outcome); gap(page,16);
        remainingGroup = new LinearLayout(this); remainingGroup.setOrientation(LinearLayout.VERTICAL);
        remainingGroup.addView(text("Time still needed · optional",14,NAVY,true));
        remaining = number("", "Remaining minutes"); remainingGroup.addView(remaining);
        remainingGroup.addView(text("Leave blank to carry forward the remaining estimate. Recorded minutes are deducted when you save.",13,MUTED,false));
        page.addView(remainingGroup); gap(page,20);
        TextView detailsToggle=button("Add a difficulty rating · optional",TERTIARY);page.addView(detailsToggle);
        optionalDetails=new LinearLayout(this);optionalDetails.setOrientation(LinearLayout.VERTICAL);optionalDetails.setVisibility(View.GONE);
        optionalDetails.addView(text("How difficult did this session feel?",16,NAVY,true));
        rating = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Not rated · optional","1 · Very easy","2 · Easy","3 · Moderate","4 · Hard","5 · Very hard"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); rating.setAdapter(adapter);
        optionalDetails.addView(rating,new LinearLayout.LayoutParams(-1,dp(52)));page.addView(optionalDetails);gap(page,12);
        detailsToggle.setOnClickListener(v->{expanded=!expanded;optionalDetails.setVisibility(expanded?View.VISIBLE:View.GONE);detailsToggle.setText(expanded?"Hide difficulty rating":"Add a difficulty rating · optional");});
        page.addView(text("Tempo will update your schedule after saving.",13,MUTED,false));
        gap(page,16); error = text("",14,BLUE,false); error.setVisibility(View.GONE); page.addView(error);
        save = button("Save session",PRIMARY); page.addView(save); gap(page,12);
        discard = button(manual?"Cancel":"End without saving",SECONDARY); page.addView(discard); gap(page,8);
        TextView discardHint = text(manual?"Nothing is saved until you confirm.":"Ends this timer without adding a session record.",12,MUTED,false);
        discardHint.setGravity(Gravity.CENTER); page.addView(discardHint); gap(page,8);
        cancel = button("Back to timer",TERTIARY); page.addView(cancel);
        if(manual) cancel.setVisibility(View.GONE);
        outcome.setOnCheckedChangeListener((g,id) -> {
            remainingGroup.setVisibility(complete.isChecked()?View.GONE:View.VISIBLE);
            save.setText(complete.isChecked()?"Complete task":"Save session");
        });
        minutes.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { updateTotal(); }
            public void afterTextChanged(Editable e) { }
        });
        noTime.setOnCheckedChangeListener((button,checked)->{minutes.setEnabled(!checked);minutes.setAlpha(checked?.5f:1f);updateTotal();});
        if(state!=null) {
            minutes.setText(state.getString("minutes","1")); remaining.setText(state.getString("remaining",""));
            complete.setChecked(state.getBoolean("complete",false)); rating.setSelection(state.getInt("rating",0));
            noTime.setChecked(state.getBoolean("noTime",false));expanded=state.getBoolean("expanded",false);
            optionalDetails.setVisibility(expanded?View.VISIBLE:View.GONE);detailsToggle.setText(expanded?"Hide difficulty rating":"Add a difficulty rating · optional");
        }
        updateTotal();
        save.setOnClickListener(v -> submit());
        discard.setOnClickListener(v -> {
            if (saving) return;
            if (manual) { finish(); return; }
            setBusy(true);
            clearTimer();
            AutoScheduler.replan(this, new AutoScheduler.Listener() {
                @Override public void onScheduled(ScheduleGenerator.Result plan) { setResult(RESULT_OK); finish(); }
                @Override public void onFailed(String message) { setResult(RESULT_OK); finish(); }
            });
        });
        cancel.setOnClickListener(v -> { if(!saving) finish(); });
        // If the previous activity died after commit, settle the receipt instead of creating another row.
        setBusy(true);
        learning.editContext(taskId,new TaskRepository.Callback<LearningStore.EditContext>() {
            public void onSuccess(LearningStore.EditContext context) {
                previousMinutes=context.recorded;updateTotal();
                learning.receipt(token,taskId,new TaskRepository.Callback<LearningStore.Saved>() {
                    public void onSuccess(LearningStore.Saved result) {if(result!=null)saved(result);else setBusy(false);}
                    public void onError(Exception e) {setBusy(false);showError(e.getMessage());}
                });
            }
            public void onError(Exception e) { setBusy(false); showError(e.getMessage()); }
        });
    }

    private void submit() {
        if(saving)return;
        try {
            Integer value=noTime.isChecked()?null:Integer.valueOf(minutes.getText().toString().trim());
            if(value!=null && (value<1 || value>1440))throw new IllegalArgumentException("Enter 1–1440 minutes, or choose Time not recorded.");
            String rest=remaining.getText().toString().trim();
            Integer left=complete.isChecked() || rest.isEmpty()?null:Integer.valueOf(rest);
            if(left!=null && (left<1 || left>10080))throw new IllegalArgumentException("Remaining time must be 1–10080 minutes.");
            error.setVisibility(View.GONE); setBusy(true);
            learning.save(token,taskId,value,rating.getSelectedItemPosition(),complete.isChecked(),left,manual?"MANUAL":"TIMER",
                    getIntent().getStringExtra("segments"),getIntent().getBooleanExtra("timingTrusted",false),
                    new TaskRepository.Callback<LearningStore.Saved>() {
                        public void onSuccess(LearningStore.Saved result) { saved(result); }
                        public void onError(Exception e) { setBusy(false); showError(e.getMessage()); }
                    });
        }catch(NumberFormatException e){showError("Enter whole minutes using numbers only.");}
        catch(IllegalArgumentException e){showError(e.getMessage());}
    }
    private void saved(LearningStore.Saved result) {
        clearTimer();
        Toast.makeText(this,result.complete?"Task completed · "+result.totalMinutes+" min recorded":"Activity saved · task still in progress",Toast.LENGTH_LONG).show();
        AutoScheduler.replan(this, new AutoScheduler.Listener() {
            @Override public void onScheduled(ScheduleGenerator.Result plan) {
                setResult(RESULT_OK); finish();
            }
            @Override public void onFailed(String message) {
                Toast.makeText(TaskFeedbackActivity.this, "Time saved; plan not updated: " + message, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK); finish();
            }
        });
    }
    private void clearTimer() {
        SharedPreferences prefs=getSharedPreferences("tempo_focus_timer",MODE_PRIVATE);
        if(token.equals(prefs.getString("sessionToken",null))) prefs.edit().clear().commit();
    }
    private void setBusy(boolean busy) { saving=busy; save.setEnabled(!busy); discard.setEnabled(!busy); cancel.setEnabled(!busy); save.setAlpha(busy?.5f:1f); }
    private void showError(String message) { error.setText(message); error.setVisibility(View.VISIBLE); }
    private void updateTotal() {
        if(noTime.isChecked()) {total.setText("Previously recorded: "+previousMinutes+" min\nThis activity adds no time. A task with missing time is excluded from duration learning.");return;}
        try {long value=Long.parseLong(minutes.getText().toString());total.setText("Previously recorded: "+previousMinutes+" min\nRecorded total after saving: "+(previousMinutes+value)+" min");}
        catch(NumberFormatException e){total.setText("Previously recorded: "+previousMinutes+" min");}
    }
    @Override public void onBackPressed(){if(!saving)super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("minutes",minutes.getText().toString());out.putString("remaining",remaining.getText().toString());
        out.putBoolean("complete",complete.isChecked());out.putInt("rating",rating.getSelectedItemPosition());
        out.putBoolean("noTime",noTime.isChecked());out.putBoolean("expanded",expanded);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy(){if(learning!=null)learning.close();super.onDestroy();}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(3),1);return t;}
    private EditText number(String value,String hint){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setContentDescription(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setTextColor(NAVY);e.setTextSize(17);e.setMinHeight(dp(52));return e;}
    private TextView button(String title,int style){
        // App-wide buttons: dark primary, white outlined secondary, quiet tertiary.
        TextView t=text(title,15,style==PRIMARY?Color.WHITE:style==SECONDARY?NAVY:MUTED,style!=TERTIARY);
        t.setGravity(Gravity.CENTER); t.setMinHeight(dp(style==TERTIARY?48:54));
        t.setPadding(dp(16),dp(12),dp(16),dp(12)); t.setFocusable(true);
        GradientDrawable background=shape(style==PRIMARY?NAVY:style==SECONDARY?Color.WHITE:Color.TRANSPARENT);
        if(style==SECONDARY) background.setStroke(dp(1),getColor(R.color.insights_line));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(getColor(R.color.insights_ice)),background,null));
        return t;
    }
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(20),dp(20),dp(20),dp(20));
        GradientDrawable d=shape(Color.WHITE);d.setCornerRadius(dp(22));l.setBackground(d);l.setElevation(dp(2));return l;}
    private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
