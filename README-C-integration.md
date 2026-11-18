# Tempo 当前排程接口

Integrated R4，2026-11-18。本文替代早期“只排今天、固定日程未接入”的交接说明。

## 生产入口

RepositoryProvider 初始化后，AutoScheduler.replan 在后台调用 ScheduleService.generateRange，覆盖今天及随后六天。新建或编辑任务、Profile 保存、反馈保存、更正和日程页面恢复均可能触发重排。运行中的计时暂缓重排；暂停计时不等同于正在运行。

ScheduleGenerator 是纯 Java 单日算法；ScheduleService 协调跨日剩余量，避免同一工作重复分配。generateAndSave 单日接口保留，主要 UI 使用七日入口。

## 约束和优先级

- 安排未归档的 PENDING / IN_PROGRESS 任务，采用 LearningStore.remaining 提供的分钟。
- 作息、固定周安排、手动锁定、截止日期、专注上限、休息和每日任务容量参与约束。
- DefaultScheduleConstraints 将每餐视为 30 分钟、偏好开始时间起三小时视为高效窗口。与手动锁定重叠的用餐块会跳过。固定周安排来自 user_profile.fixed_blocks。
- 高难度优先尝试高效窗口；无法在截止前容纳时回退其他可用时间。优先级结合截止紧迫度和重要程度。
- 不按 Work / Study / Life 限制可用时间；不能宣称自动识别“上班时只能安排工作”。用户可用固定安排和手动移时表达偏好。
- 过去的自动计划不消耗未来剩余量。当前手动块只计未来部分；其他日期的未来安排会扣除，防止重复。
- 七日事务中任一天出现锁定冲突都会回滚整批替换，避免半周计划丢失。

## 时间轴和手动调整

SchedulePreviewActivity 显示任务块和固定时段。“N fixed”定位会议或课程。连续同任务分段按休息合并显示；底层仍保存真实专注段，休息不当作实际工作时间。

点击任务 → Time slots · tap to adjust → 点具体时段 → 滚轮选择时间 → Save time。页面当前是同一日期移时。ScheduleEditRules 做页面预检查，ScheduleDao 做数据库验证。保存后锁定该段，重排保留。增加预留工作量时需要确认新的剩余量。

完成时未来块取消；归档保留历史，永久删除清理关联记录。固定会议和课程不会自动成为活动反馈。已提前取消的未来完成任务可能不再出现在当前日程分母，Home / Insights 的完成统计有独立含义。

## 模块边界和测试

R4 未修改 C 的生产源文件或 B 的数据库源文件。新增 Debug 演示调用现有 API，预先手动放置采购和课后复习，其余工作任务由排程器计算。

checks/run_checks.py 包含生产七日服务的集成检查；InternDemoCheck 使用显式演示时钟、真实数据库和生产单日算法验证指南中的周一场景。它不替代 Android UI 验证。
