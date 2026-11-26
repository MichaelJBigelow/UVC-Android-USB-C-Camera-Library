package com.herohan.uvcapp

import android.os.Bundle

class VideoCaptureConfig internal constructor() : Cloneable {

    private var mMutableConfig = Bundle()

    /**
     * Sets the recording frames per second.
     *
     * 设置录制的每秒帧数。
     *
     * @param videoFrameRate The requested frame rate in frames per second. / 请求的帧率，单位为帧/秒
     * @return The current Config. / 当前的 Config
     */
    fun setVideoFrameRate(videoFrameRate: Int): VideoCaptureConfig {
        mMutableConfig.putInt(OPTION_VIDEO_FRAME_RATE, videoFrameRate)
        return this
    }

    /**
     * Get the recording frames per second.
     *
     * 获取录制的每秒帧数。
     */
    val videoFrameRate: Int
        get() = mMutableConfig.getInt(OPTION_VIDEO_FRAME_RATE, DEFAULT_VIDEO_FRAME_RATE)

    /**
     * Sets the encoding bit rate.
     *
     * 设置编码码率。
     *
     * @param bitRate The requested bit rate in bits per second. / 请求的码率,单位为 bit/s
     * @return The current Config. / 当前的 Config
     */
    fun setBitRate(bitRate: Int): VideoCaptureConfig {
        mMutableConfig.putInt(OPTION_BIT_RATE, bitRate)
        return this
    }

    /**
     * Returns true if has been setting this encoding bit rate.
     *
     * 如果已设置此编码码率则返回 true。
     */
    fun hasBitRate(): Boolean = mMutableConfig.containsKey(OPTION_BIT_RATE)

    /**
     * Get the encoding bit rate.
     *
     * 获取编码码率。
     */
    val bitRate: Int
        get() = mMutableConfig.getInt(OPTION_BIT_RATE, DEFAULT_BIT_RATE)

    /**
     * Sets number of seconds between each key frame in seconds.
     *
     * 设置相邻关键帧之间的间隔秒数。
     *
     * @param interval The requested interval in seconds. / 请求的间隔,单位为秒
     * @return The current Config. / 当前的 Config
     */
    fun setIFrameInterval(interval: Int): VideoCaptureConfig {
        mMutableConfig.putInt(OPTION_INTRA_FRAME_INTERVAL, interval)
        return this
    }

    /**
     * Get number of seconds between each key frame in seconds.
     *
     * 获取相邻关键帧之间的间隔秒数。
     */
    val iFrameInterval: Int
        get() = mMutableConfig.getInt(OPTION_INTRA_FRAME_INTERVAL, DEFAULT_INTRA_FRAME_INTERVAL)

    /**
     * Enable the audio capture or disable the audio capture.
     *
     * 启用或禁用音频采集。
     *
     * @param enable true to turn on the audio capture, false to turn it off.. / 为 true 表示开启音频采集,为 false 表示关闭
     * @return The current Config. / 当前的 Config
     */
    fun setAudioCaptureEnable(enable: Boolean): VideoCaptureConfig {
        mMutableConfig.putBoolean(OPTION_AUDIO_CAPTURE_ENABLE, enable)
        return this
    }

    /**
     * Return true if audio capture is enabled, false if audio capture is disabled.
     *
     * 如果启用了音频采集则返回 true,禁用则返回 false。
     */
    val audioCaptureEnable: Boolean
        get() = mMutableConfig.getBoolean(OPTION_AUDIO_CAPTURE_ENABLE, DEFAULT_AUDIO_CAPTURE_ENABLE)

    /**
     * Sets the bit rate of the audio stream.
     *
     * 设置音频流的码率。
     *
     * @param bitRate The requested bit rate in bits/s. / 请求的码率,单位为 bit/s
     * @return The current Config. / 当前的 Config
     */
    fun setAudioBitRate(bitRate: Int): VideoCaptureConfig {
        mMutableConfig.putInt(OPTION_AUDIO_BIT_RATE, bitRate)
        return this
    }

    /**
     * Get the bit rate of the audio stream.
     *
     * 获取音频流的码率。
     */
    val audioBitRate: Int
        get() = mMutableConfig.getInt(OPTION_AUDIO_BIT_RATE, DEFAULT_AUDIO_BIT_RATE)

    /**
     * Sets the sample rate of the audio stream.
     *
     * 设置音频流的采样率。
     *
     * @param sampleRate The requested sample rate in Hz. / 请求的采样率，单位为 Hz
     * @return The current Config. / 当前的 Config
     */
    fun setAudioSampleRate(sampleRate: Int): VideoCaptureConfig {
        mMutableConfig.putInt(OPTION_AUDIO_SAMPLE_RATE, sampleRate)
        return this
    }

    /**
     * Get the sample rate of the audio stream.
     *
     * 获取音频流的采样率。
     */
    val audioSampleRate: Int
        get() = mMutableConfig.getInt(OPTION_AUDIO_SAMPLE_RATE, DEFAULT_AUDIO_SAMPLE_RATE)

    /**
     * Sets the number of audio channels.
     *
     * 设置音频声道数。
     *
     * @param channelCount The requested number of audio channels. / 请求的音频声道数
     * @return The current Config. / 当前的 Config
     */
    fun setAudioChannelCount(channelCount: Int): VideoCaptureConfig {
        mMutableConfig.putInt(OPTION_AUDIO_CHANNEL_COUNT, channelCount)
        return this
    }

    /**
     * Get the number of audio channels.
     *
     * 获取音频声道数。
     */
    val audioChannelCount: Int
        get() = mMutableConfig.getInt(OPTION_AUDIO_CHANNEL_COUNT, DEFAULT_AUDIO_CHANNEL_COUNT)

    /**
     * Sets the audio min buffer size.
     *
     * 设置音频最小缓冲区大小。
     *
     * @param minBufferSize The requested audio minimum buffer size, in bytes. / 请求的音频最小缓冲区大小,单位为字节
     * @return The current Config. / 当前的 Config
     */
    fun setAudioMinBufferSize(minBufferSize: Int): VideoCaptureConfig {
        mMutableConfig.putInt(OPTION_AUDIO_MIN_BUFFER_SIZE, minBufferSize)
        return this
    }

    /**
     * Get the audio min buffer size.
     *
     * 获取音频最小缓冲区大小。
     */
    val audioMinBufferSize: Int
        get() = mMutableConfig.getInt(OPTION_AUDIO_MIN_BUFFER_SIZE, DEFAULT_AUDIO_MIN_BUFFER_SIZE)

    public override fun clone(): VideoCaptureConfig =
        (super.clone() as VideoCaptureConfig).also {
            it.mMutableConfig = mMutableConfig.clone() as Bundle
        }

    private companion object {
        const val OPTION_VIDEO_FRAME_RATE = "imageCapture.recordingFrameRate"
        const val OPTION_BIT_RATE = "imageCapture.bitRate"
        const val OPTION_INTRA_FRAME_INTERVAL = "imageCapture.intraFrameInterval"
        const val OPTION_AUDIO_CAPTURE_ENABLE = "imageCapture.audioCaptureEnable"
        const val OPTION_AUDIO_BIT_RATE = "imageCapture.audioBitRate"
        const val OPTION_AUDIO_SAMPLE_RATE = "imageCapture.audioSampleRate"
        const val OPTION_AUDIO_CHANNEL_COUNT = "imageCapture.audioChannelCount"
        const val OPTION_AUDIO_MIN_BUFFER_SIZE = "imageCapture.audioMinBufferSize"

        const val DEFAULT_VIDEO_FRAME_RATE = 30

        /**
         * 8Mb/s the recommend rate for 30fps 1080p
         *
         * 8Mb/s,30fps 1080p 的推荐码率
         */
        const val DEFAULT_BIT_RATE = 8 * 1024 * 1024

        /**
         * Seconds between each key frame
         *
         * 相邻关键帧之间的间隔秒数
         */
        const val DEFAULT_INTRA_FRAME_INTERVAL = 1

        /**
         * audio capture enabled
         *
         * 是否启用音频采集
         */
        const val DEFAULT_AUDIO_CAPTURE_ENABLE = true

        /**
         * audio bit rate
         *
         * 音频码率
         */
        const val DEFAULT_AUDIO_BIT_RATE = 64000

        /**
         * audio sample rate
         *
         * 音频采样率
         */
        const val DEFAULT_AUDIO_SAMPLE_RATE = 8000

        /**
         * audio channel count
         *
         * 音频声道数
         */
        const val DEFAULT_AUDIO_CHANNEL_COUNT = 1

        /**
         * audio default minimum buffer size
         *
         * 音频默认最小缓冲区大小
         */
        const val DEFAULT_AUDIO_MIN_BUFFER_SIZE = 1024
    }
}
