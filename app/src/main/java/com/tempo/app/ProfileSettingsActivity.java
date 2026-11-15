package com.tempo.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tempo.app.model.UserProfile;
import com.tempo.app.repository.RepositoryProvider;
import com.tempo.app.repository.TaskRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small editable destination for the schedule screen's profile back button. */
public class ProfileSettingsActivity extends AppCompatActivity {
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private UserProfile profile;
    private LinearLayout body;
    private TextView save;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RepositoryProvider.init(this);
        int background = Color.rgb(247, 248, 245);
        int ink = Color.rgb(30, 48, 52);
        getWindow().setStatusBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(background);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(20), dp(24), dp(32));
        scroll.addView(body);
        setContentView(scroll);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setTranslationX(-dp(24));
        BackChevronView back = new BackChevronView(this, "返回日程");
        back.setOnClickListener(v -> openSchedule());
        navigation.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView backLabel = text("日程", 14, Color.rgb(105, 120, 119), false);
        backLabel.setGravity(Gravity.CENTER_VERTICAL);
        backLabel.setOnClickListener(v -> openSchedule());
        navigation.addView(backLabel, new LinearLayout.LayoutParams(-2, dp(48)));
        body.addView(navigation, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(text("用户档案设置", 26, ink, true));
        TextView help = text("这些时间与专注偏好会用于安排日程。", 13, Color.rgb(105, 120, 119), false);
        help.setPadding(0, dp(8), 0, dp(20)); body.addView(help);
        addField("起床时间", "07:00", false);
        addField("睡觉时间", "23:30", false);
        addField("早餐时间", "08:00", false);
        addField("午餐时间", "12:30", false);
        addField("晚餐时间", "18:30", false);
        addField("偏好开始时间", "09:00", false);
        addField("单次专注分钟", "25", true);
        addField("休息分钟", "5", true);
        addField("每日可用分钟", "240", true);
        save = text("保存设置", 16, ink, true);
        save.setGravity(Gravity.CENTER);
        save.setBackgroundColor(Color.rgb(194, 232, 213));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(52));
        saveParams.topMargin = dp(22); body.addView(save, saveParams);
        save.setOnClickListener(v -> save());

        RepositoryProvider.getUserProfileRepository().getProfile(new TaskRepository.Callback<UserProfile>() {
            @Override public void onSuccess(UserProfile value) {
                profile = value == null ? new UserProfile() : value;
                if (value != null) {
                    fill("起床时间", value.getWakeTime()); fill("睡觉时间", value.getSleepTime());
                    fill("早餐时间", value.getBreakfastTime()); fill("午餐时间", value.getLunchTime());
                    fill("晚餐时间", value.getDinnerTime()); fill("偏好开始时间", value.getPreferredStartTime());
                    fill("单次专注分钟", String.valueOf(value.getFocusDuration()));
                    fill("休息分钟", String.valueOf(value.getBreakDuration()));
                    fill("每日可用分钟", String.valueOf(value.getDailyCapacity()));
                }
            }
            @Override public void onError(Exception error) { Toast.makeText(ProfileSettingsActivity.this, "读取档案失败", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void addField(String name, String defaultValue, boolean number) {
        TextView label = text(name, 13, Color.rgb(105, 120, 119), false);
        body.addView(label);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(defaultValue);
        input.setTextSize(16);
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_DATETIME);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.bottomMargin = dp(13); body.addView(input, params);
        fields.put(name, input);
    }

    private void fill(String name, String value) { if (value != null) fields.get(name).setText(value); }
    private String value(String name) { return fields.get(name).getText().toString().trim(); }
    private void save() {
        try {
            for (String name : new String[]{"起床时间", "睡觉时间", "早餐时间", "午餐时间", "晚餐时间", "偏好开始时间"}) {
                if (!value(name).matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException(name + "应为 HH:mm");
            }
            int focus = Integer.parseInt(value("单次专注分钟"));
            int rest = Integer.parseInt(value("休息分钟"));
            int capacity = Integer.parseInt(value("每日可用分钟"));
            if (focus <= 0 || rest < 0 || capacity <= 0) throw new IllegalArgumentException("分钟数应为有效正数");
            UserProfile updated = profile == null ? new UserProfile() : profile;
            updated.setWakeTime(value("起床时间")); updated.setSleepTime(value("睡觉时间"));
            updated.setBreakfastTime(value("早餐时间")); updated.setLunchTime(value("午餐时间"));
            updated.setDinnerTime(value("晚餐时间")); updated.setPreferredStartTime(value("偏好开始时间"));
            updated.setFocusDuration(focus); updated.setBreakDuration(rest); updated.setDailyCapacity(capacity);
            save.setEnabled(false);
            RepositoryProvider.getUserProfileRepository().saveProfile(updated, new TaskRepository.Callback<Long>() {
                @Override public void onSuccess(Long id) { save.setEnabled(true); profile = updated; Toast.makeText(ProfileSettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show(); }
                @Override public void onError(Exception error) { save.setEnabled(true); Toast.makeText(ProfileSettingsActivity.this, "保存失败：" + error.getMessage(), Toast.LENGTH_LONG).show(); }
            });
        } catch (NumberFormatException error) { Toast.makeText(this, "分钟数必须是数字", Toast.LENGTH_SHORT).show(); }
        catch (IllegalArgumentException error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void openSchedule() { startActivity(new Intent(this, SchedulePreviewActivity.class)); finish(); }
    @Override public void onBackPressed() { openSchedule(); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view;
    }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
}
