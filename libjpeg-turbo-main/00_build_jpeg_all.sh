#!/bin/bash

# for arch in armeabi-v7a arm64-v8a x86 x86_64
for arch in armeabi-v7a arm64-v8a x86 x86_64
do
    bash 00_build_jpeg.sh $arch
done
