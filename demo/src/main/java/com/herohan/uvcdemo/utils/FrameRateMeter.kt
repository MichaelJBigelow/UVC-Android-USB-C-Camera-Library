package com.herohan.uvcdemo.utils

import android.os.SystemClock

/**
 * Measures how many frames per second arrive, and reports the rate every [intervalMs].
 *
 * Call [onFrame] for each frame, from one thread at a time.
 *
 * 测量每秒到达的帧数，并每隔 [intervalMs] 报告一次帧率。
 *
 * 每一帧调用一次 [onFrame]，同一时间只能由一个线程调用。
 *
 * @param intervalMs how often the rate is reported, in milliseconds / 报告帧率的间隔(毫秒)
 * @param clock current time in milliseconds / 当前时间(毫秒)
 * @param onRate receives the measured frames per second / 接收测得的每秒帧数
 */
class FrameRateMeter(
    private val intervalMs: Long = 500,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val onRate: (Double) -> Unit,
) {
    /** Start of the current interval, or null before the first frame. / 当前间隔的开始时间；第一帧之前为 null。 */
    private var intervalStart: Long? = null
    private var frames = 0

    fun onFrame() {
        val now = clock()
        val start = intervalStart
        if (start == null) {
            intervalStart = now
            return
        }
        frames++
        val elapsed = now - start
        if (elapsed > intervalMs) {
            val rate = frames * 1000.0 / elapsed
            intervalStart = now
            frames = 0
            onRate(rate)
        }
    }

    /** Starts measuring again from the next frame. / 从下一帧开始重新测量。 */
    fun reset() {
        intervalStart = null
        frames = 0
    }
}
