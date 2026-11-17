# Tempo — D 模块 Demo 操作稿

## 展示目标

证明反馈改变了后续估计、后续估计进入排程，而且系统能保留执行前预测并评估误差。手工填写的分钟数均明确说明为模拟输入；不把模拟结果当真实长期效率改善。

## 准备一个独立演示案例

建议使用独立测试模拟器/独立数据副本，不清除原验收设备数据。下面精确数字要求同类别没有其他完整学习历史。

1. 设置覆盖演示当天剩余时间的作息，专注 25 分钟、休息 5 分钟、每日容量至少 180 分钟。
2. 新建 Work 任务 `Prepare a presentation`，预估 30 分钟，Task type 可选 Report，截止时间留空。
3. 生成日程、开始计时、结束后进入 Session review。把本次分钟数改为 60，选择 Task completed，保存。说明这是模拟反馈。
4. 新建 Work 任务 `Draft a project report`，预估 30 分钟，类型保持一致。
5. Insights 顶部附近的预测卡应显示 30 → 约 38 min。只有 1 个同类型样本时仍会使用类别回退，文案显示真实依据。

不要改用 Log activity 来准备需要展示 MAE 的样本：事后手动补录不会被当作执行前预测评估。修改计时反馈的模拟分钟数可演示误差计算，但会排除出实际计时时段分布。

## 现场主线（约 2–3 分钟，先彩排）

1. Insights：展示新任务的用户预估、Tempo 预测和样本依据。
2. 日程：生成，展示新任务总计约 38 分钟，通常分为 25 + 13，休息另计。已完成任务不再保留未执行的未来占用。
3. 反馈：开始新任务，结束后把本次分钟数改为模拟的 40 分钟，选择完成并保存。
4. Insights：查看学习历史、预测误差；展开 calculation details 查看 MAE / signed bias。

如果两个任务都在首次记录前启动过新流程计时，且没有别的符合条件样本：

- 用户 MAE：(30 + 10) / 2 = 20 分钟。
- Tempo MAE：(30 + 2) / 2 = 16 分钟。
- Tempo signed bias 约为 −16 分钟。

页面展示两项任务的平均误差，不是只展示第二项。其他历史、时间衰减或不同输入可能改变结果，以页面实际值为准。

## 可选短展示

- 连续部分完成：先填剩余 12，再记录 5 且留空，剩余为 7。
- Log activity：选择 Time not recorded 后完成任务，不增加记录分钟。
- Activity history：修改一次分钟数，记录条数不增加，原预测不改变。
- When you worked：使用一个未调整分钟数的新计时 session 展示运行区间；暂停不计入。它展示活动分布，不声称识别高效时段。

## 英文讲述口径

> Tempo calibrates future duration estimates from completed tasks, plans the remaining work, and shows transparent feedback on recorded time, difficulty and completion.

> This simulated example demonstrates the learning loop and the evaluation method. It does not establish a long-term productivity improvement.

## 演示前复验

使用团队最终合并版本，确认作息覆盖现场时间、入口完整、可滚动、数据升级成功，以及反馈保存后能返回日程。D 的主要路径已在用户模拟器验收；最终合并工程仍需覆盖 Run 和一次完整彩排，不能直接把独立模块的验收结果视为整个 App 已通过。详细测试边界见 README-D-Insights.md。
