cp -r ../libjpeg-turbo/libs/arm64-v8a/include src/main/cpp/

rm -rf libs/arm64-v8a/
rm -rf libs/armeabi-v7a/
rm -rf libs/x86/
rm -rf libs/x86_64/

mkdir -p libs/arm64-v8a/
mkdir -p libs/armeabi-v7a/
mkdir -p libs/x86/
mkdir -p libs/x86_64/

cp ../libjpeg-turbo/libs/arm64-v8a/lib/*.so   libs/arm64-v8a/
cp ../libjpeg-turbo/libs/armeabi-v7a/lib/*.so libs/armeabi-v7a/
cp ../libjpeg-turbo/libs/x86/lib/*.so         libs/x86/
cp ../libjpeg-turbo/libs/x86_64/lib/*.so      libs/x86_64/
