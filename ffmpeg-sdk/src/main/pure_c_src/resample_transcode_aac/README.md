### How to build

Run the following command:
```shell
$ gcc -o resample_transcode_aac resample_transcode_aac.c cmn_util.c ffmpeg_util.c \
  -lavutil -lswresample -lavcodec -lavformat -lswscale
```

or build with cmake:
```shell
$ mkdir build-dir
$ cd build-dir
$ cmake ..
$ make
```
