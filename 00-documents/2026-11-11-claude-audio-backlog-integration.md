# Claude 音频积压修复的对照审查与合并记录

日期：2026-11-11。用户要求先获取远端更新，确认是否被本地修复覆盖，审查正确后合并；不明确处先询问。

## 范围与结论

获取 origin 后，当前分支仅落后一个提交：`739928440` → `aa4653d5c`，标题为
`perf: shed the audio backlog instead of replacing the codec session`。新增提交涉及四个音频源码文件、
CHANGELOG 和 `2026-10-26-branch-review-part2-audio-netty_cc.md`。

| 项目 | 本地原有修复是否覆盖 | 审查及处置 |
| --- | --- | --- |
| M-11：OPUS 丢帧分支不应重建 codec | 未覆盖；原本仍调用异步 flush | 接受：该 flush 会退役并重新创建 codec，而重同步只需削减积压。现改为清待解码输入并保留原生会话 |
| M-13：输入积压应与 PCM 积压一起清除 | 未覆盖；原本未清 decoder 输入 | 接受：两个 decoder 增加 clearPendingInput；OPUS 同时清 PCM 队列，AAC 保留原有同步 flush |
| M-12：异步 codec 回调的 Looper | 本地未改，远端也未实现 | 远端仅记录待决策建议。保留现有线程架构，不把本次合并解释为批准自有 HandlerThread 或屏障方案 |

本机 SDK36 的 MediaCodec 构造器源码确实优先选当前 Looper、缺省时选主 Looper；这只是平台静态依据，
不是本项目真实调用线程或性能的设备日志。本轮未部署设备，也未修改 M-12 的线程架构。

## 合并与保留

开始前已归档 69 个修改/未跟踪文件，并保存二进制 diff、索引状态和内容哈希。临时保存后将分支快进
到 `aa4653d5c`，再恢复本地工作；没有新建普通代码提交，没有推送。

恢复时只有 `2026-10-26-branch-review-part2-audio-netty_cc.md` 发生内容冲突：两方都追加了第 8 节。
保留远端第 8 节及其内部引用，将本地“2026-11-06 分支复审修复”完整保留为附录；原有本地正文订正
也保留。CHANGELOG 自动三方合并成功。其余原有文件与开始前内容哈希一致。

安全 stash `2f3577fc69a707ef0c34cf0f835a30c9d30deae5` 与本机备份仍保留；版本升级 stash 未修改，
应继续按其原哈希定位，不依赖会变化的 stash 序号。

## 合并后的补充

远端没有新增回归。本地补充 `audio/src/test/kotlin/com/leovp/audio/StreamPlayerResyncTest.kt` 六项测试：

- AAC/OPUS 的真实输入队列清空后仍接受新帧；OPUS 随后仍可正常请求 EOS。
- AAC 满队列及延迟超限两条分支都清输入，并保留一次同步 native flush。
- OPUS 满队列及延迟超限两条分支都清输入与 PCM 队列，不 flush、不重建 codec，之后继续接受帧。

测试使用真实 decoder 状态转换、队列和播放器公开收流入口，Native MediaCodec 为边界替身，
AudioTrack 为 Robolectric 实现。受控 worker 调度避免队列在断言前被后台消耗；不证明真实解码、声音或耗时。
临时禁用清队列并恢复 OPUS flush 后，六项均产生预期的积压断言失败；finally 逐字节恢复产品源码。
随后对恢复后的实现运行完整绿色验证。红灯日志：本机 `/tmp/audio-claude-resync-red-nov11.log`。

两个新 API 的 KDoc 补准在飞边界：已被 input callback 取走的帧仍可能提交，codec 内的工作也不受影响；
OPUS 清队列必须与 EOS 请求串行，不能在请求 EOS 后清掉标记。没有改变远端实现的运行逻辑。

## 本机验证

使用 JDK17、仓库 wrapper 与共享 Gradle 锁，执行：

```bash
flock /tmp/android-review-gradle.lock ./gradlew \
  :audio:testDebugUnitTest :audio:ktlintCheck :audio:detekt :audio:lintDebug \
  :demo:testDevDebugUnitTest :demo:assembleDevDebug :demo:ktlintCheck :demo:detekt \
  :demo:lintDevDebug --continue --rerun-tasks --max-workers=1 --console=plain
```

结果：exit 0，`BUILD SUCCESSFUL in 2m 48s`，1198 个任务实际执行。XML 核对 audio 13 套件、103 条测试，
Demo 12 套件、75 条测试，均无失败、错误或跳过。两模块质量检查、标准 lint 和 Demo 集成编译通过。
日志：本机 `/tmp/audio-claude-integration-final-nov11.log`。

最后检查无未解决冲突、无暂存内容、git diff --check 通过；HEAD 与获取到的 origin 分支均为
`aa4653d5c`。本地已有修复、文档及新增测试仍未提交。

## 验证边界

Claude 原文的“未编译、未测试”是其提交时的静态记录；本机执行结果以本节为准，不把历史行号、
旧测试清单视为当前工作树清单。尚未做弱网、真实延迟收敛、听感或回调 Looper 的设备验证；不将
源码中的成本推断或“低成本”描述解释为已测得的时间。M-12 仍需实际线程证据及后续明确方案决策。
