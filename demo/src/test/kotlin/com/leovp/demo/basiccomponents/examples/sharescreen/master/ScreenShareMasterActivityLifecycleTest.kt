package com.leovp.demo.basiccomponents.examples.sharescreen.master

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.Intent
import android.app.Activity
import android.os.Looper
import android.widget.TextView
import com.leovp.android.exts.screenSurfaceRotation
import com.leovp.demo.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.netty.channel.Channel
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import androidx.activity.result.ActivityResultLauncher
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenShareMasterActivityLifecycleTest {
    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun `opening the page only binds without starting a foreground service`() {
        val application = RuntimeEnvironment.getApplication()
        val service = mockk<MediaProjectionService>(relaxed = true)
        configureBinding(service)
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).setup()
        val activity = controller.get()
        try {
            assertSame(service, field(activity, "mediaProjectService"))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
            // Robolectric 4.16.1 records a successful bind in allStartedServices too.
            assertEquals(
                "Only the bind request may exist before consent: ${serviceRequestDetails()}",
                1,
                shadowOf(application).allStartedServices.size
            )
            assertTrue(
                shadowOf(application).boundServiceConnections.contains(
                    field(activity, "serviceConn") as ServiceConnection
                )
            )
        } finally {
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `permission success starts the service before immediate promotion and capture`() {
        val application = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val service = mockk<MediaProjectionService>(relaxed = true)
        configureBinding(service)
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).setup()
        val activity = controller.get()
        suppressNetworkServer(activity)
        every { service.startForegroundNotification() } answers {
            assertEquals(
                "Bind plus foreground start must precede promotion: ${serviceRequestDetails()}",
                2,
                shadowOf(application).allStartedServices.size
            )
            events.add("foreground")
            Unit
        }
        every { service.setData(any(), any()) } answers {
            events.add("data")
            Unit
        }
        every { service.startScreenShare(any()) } answers {
            events.add("capture")
            Unit
        }
        try {
            assertEquals(serviceRequestDetails(), 1, shadowOf(application).allStartedServices.size)
            dispatchCaptureResult(activity, Activity.RESULT_OK, Intent("capture-consent"))
            assertEquals(listOf("foreground", "data", "capture"), events)
            assertEquals(serviceRequestDetails(), 2, shadowOf(application).allStartedServices.size)
            discardNetworkWork(activity)
            controller.pause().stop().destroy()
            discardNetworkWork(activity)
            val stopped = shadowOf(application).nextStoppedService
            assertEquals(MediaProjectionService::class.java.name, stopped?.component?.className)
        } finally {
            discardNetworkWork(activity)
            if (!activity.isDestroyed) controller.pause().stop().destroy()
            discardNetworkWork(activity)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `permission success without a connected owner never starts a service`() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).declareComponentUnbindable(
            ComponentName(application, MediaProjectionService::class.java)
        )
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).setup()
        val activity = controller.get()
        suppressNetworkServer(activity)
        try {
            dispatchCaptureResult(activity, Activity.RESULT_OK, Intent("capture-consent"))
            assertTrue(shadowOf(application).allStartedServices.isEmpty())
        } finally {
            discardNetworkWork(activity)
            controller.pause().stop().destroy()
            discardNetworkWork(activity)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `notification failure stops the started service and propagates the original failure`() {
        val application = RuntimeEnvironment.getApplication()
        val service = mockk<MediaProjectionService>(relaxed = true)
        configureBinding(service)
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).setup()
        val activity = controller.get()
        suppressNetworkServer(activity)
        val failure = IllegalStateException("notification failed")
        every { service.startForegroundNotification() } throws failure
        try {
            val thrown = assertThrows(IllegalStateException::class.java) {
                dispatchCaptureResult(activity, Activity.RESULT_OK, Intent("capture-consent"))
            }
            assertSame(failure, thrown)
            val stopped = shadowOf(application).nextStoppedService
            assertNotNull("Failed promotion must revoke the foreground start request", stopped)
            assertEquals(MediaProjectionService::class.java.name, stopped?.component?.className)
        } finally {
            discardNetworkWork(activity)
            controller.pause().stop().destroy()
            discardNetworkWork(activity)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun suppressNetworkServer(activity: ScreenShareMasterActivity) {
        // Network setup is launched on this scope. Keep its test dispatcher unadvanced so no
        // socket or event loop group is constructed while checking foreground-service wiring.
        (field(activity, "cs") as CoroutineScope).cancel()
        setField(activity, "cs", CoroutineScope(SupervisorJob() + StandardTestDispatcher()))
    }

    private fun serviceRequestDetails(): String {
        val app = shadowOf(RuntimeEnvironment.getApplication())
        val requests = app.allStartedServices.map {
            "${it.component?.flattenToShortString()} flags=${it.flags}"
        }
        return "bound=${app.boundServiceConnections.size}, bind/start records=$requests"
    }

    private fun discardNetworkWork(activity: ScreenShareMasterActivity) {
        (field(activity, "cs") as CoroutineScope).cancel()
    }

    private fun dispatchCaptureResult(
        activity: ScreenShareMasterActivity,
        resultCode: Int,
        data: Intent,
    ) {
        val shadow = shadowOf(activity)
        while (shadow.nextStartedActivityForResult != null) {
            // Ignore the existing overlay-permission request from Activity setup.
        }
        @Suppress("UNCHECKED_CAST")
        val launcher = field(activity, "activityResultLauncher") as ActivityResultLauncher<Intent>
        launcher.launch(Intent("request-capture-consent"))
        val request = requireNotNull(shadow.nextStartedActivityForResult)
        assertTrue(
            activity.activityResultRegistry.dispatchResult(request.requestCode, resultCode, data)
        )
    }

    @Test
    fun `destroy cancels the rotation timer and rejects an already queued rotation`() {
        val rotations = AtomicInteger()
        val service = mockk<MediaProjectionService>(relaxed = true)
        every { service.changeOrientation() } answers {
            rotations.incrementAndGet()
            Unit
        }
        configureBinding(service)
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val timer = field(activity, "testTimer") as Timer
        val task = field(activity, "testTimerTask") as TimerTask
        assertSame(service, field(activity, "mediaProjectService"))
        // The locale configuration Context has no associated Display in this Robolectric setup.
        // Control only physical rotation; this test exercises observer delivery and teardown.
        mockkStatic("com.leovp.android.exts.DeviceExtKt")
        every { activity.screenSurfaceRotation } returns 0
        // Disable automatic ticks while preserving the real Timer, then drive this task manually.
        task.cancel()
        setField(activity, "lastScreenRotation", -1)
        try {
            val activeTick = Thread { task.run() }
            activeTick.start()
            activeTick.join(5_000)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("The live observer must still deliver rotations", 1, rotations.get())
            setField(activity, "lastScreenRotation", -1)
            val oldTick = Thread { task.run() }
            oldTick.start()
            oldTick.join(5_000)
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, rotations.get())
            assertNull(field(activity, "testTimer"))
            assertNull(field(activity, "testTimerTask"))
            assertThrows(IllegalStateException::class.java) {
                timer.schedule(
                    object : TimerTask() {
                        override fun run() = Unit
                    },
                    60_000
                )
            }
        } finally {
            timer.cancel()
        }
    }

    @Test
    fun `destroy before service connection unbinds the request and rejects late connection`() {
        val application = RuntimeEnvironment.getApplication()
        val service = mockk<MediaProjectionService>(relaxed = true)
        val installs = AtomicInteger()
        every { service.screenDataUpdateListener = any() } answers {
            installs.incrementAndGet()
            Unit
        }
        val binder = configureBinding(service)
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).create()
        val activity = controller.get()
        val connection = field(activity, "serviceConn") as ServiceConnection
        controller.destroy()
        assertTrue(shadowOf(application).unboundServiceConnections.contains(connection))
        assertNull(
            "A pending bind has no started-service ownership",
            shadowOf(application).nextStoppedService
        )
        connection.onServiceConnected(
            ComponentName(application, MediaProjectionService::class.java),
            binder
        )
        assertEquals(0, installs.get())
        assertNull(field(activity, "mediaProjectService"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `normal service connection is installed once and released on destroy`() {
        val application = RuntimeEnvironment.getApplication()
        val service = mockk<MediaProjectionService>(relaxed = true)
        configureBinding(service)
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).create()
        val activity = controller.get()
        val connection = field(activity, "serviceConn") as ServiceConnection
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(field(activity, "bound") as Boolean)
        controller.destroy()
        assertTrue(shadowOf(application).unboundServiceConnections.contains(connection))
        assertNull(field(activity, "mediaProjectService"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `failed binding is not unbound and creates no started service`() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).declareComponentUnbindable(
            ComponentName(application, MediaProjectionService::class.java)
        )
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).create()
        val activity = controller.get()
        val connection = field(activity, "serviceConn") as ServiceConnection
        controller.destroy()
        assertFalse(shadowOf(application).unboundServiceConnections.contains(connection))
        assertTrue(shadowOf(application).allStartedServices.isEmpty())
        assertNull(shadowOf(application).nextStoppedService)
    }

    @Test
    fun `queued data update cannot change the destroyed Activity UI`() =
        checkQueuedDataUpdate(destroy = true, replace = false)

    @Test
    fun `queued data update cannot change UI owned by a replacement service`() =
        checkQueuedDataUpdate(destroy = false, replace = true)

    @Test
    fun `current service data still updates the active Activity UI`() =
        checkQueuedDataUpdate(destroy = false, replace = false)

    private fun checkQueuedDataUpdate(destroy: Boolean, replace: Boolean) {
        val service = mockk<MediaProjectionService>(relaxed = true)
        var listener: ScreenDataUpdateListener? = null
        every { service.screenDataUpdateListener = any() } answers { listener = firstArg() }
        configureBinding(service)
        val controller = Robolectric.buildActivity(ScreenShareMasterActivity::class.java).create()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val oldListener = requireNotNull(listener)
        val text = activity.findViewById<TextView>(R.id.txtInfo)
        text.text = "sentinel"
        setField(activity, "clientChannel", mockk<Channel>(relaxed = true))
        setField(
            activity,
            "webSocketServerHandler",
            mockk<ScreenShareMasterActivity.WebSocketServerHandler>(relaxed = true)
        )
        try {
            val producer = Thread { oldListener.onUpdate(byteArrayOf(1, 2), 7, 100) }
            producer.start()
            producer.join(5_000)
            if (replace) {
                val replacement = mockk<MediaProjectionService>(relaxed = true)
                val binder = configureBinding(replacement)
                val connection = field(activity, "serviceConn") as ServiceConnection
                connection.onServiceConnected(
                    ComponentName(activity, MediaProjectionService::class.java),
                    binder
                )
            }
            if (destroy) controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
            val expected = if (destroy || replace) {
                "sentinel"
            } else {
                "flags=7 Data length=2 presentationTimeUs=100"
            }
            assertEquals(expected, text.text.toString())
        } finally {
            if (!destroy) controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun configureBinding(
        service: MediaProjectionService,
    ): MediaProjectionService.CustomBinder {
        val application = RuntimeEnvironment.getApplication()
        val binder = mockk<MediaProjectionService.CustomBinder>(relaxed = true)
        every { binder.service } returns service
        shadowOf(application).setComponentNameAndServiceForBindService(
            ComponentName(application, MediaProjectionService::class.java),
            binder
        )
        return binder
    }

    private fun field(activity: ScreenShareMasterActivity, name: String): Any? =
        ScreenShareMasterActivity::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(activity)
        }

    private fun setField(activity: ScreenShareMasterActivity, name: String, value: Any) {
        ScreenShareMasterActivity::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(activity, value)
        }
    }
}
