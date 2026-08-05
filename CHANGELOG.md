# Changelog

本文件记录 `LeoAndroidBaseUtil` 的显著变更,尤其是**破坏性 / 行为变更**。
格式参考 [Keep a Changelog](https://keepachangelog.com/)，遵循语义化版本。

## [Unreleased]

### 安全 (Security)

- **CIP-1 `ZipUtil.unzip` 加固**:解压时对每个条目做规范化路径校验,拒绝 Zip Slip 路径穿越
  (`../`、绝对路径、软链父目录),并对单条目与归档总解压体积设上限(默认 200 MiB / 1 GiB)以缓解
  ZIP bomb。写入改为「临时文件 + 原子 rename」,失败不再遗留半文件。
  - **行为变更**:遇到路径穿越或超限的归档,现在抛出 `IllegalArgumentException`(此前会静默写出)。
  - **兼容**:新增可选参数 `limits: ZipUtil.UnzipLimits`,通过 `@JvmOverloads` 保留原两参数入口,
    既有 Kotlin/Java 调用无需改动。
- **HTTP-1 默认日志级别改为 `NONE`**:`BaseHttpRequest` 不再默认以 `BODY` 级别记录完整请求/响应。
  - **破坏性变更**:此前默认 `BODY`(会打印完整报文体和所有 Header)。现新增公开属性
    `BaseHttpRequest.logLevel`,默认 `HttpLoggingInterceptor.Level.NONE`;宿主需(建议仅 debug 构建)
    显式设置 `logLevel = BODY/HEADERS` 才会输出。
  - Header 日志按名脱敏(`Authorization`、`Cookie`、`Set-Cookie`、`Proxy-Authorization` 的值以 `██` 代替);
    请求头注入日志只记 Header 名,不再记录其值。
- **HTTP-3 日志体积双向有界**:请求与响应体日志各自最多缓冲/输出 256 KiB,不再将整个响应
  (`source.request(Long.MAX_VALUE)`)或请求体读入内存;duplex/one-shot/未知长度/超限的请求体一律省略。
  修复大响应/大请求下的 OOM 风险;业务读取报文体不受影响。
- **CIP-3 `GZipUtil.decompress` 加解压上限**:新增可选参数 `maxOutputBytes`(默认 64 MiB),
  超限返回 `null`,缓解 GZIP bomb。`@JvmOverloads` 保留原有入口,既有调用无需改动。

### 修复 (Fixed)

- **LB-1 `ByteBuffer.copy()` / `copyAll()` 保留字节序**:复制时对目标 buffer 调用 `order(order())`,
  修复小端 buffer 复制后字节序被重置为大端导致的静默数据损坏。
- **HTTP-2 `HttpRequest` 并发安全**:不再共享并复用同一个可变 `Retrofit.Builder`;改为保存不可变
  header 快照,每次 `getRetrofit(baseUrl)` 新建 builder,避免并发不同 `baseUrl` 时构建出错误 Host。
- **AUD-1 `BaseMediaCodec` 确定性释放(T4)**:新增 `suspend fun releaseAndJoin()`,先
  `cancelAndJoin` codec worker 再取消 `ioScope` 并幂等释放 codec(`releaseCodecOnce()` 原子只执行一次);
  含自 join 守卫(禁止 codec worker 自身调用)。修复旧 `release()` 先释放 codec 后取消 scope 导致的
  释放期崩溃/竞态。
- **AUD-2 `AacDecoder.onInputData` 可取消(T4)**:阻塞 `queue.take()` 改为
  `queue.poll(50ms)`,返回 0 时 `process()` 跳过 `queueInputBuffer`,协程取消可及时唤醒退出。
- **AUD-3 `MicRecorder` 确定性停止(T4)**:新增 `suspend fun stopRecordAndJoin()`,先 `stop()` 让
  native `read()` 返回,再 join 录音任务并释放;含自 join 守卫与 `onStop` 幂等(只回调一次)。
- **AUD-4 Stream Player 并发串行化**:`AacStreamPlayer`/`OpusStreamPlayer` 用私有锁 + generation
  串行化 init/decode/flush/stop,`dropFrameCallback` 移出锁;修复 `audioDecoder`/`csd` 无锁 TOCTOU。
- **AUD-5 解码器初始化失败回滚**:init/`play()` 成功才提交 `csd0`(OPUS 一并 csd1/csd2);失败回滚并释放,
  维持不变量「`csd0 != null` ⇔ decoder 就绪」,消除下一帧 `!!` 崩溃路径。
- **AUD-6 `MicRecorder` 读缓冲复用**:改为固定容量 `ShortArray(bufferSize/2)`,每帧仅切片;
  修复原逐帧 `copyOfRange` 覆写导致缓冲永久收缩。
- **AUD-7 `AudioRecord.read` 负错误码处理(T8)**:`>0` 编码/回调、`==0` 继续、`<0` 记日志并停止;
  录音循环 rethrow `CancellationException`,不再吞异常静默停止。
- **AUD-8 `process()` 异常不再伪装正常 EOS(T8)**:catch 顺序 `CancellationException`(rethrow)→
  `MediaCodec.CodecException`(先于 `IllegalStateException`)→ `IllegalStateException` → 泛型
  `Exception`,均 `return false` 终止;错误经独立 `notifyCodecFailure()` 上报,不再 `return true` 忙循环。

### 新增 (Added)

- **HTTP-4 `BaseProgressObserver.cancel()` / `isDisposed`**:暴露取消入口,调用方可在页面销毁时
  终止订阅,避免向已销毁界面回调。
