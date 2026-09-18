package com.leovp.demo.basiccomponents.examples

import android.content.res.Configuration
import android.media.MediaCodec
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.leovp.android.exts.densityDpi
import com.leovp.android.exts.getBaseDirString
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.android.exts.toast
import com.leovp.android.utils.LangUtil
import com.leovp.bytes.toHexString
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.sharescreen.master.ScreenShareSetting
import com.leovp.demo.databinding.ActivityScreenshotRecordH264Binding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import com.leovp.screencapture.screenrecord.ScreenCapture
import com.leovp.screencapture.screenrecord.base.ScreenDataListener
import com.leovp.screencapture.screenrecord.base.strategies.ScreenRecordMediaCodecStrategy
import com.leovp.screencapture.screenrecord.base.strategies.Screenshot2H26xStrategy
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class RecordSingleAppScreenActivity :
    BaseDemonstrationActivity<ActivityScreenshotRecordH264Binding>(
        R.layout.activity_screenshot_record_h264
    ) {
    override fun getTagName(): String = ITAG

    companion object {
        val VIDEO_ENCODE_TYPE = ScreenRecordMediaCodecStrategy.EncodeType.H265
        private const val RELEASE_TIMEOUT_MS = 10_000L
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityScreenshotRecordH264Binding =
        ActivityScreenshotRecordH264Binding.inflate(layoutInflater)

    private val outputLock = Any()
    private val cleanupStarted = AtomicBoolean(false)

    /**
     * Identifies the recording session a recorder belongs to. A recorder that failed to release
     * keeps running and still holds this Activity through its listener, so its callbacks must be
     * dropped instead of reaching the next session's output stream.
     */
    private val activeSession = AtomicInteger(0)
    private var videoH26xOsForDebug: BufferedOutputStream? = null

    /**
     * The last capture geometry seen, in the units [Configuration] reports directly, plus the
     * density the encoder was sized against. Used only to tell a real geometry change from a
     * configuration change that leaves the capture alone.
     *
     * Density belongs here because the manifest now keeps this Activity alive across a density
     * change too: `screenWidthDp`/`screenHeightDp` are density-independent and would report
     * "nothing moved" while the pixel size the encoder is configured for has in fact changed.
     */
    private var lastConfigGeometry: Triple<Int, Int, Int>? = null
    private lateinit var screenProcessor: Screenshot2H26xStrategy
    private lateinit var recorderSetting: ScreenShareSetting

    private fun screenDataListenerFor(session: Int) = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any, flags: Int, presentationTimeUs: Long) {
            if (session != activeSession.get()) return
            val data = buffer as ByteArray
            when (flags) {
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> LogContext.log.i(
                    ITAG,
                    "Get $VIDEO_ENCODE_TYPE data[${data.size}]=${data.toHexString()} " +
                        "presentationTimeUs=$presentationTimeUs"
                )

                MediaCodec.BUFFER_FLAG_KEY_FRAME -> {
                    LogContext.log.i(
                        ITAG,
                        "Get $VIDEO_ENCODE_TYPE data Key-Frame[${data.size}] " +
                            "presentationTimeUs=$presentationTimeUs"
                    )
                }

                else -> LogContext.log.i(
                    ITAG,
                    "Get $VIDEO_ENCODE_TYPE data[${data.size}] " +
                        "presentationTimeUs=$presentationTimeUs"
                )
            }
            synchronized(outputLock) {
                // The authoritative check. The early exit above only saves the logging work: a
                // retired recorder can pass it and then be descheduled until the next session
                // has opened its own stream. Retiring a session and swapping the stream both
                // happen under this lock, so a stale writer can never reach the new file.
                if (session != activeSession.get()) return
                runCatching { videoH26xOsForDebug?.write(data) }
                    .onFailure { LogContext.log.e(ITAG, "Write screen recording data failed", it) }
            }
        }

        override fun onError(error: Throwable) {
            // A recorder that outlived its session must not stop the recording that replaced it.
            if (session != activeSession.get()) return
            LogContext.log.e(ITAG, "Screenshot recording failed", error)
            runOnUiThread {
                // Checked again here, not only above: the session can advance between posting
                // this and running it, and switching the toggle off would then stop the healthy
                // recording that replaced the one this error belongs to.
                if (session != activeSession.get()) return@runOnUiThread
                binding.toggleBtn.isChecked = false
                toast("Unable to record screen")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastConfigGeometry = resources.configuration.toCaptureGeometry()
        recorderSetting = buildRecorderSetting()
        screenProcessor = createRecorder()

        // Without this the checked state survives a configuration change and the listener below
        // fires during state restore, starting a recording the user never asked for and
        // truncating the file the previous instance is still flushing.
        binding.toggleBtn.isSaveEnabled = false
        binding.toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startRecording()
            } else {
                releaseRecorder(restartable = true)
            }
        }
    }

    /**
     * The capture geometry for the screen as it is oriented right now.
     *
     * `this`, not `application`: [screenAvailableResolution] reads
     * `WindowManager.currentWindowMetrics`, which only follows the current rotation for a UI
     * context. Through the Application context it keeps reporting the portrait bounds, so a
     * recording started in landscape would be encoded at portrait width and height with every
     * captured frame stretched to fit.
     */
    private fun buildRecorderSetting(): ScreenShareSetting {
        val screenInfo = screenAvailableResolution
        return ScreenShareSetting(
            // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            (screenInfo.width * 0.8F / 16).toInt() * 16,
            // 800 1024 1280
            (screenInfo.height * 0.8F / 16).toInt() * 16,
            densityDpi
        ).apply {
            // FIXME This does not seem to work. Check setKeyFrameRate in createRecorder()
            fps = 5f
        }
    }

    /**
     * Rebuilds the recorder for the new screen geometry, resuming the recording if one was
     * running.
     *
     * The encoder is configured once, at a fixed width and height, and every captured frame is
     * drawn at that size ([Screenshot2H26xStrategy] draws with `builder.width`/`builder.height`).
     * MediaCodec cannot be reconfigured mid-stream, so a rotated capture would be stretched into
     * the old frame for the rest of the recording - and because H.26x carries that frame forward
     * through references, the distortion would stay visible. Ending the stream and starting a new
     * one is the only way to keep the picture true.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The app language rides on the Activity's own resources, applied in
        // BaseDemonstrationActivity.attachBaseContext. That ran once, at creation; this Activity
        // now survives the configuration changes listed in the manifest instead of being
        // recreated, so nothing would re-apply it and the UI would fall back to the device locale
        // - on API 21-24 especially, where LangUtil takes the Resources.updateConfiguration path
        // that the framework overwrites when it delivers the new configuration.
        LangUtil.getInstance(this).setAppLanguage(this)
        // Read from [newConfig] rather than from the window: WindowManager.currentWindowMetrics
        // is not guaranteed to carry the new bounds until the layout pass that applies them, and
        // a stale read here would report "nothing moved" and skip the rebuild entirely. The
        // values [Configuration] carries are already the new ones.
        val geometry = newConfig.toCaptureGeometry()
        if (geometry == lastConfigGeometry) {
            // A configuration change that leaves the capture alone - a keyboard or a locale
            // switch. Splitting the recording for it would cost a file for nothing.
            return
        }
        lastConfigGeometry = geometry
        // The teardown starts now, before another frame can be captured at the old geometry; the
        // new geometry is measured in [armNextRecording], which also decides whether to resume.
        releaseRecorder(restartable = true)
    }

    /**
     * The parts of a [Configuration] that decide how the encoder must be sized: the window in dp,
     * and the density that turns those dp into the pixels it is configured with.
     *
     * `this.densityDpi` is qualified on purpose. This file also imports the `Context.densityDpi`
     * extension, and while the [Configuration] member wins here, spelling it out keeps the two
     * apart for anyone editing this later.
     */
    private fun Configuration.toCaptureGeometry() =
        Triple(screenWidthDp, screenHeightDp, this.densityDpi)

    /**
     * Opens the debug output and starts recording. Opening truncates the file, so it must
     * happen when a recording starts and never while arming the next session, which would
     * wipe the recording that just finished.
     */
    private fun startRecording() {
        openVideoOutput()
        screenProcessor.startRecord(this)
    }

    /**
     * [Screenshot2H26xStrategy] is one-shot, so every recording session needs its own
     * recorder. Reusing a released one throws instead of recording.
     *
     * Retiring the previous session here, before [startRecording] can open the next output,
     * is what makes the guard in [screenDataListenerFor] sound: a stale writer holding
     * `outputLock` can only ever find the stream its own session opened.
     */
    private fun createRecorder(): Screenshot2H26xStrategy = ScreenCapture.Builder(
        recorderSetting.width,
        recorderSetting.height,
        recorderSetting.dpi,
        null,
        ScreenCapture.BY_IMAGE_2_H26X,
        screenDataListenerFor(activeSession.incrementAndGet())
    )
        .setEncodeType(VIDEO_ENCODE_TYPE)
        .setFps(recorderSetting.fps)
        .setKeyFrameRate(20)
        .setQuality(80)
        .setSampleSize(1)
        .build() as Screenshot2H26xStrategy

    private fun openVideoOutput() {
        // A unique name per session: a fixed name would let a new recording truncate the previous
        // one, and would let a recorder that outlived its session write into the new file.
        val dstFile = File(
            getBaseDirString("output"),
            "screen-${System.currentTimeMillis()}" + when (VIDEO_ENCODE_TYPE) {
                ScreenRecordMediaCodecStrategy.EncodeType.H264 -> ".h264"
                ScreenRecordMediaCodecStrategy.EncodeType.H265 -> ".h265"
            }
        )
        LogContext.log.i(tag, "dstFile=${dstFile.absolutePath}")
        synchronized(outputLock) {
            videoH26xOsForDebug = BufferedOutputStream(FileOutputStream(dstFile))
        }
    }

    override fun onDestroy() {
        releaseRecorder(restartable = false)
        super.onDestroy()
    }

    /**
     * Tears the current recording session down. The toggle stays disabled until teardown
     * finishes, because a recorder being released cannot accept a new recording.
     *
     * @param restartable true while this screen stays alive, so a fresh session is armed once
     * teardown completes. False from [onDestroy], where nothing should be rebuilt.
     */
    private fun releaseRecorder(restartable: Boolean) {
        if (!cleanupStarted.compareAndSet(false, true)) return
        binding.toggleBtn.isEnabled = false
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var released = false
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    released = !::screenProcessor.isInitialized || awaitRecorderRelease()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogContext.log.e(ITAG, "Release screenshot recorder failed", e)
                } finally {
                    closeVideoOutput()
                }
            }
            if (!restartable || isFinishing || isDestroyed) return@launch
            if (released) {
                armNextRecording()
            } else {
                // The abandoned recorder still owns the encoder and may still emit frames.
                // Arming another session would let it corrupt the next recording, so
                // [armNextRecording] - and with it any resume - is skipped entirely.
                toast("Recorder did not release. Screen recording is disabled.")
            }
        }
    }

    /**
     * Restores the screen to a recordable state: a fresh one-shot recorder and an enabled
     * toggle. Without this the button stays disabled for the rest of the Activity's life,
     * whether the user stopped the recording or it failed. The debug file is left untouched
     * here and is reopened by [startRecording], which is what truncates it.
     */
    private fun armNextRecording() {
        // Measured here rather than carried over from onConfigurationChanged. What makes the
        // reading current is not the hop through Dispatchers.IO - a coroutine resume and a
        // Choreographer traversal are both main-looper messages, in no guaranteed order - but
        // that the Activity's resources configuration, and so currentWindowMetrics, is already
        // updated before onConfigurationChanged is dispatched at all.
        recorderSetting = buildRecorderSetting()
        screenProcessor = createRecorder()
        cleanupStarted.set(false)
        binding.toggleBtn.isEnabled = true
        // The toggle is the intent, read at the moment of resuming rather than remembered from
        // when teardown began. A recorder failing mid-teardown clears the toggle through
        // onError, and its listener call finds cleanupStarted already set and returns without
        // doing anything - a remembered flag would still say "resume" and put the screen back
        // into recording right after telling the user it had stopped.
        if (binding.toggleBtn.isChecked) {
            // A new output file per segment, so each one holds a single resolution and stays
            // playable on its own. Appending to the old file would instead demand a decoder that
            // follows a mid-stream geometry change.
            startRecording()
        }
    }

    /**
     * Bounds how long teardown blocks this Activity's cleanup. A timeout only abandons the
     * wait: a recording thread stuck in a native call keeps running and still reaches this
     * Activity through the screen data listener, so the timeout is reported as an error
     * rather than treated as a completed release.
     *
     * @return true only when teardown finished within the budget **and** the recorder reported
     * it as clean. The recorder opens its own barrier even after abandoning a callback thread
     * that is still alive, so the budget alone does not prove the recorder is done with us.
     */
    private suspend fun awaitRecorderRelease(): Boolean {
        val clean = withTimeoutOrNull(RELEASE_TIMEOUT_MS.milliseconds) {
            screenProcessor.releaseAndJoin()
        }
        if (clean == true) return true
        // Retire the session so the abandoned recorder's callbacks are ignored from now on.
        activeSession.incrementAndGet()
        if (clean == null) {
            LogContext.log.e(ITAG, "Screenshot recorder did not release in ${RELEASE_TIMEOUT_MS}ms")
        } else {
            // Teardown finished, but the recorder abandoned a callback thread that is still
            // alive and may still emit frames. Treating this as a clean release is what would
            // let it write into the next session's file.
            LogContext.log.e(ITAG, "Screenshot recorder abandoned its callback thread")
        }
        return false
    }

    private fun closeVideoOutput() {
        synchronized(outputLock) {
            val output = videoH26xOsForDebug ?: return
            videoH26xOsForDebug = null
            runCatching {
                output.flush()
                output.close()
            }.onFailure { LogContext.log.e(ITAG, "Close screen recording output failed", it) }
        }
    }

    fun onShowToastClick(@Suppress("unused") view: View) {
        toast("Custom Toast")
    }

    fun onShowDialogClick(@Suppress("unused") view: View) {
        AlertDialog.Builder(this)
            .setTitle("Title")
            .setMessage("This is a dialog")
            .setPositiveButton("OK") { dlg, _ ->
                dlg.dismiss()
            }
            .setNeutralButton("Cancel", null)
            .create()
            .show()
    }
}
