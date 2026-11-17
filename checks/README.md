# Local verification

Run `python checks/run_checks.py` at the project root with Python 3 and a JDK.

The runner compiles production pure-Java logic, LearningStore/DAOs and the page-side ScheduleEditRules into a temporary directory. The small Android database shim in checks/jvm/android executes SQL through Python standard-library SQLite. It is test-only and is outside Android app source sets.

Expected result: **247 assertions** — Insights 24; generation/preflight 25; duration/remaining 25; timeline 15; timing/types 22; production persistence workflows 47; evaluation eligibility 55; page-side manual scheduling 13; SQL contracts 21.

The checks cover actual save/correct/remaining/evaluation/migration logic and both page-side manual scheduling rules and final DAO writes. They do not emulate Android SQLiteOpenHelper version dispatch, Activities, threads, keyboard/window behavior, APK building or devices. Current device observations and integration limits are recorded in [README-D-Insights.md](../README-D-Insights.md).
