# R4 本次改动清单

底稿：Tempo_Integrated_Final_R3(1).zip。以下只描述从 R3 到 R4 的新增改动，不把更早集成的队友贡献重复计入。

## 模块归属

| 文件或范围 | 归属 | 改动原因 |
|---|---|---|
| HomeActivity、DailyReview、Home HTML | A 与 D | 使用冻结的执行前原估计和 D 的合格样本；只评价当天完成的任务；缺项与评分性质在 UI 说明 |
| LearningStore.reviewEstimates | D | 直接复用现有 samples 查询，将相同资格的原估计提供给 Home，不复制另一套 SQL 筛选 |
| ProfileSettingsActivity | A 与演示支持 | Debug 长按菜单可选新场景；明确确认模拟数据与日期；数据已写入而排程失败时单独提示；释放临时 worker |
| InternDemoData | 演示支持 | 事务中建立 4 个历史完成任务、5 个待办、作息与命名固定时段；两项个人偏好为手动参考安排；禁止向已有或归档数据混入 |
| SampleData | A 演示支持 | 原通用场景也把归档任务视为已有数据 |
| 数据库、DAO、Task 模型、AddTask、排程器、Schedule 页 | B 与 C | 本次生产源码没有改动；新场景只调用已有持久化和排程接口 |
| 说明与测试 | 交接 | 更新过期版本说明、录屏操作稿、21 项 Home 回归和 27 项演示检查 |

数据库继续为 V4；Gradle、SDK、依赖和 Android versionCode 沿用 R3。隐藏场景入口只在 Debug 应用展示，正常启动不加载模拟数据。

## 已执行的验证

- python checks/run_checks.py：363 项核心 Java / 真实 SQLite 断言通过。
- 工程 5 个原有单元测试类的 19 个方法：用 JVM 断言适配器在 UTC 与 Asia/Hong_Kong 分别运行通过；不是 Gradle Android 测试任务。
- Java 编译器解析 77 个源码文件的语法；15 个 Android XML 解析通过；Home 内嵌 JavaScript 语法通过。
- 演示测试核对周一固定会议/晚课、25 分钟专注上限、全部任务可排入、手动锁定保留及 200→250→255 分钟、4→5→5 条活动的全过程。
- 当前环境无 Android SDK 与模拟器：没有声称 APK 构建、UI 点击、字体排版、键盘或覆盖安装已通过。请按 DEMO-GUIDE.md 的设备检查验收。

## 逐文件变化

- CHANGELOG-INTEGRATED.md
- DEMO-GUIDE.md
- README-C-integration.md
- README-D-Insights.md
- README.md
- app/src/main/assets/home/index.html
- app/src/main/java/com/tempo/app/HomeActivity.java
- app/src/main/java/com/tempo/app/ProfileSettingsActivity.java
- app/src/main/java/com/tempo/app/home/DailyReview.java
- app/src/main/java/com/tempo/app/home/InternDemoData.java
- app/src/main/java/com/tempo/app/home/SampleData.java
- app/src/main/java/com/tempo/app/learning/LearningStore.java
- app/src/test/java/com/tempo/app/DailyReviewUnitTest.java
- checks/DailyReviewHistoryCheck.java
- checks/InternDemoCheck.java
- checks/README.md
- checks/run_checks.py
- docs/superpowers/specs/2026-11-17-home-overview-design.md
- 页面功能设计与交接.md
- R4-CHANGE-SUMMARY.md（本文件）

## 底稿校验

R3 ZIP SHA-256：4557e2c0019dcdac4f2455d6b1a83937d1102f115006bae17a868009326071f7
