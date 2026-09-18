---
name: project-screencapture-fps-pts-pending
description: screencapture 的 fps/PTS 错配是已知待办；修完后须删除 CLAUDE.md 与 AGENTS.md 中的对应章节
metadata:
  type: project
---

`screencapture` 的 `ScreenCapture.Builder.setFps()` 只影响 PTS，不影响采集速率（采集循环
`delay(32.milliseconds)` 硬编码；`KEY_FRAME_RATE` 接的是 `keyFrameRate` 而非 `fps`）。
2026-09-18 真机实测：传 `fps = 5f` 时 PTS 比真实时间快 4.95 倍。

用户已明确表态（2026-09-18）：**这件事之后要做，但不是现在。** 未经用户确认不要动手，
也不要在改这块代码时把该现象当成新引入的回归。

**Why:** 改动会改变 `screencapture` 的 builder 语义并波及 `ScreenShareClientActivity`
等现有调用方，属于独立立项的工作，不能顺手带过。

**How to apply:** 详情已写入 `CLAUDE.md` 与 `AGENTS.md` 的「已知问题（待处理）」章节，
证据见 `00-documents/2026-09-18-record-single-app-screen-rotation-survival_cc.md` §9。
**这件事一旦做完，必须主动提醒用户从 `CLAUDE.md` 与 `AGENTS.md` 中删除这两处章节**，
并删除本条记忆。参见 [[feedback-sync-memory]]。
