# AndroidShowcase Project Framework and Coding Standards

## 📋 Table of Contents

1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Design](#architecture-design)
4. [Modular Structure](#modular-structure)
5. [Coding Standards](#coding-standards)
6. [Dependency Management](#dependency-management)
7. [Build Configuration](#build-configuration)
8. [Code Quality](#code-quality)
9. [Testing Standards](#testing-standards)
10. [Best Practices](#best-practices)

---

## Project Overview

AndroidShowcase is a modern Android application template based on **Jetpack Compose + Kotlin + Clean Architecture**.

### Core Features
- ✅ 100% Kotlin-only codebase
- ✅ 100% Gradle Kotlin DSL configuration
- ✅ Jetpack Compose UI
- ✅ Clean Architecture layered design
- ✅ Multi-module architecture
- ✅ Dependency Injection (Hilt)
- ✅ MVVM design pattern
- ✅ Static code analysis (Detekt + Ktlint)

### SDK Versions
- `minSdk`: 25 (Android 7.1.1)
- `targetSdk`: 36 (Android 16 - Baklava)
- `compileSdk`: 36

---

## Technology Stack

### Core Frameworks
```toml
[versions]
kotlin = "2.3.10"
agp = "9.0.1"
compose-bom = "2026.02.01"
hilt = "2.59.2"
coroutines = "1.10.2"
lifecycle = "2.10.0"
```

### Main Libraries
- **UI Framework**: Jetpack Compose + Material 3
- **Architecture Components**: Lifecycle, ViewModel, Navigation
- **Dependency Injection**: Hilt
- **Networking**: OkHttp + Lib-Network
- **Image Loading**: Coil 3
- **Serialization**: Kotlinx Serialization
- **Logging**: Leo Log Library

---

## Architecture Design

### Clean Architecture Three-Layer Structure

```
┌─────────────────┐
│  Presentation   │  <- UI Layer (Composable + ViewModel)
├─────────────────┤
│    Domain       │  <- Business Logic (UseCase + Model + Repository Interface)
├─────────────────┤
│      Data       │  <- Data Layer (Repository Implementation + DataSource)
└─────────────────┘
```

### Layer Responsibilities

#### 1. Presentation Layer
- **Location**: `app/src/main/kotlin/com/leovp/androidshowcase/presentation/`
- **Components**:
  - `*Screen.kt` / `*Composable.kt`: UI components
  - `*ViewModel.kt`: State management and business logic coordination
  - `UiState`: UI state data classes
  - `UiEvent`: User interaction events

**Example**:
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

#### 2. Domain Layer
- **Location**: `app/src/main/kotlin/com/leovp/androidshowcase/domain/`
- **Components**:
  - `model/`: Business models
  - `repository/`: Repository interface definitions
  - `usecase/`: Business use cases

**Example**:
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

#### 3. Data Layer
- **Location**: `app/src/main/kotlin/com/leovp/androidshowcase/data/`
- **Components**:
  - `repository/`: Repository implementations
  - `datasource/`: Data sources (local/remote)
  - `Module.kt`: DI module configurations

---

## Modular Structure

### Module Breakdown

```
AndroidShowcase/
├── app/                      # Main application module
├── feature_base/             # Base feature module (common components, utilities)
├── feature_discovery/        # Discovery feature module
├── feature_my/               # My profile feature module
├── feature_community/        # Community feature module
└── feature_main_drawer/      # Main drawer feature module
```

### Module Dependency Rules
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

### Resource Naming Convention
Each module uses `resourcePrefix` to avoid resource conflicts:
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

## Coding Standards

### Naming Conventions

#### 1. Classes and Interfaces
- Use **PascalCase** (UpperCamelCase)
- Class names should be nouns, interfaces can be adjectives or nouns
```kotlin
class MainActivity : ComponentActivity()
interface UserRepository
abstract class BaseViewModel<S, A>
```

#### 2. Functions and Variables
- Use **camelCase** (lowerCamelCase)
- Function names should be verbs, variable names should be nouns
```kotlin
fun loadData() { }
val userList: List<User>
private val _uiState = MutableStateFlow<UiState>()
```

#### 3. Constants and Enums
- Use **SCREAMING_SNAKE_CASE**
```kotlin
const val MAX_RETRY_COUNT = 3
enum class NetworkStatus { SUCCESS, ERROR, LOADING }
```

#### 4. Private Properties
- Prefix with underscore `_` for private mutable state
```kotlin
private val _uiState = MutableStateFlow<UiState>()
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

### File Formats

#### 1. Composable Functions
- Single Composable function should not exceed 100 lines
- Complex UI should be split into multiple smaller Composables
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
- Extend `BaseViewModel<UiState, Action>`
- Use `sealed interface` to define UiState and Action
- Handle events using `onEvent()` method

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
- Single Responsibility Principle: one UseCase does one thing
- Call Repository to fetch data and return Result
```kotlin
class GetUnreadListUseCase @Inject constructor(
    private val repository: MainRepository
) {
    suspend operator fun invoke(id: String): Result<List<UnreadModel>> {
        return repository.getUnreadList(id)
    }
}
```

### Documentation Standards

#### 1. KDoc Format
```kotlin
/**
 * Author: Michael Leo
 * Date: 2023/9/4 14:08
 * Description: Main ViewModel for handling UI logic
 */
@HiltViewModel
class MainViewModel @Inject constructor(/* ... */)
```

#### 2. File Header Comments
```kotlin
/**
 * Author: Michael Leo
 * Date: 2023/9/4 14:08
 */
package com.leovp.androidshowcase.presentation
```

#### 3. Prohibited Comments
```kotlin
// ❌ Forbidden: TODO, FIXME, STOPSHIP
// TODO: To be completed // Not allowed
// FIXME: Needs fix // Not allowed
// STOPSHIP: Fix before release // Not allowed

// ✅ Should use task tracking system
```

### Code Style

#### 1. Import Order
```kotlin
// Standard library
import androidx.compose.runtime.*
import kotlinx.coroutines.*

// AndroidX
import androidx.lifecycle.*

// Third-party libraries
import com.google.dagger.hilt.*
import io.coil.*

// Project internal
import com.leovp.androidshowcase.domain.*
import com.leovp.feature.base.*
```

#### 2. Blank Line Rules
- One blank line after class declarations
- One blank line between functions
- One blank line between logical blocks
- No more than two consecutive blank lines

#### 3. Line Width Limit
- Maximum line width: **120 characters**
- Wrap and use continuation indent when exceeded

```kotlin
// ✅ Correct
val veryLongVariableName = someVeryLongMethodCall(
    parameter1,
    parameter2,
    parameter3
)

// ❌ Incorrect
val veryLongVariableName = someVeryLongMethodCall(parameter1, parameter2, parameter3) // Exceeds 120 characters
```

---

## Dependency Management

### Version Catalog (libs.versions.toml)

All dependencies are centrally managed in `gradle/libs.versions.toml`:

```toml
[versions]
# Version definitions
hilt = "2.59.2"
compose-bom = "2026.02.01"

[libraries]
# Dependency declarations
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }

[bundles]
# Dependency bundles
androidx-compose = [
    "androidx-compose-activity",
    "androidx-compose-viewmodel",
    "androidx-compose-material-iconsExtended",
]

[plugins]
# Plugin declarations
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### Module-level Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Use type-safe project accessors
    implementation(projects.featureBase)
    
    // Use version catalog
    implementation(libs.hilt.android)
    implementation(libs.bundles.androidx.compose)
    ksp(libs.hilt.compiler)
    
    // Platform dependency (BOM)
    implementation(platform(libs.androidx.compose.bom))
}
```

---

## Build Configuration

### Global Configuration (Root build.gradle.kts)

```kotlin
// Java/Kotlin version configuration
val javaVersion: JavaVersion by extra {
    JavaVersion.toVersion(libs.versions.javaVersion.get().toInt())
}

allprojects {
    // Apply code analysis tools
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    // Detekt configuration
    detekt {
        config.from(files("$rootDir/config/detekt/detekt.yml"))
        parallel = true
    }
    
    // Ktlint configuration
    ktlint {
        android.set(true)
        ignoreFailures.set(false)
    }
}
```

### Product Flavors

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

### Signing Configuration

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

## Code Quality

### Static Analysis Tools

#### 1. Detekt (Code Smell Detection)
```bash
# Run Detekt
./gradlew detekt

# View report
# Report location: build/reports/detekt/detekt.html
```

**Key Rules**:
- Cyclomatic complexity threshold: 15
- Method length threshold: 70 lines
- Class size threshold: 600 lines
- Parameter count threshold: 7
- Nesting depth threshold: 4

#### 2. Ktlint (Code Formatting)
```bash
# Check code format
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat
```

**Key Rules**:
- Indentation: 4 spaces
- Maximum line width: 120 characters
- Import order: *, java.**, javax.**, kotlin.**, ^
- Final newline at end of file: Required

#### 3. Unified Check Command
```bash
# Run all static checks
./gradlew staticCheck
```

### Dependency Update Check

```bash
# Check for dependency updates
./gradlew dependencyUpdates
```

---

## Testing Standards

### Testing Frameworks
- **Unit Tests**: JUnit 5
- **Android Tests**: AndroidJUnit5 Runner
- **Compose Tests**: Compose UI Test

### Test Dependencies

```kotlin
dependencies {
    // Unit tests
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    
    // Android tests
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.mannodermaus.junit5.core)
    androidTestRuntimeOnly(libs.mannodermaus.junit5.runner)
}
```

### Test Class Naming

```kotlin
// Unit tests
class MainViewModelTest {
    @Test
    fun `given valid input when load data then returns success`() { }
}

// Android tests
@RunWith(AndroidJUnit5::class)
class MainScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun `when screen loads then shows content`() { }
}
```

### Code Coverage (Jacoco)

```kotlin
// Module-level build.gradle.kts
apply(from = "../jacoco.gradle.kts")

plugins {
    jacoco
}

// Generate coverage report
./gradlew jacocoTestReport

// Verify coverage
./gradlew jacocoCoverageVerification
```

---

## Best Practices

### 1. Compose Performance Optimization

#### Use State Hoisting
```kotlin
// ✅ Recommended: State hoisting
@Composable
fun ParentScreen() {
    var count by remember { mutableStateOf(0) }
    CounterDisplay(count = count, onIncrement = { count++ })
}

@Composable
fun CounterDisplay(count: Int, onIncrement: () -> Unit) {
    Text(text = "$count")
    Button(onClick = onIncrement) { Text("Increment") }
}

// ❌ Not recommended: State inside child component
```

#### Use derivedStateOf
```kotlin
// ✅ Recommended: Avoid unnecessary recomposition
val sortedList by remember {
    derivedStateOf { list.sortedBy { it.priority } }
}

// ❌ Not recommended: Sort on every recomposition
val sortedList = list.sortedBy { it.priority }
```

#### Use remember and derivedStateOf
```kotlin
@Composable
fun ExpensiveOperation(data: List<String>) {
    // ✅ Cache computation result
    val filteredData by remember(data) {
        derivedStateOf { data.filter { it.isNotEmpty() } }
    }
}
```

### 2. ViewModel Best Practices

#### Use StateFlow
```kotlin
// ✅ Recommended
private val _uiState = MutableStateFlow<UiState>(Loading())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// ❌ Avoid using LiveData
```

#### Avoid Memory Leaks
```kotlin
// ✅ Use viewModelScope
fun loadData() {
    viewModelScope.launch {
        // Coroutine automatically cancelled when ViewModel is destroyed
        val data = repository.getData()
        _uiState.value = Success(data)
    }
}

// ❌ Avoid using GlobalScope
```

### 3. Dependency Injection Best Practices

#### Use Hilt
```kotlin
// ✅ Recommended: Use @HiltViewModel
@HiltViewModel
class MainViewModel @Inject constructor(
    private val useCase: MainUseCase
) : BaseViewModel<UiState, Action>()

// ❌ Avoid manual ViewModel creation
```

#### Module Organization
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

### 4. Coroutines Best Practices

#### Structured Concurrency
```kotlin
// ✅ Use scope
viewModelScope.launch {
    // All child coroutines automatically cancelled when ViewModel is destroyed
    val data = async { fetchData() }
    val image = async { fetchImage() }
    
    showResult(data.await(), image.await())
}

// ❌ Avoid using GlobalScope
GlobalScope.launch { } // Forbidden
```

#### Exception Handling
```kotlin
// ✅ Use try-catch or Result
viewModelScope.launch {
    val result = runCatching {
        repository.getData()
    }
    
    result.onSuccess { showData(it) }
        .onFailure { showError(it) }
}
```

### 5. Resource Management

#### String Resources
```xml
<!-- ✅ Recommended: Use resource files -->
<string name="home_title">Home</string>

<!-- ❌ Avoid: Hardcoded strings -->
Text(text = "Home")
```

#### Color Resources
```kotlin
// ✅ Recommended: Use theme colors
Color(MaterialTheme.colorScheme.primary)

// ❌ Avoid: Hardcoded color values
Color(0xFF0000FF)
```

### 6. Logging Standards

```kotlin
import com.leovp.log.base.i
import com.leovp.log.base.w
import com.leovp.log.base.e

// ✅ Use unified logging library
i(tag) { "Data loaded successfully: $data" }
w(tag) { "Warning message" }
e(tag, exception) { "Error message" }

// ❌ Avoid using println or Log directly
```

### 7. Git Commit Convention

```bash
# Commit format
<type>(scope): <subject>

# Type categories
feat:     New feature
fix:      Bug fix
docs:     Documentation update
style:    Code style adjustment
refactor: Code refactoring
test:     Test related
chore:    Build/toolchain related

# Examples
feat(discovery): Add search feature to discovery page
fix(network): Fix network connection timeout issue
refactor(viewmodel): Refactor main ViewModel state management
```

---

## Appendix

### Common Gradle Commands

```bash
# Clean build
./gradlew clean

# Build Debug version
./gradlew assembleDebug

# Build Release version
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run connected device tests
./gradlew connectedAndroidTest

# View dependency tree
./gradlew app:dependencies

# Remove unused dependencies
./gradlew sortDependencies
```

### Project Structure Quick Reference

```
app/
├── src/main/
│   ├── kotlin/com/leovp/androidshowcase/
│   │   ├── data/              # Data layer
│   │   │   ├── datasource/    # Data sources
│   │   │   ├── repository/    # Repository implementations
│   │   │   └── MainModule.kt  # DI configuration
│   │   ├── domain/            # Domain layer
│   │   │   ├── model/         # Business models
│   │   │   ├── repository/    # Repository interfaces
│   │   │   └── usecase/       # Business use cases
│   │   ├── presentation/      # Presentation layer
│   │   │   ├── MainScreen.kt  # UI screens
│   │   │   ├── MainViewModel.kt
│   │   │   └── MainUiState.kt
│   │   ├── testdata/          # Test data
│   │   ├── ui/                # Common UI components
│   │   └── utils/             # Utility classes
│   └── res/                   # Resource files
└── build.gradle.kts
```

### Configuration Files Location

| Configuration File | Path | Description |
|-------------------|------|-------------|
| Dependency Versions | `gradle/libs.versions.toml` | Centralized dependency version management |
| Detekt Configuration | `config/detekt/detekt.yml` | Code static analysis rules |
| Signing Configuration | `config/sign/keystore.properties` | App signing credentials |
| Jacoco Configuration | `jacoco.gradle.kts` | Code coverage configuration |
| Global Build Configuration | `build.gradle.kts` | Root project build configuration |
| Module Settings | `settings.gradle.kts` | Project module inclusion |

---

## Continuous Updates

This document should be continuously updated as the project evolves. When adding new features or modifying the architecture, please update the corresponding sections accordingly.

**Last Updated**: 2026-03-26
**Maintainer**: Michael Leo
