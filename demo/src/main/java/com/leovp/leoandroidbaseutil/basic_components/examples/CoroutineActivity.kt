package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class CoroutineActivity : BaseDemonstrationActivity() {

    val mainScope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coroutine)

//        GlobalScope.launch { // 在后台启动一个新的协程并继续
//            delay(1000L) // 非阻塞的等待 1 秒钟（默认时间单位是毫秒）
//            println("World!") // 在延迟后打印输出
//        }
//        println("Hello,") // 协程已在等待时主线程还在继续
//        Thread.sleep(2000L) // 阻塞主线程 2 秒钟来保证 JVM 存活

//        val cs = CoroutineScope(Dispatchers.Main)
//        val ftp = CoroutineScope(Executors.newFixedThreadPool(5).asCoroutineDispatcher())
//        val ste = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
//        CoroutineScope(Dispatchers.Default).launch(ste) { }
//        GlobalScope.launch(ste) { }

        val cs = CoroutineScope(Dispatchers.Main)
        cs.launch {
            var name = fetchDoc("Book0")
            LogContext.log.e(ITAG, "Finally result-fetchDoc=$name")
            name = fetchDocByPost("Post1")
            LogContext.log.e(ITAG, "Finally result-fetchDocByPost=$name")
        }
        cs.launch { fetchTwoDocs() }
        cs.launch {
            withTimeout(1300L) {
                repeat(1000) { i ->
                    LogContext.log.e(ITAG, "I'm sleeping $i ...")
                    delay(500L)
                }
            }
        }
        cs.launch {
            val time = measureTimeMillis {
                delay(987L)
            }
            LogContext.log.e(ITAG, "measureTimeMillis cost=$time")
        }

        cs.launch { // 运行在父协程的上下文中，即 runBlocking 主协程
            LogContext.log.e(ITAG, "main runBlocking      : I'm working in thread ${Thread.currentThread().name}")
        }
        cs.launch(Dispatchers.Unconfined) { // 不受限的——将工作在主线程中
            LogContext.log.e(ITAG, "Unconfined            : I'm working in thread ${Thread.currentThread().name}")
        }
        cs.launch(Dispatchers.Default) { // 将会获取默认调度器
            LogContext.log.e(ITAG, "Default               : I'm working in thread ${Thread.currentThread().name}")
        }
        cs.launch(newSingleThreadContext("MyOwnThread")) { // 将使它获得一个新的线程
            LogContext.log.e(ITAG, "newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
        }
        cs.launch(Dispatchers.Default + CoroutineName("-test")) { coroutineName() }

        mainScope.launch {
            coroutineName()
        }

        doSomething()

        LogContext.log.e(ITAG, "Main done")
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    fun doSomething() {
        // 在示例中启动了 10 个协程，且每个都工作了不同的时长
        repeat(10) { i ->
            mainScope.launch {
                delay((i + 1) * 200L) // 延迟 200 毫秒、400 毫秒、600 毫秒等等不同的时间
                LogContext.log.e(ITAG, "Coroutine $i is done|${Thread.currentThread().name}")
            }
        }
    }

    private suspend fun coroutineName() = coroutineScope {
        val v1 = async(CoroutineName("v1coroutine")) {
            delay(500)
            LogContext.log.e(ITAG, "Computing v1 | ${Thread.currentThread().name}")
            252
        }
        val v2 = async(CoroutineName("v2coroutine")) {
            delay(1000)
            LogContext.log.e(ITAG, "Computing v2 | ${Thread.currentThread().name}")
            6
        }
        LogContext.log.e(ITAG, "The answer for v1 / v2 = ${v1.await() / v2.await()}")
    }

    private fun normalMethod() {
        LogContext.log.w(ITAG, "normalMethod")
    }

    // Dispatchers.Main
    suspend fun fetchDoc(name: String): String {
        // Dispatchers.Main
        val result = get(name)
        // Dispatchers.Main
        LogContext.log.e(ITAG, "[fetchDoc] You got=$result [${Thread.currentThread().name}]")
        return result
    }

    // Dispatchers.Main
    suspend fun fetchDocByPost(name: String): String {
        // Dispatchers.Main
        val result = post(name)
        // Dispatchers.Main
        LogContext.log.e(ITAG, "[fetchDocByPost] You got=$result [${Thread.currentThread().name}]")
        return result
    }

    suspend fun fetchTwoDocs() = coroutineScope {
        val deferreds = listOf(     // fetch two docs at the same time
            async { fetchDoc("Book1") },  // async returns a result for the first doc
            async { fetchDoc("Book2") }   // async returns a result for the second doc
        )
        LogContext.log.e(ITAG, "All async done - ${Thread.currentThread().name}")
        // use awaitAll to wait for both network requests
        deferreds.awaitAll()
        LogContext.log.e(ITAG, "After awaitAll - ${Thread.currentThread().name}")
    }

    // look at this in the next section
    suspend fun get(url: String) = withContext(Dispatchers.IO) {
        delay(2000)
        "Model get/BookName[$url]-Thread[${Thread.currentThread().name}]"
    }

    // look at this in the next section
    suspend fun post(url: String) = withContext(Dispatchers.IO) {
        delay(2000)
        "Model post/BookName[$url]-Thread[${Thread.currentThread().name}]"
    }
}