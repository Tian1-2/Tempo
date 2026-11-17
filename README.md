# Tempo — an adaptive study and task planner for Android

Tempo is a time-management app for students. You describe your daily routine and add tasks with an
estimate, difficulty, importance and an optional deadline. Tempo plans the next seven days around your
meals, classes and meetings, times your focus sessions, and learns from what actually happened so that
later estimates get closer to reality.

COMP7506 group project. Native Android, Java, no network access and no account: all data stays in an
on-device SQLite database.

## Features

| Area | What it does |
|---|---|
| **Home** | Greeting, today's progress, the next task, and a short review of yesterday. |
| **Profile ("Your rhythm")** | Wake-up and bed time, meals, best focus time, focus/break length and daily task hours (0.5–12 h). |
| **Fixed schedule** | Weekly classes and meetings (e.g. Tuesday 14:00–16:00). Tasks are never planned on top of them; they appear as grey blocks on the schedule. |
| **Automatic planning** | Re-plans the next 7 days whenever a task, the profile or the fixed schedule changes, or a focus session ends. Keeps breaks between tasks, prefers one continuous block per task, respects deadlines and the daily limit. |
| **Stable plan** | Opening the schedule does not move suggestions you have already seen. Only slots whose start time passed without being done are moved. |
| **Schedule** | Week strip and a 24-hour timeline. Tap a block to start a focus timer, move a slot with time wheels, or delete the whole task. |
| **Focus timer** | Start, pause and resume; the session review records minutes, difficulty and whether the task is done. |
| **Insights** | Learned duration estimates, remaining work, 7-day time chart, estimate error (MAE / bias), and an editable activity history. |

## Requirements

- Android Studio (recent stable) or the command line with **JDK 17 or newer**
- Android SDK Platform **37** (`compileSdk 37`, `targetSdk 37`); runs on Android 7.0+ (`minSdk 24`)
- Gradle 9.5 is downloaded by the wrapper; no other setup is needed

## Build and run

**Android Studio:** *File → Open* this folder (the one containing `settings.gradle.kts`), wait for Gradle
sync, pick an emulator or device and press **Run**.

**Command line:**

```bash
./gradlew :app:assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tempo.app/.HomeActivity
```

If Gradle cannot find the SDK, create `local.properties` with `sdk.dir=/path/to/Android/sdk`.

### First launch

1. Open **Profile** from Home, fill in your routine (or tap *Use recommended routine*) and **Save**.
2. Optionally open **Edit fixed schedule** and add your weekly classes.
3. Add a task with **+**. Tempo saves it and suggests a time right away.

**Demo data (debug builds only):** on the Profile page, long-press the **Your rhythm** title to load a
week of sample tasks, focus history and a weekly lecture. It only works on an app with no tasks.

## Tests

```bash
./gradlew :app:testDebugUnitTest      # JUnit: scheduler, constraints, daily review, fixed schedule
python3 checks/run_checks.py          # DAO and SQLite checks with a small Android shim (needs JDK + Python 3)
```

`checks/run_checks.py` runs the production database code against real SQLite without an emulator;
see [checks/README.md](checks/README.md).

## Project structure

```
app/src/main/java/com/tempo/app/
  HomeActivity, SchedulePreviewActivity, AddTaskActivity,
  ProfileSettingsActivity, FixedScheduleActivity,
  FocusTimerActivity, TaskFeedbackActivity,
  InsightsActivity, SessionHistoryActivity     screens
  scheduler/    ScheduleGenerator (pure Java planner), ScheduleService (7-day window),
                AutoScheduler (background entry point), DefaultScheduleConstraints
  learning/     duration learning, remaining work, feedback storage
  insights/     statistics and charts
  database/     SQLiteOpenHelper and DAOs (tasks, feedback, profile, schedule, fixed events)
  repository/   async repositories used by the screens
  model/        Task, ScheduleItem, UserProfile, TaskFeedback, FixedEvent
app/src/main/assets/home/   home page (HTML rendered in a WebView)
checks/                     JVM + SQLite verification scripts
```

## More documentation

- [OPTIMIZATION-CHANGELOG.md](OPTIMIZATION-CHANGELOG.md) — scheduling and editing changes (Chinese)
- [README-D-Insights.md](README-D-Insights.md) — learning, feedback and insights module (Chinese)
- [README-C-integration.md](README-C-integration.md) — scheduling algorithm integration notes (Chinese)
- [DEMO-GUIDE.md](DEMO-GUIDE.md) — live demo script (Chinese)
