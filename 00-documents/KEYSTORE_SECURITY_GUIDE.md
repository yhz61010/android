# 🔐 签名密钥安全配置指南

### 步骤 1: 生成新的密钥

执行以下命令生成新密钥：

```bash
chmod +x generate-keystore.sh
./generate-keystore.sh
```

**重要：** 
- 脚本中的密码是示例，请修改为强密码
- 生成的 `leo-anroid-release.jks` 文件需要安全备份

### 步骤 2: 配置本地 gradle.properties

1. 复制模板文件：
   ```bash
   cp gradle.properties.template gradle.properties
   ```

2. 编辑 `gradle.properties`，填入你的实际配置：
   ```properties
   leovp.storeFile=leo-anroid-release.jks
   leovp.storePassword=你的密钥库密码
   leovp.keyAlias=你的密钥别名
   leovp.keyPassword=你的密钥密码
   ```

3. **重要：** 确保 `gradle.properties` 不会被提交到 Git：
   - 该文件已在 `.gitignore` 中
   - 只在本地保存，不要上传

### 步骤 3: 若已经提交过密钥文件，需要从 Git 历史中删除旧密钥

⚠️ **必须彻底从 Git 历史中移除旧密钥文件**

```bash
# 1. 先从当前目录删除
git rm --cached leo-anroid-release.jks
# 只从 Git 版本控制中移除该文件
# 不会删除你本地的 leo-anroid-release.jks 文件
# 该文件会从远程仓库中删除
# 提交并推送后，其他开发者拉取代码时，这个文件会从他们的仓库中消失

# 2. 从 Git 历史中彻底清除
git filter-branch --force --index-filter \
  'git rm --cached --ignore-unmatch leo-anroid-release.jks' \
  --prune-empty --tag-name-filter cat -- --all

# 3. 清理引用
rm -rf .git/refs/original/
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# 4. 强制推送到远程仓库
git push --force --all
```

**注意：** 强制推送会影响所有协作者，请提前通知团队！

### 步骤 4: 验证构建

测试新的配置是否正确：

```bash
# 清理旧的构建
./gradlew clean

# 构建 debug 版本测试
./gradlew assembleDevDebug

# 构建 release 版本测试
./gradlew assembleDevRelease
```

检查 APK 是否正确签名：

```bash
# 查看 APK 签名信息
apksigner verify --verbose app/build/outputs/apk/qr/release/app-dev-release.apk
```

---

## 🔄 CI/CD 配置

如果在 CI/CD 环境中构建，请使用环境变量：

### GitHub Actions 示例

```yaml
- name: Build Release APK
  env:
    KEYSTORE_PATH: ${{ secrets.KEYSTORE_PATH }}
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: ./gradlew assembleRelease
```

### Jenkins 示例

```groovy
environment {
    KEYSTORE_PATH = credentials('keystore-path')
    KEYSTORE_PASSWORD = credentials('keystore-password')
    KEY_ALIAS = credentials('key-alias')
    KEY_PASSWORD = credentials('key-password')
}
```

---

## 🛡️ 安全最佳实践

### ✅ 应该做的：
1. ✅ 使用环境变量或本地配置文件管理敏感信息
2. ✅ 将密钥文件添加到 `.gitignore`
3. ✅ 定期更换密码
4. ✅ 离线备份密钥文件（加密存储）
5. ✅ 限制密钥访问权限

### ❌ 不应该做的：
1. ❌ 将密钥文件提交到版本控制
2. ❌ 在代码中硬编码密码
3. ❌ 使用弱密码（如生日、简单数字）
4. ❌ 通过明文传输密钥（邮件、聊天工具）
5. ❌ 多人共享同一个密钥文件

---

## 🆘 常见问题

### Q: 如果我已经提交了旧密钥怎么办？
A: 立即执行"步骤 3"，从 Git 历史中彻底删除，并更换新密钥。

### Q: 可以在多台电脑上使用同一个密钥吗？
A: 可以，但需要通过安全方式同步（如加密 U 盘），不要通过网络传输。

### Q: 如果密钥丢失了会怎样？
A: 无法更新 Play Store 上的应用，只能发布新包名的应用。**务必备份！**

### Q: 需要多久更换一次密钥？
A: 建议每年更换一次，或者在怀疑泄露时立即更换。

---

## 📞 需要帮助？

如果在配置过程中遇到问题，请检查：
1. Gradle 配置是否正确
2. 环境变量是否设置
3. 密钥文件路径是否正确
4. 密码是否输入正确

**记住：保护好密钥就是保护好你的应用！** 🔐
