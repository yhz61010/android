# 分支复审问题修复计划

**目标：** 修复 `739928440` 上确认的六项代码问题，纠正两项文档事实并复核本轮变更。

**依据：** 2026-11-05 相对 `master@97c84d242` 的审查，以及本轮当前源码复核。用户已授权方案明确时直接实施；不提交或推送。

**架构：** 保留既有单会话、单 owner 和释放屏障。编码失败沿 encoder、wrapper、recorder 传递；UI 在主线程执行副作用前核验会话；失败的网络注销保留句柄；录屏错误通知晚于释放屏障。

**技术栈：** Kotlin、协程、MediaCodec/AudioRecord/MediaPlayer、JUnit、Robolectric、MockK。

## 全局约束与重点交错

- 最低 API 21；公开 API 变化写 KDoc 和 CHANGELOG，不保留兼容入口。
- 保留所有已有修改；不修改 Claude 配置、工具链版本或 Native 二进制。
- 未明确的产品语义先询问；测试不代替真机声音、厂商 Codec 或网络行为验证。
- 编码回调可能在 recorder 构造期间失败：清理须等待初始化结束，不能访问未初始化字段。
- 编码失败与显式停止并发：只释放一次，失败不得被成功停止掩盖。
- 注销失败后连续重启：不得覆盖旧句柄或累积 framework 注册。
- 错误通知内等待释放：资源和屏障均已完成，不能等待通知自身退出。
- UI 任务排队期间新会话启动、Cipher prepare 期间 Activity 销毁：旧任务不得改动新会话，局部资源仍须释放。

## 实施步骤

### 1. 编码失败与录音终态

文件：`audio` 中 AAC/OPUS encoder、encoder wrapper、`AudioEncoderManager`、`MicRecorder` 及就近回归。

- [x] 为真实 wrapper→recorder 路径注入输出写入异常；断言 `onStop(false)`、AudioRecord 停止/释放、codec 释放均一次，先确认旧代码失败。
- [x] 给 encoder/wrapper/manager 增加必填失败回调；MicRecorder 异步认领停止，初始化完成后等待录音 worker 退出再释放编码器。
- [x] 源码核对构造期间早到失败的屏障与回滚；回归覆盖 pre-start 失败、停止并发、重复回调、通知延迟和 PCM 取消；运行 `:audio:testDebugUnitTest :audio:ktlintCheck :audio:detekt --rerun-tasks`。

### 2. 网络注销重试

文件：`NetworkMonitor.kt`、`NetworkMonitorLifecycleTest.kt`。

- [x] 先验证第二次 stop 重试、连续 restart 保留失败句柄、旧事件不能污染新会话。
- [x] 注销成功才清字段；仍有旧注册时不发布另一个 listener；定向测试红转绿。

### 3. 录屏释放屏障

文件：`Screenshot2H26xStrategy.kt`、对应测试。

- [x] 将 `onError` 内等待 `releaseAndJoin()` 的超时探针迁入正式回归并确认失败。
- [x] 完成资源清理和释放屏障后再通知一次错误，覆盖正常 job 和未运行 job 路径。

### 4. Demo 会话与 Cipher 生命周期

文件：`AudioActivity.kt`、`RecordingIntentCoordinator.kt`、`AudioCipherActivity.kt`、必要的内部资源交接类与测试。

- [x] 先覆盖旧录音失败 UI 延后、新播放替换旧播放器、Cipher prepare 期间销毁。
- [x] 主线程实际更新按钮时再次校验代际/身份；Cipher 阻塞工作保留 IO，未移交资源用 finally 释放。
- [x] 运行 Demo 单测、质量检查和 `:demo:assembleDevDebug`。

### 5. 文档和最终复核

- [x] 更正 `AGENTS.md` 和审查文档的 staticCheck 覆盖说明；不把历史成功改写成全仓验证通过。
- [x] 更正 Native 整改计划 JavaCPP AAR 验收为检查发布元数据、classifier 依赖与消费端加载。
- [x] 更新 KDoc、CHANGELOG、实施记录，清除上轮七处文档空白问题。
- [x] 重跑受影响模块组合检查和全仓单测；等价修复已知基线质量错误，重新审查完整本轮 diff。

## 验证边界

本轮不执行真机测试，不宣称实际声音、API 21～26 厂商行为、sanitizer 或 16 KB 页设备加载通过。实际命令与结果在实施记录中追加。

实施结果和各次验证的实际覆盖见 `../2026-11-06-review-findings-remediation.md`；构造中的通知交错仅作源码审查，未伪称自动化覆盖。
