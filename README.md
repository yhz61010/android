[![Leo Version](https://jitpack.io/v/yhz61010/LeoAndroidBaseUtilProject-Kotlin.svg)](https://jitpack.io/#yhz61010/LeoAndroidBaseUtilProject-Kotlin)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.6.10-blue)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-7.1.2-orange)](https://developer.android.com/studio/releases/gradle-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-7.4-green)](https://gradle.org)


[![codebeat badge](https://codebeat.co/badges/e657f36f-48ab-466c-9dae-36da4241b675)](https://codebeat.co/projects/github-com-yhz61010-leoandroidbaseutilproject-kotlin-master)
[![CodeFactor](https://www.codefactor.io/repository/github/yhz61010/leoandroidbaseutilproject-kotlin/badge)](https://www.codefactor.io/repository/github/yhz61010/leoandroidbaseutilproject-kotlin)

# Known Issues
1. ~~AudioActivity(Solved. See AacFilePlayer.kt bf1bfe248a54308cd91cfa7f516026b237413119 commit)~~
~~Can not play AAC file in Audio demo on OnePlus 8T device.~~
2. AudioReceiver
In realtime communication of Audio demo, if the audio codec is 8Khz/16bit/2ch, the volume of receiver is too small to hear.

# TODO List
~~1. Camera2Live~~(Solved)
~~When you initialize `Camera2Component`, you must specify the *encoder* type manually.
This is not a wise way. In the next version, I will identity the *encoder* automatically
according to the camera characteristics.~~

~~2. Network Monitor~~(Solved)
~~Now, I do not show you a way to get the network traffic and ping by implementing a listener.
That means you can just check the network traffic and ping in log but you can not get them in your code.
In the next version, I will provide you a listener that you can use them freely when network traffic and ping are changed.~~

# About Log
Almost every project will use a log wrapper to manage your log. So does this library.
Here comes a question: how to save this library logs in your project if you need it?
In order to solve this problem, I implement a log system by using `LogContext`.
You just need to initiate `LogContext` with your custom log wrapper and output your log by using `LogContext`
I have already implement the `LLog` which is a wrapper of Android default log as default implementation for `LogContext`.
Of course, you can implement your log wrapper by implement `ILog`.
**DO NOT** forget to initialize it first in `Application`.

Please check the `LogActivity` for details.

FYI: You can use [Xlog](https://github.com/Tencent/mars) as your wrapper implements. It is an efficient and powerful log.

# About Camera2Live Module(Device Camera Information List)

**Notice**: All devices below are supported `OMX.google.h264.encoder` H.264 encoder

## Nexus

------

### Nexus 6(Root)(Android 7.1.1)
#### Camera supported hardware level
##### Lens Back: LEVEL_FULL(1)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [24, 24], [7, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 90)
`[[7, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4160, 3120][4160, 2774][4160, 2340][4000, 3000][3840, 2160][3264, 2176][3200, 2400][3200, 1800][2592, 1944][2592, 1728][2048, 1536][1920, 1440][1920, 1080][1280, 960][1280, 768][1280, 720][1024, 768][800, 600][800, 480][720, 480][640, 480][352, 288][320, 240][176, 144]`
##### Lens Front(Camera Sensor Orientation: 90)
`[1920, 1080][1280, 960][1280, 768][1280, 720][1024, 768][800, 600][800, 480][720, 480][640, 480][352, 288][320, 240][176, 144]`

------

### Nexus 6P(Root)(Android 8.1)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [24, 24], [15, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 90)
`[[15, 15], [10, 20], [20, 20], [24, 24], [10, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
 `[4032, 3024][4000, 3000][3840, 2160][3288, 2480][3264, 2448][3200, 2400][2976, 2976][2592, 1944][2688, 1512][2048, 1536][1920, 1080][1600, 1200][1440, 1080][1280, 960][1280, 768][1280, 720][1024, 768][800, 600][864, 480][800, 480][720, 480][640, 480][640, 360][352, 288][320, 240][176, 144][160, 120]`
##### Lens Front(Camera Sensor Orientation: 90)
`[3264, 2448][3200, 2400][2592, 1944][2688, 1512][2048, 1536][1920, 1080][1600, 1200][1440, 1080][1280, 960][1280, 768][1280, 720][1024, 768][800, 600][864, 480][800, 480][720, 480][640, 480][640, 360][352, 288][320, 240][176, 144][160, 120]`

------

## MeiZu

------

### MeiZu Pro5(M576)(Android 5.1)
#### Camera supported hardware level
##### Lens Back: LEVEL_LEGACY(2)
##### Lens Front: LEVEL_LEGACY(2)
#### H.264 Encoder
`OMX.Exynos.AVC.Encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[14000, 24000], [24000, 24000], [14000, 30000], [30000, 30000]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[14000, 30000], [30000, 30000]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[1920, 1440][1920, 1080][1440, 1080][1280, 720][800, 600][720, 480][640, 480][320, 240][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[1920, 1440][1920, 1080][1440, 1080][1280, 720][800, 600][720, 480][640, 480][320, 240][176, 144]`

------

### MeiZu MX6(M6850)(Android 7.1.1)
#### Camera supported hardware level
##### Lens Back: LEVEL_LEGACY(2)
##### Lens Front: LEVEL_LEGACY(2)
#### H.264 Encoder
`OMX.MTK.VIDEO.ENCODER.AVC`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [20, 20], [24, 24], [5, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[5, 15], [20, 20], [5, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[1680, 1260][1920, 1088][1920, 1080][1440, 1080][1280, 720][960, 540][800, 600][864, 480][800, 480][720, 480][640, 480][480, 320][352, 288][320, 240][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[1440, 1080][1280, 720][960, 720][960, 540][800, 600][864, 480][800, 480][720, 480][640, 480][480, 368][480, 320][352, 288][320, 240][176, 144]`

------

## Samsung

------

### Samsung Galaxy S20 5G(SM-G9810)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [7, 24], [24, 24], [7, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [24, 24], [7, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4032x3024, 4032x2268, 4032x1816, 3024x3024, 2400x1080, 1920x864, 1920x824, 3840x2160, 1920x1080, 1440x1080, 1088x1088, 1280x720, 960x720, 720x480, 640x480, 640x360, 352x288, 320x240, 256x144, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3216x1808, 3216x1448, 2944x2208, 2944x1656, 2944x1320, 2208x2208, 2400x1080, 1920x864, 1920x824, 1920x1080, 1440x1080, 1088x1088, 1280x720, 960x720, 720x480, 640x480, 640x360, 352x288, 320x240, 256x144, 176x144]`

------

### Samsung Galaxy A51 5G(SM-A5160)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.Exynos.AVC.Encoder`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [15, 20], [20, 20], [24, 24], [8, 30], [10, 30], [15, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [15, 20], [20, 20], [24, 24], [8, 30], [10, 30], [15, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4000x3000, 4000x2250, 4000x1800, 3840x2160, 3264x2448, 2992x2992, 2576x1932, 2400x1080, 1920x1080, 1440x1080, 1088x1088, 1280x720, 960x720, 800x450, 720x720, 720x480, 640x480, 640x360, 352x288, 320x240, 256x144, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2640x1980, 2640x1488, 2640x1188, 1968x1968, 2400x1080, 2144x1200, 1920x1080, 1440x1080, 1088x1088, 1280x720, 960x720, 800x450, 720x720, 720x480, 640x480, 352x288, 320x240, 256x144, 176x144]`

------

### Samsung Galaxy S10(SM-G9730)(Android 9.0)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`  
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [7, 24], [24, 24], [8, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [24, 24], [8, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4032, 3024][4032, 2268][4032, 1908][3024, 3024][3840, 2160][2560, 1440][1920, 1080][1280, 720][1920, 910][960, 540][1440, 1080][1280, 960][1088, 1088][960, 720][720, 480][640, 480][352, 288][320, 240][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2944, 2208][2944, 1656][2944, 1396][2208, 2208][1920, 1080][1280, 720][2288, 1080][1920, 910][960, 540][1440, 1080][1280, 960][1088, 1088][960, 720][720, 480][640, 480][352, 288][320, 240][176, 144]`

------

### Samsung Galaxy Note8(SM-N9500)(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_FULL(1)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [20, 20], [24, 24], [7, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [20, 20], [24, 24], [7, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4032x3024, 4032x2268, 3984x2988, 3264x2448, 3264x1836, 3024x3024, 2976x2976, 2880x2160, 2560x1920, 2560x1440, 2560x1080, 2448x2448, 2160x2160, 2048x1152, 1920x1080, 1440x1080, 1280x960, 1280x720, 720x480, 640x480, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3264x1836, 2880x2160, 2560x1920, 2560x1440, 2560x1080, 2448x2448, 2160x2160, 2048x1152, 1920x1080, 1440x1080, 1280x960, 1280x720, 720x480, 640x480, 320x240, 176x144]`

------

### Samsung Galaxy S7 Edge(SM-G9350)(Android 8.0)
#### Camera supported hardware level
##### Lens Back: LEVEL_FULL(1)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [20, 20], [24, 24], [30, 30], [7, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [20, 20], [24, 24], [30, 30], [7, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4032, 3024][4032, 2268][3984, 2988][3264, 2448][3264, 1836][3024, 3024][2976, 2976][2880, 2160][2592, 1944][2560, 1920][2560, 1440][2560, 1080][2160, 2160][2048, 1536][2048, 1152][1936, 1936][1920, 1080][1440, 1080][1280, 960][1280, 720][960, 720][720, 480][640, 480][320, 240][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2592, 1944][2560, 1920][2560, 1440][2560, 1080][2048, 1536][2048, 1152][1936, 1936][1920, 1080][1440, 1080][1280, 960][1280, 720][960, 720][720, 480][640, 480][320, 240][176, 144]`

------

## HuaWei

------

### HuaWei Honor 8 Lite(PRA-AL00X)(Android 8.0)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.IMG.TOPAZ.VIDEO.Encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[30, 30], [14, 30], [14, 20], [20, 20], [14, 25], [25, 25], [12, 15], [15, 15], [14, 14]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[30, 30], [14, 30], [14, 20], [20, 20], [14, 25], [25, 25], [12, 15], [15, 15], [14, 14]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[3968, 2976][1920, 1080][1440, 1080][1280, 960][1280, 720][960, 720][960, 544][720, 720][640, 480][352, 288][320, 240][208, 144][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264, 2448][1920, 1080][1440, 1080][1280, 960][1280, 720][960, 720][960, 544][720, 720][640, 480][320, 240][352, 288][208, 144][176, 144]`

------

### HuaWei Honor 9 Lite(LLD-AL00)(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.IMG.TOPAZ.VIDEO.Encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[14, 14], [12, 15], [15, 15], [14, 20], [20, 20], [14, 25], [25, 25], [14, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[14, 14], [12, 15], [15, 15], [14, 20], [20, 20], [14, 25], [25, 25], [14, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4160, 3120][2160, 1080][1920, 1080][1440, 1080][1440, 720][1280, 960][1280, 720][960, 720][960, 544][720, 720][640, 480][352, 288][320, 240][208, 144][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[4160, 3120][3264, 2448][2160, 1080][1920, 1080][1440, 1080][1440, 720][1280, 960][1280, 720][960, 720][960, 544][720, 720][640, 480][352, 288][320, 240][208, 144][176, 144]`

------

### HuaWei 畅享9 (DUB-AL00)(Android 8.1)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [7, 20], [20, 20], [7, 24], [24, 24], [7, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [20, 20], [24, 24], [7, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4160, 3120][4000, 3000][3968, 2976][3840, 2880][4160, 2048][3120, 3120][3520, 2640][3264, 2448][3200, 2400][2592, 1944][2688, 1512][2048, 1536][1920, 1080][1600, 1200][1440, 1080][1280, 960][1440, 712][1280, 768][1280, 720][1200, 1200][1280, 480][1280, 400][720, 720][1024, 768][960, 720][800, 600][864, 480][800, 480][720, 480][640, 480][640, 360][480, 640][480, 360][480, 320]  [352, 288][320, 240][240, 320][176, 144][144, 176]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264, 2448][3200, 2400][2432, 2432][3264, 1600][2592, 1944][2688, 1512][2048, 1536][1920, 1080][2560, 800][1600, 1200][1440, 1080][1280, 960][1440, 712][1280, 768][1280, 720][1200, 1200][1280, 480][1280, 400][720, 720][1024, 768][800, 600][864, 480][800, 480][720, 480][640, 480][640, 360][480, 640][480, 360][480, 320][352, 288][320, 240][240, 320][176, 144][160, 120][144, 176]`

------

### HuaWei P9(EVA-TL00)(Android 8.0)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.IMG.TOPAZ.VIDEO.Encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[14, 30], [30, 30], [14, 20], [20, 20], [14, 25], [25, 25], [12, 15], [15, 15]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[14, 30], [30, 30], [14, 20], [20, 20], [14, 25], [25, 25], [12, 15], [15, 15]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[3968, 2976][2976, 2976][3280, 2448][3264, 2448][3264, 1840][2448, 2448][2592, 1952][1920, 1080][1440, 1080][1536, 864][1280, 960][1280, 720][960, 720][720, 720][640, 480][736, 414][544, 408][400, 400][352, 288][320, 240][208, 144][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264, 2448][2448, 2448][3264, 1840][1920, 1080][1440, 1080][1536, 864][1280, 960][1280, 720][960, 720][720, 720][640, 480][736, 414][544, 408][400, 400][352, 288][320, 240][208, 144][176, 144]`

------

### HuaWei Mate 10(ALP-AL00)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.hisi.video.encoder.avc`  
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[12, 15], [15, 15], [14, 20], [20, 20], [14, 25], [25, 25], [14, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[12, 15], [15, 15], [14, 20], [20, 20], [14, 25], [25, 25], [14, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[3968, 2976][2976, 2976][3840, 2160][3280, 2448][3264, 2448][3264, 1840][2448, 2448][2592, 1952][2048, 1536][1920, 1080][1440, 1080][1536, 864][1456, 1456][1280, 960][1280, 720][960, 720][960, 540][720, 720][720, 540][640, 480][640, 360][736, 412][544, 408][480, 360][400, 400][352, 288][320, 240][208, 144][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264, 2448][2448, 2448][3264, 1840][1920, 1080][1440, 1080][1456, 1080][1536, 864][2048, 1536][1456, 1456][1280, 960][1280, 720][960, 720][720, 720][736, 412][640, 480][352, 288][320, 240][208, 144][176, 144]`

------

### HuaWei Mate 30 Pro(LIO-AL00)(Android 10)
#### H.264 Encoder
`OMX.hisi.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[12, 15], [15, 15], [14, 20], [20, 20], [24, 24], [14, 25], [25, 25], [14, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[12, 15], [15, 15], [14, 20], [20, 20], [24, 24], [14, 25], [25, 25], [14, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[3648, 2736][3648, 2056][3648, 1712][2736, 2736][3840, 2160][3840, 1648][3120, 2340][2560, 1080][2288, 1080][2160, 1080][3280, 2448][3264, 2448][3264, 1840][3008, 2256][2688, 2016][2448, 2448][2592, 1952][2048, 1536][1920, 1080][1440, 1080][1600, 1080][1536, 864][1456, 1456][1664, 768][1680, 720][1520, 720][1440, 720][1280, 960][1280, 720][1088, 1080][1088, 720][960, 720][960, 540][720, 720][720, 540][640, 480][640, 360][736, 412][544, 408][480, 360][400, 400][352, 288][320, 240][208, 144][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3264x1836, 3264x1504, 2288x1080, 2160x1080, 1920x1080, 1920x960, 1664x768, 1552x720, 1440x1080, 1440x720, 1280x960, 1280x720, 960x720, 960x544, 720x720, 640x480, 320x240, 352x288, 208x144, 176x144]`

------

### HuaWei P40(ANA-AN00)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.hisi.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[12, 15], [15, 15], [14, 20], [20, 20], [24, 24], [14, 25], [25, 25], [14, 30], [30, 30], [15, 60], [30, 60], [60, 60]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[12, 15], [15, 15], [14, 20], [20, 20], [24, 24], [14, 25], [25, 25], [14, 30], [30, 30], [60, 60]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4096x3072, 4096x2304, 4096x1888, 3648x2056, 3648x1680, 3648x2056, 3072x3072, 3840x2160, 3840x1760, 3840x1648, 3120x2340, 2560x1080, 3280x2448, 3264x2448, 3264x1840, 3008x2256, 2448x2448, 2336x1080, 2048x1536, 1920x1080, 1552x720, 1440x1080, 1456x1456, 1664x768, 1440x720, 1280x960, 1280x720, 1088x1080, 960x720, 960x540, 720x720, 720x540, 640x480, 640x360, 736x412, 544x408, 480x360, 400x400, 352x288, 320x240, 208x144, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3840x2160, 3264x2448, 2448x2448, 3264x1836, 3264x1504, 3264x1680, 3264x1480, 2336x1080, 2160x1080, 1920x1080, 1920x960, 1664x768, 1552x720, 1440x1080, 1440x720, 1280x960, 1280x720, 1216x912, 960x720, 960x544, 720x720, 640x480, 320x240, 352x288, 208x144, 176x144, 1600x1200, 896x672]`

------

### HuaWei P40 Pro(ELS-TN00)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.hisi.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[12, 15], [15, 15], [14, 20], [20, 20], [24, 24], [14, 25], [25, 25], [14, 30], [30, 30], [15, 60], [30, 60], [60, 60]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[12, 15], [15, 15], [14, 20], [20, 20], [24, 24], [14, 25], [25, 25], [14, 30], [30, 30], [60, 60]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4096x3072, 4096x1864, 3072x3072, 3840x2160, 3840x1648, 3120x2340, 2560x1080, 2368x1080, 2160x1080, 3264x2448, 3264x1840, 3008x2256, 2688x2016, 2448x2448, 2592x1952, 2048x1536, 1920x1080, 1440x1080, 1600x1080, 1536x864, 1456x1456, 1712x720, 1584x720, 1440x720, 1280x960, 1280x720, 1088x1080, 1088x720, 960x720, 960x540, 720x720, 720x540, 640x480, 640x360, 736x412, 544x408, 480x360, 400x400, 352x288, 320x240, 208x144, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3840x2160, 3264x2448, 2448x2448, 3264x1836, 3264x1504, 3264x1680, 3264x1480, 2368x1080, 2160x1080, 1920x1080, 1920x960, 1600x1200, 1584x720, 1440x1080, 1440x720, 1216x912, 1280x960, 1280x720, 1024x768, 960x720, 960x544, 768x576, 720x720, 640x480, 320x240, 352x288, 208x144, 176x144]`

------

## OPPO

------

### OPPO A72 5G(PDYM20)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.MTK.VIDEO.ENCODER.AVC`
`OMX.oppo.h264.encoder`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[10, 10], [15, 15], [15, 20], [20, 20], [5, 30], [15, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[10, 10], [15, 15], [15, 20], [20, 20], [5, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4096x3072, 4096x2304, 4096x1840, 3840x2160, 3456x3456, 3360x1512, 3264x2448, 3264x1840, 3264x1632, 3264x1572, 3264x1504, 3264x1472, 2912x1344, 2560x1920, 2560x1080, 2340x1080, 2304x1728, 2280x1080, 2160x1080, 1920x1920, 1920x1440, 1920x1080, 1872x864, 1600x1200, 1600x720, 1560x720, 1560x702, 1440x1088, 1440x1080, 1280x960, 1280x720, 1088x1088, 960x544, 800x400, 720x480, 640x480, 640x360, 352x288, 320x240, 192x144, 192x108, 176x144, 160x96]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3264x1840, 3264x1632, 3264x1572, 3264x1504, 3264x1472, 2912x1344, 2560x1920, 2560x1080, 2448x2448, 2340x1080, 2304x1728, 2280x1080, 2160x1080, 1920x1920, 1920x1440, 1920x1080, 1872x864, 1600x1200, 1600x720, 1560x720, 1560x702, 1440x1088, 1440x1080, 1280x960, 1280x720, 1088x1088, 960x544, 800x400, 720x480, 640x480, 640x360, 352x288, 320x240, 192x144, 192x108, 176x144, 160x96]`

------

### OPPO A32(PDVM00)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [13, 24], [15, 24], [24, 24], [13, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[10, 10], [15, 15], [15, 24], [24, 24], [10, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4160x1872, 3840x2160, 3264x2448, 3264x1472, 2448x2448, 3200x2400, 3264x1840, 2688x1512, 2592x1944, 2592x1940, 2304x1728, 2400x1080, 2048x1536, 1920x1440, 1920x1080, 1600x1200, 1600x900, 1600x720, 1520x720, 1440x1080, 1200x1200, 1280x960, 1280x768, 1280x720, 1080x1080, 1024x738, 1024x768, 960x960, 960x720, 864x480, 800x600, 800x480, 720x1280, 720x720, 720x480, 640x480, 640x400, 640x360, 352x288, 320x240, 240x320, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3264x1472, 2448x2448, 3200x2400, 3264x1840, 2688x1512, 2592x1944, 2592x1940, 2304x1728, 2400x1080, 2048x1536, 1920x1440, 1920x1080, 1600x1200, 1600x900, 1600x720, 1520x720, 1440x1080, 1200x1200, 1280x960, 1280x768, 1280x720, 1080x1080, 1024x738, 1024x768, 960x960, 960x720, 864x480, 800x600, 800x480, 720x1280, 720x720, 720x480, 640x480, 640x400, 640x360, 352x288, 320x240, 240x320, 176x144]`

------

### OPPO Reno4 5G(PDPM00)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [17, 24], [24, 24], [9, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [10, 24], [24, 24], [10, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4000x3000, 4000x2250, 4000x2248, 4000x1800, 3840x2160, 3264x2448, 3264x1836, 3200x2400, 3200x1440, 2448x2448, 3000x3000, 2688x1512, 2592x1944, 2400x1080, 2340x1080, 2304x1728, 2048x1536, 2240x1008, 2280x1080, 2264x1080, 2160x1080, 1920x1440, 1920x1080, 1600x1200, 1440x1080, 1560x720, 1280x960, 1280x768, 1280x720, 1080x1080, 1024x768, 864x480, 800x400, 800x600, 800x480, 720x480, 640x480, 640x400, 480x640, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3264x1836, 3200x2400, 3200x1440, 2448x2448, 2688x1512, 2592x1944, 2400x1080, 2340x1080, 2304x1728, 2048x1536, 2240x1008, 2280x1080, 2264x1080, 2160x1080, 1920x1440, 1920x1080, 1600x1200, 1440x1080, 1560x720, 1280x960, 1280x768, 1280x720, 1080x1080, 1024x768, 864x480, 800x400, 800x600, 800x480, 720x480, 640x480, 640x400, 480x640, 352x288, 320x240, 176x144]`

------

### OPPO R15 梦镜版(PAAM00)(Android 8.1.0)
#### Camera supported hardware level
##### Lens Back: LEVEL_LEGACY(2)
##### Lens Front: LEVEL_LEGACY(2)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
`OMX.oppo.h264.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [20, 20], [10, 24], [24, 24], [10, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [7, 20], [20, 20], [7, 24], [24, 24], [7, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[1440x1080, 1280x960, 1520x720, 1280x720, 960x720, 720x480, 640x480, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[1440x1080, 1280x960, 1520x720, 1280x720, 960x720, 720x480, 640x480, 352x288, 320x240, 176x144]`

------

## vivo

------

### vivo G1(V1962BA)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.Exynos.AVC.Encoder`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [8, 30], [10, 30], [15, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [8, 30], [10, 30], [15, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4000x3000, 4000x2256, 4000x1856, 4000x1808, 2992x2992, 3840x2160, 3264x2448, 3264x1836, 3264x1520, 3264x1472, 3232x1536, 2448x2448, 2560x1920, 2560x1440, 2560x1184, 2560x1152, 1920x1920, 1920x816, 1632x760, 1600x1200, 1600x900, 1600x752, 1200x1200, 2400x1080, 2336x1080, 1920x1080, 1920x896, 1600x720, 1552x720, 1440x1080, 1088x1088, 1280x720, 1024x768, 960x720, 960x540, 720x720, 640x480, 320x240, 256x144, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3264x1836, 3264x1520, 3264x1472, 2448x2448, 2560x1920, 2560x1440, 2560x1184, 2560x1152, 1920x1920, 1632x1224, 1632x918, 1632x760, 1600x1200, 1600x900, 1600x752, 1200x1200, 2400x1080, 2336x1080, 1920x1080, 1920x896, 1920x816, 1600x720, 1552x720, 1440x1080, 1088x1088, 1280x720, 1024x768, 960x720, 720x720, 640x480, 320x240, 256x144, 176x144]`

------

### vivo X50(V2001A)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.Exynos.AVC.Encoder`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[4, 15], [15, 15], [8, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[4, 15], [15, 15], [6, 24], [24, 24], [8, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4000x3000, 4000x2256, 4000x1824, 3264x2448, 3264x1840, 3264x1488, 2048x1536, 2048x1152, 2048x944, 2000x1500, 1500x1500, 2000x912, 3840x2160, 3264x1836, 2560x1920, 2560x1440, 1920x1920, 2376x1080, 1920x1080, 1632x1224, 1600x1200, 1600x900, 1600x736, 1188x540, 1440x1080, 1280x960, 1280x720, 960x540, 800x600, 800x480, 720x480, 640x480, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3264x1840, 3264x1488, 2048x1536, 2048x1152, 2048x944, 2000x1500, 1500x1500, 2000x912, 3264x1836, 2560x1920, 2560x1440, 1920x1920, 2376x1080, 1920x1080, 1632x1224, 1600x1200, 1600x900, 1600x736, 1188x540, 1440x1080, 1280x960, 1280x720, 960x540, 800x600, 800x480, 720x480, 640x480, 352x288, 320x240, 176x144]`

------

### vivo iQOO(V1824A)(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [8, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [8, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4032, 3024][4032, 2268][4032, 1872][3840, 2160][3264, 2448][3264, 1836][3232, 1536][3120, 1440][3024, 3024][2944, 1656][2560, 1920][2560, 1920][2560, 1440][2448, 2448][2340, 1080][2176, 2176][2016, 1512][1920, 1920][1920, 1440][1920, 1080][1600, 1200][1600, 752][1440, 1080][1280, 960][1280, 768][1280, 720][1200, 1200][1080, 1080][1024, 738][1024, 768][864, 480][800, 600][800, 480][720, 480][640, 480][640, 360][480, 640][352, 288][320, 240][176, 144][160, 120][144, 176]`
##### Lens Front(Camera Sensor Orientation: 270)
`[4032, 3024][4032, 2268][4032, 1872][3840, 2160][3264, 2448][3264, 1836][3232, 1536][3120, 1440][3024, 3024][2944, 1656][2560, 1920][2560, 1920][2560, 1440][2448, 2448][2340, 1080][2176, 2176][2016, 1512][1920, 1920][1920, 1440][1920, 1080][1600, 1200][1600, 752][1440, 1080][1280, 960][1280, 768][1280, 720][1200, 1200][1080, 1080][1024, 738][1024, 768][864, 480][800, 600][800, 480][720, 480][640, 480][640, 360][480, 640][352, 288][320, 240][176, 144][160, 120][144, 176]`

------

### vivo NEX 3(V1923A)(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_LIMITED(0)
##### Lens Front: LEVEL_LIMITED(0)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[4, 15], [15, 15], [8, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [8, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4608, 3456][4608, 2592][4608, 2208][4160, 3120][4160, 2352][4160, 2000][3840, 2160][2304, 1728][2304, 1296][2256, 1080][1920, 1440][1920, 1080][1600, 1200][1600, 768][1504, 720][1440, 1080][1280, 960][1280, 720][1024, 768][960, 540][800, 600][800, 480][720, 480][640, 480][352, 288][320, 240][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2304, 1728][2304, 1296][2256, 1080][1920, 1440][1920, 1080][1600, 1200][1600, 768][1504, 720][1440, 1080][1280, 960][1280, 720][1024, 768][960, 540][800, 600][800, 480][720, 480][640, 480][352, 288][320, 240][176, 144]`

------

### vivo U3x(V1928A)(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [20, 20], [7, 24], [24, 24], [7, 30], [24, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [20, 20], [7, 24], [24, 24], [7, 30], [24, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4160x3120, 4000x3000, 3840x2160, 3264x2448, 3200x2400, 2320x1080, 1920x1080, 2560x800, 1600x1200, 1440x1080, 1280x960, 1280x720, 1200x1200, 1280x480, 1280x400, 1024x768, 800x600, 864x480, 720x480, 640x640, 640x480, 640x360, 480x360, 480x320, 352x288, 320x240, 176x144, 160x120]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3200x2400, 2320x1080, 1920x1080, 2560x800, 1600x1200, 1440x1080, 1280x960, 1280x720, 1200x1200, 1280x480, 1280x400, 1024x768, 800x600, 864x480, 720x480, 640x640, 640x480, 640x360, 480x360, 480x320, 352x288, 320x240, 176x144, 160x120]`

------

## XiaoMi

------

### XiaoMi MIX 3(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_FULL(1)
##### Lens Front: LEVEL_FULL(1)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [7, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [5, 22], [22, 22], [7, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4032, 3024][4000, 3000][4032, 2268][4032, 2016][3840, 2160][2880, 2156][2688, 1512][2592, 1296][1920, 1440][1920, 1080][1600, 1200][1280, 960][1280, 720][1280, 640][800, 600][720, 480][640, 480][640, 360][352, 288][320, 240][176, 144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2880, 2156][2688, 1512][2592, 1296][1920, 1440][1920, 1080][1600, 1200][1280, 960][1280, 720][1280, 640][800, 600][720, 480][640, 480][640, 360][352, 288][320, 240][176, 144]`

------

### Redmi K30(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [5, 20], [20, 20], [8, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [8, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4624x3472, 4624x2600, 3840x2160, 2160x1080, 1920x1440, 1920x1080, 1600x1200, 1560x720, 1440x1080, 1280x960, 1280x720, 800x600, 720x480, 640x480, 640x360, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2592x1940, 2160x1080, 1920x1440, 1920x1080, 1600x1200, 1560x720, 1440x1080, 1280x960, 1280x720, 800x600, 720x480, 640x480, 640x360, 352x288, 320x240, 176x144]`

------

### RedMi 8(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [7, 20], [20, 20], [7, 24], [24, 24], [7, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [20, 20], [7, 24], [24, 24], [7, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4032x3016, 4000x3000, 4032x2268, 4000x2000, 3264x2448, 4032x1908, 3200x2400, 3200x1800, 3200x1516, 2592x1944, 2688x1512, 2592x1296, 2048x1536, 1920x1080, 1600x1200, 1600x900, 1600x758, 1440x1080, 1520x1140, 1520x720, 1280x960, 1440x720, 1280x720, 960x720, 800x600, 720x480, 640x480, 640x360, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[3264x2448, 3200x2400, 3200x1800, 3200x1516, 2592x1944, 2688x1512, 2592x1296, 2048x1536, 1920x1080, 1600x1200, 1600x900, 1600x758, 1440x1080, 1520x1140, 1520x720, 1280x960, 1440x720, 1280x720, 960x720, 800x600, 720x480, 640x480, 640x360, 352x288, 320x240, 176x144]`

------

### RedMi Note8 Pro(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_FULL(1)
##### Lens Front: LEVEL_FULL(1)
#### H.264 Encoder
`OMX.MTK.VIDEO.ENCODER.AVC`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[10, 10], [15, 15], [15, 20], [20, 20], [5, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[10, 10], [15, 15], [15, 20], [20, 20], [5, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4640x3472, 4624x3472, 4624x2600, 4624x2136, 3840x2160, 3472x3472, 2560x1920, 1920x1440, 2340x1080, 1920x1080, 1440x1080, 1080x1080, 1440x720, 1280x720, 960x720, 720x480, 640x480, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2592x1940, 2592x1460, 2592x1196, 1944x1944, 2560x1920, 1920x1440, 2340x1080, 1920x1080, 1440x1080, 1280x720, 960x720, 720x480, 640x480, 352x288, 320x240, 176x144]`

------

### RedMi 6A(Android 9)
#### Camera supported hardware level
##### Lens Back: LEVEL_LEGACY(2)
##### Lens Front: LEVEL_LEGACY(2)
#### H.264 Encoder
`OMX.MTK.VIDEO.ENCODER.AVC`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[5, 15], [20, 20], [24, 24], [10, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[5, 15], [20, 20], [24, 24], [10, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[1440x1080, 1440x720, 1280x720, 960x720, 960x540, 800x600, 864x480, 800x480, 720x480, 640x480, 640x360, 640x320, 480x368, 480x320, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[1440x1080, 1440x720, 1280x720, 960x720, 960x540, 800x600, 864x480, 800x480, 720x480, 640x480, 640x360, 640x320, 480x368, 480x320, 352x288, 320x240, 176x144]`

## OnePlus

------

### OnePlus 8T(KB2000)(Android 11)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_3(3)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
`c2.android.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [8, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [8, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4000x3000, 4000x2250, 4000x1818, 4000x1800, 3840x2160, 3840x1644, 3456x2592, 3280x2464, 3264x2448, 3264x1836, 3264x1472, 3200x2400, 3168x1440, 3000x3000, 2880x2160, 2592x1944, 2448x2448, 2688x1512, 2592x1168, 2376x1080, 2304x1728, 2160x1080, 1944x1944, 1920x1080, 1920x864, 1920x822, 1600x1200, 1600x800, 1600x720, 1584x720, 1440x1080, 1280x960, 1280x768, 1024x768, 1280x720, 1200x1200, 1188x540, 1200x540, 1080x1080, 840x360, 800x400, 792x360, 720x540, 720x480, 640x640, 640x480, 640x360, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[2328x1748, 2304x1728, 2160x1080, 1920x1080, 1920x864, 1920x822, 1600x1200, 1600x800, 1600x720, 1584x720, 1440x1080, 1280x960, 1280x768, 1024x768, 1280x720, 1200x1200, 1188x540, 1200x540, 1080x1080, 840x360, 800x400, 792x360, 720x540, 720x480, 640x640, 640x480, 640x360, 352x288, 320x240, 176x144]`

------

### OnePlus 7 Pro(GM1910)(Android 10)
#### Camera supported hardware level
##### Lens Back: LEVEL_3(3)
##### Lens Front: LEVEL_FULL(1)
#### H.264 Encoder
`OMX.qcom.video.encoder.avc`
`c2.android.avc.encoder`
`c2.qti.avc.encoder`
#### Camera supported FPS
##### Lens Back(Camera Sensor Orientation: 90)
`[[15, 15], [6, 23], [23, 23], [8, 28], [28, 28], [8, 30], [30, 30]]`
##### Lens Front(Camera Sensor Orientation: 270)
`[[15, 15], [8, 30], [30, 30]]`
#### Camera supported size
##### Lens Back(Camera Sensor Orientation: 90)
`[4000x3000, 4000x1824, 4000x1800, 3840x2160, 3264x2448, 3264x1472, 3264x1468, 3200x2400, 3000x3000, 2976x2976, 2688x1512, 2592x1944, 2448x2448, 2080x960, 1920x864, 1920x1080, 1440x1080, 1280x960, 1280x768, 1280x720, 1080x1080, 1024x738, 1024x768, 864x480, 800x600, 800x480, 720x480, 640x480, 352x288, 320x240, 176x144]`
##### Lens Front(Camera Sensor Orientation: 270)
`[4608x2592, 4608x2112, 4608x2080, 4160x1920, 4096x2304, 4000x3000, 4000x1824, 4000x1800, 3456x3456, 3264x2448, 3264x1472, 3264x1468, 3200x2400, 3120x3120, 3000x3000, 2976x2976, 2688x1512, 2592x1944, 2448x2448, 2328x1746, 2080x960, 1920x864, 1920x1080, 1440x1080, 1280x960, 1280x768, 1280x720, 1080x1080, 1024x738, 1024x768, 864x480, 800x600, 800x480, 720x480, 640x480, 352x288, 320x240, 176x144]`
