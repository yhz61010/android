cp -r ../libjpeg-turbo-main/libs/arm64-v8a/include src/main/cpp/

mkdir -p libs/arm64-v8a/
mkdir -p libs/armeabi-v7a/

cp ../libjpeg-turbo-main/libs/arm64-v8a/lib/*.so libs/arm64-v8a/
cp ../libjpeg-turbo-main/libs/armeabi-v7a/lib/*.so libs/armeabi-v7a/