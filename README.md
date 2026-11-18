# Tempo Integrated R5

Android Studio 打开本目录，Sync 后 Run app。正常升级保持同一签名并覆盖安装；演示数据使用专用空白模拟器。当前源码未在本环境构建 APK。

R5 将 D 的 Insights 改为 **Overview / Estimates / Patterns**，沿用 R4 配色及数据口径。A/B/C 生产源码与 R4 相同。

- 录屏逐步操作、Profile、任务、参考日程与数字核对：[DEMO-GUIDE.md](DEMO-GUIDE.md)
- R4 改动与 A/B/C/D 边界：[R4-CHANGE-SUMMARY.md](R4-CHANGE-SUMMARY.md)
- 当前全部页面：[页面功能设计与交接.md](页面功能设计与交接.md)
- 当前 D 统计与反馈：[README-D-Insights.md](README-D-Insights.md)
- 当前 C 排程接口：[README-C-integration.md](README-C-integration.md)
- 自动检查：[checks/README.md](checks/README.md)
- 按版本的历史修改：[CHANGELOG-INTEGRATED.md](CHANGELOG-INTEGRATED.md)

演示入口：Debug app → Profile → 长按 Your rhythm → Internship + evening classes。要求空安装及周一 07:00–08:25，建议模拟器 2026-11-23 08:00。场景含模拟历史与手动参考安排；统计仍走应用真实计算。
