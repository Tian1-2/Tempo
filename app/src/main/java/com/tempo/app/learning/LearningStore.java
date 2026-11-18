package com.tempo.app.learning;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.TaskDao;
import com.tempo.app.database.AppDatabaseHelper;
import com.tempo.app.model.Task;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.tempo.app.insights.TimeOfDayStats;

/** D's persistence. All feedback mutations and recalibration share one SQLite transaction. */
public final class LearningStore {
    private final AppDatabaseHelper helper;
    public LearningStore(AppDatabaseHelper helper) { this.helper = helper; }

    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS learning_snapshots (task_id INTEGER PRIMARY KEY, "
                + "estimate INTEGER NOT NULL, prediction INTEGER NOT NULL, category TEXT, task_type TEXT, "
                + "difficulty INTEGER, deadline INTEGER, captured_at INTEGER NOT NULL, evaluable INTEGER NOT NULL, "
                + "completed_at INTEGER, remaining_override INTEGER, scope_changed INTEGER NOT NULL DEFAULT 0, "
                + "remaining_anchor_feedback_id INTEGER NOT NULL DEFAULT 0, "
                + "FOREIGN KEY(task_id) REFERENCES tasks(task_id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS feedback_submissions (token TEXT PRIMARY KEY, "
                + "task_id INTEGER NOT NULL, feedback_id INTEGER NOT NULL, "
                + "FOREIGN KEY(task_id) REFERENCES tasks(task_id) ON DELETE CASCADE, "
                + "FOREIGN KEY(feedback_id) REFERENCES task_feedback(feedback_id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS session_details (feedback_id INTEGER PRIMARY KEY, "
                + "source TEXT NOT NULL, timing_valid INTEGER NOT NULL DEFAULT 0, "
                + "revision INTEGER NOT NULL DEFAULT 0, edited_at INTEGER, "
                + "FOREIGN KEY(feedback_id) REFERENCES task_feedback(feedback_id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS session_segments (feedback_id INTEGER NOT NULL, "
                + "segment_index INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL, "
                + "PRIMARY KEY(feedback_id,segment_index), "
                + "FOREIGN KEY(feedback_id) REFERENCES task_feedback(feedback_id) ON DELETE CASCADE)");
    }

    public static void upgradeTables(SQLiteDatabase db) {
        createTables(db);
        boolean scope=false, anchor=false;
        try (Cursor c=db.rawQuery("PRAGMA table_info(learning_snapshots)",null)) {
            while(c.moveToNext()) {
                scope |= "scope_changed".equals(c.getString(1));
                anchor |= "remaining_anchor_feedback_id".equals(c.getString(1));
            }
        }
        if(!scope) db.execSQL("ALTER TABLE learning_snapshots ADD COLUMN scope_changed INTEGER NOT NULL DEFAULT 0");
        if(!anchor) {
            db.execSQL("ALTER TABLE learning_snapshots ADD COLUMN remaining_anchor_feedback_id INTEGER NOT NULL DEFAULT 0");
            // Existing overrides were entered after the latest legacy feedback, not before it.
            db.execSQL("UPDATE learning_snapshots SET remaining_anchor_feedback_id=COALESCE((SELECT MAX(feedback_id) "
                    + "FROM task_feedback f WHERE f.task_id=learning_snapshots.task_id),0) WHERE remaining_override IS NOT NULL");
        }
        // Repair V2's future COMPLETED plan blocks only when a saved task completion proves they were unused.
        try(Cursor c=db.rawQuery("SELECT s.task_id,s.completed_at FROM learning_snapshots s JOIN tasks t ON t.task_id=s.task_id "
                + "WHERE t.status='COMPLETED' AND s.completed_at>0 AND s.completed_at<=?",new String[]{String.valueOf(System.currentTimeMillis())})) {
            while(c.moveToNext()) {
                int taskId=c.getInt(0);long completed=c.getLong(1);
                ContentValues cancelled=new ContentValues();cancelled.put("status","CANCELLED");
                db.update("schedule_items",cancelled,"task_id=? AND status='COMPLETED' AND start_time>=?",new String[]{String.valueOf(taskId),String.valueOf(completed)});
                long end=Math.floorDiv(completed+59_999,60_000)*60_000;
                ContentValues shortened=new ContentValues();shortened.put("end_time",end);
                db.update("schedule_items",shortened,"task_id=? AND status='COMPLETED' AND start_time<? AND end_time>?",
                        new String[]{String.valueOf(taskId),String.valueOf(completed),String.valueOf(end)});
                releaseCompletedPlan(db,taskId,completed);
            }
        }
    }

    public static final class Prepared {
        public final int prediction;
        public final long previousMinutes;
        public Prepared(int prediction, long previousMinutes) { this.prediction = prediction; this.previousMinutes = previousMinutes; }
    }
    public static final class Saved {
        public final long feedbackId, totalMinutes;
        public final boolean complete;
        public Saved(long feedbackId, long totalMinutes, boolean complete) {
            this.feedbackId = feedbackId; this.totalMinutes = totalMinutes; this.complete = complete;
        }
    }

    public static final class EditContext {
        public final boolean started;
        public final long recorded;
        public final int remaining;
        EditContext(boolean started,long recorded,int remaining) {this.started=started;this.recorded=recorded;this.remaining=remaining;}
    }

    public static final class Record {
        public long id, savedAt;
        public int taskId, difficulty, revision;
        public Integer minutes;
        public String title, outcome, source, category;
    }

    public Prepared prepare(int taskId, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            refreshPredictionsInTransaction(db, now);
            Task task = new TaskDao(helper).getTaskById(taskId);
            if (task == null || task.isArchived()) throw new IllegalArgumentException("Task no longer exists.");
            if ("COMPLETED".equals(task.getStatus())) throw new IllegalArgumentException("This task is already completed.");
            long actual = actualMinutes(db, taskId);
            capture(db, task, actual, now);
            Prepared result = new Prepared(task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration(), actual);
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }

    private void capture(SQLiteDatabase db, Task task, long actual, long now) {
        ContentValues values = new ContentValues();
        values.put("task_id", task.getTaskId());
        values.put("estimate", task.getEstimatedDuration());
        values.put("prediction", task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration());
        values.put("category", task.getCategory()); values.put("task_type", task.getTaskType());
        values.put("difficulty", task.getDifficulty()); values.put("deadline", task.getDeadline());
        values.put("captured_at", now); values.put("evaluable", actual == 0 && !hasFeedback(db,task.getTaskId()) ? 1 : 0);
        db.insertWithOnConflict("learning_snapshots", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public Saved receipt(String token, int taskId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT s.task_id, f.feedback_id, f.completion_status FROM feedback_submissions s "
                + "JOIN task_feedback f ON f.feedback_id=s.feedback_id WHERE s.token=?", new String[]{token})) {
            if (!c.moveToFirst()) return null;
            if (c.getInt(0) != taskId) throw new IllegalArgumentException("Session belongs to another task.");
            return new Saved(c.getLong(1), actualMinutes(db, taskId), "COMPLETED".equals(c.getString(2)));
        }
    }

    public Saved save(String token, int taskId, int minutes, int difficulty, boolean complete,
                      Integer remaining, long now) {
        return save(token,taskId,Integer.valueOf(minutes),difficulty,complete,remaining,"TIMER","",false,now);
    }

    public Saved save(String token, int taskId, Integer minutes, int difficulty, boolean complete,
                      Integer remaining, String source, String encodedSegments, boolean timingTrusted, long now) {
        if (token == null || token.trim().isEmpty()) throw new IllegalArgumentException("Session ID is missing.");
        validateFeedback(minutes,difficulty,remaining);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Saved existing = receipt(token, taskId);
            if (existing != null) { db.setTransactionSuccessful(); return existing; }
            Task task = new TaskDao(helper).getTaskById(taskId);
            if (task == null || task.isArchived() || "COMPLETED".equals(task.getStatus())) throw new IllegalArgumentException("This task is missing or already completed.");
            // prepare() normally captures before timing. A recovery save without a snapshot is not evaluable.
            try (Cursor c = db.rawQuery("SELECT task_id FROM learning_snapshots WHERE task_id=?", new String[]{String.valueOf(taskId)})) {
                if (!c.moveToFirst()) capture(db, task, Math.max(1, actualMinutes(db, taskId)), now);
            }
            Integer previousOverride=readOverride(db,taskId);
            ContentValues feedback = new ContentValues();
            feedback.put("task_id", taskId);
            if(minutes==null) feedback.putNull("actual_duration"); else feedback.put("actual_duration", minutes);
            feedback.put("actual_difficulty", difficulty); feedback.put("completion_status", complete ? "COMPLETED" : "PARTIAL");
            feedback.put("completed_at", now);
            long id = db.insertOrThrow("task_feedback", null, feedback);
            List<SessionTiming.Segment> segments=SessionTiming.decode(encodedSegments);
            boolean valid="TIMER".equals(source) && timingTrusted && SessionTiming.matchesRecordedMinutes(segments,minutes,now);
            ContentValues details=new ContentValues(); details.put("feedback_id",id);
            details.put("source",minutes==null?"STATUS":("TIMER".equals(source)?(valid?"TIMER":"ADJUSTED"):"MANUAL"));
            details.put("timing_valid",valid?1:0); db.insertOrThrow("session_details",null,details);
            for(int index=0;index<segments.size();index++) {
                ContentValues segment=new ContentValues(); segment.put("feedback_id",id);segment.put("segment_index",index);
                segment.put("started_at",segments.get(index).start);segment.put("ended_at",segments.get(index).end);
                db.insertOrThrow("session_segments",null,segment);
            }
            ContentValues receipt = new ContentValues();
            receipt.put("token", token); receipt.put("task_id", taskId); receipt.put("feedback_id", id);
            db.insertOrThrow("feedback_submissions", null, receipt);
            ContentValues status = new ContentValues(); status.put("status", complete ? "COMPLETED" : "IN_PROGRESS");
            db.update("tasks", status, "task_id=?", new String[]{String.valueOf(taskId)});
            ContentValues snapshot = new ContentValues();
            if (complete) snapshot.put("completed_at", now);
            Integer nextOverride=complete?null:DurationLearning.advanceRemaining(previousOverride,minutes==null?0:minutes,remaining);
            if(nextOverride==null) snapshot.putNull("remaining_override"); else snapshot.put("remaining_override",nextOverride);
            if(!complete && remaining!=null) snapshot.put("remaining_anchor_feedback_id",id);
            // This flag describes pre-work provenance and also supports deadline comparisons.
            // Missing/backfilled time is filtered separately in samples(), without losing a known completion date.
            if(!"TIMER".equals(source)) snapshot.put("evaluable",0);
            db.update("learning_snapshots", snapshot, "task_id=?", new String[]{String.valueOf(taskId)});
            if (complete) {
                releaseCompletedPlan(db,taskId,now);
            }
            refreshPredictionsInTransaction(db, now);
            Saved saved = new Saved(id, actualMinutes(db, taskId), complete);
            db.setTransactionSuccessful();
            return saved;
        } finally { db.endTransaction(); }
    }

    private static void validateFeedback(Integer minutes,int difficulty,Integer remaining) {
        if(minutes!=null && (minutes<1 || minutes>1440)) throw new IllegalArgumentException("Enter 1–1440 minutes, or choose Time not recorded.");
        if(difficulty<0 || difficulty>5) throw new IllegalArgumentException("Choose a difficulty from 1 to 5, or leave it unrated.");
        if(remaining!=null && (remaining<1 || remaining>10080)) throw new IllegalArgumentException("Remaining time must be 1–10080 minutes.");
    }

    private static boolean hasFeedback(SQLiteDatabase db,int taskId) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM task_feedback WHERE task_id=? LIMIT 1",new String[]{String.valueOf(taskId)})) {return c.moveToFirst();}
    }

    private static Integer readOverride(SQLiteDatabase db,int taskId) {
        try(Cursor c=db.rawQuery("SELECT remaining_override FROM learning_snapshots WHERE task_id=?",new String[]{String.valueOf(taskId)})) {
            return c.moveToFirst() && !c.isNull(0)?c.getInt(0):null;
        }
    }

    private static void releaseCompletedPlan(SQLiteDatabase db,int taskId,long now) {
        // Preserve rows as cancelled history, but release their occupancy, including future dates/manual blocks.
        ContentValues cancelled=new ContentValues(); cancelled.put("status","CANCELLED");
        db.update("schedule_items",cancelled,"task_id=? AND status='PLANNED' AND start_time>=?",new String[]{String.valueOf(taskId),String.valueOf(now)});
        // A currently open block ends at the next whole minute. This remains a plan, not an execution measurement.
        long boundary=Math.floorDiv(now+59_999,60_000)*60_000;
        ContentValues shortened=new ContentValues(); shortened.put("end_time",boundary);
        db.update("schedule_items",shortened,"task_id=? AND status='PLANNED' AND start_time<? AND end_time>?",
                new String[]{String.valueOf(taskId),String.valueOf(now),String.valueOf(boundary)});
        ContentValues done=new ContentValues(); done.put("status","COMPLETED");
        db.update("schedule_items",done,"task_id=? AND status='PLANNED'",new String[]{String.valueOf(taskId)});
    }

    public EditContext editContext(int taskId) {
        SQLiteDatabase db=helper.getReadableDatabase(); Task task=new TaskDao(helper).getTaskById(taskId);
        if(task==null || task.isArchived()) throw new IllegalArgumentException("Task no longer exists.");
        boolean started=hasFeedback(db,taskId);
        try(Cursor c=db.rawQuery("SELECT task_id FROM learning_snapshots WHERE task_id=?",new String[]{String.valueOf(taskId)})) {started|=c.moveToFirst();}
        long recorded=actualMinutes(db,taskId);
        return new EditContext(started,recorded,DurationLearning.remaining(task.getPredictedDuration()>0?task.getPredictedDuration():task.getEstimatedDuration(),recorded,readOverride(db,taskId),focusMinutes()));
    }

    private int focusMinutes() {
        com.tempo.app.model.UserProfile profile=new com.tempo.app.database.UserProfileDao(helper).getProfile();
        return profile==null?25:profile.getFocusDuration();
    }

    /** Called inside the slot edit transaction after the user confirms the new remaining estimate. */
    public void setPlanningRemaining(int taskId, int minutes, long now) {
        validateFeedback(null, 0, minutes);
        SQLiteDatabase db = helper.getWritableDatabase();
        Task task = new TaskDao(helper).getTaskById(taskId);
        if (task == null || task.isArchived() || "COMPLETED".equals(task.getStatus()))
            throw new IllegalArgumentException("This task is no longer available for planning.");
        capture(db, task, actualMinutes(db, taskId), now);
        ContentValues values = new ContentValues();
        values.put("remaining_override", minutes);
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(feedback_id),0) FROM task_feedback WHERE task_id=?",
                new String[]{String.valueOf(taskId)})) {
            c.moveToFirst();
            values.put("remaining_anchor_feedback_id", c.getLong(0));
        }
        db.update("learning_snapshots", values, "task_id=?", new String[]{String.valueOf(taskId)});
    }

    public int updateTask(Task edited,Integer remaining,long now) {
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
        try {
            Task current=new TaskDao(helper).getTaskById(edited.getTaskId());
            if(current==null || current.isArchived()) throw new IllegalArgumentException("Task is archived or no longer exists.");
            EditContext context=editContext(current.getTaskId());
            boolean changed=current.getEstimatedDuration()!=edited.getEstimatedDuration()
                    || !Objects.equals(current.getCategory(),edited.getCategory())
                    || !Objects.equals(cleanType(current.getTaskType()),cleanType(edited.getTaskType()));
            if(context.started && changed) {
                if(!"COMPLETED".equals(current.getStatus()) && remaining==null)
                    throw new IllegalArgumentException("This task has already started. Confirm its remaining time before changing its estimate, category or type.");
                validateFeedback(null,0,remaining);
                capture(db,current,Math.max(1,context.recorded),now);
                ContentValues state=new ContentValues(); state.put("scope_changed",1);
                if(remaining!=null) state.put("remaining_override",remaining);
                try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(feedback_id),0) FROM task_feedback WHERE task_id=?",new String[]{String.valueOf(current.getTaskId())})) {
                    c.moveToFirst();state.put("remaining_anchor_feedback_id",c.getLong(0));
                }
                db.update("learning_snapshots",state,"task_id=?",new String[]{String.valueOf(current.getTaskId())});
            }
            // Preserve state from the transaction, not a possibly stale editing screen.
            edited.setStatus(current.getStatus()); edited.setPredictedDuration(current.getPredictedDuration());
            int rows=new TaskDao(helper).updateTask(edited);refreshPredictionsInTransaction(db,now);
            db.setTransactionSuccessful();return rows;
        } finally {db.endTransaction();}
    }
    private static String cleanType(String value) {return value==null?"":value.trim();}

    public List<Task> pendingTasks() {return new TaskDao(helper).getPendingTasks();}

    public static final class History {
        public final List<Record> rows;
        public final int count, weekMinutes, finished;
        History(List<Record> rows, int count, int weekMinutes, int finished) {
            this.rows = rows; this.count = count; this.weekMinutes = weekMinutes; this.finished = finished;
        }
    }

    public History history(long now) {
        SQLiteDatabase db = helper.getReadableDatabase();
        long start = com.tempo.app.insights.InsightsStats.weekStart(now, java.util.TimeZone.getDefault()).getTimeInMillis();
        db.beginTransactionNonExclusive();
        try (Cursor c = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(CASE WHEN completed_at BETWEEN ? AND ? "
                + "AND actual_duration>0 THEN actual_duration ELSE 0 END),0), "
                + "COUNT(DISTINCT CASE WHEN completion_status='COMPLETED' THEN task_id END) FROM task_feedback",
                new String[]{String.valueOf(start), String.valueOf(now)})) {
            c.moveToFirst();
            History result = new History(records(200), c.getInt(0), c.getInt(1), c.getInt(2));
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }

    public List<Record> records(int limit) {
        List<Record> records=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT f.feedback_id,f.task_id,t.title,f.actual_duration,f.actual_difficulty," 
                + "f.completion_status,f.completed_at,COALESCE(d.source,'LEGACY'),COALESCE(d.revision,0),t.category "
                + "FROM task_feedback f JOIN tasks t ON t.task_id=f.task_id LEFT JOIN session_details d ON d.feedback_id=f.feedback_id "
                + "ORDER BY f.completed_at DESC,f.feedback_id DESC LIMIT ?",new String[]{String.valueOf(limit)})) {
            while(c.moveToNext()) {Record r=new Record();r.id=c.getLong(0);r.taskId=c.getInt(1);r.title=c.getString(2);
                r.minutes=c.isNull(3)||c.getInt(3)<=0?null:c.getInt(3);r.difficulty=c.getInt(4);r.outcome=c.getString(5);r.savedAt=c.getLong(6);
                r.category=c.getString(9);r.source=c.getString(7);r.revision=c.getInt(8);records.add(r);}
        }
        return records;
    }

    public void correctRecord(long id,int expectedRevision,Integer minutes,int difficulty,long now) {
        validateFeedback(minutes,difficulty,null);
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT f.task_id,f.actual_duration,COALESCE(d.revision,0),COALESCE(d.source,'LEGACY') FROM task_feedback f "
                + "LEFT JOIN session_details d ON d.feedback_id=f.feedback_id WHERE f.feedback_id=?",new String[]{String.valueOf(id)})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("This record no longer exists.");
            if(c.getInt(2)!=expectedRevision) throw new IllegalArgumentException("This record changed. Reopen it before editing.");
            int taskId=c.getInt(0), oldMinutes=Math.max(0,c.getInt(1));
            String previousSource=c.getString(3);
            boolean timeChanged=!Objects.equals(c.isNull(1)||oldMinutes==0?null:oldMinutes,minutes);
            ContentValues values=new ContentValues();if(minutes==null) values.putNull("actual_duration");else values.put("actual_duration",minutes);
            values.put("actual_difficulty",difficulty);db.update("task_feedback",values,"feedback_id=?",new String[]{String.valueOf(id)});
            ContentValues initial=new ContentValues();initial.put("feedback_id",id);initial.put("source","LEGACY");
            db.insertWithOnConflict("session_details",null,initial,SQLiteDatabase.CONFLICT_IGNORE);
            ContentValues metadata=new ContentValues();metadata.put("revision",expectedRevision+1);metadata.put("edited_at",now);
            if(timeChanged) {
                metadata.put("source",correctedSource(previousSource,expectedRevision,oldMinutes,minutes));
                metadata.put("timing_valid",0);
            } else if("ADJUSTED".equals(previousSource) && expectedRevision==0) {
                // A first rating-only edit must not make a known initial adjustment look like an ambiguous V8 edit.
                metadata.put("source","CORRECTED");
            }
            db.update("session_details",metadata,"feedback_id=?",new String[]{String.valueOf(id)});
            if(timeChanged) try(Cursor s=db.rawQuery("SELECT remaining_override,remaining_anchor_feedback_id FROM learning_snapshots WHERE task_id=?",new String[]{String.valueOf(taskId)})) {
                if(s.moveToFirst() && !s.isNull(0) && id>s.getLong(1)) {
                    long revised=(long)s.getInt(0)+oldMinutes-(minutes==null?0:minutes);
                    ContentValues remaining=new ContentValues();remaining.put("remaining_override",(int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,revised)));
                    db.update("learning_snapshots",remaining,"task_id=?",new String[]{String.valueOf(taskId)});
                }
            }
            refreshPredictionsInTransaction(db,now);db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }

    private static String correctedSource(String previous,int revision,int oldMinutes,Integer minutes) {
        if(minutes==null) return "STATUS";
        // Keep the fact that time was missing even after later edits or reopening the app.
        if(oldMinutes<=0 || "STATUS".equals(previous) || "BACKFILLED".equals(previous)) return "BACKFILLED";
        if("MANUAL".equals(previous)) return "MANUAL";
        // V8/V8.1 overwrote both normal corrections and null-to-time fills with ADJUSTED.
        // Preserve this uncertainty; a subsequent edit must not restore evaluation eligibility.
        if("ADJUSTED".equals(previous) && revision>0) return "ADJUSTED";
        return "CORRECTED";
    }

    public void refreshPredictions(long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try { refreshPredictionsInTransaction(db, now); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }

    private void refreshPredictionsInTransaction(SQLiteDatabase db, long now) {
        List<DurationLearning.Sample> history = samples(db);
        for (Task task : new TaskDao(helper).getPendingTasks()) {
            if (hasFeedback(db, task.getTaskId())) continue;
            try (Cursor started = db.rawQuery("SELECT task_id FROM learning_snapshots WHERE task_id=?", new String[]{String.valueOf(task.getTaskId())})) {
                if (started.moveToFirst()) continue; // Prediction is frozen from first start, even before save.
            }
            DurationLearning.Prediction prediction = DurationLearning.predict(task.getTaskId(), task.getCategory(),
                    task.getTaskType(), task.getEstimatedDuration(), history, now);
            if (prediction.minutes <= 0) continue;
            ContentValues values = new ContentValues();
            // Preserve B/C's zero-means-no-model convention for a cold start.
            values.put("predicted_duration", prediction.sampleCount == 0 ? 0 : prediction.minutes);
            db.update("tasks", values, "task_id=?", new String[]{String.valueOf(task.getTaskId())});

        }
    }

    private List<DurationLearning.Sample> samples(SQLiteDatabase db) {
        List<DurationLearning.Sample> result = new ArrayList<>();
        // Train on complete known totals, but compare predictions only when every session has usable provenance.
        // ADJUSTED + revision>0 denotes an ambiguous pre-V8.2 correction; new known corrections use CORRECTED.
        String sql = "SELECT t.task_id, COALESCE(s.category,t.category), COALESCE(s.task_type,t.task_type), "
                + "COALESCE(s.estimate,t.estimated_duration), COALESCE(s.prediction,0), SUM(f.actual_duration), "
                + "MAX(f.completed_at), CASE WHEN s.completed_at IS NOT NULL AND MIN(CASE WHEN d.source IN ('STATUS','BACKFILLED','MANUAL') "
                + "OR (d.source='ADJUSTED' AND d.revision>0) THEN 0 ELSE 1 END)=1 THEN COALESCE(s.evaluable,0) ELSE 0 END "
                + "FROM tasks t JOIN task_feedback f ON f.task_id=t.task_id LEFT JOIN session_details d ON d.feedback_id=f.feedback_id "
                + "LEFT JOIN learning_snapshots s ON s.task_id=t.task_id WHERE t.status='COMPLETED' "
                + "AND COALESCE(s.scope_changed,0)=0 AND (s.task_id IS NULL OR (s.estimate=t.estimated_duration AND COALESCE(s.category,'')=COALESCE(t.category,'') "
                + "AND COALESCE(s.task_type,'')=COALESCE(t.task_type,''))) GROUP BY t.task_id "
                + "HAVING MIN(CASE WHEN f.actual_duration>0 AND f.completed_at>0 THEN 1 ELSE 0 END)=1";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) result.add(new DurationLearning.Sample(c.getInt(0), c.getString(1), c.getString(2),
                    c.getInt(3), c.getInt(4), c.getLong(5), c.getLong(6), c.getInt(7) == 1));
        }
        return result;
    }

    public static long actualMinutes(SQLiteDatabase db, int taskId) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(CASE WHEN actual_duration>0 THEN actual_duration ELSE 0 END),0) "
                + "FROM task_feedback WHERE task_id=?", new String[]{String.valueOf(taskId)})) {
            c.moveToFirst(); return c.getLong(0);
        }
    }

    public Map<Integer, Integer> remaining(List<Task> tasks, int focusMinutes) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Map<Integer, Integer> result = new HashMap<>();
        for (Task task : tasks) {
            Integer override = null;
            try (Cursor c = db.rawQuery("SELECT remaining_override FROM learning_snapshots WHERE task_id=?", new String[]{String.valueOf(task.getTaskId())})) {
                if (c.moveToFirst() && !c.isNull(0)) override = c.getInt(0);
            }
            int total = task.getPredictedDuration() > 0 ? task.getPredictedDuration() : task.getEstimatedDuration();
            result.put(task.getTaskId(), DurationLearning.remaining(total, actualMinutes(db, task.getTaskId()), override, focusMinutes));
        }
        return result;
    }

    /** Original estimates for the same completed, comparable tasks used by Insights MAE.
     * Home must never evaluate history against today's editable task estimate. */
    public Map<Integer, Integer> reviewEstimates(long now) {
        Map<Integer, Integer> result = new HashMap<>();
        for (DurationLearning.Sample sample : samples(helper.getReadableDatabase())) {
            if (sample.evaluable && sample.estimate > 0 && sample.prediction > 0
                    && sample.actual > 0 && sample.completedAt <= now) {
                result.put(sample.taskId, sample.estimate);
            }
        }
        return result;
    }

    public LearningSummary summary(long now) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<DurationLearning.Sample> history = samples(db);
        LearningSummary result = new LearningSummary();
        for (DurationLearning.Sample sample : history) {
            if (sample.estimate <= 0 || sample.actual <= 0 || sample.completedAt > now) continue;
            result.historyTasks++;
            if (!sample.evaluable || sample.prediction <= 0) continue;
            result.evaluatedTasks++;
            result.estimateMae += Math.abs(sample.estimate - (double) sample.actual);
            result.predictionMae += Math.abs(sample.prediction - (double) sample.actual);
            result.predictionBias += sample.prediction - (double) sample.actual;
        }
        if (result.evaluatedTasks > 0) {
            result.estimateMae /= result.evaluatedTasks; result.predictionMae /= result.evaluatedTasks;
            result.predictionBias /= result.evaluatedTasks;
        }
        // Deadline evidence is independent of missing, backfilled or corrected duration values.
        try (Cursor c = db.rawQuery("SELECT deadline,completed_at FROM learning_snapshots WHERE evaluable=1 AND deadline>0 AND completed_at>0 AND completed_at<=?", new String[]{String.valueOf(now)})) {
            while (c.moveToNext()) { result.deadlineTasks++; if (c.getLong(1) <= c.getLong(0)) result.onTimeTasks++; }
        }
        java.util.Calendar week = java.util.Calendar.getInstance(); week.setTimeInMillis(now);
        week.set(java.util.Calendar.HOUR_OF_DAY, 0); week.set(java.util.Calendar.MINUTE, 0);
        week.set(java.util.Calendar.SECOND, 0); week.set(java.util.Calendar.MILLISECOND, 0); week.add(java.util.Calendar.DAY_OF_MONTH, -6);
        try (Cursor c = db.rawQuery("SELECT COUNT(*), AVG(f.actual_difficulty-COALESCE(s.difficulty,t.difficulty)) "
                + "FROM task_feedback f JOIN tasks t ON t.task_id=f.task_id LEFT JOIN learning_snapshots s ON s.task_id=t.task_id "
                + "WHERE f.actual_difficulty BETWEEN 1 AND 5 AND COALESCE(s.difficulty,t.difficulty) BETWEEN 1 AND 5 "
                + "AND f.completed_at>=? AND f.completed_at<=?", new String[]{String.valueOf(week.getTimeInMillis()), String.valueOf(now)})) {
            if (c.moveToFirst()) { result.difficultyComparisons=c.getInt(0); result.difficultyDelta=c.getDouble(1); }
        }
        for (Task task : new TaskDao(helper).getPendingTasks()) {
            if (result.previews.size() >= 3) break;
            try (Cursor saved = db.rawQuery("SELECT s.estimate,s.prediction,s.scope_changed,s.category FROM learning_snapshots s JOIN tasks t ON t.task_id=s.task_id "
                    + "WHERE s.task_id=? AND t.status IN ('PENDING','IN_PROGRESS')", new String[]{String.valueOf(task.getTaskId())})) {
                if (saved.moveToFirst()) {
                    if (saved.getInt(0) > 0 && saved.getInt(1) > 0) {
                        LearningSummary.Preview preview=new LearningSummary.Preview(task.getTitle(),saved.getInt(0),saved.getInt(1),0,true);
                        preview.scopeChanged=saved.getInt(2)==1;preview.category=TaskTypes.categoryLabel(saved.getString(3));
                        preview.remaining=DurationLearning.remaining(task.getPredictedDuration()>0?task.getPredictedDuration():task.getEstimatedDuration(),actualMinutes(db,task.getTaskId()),readOverride(db,task.getTaskId()),focusMinutes());
                        result.previews.add(preview);
                    }
                    continue;
                }
            }
            if (actualMinutes(db, task.getTaskId()) > 0 || task.getEstimatedDuration() <= 0) continue;
            DurationLearning.Prediction p = DurationLearning.predict(task.getTaskId(), task.getCategory(), task.getTaskType(), task.getEstimatedDuration(), history, now);
            LearningSummary.Preview preview=new LearningSummary.Preview(task.getTitle(),task.getEstimatedDuration(),
                    task.getPredictedDuration()>0?task.getPredictedDuration():task.getEstimatedDuration(),p.sampleCount);
            preview.category=TaskTypes.categoryLabel(task.getCategory());
            preview.basis="Same task type".equals(p.scope)?preview.category+" / "+task.getTaskType():preview.category;
            result.previews.add(preview);
        }
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM task_feedback WHERE (actual_duration IS NULL OR actual_duration<=0) AND completed_at>=? AND completed_at<=?",
                new String[]{String.valueOf(week.getTimeInMillis()),String.valueOf(now)})) {c.moveToFirst();result.untimedRecords=c.getInt(0);}
        List<TimeOfDayStats.Entry> segments=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT s.feedback_id,s.started_at,s.ended_at FROM session_segments s JOIN session_details d ON d.feedback_id=s.feedback_id "
                + "WHERE d.timing_valid=1 AND s.ended_at>? AND s.started_at<?",new String[]{String.valueOf(week.getTimeInMillis()),String.valueOf(now)})) {
            while(c.moveToNext()) segments.add(new TimeOfDayStats.Entry(c.getLong(0),c.getLong(1),c.getLong(2)));
        }
        result.timeOfDay=TimeOfDayStats.calculate(segments,now,java.util.TimeZone.getDefault());
        return result;
    }
}
