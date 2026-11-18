"""Run core Java/SQLite checks with a JDK and Python; no Android SDK required."""
from pathlib import Path
import subprocess,sys,tempfile,shutil
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'app/src/main/java/com/tempo/app'
def run(command):subprocess.run([str(v) for v in command],cwd=ROOT,check=True)
java=shutil.which('java')
if not java:raise SystemExit('A JDK with java and jdk.compiler is required.')
with tempfile.TemporaryDirectory(prefix='tempo-check-') as out:
    production=list((BASE/'model').glob('*.java'))
    production += [BASE/p for p in ['insights/InsightsStats.java','insights/TimeOfDayStats.java','planning/GenerationPreflight.java','learning/DurationLearning.java','learning/LearningSummary.java','learning/TaskTypes.java','learning/SessionTiming.java','learning/LearningStore.java','scheduler/ScheduleGenerator.java','scheduler/ScheduleModelAdapter.java','ui/TimelineScale.java','database/AppDatabaseHelper.java','database/CategoryDao.java','database/FixedCommitmentDao.java','ui/Categories.java','database/TaskDao.java','database/TaskFeedbackDao.java','database/ScheduleDao.java','database/UserProfileDao.java']]
    production += [BASE/'planning/ScheduleEditRules.java', BASE/'scheduler/FixedBlocks.java', BASE/'scheduler/ScheduleDisplayBlocks.java']
    production += list((BASE/'repository').glob('*.java'))
    production += [BASE/p for p in ['home/DailyReview.java','home/InternDemoData.java','scheduler/ScheduleConstraintsProvider.java','scheduler/DefaultScheduleConstraints.java','scheduler/ScheduleService.java']]
    files=production+list((ROOT/'checks/jvm').rglob('*.java'))+list((ROOT/'checks').glob('*Check.java'))
    run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-source','11','-target','11','-Xlint:-options','-d',out,*files])
    for name in ['InsightsStatsCheck','GenerationEntryCheck','DurationLearningCheck','TimelineScaleCheck','SessionTimingCheck','WorkflowCheck','EvaluationEligibilityCheck','ScheduleEditRulesCheck','IntegratedReleaseCheck','DailyReviewHistoryCheck','InternDemoCheck','FixedScheduleCheck','CategoryCheck']:
        run([java,'-Dpython='+sys.executable,'-Dsqlite.bridge='+str(ROOT/'checks/jvm/sqlite_bridge.py'),'-cp',out,name])
run([sys.executable,'checks/check_learning_sql.py'])
