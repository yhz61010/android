# AndroidShowcase 项目框架设计与代码编写规范

## 📋 目录

1. [项目概述](#项目概述)
2. [技术栈](#技术栈)
3. [架构设计](#架构设计)
4. [模块化结构](#模块化结构)
5. [代码规范](#代码规范)
6. [依赖管理](#依赖管理)
7. [构建配置](#构建配置)
8. [代码质量](#代码质量)
9. [测试规范](#测试规范)
10. [最佳实践](#最佳实践)

---

## 项目概述

AndroidShowcase 是一个基于 **Jetpack Compose + Kotlin + Clean Architecture** 的现代化 Android 应用模板项目。

### 核心特性
- ✅ 100% Kotlin-only 代码
- ✅ 100% Gradle Kotlin DSL 配置
- ✅ Jetpack Compose UI
- ✅ Clean Architecture 分层架构
- ✅ 多模块化设计
- ✅ 依赖注入 (Hilt)
- ✅ MVVM 设计模式
- ✅ 静态代码分析 (Detekt + Ktlint)

### SDK 版本
- `minSdk`: 25 (Android 7.1.1)
- `targetSdk`: 36 (Android 16)
- `compileSdk`: 36

---

## 技术栈

### 核心框架
```toml
[versions]
kotlin = "2.3.10"
agp = "9.0.1"
compose-bom = "2026.02.01"
hilt = "2.59.2"
coroutines = "1.10.2"
lifecycle = "2.10.0"
```

### 主要库
- **UI 框架**: Jetpack Compose + Material 3
- **架构组件**: Lifecycle, ViewModel, Navigation
- **依赖注入**: Hilt
- **网络请求**: OkHttp + Lib-Network
- **图片加载**: Coil 3
- **序列化**: Kotlinx Serialization
- **日志**: Leo Log Library

---

## 架构设计

### Clean Architecture 三层架构

```
┌─────────────────┐
│  Presentation   │  <- UI Layer (Composable + ViewModel)
├─────────────────┤
│    Domain       │  <- Business Logic (UseCase + Model + Repository Interface)
├─────────────────┤
│      Data       │  <- Data Layer (Repository Implementation + DataSource)
└─────────────────┘
```

### 各层职责

#### 1. Presentation Layer (展示层)
- **位置**: `app/src/main/kotlin/com/leovp/androidshowcase/presentation/`
- **组成**:
  - `*Screen.kt` / `*Composable.kt`: UI 组件
  - `*ViewModel.kt`: 状态管理和业务逻辑协调
  - `UiState`: 界面状态数据类
  - `UiEvent`: 用户交互事件

**示例**:
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val useCase: MainUseCase,
    uiEventManager: UiEventManager,
) : BaseViewModel<UiState, BaseAction<UiState>>(Content(), uiEventManager) {
    
    sealed interface UiState : BaseState {
        data class Content(
            val unreadList: List<UnreadModel> = emptyList(),
            val isLoading: Boolean = false,
        ) : UiState
    }
    
    sealed interface Action : BaseAction.Simple<UiState> {
        data object ShowLoading : Action
        data class LoadSuccess(val unreadList: List<UnreadModel>) : Action
    }
}
```

#### 2. Domain Layer (领域层)
- **位置**: `app/src/main/kotlin/com/leovp/androidshowcase/domain/`
- **组成**:
  - `model/`: 业务模型
  - `repository/`: 仓库接口定义
  - `usecase/`: 业务用例

**示例**:
```kotlin
// UseCase
class MainUseCase @Inject constructor(
    private val repository: MainRepository
) {
    suspend fun getUnreadList(id: String): Result<List<UnreadModel>> {
        return repository.getUnreadList(id)
    }
}

// Model
data class UnreadModel(
    val id: String,
    val title: String,
    val content: String
)
```

#### 3. Data Layer (数据层)
- **位置**: `app/src/main/kotlin/com/leovp/androidshowcase/data/`
- **组成**:
  - `repository/`: 仓库实现
  - `datasource/`: 数据源 (本地/远程)
  - `Module.kt`: DI 模块配置

---

## 模块化结构

### 模块划分

```
AndroidShowcase/
├── app/                      # 主应用模块
├── feature_base/             # 基础功能模块 (通用组件、工具类)
├── feature_discovery/        # 发现功能模块
├── feature_my/               # 我的功能模块
├── feature_community/        # 社区功能模块
└── feature_main_drawer/      # 主抽屉功能模块
```

### 模块依赖规则
```kotlin
// settings.gradle.kts
include(
    ":app",
    ":feature_base",
    ":feature_discovery",
    ":feature_my",
    ":feature_community",
    ":feature_main_drawer",
)

// app/build.gradle.kts
dependencies {
    implementation(projects.featureDiscovery)
    implementation(projects.featureMy)
    implementation(projects.featureCommunity)
    implementation(projects.featureMainDrawer)
}
```

### 资源命名规范
每个模块使用 `resourcePrefix` 避免资源冲突:
```kotlin
// app/build.gradle.kts
android {
    resourcePrefix = "app_"
}

// feature_base/build.gradle.kts
android {
    resourcePrefix = "feature_base_"
}
```

---

## 代码规范

### 命名规范

#### 1. 类和接口
- 使用 **PascalCase** (大驼峰)
- 类名应为名词，接口名可为形容词或名词
```kotlin
class MainActivity : ComponentActivity()
interface UserRepository
abstract class BaseViewModel<S, A>
```

#### 2. 函数和变量
- 使用 **camelCase** (小驼峰)
- 函数名应为动词，变量名应为名词
```kotlin
fun loadData() { }
val userList: List<User>
private val _uiState = MutableStateFlow<UiState>()
```

#### 3. 常量和枚举
- 使用 **SCREAMING_SNAKE_CASE**
```kotlin
const val MAX_RETRY_COUNT = 3
enum class NetworkStatus { SUCCESS, ERROR, LOADING }
```

#### 4. 私有属性
- 以下划线 `_` 开头表示私有可变状态
```kotlin
private val _uiState = MutableStateFlow<UiState>()
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

### 文件格式

#### 1. Composable 函数
- 单个 Composable 函数不超过 100 行
- 复杂 UI 应拆分为多个小的 Composable
```kotlin
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    // ...
}

@Composable
private fun TopAppBarSection(/* ... */) { /* ... */ }

@Composable
private fun ContentSection(/* ... */) { /* ... */ }
```

#### 2. ViewModel
- 继承 `BaseViewModel<UiState, Action>`
- 使用 `sealed interface` 定义 UiState 和 Action
- 事件处理使用 `onEvent()` 方法

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val useCase: MainUseCase
) : BaseViewModel<UiState, BaseAction<UiState>>(Content(), uiEventManager) {
    
    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.Search -> handleSearch(event)
                UiEvent.Refresh -> loadData()
            }
        }
    }
}
```

#### 3. UseCase
- 单一职责原则，一个 UseCase 只做一件事
- 调用 Repository 获取数据并返回 Result
```kotlin
class GetUnreadListUseCase @Inject constructor(
    private val repository: MainRepository
) {
    suspend operator fun invoke(id: String): Result<List<UnreadModel>> {
        return repository.getUnreadList(id)
    }
}
```

### 注释规范

#### 1. KDoc 格式
```kotlin
/**
 * Author: Michael Leo
 * Date: 2023/9/4 14:08
 * Description: Main ViewModel for handling UI logic
 */
@HiltViewModel
class MainViewModel @Inject constructor(/* ... */)
```

#### 2. 文件头注释
```kotlin
/**
 * Author: Michael Leo
 * Date: 2023/9/4 14:08
 */
package com.leovp.androidshowcase.presentation
```

#### 3. 禁止的注释
```kotlin
// ❌ 禁止使用 TODO, FIXME, STOPSHIP
// TODO: 待完成 // 不允许
// FIXME: 需要修复 // 不允许
// STOPSHIP: 发布前解决 // 不允许

// ✅ 应该使用任务追踪系统
```

### 代码风格

#### 1. 导入顺序
```kotlin
// 标准库
import androidx.compose.runtime.*
import kotlinx.coroutines.*

// AndroidX
import androidx.lifecycle.*

// 第三方库
import com.google.dagger.hilt.*
import io.coil.*

// 项目内部
import com.leovp.androidshowcase.domain.*
import com.leovp.feature.base.*
```

#### 2. 空行规则
- 类声明后空一行
- 函数之间空一行
- 逻辑块之间空一行
- 禁止连续两个以上空行

#### 3. 行宽限制
- 最大行宽：**120 字符**
- 超出应换行并使用续行缩进

```kotlin
// ✅ 正确
val veryLongVariableName = someVeryLongMethodCall(
    parameter1,
    parameter2,
    parameter3
)

// ❌ 错误
val veryLongVariableName = someVeryLongMethodCall(parameter1, parameter2, parameter3) // 超过 120 字符
```

---

## 依赖管理

### 版本目录 (libs.versions.toml)

所有依赖在 `gradle/libs.versions.toml` 中统一管理:

```toml
[versions]
# 版本号定义
hilt = "2.59.2"
compose-bom = "2026.02.01"

[libraries]
# 依赖声明
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }

[bundles]
# 依赖分组
androidx-compose = [
    "androidx-compose-activity",
    "androidx-compose-viewmodel",
    "androidx-compose-material-iconsExtended",
]

[plugins]
# 插件声明
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### 模块级依赖配置

```kotlin
// build.gradle.kts
dependencies {
    // 使用类型安全的项目访问器
    implementation(projects.featureBase)
    
    // 使用版本目录
    implementation(libs.hilt.android)
    implementation(libs.bundles.androidx.compose)
    ksp(libs.hilt.compiler)
    
    // 平台依赖 (BOM)
    implementation(platform(libs.androidx.compose.bom))
}
```

---

## 构建配置

### 全局配置 (根目录 build.gradle.kts)

```kotlin
// Java/Kotlin 版本配置
val javaVersion: JavaVersion by extra {
    JavaVersion.toVersion(libs.versions.javaVersion.get().toInt())
}

allprojects {
    // 应用代码分析工具
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    // Detekt 配置
    detekt {
        config.from(files("$rootDir/config/detekt/detekt.yml"))
        parallel = true
    }
    
    // Ktlint 配置
    ktlint {
        android.set(true)
        ignoreFailures.set(false)
    }
}
```

### 产品风味 (Product Flavors)

```kotlin
flavorDimensions += listOf("version")

productFlavors {
    create("dev") {
        dimension = "version"
        applicationIdSuffix = ".dev"
        versionNameSuffix = "-dev"
    }
    create("prod") {
        dimension = "version"
    }
}
```

### 签名配置

```kotlin
signingConfigs {
    create("release") {
        keyAlias = getSignProperty("keyAlias")
        keyPassword = getSignProperty("keyPassword")
        storeFile = File(rootDir, getSignProperty("storeFile"))
        storePassword = getSignProperty("storePassword")
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }
}
```

---

## 代码质量

### 静态分析工具

#### 1. Detekt (代码异味检测)
```bash
# 运行 Detekt
./gradlew detekt

# 查看报告
# 报告位置：build/reports/detekt/detekt.html
```

**关键规则**:
- 圈复杂度阈值：15
- 方法长度阈值：70 行
- 类大小阈值：600 行
- 参数数量阈值：7 个
- 嵌套深度阈值：4 层

#### 2. Ktlint (代码格式化)
```bash
# 检查代码格式
./gradlew ktlintCheck

# 自动格式化
./gradlew ktlintFormat
```

**关键规则**:
- 缩进：4 空格
- 最大行宽：120 字符
- 导入顺序：*, java.**, javax.**, kotlin.**, ^
- 文件末尾空行：必须

#### 3. 统一检查命令
```bash
# 运行所有静态检查
./gradlew staticCheck
```

### 依赖更新检查

```bash
# 检查依赖更新
./gradlew dependencyUpdates
```

---

## 测试规范

### 测试框架
- **单元测试**: JUnit 5
- **Android 测试**: AndroidJUnit5 Runner
- **Compose 测试**: Compose UI Test

### 测试依赖

```kotlin
dependencies {
    // 单元测试
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    
    // Android 测试
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.mannodermaus.junit5.core)
    androidTestRuntimeOnly(libs.mannodermaus.junit5.runner)
}
```

### 测试类命名

```kotlin
// 单元测试
class MainViewModelTest {
    @Test
    fun `given valid input when load data then returns success`() { }
}

// Android 测试
@RunWith(AndroidJUnit5::class)
class MainScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun `when screen loads then shows content`() { }
}
```

### 代码覆盖率 (Jacoco)

```kotlin
// 模块级 build.gradle.kts
apply(from = "../jacoco.gradle.kts")

plugins {
    jacoco
}

// 生成覆盖率报告
./gradlew jacocoTestReport

// 验证覆盖率
./gradlew jacocoCoverageVerification
```

---

## 最佳实践

### 1. Compose 性能优化

#### 使用状态提升
```kotlin
// ✅ 推荐：状态提升
@Composable
fun ParentScreen() {
    var count by remember { mutableStateOf(0) }
    CounterDisplay(count = count, onIncrement = { count++ })
}

@Composable
fun CounterDisplay(count: Int, onIncrement: () -> Unit) {
    Text(text = "$count")
    Button(onClick = onIncrement) { Text("增加") }
}

// ❌ 不推荐：状态在子组件内部
```

#### 使用 derivedStateOf
```kotlin
// ✅ 推荐：避免不必要的重组
val sortedList by remember {
    derivedStateOf { list.sortedBy { it.priority } }
}

// ❌ 不推荐：每次重组都排序
val sortedList = list.sortedBy { it.priority }
```

#### 使用 remember 和 derivedStateOf
```kotlin
@Composable
fun ExpensiveOperation(data: List<String>) {
    // ✅ 缓存计算结果
    val filteredData by remember(data) {
        derivedStateOf { data.filter { it.isNotEmpty() } }
    }
}
```

### 2. ViewModel 最佳实践

#### 使用 StateFlow
```kotlin
// ✅ 推荐
private val _uiState = MutableStateFlow<UiState>(Loading())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// ❌ 避免使用 LiveData
```

#### 避免内存泄漏
```kotlin
// ✅ 使用 viewModelScope
fun loadData() {
    viewModelScope.launch {
        // 协程自动在 ViewModel 销毁时取消
        val data = repository.getData()
        _uiState.value = Success(data)
    }
}

// ❌ 避免使用 GlobalScope
```

### 3. 依赖注入最佳实践

#### 使用 Hilt
```kotlin
// ✅ 推荐：使用 @HiltViewModel
@HiltViewModel
class MainViewModel @Inject constructor(
    private val useCase: MainUseCase
) : BaseViewModel<UiState, Action>()

// ❌ 避免手动创建 ViewModel
```

#### 模块划分
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideUserRepository(): UserRepository {
        return UserRepositoryImpl()
    }
}
```

### 4. 协程最佳实践

#### 结构化并发
```kotlin
// ✅ 使用作用域
viewModelScope.launch {
    // 所有子协程在 ViewModel 销毁时自动取消
    val data = async { fetchData() }
    val image = async { fetchImage() }
    
    showResult(data.await(), image.await())
}

// ❌ 避免使用 GlobalScope
GlobalScope.launch { } // 禁止
```

#### 异常处理
```kotlin
// ✅ 使用 try-catch 或 Result
viewModelScope.launch {
    val result = runCatching {
        repository.getData()
    }
    
    result.onSuccess { showData(it) }
        .onFailure { showError(it) }
}
```

### 5. 资源管理

#### 字符串资源
```xml
<!-- ✅ 推荐：使用资源文件 -->
<string name="home_title">首页</string>

<!-- ❌ 避免：硬编码字符串 -->
Text(text = "首页")
```

#### 颜色资源
```kotlin
// ✅ 推荐：使用主题颜色
Color(MaterialTheme.colorScheme.primary)

// ❌ 避免：硬编码颜色值
Color(0xFF0000FF)
```

### 6. 日志规范

```kotlin
import com.leovp.log.base.i
import com.leovp.log.base.w
import com.leovp.log.base.e

// ✅ 使用统一的日志库
i(tag) { "数据加载成功：$data" }
w(tag) { "警告信息" }
e(tag, exception) { "错误信息" }

// ❌ 避免使用 println 或 Log 直接输出
```

### 7. Git 提交规范

```bash
# 提交格式
<type>(scope): <subject>

# type 类型
feat:     新功能
fix:      Bug 修复
docs:     文档更新
style:    代码格式调整
refactor: 重构代码
test:     测试相关
chore:    构建/工具链相关

# 示例
feat(discovery): 添加发现页面搜索功能
fix(network): 修复网络连接超时问题
refactor(viewmodel): 重构主 ViewModel 状态管理
```

---

## 附录

### 常用 Gradle 命令

```bash
# 清理构建
./gradlew clean

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 运行所有测试
./gradlew test

# 运行连接的设备测试
./gradlew connectedAndroidTest

# 查看依赖树
./gradlew app:dependencies

# 移除未使用的依赖
./gradlew sortDependencies
```

### 项目结构快速参考

```
app/
├── src/main/
│   ├── kotlin/com/leovp/androidshowcase/
│   │   ├── data/              # 数据层
│   │   │   ├── datasource/    # 数据源
│   │   │   ├── repository/    # 仓库实现
│   │   │   └── MainModule.kt  # DI 配置
│   │   ├── domain/            # 领域层
│   │   │   ├── model/         # 业务模型
│   │   │   ├── repository/    # 仓库接口
│   │   │   └── usecase/       # 业务用例
│   │   ├── presentation/      # 展示层
│   │   │   ├── MainScreen.kt  # UI 界面
│   │   │   ├── MainViewModel.kt
│   │   │   └── MainUiState.kt
│   │   ├── testdata/          # 测试数据
│   │   ├── ui/                # 通用 UI 组件
│   │   └── utils/             # 工具类
│   └── res/                   # 资源文件
└── build.gradle.kts
```

### 配置文件位置

| 配置文件 | 路径 | 说明 |
|---------|------|------|
| 依赖版本 | `gradle/libs.versions.toml` | 统一管理所有依赖版本 |
| Detekt 配置 | `config/detekt/detekt.yml` | 代码静态分析规则 |
| 签名配置 | `config/sign/keystore.properties` | 应用签名密钥 |
| Jacoco 配置 | `jacoco.gradle.kts` | 代码覆盖率配置 |
| 全局构建配置 | `build.gradle.kts` | 根项目构建配置 |
| 模块设置 | `settings.gradle.kts` | 项目模块包含关系 |

---

## 持续更新

本文档应随项目发展持续更新。添加新功能或修改架构时，请同步更新相应章节。

**最后更新**: 2026-03-26
**维护者**: Michael Leo
