# Tempo 整合修订日志 · 2026-11-18

## R5 · Insights 三页签

Overview 把七日用时、总体完成率、周图和类别集中到一页。Estimates 并排显示 MAE，计算说明可展开。Patterns 汇总记录天数、难度和时段，其展开项包含截止日期统计。界面复用 R4 原有配色，主绿 #347E5B、薄荷绿 #C2E8D5、浅绿 #E9F5EE。只有 D 的 Insights 展示层有生产变更。录屏路径见 DEMO-GUIDE.md 第 7 节。

## R4 · 历史估计口径与实习晚课演示

以 Tempo_Integrated_Final_R3(1).zip 为底稿。Home 的昨日准确度改用 LearningStore 提供的执行前原估计，与 Insights MAE 共享资格筛选；只比较当日完成的合格任务。范围变化、手工补录、缺时后补填等不再产生虚假的“spot on”。无证据时不显示准确度组件，评分说明明确缺项重分配和非生产力评分。

Profile 的 Debug 长按入口新增 Internship + evening classes；显式确认后在空安装生成 4 个历史任务、5 个待办和每周固定安排。只接受周一 07:00–08:25，推荐 2026-11-23 08:00。采购和课后复习是手动参考安排，其余工作由现有排程器生成。所有活动为模拟数据，通过实际 DAO 和 LearningStore 保存；没有硬编码 Insights 数字。归档任务也阻止加载，整套新场景在一个事务中写入。

本次触及 A Home/Profile、D LearningStore、演示与检查文档；B/C 生产源文件、数据库 V4 和依赖版本未改。完整差异见 R4-CHANGE-SUMMARY.md。已通过 363 项核心 Java / SQLite 断言，其中新增 21 项 Home 回归和 27 项演示场景检查；没有 Android SDK，尚需 Android Studio 构建及设备验收。

当前操作和接口以 README-D-Insights.md、README-C-integration.md、页面功能设计与交接.md 为准。以下 R3/R2 及更早日志保留当时事实，不代表本次修改范围或当前文档口径。

## R3 · Insights 计算细节按钮间距

D 模块 · InsightsActivity.java。在 How close were the estimates? 卡片中，说明文字与 Show calculation details 按钮原本直接相邻。两者之间现留出 16 dp 垂直间距，展开后的 Hide calculation details 按钮也使用同一间距。

## R3 · 固定安排配色

按确认的预览调整 A 模块 Profile、C 模块 Schedule 及共享 Ui。两页的固定安排使用 #EFF5F2 浅底和 4 dp 的 #347E5B 左边线。日程卡第一行是会议名，第二行显示时间和绿色 Fixed 标签，顶部定位按钮使用同一主题绿。普通学习任务沿用薄荷绿整卡，方便在相邻时间段中区分。R2 的功能和数据库版本沿用。此修订覆盖下文 R2 历史日志中的紫色样式说明。


## R2 · 模拟器反馈后的修订（2026-11-18）

本次基于用户已跑过的 `Tempo_Integrated_Final(1).zip`，原包 SHA-256 为 `ada297356c2e04bff9e82d9da37e2d68cf2df0d298a011de1a158d10171b842b`。下面的归属按小组原分工标记。旧版记录和初次整合的日志保留在本节之后。

| 使用场景 | 改动和行为 | 模块与主要文件 |
|---|---|---|
| 今天有两项任务，提前完成其中一项后，首页从 0/2 变成 0/1。 | 首页按反馈发生日期统计已完成的不同任务，再加上今日计划中尚待完成的任务。示例现在为 1/2 done，1 task to go。提前完成会释放后面的时段，提前完成的同一项只计一次。全部做完后，空列表会显示今日已无更多排程。 | A 首页，D 完成反馈，C 计划。DailyReview、HomeActivity、home/index.html。 |
| 首页和 Yesterday 把手动补录的分钟称为 focused。 | 两处时间总计均显示 recorded。Yesterday 评分中的时间栏与说明也改用 recorded time。计时和手动补录均计入记录时间，Insights 中按真实计时间隔统计工作时段的规则继续适用。 | A 首页与回顾、D 统计口径。DailyReview、home/index.html。 |
| 剩余 30 分钟，手动修改后，跨日锁定的未来时段合计达到 60 分钟。 | 保存时在同一数据库事务里检查所有日期。只计算尚未过去的锁定分钟，排除正在修改的旧值。超过剩余量时显示两个具体数字；用户可以返回改短时段，也可以按 Use 60 min，同时确认时段和新剩余估计。这个动作本身不会添加活动记录。确认前若反馈或时段已经变化，重新核对数值。后续补录使旧锁定量超出剩余时，重排会保留已有计划并指出具体任务和差额。 | C 手动调整和排程，B 持久化，D 剩余量。SchedulePreviewActivity、ScheduleDao、ScheduleRepository、ScheduleEditRules、ScheduleService、LearningStore。 |
| 昨天计划 60 分钟、做了 30 分钟，今天归档后，昨天的计划变成零，昨日评分也消失。 | 归档保留已过去的计划，释放未来时段；进行中的时段保留到归档时刻，按分钟显示。历史计划保留为只读，和实际工作记录分别统计。回看过去日期可以看到 Past plan；恢复任务时按剩余工作重新安排。永久删除仍清除关联历史。 | B 任务归档，A 昨日回顾，C 时间轴。TaskDao、TaskRepository、ScheduleItem、DailyReview、SchedulePreviewActivity、ScheduleService。 |
| 每周会议只在时间轴上方出现一行小字，看起来该时段仍然有空。 | Schedule 在实际开始和结束时间间绘制淡紫底、紫边的色块，并写明 Fixed、名称和时间。即使当天只有固定安排也能看到。靠得很近的任务与休息不会把固定安排合并进去。保存新作息后若旧计划因冲突而保留，重叠的固定安排与旧任务分栏显示。顶部的固定安排按钮可直接定位一项，或在多项中选择。固定安排不计入任务数。 | A Profile，C 排程界面。FixedBlocks、SchedulePreviewActivity、Ui。 |
| Manage tasks 像普通标题。Profile 的 Daily task time、固定安排卡片、删除按钮和添加入口挤在一起。 | Manage tasks 使用整块深色按钮并提供点击反馈。Profile 的固定安排卡与前一张卡间隔 22 dp，各条目间增加留白，Remove 有独立点击区域。两页复用同一组颜色（底色 #F2EBFA、边框 #C3ADD8、文字 #533A73）。可选名称用于标识会议或课程；点已有条目的文字可改名、星期或时间，Done 后点 Save changes／Save routine 持久保存。 | A Profile、C 日程、共享 UI。ProfileSettingsActivity、SchedulePreviewActivity、FixedBlocks、Ui。 |

**兼容范围。** 数据库继续使用 V4。旧格式 `Thu 09:00-10:00` 会显示为 Fixed commitment，可直接改名。对于旧版本归档时已经统一标成 CANCELLED 的时段，数据里缺少取消原因，本次没有推测或补造历史。新的归档操作按本节规则保留原计划。

**验证。** 工程自带检查可用 `python checks/run_checks.py` 重跑。本次通过 315 条断言，其中 28 条是新增的整合回归；覆盖提前完成、历史与未来锁、跨日合计、调整时有新反馈、确认与取消、后续记录、归档与恢复、旧固定安排、命名与重叠校验、短时段和午夜边界。另在 JVM 上分别以香港时区和 UTC 执行了工程的 19 个单元测试方法，并检查首页实际 HTML 对提前完成、全部做完和空日程三种状态的输出。当前环境缺少 Android SDK 和 Gradle 9.5.0 缓存，构建依赖下载受限，本次修改后的 APK 尚待 Android Studio 编译及模拟器目视复验。

**模拟器快速复核**

1. 今天排两项任务，提前完成第一项并保存 15 分钟。回首页看 1/2 done、1 task to go 和 15 min recorded；Schedule 应已释放这项任务的未来时段。
2. 在 Profile 添加一个名为 Team sync 的固定时段并保存，在对应星期点固定安排按钮。核对紫色卡片的名称、起止时间、Profile 条目之间的留白，以及 Manage tasks 的点击效果。
3. 在尚余 30 分钟的任务上将未来锁定量改到 60 分钟。先选 Adjust slot 检查旧值仍在，再确认 Use 60 min；核对 Insights 的剩余量和未来工作分钟。
4. 对有昨日计划与部分记录的任务归档。回看 Yesterday 和昨日时间轴，核对原计划与实际记录。再看未来日期确认该任务已腾出时段。



本包从团队的 `Tempo_integrated.zip` 修改。压缩包内的 `Tempo/` 是完整 Android Studio 工程，包含 Gradle Wrapper、应用源码、资源和检查脚本。原包 SHA-256 为 `c6b866d260b5eaa0e10fe1ac426bfd05804d5182d892254832763ee429e569f0`。本次从同学的 `Tempo_Optimization.zip` 引入已经完成的优化，再修复两份整合实现里影响实际使用的问题。下表的 A/B/C/D 指相关模块和建议复核人，沿用团队原先分工。

## 一、用户能感受到什么变化

| 用户场景或复现 | 本次处理与原因 | 涉及模块和主要文件 |
|---|---|---|
| ① 任务估计 120 分钟，过去有一段手动排定的 30 分钟，用户另存了 30 分钟实际投入。Insights 剩余 90 分钟，后续计划却只有 60 分钟。 | 剩余工作根据反馈计算。排程只预留尚未过去的手动工作量；已结束的时段仅作时间线记录。进行中的手动时段只预留未来部分。当天容量扣除真实已记录分钟。测试覆盖未来合计 90 分钟，连续生成两次仍是 90。 | C 排程、D 反馈与剩余量、B 持久化。ScheduleService、ScheduleModelAdapter。 |
| ② 用户先固定明天的 60 分钟，后天已有 30 分钟，再将每日容量降到 0.5 小时。刷新后原计划有一部分消失。 | 在同一数据库事务内清理旧自动时段并生成七天。遇到与手动时段冲突的日期就回滚整批排程，提示具体日期。Profile 的新设置和已保存的任务、反馈独立保留。用户可以继续调整容量或手动时段后重试。 | C 排程事务；A Profile 与日程提示；B SQLite。ScheduleService、ProfileSettingsActivity、SchedulePreviewActivity。 |
| ③ 高难度任务需 60 分钟，中午 12:00 截止，偏好 11:45 开始。上午明明可用，只在临近截止时排了一小段。 | 先看偏好时段能否装下任务和内部休息。放不下时查找截止前更早的连续空档，再尝试拆分。设置了课程或会议的时间同样排除。实际可用容量不足时仍允许只排一部分。 | C 算法、A 偏好输入。ScheduleGenerator、DefaultScheduleConstraints。 |
| 没有截止日期的任务次日又出现；点 Use recommended routine，再改两项和保存，今天也出现了任务。 | 保存 Profile 后立即用剩余工作、作息和每日容量重排今天起的七天。无截止时间的未完成任务可以跨天；任务名跨天重复时，需核对分钟总数。首次空日程提供新增入口。示例数据仅在调试版显式加载后写入。 | A Profile、B 任务入口、C 自动排程。ProfileSettingsActivity、SchedulePreviewActivity、AutoScheduler。 |
| 用户不想再做一项任务，但已投入的时间仍然有意义；另一个用户希望彻底清掉测试任务。 | 提供 Archive task、Restore task 和二次确认的 Delete permanently。归档释放计划、保留事实；恢复沿用原任务状态和反馈，由当前 Profile 对剩余工作重新排程；永久删除同时移除关联数据。旧编辑页不能再向已归档任务写新反馈。详见下表。 | B 主责，A/C 操作入口与计时保护，D 历史和学习。TaskDao、TaskRepository、LearningStore、InsightsRepository、SchedulePreviewActivity。 |
| 新建任务暂时排不上，用户找不到它，无法调整或删除。 | 日程页 Manage tasks 可查看 Unscheduled、Scheduled、Completed 和 Archived，支持新建、编辑、归档、恢复或永久删除。未能排入日程的任务仍然可找回。 | B 任务查询、C 日程入口。TaskDao、Task、TaskRepository、SchedulePreviewActivity、AddTaskActivity。 |
| 固定课、例会每周重复，用户不希望每次重新生成都重新挪开任务。 | Profile 的 Fixed weekly commitments 支持星期和起止时间，可添加、移除并随 Profile 保存。自动生成和手动移时均检查占用，日程页列出选中日期的固定时段。适用每周同日、当日内的时间段。 | A Profile、B 档案持久化、C 约束与时间编辑。UserProfile、UserProfileDao、FixedBlocks、DefaultScheduleConstraints、ScheduleDao。 |
| 昨天只做了一部分，今天完成，Home 却把昨天也算完成；未记时任务可能让估计显得准确。 | Home 回顾按当日结束前保存的完成反馈统计。耗时不完整时排除该任务的估计准确度，样本不足时给出缺数据提示。只有可比较样本全部准确才显示 “spot on”。活动任务列表过滤已归档任务与已取消时段。 | A Home、D 统计定义。DailyReview、HomeActivity、home/index.html。 |
| History 最多列 200 条，同周保存更多活动后，顶部总数和分钟可能与 Insights 对不上。 | History 在同一数据库快照中分别读取最近 200 条明细和全库聚合，七日分钟与 Insights 使用同一本地日历边界。归档任务的历史可继续按原记录查看和更正。覆盖 201 条周内记录加 1 条边界外记录。 | D History/Insights、B 数据查询。SessionHistoryActivity、LearningStore、LearningRepository、InsightsStats。 |
| 修改历史分钟后，剩余工作和日程需要同步；排程冲突又容易让人误以为反馈没保存而重复提交。 | 保存会话、手工补录和修改原记录后触发重排，保存结果与重排失败分别反馈。更正沿用同一个 feedback ID。原本未记时、事后补记的分钟进入时间统计，其预测误差资格仍受原有来源规则保护。 | D Feedback/History、C 自动排程。TaskFeedbackActivity、SessionHistoryActivity、AutoScheduler、insights.xml。 |

### 来自队友优化分支的工作

本包纳入了自动重排入口、跨任务休息、连续工作块合并展示、截止时间和手动调时滚轮、未排入时的反馈及重试、Profile 推荐值的显式保存、按小时设置容量、运行中和暂停计时的区分、跨天计时回顾。这些是队友已有贡献。本次另行调整了过去时段的扣减、整周回滚、偏好与截止时间、任务归档和历史统计，详细位置见上表。整合底稿已具有的 Overnight / Evening 标签、固定的执行前预测和事后补填时间的误差资格规则沿用。

## 二、删除和统计的产品决定

| 操作 | 后续日程 | History / Insights | 对预测的影响 |
|---|---|---|---|
| 归档未完成任务 | 取消它尚为 PLANNED 的时段，退出待办 | 保留已记录分钟；未完成的归档任务移出总体完成率分母 | 未完成任务不成为已完成学习样本 |
| 归档已完成任务 | 继续保持完成状态 | 保留已完成事实、反馈、分钟和符合统计条件的指标 | 原先合格的样本继续参与学习与评估 |
| 恢复归档 | 未完成任务按剩余量重新参与排程；已完成任务保持完成 | 沿用原任务、原记录，不重复累计 | 继续使用原有预测快照 |
| 永久删除 | 同一事务清理该任务及关联时段、反馈和学习明细 | 同时移出 History、Insight 统计和对应的当日容量占用 | 移除对应样本，刷新尚未冻结的预测；其他任务已经冻结的执行前预测保留 |

例如，用户取消一门课但想记住自己读过半小时，可以归档；用户误建任务、希望这半小时彻底移出记录，可以永久删除。对当前任务仍有未结束的专注会话时，界面要求先处理会话。验证一项已完成任务后，归档时 History 和 Insights 均保留 30 分钟；永久删除时这项任务的记录和学习样本同时清除。

## 三、数据库交接

数据库由 V3 升到 V4。新增 `tasks.archived`，使“是否在日常列表显示”独立于原本的完成状态。新增 `user_profile.fixed_blocks`，旧档案默认没有固定周时段。升级保留原有任务、反馈、预测快照与档案，不依赖清库。

队友的优化版曾用 `DELETED` 覆盖任务状态。升级时把这些行设为归档，并依据仍在的反馈恢复状态：有完成反馈恢复为已完成；有其他反馈恢复为进行中；无反馈按待办处理。旧版已经永久丢失的记录无法通过迁移重建。V2 先执行旧有迁移，再追加 V4 字段。两条路径均使用历史表结构和真实 SQLite 测试，Android 覆盖安装仍需设备验收。

## 四、已执行的检查

| 检查 | 结果与范围 |
|---|---|
| `python checks/run_checks.py` | 287 项断言通过。其中 33 项专测本次整合，调用生产排程服务、仓储和 DAO，通过 JVM 适配器在真实 SQLite 上运行。其余覆盖 Insights、预测、剩余量、计时、时间线、事后校正、排程入口和迁移等。 |
| 工程已有单元测试 | 五个测试类共 19 个测试方法，在 UTC 和 Asia/Hong_Kong 分别运行通过。通过 JVM 直接调用这些方法；Gradle 的 Android 测试任务仍待运行。 |
| 静态与封包检查 | Java 源文件语法、Android XML 解析、Home 内嵌 JavaScript 语法，以及 ZIP 解压完整性。解压后的工程再次运行核心检查。 |
| Android 编译和设备交互 | 该环境没有 Android SDK、模拟器和缓存的 Gradle 9.5.0，Wrapper 下载受到网络限制。此次未产出 APK。用户先前提供的模拟器截图属于旧版；本日志不把它们计为新包验收。 |

### 组员最短设备复验路径

1. 解压，在 Android Studio 打开含 `settings.gradle.kts` 的 `Tempo/`，Sync 后运行 `:app:assembleDebug` 和 `:app:testDebugUnitTest`。在测试模拟器 Run，确认导航、Profile 和 Manage tasks 可用。
2. 在装有已知版本、签名匹配且有测试记录的模拟器上直接覆盖安装，核对旧任务、反馈和 Profile。V2 和 V3 各需要独立来源；记不清的来源记为“未知”。不要卸载或清数据来模拟升级成功。
3. 测 120 分钟任务。在今天建一段已过去的 30 分钟手动时段，同任务另存 30 分钟部分完成。容量和截止时间允许时，核对 Insights 剩余 90，向后七天同任务的未来工作量合计 90；休息另算。重新进入日程再查一次。
4. 测容量冲突。明天手动固定 60 分钟，记下后天的一段 30 分钟安排，将 Profile 的每日容量改为 0.5 小时。核对提示的冲突日期、原计划保留；调高容量后再查。另设高难度 60 分钟任务在中午截止、偏好 11:45，确认空闲的上午可用并避开固定课程。
5. 用同一条 30 分钟完成记录，依次归档、恢复、永久删除。逐步查看 History、Insights 和学习样本。再试一项未完成任务，恢复后其剩余工作能重新排入。
6. 在横屏和键盘打开时测试更正原记录，保存后返回日程，退出重进核对结果。测试 Profile 固定周时段保存、重开和手动调时冲突。最新工程的屏幕布局、输入法和覆盖安装以此次设备结果为准。

## 五、功能边界

自动排程覆盖今天及随后六天。容量、固定时段、手动锁定或截止时间可能使部分任务暂时排不上。无截止日期不会自动把任务限制到创建当天。“Best focus time”来自用户偏好；实际计时时段反映用户何时工作，不足以推断何时最高效。固定安排当前支持手输的每周时段；外部日历同步及可自定义颜色的任务标签均不在本次修复内。

## 六、相对于整合底稿的逐文件索引

以下列出内容有差异的文件及新增文件。上文的场景表说明修改原因与建议复核人。原有交接截图保留为历史材料。
- 新增 `CHANGELOG-INTEGRATED.md`
- 修改 `DEMO-GUIDE.md`
- 修改 `README-C-integration.md`
- 修改 `README-D-Insights.md`
- 修改 `app/src/main/assets/home/index.html`
- 修改 `app/src/main/java/com/tempo/app/AddTaskActivity.java`
- 新增 `app/src/main/java/com/tempo/app/FocusSessionState.java`
- 修改 `app/src/main/java/com/tempo/app/FocusTimerActivity.java`
- 修改 `app/src/main/java/com/tempo/app/HomeActivity.java`
- 修改 `app/src/main/java/com/tempo/app/ProfileSettingsActivity.java`
- 修改 `app/src/main/java/com/tempo/app/SchedulePreviewActivity.java`
- 修改 `app/src/main/java/com/tempo/app/SessionHistoryActivity.java`
- 修改 `app/src/main/java/com/tempo/app/TaskFeedbackActivity.java`
- 新增 `app/src/main/java/com/tempo/app/WheelPickers.java`
- 修改 `app/src/main/java/com/tempo/app/database/AppDatabaseHelper.java`
- 修改 `app/src/main/java/com/tempo/app/database/ScheduleDao.java`
- 修改 `app/src/main/java/com/tempo/app/database/TaskDao.java`
- 修改 `app/src/main/java/com/tempo/app/database/UserProfileDao.java`
- 修改 `app/src/main/java/com/tempo/app/home/DailyReview.java`
- 修改 `app/src/main/java/com/tempo/app/insights/InsightsRepository.java`
- 修改 `app/src/main/java/com/tempo/app/insights/InsightsStats.java`
- 修改 `app/src/main/java/com/tempo/app/learning/LearningRepository.java`
- 修改 `app/src/main/java/com/tempo/app/learning/LearningStore.java`
- 修改 `app/src/main/java/com/tempo/app/model/Task.java`
- 修改 `app/src/main/java/com/tempo/app/model/UserProfile.java`
- 修改 `app/src/main/java/com/tempo/app/repository/TaskRepository.java`
- 修改 `app/src/main/java/com/tempo/app/scheduler/AutoScheduler.java`
- 修改 `app/src/main/java/com/tempo/app/scheduler/DefaultScheduleConstraints.java`
- 新增 `app/src/main/java/com/tempo/app/scheduler/FixedBlocks.java`
- 修改 `app/src/main/java/com/tempo/app/scheduler/ScheduleGenerator.java`
- 修改 `app/src/main/java/com/tempo/app/scheduler/ScheduleModelAdapter.java`
- 修改 `app/src/main/java/com/tempo/app/scheduler/ScheduleService.java`
- 修改 `app/src/main/res/values/insights.xml`
- 修改 `app/src/main/res/values/themes.xml`
- 修改 `app/src/test/java/com/tempo/app/DailyReviewUnitTest.java`
- 修改 `app/src/test/java/com/tempo/app/DefaultScheduleConstraintsUnitTest.java`
- 修改 `app/src/test/java/com/tempo/app/ScheduleIntegrationUnitTest.java`
- 新增 `checks/IntegratedReleaseCheck.java`
- 修改 `checks/WorkflowCheck.java`
- 修改 `checks/check_learning_sql.py`
- 新增 `checks/fixtures/schema-v2.sql`
- 新增 `checks/fixtures/schema-v3.sql`
- 修改 `checks/jvm/android/content/Context.java`
- 修改 `checks/jvm/android/database/sqlite/SQLiteDatabase.java`
- 新增 `checks/jvm/android/os/Handler.java`
- 新增 `checks/jvm/android/os/Looper.java`
- 修改 `checks/run_checks.py`
- 修改 `页面功能设计与交接.md`

另为 Unix 环境的 Gradle Wrapper 入口 `gradlew` 设置了可执行权限。
