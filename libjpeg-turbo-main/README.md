**Attention:**
`libjpeg-turbo-main-20220325.tar.gz` This file is downloaded at 2022/03/25.

## How to compile `ibjpeg-turbo`

### Download
Download `libjpeg-turbo` sources or use the downloaded sources `libjpeg-turbo-main-20220325.tar.gz`
(This is the original official version just excludes `.git` and `.github` folder. Downloaded date: 2022/03/25)

```
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libjpeg-turbo-main
$ mkdir source
$ cd source
$ git clone https://github.com/libjpeg-turbo/libjpeg-turbo.git .
# or unzip `libjpeg-turbo-main-20220325.tar.gz` file
$ tar xvzf ../libjpeg-turbo-main-20220325.tar.gz --strip-components 1
```

### Compile
Run the following command:
```shell
$ cd /Users/yhz61010/AndroidStudioProjects/LeoAndroidBaseUtilProject-Kotlin/libjpeg-turbo-main/
$ sh 00_build_jpeg_all.sh
```