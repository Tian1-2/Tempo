# C 排程模块接入说明

本目录是从组员 Android 工程复制到纯英文路径的开发副本。Android Studio 请打开 `D:\Projects\Android\Tempo\team-project`，不要继续打开包含 `HKU！\课程` 的旧路径。旧目录未被修改。

## 已接入

- `com.tempo.app.scheduler.ScheduleGenerator`：纯 Java 单日排程算法。
- `ScheduleModelAdapter.pendingTasks(...)`：将 B 的 `Task` 转为排程任务；`predictedDuration > 0` 时使用预测分钟数，否则使用 `estimatedDuration`；只取 `PENDING` / `IN_PROGRESS`。
- `ScheduleModelAdapter.lockedSessions(...)`：从已有 `ScheduleItem` 提取 `manuallyEdited && PLANNED` 的锁定时段。
- `ScheduleModelAdapter.newScheduleItems(...)`：只转换新生成的时段；锁定时段保留原数据库记录和 ID。

调用方仍需根据 `UserProfile`、固定日程和目标日期构造 `ScheduleGenerator.Request`。`UserProfile` 只给出三餐的时间点，没有用餐时长；固定日程也没有现成模型，因此 C 暂不猜测这些阻塞区间。请 A/B 确定后传入 `blockedIntervals`。`preferredStartTime` 也没有结束时间，高效窗口需 A/D 确定。

## 当前数据库联动

`ScheduleService.generateAndSave(date, fixedBusyIntervals)` 必须在后台线程调用。它读取数据库中的待办任务、用户档案和当天已有时段，运行排程算法，并通过 `ScheduleRepository.replaceGeneratedForDateSync` 在一个事务中替换当天自动生成且仍为 `PLANNED` 的时段。手动编辑及已完成记录保留。返回值含排好的时段及未排完任务。

调用前需要 `RepositoryProvider.init(context)`，数据库中需要有效的 `UserProfile`（起床、睡觉时间以及正数的专注时长和每日容量）。`fixedBusyIntervals` 由页面/固定日程模块传入；当前数据库没有固定日程表。三餐时长和高效窗口结束时间也尚无统一字段，服务不会自行猜测。当前 `TaskRepository.getPendingTasksSync()` 只查询 `PENDING`，尚不包含 `IN_PROGRESS`。

`MainActivity` 还没有“生成日程”按钮，也没有调用 `ScheduleService`。直接运行 App 只会执行组员原有的数据库插入测试。要在模拟器演示完整排程，还需要 A 提供页面入口、Profile 输入和固定日程数据。

## 日程页面预览

`SchedulePreviewActivity` 现为 Launcher，采用米白、深蓝、薄荷绿的简约样式。它读取当天数据库日程并按时间轴展示；数据库为空时显示三条可操作的示例任务（负数 ID，不写入数据库）。任务详情可开始、暂停、恢复及结束计时；运行状态保存在 `SharedPreferences`，离开页面后继续计时并可返回恢复。结束后先展示本次用时，用户确认才写入一条 `PARTIAL` 反馈；此计时流程不把任务标记为完成。真实任务的反馈保存使用现有 `TaskRepository.addFeedback`。

日程预览已改为顶部一周日期、下方按小时排列的纵向时间轴。点选日期会读取该日 `schedule_items`；任务块按真实起止时间定位，点击块进入安排详情。无任务数据时，仅今天显示 3 条明确标注的示例日程。

2026-11-14 页面调整：周日期和展示切换固定在上方，时间轴可独立上下滚动。点任务块先打开安排详情；可逐段修改开始与结束时间，保存后该段标为 `is_manually_edited=1`，重新生成时会保留。详情里的“开始专注”才跳转到独立的 `FocusTimerActivity`。数据库编辑会拒绝与已有日程重叠；固定日程尚无数据库记录，因此暂时无法校验与固定日程的冲突。

页面现改为逐段显示任务。`含休息` 会在相邻专注段之间画灰色休息小块；`仅任务` 隐藏休息小块。任务详情可查看任务属性，并直接编辑标题、描述、类别、类型、预估时长、难度、重要度、截止时间，调用 `TaskRepository.updateTask` 保存；系统预测时长仍由 D 模块负责。

2026-11-14 页面继续调整：时间轴覆盖 `00:00–24:00`，打开当天时滚动到当前时间附近，可上下滑动查看全天；当前时间使用红色时间标签与横线，并每分钟更新。底部固定三个紧凑入口“今天 / 计时 / 添加”。顶部“今日事项 已完成数/总数”统计该日有日程的不同任务，非今天显示“当日事项”；完成状态按 `Task.status=COMPLETED` 判定。`schedule-timeline-v4.png` 和 `schedule-created-v4.png` 是此前版本的模拟器截图。

2026-11-14 布局微调：去掉日期前后箭头，点日期切换当天，左右滑动日期栏切换周。年月移至顶部标题位置，左侧返回箭头打开新增的 `ProfileSettingsActivity`；该页使用现有 `UserProfileRepository` 读取和保存档案设置，并可返回日程。休息开关改为简洁文字标签。时间轴不再使用左右页面边距，铺满屏幕宽度。`schedule-timeline-v5.png`、`schedule-week-swipe-v5.png` 和 `profile-settings-v1.png` 是模拟器截图。

页面视觉调整：年月标题居中，选中日期用仅包围日期数字的薄荷绿圆形标记；日期栏与“今日事项”之间增加浅色细分界线。事项标题与完成数分开排版，完成数使用深绿色强调。`schedule-timeline-v6.png` 为模拟器截图。

添加与计时页面改版：底部“添加”打开独立的 `AddTaskActivity`，用任务内容卡片、类别选项及 1–5 评分选择器创建任务；底部固定保存操作。`FocusTimerActivity` 使用圆形计时区和固定底部操作，运行、暂停及恢复状态同步显示；结束用时确认改为自定义弹窗。任务写入和计时反馈仍走原有 Repository。`add-task-v2.png`、`focus-timer-v2.png`、`focus-timer-running-v2.png`、`focus-result-v2.png` 是此前版本的模拟器截图。

2026-11-15 任务到排程流程调整：`AddTaskActivity` 先填写名称、类别、预计时长、可选截止时间、难度和重要程度，通过 `TaskRepository.addTask` 只写入任务，**不创建手动日程**。重要程度表示任务对用户目标的影响，结合截止紧迫度参与优先级评分；难度表示完成负担，用于匹配高效时段。保存后按任务 ID 调用 `ScheduleRepository.getScheduleByTaskId` 展示已有推荐时段；尚无结果时显示等待状态及刷新入口。推荐时段由排程模块写入 `schedule_items`，本页面不生成推荐。时间轴任务详情展示任务条件与时段来源，编辑任务条件复用 `AddTaskActivity` 的编辑模式，调整具体时段使用可直接输入的 `HH:mm` 字段，并仍调用 `updatePlannedTime` 锁定手动修改。接入排程生成方时，请在任务保存后触发其生成流程，并把生成后的 `schedule_items` 写入数据库；页面刷新后即可展示结果。`add-task-flow-v3.png`、`recommendation-pending-v3.png`、`schedule-detail-flow-v3.png`、`schedule-time-edit-v3.png` 为模拟器截图。

后续展示调整：推荐页直接显示任务预计完成用时（优先 `predicted_duration`，否则 `estimated_duration`）与当前已安排分钟数；计时页圆环按“已保存反馈的累计用时＋本次计时”相对于预计完成用时推进，达到预计时形成闭环，超时后显示超出的分钟数。各页面左上角统一使用 `BackChevronView` 绘制返回图标，避免字体符号基线不一致。

日程时间轴只展示已有 `schedule_items` 的任务时段，不再单独列出“待安排任务”。新建任务保存后若排程结果尚未写入，推荐页继续显示等待状态。日程、新建任务、用户档案和计时页的返回图标已统一靠左；图标旁文字统一为较浅的 14sp，并与图标垂直居中。

## 2026-11-14 排程规则与页面接口

- 优先级评分：`100 / (1 + 距截止小时数 / 6) + importance * 12`。无截止时间的紧迫度为 0；已过截止时间按 0 小时计，分配阶段仍会拒绝超过截止时间的时段。分数相同按截止时间、任务 ID 排序。
- `ScheduleConstraintsProvider.getForDate(date)` 返回 `DayConstraints(fixedBusyIntervals, productiveWindows)`。两种列表均使用目标日内的绝对毫秒时间戳，起止对齐到整分钟。B 可从固定日程表提供前者；A/D 可从用户偏好或学习结果提供后者。服务可调用 `generateAndSave(date, provider)`。
- `ScheduleDisplayBlocks.group(sessions, fixedBusyIntervals, breakMinutes)` 将同一任务中仅隔休息的相邻分段合成显示块；跨固定日程的分段仍分开。`Block.taskMinutes` 是任务时间，`Block.breakMinutes` 是段间休息时间。Home 默认用 `shownMinutes(true)` 显示“任务＋休息”，切换按钮用 `shownMinutes(false)` 显示“仅任务”。底层 `ScheduleItem` 仍按真实任务分段保存，不把休息误存成任务。
- 当前日程页已实现日期、计时和添加入口。数据库尚未有固定日程或完整高效窗口字段，需要 B/D 按上述接口补数据来源。

已通过 `:app:assembleDebug` 和 `:app:testDebugUnitTest`。单元测试覆盖任务模型转换、预测时长选用、锁定时段保留及排程结果转换；模拟器上已验证任务写入、推荐等待页、日程详情及编辑页面。
