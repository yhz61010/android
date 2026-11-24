# 待提交修改复审与交付记录

日期：2026-11-24。基线：`review/native-modules@aa4653d5cc3f5e7a0fba06260a9765249f2778cd`。
用户授权重新审查此前修改、修复明确问题，完成后提交并推送；方案不明确处必须先询问。

## 范围

覆盖基线之后的全部待提交代码、测试、构建配置和文档，包括新增未跟踪文件。依据 11 月 6 日、
10 日的修复计划及 11 日 Claude 音频积压合并记录，对照实际代码重新核对，不把历史绿灯当作本轮验证。
`AGENTS.md` 保留本地修改，按仓库默认交付边界不纳入提交；Claude 配置、Native 二进制和工具链升级
stash 不变。M-12 回调线程架构继续保留为待证据与方案决策事项。

## 本次发现及修复目标

1. 编码回调先发生输出异常、随后在返回 native buffer 时与显式停止交错，外层 catch 会因为状态已是
   RELEASING 而丢弃异常，录音最终误报成功。同步 worker 存在同类窗口。实际处理失败必须在 codec
   操作锁内保存；停止仍可立即关闭输入并取消 worker，通知继续在锁外执行。实际协程取消不得误报失败。
2. GL 已取得上传请求并通过取消检查、尚未读取像素时，producer 可取消退出。调用方复用原数组后，
   GL 仍会读取它。公开提交入口应创建自有像素快照；取消仍及时返回，已经认领的绘制只读取快照。
3. 投影策略立即进入终态，但 Service 要等退休后的错误通知才关闭工作门禁。期间旋转/截图的正常
   拒绝会逃逸至主线程。Demo 应处理工作拒绝，保留退休后最终错误通知及既有清理所有权。
4. 截图队列过载是同步拒绝，Demo 未处理便会导致主线程崩溃。单次拒绝不能停止整个录制，也不能
   阻止队列恢复后继续截图。
5. Demo 请求关键帧直接借出 codec，只持有 Service 的锁，策略自行进行的异步退休不会经过该锁。
   现移除公开 `h26xEncoder` 借用入口，新增 `requestKeyFrame()`，在当前 EncoderSession 的锁内
   复查活动身份并执行原生请求，退休等待在途请求结束。非活动录制返回 false，请求异常交调用方；
   Demo 记录异常，单次关键帧请求失败不改变录制生命周期。KDoc 与 CHANGELOG 同步给出迁移。

音频保留 `markReleasing()` 的立即门禁与取消顺序，只改变已接受操作的错误保存位置：释放仍可先
进入 RELEASING，但 native 清理取得同一操作锁前能够看到失败快照。只由 RUNNING → FAILED 的
胜出者在锁外通知，释放拥有者直接读取稳定快照。异步捕获前再次检查 callback epoch，避免 hook
重入 flush 后的旧异常污染新会话；同步 worker 对真正协程取消保留独立分支。

GL 回归使用真实公开 View → renderer → coordinator → feedData，反射只在 prepare 之前插入调度
屏障；原实现实际得到 `[9, 9, 9, 9]`，修复后保留原始 Y/U/V 像素。Service 的三个工作拒绝用例
使用真实 Service 与受控处理器边界；关键帧回归使用真实 Service/策略和受控 codec，阻塞原生请求
后从策略侧独立发起释放，验证 native release 必须等请求退出。测试不模拟真实 GPU 或厂商驱动。

## 验证记录

新增回归先运行旧实现：两个音频用例分别观察到失败快照为 null、录音错误报告成功；GL 用例观察到
被调用方改写的像素；三个 Service 用例观察到工作拒绝异常逃逸。另一个真实关键帧请求用例观察到
native release 没有等待在途请求。七项均为预期行为断言失败，不是编译或测试基础设施异常。
日志：本机 `/tmp/android-review-nov24-red.log`、`/tmp/android-review-nov24-keyframe-red.log`。

首轮全仓检查期间新增了回归文件，部分检查读取到了新测试，不能作为最终提交快照的绿色证据；
首轮及中间定向检查被新代码/测试的长行、缩进和异常 catch 风格阻断，已按现有规则修正，没有放宽检查。

修复后执行四模块全单测、ktlint 和 detekt，exit 0，`BUILD SUCCESSFUL in 2m 28s`，610 个任务实际执行：

| 模块 | 测试套件 | 测试 | 失败/错误/跳过 |
| --- | ---: | ---: | ---: |
| audio | 13 | 105 | 0/0/0 |
| opengl | 3 | 10 | 0/0/0 |
| screencapture | 5 | 50 | 0/0/0 |
| demo | 12 | 79 | 0/0/0 |

合计 244 条测试。日志：本机 `/tmp/android-review-nov24-green2.log`。之后只修改交付文档和 CHANGELOG，
未再调整产品实现；最终全仓矩阵另行记录，不将不同轮次的测试重复相加。

最终全仓验证命令：

```bash
flock /tmp/android-review-gradle.lock ./gradlew \
  testDebugUnitTest :demo:testDevDebugUnitTest :demo:assembleDevDebug ktlintCheck detekt \
  :audio:lintDebug :androidbase:lintDebug :lib-common-android:lintDebug \
  :camera2live:lintDebug :opengl:lintDebug :screencapture:lintDebug \
  :lib-json:lintDebug :lib-compress:lintDebug :draw-on-screen:lintDebug :demo:lintDevDebug \
  --continue --rerun-tasks --max-workers=1 --console=plain
```

最终结果：exit 0，`BUILD SUCCESSFUL in 6m 29s`，2019 个任务全部实际执行。按本轮执行的 Test
任务读取 XML：23 个有测试的模块入口、104 个测试套件、551 条测试，零失败、错误或跳过；未把
NO-SOURCE 的模块计作通过了测试。根项目及 36 个子项目的 ktlintCheck/detekt 均实际执行通过，
九个库的 lintDebug、Demo 的 lintDevDebug 和 assembleDevDebug 通过。
日志为本机 `/tmp/android-review-nov24-final-matrix.log`，计数为
`/tmp/android-review-nov24-final-test-counts.json`。

保留 Gradle 现有弃用提示和编译警告，不把成功构建表述为零警告；例如未修改的
`SuspendTeardownGateTest.kt` 仍有 ExperimentalCoroutinesApi opt-in 提示。

产品修复后的第二轮静态复审未发现其它明确缺陷。文档事实、路径、代码围栏及 git diff --check
通过。提交使用核对后的 74 个文件显式清单，包括此前未提交修复和本次补充；AGENTS.md 的
既有修改继续留在本地，不进入该提交。提交与远端结果另以 Git 命令核对。

## 真机边界

本次 `adb devices -l` 未列出设备。未执行真机、仪器测试或真实媒体播放，不能证明 API 21～26
及 Android 14+ 的厂商 Codec/EGL、画面/声音、旋转、通知回跳或持续截图性能；仍需按仓库要求补齐
旧 API 与新设备验证。单测中的 framework/Native 边界替身仅证明本机控制流和资源所有权协议。
