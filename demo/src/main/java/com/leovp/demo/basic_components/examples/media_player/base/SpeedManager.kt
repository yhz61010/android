package com.leovp.demo.basic_components.examples.media_player.base

import com.leovp.log_sdk.LogContext

/**
 * Movie player callback.
 *
 * The goal here is to play back frames at the original rate.  This is done by introducing
 * a pause before the frame is submitted to the renderer.
 *
 * This is not coordinated with VSYNC.  Since we can't control the display's refresh rate, and
 * the source material has time stamps that specify when each frame should be presented,
 * we will have to drop or repeat frames occasionally.
 *
 * Thread restrictions are noted in the method descriptions.  The FrameCallback overrides should
 * only be called from the MoviePlayer.
 */
class SpeedManager {
    private var mPrevPresentUsec: Long = 0
    private var mPrevMonoUsec: Long = 0
    private var mFixedFrameDurationUsec: Long = 0
    private var mLoopReset = false

    /**
     * Sets a fixed playback rate.  If set, this will ignore the presentation time stamp
     * in the video file.  Must be called before playback thread starts.
     */
    fun setFixedPlaybackRate(fps: Int) {
        mFixedFrameDurationUsec = ONE_MILLION / fps
    }

    // runs on decode thread
    fun preRender(presentationTimeUs: Long) {
        // For the first frame, we grab the presentation time from the video
        // and the current monotonic clock time.  For subsequent frames, we
        // sleep for a bit to try to ensure that we're rendering frames at the
        // pace dictated by the video stream.
        //
        // If the frame rate is faster than vsync we should be dropping frames.  On
        // Android 4.4 this may not be happening.
        if (mPrevMonoUsec == 0L) {
            // Latch current values, then return immediately.
            mPrevMonoUsec = System.nanoTime() / 1000
            mPrevPresentUsec = presentationTimeUs
        } else {
            // Compute the desired time delta between the previous frame and this frame.
            var frameDelta: Long
            if (mLoopReset) {
                // We don't get an indication of how long the last frame should appear
                // on-screen, so we just throw a reasonable value in.  We could probably
                // do better by using a previous frame duration or some sort of average;
                // for now we just use 30fps.
                mPrevPresentUsec = presentationTimeUs - ONE_MILLION / 30
                mLoopReset = false
            }
            if (mFixedFrameDurationUsec != 0L) {
                // Caller requested a fixed frame rate.  Ignore PTS.
                frameDelta = mFixedFrameDurationUsec
            } else {
                frameDelta = presentationTimeUs - mPrevPresentUsec
                LogContext.log.d(TAG, "frameDelta: $frameDelta")
            }
            if (frameDelta < 0) {
                //LogManager.w("Weird, video times went backward");
                frameDelta = 0
            } else if (frameDelta == 0L) {
                // This suggests a possible bug in movie generation.
                //LogManager.i("Warning: current frame and previous frame had same timestamp");
            } else if (frameDelta > 10 * ONE_MILLION) {
                // Inter-frame times could be arbitrarily long.  For this player, we want
                // to alert the developer that their movie might have issues (maybe they
                // accidentally output timestamps in nsec rather than usec).
                frameDelta = 5 * ONE_MILLION
            }
            val desiredUsec = mPrevMonoUsec + frameDelta // when we want to wake up
            var nowUs = System.nanoTime() / 1000
            while (nowUs < desiredUsec - 100 /*&& mState == RUNNING*/) {
                // Sleep until it's time to wake up.  To be responsive to "stop" commands
                // we're going to wake up every half a second even if the sleep is supposed
                // to be longer (which should be rare).  The alternative would be
                // to interrupt the thread, but that requires more work.
                //
                // The precision of the sleep call varies widely from one device to another;
                // we may wake early or late.  Different devices will have a minimum possible
                // sleep time. If we're within 100us of the target time, we'll probably
                // overshoot if we try to sleep, so just go ahead and continue on.
                var sleepTimeUsec = desiredUsec - nowUs
                if (sleepTimeUsec > 500000) {
                    sleepTimeUsec = 500000
                }
                try {
                    if (CHECK_SLEEP_TIME) {
                        Thread.sleep(sleepTimeUsec / 1000, (sleepTimeUsec % 1000).toInt() * 1000)
                    } else {
                        val time = sleepTimeUsec / 1000
                        LogContext.log.d(TAG, "time: $time")
                        Thread.sleep(time, (sleepTimeUsec % 1000).toInt() * 1000)
                    }
                } catch (ie: InterruptedException) {
                }
                nowUs = System.nanoTime() / 1000
            }

            // Advance times using calculated time values, not the post-sleep monotonic
            // clock time, to avoid drifting.
            mPrevMonoUsec += frameDelta
            mPrevPresentUsec += frameDelta
        }
    }

    // runs on decode thread
    fun postRender() {}
    fun loopReset() {
        mLoopReset = true
    }

    fun reset() {
        mPrevPresentUsec = 0
        mPrevMonoUsec = 0
        mFixedFrameDurationUsec = 0
        mLoopReset = false
    }

    companion object {
        private const val TAG = "SpeedManager"
        private const val CHECK_SLEEP_TIME = false
        private const val ONE_MILLION = 1000000L
    }
}