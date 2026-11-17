"""Exercise the actual Java schema/query strings with stdlib SQLite.

This verifies SQL contracts and transaction behavior, not Android Activity execution.
Run from the project root: python checks/check_learning_sql.py
"""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/tempo/app"
database_source = (JAVA / "database/AppDatabaseHelper.java").read_text()
learning_source = (JAVA / "learning/LearningStore.java").read_text()
dao_source = (JAVA / "database/ScheduleDao.java").read_text()


def literal(expression):
    return "".join(json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"', expression))


base_schema = [literal(s) for s in re.findall(r"db\.execSQL\((.*?)\);", database_source, re.S)]
new_schema = [literal(s) for s in re.findall(r"db\.execSQL\((.*?)\);", learning_source, re.S) if literal(s).startswith("CREATE")]
sample_sql = literal(re.search(r"String sql = (.*?);", learning_source, re.S).group(1))
db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
checks = 0


def check(condition, message):
    global checks
    checks += 1
    assert condition, message


for statement in base_schema:
    db.execute(statement)
db.execute("INSERT INTO tasks(task_id,title,category,estimated_duration,status) VALUES(1,'Legacy','Work',30,'COMPLETED')")
db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(1,60,'COMPLETED',1000)")
db.commit()
before = list(db.execute("SELECT * FROM tasks")), list(db.execute("SELECT * FROM task_feedback"))
for statement in new_schema:
    db.execute(statement)
after = list(db.execute("SELECT * FROM tasks")), list(db.execute("SELECT * FROM task_feedback"))
check(before == after, "migration preserves existing task and feedback bytes")
check(len(base_schema) == 4 and len(new_schema) == 4, "four B tables and four D tables")
for statement in new_schema:
    db.execute(statement)
check(list(db.execute("SELECT * FROM tasks")) == before[0], "migration can be retried")
sample = list(db.execute(sample_sql))[0]
check(sample[0] == 1 and sample[5] == 60 and sample[7] == 0, "legacy history is never a prior prediction evaluation")

db.execute("INSERT INTO tasks(task_id,title,category,estimated_duration,predicted_duration,status) VALUES(2,'New','Work',30,38,'PENDING')")
db.execute("INSERT INTO learning_snapshots(task_id,estimate,prediction,category,captured_at,evaluable) VALUES(2,30,38,'Work',1500,1)")
db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(2,20,'PARTIAL',2000)")
db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(2,10,'PARTIAL',3000)")
db.execute("UPDATE tasks SET status='IN_PROGRESS' WHERE task_id=2")
check(len(list(db.execute(sample_sql))) == 1, "partial work is excluded from training")
db.commit()
with db:
    row = db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(2,30,'COMPLETED',4000)").lastrowid
    db.execute("INSERT INTO feedback_submissions(token,task_id,feedback_id) VALUES('session-2',2,?)", (row,))
    db.execute("UPDATE tasks SET status='COMPLETED' WHERE task_id=2")
    db.execute("UPDATE learning_snapshots SET completed_at=4000 WHERE task_id=2")
sample = next(row for row in db.execute(sample_sql) if row[0] == 2)
check(sample[5] == 60, "three sessions aggregate into one complete task")
check(sample[4] == 38 and sample[7] == 1, "pre-work prediction remains fixed")
check(len(list(db.execute(sample_sql))) == 2, "one sample per task")
count_before = db.execute("SELECT COUNT(*) FROM task_feedback").fetchone()[0]
try:
    with db:
        row = db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(2,30,'COMPLETED',4001)").lastrowid
        db.execute("INSERT INTO feedback_submissions(token,task_id,feedback_id) VALUES('session-2',2,?)", (row,))
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError("duplicate token accepted")
check(db.execute("SELECT COUNT(*) FROM task_feedback").fetchone()[0] == count_before, "duplicate-token transaction rolls back extra feedback")
check(db.execute("SELECT COUNT(*) FROM feedback_submissions").fetchone()[0] == 1, "one receipt")
check(db.execute("SELECT prediction FROM learning_snapshots WHERE task_id=2").fetchone()[0] == 38, "retry does not rewrite snapshot")

db.execute("INSERT INTO tasks(task_id,title,category,estimated_duration,status) VALUES(3,'Earlier partial','Work',30,'COMPLETED')")
db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(3,2,'PARTIAL',1000)")
db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(3,58,'COMPLETED',4000)")
db.execute("INSERT INTO learning_snapshots(task_id,estimate,prediction,category,captured_at,evaluable,completed_at) VALUES(3,30,38,'Work',1500,0,4000)")
sample = next(row for row in db.execute(sample_sql) if row[0] == 3)
check(sample[5] == 60 and sample[7] == 0, "work predating snapshot is trainable but not evaluable")
deadline_sql = literal(re.search(r'db.rawQuery\(("SELECT deadline,completed_at .*?"), new String', learning_source).group(1))
db.execute("UPDATE learning_snapshots SET deadline=4500 WHERE task_id IN (2,3)")
check(list(db.execute(deadline_sql, (5000,))) == [(4500, 4000)], "deadline comparison excludes snapshots made after recorded work")
check(list(db.execute(deadline_sql, (3500,))) == [], "future completion is excluded from deadline comparison")
db.execute("UPDATE tasks SET estimated_duration=40 WHERE task_id=2")
check(not any(row[0] == 2 for row in db.execute(sample_sql)), "changed task scope is excluded from learning")
db.execute("UPDATE tasks SET estimated_duration=30 WHERE task_id=2")
db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(2,NULL,'PARTIAL',4000)")
check(not any(row[0] == 2 for row in db.execute(sample_sql)), "unknown durations invalidate total-time samples")
db.execute("DELETE FROM task_feedback WHERE task_id=2 AND actual_duration IS NULL")

for start, manual, status in [(100, 0, "PLANNED"), (300, 0, "PLANNED"), (500, 1, "PLANNED"), (700, 0, "COMPLETED")]:
    db.execute("INSERT INTO schedule_items(task_id,scheduled_date,start_time,end_time,is_manually_edited,status) VALUES(1,'2026-11-17',?,?,?,?)", (start, start+50, manual, status))
predicate = re.search(r'"(scheduled_date = \? AND is_manually_edited = 0 AND status = \? AND start_time >= \?)"', dao_source).group(1)
db.execute("DELETE FROM schedule_items WHERE " + predicate, ("2026-11-17", "PLANNED", "200"))
check([r[0] for r in db.execute("SELECT start_time FROM schedule_items ORDER BY start_time")] == [100, 500, 700], "replan preserves elapsed, manual, and completed rows")
db.commit()
try:
    with db:
        db.execute("INSERT INTO feedback_submissions(token,task_id,feedback_id) VALUES('bad',9999,9999)")
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError("orphan receipt accepted")
check(db.execute("SELECT COUNT(*) FROM feedback_submissions WHERE token='bad'").fetchone()[0] == 0, "foreign keys reject orphan receipt")

# V6: a task's saved comparison stays visible after discarding a timer session.
preview_sql = literal(re.search(r'db.rawQuery\(("SELECT s.estimate,s.prediction.*?), new String', learning_source, re.S).group(1))
db.execute("INSERT INTO tasks(task_id,title,category,estimated_duration,predicted_duration,status) VALUES(4,'Discard test','Study',60,65,'PENDING')")
db.execute("INSERT INTO learning_snapshots(task_id,estimate,prediction,category,captured_at,evaluable) VALUES(4,60,65,'Study',5000,1)")
check([row[:2] for row in db.execute(preview_sql, ('4',))] == [(60, 65)], "snapshot with no saved session remains visible")
db.execute("UPDATE tasks SET estimated_duration=70,predicted_duration=75,status='IN_PROGRESS' WHERE task_id=4")
db.execute("INSERT INTO task_feedback(task_id,actual_duration,completion_status,completed_at) VALUES(4,5,'PARTIAL',6000)")
check([row[:2] for row in db.execute(preview_sql, ('4',))] == [(60, 65)], "ongoing task displays original snapshot instead of changed values")
db.execute("UPDATE tasks SET status='COMPLETED' WHERE task_id=4")
check(list(db.execute(preview_sql, ('4',))) == [], "completed task leaves the pending comparison list")

print(f"PASS: {checks} SQLite migration, aggregation, receipt, replan and preview assertions.")
