# Local verification

Run python checks/run_checks.py at the project root with Python 3 and a JDK.

The runner compiles production Java models, learning/analytics, DAOs, repositories, scheduling services, Home review and the explicit demo fixture. A test-only Android database shim executes real SQLite through Python. Its files stay outside Android app source sets.

Expected result for R4: **363 assertions**.

| Area | Assertions |
|---|---:|
| Insights | 24 |
| Generation entry and preflight | 25 |
| Duration learning and remaining work | 25 |
| Timeline geometry | 15 |
| Timing and task types | 22 |
| Persistence workflows | 54 |
| Evaluation eligibility | 55 |
| Manual scheduling rules | 13 |
| Integrated seven-day release workflows | 61 |
| Home frozen historical evaluation | 21 |
| Internship demonstration fixture | 27 |
| SQLite contracts | 21 |

DailyReviewHistoryCheck reproduces the false 60→120 minute “spot on” result and covers shared D eligibility, missing/backfilled time, manual logs, positive corrections, future completions, archive and deletion.

InternDemoCheck uses production persistence, an explicit 2026-11-23 local clock and the production scheduling algorithm. It validates commitments, focus limits, locked preferences, repeated planning, the on-screen new task, sample counts and recording-result numbers. It prints the reference timeline. Separate integrated release checks exercise the real seven-day service with the host clock.

These checks do not emulate Activities, input methods, Android lifecycle/thread behavior, screen layout or APK building. Run Android Gradle tasks and the device checklist in DEMO-GUIDE.md before recording.
