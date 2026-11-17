package com.tempo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** NumberPicker wheels for task deadlines and manually adjusted schedule slots. */
public final class WheelPickers {
    private WheelPickers() { }

    public static void pickTime(Activity activity, EditText target, boolean allowEndOfDay) {
        Calendar selected = Calendar.getInstance();
        String value = target.getText().toString().trim();
        if (value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            selected.set(Calendar.HOUR_OF_DAY, Integer.parseInt(value.substring(0, 2)));
            selected.set(Calendar.MINUTE, Integer.parseInt(value.substring(3)));
        }
        showTime(activity, selected, target, false, allowEndOfDay);
    }

    public static void pickDeadline(Activity activity, EditText target) {
        Calendar selected = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setLenient(false);
        try {
            Date date = format.parse(target.getText().toString().trim());
            if (date != null) selected.setTime(date);
        } catch (ParseException ignored) { }

        LinearLayout wheels = row(activity);
        NumberPicker year = wheel(activity, selected.get(Calendar.YEAR), 2020, 2100);
        NumberPicker month = wheel(activity, selected.get(Calendar.MONTH) + 1, 1, 12);
        NumberPicker day = wheel(activity, selected.get(Calendar.DAY_OF_MONTH), 1,
                selected.getActualMaximum(Calendar.DAY_OF_MONTH));
        year.setOnValueChangedListener((picker, oldValue, newValue) -> updateDayMaximum(year, month, day));
        month.setOnValueChangedListener((picker, oldValue, newValue) -> updateDayMaximum(year, month, day));
        wheels.addView(year, weight()); wheels.addView(month, weight()); wheels.addView(day, weight());
        new AlertDialog.Builder(activity).setTitle("Choose deadline date").setView(wheels)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (dialog, which) -> {
                    selected.set(year.getValue(), month.getValue() - 1, day.getValue());
                    showTime(activity, selected, target, true, false);
                }).show();
    }

    private static void showTime(Activity activity, Calendar selected, EditText target,
                                 boolean deadline, boolean allowEndOfDay) {
        LinearLayout wheels = row(activity);
        NumberPicker hour = wheel(activity, selected.get(Calendar.HOUR_OF_DAY), 0,
                allowEndOfDay ? 24 : 23);
        NumberPicker minute = wheel(activity, selected.get(Calendar.MINUTE), 0, 59);
        hour.setFormatter(value -> String.format(Locale.US, "%02d", value));
        minute.setFormatter(value -> String.format(Locale.US, "%02d", value));
        if (allowEndOfDay) hour.setOnValueChangedListener((picker, oldValue, newValue) -> {
            if (newValue == 24) minute.setValue(0);
            minute.setEnabled(newValue != 24);
        });
        wheels.addView(hour, weight()); wheels.addView(minute, weight());
        new AlertDialog.Builder(activity).setTitle(deadline ? "Choose deadline time" : "Choose time")
                .setView(wheels).setNegativeButton("Cancel", null)
                .setPositiveButton("Done", (dialog, which) -> {
                    if (deadline) {
                        selected.set(Calendar.HOUR_OF_DAY, hour.getValue());
                        selected.set(Calendar.MINUTE, minute.getValue());
                        target.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                .format(selected.getTime()));
                    } else {
                        target.setText(String.format(Locale.US, "%02d:%02d",
                                hour.getValue(), minute.getValue()));
                    }
                }).show();
    }

    private static void updateDayMaximum(NumberPicker year, NumberPicker month, NumberPicker day) {
        Calendar date = Calendar.getInstance();
        date.set(year.getValue(), month.getValue() - 1, 1);
        int maximum = date.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (day.getValue() > maximum) day.setValue(maximum);
        day.setMaxValue(maximum);
    }

    private static NumberPicker wheel(Activity activity, int value, int min, int max) {
        NumberPicker picker = new NumberPicker(activity);
        picker.setMinValue(min); picker.setMaxValue(max); picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return picker;
    }

    private static LinearLayout row(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        int horizontal = (int) (activity.getResources().getDisplayMetrics().density * 20);
        layout.setPadding(horizontal, 0, horizontal, 0);
        return layout;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }
}
