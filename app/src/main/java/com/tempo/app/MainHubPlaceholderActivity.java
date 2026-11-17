package com.tempo.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Temporary destination for the home icon until the main-page owner replaces it. */
public class MainHubPlaceholderActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int background = Color.rgb(247, 248, 245);
        getWindow().setStatusBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(24), 0, dp(24), 0);
        page.setBackgroundColor(background);
        setContentView(page);
        page.addView(label("主页面待接入", 22, true));
        page.addView(label("这里预留给主页面模块。", 14, false));
        TextView profile = label("用户档案设置", 16, false);
        profile.setOnClickListener(v -> startActivity(new Intent(this, ProfileSettingsActivity.class)));
        page.addView(profile);
        TextView insights = label("Insights · 时间洞察", 16, false);
        insights.setOnClickListener(v -> startActivity(new Intent(this, InsightsActivity.class)));
        page.addView(insights);
        TextView schedule = label("返回日程", 16, false);
        schedule.setOnClickListener(v -> { startActivity(new Intent(this, SchedulePreviewActivity.class)); finish(); });
        page.addView(schedule);
    }

    private TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(30, 48, 52));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(12), 0, dp(12));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
