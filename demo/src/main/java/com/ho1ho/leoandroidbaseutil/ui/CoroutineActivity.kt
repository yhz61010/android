package com.ho1ho.leoandroidbaseutil.ui

import android.os.Bundle
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class CoroutineActivity : BaseDemonstrationActivity() {
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
            CLog.e(ITAG, "Finally result-fetchDoc=$name")
            name = fetchDocByPost("Post1")
            CLog.e(ITAG, "Finally result-fetchDocByPost=$name")
        }
        cs.launch { fetchTwoDocs() }
        cs.launch {
            withTimeout(1300L) {
                repeat(1000) { i ->
                    CLog.e(ITAG, "I'm sleeping $i ...")
                    delay(500L)
                }
            }
        }
        cs.launch {
            val time = measureTimeMillis {
                delay(987L)
            }
            CLog.e(ITAG, "measureTimeMillis cost=$time")
        }

        CLog.e(ITAG, "Main done")
    }

    private fun normalMethod() {
        CLog.w(ITAG, "normalMethod")
    }

    // Dispatchers.Main
    suspend fun fetchDoc(name: String): String {
        // Dispatchers.Main
        val result = get(name)
        // Dispatchers.Main
        CLog.e(ITAG, "[fetchDoc] You got=$result [${Thread.currentThread().name}]")
        return result
    }

    // Dispatchers.Main
    suspend fun fetchDocByPost(name: String): String {
        // Dispatchers.Main
        val result = post(name)
        // Dispatchers.Main
        CLog.e(ITAG, "[fetchDocByPost] You got=$result [${Thread.currentThread().name}]")
        return result
    }

    suspend fun fetchTwoDocs() = coroutineScope {
        val deferreds = listOf(     // fetch two docs at the same time
            async { fetchDoc("Book1") },  // async returns a result for the first doc
            async { fetchDoc("Book2") }   // async returns a result for the second doc
        )
        CLog.e(ITAG, "All async done - ${Thread.currentThread().name}")
        // use awaitAll to wait for both network requests
        deferreds.awaitAll()
        CLog.e(ITAG, "After awaitAll - ${Thread.currentThread().name}")
    }

    // look at this in the next section
    suspend fun get(url: String) = withContext(Dispatchers.IO) {
        delay(2000)
        "Model get/BookName[$url]-Thread[${Thread.currentThread().name}]"
    }

    // look at this in the next section
    suspend fun post(url: String) = coroutineScope {
        delay(2000)
        "Model post/BookName[$url]-Thread[${Thread.currentThread().name}]"
    }
}