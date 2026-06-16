---
paths:
  - "**/*.kt"
  - "**/*.kts"
---
# Kotlin Testing

> This file extends [common/testing.md](../common/testing.md) with Kotlin and Android-specific content.

## Test Framework (this project)

- **JUnit 5 (Jupiter)** — primary framework (`@Test`, runs on JUnit Platform)
- **Mockk** — mocking (`mockk`, `every`, `coEvery`, `verify`)
- **Kluent** — fluent assertions (`shouldBeEqualTo`, `shouldBe`, `shouldContain`)
- **Robolectric** — Android unit tests without a device
- **kotlinx-coroutines-test** — coroutine testing (`runTest`, `TestDispatcher`)

## ViewModel / StateFlow Testing

Drive events under `runTest`, advance the dispatcher, then assert on `state.value`
with Kluent. Inject a `TestDispatcher` so coroutines run on the test scheduler.

```kotlin
@Test
fun `load populates items in state`() = runTest {
    val repo = FakeItemRepository().apply { addItem(testItem) }
    val viewModel = ItemListViewModel(GetItemsUseCase(repo))

    viewModel.state.value shouldBeEqualTo ItemListState() // initial
    viewModel.onEvent(ItemListEvent.Load)
    advanceUntilIdle()
    viewModel.state.value.items shouldBeEqualTo listOf(testItem)
}
```

## Mocks and Fakes

**Mockk** is the project's mocking tool. Use it for collaborators whose behavior
you stub per-test:

```kotlin
val repo = mockk<ItemRepository>()
coEvery { repo.getAll() } returns Result.success(listOf(testItem))
// ...
coVerify { repo.getAll() }
```

Prefer **hand-written fakes** for stateful collaborators (repositories with
in-memory data) — they read more clearly than heavily-stubbed mocks:

```kotlin
class FakeItemRepository : ItemRepository {
    private val items = mutableListOf<Item>()
    var fetchError: Throwable? = null

    override suspend fun getAll(): Result<List<Item>> {
        fetchError?.let { return Result.failure(it) }
        return Result.success(items.toList())
    }

    override fun observeAll(): Flow<List<Item>> = flowOf(items.toList())

    fun addItem(item: Item) { items.add(item) }
}
```

## Coroutine Testing

```kotlin
@Test
fun `parallel operations complete`() = runTest {
    val repo = FakeRepository()
    val result = loadDashboard(repo)
    advanceUntilIdle()
    assertNotNull(result.items)
    assertNotNull(result.stats)
}
```

Use `runTest` — it auto-advances virtual time and provides `TestScope`.

## Room Testing

Use an in-memory database for Room tests (the `demo` module's Room example shows the setup):

```kotlin
@Test
fun `insert word into in-memory db`() = runTest {
    val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        WordRoomDatabase::class.java
    ).allowMainThreadQueries().build()
    val dao = db.wordDao()

    dao.insert(Word("hello"))   // suspend DAO ops run on the test scheduler
    dao.deleteAll()

    db.close()
}
// getAlphabetizedWords() returns LiveData<List<Word>> — to assert query results,
// add InstantTaskExecutorRule and observe via a getOrAwaitValue() helper.
```

## Test Naming

Use backtick-quoted descriptive names:

```kotlin
@Test
fun `search with empty query returns all items`() = runTest { }

@Test
fun `delete item emits updated list without deleted item`() = runTest { }
```

## Test Organization

```
src/
├── test/kotlin/        # JVM/Robolectric unit tests (JUnit 5 + Mockk + Kluent)
└── androidTest/kotlin/ # Instrumented tests on device/emulator (Room, UI)
```

Tests run in parallel (`maxParallelForks = availableProcessors / 2`) and use
`unitTests.isReturnDefaultValues = true` + `isIncludeAndroidResources = true`.

Cover ViewModel + UseCase + pure-logic utilities for every feature.
