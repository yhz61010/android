package com.leovp.demo.basiccomponents.examples

import android.media.MediaCodec
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.leovp.android.exts.densityDpi
import com.leovp.android.exts.getBaseDirString
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.android.exts.toast
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
    private var videoH26xOsForDebug: BufferedOutputStream? = null
    private lateinit var screenProcessor: Screenshot2H26xStrategy
    private lateinit var recorderSetting: ScreenShareSetting

    private val screenDataListener = object : ScreenDataListener {
        override fun onDataUpdate(buffer: Any, flags: Int, presentationTimeUs: Long) {
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
                runCatching { videoH26xOsForDebug?.write(data) }
                    .onFailure { LogContext.log.e(ITAG, "Write screen recording data failed", it) }
            }
        }

        override fun onError(error: Throwable) {
            LogContext.log.e(ITAG, "Screenshot recording failed", error)
            runOnUiThread {
                binding.toggleBtn.isChecked = false
                toast("Unable to record screen")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screenInfo = application.screenAvailableResolution
        recorderSetting = ScreenShareSetting(
            // 600 768 720     [1280, 960][1280, 720][960, 720][720, 480]
            (screenInfo.width * 0.8F / 16).toInt() * 16,
            // 800 1024 1280
            (screenInfo.height * 0.8F / 16).toInt() * 16,
            densityDpi
        )
        // FIXME This does not seem to work. Check below setKeyFrameRate
        recorderSetting.fps = 5f

        screenProcessor = createRecorder()

        binding.toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startRecording()
            } else {
                releaseRecorder(restartable = true)
            }
        }
    }

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
     */
    private fun createRecorder(): Screenshot2H26xStrategy = ScreenCapture.Builder(
        recorderSetting.width,
        recorderSetting.height,
        recorderSetting.dpi,
        null,
        ScreenCapture.BY_IMAGE_2_H26X,
        screenDataListener
    )
        .setEncodeType(VIDEO_ENCODE_TYPE)
        .setFps(recorderSetting.fps)
        .setKeyFrameRate(20)
        .setQuality(80)
        .setSampleSize(1)
        .build() as Screenshot2H26xStrategy

    private fun openVideoOutput() {
        val dstFile = File(
            getBaseDirString("output"),
            "screen" + when (VIDEO_ENCODE_TYPE) {
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
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    if (::screenProcessor.isInitialized) awaitRecorderRelease()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogContext.log.e(ITAG, "Release screenshot recorder failed", e)
                } finally {
                    closeVideoOutput()
                }
            }
            if (restartable) armNextRecording()
        }
    }

    /**
     * Restores the screen to a recordable state: a fresh one-shot recorder and an enabled
     * toggle. Without this the button stays disabled for the rest of the Activity's life,
     * whether the user stopped the recording or it failed. The debug file is left untouched
     * here and is reopened by [startRecording], which is what truncates it.
     */
    private fun armNextRecording() {
        screenProcessor = createRecorder()
        cleanupStarted.set(false)
        binding.toggleBtn.isEnabled = true
    }

    /**
     * Bounds how long teardown blocks this Activity's cleanup. A timeout only abandons the
     * wait: a recording thread stuck in a native call keeps running and still reaches this
     * Activity through the screen data listener, so the timeout is reported as an error
     * rather than treated as a completed release.
     */
    private suspend fun awaitRecorderRelease() {
        val released = withTimeoutOrNull(RELEASE_TIMEOUT_MS.milliseconds) {
            screenProcessor.releaseAndJoin()
            true
        } ?: false
        if (!released) {
            LogContext.log.e(ITAG, "Screenshot recorder did not release in ${RELEASE_TIMEOUT_MS}ms")
        }
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
