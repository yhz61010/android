# 分支复审问题修复记录（2026-11-06）

## 范围与约束

基线为 `review/native-modules@739928440`。依据 2026-11-05 相对 `master@97c84d242` 的复审，
修复六项代码问题、两项文档问题，并等价修正复审中列出的既有质量错误和七处文档空白。
用户已授权方案明确时直接修改；本轮不提交、不推送，不恢复工具链升级 stash，不修改 Native 二进制。

计划见 `plans/2026-11-06-review-findings-remediation.md`。

## 1. 录音编码失败

- `AacEncoder` / `OpusEncoder` 增加必填 `errorCallback`；两个 wrapper 与 encoder manager 通过
  必填 `onFailure` 把 codec 和输出回调异常传到 `MicRecorder`。
- recorder 在回调入口记录失败，再在 IO 协程等待初始化屏障；正常构造完成后才允许触碰 AudioRecord。
  构造异常时取消普通失败任务，原有不可取消回滚继续负责 encoder 释放。
- 失败清理复用单次 stop 所有权：先唤醒 AudioRecord.read，等待录音 worker 退出，再释放音效、
  AudioRecord 和 encoder；最终仅通知一次 `onStop(false)`。错误回调不在 codec 锁内等待清理。
- 独立复核发现「codec 已选中 FAILED、锁外通知尚未到达」的窗口。新增回归延迟通知，再并发显式停止，
  旧实现错误报告成功。现在 `transitionToCodecFailure(error)` 在 codec 锁内同时保存终态原因；
  native release 必经同一锁。`AudioEncoderWrapper.failure` 暴露稳定快照，recorder 在 encoder
  release 后读取，因此不依赖通知的调度顺序，也不必等待通知结束。
- 公开 API 的新参数、wrapper 快照属性及 protected helper 迁移均记录到 KDoc / CHANGELOG；
  自定义 wrapper 须实现 `failure`，预编译调用方须重编译，无兼容入口。
- 继续复核补出 PCM 输出取消路径：worker 仍需完成清理再重抛取消；显式 stop 已认领所有权时，
  worker 的预期取消交由该 owner 释放，不应误报录音失败。

回归覆盖真实 encoder→wrapper→recorder 路径的 AAC/OPUS 输出异常、构造返回后且采集启动前失败、
重复错误、与显式停止并发及延迟 owner 通知；另以正反对照区分 PCM 输出自行取消与显式停止的预期取消。构造中的早到通知由源码所有权和初始化屏障核对，
不把 pre-start 用例误写成构造中的交错验证。

## 2. 网络注销

`NetworkMonitor` 仅在 framework 注销成功后清空 Wi-Fi callback；后续 stop/start 会重试失败句柄。
旧注册仍未清理时不创建新 Wi-Fi listener，ping/traffic 新会话继续运行。旧 listener 的 generation
已经失效，迟到事件不能写入新会话。测试覆盖第二次停止重试、连续重启不累积注册、恢复后的清理和旧事件隔离。

## 3. 录屏释放屏障

从录制协程 finally 移除错误通知，改为 job 完成处理器在资源已释放、屏障完成、dispatcher 关闭后通知。
未运行 job 的 orphan 分支继续在 owner 线程实际释放后打开屏障和通知；错误 listener 的异常仍隔离。
正式回归验证 onError 内同步等待 releaseAndJoin 不再等自身，并覆盖初始化/启动/输出失败、排队取消、
主动停止不误报、回调抛异常与只通知一次。

## 4. Demo 会话与 Cipher

- 录音失败返回「失败后的停止 generation」。主线程实际更新按钮或提示前重查该代际，覆盖 onStop、
  启动 catch 和权限拒绝；旧任务不再影响新会话。
- AAC/OPUS 的完成、错误、启动 catch 与 PCM finally 按 player 身份守卫整个 UI 副作用。
  PCM worker 只向本次局部 player 写入。
- Cipher 由 Main 协程持有尚未发布的 MediaPlayer；解密、文件写入和 prepare 放在 IO，返回 Main
  后检查取消，再 start/publish。准备期间销毁会阻止交接，并在同步 prepare 返回后释放局部 player。
  输入/输出流 use 关闭，临时文件 finally 删除；正常已发布实例由 onDestroy 摘除并释放。
- 新增真实 Activity 路径测试，包含旧任务隔离与当前任务正常生效的正向对照，以及 Cipher 三类失败释放。
- 输出流关闭原先先显式 flush 再 close；磁盘错误使 flush 再次抛出时会跳过 close。两条真实
  BufferedOutputStream 回归分别覆盖缓冲写回失败和底层 flush 失败，旧实现均未关闭底层流。
  现仅调用 close，由其完成 flush 和关闭；失败日志和 UI 会话语义不变。

## 5. 文档与质量说明

- `staticCheck` 的 ktlint/detekt 依赖只指向根项目，正则漏掉普通库 testDebugUnitTest/lintDebug
  主入口；匹配到的 flavor debug、lintVitalAnalyzeDebug 等及传递依赖会执行部分 lint，仍不等于
  完整库单测、标准 lint 或模块质量检查。本轮仅纠正文档，不扩大聚合脚本行为。
  历史运行成功记录保留，但撤销「证明全仓质量通过」的推论；M-9 中相同的错误推论也已更正。
- JavaCPP 发布验收改为检查真实 `.module` / POM、android-arm64 classifier JAR 和消费端资源，
  不要求本模块 AAR 内嵌传递 Native 库。
- `lib-json` 仅把先捕获 Exception 再判断取消，等价改为独立的 CancellationException catch；
  空串/null 降级及取消继续抛出的契约不变。`lib-compress` 仅调整测试表达式换行；`draw-on-screen` 仅调整注释表述和换行。
- 清理三份历史文档中的四处空白行尾空格和三处多余 EOF 空行，不改写历史设备结果。

## 6. 验证

初始正式红灯：网络 2 项预期断言失败、录屏错误通知屏障超时、Demo 首批 6 项会话/销毁失败、
录音首批 3 项未停止，以及通知延迟窗口的成功误报。修复后分别执行模块级测试和质量检查。

全仓组合命令：

```bash
./gradlew testDebugUnitTest :demo:testDevDebugUnitTest :demo:assembleDevDebug \
  ktlintCheck detekt --continue --rerun-tasks --max-workers=1 --console=plain
```

2026-11-06 结果为 `BUILD SUCCESSFUL in 4m 5s`，1433 个任务全部实际执行；93 个 suite 合计
452 条单测，零失败、错误或跳过。这次覆盖普通库测试和全仓模块质量检查，未用 staticCheck 代替。

后续的输出关闭修复重新执行 Demo 全单测、ktlint/detekt，521 个任务实际执行，46 条测试通过。
随后执行受影响模块的标准 Android lint 和集成检查：

```bash
./gradlew :audio:testDebugUnitTest :audio:ktlintCheck :audio:detekt :demo:assembleDevDebug \
  :audio:lintDebug :androidbase:lintDebug :screencapture:lintDebug :demo:lintDevDebug \
  --continue --rerun-tasks --max-workers=1 --console=plain
```

结果 `BUILD SUCCESSFUL in 2m 59s`，1185 个任务全部实际执行。该次尚未包含最后的正常停止取消对照。
新增对照先确认原补丁误报失败，再收紧取消分支：stop 已认领所有权时交由该 owner 清理，其他取消先清理再传播。
最终重跑：

```bash
./gradlew :audio:testDebugUnitTest :audio:ktlintCheck :audio:detekt :audio:lintDebug \
  :demo:assembleDevDebug --continue --rerun-tasks --max-workers=1 --console=plain
```

结果 `BUILD SUCCESSFUL in 1m 1s`，723 个任务全部实际执行；audio 共 91 条单测通过，
其中本轮 `MicRecorderEncoderFailureTest` 为 7 条。XML 汇总为 93 个 suite、456 条测试、零失败、错误或跳过；
这是首次全仓 452 条与随后受影响模块重跑后的合计，不表述为最后又执行了一次全仓 456 条。

最后的 KDoc 和注释修订另行重跑 audio / draw-on-screen 的 ktlint、detekt，通过，14 个任务实际执行。

Gradle 使用 JDK 17，并以 `flock /tmp/android-review-gradle.lock` 串行运行，日志保存在本机 `/tmp`。
上述构建包含 Native 编译及打包任务，不等于实际加载或真机功能验证。

## 7. 未验证项

未执行真机测试。Mock/Robolectric 不能证明 API 21～26 与较新设备的实际声音、厂商 MediaCodec/AudioRecord、
framework 网络注销或长时间运行。同步 prepare 无法立即中断，释放时机是它返回后；不宣称销毁瞬间即释放。
本轮未做 sanitizer 或 16 KB 页设备加载，也不把历史 Native 产物检查写成本轮新设备验证。
