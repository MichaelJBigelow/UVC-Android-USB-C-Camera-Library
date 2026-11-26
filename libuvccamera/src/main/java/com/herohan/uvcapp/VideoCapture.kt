package com.herohan.uvcapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import com.serenegiant.usb.Size
import com.serenegiant.utils.UVCUtils
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "VideoCapture"

/**
 * Amount of time to wait for dequeuing a buffer from the videoEncoder.
 *
 * 等待从 videoEncoder 出队缓冲区的时间量。
 */
private const val DEQUE_TIMEOUT_USEC = 10000L

/**
 * Android preferred mime type for AVC video.
 *
 * Android 推荐的 AVC 视频 mime 类型。
 */
private const val VIDEO_MIME_TYPE = "video/avc"
private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"

class VideoCapture internal constructor(
    rendererHolder: ICameraRendererHolder,
    config: VideoCaptureConfig,
    resolution: Size?,
) {

    private val mVideoBufferInfo = MediaCodec.BufferInfo()
    private val mAudioBufferInfo = MediaCodec.BufferInfo()
    private val mMuxerLock = Any()
    private val mEndOfVideoStreamSignal = AtomicBoolean(true)
    private val mEndOfAudioStreamSignal = AtomicBoolean(true)
    private val mEndOfAudioVideoSignal = AtomicBoolean(true)

    /**
     * For record the first sample written time.
     *
     * 用于记录写入第一个样本的时间。
     */
    @VisibleForTesting
    @JvmField
    val mIsFirstVideoKeyFrameWrite = AtomicBoolean(false)

    @VisibleForTesting
    @JvmField
    val mIsFirstAudioSampleWrite = AtomicBoolean(false)

    /**
     * Thread on which all encoding occurs.
     *
     * 所有编码操作所在的线程。
     */
    private val mVideoHandlerThread = HandlerThread(TAG + "video encoding thread")
    private val mVideoHandler: Handler

    /**
     * Thread on which audio encoding occurs.
     *
     * 音频编码所在的线程。
     */
    private val mAudioHandlerThread = HandlerThread(TAG + "audio encoding thread")
    private val mAudioHandler: Handler

    private var mVideoEncoder: MediaCodec? = null
    private var mAudioEncoder: MediaCodec? = null
    private var mRecordingFuture: FutureTask<String>? = null

    /**
     * Guards [mRecordingSessionActive] and [mRecordingWaitRelease], so release()
     * either frees resources itself or leaves it to the session's final task, never neither.
     *
     * 保护 [mRecordingSessionActive] 和 [mRecordingWaitRelease]，确保 release()
     * 要么自己释放资源，要么交给录制会话的最后一个任务释放，不会两者都不做。
     */
    private val mReleaseLock = Any()

    /**
     * True from the start of a recording session until its final task (mRecordingFuture) has run.
     *
     * 从录制会话开始到其最后一个任务（mRecordingFuture）执行完毕期间为 true。
     */
    @GuardedBy("mReleaseLock")
    private var mRecordingSessionActive = false

    @GuardedBy("mReleaseLock")
    private var mRecordingWaitRelease = false

    /**
     * The muxer that writes the encoding data to file.
     *
     * 将编码数据写入文件的 muxer。
     */
    @GuardedBy("mMuxerLock")
    private var mMuxer: MediaMuxer? = null
    private val mMuxerStarted = AtomicBoolean(false)

    /**
     * The index of the video track used by the muxer.
     *
     * muxer 所使用的视频轨道的索引。
     */
    @GuardedBy("mMuxerLock")
    private var mVideoTrackIndex = 0

    /**
     * The index of the audio track used by the muxer.
     *
     * muxer 所使用的音频轨道的索引。
     */
    @GuardedBy("mMuxerLock")
    private var mAudioTrackIndex = 0

    /**
     * Surface the camera writes to, which the videoEncoder uses as input.
     *
     * 相机写入的 Surface，videoEncoder 将其用作输入。
     */
    private var mCameraSurface: Surface? = null

    private var mSavedVideoUri: Uri? = null
    private var mParcelFileDescriptor: ParcelFileDescriptor? = null

    /**
     * audio raw data
     *
     * 音频原始数据
     */
    private var mAudioRecorder: AudioRecord? = null

    @Volatile
    private var mAudioBufferSize = 0

    @Volatile
    private var mIsRecording = false
    private var mAudioChannelCount = 0
    private var mAudioSampleRate = 0
    private var mAudioBitRate = 0
    private val mIsAudioEnabled = AtomicBoolean(true)

    /**
     * Why the audio thread stopped this recording, if it did. The video thread reports it, so the
     * recording ends with exactly one result.
     *
     * 音频线程中止本次录像的原因(如有)。由视频线程报告，使录像只产生一个结果。
     */
    private val mAudioFailure = AtomicReference<Throwable?>()

    private var mVideoEncoderInitStatus = VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_UNINITIALIZED
    private var mVideoEncoderErrorMessage: Throwable? = null

    private var mAudioEncoderInitStatus = AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_UNINITIALIZED
    private var mAudioEncoderErrorMessage: Throwable? = null

    private val mRendererHolderWeak = WeakReference(rendererHolder)

    // Changed on the camera thread, read by the recording session's final task
    // 在相机线程上修改，由录像会话的最后一个任务读取
    @Volatile
    private var mConfig: VideoCaptureConfig = config.clone()

    @Volatile
    private var mResolution: Size? = resolution

    private val mMainHandler = Handler(Looper.getMainLooper())

    private val mExecutor: ExecutorService = AtomicInteger(0).let { threadId ->
        Executors.newFixedThreadPool(1) { r ->
            Thread(r, TAG + "video_capture" + threadId.getAndIncrement())
        }
    }

    init {
        // video thread start
        // 视频线程启动
        mVideoHandlerThread.start()
        mVideoHandler = Handler(mVideoHandlerThread.looper)

        // audio thread start
        // 音频线程启动
        mAudioHandlerThread.start()
        mAudioHandler = Handler(mAudioHandlerThread.looper)

        initVideoAudioEncoder()
    }

    internal fun setConfig(config: VideoCaptureConfig) {
        mConfig = config.clone()
        reinitEncodersUnlessRecording()
    }

    internal fun setResolution(resolution: Size?) {
        mResolution = resolution
        reinitEncodersUnlessRecording()
    }

    /**
     * Rebuilds the encoders for the new config or resolution, unless a recording session is still
     * running: its final task reconfigures the encoders with the new values anyway, and rebuilding
     * them now would kill the recording.
     *
     * 按新的配置或分辨率重建编码器；但如果录像会话仍在进行则不重建：
     * 该会话的最后一个任务无论如何都会用新值重新配置编码器，而此时重建会中断录像。
     */
    private fun reinitEncodersUnlessRecording() {
        synchronized(mReleaseLock) {
            if (mRecordingSessionActive) {
                Log.i(TAG, "Recording in progress; the new settings apply to the next recording")
                return
            }
            initVideoAudioEncoder()
        }
    }

    /**
     * Creates a [MediaFormat] using size and default configs
     *
     * 使用尺寸和默认配置创建 [MediaFormat]
     */
    private fun createVideoMediaFormat(): MediaFormat {
        val resolution = checkNotNull(mResolution) { "The camera has no preview size" }
        val config = mConfig
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, resolution.width, resolution.height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(
            MediaFormat.KEY_BIT_RATE,
            if (config.hasBitRate()) config.bitRate else 8 * resolution.width * resolution.height,
        )
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.videoFrameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameInterval)

        return format
    }

    private fun initVideoAudioEncoder() {
        if (mCameraSurface != null) {
            mVideoEncoder?.let {
                it.stop()
                it.release()
            }
            mAudioEncoder?.let {
                it.stop()
                it.release()
            }
            releaseCameraSurface(false)
        }

        mVideoEncoder = try {
            MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        } catch (e: IOException) {
            throw IllegalStateException("Unable to create Video MediaCodec due to: " + e.cause)
        }
        mAudioEncoder = try {
            MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
        } catch (e: Exception) {
            mIsAudioEnabled.set(false)
            Log.e(TAG, "Unable to create Audio MediaCodec, disable audio." + e.message, e)
            null
        }

        setupEncoder()
    }

    val isRecording: Boolean
        get() = mIsRecording

    /**
     * Starts recording video, which continues until [VideoCapture.stopRecording] is
     * called.
     *
     * StartRecording() is asynchronous. User needs to check if any error occurs by setting the
     * [OnVideoCaptureCallback.onError].
     *
     * Sound is recorded only when the app holds RECORD_AUDIO; otherwise the video has no sound.
     *
     * 开始录制视频，直到调用 [VideoCapture.stopRecording] 为止。
     *
     * StartRecording() 是异步的。用户需要通过设置 [OnVideoCaptureCallback.onError] 来检查是否发生错误。
     *
     * 仅当应用拥有 RECORD_AUDIO 权限时才录制声音；否则视频没有声音。
     *
     * @param outputFileOptions Location to save the video capture / 保存视频采集的位置
     * @param callback          Callback for when the recorded video saving completion or failure. / 录制的视频保存完成或失败时的回调
     */
    fun startRecording(outputFileOptions: OutputFileOptions, callback: OnVideoCaptureCallback) {
        Log.i(TAG, "startRecording")
        mIsFirstVideoKeyFrameWrite.set(false)
        mIsFirstAudioSampleWrite.set(false)
        mAudioFailure.set(null)

        val postListener: OnVideoCaptureCallback = VideoCaptureListenerWrapper(callback)

        val rendererHolder = mRendererHolderWeak.get()
        if (rendererHolder == null) {
            // Not bound. Notify callback.
            // 未绑定。通知回调。
            postListener.onError(ERROR_INVALID_CAMERA, "Not bound to a Camera", null)
            return
        }

        // Check video encoder initialization status, if there is any error happened
        // return error callback directly.
        // 检查视频编码器的初始化状态，如果发生任何错误，
        // 直接返回错误回调。
        if (mVideoEncoderInitStatus != VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_UNINITIALIZED) {
            postListener.onError(
                ERROR_ENCODER,
                "Video encoder initialization failed before start recording ",
                mVideoEncoderErrorMessage,
            )
            return
        }

        // Check audio encoder initialization status, if there is any error happened
        // return error callback directly.
        // 检查音频编码器的初始化状态，如果发生任何错误，
        // 直接返回错误回调。
        if (mAudioEncoderInitStatus != AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_UNINITIALIZED) {
            postListener.onError(
                ERROR_ENCODER,
                "Audio encoder initialization failed before start recording ",
                mAudioEncoderErrorMessage,
            )
            return
        }

        if (!mEndOfAudioVideoSignal.get()) {
            postListener.onError(ERROR_RECORDING_IN_PROGRESS, "It is still in video recording!", null)
            return
        }

        if (mIsAudioEnabled.get()) {
            startAudioRecorder()
        }

        synchronized(mReleaseLock) {
            mRecordingSessionActive = true
        }
        val recordingFuture = FutureTask {
            val cameraSurface = mCameraSurface
            if (cameraSurface != null) {
                mRendererHolderWeak.get()?.removeSlaveSurface(cameraSurface.hashCode())
            }

            synchronized(mReleaseLock) {
                try {
                    if (mRecordingWaitRelease) {
                        mRecordingWaitRelease = false
                        releaseResources()
                        mIsRecording = false
                        "releaseResources"
                    } else {
                        // Do the setup of the videoEncoder at the end of video recording instead of at the
                        // start of recording because it requires attaching a new Surface. This causes a
                        // glitch so we don't want that to incur latency at the start of capture.
                        // 在视频录制结束时而不是录制开始时设置 videoEncoder，
                        // 因为这需要附加一个新的 Surface。这会导致画面卡顿，
                        // 所以我们不希望它在采集开始时造成延迟。
                        setupEncoder()
                        "startRecording"
                    }
                } finally {
                    mRecordingSessionActive = false
                }
            }
        }
        mRecordingFuture = recordingFuture

        try {
            // video encoder start
            // 视频编码器启动
            Log.i(TAG, "videoEncoder start")
            checkNotNull(mVideoEncoder) { "No video encoder" }.start()

            // audio encoder start
            // 音频编码器启动
            if (mIsAudioEnabled.get()) {
                Log.i(TAG, "audioEncoder start")
                mAudioEncoder?.start()
            }
        } catch (e: IllegalStateException) {
            stopAudioRecorder()
            mExecutor.execute(recordingFuture)
            postListener.onError(ERROR_ENCODER, "Audio/Video encoder start fail", e)
            return
        }

        try {
            synchronized(mMuxerLock) {
                mMuxer = initMediaMuxer(outputFileOptions)
                    ?: throw IllegalArgumentException("MediaMuxer creation failed!")
            }
        } catch (e: Exception) {
            if (e !is IOException && e !is RuntimeException) {
                throw e
            }
            // Also catch runtime exceptions (IllegalArgumentException from above and MediaMuxer,
            // SecurityException etc. from ContentResolver), so the session still ends, its final
            // task resets the encoders and AudioRecord, and release() isn't deferred forever
            // 同时捕获运行时异常（上面以及 MediaMuxer 抛出的 IllegalArgumentException，
            // ContentResolver 抛出的 SecurityException 等），确保会话仍然结束，
            // 由其最后一个任务重置编码器和 AudioRecord，release() 不会被无限期推迟
            // Stop the audio input and remove the output this session created
            // 停止音频输入，并删除本次会话创建的输出
            stopAudioRecorder()
            discardFailedOutput(outputFileOptions)
            mExecutor.execute(recordingFuture)
            postListener.onError(ERROR_MUXER, "MediaMuxer creation failed!", e)
            return
        }

        mEndOfVideoStreamSignal.set(false)
        mEndOfAudioStreamSignal.set(false)
        mEndOfAudioVideoSignal.set(false)
        mIsRecording = true

        postListener.onStart()

        // Attach Surface to renderer holder.
        // 将 Surface 附加到 renderer holder。
        mCameraSurface?.let { rendererHolder.addSlaveSurface(it.hashCode(), it, true) }

        if (mIsAudioEnabled.get()) {
            mAudioHandler.post { audioEncode() }
        }

        mVideoHandler.post {
            var errorOccurred = videoEncode(postListener, outputFileOptions)
            val audioFailure = mAudioFailure.getAndSet(null)
            if (!errorOccurred && audioFailure != null) {
                // The audio thread failed: report that as the recording's one result.
                // 音频线程失败：将其作为本次录像的唯一结果报告。
                postListener.onError(ERROR_ENCODER, "Audio encoder encode failed!", audioFailure)
                errorOccurred = true
            }
            if (!errorOccurred && publishMediaStoreOutput(outputFileOptions)) {
                // Only file:// outputs need a scan; MediaStore entries are already indexed.
                // 只有 file:// 输出需要扫描；MediaStore 条目已建立索引。
                MediaScanHelper.scanMediaFile(mSavedVideoUri)
                postListener.onVideoSaved(OutputFileResults(mSavedVideoUri))
            } else {
                if (!errorOccurred) {
                    postListener.onError(ERROR_FILE_IO, "Failed to publish the video", null)
                }
                discardFailedOutput(outputFileOptions)
            }
            mSavedVideoUri = null
            mExecutor.execute(recordingFuture)
        }
    }

    /**
     * Starts the audio input; if it can't start, the recording continues without audio.
     *
     * 启动音频输入；如果无法启动，则在没有音频的情况下继续录制。
     */
    private fun startAudioRecorder() {
        try {
            // Audio input start
            // 音频输入启动
            mAudioRecorder?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.startRecording()
                }
            }
        } catch (e: IllegalStateException) {
            // Disable the audio if the audio input cannot start. And Continue the recording
            // without audio.
            // 如果音频输入无法启动，则禁用音频，并在没有音频的情况下
            // 继续录制。
            Log.i(TAG, "AudioRecorder cannot start recording, disable audio." + e.message)
            mIsAudioEnabled.set(false)
            // Only release this session's AudioRecord; the audio thread and encoder are
            // reused by later recordings, and setupEncoder() creates a new AudioRecord.
            // 只释放本次会话的 AudioRecord；音频线程和编码器会被之后的录制复用，
            // setupEncoder() 会创建新的 AudioRecord。
            releaseAudioRecorder()
        }

        // Gets the AudioRecorder's state
        // 获取 AudioRecorder 的状态
        val recorder = mAudioRecorder
        if (recorder != null && recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.i(TAG, "AudioRecorder startRecording failed - incorrect state: " + recorder.recordingState)
            mIsAudioEnabled.set(false)
            releaseAudioRecorder()
        }
    }

    /**
     * Stops recording video, this must be called after
     * [VideoCapture.startRecording] is called.
     *
     * stopRecording() is asynchronous API. User need to check if
     * [OnVideoCaptureCallback.onVideoSaved] or [OnVideoCaptureCallback.onError] be called
     * before startRecording.
     *
     * 停止录制视频，必须在调用 [VideoCapture.startRecording] 之后调用。
     *
     * stopRecording() 是异步 API。用户需要检查在再次 startRecording 之前，是否已调用
     * [OnVideoCaptureCallback.onVideoSaved] 或 [OnVideoCaptureCallback.onError]。
     */
    fun stopRecording() {
        Log.i(TAG, "stopRecording")

        if (mIsRecording) {
            if (mIsAudioEnabled.get()) {
                // Stop audio encoder thread, and wait video encoder and muxer stop.
                // 停止音频编码器线程，并等待视频编码器和 muxer 停止。
                mEndOfAudioStreamSignal.set(true)
            } else {
                // Audio is disabled, stop video encoder thread directly.
                // 音频已禁用，直接停止视频编码器线程。
                mEndOfVideoStreamSignal.set(true)
            }
        }
    }

    fun release() {
        stopRecording()

        synchronized(mReleaseLock) {
            if (mRecordingSessionActive) {
                // A recording is still finishing; its final task releases the resources
                // 录制仍在收尾；由其最后一个任务释放资源
                mRecordingWaitRelease = true
            } else {
                releaseResources()
            }
        }
    }

    private fun releaseResources() {
        mVideoHandlerThread.quitSafely()

        // audio encoder release
        // 音频编码器释放
        releaseAudioInputResource()

        // Release the video encoder even if no input surface was created
        // 即使没有创建输入 Surface，也要释放视频编码器
        releaseCameraSurface(true)

        mExecutor.shutdown()
    }

    private fun releaseAudioInputResource() {
        mAudioHandlerThread.quitSafely()
        mAudioEncoder?.release()
        mAudioEncoder = null

        releaseAudioRecorder()
    }

    /**
     * Stops the audio input if it is recording.
     *
     * 如果音频输入正在录制，则将其停止。
     */
    private fun stopAudioRecorder() {
        val recorder = mAudioRecorder ?: return
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stopAudioRecorder:", e)
        }
    }

    /**
     * Makes a finished MediaStore video visible to other apps by clearing IS_PENDING.
     *
     * 通过清除 IS_PENDING，使录制完成的 MediaStore 视频对其他应用可见。
     *
     * @return false if the entry could not be published / 无法发布该条目时返回 false
     */
    private fun publishMediaStoreOutput(outputFileOptions: OutputFileOptions): Boolean {
        val savedUri = mSavedVideoUri
        if (!outputFileOptions.isSavingToMediaStore || savedUri == null) {
            return true
        }
        return try {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            outputFileOptions.contentResolver!!.update(savedUri, values, null, null) > 0
        } catch (e: RuntimeException) {
            Log.e(TAG, "publishMediaStoreOutput:", e)
            false
        }
    }

    /**
     * Closes and deletes the MediaStore entry of a recording that failed, so no broken or empty
     * video is left behind.
     *
     * 关闭并删除失败录像的 MediaStore 条目，避免留下损坏或空白的视频。
     */
    private fun discardFailedOutput(outputFileOptions: OutputFileOptions) {
        mParcelFileDescriptor?.let {
            try {
                it.close()
            } catch (e: IOException) {
                Log.w(TAG, "discardFailedOutput:", e)
            }
        }
        mParcelFileDescriptor = null
        val savedUri = mSavedVideoUri
        if (outputFileOptions.isSavingToMediaStore && savedUri != null) {
            try {
                outputFileOptions.contentResolver!!.delete(savedUri, null, null)
            } catch (e: RuntimeException) {
                Log.w(TAG, "discardFailedOutput:", e)
            }
            mSavedVideoUri = null
        }
    }

    private fun releaseAudioRecorder() {
        mAudioRecorder?.release()
        mAudioRecorder = null
    }

    private fun releaseCameraSurface(releaseVideoEncoder: Boolean) {
        if (releaseVideoEncoder) {
            mVideoEncoder?.release()
            mVideoEncoder = null
        }

        mCameraSurface?.release()
        mCameraSurface = null
    }

    /**
     * Setup the [MediaCodec] for encoding video from a camera [Surface] and encoding
     * audio from selected audio source.
     *
     * 设置 [MediaCodec]，用于对来自相机 [Surface] 的视频以及来自所选音频源的音频进行编码。
     */
    private fun setupEncoder() {
        val videoEncoder = mVideoEncoder ?: return

        // video encoder setup
        // 视频编码器设置
        videoEncoder.reset()
        mVideoEncoderInitStatus = VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_UNINITIALIZED

        // Configures a Video encoder, if there is any exception, will abort follow up actions
        // 配置视频编码器，如果出现任何异常，将中止后续操作
        try {
            videoEncoder.configure(
                createVideoMediaFormat(),
                /*surface*/ null,
                /*crypto*/ null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
        } catch (e: Exception) {
            handleVideoEncoderInitError(e)
            return
        }

        if (mCameraSurface != null) {
            releaseCameraSurface(false)
        }
        mCameraSurface = videoEncoder.createInputSurface()

        // audio encoder setup
        // 音频编码器设置
        // reset audio inout flag
        // 重置音频输入标志
        // Audio stays off if the audio encoder could not be created
        // 如果无法创建音频编码器，则保持关闭音频
        val audioEncoder = mAudioEncoder
        mIsAudioEnabled.set(mConfig.audioCaptureEnable && audioEncoder != null)

        if (mIsAudioEnabled.get() && audioEncoder != null) {
            setAudioParameters()
            audioEncoder.reset()
            // Configures a Audio encoder, if there is any exception, will abort follow up actions
            // 配置音频编码器，如果出现任何异常，将中止后续操作
            try {
                audioEncoder.configure(createAudioMediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (e: Exception) {
                handleAudioEncoderInitError(e)
                return
            }

            mAudioRecorder?.release()
            mAudioRecorder = autoConfigAudioRecordSource()
            // check mAudioRecorder
            // 检查 mAudioRecorder
            if (mAudioRecorder == null) {
                Log.e(TAG, "AudioRecord object cannot initialized correctly!")
                mIsAudioEnabled.set(false)
            }
        }

        synchronized(mMuxerLock) {
            mVideoTrackIndex = -1
            mAudioTrackIndex = -1
        }
        mIsRecording = false
    }

    private fun handleVideoEncoderInitError(e: Exception) {
        mVideoEncoderInitStatus = VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_INITIALIZED_FAILED
        mVideoEncoderErrorMessage = e

        if (e is MediaCodec.CodecException) {
            val errorCode = e.errorCode
            Log.i(TAG, "CodecException: code: " + errorCode + " diagnostic: " + e.diagnosticInfo)
            if (errorCode == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                mVideoEncoderInitStatus = VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE
            } else if (errorCode == MediaCodec.CodecException.ERROR_RECLAIMED) {
                mVideoEncoderInitStatus = VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED
            }
        }
    }

    private fun handleAudioEncoderInitError(e: Exception) {
        mAudioEncoderInitStatus = AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_INITIALIZED_FAILED
        mAudioEncoderErrorMessage = e

        if (e is MediaCodec.CodecException) {
            val errorCode = e.errorCode
            Log.i(TAG, "CodecException: code: " + errorCode + " diagnostic: " + e.diagnosticInfo)
            if (errorCode == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                mAudioEncoderInitStatus = AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE
            } else if (errorCode == MediaCodec.CodecException.ERROR_RECLAIMED) {
                mAudioEncoderInitStatus = AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED
            }
        }
    }

    /**
     * Write a buffer that has been encoded to file.
     *
     * 将已编码的缓冲区写入文件。
     *
     * @param bufferIndex the index of the buffer in the videoEncoder that has available data / videoEncoder 中有可用数据的缓冲区的索引
     * @return returns true if this buffer is the end of the stream / 如果此缓冲区是流的末尾则返回 true
     */
    private fun writeVideoEncodedBuffer(videoEncoder: MediaCodec, bufferIndex: Int): Boolean {
        if (bufferIndex < 0) {
            Log.e(TAG, "Output buffer should not have negative index: $bufferIndex")
            return false
        }
        // Get data from buffer
        // 从缓冲区获取数据
        val outputBuffer = videoEncoder.getOutputBuffer(bufferIndex)

        // Check if buffer is valid, if not then return
        // 检查缓冲区是否有效，无效则返回
        if (outputBuffer == null) {
            Log.d(TAG, "OutputBuffer was null.")
            return false
        }

        // Write data to mMuxer if available
        // 如果有可用数据，则写入 mMuxer
        if (mMuxerStarted.get()) {
            if (mVideoBufferInfo.size > 0) {
                outputBuffer.position(mVideoBufferInfo.offset)
                outputBuffer.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size)
                mVideoBufferInfo.presentationTimeUs = System.nanoTime() / 1000

                synchronized(mMuxerLock) {
                    if (!mIsFirstVideoKeyFrameWrite.get()) {
                        val isKeyFrame = (mVideoBufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        if (isKeyFrame) {
                            Log.i(TAG, "First video key frame written.")
                            mIsFirstVideoKeyFrameWrite.set(true)
                        } else {
                            // Request a sync frame immediately
                            // 立即请求同步帧
                            val syncFrame = Bundle()
                            syncFrame.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                            videoEncoder.setParameters(syncFrame)
                        }
                    }

                    checkNotNull(mMuxer) { "The muxer is released" }
                        .writeSampleData(mVideoTrackIndex, outputBuffer, mVideoBufferInfo)
                }
            } else {
                Log.i(TAG, "mVideoBufferInfo.size <= 0, index $bufferIndex")
            }
        }

        // Release data
        // 释放数据
        videoEncoder.releaseOutputBuffer(bufferIndex, false)

        // Return true if EOS is set
        // 如果设置了 EOS，则返回 true
        return (mVideoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
    }

    private fun writeAudioEncodedBuffer(audioEncoder: MediaCodec, bufferIndex: Int): Boolean {
        val buffer = checkNotNull(audioEncoder.getOutputBuffer(bufferIndex)) { "No audio output buffer" }
        buffer.position(mAudioBufferInfo.offset)
        if (mMuxerStarted.get()) {
            try {
                if (mAudioBufferInfo.size > 0 && mAudioBufferInfo.presentationTimeUs > 0) {
                    synchronized(mMuxerLock) {
                        if (!mIsFirstAudioSampleWrite.get()) {
                            Log.i(TAG, "First audio sample written.")
                            mIsFirstAudioSampleWrite.set(true)
                        }
                        checkNotNull(mMuxer) { "The muxer is released" }
                            .writeSampleData(mAudioTrackIndex, buffer, mAudioBufferInfo)
                    }
                } else {
                    Log.i(
                        TAG, "mAudioBufferInfo size: " + mAudioBufferInfo.size + " " +
                            "presentationTimeUs: " + mAudioBufferInfo.presentationTimeUs
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "audio error:size=" + mAudioBufferInfo.size +
                        "/offset=" + mAudioBufferInfo.offset +
                        "/timeUs=" + mAudioBufferInfo.presentationTimeUs,
                    e,
                )
            }
        }
        audioEncoder.releaseOutputBuffer(bufferIndex, false)
        return (mAudioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
    }

    /**
     * Encoding which runs indefinitely until end of stream is signaled. This should not run on the
     * main thread otherwise it will cause the application to block.
     *
     * 持续运行的编码，直到发出流结束信号。不应在主线程上运行，否则会导致应用程序阻塞。
     *
     * @return returns `true` if an error condition occurred, otherwise returns `false` / 如果发生错误则返回 `true`，否则返回 `false`
     */
    private fun videoEncode(
        videoSavedCallback: OnVideoCaptureCallback,
        outputFileOptions: OutputFileOptions,
    ): Boolean {
        // Main encoding loop. Exits on end of stream.
        // 主编码循环。在流结束时退出。
        val videoEncoder = mVideoEncoder
        var errorOccurred = if (videoEncoder == null) {
            // Released meanwhile / 在此期间已被释放
            videoSavedCallback.onError(ERROR_ENCODER, "Video encoder encode failed!", null)
            true
        } else {
            drainVideoEncoder(videoEncoder, videoSavedCallback)
        }

        try {
            // new MediaMuxer instance required for each new file written, and release current one.
            // 每写入一个新文件都需要一个新的 MediaMuxer 实例，并释放当前的实例。
            synchronized(mMuxerLock) {
                mMuxer?.let {
                    if (mMuxerStarted.get()) {
                        Log.i(TAG, "Muxer already started")
                        it.stop()
                    }
                    it.release()
                }
                mMuxer = null
            }

            // A final checking for recording result, if the recorded file has no key
            // frame, then the video file is not playable, needs to call
            // onError() and will be removed.
            // 对录制结果做最终检查，如果录制的文件没有关键帧，
            // 则该视频文件无法播放，需要调用
            // onError()，并且该文件将被删除。
            val checkResult = removeRecordingResultIfNoVideoKeyFrameArrived(outputFileOptions)

            if (!checkResult) {
                videoSavedCallback.onError(ERROR_RECORDING_TOO_SHORT, "The file has no video key frame.", null)
                errorOccurred = true
            }
        } catch (e: IllegalStateException) {
            // The video encoder has not got the key frame yet.
            // 视频编码器尚未获得关键帧。
            Log.i(TAG, "muxer stop IllegalStateException: " + System.currentTimeMillis())
            Log.i(TAG, "muxer stop exception, mIsFirstVideoKeyFrameWrite: " + mIsFirstVideoKeyFrameWrite.get())
            if (mIsFirstVideoKeyFrameWrite.get()) {
                // If muxer throws IllegalStateException at this moment and also the key frame
                // has received, this will reported as a Muxer stop failed.
                // Otherwise, this error will be ERROR_RECORDING_TOO_SHORT.
                // 如果此时 muxer 抛出 IllegalStateException 并且已经收到关键帧，
                // 则会报告为 Muxer 停止失败。
                // 否则，该错误将为 ERROR_RECORDING_TOO_SHORT。
                videoSavedCallback.onError(ERROR_MUXER, "Muxer stop failed!", e)
            } else {
                videoSavedCallback.onError(ERROR_RECORDING_TOO_SHORT, "The file has no video key frame.", null)
            }
            errorOccurred = true
        }

        mParcelFileDescriptor?.let {
            try {
                it.close()
                mParcelFileDescriptor = null
            } catch (e: IOException) {
                videoSavedCallback.onError(ERROR_MUXER, "File descriptor close failed!", e)
                errorOccurred = true
            }
        }

        mMuxerStarted.set(false)

        // notify the UI thread that the video recording has finished
        // 通知 UI 线程视频录制已结束
        mEndOfAudioVideoSignal.set(true)
        mIsFirstVideoKeyFrameWrite.set(false)

        Log.i(TAG, "Video encode thread end.")
        return errorOccurred
    }

    /**
     * Encodes until the end of stream, then stops [videoEncoder].
     *
     * 编码直到流结束，然后停止 [videoEncoder]。
     *
     * @return `true` if an error occurred and was reported / 发生错误并已报告时返回 `true`
     */
    private fun drainVideoEncoder(videoEncoder: MediaCodec, videoSavedCallback: OnVideoCaptureCallback): Boolean {
        var errorOccurred = false
        var videoEos = false
        try {
            while (!videoEos && !errorOccurred) {
                // Check for end of stream from main thread
                // 检查来自主线程的流结束信号
                if (mEndOfVideoStreamSignal.get()) {
                    videoEncoder.signalEndOfInputStream()
                    mEndOfVideoStreamSignal.set(false)
                }

                // Deque buffer to check for processing step
                // 出队缓冲区以检查处理步骤
                when (val outputBufferId = videoEncoder.dequeueOutputBuffer(mVideoBufferInfo, DEQUE_TIMEOUT_USEC)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (mMuxerStarted.get()) {
                            videoSavedCallback.onError(
                                ERROR_ENCODER,
                                "Unexpected change in video encoding format.",
                                null,
                            )
                            errorOccurred = true
                        }

                        synchronized(mMuxerLock) {
                            val muxer = checkNotNull(mMuxer) { "The muxer is released" }
                            mVideoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)

                            if ((mIsAudioEnabled.get() && mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) ||
                                (!mIsAudioEnabled.get() && mVideoTrackIndex >= 0)
                            ) {
                                Log.i(
                                    TAG,
                                    "MediaMuxer started on video encode thread and audio enabled: $mIsAudioEnabled",
                                )
                                muxer.start()
                                mMuxerStarted.set(true)
                            }
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Timed out. Just wait until next attempt to deque.
                        // 超时。等待下一次出队尝试。
                    }

                    else -> videoEos = writeVideoEncodedBuffer(videoEncoder, outputBufferId)
                }
            }
        } catch (e: IllegalStateException) {
            videoSavedCallback.onError(ERROR_ENCODER, "Video encoder encode failed!", e)
            errorOccurred = true
        } catch (e: IllegalArgumentException) {
            // IllegalArgumentException: the muxer refused the encoder's format
            // IllegalArgumentException：muxer 不接受编码器的格式
            videoSavedCallback.onError(ERROR_ENCODER, "Video encoder encode failed!", e)
            errorOccurred = true
        }

        try {
            Log.i(TAG, "videoEncoder stop")
            videoEncoder.stop()
        } catch (e: IllegalStateException) {
            videoSavedCallback.onError(ERROR_ENCODER, "Video encoder stop failed!", e)
            errorOccurred = true
        }

        return errorOccurred
    }

    private fun audioEncode() {
        // Audio encoding loop. Exits on end of stream.
        // 音频编码循环。在流结束时退出。
        var audioEos = false
        var lastAudioTimestamp = 0L
        try {
            while (!audioEos && mIsRecording) {
                // Check for end of stream from main thread
                // 检查来自主线程的流结束信号
                if (mEndOfAudioStreamSignal.get()) {
                    mEndOfAudioStreamSignal.set(false)
                    mIsRecording = false
                }

                // get audio deque input buffer
                // 获取音频出队输入缓冲区
                val audioEncoder = mAudioEncoder
                val audioRecorder = mAudioRecorder
                if (audioEncoder != null && audioRecorder != null) {
                    try {
                        val index = audioEncoder.dequeueInputBuffer(-1)
                        if (index >= 0) {
                            // Not an IllegalStateException, which the catch below only logs: a
                            // missing buffer ends the recording through the outer catch.
                            // 不是 IllegalStateException(下面的 catch 只会记录它)：缺少缓冲区时
                            // 通过外层 catch 结束录像。
                            val buffer = audioEncoder.getInputBuffer(index)
                                ?: throw NullPointerException("No audio input buffer")
                            buffer.clear()
                            val length = audioRecorder.read(buffer, mAudioBufferSize)
                            if (length > 0) {
                                audioEncoder.queueInputBuffer(
                                    index,
                                    0,
                                    length,
                                    System.nanoTime() / 1000,
                                    if (mIsRecording) 0 else MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                            }
                        }
                    } catch (e: MediaCodec.CodecException) {
                        Log.i(TAG, "audio dequeueInputBuffer CodecException " + e.message)
                    } catch (e: IllegalStateException) {
                        Log.i(TAG, "audio dequeueInputBuffer IllegalStateException " + e.message)
                    }

                    // start to dequeue audio output buffer
                    // 开始出队音频输出缓冲区
                    var outIndex: Int
                    do {
                        outIndex = audioEncoder.dequeueOutputBuffer(mAudioBufferInfo, 0)
                        when (outIndex) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(mMuxerLock) {
                                val muxer = checkNotNull(mMuxer) { "The muxer is released" }
                                mAudioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                                if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                                    Log.i(TAG, "MediaMuxer start on audio encoder thread.")
                                    muxer.start()
                                    mMuxerStarted.set(true)
                                }
                            }

                            MediaCodec.INFO_TRY_AGAIN_LATER -> {}

                            else -> {
                                // Drops out of order audio frame if the frame's earlier than last
                                // frame.
                                // 如果音频帧早于上一帧，
                                // 则丢弃该乱序音频帧。
                                if (mAudioBufferInfo.presentationTimeUs > lastAudioTimestamp) {
                                    audioEos = writeAudioEncodedBuffer(audioEncoder, outIndex)
                                    lastAudioTimestamp = mAudioBufferInfo.presentationTimeUs
                                } else {
                                    Log.w(
                                        TAG,
                                        "Drops frame, current frame's timestamp " +
                                            mAudioBufferInfo.presentationTimeUs +
                                            " is earlier that last frame " + lastAudioTimestamp,
                                    )
                                    // Releases this frame from output buffer
                                    // 从输出缓冲区释放此帧
                                    audioEncoder.releaseOutputBuffer(outIndex, false)
                                }
                            }
                        }
                    } while (outIndex >= 0 && !audioEos) // end of dequeue output buffer / 出队输出缓冲区结束
                }
            } // end of while loop / while 循环结束
        } catch (e: RuntimeException) {
            // Don't let the audio thread die: stop audio and end the video stream below; the video
            // thread then reports this failure as the recording's result.
            // 不要让音频线程崩溃：在下面停止音频并结束视频流；随后由视频线程将此失败作为录像结果报告。
            Log.e(TAG, "audioEncode:", e)
            mIsRecording = false
            mAudioFailure.compareAndSet(null, e)
        }

        // Audio Stop
        // 音频停止
        try {
            Log.i(TAG, "audioRecorder stop")
            mAudioRecorder?.stop()
        } catch (e: IllegalStateException) {
            // The samples are already written; the video still gets its one result.
            // 采样已写入；视频仍只产生一个结果。
            Log.w(TAG, "Audio recorder stop failed!", e)
        }

        try {
            mAudioEncoder?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Audio encoder stop failed!", e)
        }

        Log.i(TAG, "Audio encode thread end")
        // Use AtomicBoolean to signal because MediaCodec.signalEndOfInputStream() is not thread
        // safe
        // 使用 AtomicBoolean 发出信号，因为 MediaCodec.signalEndOfInputStream() 不是线程
        // 安全的
        mEndOfVideoStreamSignal.set(true)
    }

    /**
     * Creates a [MediaFormat] using parameters for audio from the configuration
     *
     * 使用配置中的音频参数创建 [MediaFormat]
     */
    private fun createAudioMediaFormat(): MediaFormat {
        val format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mAudioSampleRate, mAudioChannelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitRate)

        return format
    }

    /**
     * Create a AudioRecord object to get raw data
     *
     * 创建 AudioRecord 对象以获取原始数据
     */
    // RECORD_AUDIO is checked first thing below.
    // 下面一开始就检查了 RECORD_AUDIO。
    @SuppressLint("MissingPermission")
    private fun autoConfigAudioRecordSource(): AudioRecord? {
        // Without RECORD_AUDIO the video is recorded without sound.
        // 没有 RECORD_AUDIO 权限时，录制的视频没有声音。
        if (UVCUtils.getApplication().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO is not granted, recording without sound")
            return null
        }

        // Use channel count to determine stereo vs mono
        // 使用声道数判断是立体声还是单声道
        val channelConfig = if (mAudioChannelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO

        try {
            // Use only ENCODING_PCM_16BIT because it mandatory supported.
            // 仅使用 ENCODING_PCM_16BIT，因为它是强制支持的。
            var bufferSize = AudioRecord.getMinBufferSize(mAudioSampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)

            if (bufferSize <= 0) {
                bufferSize = mConfig.audioMinBufferSize
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                mAudioSampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )

            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                mAudioBufferSize = bufferSize
                Log.i(
                    TAG,
                    "source: " + MediaRecorder.AudioSource.CAMCORDER +
                        " audioSampleRate: " + mAudioSampleRate +
                        " channelConfig: " + channelConfig +
                        " audioFormat: " + AudioFormat.ENCODING_PCM_16BIT +
                        " bufferSize: " + bufferSize,
                )
                return recorder
            }
            // Free the native resources of a recorder that failed to initialize
            // 释放初始化失败的 recorder 的 native 资源
            recorder.release()
        } catch (e: Exception) {
            Log.e(TAG, "Exception, keep trying.", e)
        }
        return null
    }

    /**
     * Set audio record parameters
     *
     * 设置音频录制参数
     */
    private fun setAudioParameters() {
        val config = mConfig
        mAudioChannelCount = config.audioChannelCount
        mAudioSampleRate = config.audioSampleRate
        mAudioBitRate = config.audioBitRate
    }

    private fun removeRecordingResultIfNoVideoKeyFrameArrived(outputFileOptions: OutputFileOptions): Boolean {
        // 1. There should be one video key frame at least.
        // 1. 至少应有一个视频关键帧。
        Log.i(TAG, "check Recording Result First Video Key Frame Write: " + mIsFirstVideoKeyFrameWrite.get())
        val checkKeyFrame = mIsFirstVideoKeyFrameWrite.get()
        if (!checkKeyFrame) {
            Log.i(TAG, "The recording result has no key frame.")
        }

        // 2. If no key frame, remove file except the target is a file descriptor case.
        // 2. 如果没有关键帧，则删除文件，但目标为 file descriptor 的情况除外。
        if (!checkKeyFrame) {
            if (outputFileOptions.isSavingToFile) {
                Log.i(TAG, "Delete file.")
                outputFileOptions.file?.delete()
            } else if (outputFileOptions.isSavingToMediaStore) {
                Log.i(TAG, "Delete file.")
                val savedUri = mSavedVideoUri
                if (savedUri != null) {
                    outputFileOptions.contentResolver?.delete(savedUri, null, null)
                }
            }
        }

        return checkKeyFrame
    }

    @Throws(IOException::class)
    private fun initMediaMuxer(outputFileOptions: OutputFileOptions): MediaMuxer? {
        if (outputFileOptions.isSavingToFile) {
            val savedVideoFile = outputFileOptions.file!!
            mSavedVideoUri = Uri.fromFile(savedVideoFile)
            return MediaMuxer(savedVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
        if (outputFileOptions.isSavingToFileDescriptor) {
            return MediaMuxer(outputFileOptions.fileDescriptor!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
        require(outputFileOptions.isSavingToMediaStore) { "The OutputFileOptions should assign before recording" }

        val contentResolver = outputFileOptions.contentResolver!!
        val saveCollection = outputFileOptions.saveCollection!!
        val values = outputFileOptions.contentValues?.let { ContentValues(it) } ?: ContentValues()

        // Hide the entry from other apps until the video is complete.
        // 在视频完成之前，对其他应用隐藏该条目。
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val savedUri = contentResolver.insert(saveCollection, values) ?: throw IOException("Invalid Uri!")
        mSavedVideoUri = savedUri

        // If this throws, the caller closes the descriptor and deletes the entry.
        // 如果此处抛出异常，由调用方关闭文件描述符并删除该条目。
        val parcelFileDescriptor = contentResolver.openFileDescriptor(savedUri, "rw") ?: return null
        mParcelFileDescriptor = parcelFileDescriptor
        return MediaMuxer(parcelFileDescriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * Describes the error that occurred during video capture operations.
     *
     * This is a parameter sent to the error callback functions set in listeners such as
     * [OnVideoCaptureCallback.onError].
     *
     * See message parameter in onError callback or log for more details.
     *
     * 描述视频采集操作期间发生的错误。
     *
     * 这是发送给错误回调函数的参数，这些回调函数在监听器中设置，例如 [OnVideoCaptureCallback.onError]。
     *
     * 详细信息请参见 onError 回调中的 message 参数或日志。
     */
    @IntDef(
        ERROR_UNKNOWN, ERROR_ENCODER, ERROR_MUXER, ERROR_RECORDING_IN_PROGRESS,
        ERROR_FILE_IO, ERROR_INVALID_CAMERA, ERROR_RECORDING_TOO_SHORT,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class VideoCaptureError

    private enum class VideoEncoderInitStatus {
        VIDEO_ENCODER_INIT_STATUS_UNINITIALIZED,
        VIDEO_ENCODER_INIT_STATUS_INITIALIZED_FAILED,
        VIDEO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE,
        VIDEO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED,
    }

    private enum class AudioEncoderInitStatus {
        AUDIO_ENCODER_INIT_STATUS_UNINITIALIZED,
        AUDIO_ENCODER_INIT_STATUS_INITIALIZED_FAILED,
        AUDIO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE,
        AUDIO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED,
    }

    /**
     * Listener containing callbacks for video file I/O events.
     *
     * 包含视频文件 I/O 事件回调的监听器。
     */
    interface OnVideoCaptureCallback {
        /**
         * Called when started recording successfully.
         *
         * 成功开始录制时调用。
         */
        fun onStart()

        /**
         * Called when the video has been successfully saved.
         *
         * 视频成功保存时调用。
         */
        fun onVideoSaved(outputFileResults: OutputFileResults)

        /**
         * Called when an error occurs while attempting to save the video.
         *
         * 尝试保存视频时发生错误时调用。
         */
        fun onError(@VideoCaptureError videoCaptureError: Int, message: String, cause: Throwable?)
    }

    private inner class VideoCaptureListenerWrapper(
        private val mOnVideoCaptureCallback: OnVideoCaptureCallback,
    ) : OnVideoCaptureCallback {

        override fun onStart() {
            mMainHandler.post { mOnVideoCaptureCallback.onStart() }
        }

        override fun onVideoSaved(outputFileResults: OutputFileResults) {
            mMainHandler.post { mOnVideoCaptureCallback.onVideoSaved(outputFileResults) }
        }

        override fun onError(@VideoCaptureError videoCaptureError: Int, message: String, cause: Throwable?) {
            mMainHandler.post { mOnVideoCaptureCallback.onError(videoCaptureError, message, cause) }
        }
    }

    /**
     * Info about the saved video file.
     *
     * 已保存视频文件的信息。
     *
     * @property savedUri The [Uri] of the saved video file, or null when it was saved to a file
     * descriptor. / 已保存视频文件的 [Uri]；保存到文件描述符时为 null。
     */
    class OutputFileResults internal constructor(val savedUri: Uri?)

    /**
     * Options for saving newly captured video.
     *
     * this class is used to configure save location and other options. Save location can be
     * either a [File], [MediaStore]. The metadata will be
     * stored with the saved video.
     *
     * 保存新采集视频的选项。
     *
     * 此类用于配置保存位置及其他选项。保存位置可以是 [File] 或 [MediaStore]。元数据将与保存的视频一起存储。
     */
    class OutputFileOptions internal constructor(
        /**
         * The File object which is set by the [Builder].
         *
         * 由 [Builder] 设置的 File 对象。
         */
        internal val file: File?,
        /**
         * The FileDescriptor object which is set by the [Builder].
         *
         * 由 [Builder] 设置的 FileDescriptor 对象。
         */
        internal val fileDescriptor: FileDescriptor?,
        /**
         * The content resolver which is set by the [Builder].
         *
         * 由 [Builder] 设置的 content resolver。
         */
        internal val contentResolver: ContentResolver?,
        /**
         * The URI which is set by the [Builder].
         *
         * 由 [Builder] 设置的 URI。
         */
        internal val saveCollection: Uri?,
        /**
         * The content values which is set by the [Builder].
         *
         * 由 [Builder] 设置的 content values。
         */
        internal val contentValues: ContentValues?,
    ) {

        /**
         * Checking the caller wants to save video to MediaStore.
         *
         * 检查调用方是否希望将视频保存到 MediaStore。
         */
        internal val isSavingToMediaStore: Boolean
            get() = saveCollection != null && contentResolver != null && contentValues != null

        /**
         * Checking the caller wants to save video to a File.
         *
         * 检查调用方是否希望将视频保存到 File。
         */
        internal val isSavingToFile: Boolean
            get() = file != null

        /**
         * Checking the caller wants to save video to a FileDescriptor.
         *
         * 检查调用方是否希望将视频保存到 FileDescriptor。
         */
        internal val isSavingToFileDescriptor: Boolean
            get() = fileDescriptor != null

        /**
         * Builder class for [OutputFileOptions].
         *
         * [OutputFileOptions] 的 Builder 类。
         */
        class Builder {
            private var mFile: File? = null
            private var mFileDescriptor: FileDescriptor? = null
            private var mContentResolver: ContentResolver? = null
            private var mSaveCollection: Uri? = null
            private var mContentValues: ContentValues? = null

            /**
             * Creates options to write captured video to a [File].
             *
             * 创建用于将采集的视频写入 [File] 的选项。
             *
             * @param file save location of the video. / 视频的保存位置
             */
            constructor(file: File) {
                mFile = file
            }

            /**
             * Creates options to write captured video to a [FileDescriptor].
             *
             * Using a FileDescriptor to record a video is only supported for Android 8.0 or
             * above.
             *
             * 创建用于将采集的视频写入 [FileDescriptor] 的选项。
             *
             * 使用 FileDescriptor 录制视频仅支持 Android 8.0 及以上版本。
             *
             * @param fileDescriptor to save the video. / 用于保存视频
             */
            constructor(fileDescriptor: FileDescriptor) {
                mFileDescriptor = fileDescriptor
            }

            /**
             * Creates options to write captured video to [MediaStore].
             *
             * 创建用于将采集的视频写入 [MediaStore] 的选项。
             *
             * Example: / 示例：
             *
             * ```
             * ContentValues contentValues = new ContentValues();
             * contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO");
             * contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
             *
             * OutputFileOptions options = new OutputFileOptions.Builder(
             *         getContentResolver(),
             *         MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
             *         contentValues).build();
             * ```
             *
             * @param contentResolver to access [MediaStore] / 用于访问 [MediaStore]
             * @param saveCollection  The URL of the table to insert into. / 要插入的表的 URL。
             * @param contentValues   to be included in the created video file. / 要包含在所创建视频文件中的内容值。
             */
            constructor(contentResolver: ContentResolver, saveCollection: Uri, contentValues: ContentValues) {
                mContentResolver = contentResolver
                mSaveCollection = saveCollection
                mContentValues = contentValues
            }

            /**
             * Builds [OutputFileOptions].
             *
             * 构建 [OutputFileOptions]。
             */
            fun build(): OutputFileOptions =
                OutputFileOptions(mFile, mFileDescriptor, mContentResolver, mSaveCollection, mContentValues)
        }
    }

    companion object {
        /**
         * An unknown error occurred.
         *
         * See message parameter in onError callback or log for more details.
         *
         * 发生未知错误。
         *
         * 详细信息请参见 onError 回调中的 message 参数或日志。
         */
        const val ERROR_UNKNOWN = 0

        /**
         * An error occurred with encoder state, either when trying to change state or when an
         * unexpected state change occurred.
         *
         * 发生编码器状态错误，可能是在尝试更改状态时，也可能是发生了意外的状态变更。
         */
        const val ERROR_ENCODER = 1

        /**
         * An error with muxer state such as during creation or when stopping.
         *
         * 发生 muxer 状态错误，例如在创建或停止期间。
         */
        const val ERROR_MUXER = 2

        /**
         * An error indicating start recording was called when video recording is still in progress.
         *
         * 表示在视频录制仍在进行时调用了开始录制。
         */
        const val ERROR_RECORDING_IN_PROGRESS = 3

        /**
         * An error indicating the file saving operations.
         *
         * 表示文件保存操作出错。
         */
        const val ERROR_FILE_IO = 4

        /**
         * An error indicating this VideoCapture is not bound to a camera.
         *
         * 表示此 VideoCapture 未绑定到相机。
         */
        const val ERROR_INVALID_CAMERA = 5

        /**
         * An error indicating the video file is too short.
         * The output file will be deleted if the OutputFileOptions is backed by File or uri.
         *
         * 表示视频文件过短。
         * 如果 OutputFileOptions 基于 File 或 uri，则输出文件将被删除。
         */
        const val ERROR_RECORDING_TOO_SHORT = 6
    }
}
