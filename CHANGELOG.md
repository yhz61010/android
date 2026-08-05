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
