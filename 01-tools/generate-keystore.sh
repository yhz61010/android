#!/bin/bash

# 生成新的 Android 签名密钥
# 注意：这个脚本会生成新的密钥，请妥善保管密码

KEYSTORE_FILE="leo-anroid-release.jks"
KEY_ALIAS="leovp-android-release"
KEYSTORE_PASSWORD="12345678"  # ← 请修改这个密码
KEY_PASSWORD="12345678"       # ← 请修改这个密码

echo "正在生成新的签名密钥..."
echo "密钥库文件：$KEYSTORE_FILE"
echo "密钥别名：$KEY_ALIAS"
echo ""
echo "⚠️ 重要提示："
echo "1. 请在生成后立即修改 gradle.properties 中的密码"
echo "2. 将 leo-anroid-release.jks 添加到 .gitignore"
echo "3. 永远不要将密钥和密码提交到代码仓库"
echo ""

keytool -genkey -v \
  -keystore "$KEYSTORE_FILE" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias "$KEY_ALIAS" \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=LeoVP, OU=Development, O=LeoVP, L=Dalian, ST=Liaoning, C=CN"

if [ $? -eq 0 ]; then
  echo ""
  echo "✅ 密钥生成成功！"
  echo ""
  echo "密钥信息："
  echo "  文件：$KEYSTORE_FILE"
  echo "  别名：$KEY_ALIAS"
  echo "  有效期：10000 天（约 27 年）"
  echo ""
  echo "🔐 请立即执行以下操作："
  echo "  1. 备份 $KEYSTORE_FILE 到安全位置"
  echo "  2. 在 gradle.properties 中配置密码"
  echo "  3. 将 $KEYSTORE_FILE 加入 .gitignore"
  echo ""
else
  echo ""
  echo "❌ 密钥生成失败！"
  exit 1
fi
