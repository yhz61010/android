## Convert video to raw stream
### Convert H.264 video to raw stream
```shell
$ ffmpeg -i tears_400_x264.mp4 -vcodec copy -an -f h264 tears_400_x264_raw.h264
$ ffmpeg -i tears_400_x264.mp4 -vcodec copy -an -bsf:v h264_mp4toannexb tears_400_x264_raw.h264
```
### Convert H.265 video to raw stream
```shell
$ ffmpeg -i tears_400_x265.mp4 -vcodec copy -an -f h265 tears_400_x265_raw.h265
$ ffmpeg -i tears_400_x265.mp4 -vcodec copy -an -bsf:v hevc_mp4toannexb tears_400_x265_raw.h265
```

**Parameter description:**
```
-vcodec codec: force video codec ('copy' to copy stream)
-an			 : disable audio
-f fmt       : force format
-bsf[:stream_specifier] bitstream_filters (output,per-stream)
           Set bitstream filters for matching streams. bitstream_filters is a comma-separated list of itstream filters. 
           Use the "-bsfs" option to get the list of bitstream filters.
           ffmpeg -i tears_400_x264.mp4 -c:v copy -bsf:v h264_mp4toannexb -an tears_400_x264_raw.h264

# https://www.ffmpeg.org/ffmpeg-bitstream-filters.html#h264_005fmp4toannexb
h264_mp4toannexb: Convert an H.264 bitstream from length prefixed mode to start code prefixed mode (as defined in the Annex B of the ITU-T H.264 specification). 
```

使用 `ffmpeg -bsfs` 命令可以查看 `ffmpeg` 工具支持的 Bitstream Filter 类型。

**PS:**
H.264 有两种封装模式:
- Annex-B 模式，也叫 MPEG-2 transport stream format 格式（ts格式）：传统模式，有 startcode。
  SPS 和 PPS 是在 ES 中，vlc 里打开编码器信息中显示 H.264。
- avcC 模式，也叫 AVC1 模式：MPEG-4 格式，字节对齐，因此也叫 Byte-Stream Format。Elementary Stream 格式。用于mp4/flv/mkv等封装中。没有 startcode。
  SPS 和 PPS 以及其它信息被封装在 container 中，每一个 frame 前面是这个 frame 的长度,vlc 里打开编码器信息中显示 avc1。

Android 硬解码 MediaCodec 只接受 AnnexB 格式的数据，而 Apple 的 VideoToolBox，只支持 avcC 的格式。

这两种格式的区别有两点：
1. NALU 的分割方式不同
2. SPS/PPS 的数据结构不同。

- Annex-B 格式使用start code进行分割，start code 为 0x00 00 01 或 0x00 00 00 01，
  SPS/PPS 作为一般NALU单元以start code作为分隔符的方式放在文件或者直播流的头部。
- AVCC 格式使用 NALU 长度（固定字节，字节数由extradata中的信息给定）进行分割，在封装文件或者直播流的头部包含 extradata 信息（非 NALU），
  extradata 中包含 NALU 长度的字节数以及 SPS/PPS 信息。

extradata 的数据结构如下：
```
bits    
8   version ( always 0x01 )
8   avc profile ( sps[0][1] )
8   avc compatibility ( sps[0][2] )
8   avc level ( sps[0][3] )
6   reserved ( all bits on )
2   NALULengthSizeMinusOne
3   reserved ( all bits on )
5   number of SPS NALUs (usually 1)

repeated once per SPS:
    16         SPS size
    variable   SPS NALU data

8 number of PPS NALUs (usually 1)

repeated once per PPS:
    16       PPS size
    variable PPS NALU data
```

我们注意一下这个值 NALULengthSizeMinusOne，通过将这个值加 1 ，我们就得出了后续每个 NALU 前面前缀（也就是表示长度的整数）的字节数
例如，这个 NALULengthSizeMinusOne 是 3，那么每个 NALU 前面前缀的长度就是 4 个字节。
我们在读取后续数据时，可以先读 4 个字节，然后把这四个字节转成整数，就是这个 NALU 的长度了，注意，这个长度并不包含起始的4个字节，是单纯 NALU 的长度。

| 编解码器     | 编码输出 | 解码输入 |
| ------------ | -------- | -------- |
| libopenh264  | Annex B  | Annex B  |
| MediaCodec   | Annex B  | Annex B  |
| VideoToolbox | avcC     | avcC     |


## Reference
- [T-REC-H.264-202108-I!!PDF-E.pdf](https://res.leovp.com/resources/documents/video/T-REC-H.264-202108-I!!PDF-E.pdf)
- https://www.jianshu.com/p/3192162ffda1
- https://developer.huawei.com/consumer/cn/forum/topic/0203472320869270276?fid=23&postId=0303472320869270152
- https://blog.csdn.net/momo0853/article/details/115136696
- https://www.its404.com/article/m0_37684310/78541557