package com.leovp.demo.basiccomponents.examples.sharescreen.master

import android.content.Intent
import android.app.Service
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.MediaCodec
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import com.leovp.android.exts.getBaseDirString
import com.leovp.image.writeToFile
import com.leovp.screencapture.screenrecord.base.ScreenProcessor
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.ScreenCapture
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class MediaProjectionServiceLifecycleTest {
    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun `service does not request sticky recreation without fresh projection consent`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        try {
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
            assertNull(service.screenProcessor)
        } finally {
            controller.destroy()
            thread.join(5_000)
        }
    }

    @Test
    fun `foreground notification reopens the existing capture Activity`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        try {
            service.startForegroundNotification()
            val notification = requireNotNull(shadowOf(service).lastForegroundNotification)
            val intent = shadowOf(notification.contentIntent).savedIntent
            assertEquals(ScreenShareMasterActivity::class.java.name, intent.component?.className)
            val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            assertEquals(flags, intent.flags and flags)
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val resolved = service.packageManager.resolveActivity(intent, 0)
            assertEquals(ScreenShareMasterActivity::class.java.name, resolved?.activityInfo?.name)
        } finally {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            controller.destroy()
            thread.join(5_000)
        }
    }

    @Test
    fun `destroy drains a queued release before quitting even when flush fails`() =
        checkDestroyedCapture(queueRelease = true, failFlush = true)

    @Test
    fun `destroy alone releases the published capture and quits its worker`() =
        checkDestroyedCapture(queueRelease = false, failFlush = false)

    @Test
    fun `processor cleanup failures still close output stop projection and quit`() =
        checkDestroyedCapture(queueRelease = true, failFlush = false, failProcessor = true)

    @Test
    fun `output close failure still stops projection and quits`() =
        checkDestroyedCapture(queueRelease = true, failFlush = false, failClose = true)

    @Test
    fun `system projection stop cleans capture and an old callback cannot stop its replacement`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val handler = field(service, "serviceHandler") as Handler
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        val callbacks = prepareProjectionCallbacks(service, processor, events).callbacks
        var closes = 0
        setField(
            service,
            "videoH26xOs",
            BufferedOutputStream(object : OutputStream() {
                override fun write(value: Int) = Unit
                override fun close() {
                    closes++
                }
            })
        )
        val setting = ScreenShareSetting(320, 240, 160)
        try {
            service.setData(-1, Intent())
            service.startScreenShare(setting)
            assertNotNull("Service must own a system-stop callback", callbacks.firstOrNull())
            assertTrue(events.indexOf("register") < events.indexOf("init"))
            val oldCallback = callbacks.first()
            oldCallback.onStop()
            val cleaned = CountDownLatch(1)
            handler.post { cleaned.countDown() }
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            assertNull(service.screenProcessor)
            assertEquals(1, closes)
            assertEquals(1, events.count { it == "stop" })
            assertEquals(1, events.count { it == "release" })
            assertEquals(1, events.count { it == "unregister" })

            val replacement = RecordingProcessor(events, false) { service.screenProcessor }
            every { anyConstructed<ScreenCapture.Builder>().build() } returns replacement
            service.setData(-1, Intent())
            service.startScreenShare(setting)
            oldCallback.onStop()
            val oldNotificationDrained = CountDownLatch(1)
            handler.post { oldNotificationDrained.countDown() }
            assertTrue(oldNotificationDrained.await(5, TimeUnit.SECONDS))
            assertSame(replacement, service.screenProcessor)
            assertEquals(1, events.count { it == "release" })
        } finally {
            controller.destroy()
            thread.join(5_000)
            thread.quitSafely()
        }
    }

    @Test
    fun `startup failure unregisters the service projection callback`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val events = CopyOnWriteArrayList<String>()
        val failure = IllegalStateException("start failed")
        val processor = RecordingProcessor(events, false) { service.screenProcessor }.apply {
            startFailure = failure
        }
        val callbacks = prepareProjectionCallbacks(service, processor, events).callbacks
        try {
            service.setData(-1, Intent())
            val caught = runCatching {
                service.startScreenShare(ScreenShareSetting(320, 240, 160))
            }.exceptionOrNull()
            assertSame(failure, caught)
            assertEquals(1, callbacks.size)
            assertEquals(1, events.count { it == "unregister" })
            assertEquals(1, events.count { it == "release" })
            assertNull(service.screenProcessor)
        } finally {
            controller.destroy()
            thread.join(5_000)
            thread.quitSafely()
        }
    }

    @Test
    fun `failed callback unregister remains owned and is retried before a new capture`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val handler = field(service, "serviceHandler") as Handler
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        val fixture = prepareProjectionCallbacks(service, processor, events)
        var unregisters = 0
        every { fixture.projection.unregisterCallback(any()) } answers {
            unregisters++
            if (unregisters == 1) error("OEM unregister failure")
        }
        val setting = ScreenShareSetting(320, 240, 160)
        try {
            service.setData(-1, Intent())
            service.startScreenShare(setting)
            service.stopScreenShare()
            val cleaned = CountDownLatch(1)
            handler.post { cleaned.countDown() }
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            assertNull(service.screenProcessor)
            assertEquals(1, events.count { it == "release" })
            assertEquals(1, unregisters)
            val replacement = RecordingProcessor(events, false) { service.screenProcessor }
            every { anyConstructed<ScreenCapture.Builder>().build() } returns replacement
            service.setData(-1, Intent())
            service.startScreenShare(setting)
            assertEquals("New registration must not overwrite a failed unregister", 2, unregisters)
            assertEquals(2, fixture.callbacks.size)
            assertSame(replacement, service.screenProcessor)
        } finally {
            controller.destroy()
            thread.join(5_000)
            thread.quitSafely()
        }
    }

    @Test
    fun `rotation rejection while terminal notification is pending stays inside the service`() =
        checkWorkRejection(rotation = true)

    @Test
    fun `screenshot rejection while terminal notification is pending stays inside the service`() =
        checkWorkRejection(rotation = false)

    private fun checkWorkRejection(rotation: Boolean) {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val handler = field(service, "serviceHandler") as Handler
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        val fixture = prepareProjectionCallbacks(service, processor, events)
        try {
            service.setData(-1, Intent())
            service.startScreenShare(ScreenShareSetting(320, 240, 160))
            processor.workFailure = IllegalStateException("Strategy is already terminal")

            val rejected = runCatching {
                if (rotation) service.changeOrientation() else service.takeScreenshot(null, null)
            }.exceptionOrNull()
            assertNull("Strategy rejection must not escape the Activity-facing entry", rejected)

            fixture.listeners.single().onError(IllegalStateException("Retirement completed"))
            val cleaned = CountDownLatch(1)
            handler.post { cleaned.countDown() }
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            assertNull(service.screenProcessor)
            assertEquals(1, events.count { it == "release" })
        } finally {
            controller.destroy()
            thread.join(5_000)
        }
    }

    @Test
    fun `busy screenshot rejection leaves recording available for the next request`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        prepareProjectionCallbacks(service, processor, events)
        try {
            service.setData(-1, Intent())
            service.startScreenShare(ScreenShareSetting(320, 240, 160))
            processor.workFailure = IllegalStateException("Screenshot queue is full")
            val rejected = runCatching { service.takeScreenshot(null, null) }.exceptionOrNull()
            assertNull("A busy capture must not crash the UI", rejected)
            assertSame(processor, service.screenProcessor)
            assertFalse(events.contains("release"))

            processor.workFailure = null
            service.takeScreenshot(null, null)
            assertNotNull(processor.screenshot)
        } finally {
            controller.destroy()
            thread.join(5_000)
        }
    }

    @Test
    fun `key frame request cannot overlap independent strategy retirement`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val codec = mockk<MediaCodec>(relaxed = true)
        val strategy = ScreenRecordMediaCodecStrategy.Builder(
            320, 240, 160, null, mockk(relaxed = true)
        ).build()
        ReflectionHelpers.setField(strategy, "h26xEncoder", codec)
        val sessionClass = ScreenRecordMediaCodecStrategy::class.java.declaredClasses
            .single { it.simpleName == "EncoderSession" }
        val session = sessionClass.getDeclaredConstructor(MediaCodec::class.java).apply {
            isAccessible = true
        }.newInstance(codec)
        ReflectionHelpers.setField(strategy, "encoderSession", session)
        ReflectionHelpers.getField<AtomicBoolean>(strategy, "videoEncoderLoop").set(true)
        ReflectionHelpers.getField<AtomicBoolean>(strategy, "encoderStarted").set(true)
        setField(service, "screenProcessor", strategy)
        setField(service, "acceptingWork", true)
        val requesting = CountDownLatch(1)
        val continueRequest = CountDownLatch(1)
        val nativeReleased = CountDownLatch(1)
        every { codec.setParameters(any()) } answers {
            requesting.countDown()
            check(continueRequest.await(5, TimeUnit.SECONDS))
        }
        every { codec.release() } answers { nativeReleased.countDown() }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val request = executor.submit { service.triggerIFrame() }
            assertTrue(requesting.await(2, TimeUnit.SECONDS))
            val retirement = executor.submit { strategy.onRelease() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (ReflectionHelpers.getField<MediaCodec?>(strategy, "h26xEncoder") != null &&
                System.nanoTime() < deadline
            ) {
                Thread.sleep(1)
            }
            assertNull(ReflectionHelpers.getField<MediaCodec?>(strategy, "h26xEncoder"))
            try {
                assertFalse(
                    "Native release must wait for the active key-frame operation",
                    nativeReleased.await(200, TimeUnit.MILLISECONDS)
                )
            } finally {
                continueRequest.countDown()
                request.get(2, TimeUnit.SECONDS)
                retirement.get(2, TimeUnit.SECONDS)
            }
            assertEquals(0L, nativeReleased.count)
        } finally {
            continueRequest.countDown()
            controller.destroy()
            thread.join(5_000)
            executor.shutdownNow()
        }
    }

    private data class ProjectionFixture(
        val projection: MediaProjection,
        val callbacks: MutableList<MediaProjection.Callback>,
        val listeners: MutableList<ScreenDataListener>,
    )

    @Test
    fun `native failure closes the service owner and an old failure cannot stop its replacement`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val handler = field(service, "serviceHandler") as Handler
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        val fixture = prepareProjectionCallbacks(service, processor, events)
        var closes = 0
        setField(
            service,
            "videoH26xOs",
            BufferedOutputStream(object : OutputStream() {
                override fun write(value: Int) = Unit
                override fun close() {
                    closes++
                }
            })
        )
        val setting = ScreenShareSetting(320, 240, 160)
        try {
            service.setData(-1, Intent())
            service.startScreenShare(setting)
            val oldListener = fixture.listeners.single()
            oldListener.onError(IllegalStateException("native producer failed"))
            val cleaned = CountDownLatch(1)
            handler.post { cleaned.countDown() }
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            assertNull(service.screenProcessor)
            assertEquals(1, closes)
            val replacement = RecordingProcessor(events, false) { service.screenProcessor }
            every { anyConstructed<ScreenCapture.Builder>().build() } returns replacement
            service.setData(-1, Intent())
            service.startScreenShare(setting)
            oldListener.onError(IllegalStateException("old failure delivered late"))
            val failureDrained = CountDownLatch(1)
            handler.post { failureDrained.countDown() }
            assertTrue(failureDrained.await(5, TimeUnit.SECONDS))
            assertSame(replacement, service.screenProcessor)
            assertEquals(1, events.count { it == "release" })
        } finally {
            controller.destroy()
            thread.join(5_000)
            thread.quitSafely()
        }
    }

    private fun prepareProjectionCallbacks(
        service: MediaProjectionService,
        processor: ScreenProcessor,
        events: MutableList<String>,
    ): ProjectionFixture {
        val callbacks = CopyOnWriteArrayList<MediaProjection.Callback>()
        val listeners = CopyOnWriteArrayList<ScreenDataListener>()
        val projection = mockk<MediaProjection>(relaxed = true)
        every { projection.registerCallback(any(), any()) } answers {
            callbacks.add(firstArg())
            events.add("register")
            Unit
        }
        every { projection.unregisterCallback(any()) } answers {
            events.add("unregister")
            Unit
        }
        mockkConstructor(MediaProjectionManager::class, ScreenCapture.Builder::class)
        every {
            anyConstructed<MediaProjectionManager>().getMediaProjection(any(), any())
        } returns projection
        mockkObject(service, recordPrivateCalls = true)
        every { service["screenDataListenerFor"](any<Long>()) } answers {
            val listener = callOriginal() as ScreenDataListener
            listeners.add(listener)
            listener
        }
        every { anyConstructed<ScreenCapture.Builder>().build() } returns processor
        return ProjectionFixture(projection, callbacks, listeners)
    }

    @Test
    fun `unbind releases the capture while leaving the service thread available`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val handler = field(service, "serviceHandler") as Handler
        val thread = field(service, "serviceThread") as HandlerThread
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        setField(service, "screenProcessor", processor)
        try {
            service.onUnbind(Intent())
            val cleaned = CountDownLatch(1)
            handler.post { cleaned.countDown() }
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("stop", "release"), events.toList())
            assertNull(service.screenProcessor)
            assertTrue(thread.isAlive)
        } finally {
            controller.destroy()
            thread.quitSafely()
            thread.join(5_000)
        }
    }

    @Test
    fun `stop rejects late screenshots and recycles a previously requested result`() =
        checkLateScreenshotResult(replace = false)

    @Test
    fun `old screenshot cannot be saved into a replacement capture`() =
        checkLateScreenshotResult(replace = true)

    @Test
    fun `rejected screenshot save post recycles its bitmap`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        mockkConstructor(MediaProjectionManager::class, ScreenCapture.Builder::class)
        every {
            anyConstructed<MediaProjectionManager>().getMediaProjection(any(), any())
        } returns mockk(relaxed = true)
        every { anyConstructed<ScreenCapture.Builder>().build() } returns processor
        try {
            service.setData(-1, Intent())
            service.startScreenShare(ScreenShareSetting(320, 240, 160))
            service.takeScreenshot(null, null)
            val callback = requireNotNull(processor.screenshot)
            thread.quitSafely()
            thread.join(5_000)
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            callback(bitmap)
            assertTrue(bitmap.isRecycled)
            controller.destroy()
            assertNull(service.screenProcessor)
        } finally {
            thread.quitSafely()
            thread.join(5_000)
        }
    }

    @Test
    fun `in flight screenshot save finishes before capture cleanup releases its owner`() {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        mockkConstructor(MediaProjectionManager::class, ScreenCapture.Builder::class)
        every {
            anyConstructed<MediaProjectionManager>().getMediaProjection(any(), any())
        } returns mockk(relaxed = true)
        every { anyConstructed<ScreenCapture.Builder>().build() } returns processor
        val saving = CountDownLatch(1)
        val finishSave = CountDownLatch(1)
        val savingThread = AtomicReference<Thread>()
        mockkStatic("com.leovp.image.ImageExtKt")
        every { any<Bitmap>().writeToFile(any(), any(), any()) } answers {
            savingThread.set(Thread.currentThread())
            saving.countDown()
            check(finishSave.await(5, TimeUnit.SECONDS))
            callOriginal()
            events.add("saved")
            Unit
        }
        val setting = ScreenShareSetting(320, 240, 160)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        var callbackThread: Thread? = null
        try {
            service.setData(-1, Intent())
            service.startScreenShare(setting)
            service.takeScreenshot(null, null)
            val callback = requireNotNull(processor.screenshot)
            callbackThread = Thread { callback(bitmap) }.apply { start() }
            assertTrue(saving.await(5, TimeUnit.SECONDS))
            assertSame(
                "Screenshot IO must share the capture cleanup FIFO",
                thread,
                savingThread.get()
            )
            service.stopScreenShare()
            service.startScreenShare(setting)
            controller.destroy()
            assertSame(processor, service.screenProcessor)
            assertFalse(events.contains("release"))
            assertEquals(1, events.count { it == "init" })
            finishSave.countDown()
            callbackThread.join(5_000)
            thread.join(5_000)
            assertFalse(thread.isAlive)
            assertTrue(bitmap.isRecycled)
            assertTrue(events.indexOf("saved") < events.indexOf("stop"))
            assertNull(service.screenProcessor)
        } finally {
            finishSave.countDown()
            callbackThread?.join(5_000)
            thread.quitSafely()
            thread.join(5_000)
        }
    }

    private fun checkLateScreenshotResult(replace: Boolean) {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val thread = field(service, "serviceThread") as HandlerThread
        val handler = field(service, "serviceHandler") as Handler
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, false) { service.screenProcessor }
        mockkConstructor(MediaProjectionManager::class, ScreenCapture.Builder::class)
        every {
            anyConstructed<MediaProjectionManager>().getMediaProjection(any(), any())
        } returns mockk(relaxed = true)
        every { anyConstructed<ScreenCapture.Builder>().build() } returns processor
        service.setData(-1, Intent())
        val setting = ScreenShareSetting(320, 240, 160)
        try {
            service.startScreenShare(setting)
            assertSame(processor, service.screenProcessor)
            service.takeScreenshot(null, null)
            val callback = requireNotNull(processor.screenshot)
            service.stopScreenShare()
            service.takeScreenshot(null, null)
            service.changeOrientation()
            val cleaned = CountDownLatch(1)
            handler.post { cleaned.countDown() }
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            if (replace) {
                val replacement = RecordingProcessor(events, false) { service.screenProcessor }
                every { anyConstructed<ScreenCapture.Builder>().build() } returns replacement
                service.setData(-1, Intent())
                service.startScreenShare(setting)
                assertSame(replacement, service.screenProcessor)
            }
            val screenshotFile = File(service.getBaseDirString("screenshot"), "screenshot.jpg")
            screenshotFile.delete()
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            callback(bitmap)
            assertTrue("Late screenshot ownership must be discharged", bitmap.isRecycled)
            assertFalse("Late screenshot must not create an output file", screenshotFile.exists())
            assertEquals(1, events.count { it == "screenshot" })
            assertFalse(events.contains("rotate"))
            controller.destroy()
            thread.join(5_000)
            service.startScreenShare(setting)
            assertEquals(
                "Destroyed service cannot initialize a new capture",
                if (replace) 2 else 1,
                events.count { it == "init" }
            )
            assertNull(service.screenProcessor)
        } finally {
            thread.quitSafely()
            thread.join(5_000)
        }
    }

    private fun checkDestroyedCapture(
        queueRelease: Boolean,
        failFlush: Boolean,
        failProcessor: Boolean = false,
        failClose: Boolean = false,
    ) {
        val controller = Robolectric.buildService(MediaProjectionService::class.java).create()
        val service = controller.get()
        val handler = field(service, "serviceHandler") as Handler
        val thread = field(service, "serviceThread") as HandlerThread
        val events = CopyOnWriteArrayList<String>()
        val processor = RecordingProcessor(events, failProcessor) { service.screenProcessor }
        val projection = mockk<MediaProjection>()
        every { projection.stop() } answers {
            assertSame(processor, service.screenProcessor)
            events.add("projection")
            Unit
        }
        val output = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun flush() {
                events.add("flush")
                if (failFlush) throw IOException("flush failed")
            }
            override fun close() {
                events.add("close")
                if (failClose) throw IOException("close failed")
            }
        }
        setField(service, "screenProcessor", processor)
        setField(service, "mediaProjection", projection)
        setField(service, "videoH26xOs", BufferedOutputStream(output))
        service.outputH26xFile = true
        val occupied = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        handler.post {
            occupied.countDown()
            check(proceed.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(occupied.await(5, TimeUnit.SECONDS))
            if (queueRelease) {
                service.onReleaseScreenShare()
                service.onUnbind(Intent())
            }
            controller.destroy()
            assertSame(
                "Cleanup retains the owner until every resource is released",
                processor,
                service.screenProcessor
            )
            assertTrue(thread.isAlive)
            proceed.countDown()
            thread.join(5_000)

            assertFalse("Service destroy must quit its HandlerThread after cleanup", thread.isAlive)
            assertEquals(1, events.count { it == "stop" })
            assertEquals(1, events.count { it == "release" })
            assertEquals(1, events.count { it == "close" })
            assertEquals(1, events.count { it == "projection" })
            assertTrue(events.indexOf("stop") < events.indexOf("release"))
            assertTrue(events.indexOf("release") < events.indexOf("close"))
            assertNull(service.screenProcessor)
        } finally {
            proceed.countDown()
            thread.quitSafely()
            thread.join(5_000)
        }
    }

    private class RecordingProcessor(
        private val events: MutableList<String>,
        private val failCleanup: Boolean,
        private val owner: () -> ScreenProcessor?,
    ) : ScreenProcessor {
        var screenshot: ((Bitmap) -> Unit)? = null
        var startFailure: Throwable? = null
        var workFailure: Exception? = null
        override fun onInit() {
            events.add("init")
        }
        override fun onStart() {
            startFailure?.let { throw it }
        }
        override fun onStop() {
            assertSame(this, owner())
            events.add("stop")
            if (failCleanup) error("processor stop failed")
        }
        override fun onRelease() {
            assertSame(this, owner())
            events.add("release")
            if (failCleanup) error("processor release failed")
        }
        override fun getVideoSize() = Size(320, 240)
        override fun changeOrientation() {
            workFailure?.let { throw it }
            events.add("rotate")
        }
        override fun takeScreenshot(width: Int?, height: Int?, result: (Bitmap) -> Unit) {
            workFailure?.let { throw it }
            events.add("screenshot")
            screenshot = result
        }
    }

    private fun field(service: MediaProjectionService, name: String): Any? =
        MediaProjectionService::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(service)
        }

    private fun setField(service: MediaProjectionService, name: String, value: Any) {
        MediaProjectionService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }
}
