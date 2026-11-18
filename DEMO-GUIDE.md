# Tempo 实习与晚课录屏操作稿

Integrated R5 · 2026 年 11 月 18 日 · 建议成片 6–8 分钟

这份操作稿用于演示“白天实习、晚上 19:00–22:00 上课”的一位学生如何安排周一：例会准备、实习报表、采购、晚课与课后复习。先在专用空白模拟器加载场景，再按镜头顺序录制。英文短句可直接作旁白，也可以用中文说明。

**最快开始：**打开 R5 工程并 Run → 将演示模拟器设为周一 2026-11-23 08:00 → Profile 长按 Your rhythm → Internship + evening classes → Load。正式录制前完成第二页的准备。

## 这版优化了什么

| 范围 | R5 录制要点 |
|---|---|
| Overview | 记录时长、七日图与类别放在同一页，总体完成率标注 All history |
| Estimates | 并排比较原估计和 Tempo 的平均误差，点击 How this is calculated 可展开 |
| Patterns | 查看七日记录、实际难度和计时时段，About these patterns 可查看截止完成情况 |
| 工程范围 | R5 改动 D 的 Insights 界面，沿用 R4 的 A、B、C 和演示场景 |

**交付是 Android Studio 源码工程。**核心 Java / SQLite 检查已通过 363 项；本环境没有 Android SDK，未构建 APK，需在你的 Android Studio Sync / Run 并走完文末设备复验。

## 录制主线

| 镜头 | 目标时长 | 展示内容 |
|---|---:|---|
| 1 | 30 秒 | Home 与角色背景 |
| 2 | 45 秒 | Profile 的专注规则和固定会议、晚课 |
| 3 | 60 秒 | 新建 Life 采购任务，看到建议用时和排程 |
| 4 | 60 秒 | 周一日程、固定安排定位、手动调时 |
| 5 | 60 秒 | 实习报表计时，暂停，回顾并保存完成 |
| 6 | 90 秒 | Insights 的记录、预测、MAE、难度与口径 |
| 7 | 40 秒 | History 更正原记录并核对更新 |
| 收尾 | 15 秒 | 本地规则排程与统计校准的边界 |

场景中的历史是模拟记录；正式片头或字幕写一句“Demo with simulated activity data”。屏幕上所有统计仍来自数据库和实际业务计算，不是写死的界面数字。

<!-- PAGEBREAK -->

## 1 录制前准备

### 打开优化工程

1. 解压 Tempo_Integrated_Final_R5.zip，在 Android Studio 打开里面的 Tempo 文件夹，确认这一层有 settings.gradle.kts。
2. 等待 Gradle Sync。使用原来能运行项目的 SDK 和 JDK 配置；本次未调整依赖或数据库版本。
3. 在 Device Manager 新建一个专用模拟器，命名 TempoDemo。沿用你已经安装并可正常使用的手机尺寸及 Android 系统镜像。
4. 顶部设备下拉框选择 TempoDemo，运行 app。默认 Debug 构建才有本稿的隐藏数据入口。

### 设置演示日期

在**模拟器里的 Android 系统设置**搜索 Date & time，关闭自动日期/时间，将时区设为香港或上海，将日期和时间设为 **2026-11-23，08:00**。这是周一。调整的是演示设备，不是电脑日期。

日期设置完成后再打开 Tempo。录制期间不要修改日期或时间。加载入口允许周一 07:00–08:25；主流程应在 08:30 前开始，例会准备在 09:00 截止。

### 一次加载场景

1. Home → Profile。若先看到早间问候，点 Start my day 再进入 Profile。
2. 等待 Your rhythm 页面加载；长按页面顶部 **Your rhythm**。
3. 在 Choose demo data 中选择 **Internship + evening classes**。
4. 阅读确认框后点 **Load**。应用会写入示例 Profile、4 个历史完成任务和 5 个待办，并调用现有自动排程。
5. 回到 Home → Schedule，确认周一日期是 23，页面有 **5 fixed**。
6. 在 Insights → Overview 核对初始数据，约 **3.3 h、4 saved sessions、44%（4 of 9）**。点 Estimates 查看 Upcoming estimates。报表的 **40 → 45 min** 也可从 Schedule 中点该任务查看。

### 正式录制前的状态

此时不要完成任务、补录或更正记录。正式片中只新增本稿指定的一个 Life 任务；其他输入值不要临时改动，后面的数字才能一致。

可以先试录熟悉按钮。重新开始整段录制时，在专用 TempoDemo 设备的 Tempo 应用信息中清除应用存储，重新设为周一 08:00，再加载同一场景。重新 Run 覆盖安装会保留数据，不会自动重置这套示例。

常见提示：加载失败若写着 existing tasks，说明安装并非空白；若提示 Monday 07:00–08:25，先检查模拟器时间；若没有长按菜单，确认运行的是 R5 的 Debug 构建。

<!-- PAGEBREAK -->

## 2 身份与 Profile 设置

角色是一位在白天实习、晚上上课的研究生。主片只演示周一；示例 Profile 为周一至周五预留晚课，正式个人使用时可改成真实课表。

### 日常参数

| Profile 字段 | 演示值 | 说明 |
|---|---|---|
| Wake up / Bedtime | 07:00 / 23:30 | 当日可安排范围 |
| Breakfast / Lunch / Dinner | 07:30 / 12:30 / 18:30 | 当前约束各按 30 分钟处理 |
| Best focus time | 10:30 | 用户指定的偏好；当前实现使用其后三小时 |
| Focus duration | 25 min | 自动分段的单次专注上限 |
| Break duration | 5 min | 专注段之间的休息 |
| Daily capacity | 4 hours | 可管理任务的总分钟上限，不是全天在岗时长 |

### 固定安排

| 重复日 | 时间 | 名称 |
|---|---|---|
| 周一至周五 | 08:00–08:30 | Commute to internship |
| 周一 | 09:00–09:30 | Weekly team meeting |
| 周一 | 09:30–10:30 | Internship · routine work |
| 周二至周五 | 09:00–10:30 | Internship · routine work |
| 周一至周五 | 13:00–18:00 | Internship · routine work |
| 周一至周五 | 19:00–22:00 | Evening class |

“Routine work”是无需逐项管理的实习在岗时间，内部也可包含休息。10:30–12:30 留给本次演示的报表和费用整理。会议与晚课是固定占用，不能把这些时段又当作可排任务时间。

固定安排本身不会增加 Recorded time；只有保存活动记录才增加。4 小时容量只约束另外管理的任务，不表示实习只工作 4 小时。

### 为什么有些个人任务已经在晚上

示例已把 Buy groceries 放在 18:00，把 Review lecture notes 放在 22:05，并标记为手动安排，表示用户自己选择的偏好。自动排程保留这些安排。

当前排程器没有“Work 只能在工作时间、Study 只能在晚上”的类别规则。录制时应说明是“固定占用＋用户手动选择＋自动安排剩余任务”，不要宣称它自动理解实习身份。

<!-- PAGEBREAK -->

## 3 任务数据与预期预测

前五项由场景入口准备，第六项在正式录制中创建。截止日期均为 2026-11-23。表中“难／重”依次表示 Difficulty / Importance。

| 任务名称 | Category / Type | 原估计 → Tempo | 截止 | 难／重 |
|---|---|---:|---|---|
| Prepare Monday team update | Work / Meeting preparation | 20 → 22 min | 09:00 | 2／5 |
| Weekly sales summary | Work / Report | 40 → 45 min | 12:30 | 3／4 |
| Update expense tracker | Work / Administration | 30 → 34 min | 18:00 | 2／3 |
| Review lecture notes | Study / Revision | 40 → 43 min | 23:30 | 3／3 |
| Buy groceries | Life / Errands | 20 → 21 min | 18:30 | 1／2 |
| Order household essentials | Life / Errands | 15 → 16 min | 18:30 | 1／2 |

建议把第六项的描述输入为：Order detergent, toothpaste and tissues online.

### 预置历史

| 保存日期与开始时刻 | 已完成任务 | 原估计／实际 | 预计／反馈难度 |
|---|---|---:|---:|
| 11 月 20 日 周五 10:30 | Last week's sales summary | 60／90 min | 3／4 |
| 11 月 21 日 周六 10:00 | Read the HCI paper | 40／50 min | 3／4 |
| 11 月 22 日 周日 14:00 | Review database notes | 30／35 min | 2／2 |
| 11 月 22 日 周日 17:00 | Weekend grocery run | 20／25 min | 1／1 |

历史合计 **200 分钟、4 条活动、4 个完成任务、3 个有记录的日期**。类别为 Work 90、Study 85、Life 25 分钟。历史样本和当前任务都通过实际的 SQLite、学习与排程接口生成。

### 预测为什么只调整一点

过去一次实习报表用了 90 分钟，原估计是 60 分钟。系统参考这个偏差，但通过小样本收缩和时间权重控制影响，因此新的 40 分钟估计变为 45 分钟，而不是直接乘成 60 分钟。

这里 Work 的历史样本只有一个，采用的是同类别校准；不是根据标题理解任务。达到三个同类型完成样本后，才优先使用该类型。新任务尚未开始时预测可以更新；开始计时后会保存该次预测用于后续比较。

<!-- PAGEBREAK -->

## 4 周一参考日程

以下是演示时钟保持在周一早上、已新增 Order household essentials，并按主片步骤锁定其 12:10 时段后的参考安排。生成器的核心结果已验证；页面会把同任务连续分段合并成一个显示块。

| 时间 | 活动 | 来源 |
|---|---|---|
| 07:30–08:00 | Breakfast | 用餐约束 |
| 08:00–08:30 | Commute to internship | 固定安排 |
| 08:30–08:52 | Prepare Monday team update | 自动安排 22 分钟 |
| 09:00–09:30 | Weekly team meeting | 固定安排 |
| 09:30–10:30 | Internship · routine work | 固定安排 |
| 10:30–10:55 | Weekly sales summary 第一段 | 自动安排 25 分钟 |
| 10:55–11:00 | Break | 5 分钟休息 |
| 11:00–11:20 | Weekly sales summary 第二段 | 自动安排 20 分钟 |
| 11:25–11:50 | Update expense tracker 第一段 | 自动安排 25 分钟 |
| 11:50–11:55 | Break | 5 分钟休息 |
| 11:55–12:04 | Update expense tracker 第二段 | 自动安排 9 分钟 |
| 12:10–12:26 | Order household essentials | 片中手动调整并锁定 |
| 12:30–13:00 | Lunch | 用餐约束 |
| 13:00–18:00 | Internship · routine work | 固定安排 |
| 18:00–18:21 | Buy groceries | 预先手动安排 21 分钟 |
| 18:30–19:00 | Dinner | 用餐约束 |
| 19:00–22:00 | Evening class | 固定安排 |
| 22:05–22:30 | Review lecture notes 第一段 | 手动安排 25 分钟 |
| 22:30–22:35 | Break | 5 分钟休息 |
| 22:35–22:53 | Review lecture notes 第二段 | 手动安排 18 分钟 |
| 23:30 | Bedtime | 作息边界 |

其余小段空白是缓冲或休息，不需要全部填满。用餐约束不一定绘制成固定安排卡片；不是缺数据。

**自动生成的新增采购时段最初是 12:09–12:25。**录制时把它改为 12:10–12:26，展示保留 16 分钟、只改变用户偏好时间的操作。

保存“完成报表”后，报表尚未开始的未来时段会释放，其他未开始任务可能重排或调整预测。此表用于保存之前的画面，不要求保存之后还一模一样。

<!-- PAGEBREAK -->

## 5 录屏镜头 1 至 3

### 镜头 1 Home 与角色背景

**操作：**从 Home 开始。若先进入 Yesterday，停留约 3 秒，点 Start my day。展示今日待办、下一项和底部导航，接着进入 Profile。

**中文说明：**“我白天实习，晚上七点到十点上课。Tempo 把固定安排、需要完成的任务和实际记录放在同一条使用流程里。”

**英文旁白：**“I work as an intern during the day and attend classes from seven to ten in the evening. Tempo helps me fit flexible tasks around those commitments.”

### 镜头 2 Profile 和固定安排

**操作：**展示 Focus 25、Break 5、Daily capacity 4 hours。向下找到 Fixed weekly commitments，停在 Weekly team meeting 和 Evening class。无需改值或重新保存，返回 Home → Schedule。

在日程页点击 **5 fixed**，选择 Weekly team meeting，显示 09:00–09:30；再点击 5 fixed → Evening class，显示 19:00–22:00。点底部 Today 回到当天时间附近，准备添加任务。

**中文说明：**“例会和晚课预先占用时间；普通任务在剩余空间中安排。专注超过二十五分钟会分段，中间预留休息。”

**英文旁白：**“My meeting and evening class are fixed. Flexible work is planned around them, with twenty-five-minute focus blocks and five-minute breaks.”

### 镜头 3 新建采购任务

点击 Schedule 底部 **+ Add**，按下表输入。键盘需要时可收起以显示保存按钮。

| 字段 | 输入值 |
|---|---|
| Task name | Order household essentials |
| Description | Order detergent, toothpaste and tissues online. |
| Category | Life |
| Task type | Errands |
| Estimated duration | 15 |
| Deadline | 2026-11-23 18:30 |
| Difficulty / Importance | 1 / 2 |

点保存，等待结果显示 Tempo 预计 **16 min** 和建议时段，再点 **Back to schedule**。若显示等待，刷新推荐并等它结束。此时完成率应为 **40%（4 of 10）**，记录仍为 **200 分钟、4 条活动**。

**英文旁白：**“I also need to order some household essentials. I estimate fifteen minutes. Tempo makes a small adjustment using my completed activities and finds an available slot.”

<!-- PAGEBREAK -->

## 6 录屏镜头 4 与 5

### 镜头 4 日程与手动调整

1. 在周一时间轴找到 Weekly sales summary，点开详情。展示 Estimate 40 min、Tempo predicts 45 min。
2. 展示 Time slots 中的 10:30–10:55 和 11:00–11:20，说明这两个分段属于同一任务。点 Close。
3. 找到 Order household essentials，点开详情，再点 **12:09–12:25 suggested**。
4. 在 Adjust time slot 中点 Start，用滚轮设 **12:10**；End 设 **12:26**。点 **Save time**。
5. 等待重排后再次打开该任务，确认该段标为 **adjusted**。随后关闭详情。

**英文旁白：**“The report is split into two focus blocks. I can also move a suggested slot myself. Once adjusted, that slot is kept when the plan is regenerated.”

同一任务被拆成两段，不是重复生成两次；预计工作 45 分钟，段间休息 5 分钟另算。这里仅修改时段，没有增加活动记录。

### 镜头 5 计时与保存完成

1. 再次打开 Weekly sales summary → **Start focus →**。如果计时页状态仍是 Ready，再点击底部 **Start focus**。
2. 等约 5 秒，点 **Pause**，停 2 秒，再点 **Resume**，运行约 5 秒。
3. 点击 **Finish and review**，进入 **Session review**。这时计时处于暂停状态。
4. 将 Minutes 改为 **50**。这是为了把演示压缩到几十秒，模拟一次实际用了 50 分钟的报表工作。
5. 选择 **Task completed · save and finish**。剩余时间不用填；展开可选难度区域，选择 **4**。
6. 点击 **Save session**，等待保存和排程完成。应提示完成并记录 50 分钟，返回日程。
7. 从左上角 Home 返回，再进入 Insights。

**英文旁白：**“For this short demo, I adjust the recorded duration to fifty minutes. I mark the task as completed and rate its difficulty. Saving updates the activity history, estimates and remaining plan.”

**字幕建议：**“Demo time compression · 50 minutes entered for illustration.”

保存后不能期待报表还占着未来两个时段：如果在计划开始前就完成，它们会取消。日程表头按当前显示的安排计数，分母可能减少；本步骤以 Home、Insights 和 History 的完成与活动记录核对结果。

<!-- PAGEBREAK -->

## 7 录屏镜头 6 与 7

### 镜头 6 Insights

打开 Insights，按顶部 **Overview → Estimates → Patterns** 的顺序录制。在每个页签按需向下滚动。

**Overview。** 先展示 **4.2 h、5 saved sessions、50%（5 of 10）**。七日图新增周一的记录。接着展示 Where time went，Work **2 h 20 m**、Study **1 h 25 m**、Life **25 m**，共 250 分钟，对应 56%、34%、10%。

**Estimates。** 在 Estimate accuracy 停一下，画面应显示 **5 evaluated tasks**、Your estimates **12.0 min**、Tempo estimates **10.6 min**。点 **How this is calculated**，讲解平均绝对误差后再收起。下方 Upcoming estimates 展示待办及进行中任务。

**Patterns。** 依次展示 **4 / 7** 天有记录、主观难度 **3.0 / 5**、高于预期 **+0.6**。When you worked 只统计有效计时区间，本次手动改为 50 分钟的记录只进入总时长。**About these patterns** 可展开截止日期统计。

**英文旁白：**“Insights separates saved activity from planned time. Both error figures use the same completed tasks and predictions saved before work began. This small simulated example illustrates the calculation; it does not establish a general improvement.”

不要说“AI 已证明预测更准”或“知道我晚上效率最高”。此处是可解释的统计校准；数字较低只是这组示例的结果。

### 镜头 7 更正同一条记录

1. 回到 **Overview**，在类别下方点 **History ↗**，或从 Home → History 进入。
2. 找到今天的 Weekly sales summary，进入更正界面。
3. 将分钟从 **50 改为 55**，保持难度 4，保存。
4. 确认仍然只有 **5 条记录**，总分钟变成 **255**。再次打开该条记录看到 55。
5. 返回 **Overview** 看 **4.3 h** 和 **50%**，再点 **Estimates** 看更新后的 **13.0 / 11.6 min**。

**英文旁白：**“If I entered the wrong duration, I can correct the original record. The totals and evaluation update without creating a duplicate session.”

**收尾旁白：**“Tempo combines local rule-based scheduling with statistical adjustments from saved feedback. The user remains in control of both the plan and the records.”

<!-- PAGEBREAK -->

## 8 录制时的数字核对表

各列按本稿顺序执行；额外完成任务、补录或改分钟都会改变这些数值。

| 指标 | 刚加载场景 | 新增采购后 | 保存报表 50 后 | 更正为 55 后 |
|---|---:|---:|---:|---:|
| 全部任务数 | 9 | 10 | 10 | 10 |
| 完成任务数 | 4 | 4 | 5 | 5 |
| Overall completion | 44% | 40% | 50% | 50% |
| Saved sessions | 4 | 4 | 5 | 5 |
| Recorded minutes | 200 | 200 | 250 | 255 |
| 界面小时显示 | 3.3 h | 3.3 h | 4.2 h | 4.3 h |
| 有记录的日期 | 3 | 3 | 4 | 4 |
| MAE 评价任务数 | 4 | 4 | 5 | 5 |
| Your estimates MAE | 12.5 min | 12.5 min | 12.0 min | 13.0 min |
| Tempo estimates MAE | 12.0 min | 12.0 min | 10.6 min | 11.6 min |
| 平均反馈难度 | 2.8 / 5 | 2.8 / 5 | 3.0 / 5 | 3.0 / 5 |
| 反馈减预计难度 | +0.5 | +0.5 | +0.6 | +0.6 |

### 需要回答时再展开的计算

前四个历史任务的用户估计绝对误差为 30、10、5、5，平均 12.5 分钟。系统的执行前预测对应误差为 30、10、3、5，平均 12.0 分钟。

新增报表原估计 40、保存的系统预测 45、实际输入 50，因此新增的两种误差分别为 10 和 5。汇总后用户 MAE 为 60÷5＝12.0，系统 MAE 为 53÷5＝10.6。更正为 55 后，对应 65÷5＝13.0 与 58÷5＝11.6。

### 不保存的备用镜头

主片时间够时，在未完成的 Update expense tracker 上开始计时约 10 秒 → **Finish and review** → **End without saving**。返回日程是正常结果。再进 Insights，保存条数、分钟、完成率和 MAE 都应保持上一列数值。因当前时间推进，日程的位置可以重新计算。

### 三分钟精简版

时间紧时，只保留：Home 背景 15 秒 → 固定会议与晚课 30 秒 → 40→45 分钟预测和拆段 30 秒 → 计时保存 45 秒 → Insights 45 秒 → History 与边界说明 15 秒。Life 任务在录制前创建并调时即可，数字依然按上表“新增采购后”开始。

<!-- PAGEBREAK -->

## 9 最后一次设备复验

### 拿到 R5 后先检查

- Sync 和 Run 成功，Home、Profile、Schedule、Insights、History 均能打开。
- Debug 长按入口能看到 Internship + evening classes；加载后重开，任务、固定安排和历史仍在。
- Schedule 的 5 fixed 能定位周一例会和晚课；任务没有压在固定安排上。
- 新增采购预测为 16 分钟；调至 12:10–12:26 后仍是 16 分钟，并标记 adjusted。
- 保存报表后只有一条新增记录；History 更正后条数不增加。
- Insights 三个页签均可切换。展开 Estimates 的 How this is calculated，切到 Patterns 再返回，核对展开状态与正文间距。

每项正常即可开始成片。核心检查不能代替这些设备操作；遇到红色排程提示先处理，避免把错误状态录进主片。

### R4 昨日回顾修复的独立复验

这一项放在录制完成之后或另一台测试设备上进行，不要混入主片数据。

1. Home → Yesterday，记下原准确度和提醒。
2. Schedule → Manage tasks → 找到已完成的 Review database notes → Edit task，将预计用时从 30 改为 35 并保存，保持已完成状态。
3. 再看 Yesterday 和 Insights。这个任务因工作范围改变而退出可比较样本；不能把 35 分钟实际耗时与新改的 35 比较成“完美估计”。
4. 记录时间和完成事实仍应保留。更改完成任务的原预计时间与更正活动实际分钟，是不同操作。

### 常见情况怎么判断

| 情况 | 判断与下一步 |
|---|---|
| 时间轴看不到晚课 | 点 5 fixed → Evening class 直接定位；时间轴可滚动，不是课程丢了 |
| 看见一个 45 分钟报表大块 | 显示层合并了分段；打开 Time slots 能看到 25＋20，休息另算 |
| 保存后报表从未来日程消失 | 提前完成释放未来占用；到 History / Insights 核对已保存记录 |
| 小时数看起来没变化 | 只有一位小数时小量变化可能被舍入；看 session 条数或 History 分钟 |
| MAE 与稿子不同 | 先核对是否多保存、补录、更正或加载到旧数据；按上表逐列定位 |
| 新增任务显示未排入 | 检查日期、截止、固定时段和容量；确认没有运行中的计时再刷新 |
| 重录想恢复初始状态 | 在专用 TempoDemo 安装清除存储，再设周一 08:00 并重新加载 |

交给组员时说明，R5 基于 R4，改动集中在 D 的 Insights 展示层。B/C 的任务、数据库和排程代码沿用 R4。演示数据来自本地模拟记录，所有指标均由应用计算。
