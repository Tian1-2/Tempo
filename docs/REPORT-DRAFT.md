# Tempo — Project Report (draft)

> Draft for the COMP7506 written report (due 30 Nov, at least 2 pages). Sections marked **TODO**
> need the team's input. Check every claim about other apps against their current versions before
> submitting, and complete the AI-use declaration required by the course.

## 1. App category and motivation

**Category:** productivity — personal time management for students.

Students juggle lectures, assignments with deadlines, part-time work and personal life. Most of them
keep a to-do list and a timetable, but the step in between — deciding *when* to do each task, and how
long it will really take — is left to them. Two problems follow:

1. **Planning takes effort, so it is skipped.** Turning a list into a realistic day means checking free
   time, meals, classes and deadlines by hand.
2. **Estimates are optimistic.** People routinely underestimate how long work takes, so plans fail and
   tasks spill into the night.

Tempo's goal is to do that middle step automatically and to get better at it over time: the student
describes their routine and tasks; Tempo proposes a realistic plan, times the work, and learns from
what actually happened.

## 2. Existing apps and their limitations

| App | What it does well | What is missing for our users |
|---|---|---|
| **Google Calendar** | Fixed events, sharing, reminders | Tasks are not time-blocked automatically; the user must find a free slot for every task by hand and re-arrange when plans change. |
| **Todoist** | Fast task capture, due dates, projects and labels | A list, not a plan: it records *when a task is due*, not *when to work on it*, and has no notion of how long tasks really take. |
| **TickTick** | Tasks, calendar view and a built-in Pomodoro timer | The timer and the plan are separate: focus sessions do not change future estimates, and tasks are placed on the calendar manually. |
| **Forest** | Motivating focus timer | Only a timer; no task list, no schedule, no deadlines. |
| **Motion / Reclaim-style auto-schedulers** | Automatic time-blocking around meetings | Aimed at professionals and calendars in the cloud (accounts, subscriptions); they do not model a student's routine (meals, sleep, focus window) or learn personal estimate bias. |

**Gap Tempo addresses:** one offline app that (a) plans tasks into the student's real day automatically,
(b) respects classes, meals, sleep, breaks and deadlines, and (c) learns from timed sessions so that
future plans use realistic durations.

## 3. Design

**Principles**

- *Automatic but predictable.* Plans update by themselves whenever something changes, but suggestions
  the user has already seen do not move just because the page was opened; a slot is moved only once it
  is clearly missed (more than 15 minutes past its start).
- *Honest about time.* Breaks are planned explicitly, pieces shorter than 10 minutes are avoided, and
  learned durations are shown next to the user's own estimate, with the evidence used.
- *Low effort input.* Time wheels instead of typing, a recommended routine, hours instead of minutes
  for daily capacity, and one-tap reminders that open the focus timer.
- *Private and offline.* All data stays in an on-device SQLite database; there is no account and no
  network access.

**Architecture**

- *Screens* (Activities): Home (HTML page in a WebView), Schedule timeline, Add/Edit task, Profile,
  Categories, Focus timer, Session review, Insights, History.
- *Planning engine* (pure Java, unit-tested without Android): `ScheduleGenerator` places work into free
  windows (wake–sleep minus meals, fixed events and existing slots), prefers one continuous block per
  task, puts hard tasks into the productive window, and honours deadlines and the daily limit.
  `ScheduleService` runs it over a rolling 7-day window; `AutoScheduler` triggers it in the background.
- *Learning* (`LearningStore`, `DurationLearning`): every timed session records actual minutes; future
  tasks of the same category/type get a predicted duration from the user's past over- or under-runs.
- *Data*: SQLite tables for tasks, schedule items, profile (including weekly commitments), feedback,
  learning snapshots and categories, accessed through DAOs and repositories; migrations keep old data on upgrade.
- *Reminders*: one exact alarm for the next planned slot, re-armed after every re-plan and after reboot.

## 4. Features

1. **Routine profile** — wake/sleep, meals, best focus time, focus/break length, daily task hours.
2. **Fixed schedule** — named weekly classes and meetings, edited in Profile, that the planner never overlaps.
3. **Automatic 7-day planning** — on every change (task saved or archived, profile or fixed schedule
   edited, focus session ended); tasks that do not fit today continue on later days.
4. **Schedule timeline** — colour-coded by category, with breaks and fixed events; move a slot with
   time wheels (it stays locked) or archive a task (history is kept; it can be restored).
5. **Custom categories and colours** — Study / Work / Life plus the user's own.
6. **Focus timer and session review** — pause/resume, record minutes, difficulty and progress.
7. **Learning and Insights** — Overview / Estimates / Patterns tabs: predicted durations, remaining
   work, 7-day chart, estimate error (MAE), time-of-day patterns, editable activity history.
8. **Morning review** — between 07:00 and 09:00, yesterday's score from completion, recorded time and
   estimate accuracy.
9. **Reminders** — notification before each task; tap to start focusing.

**TODO:** add 3–4 screenshots (Home, Schedule, Insights, Add task) with one-line captions.

## 5. Testing

- 30 JUnit tests for the planner, constraints, daily review, morning review, categories and fixed schedule.
- `checks/run_checks.py`: 14 suites, about 390 assertions, running the production database and planning
  code against real SQLite (including upgrades from every earlier database version).
- Manual runs on the Android emulator (Android 16): upgrade from older databases, reminders firing on
  time, and planning around fixed events.

## 6. Team contributions

**TODO** — one line per member, e.g.:

| Member | Main contributions |
|---|---|
| *Name (A)* | Pages and navigation, automatic multi-day planning, fixed schedule, categories, reminders |
| *Name (B)* | Database schema, DAOs and repositories |
| *Name (C)* | Scheduling algorithm (`ScheduleGenerator`) |
| *Name (D)* | Learning, feedback, Insights and History |
| *Tian1-2* | Scheduling optimisation, task deletion, wheel pickers, profile input |

## 7. Limitations and future work

- Fixed events repeat weekly only; no one-off events or import from a calendar.
- Learning needs a few completed tasks per category before predictions change.
- Reminders use exact alarms; a Play Store release would need the corresponding policy declaration.
- No cloud sync or multi-device support (by design for privacy, but some users will want it).
