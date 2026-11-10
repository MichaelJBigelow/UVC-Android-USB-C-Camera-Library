package com.herohan.uvcapp;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.serenegiant.usb.Size;
import com.serenegiant.utils.UVCUtils;
import com.serenegiant.utils.UriHelper;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoCapture {
    private static final String TAG = VideoCapture.class.getSimpleName();

    /**
     * An unknown error occurred.
     *
     * <p>See message parameter in onError callback or log for more details.
     *
     * 发生未知错误。
     *
     * <p>详细信息请参见 onError 回调中的 message 参数或日志。
     */
    public static final int ERROR_UNKNOWN = 0;
    /**
     * An error occurred with encoder state, either when trying to change state or when an
     * unexpected state change occurred.
     *
     * 发生编码器状态错误，可能是在尝试更改状态时，也可能是发生了意外的状态变更。
     */
    public static final int ERROR_ENCODER = 1;
    /**
     * An error with muxer state such as during creation or when stopping.
     *
     * 发生 muxer 状态错误，例如在创建或停止期间。
     */
    public static final int ERROR_MUXER = 2;
    /**
     * An error indicating start recording was called when video recording is still in progress.
     *
     * 表示在视频录制仍在进行时调用了开始录制。
     */
    public static final int ERROR_RECORDING_IN_PROGRESS = 3;
    /**
     * An error indicating the file saving operations.
     *
     * 表示文件保存操作出错。
     */
    public static final int ERROR_FILE_IO = 4;
    /**
     * An error indicating this VideoCapture is not bound to a camera.
     *
     * 表示此 VideoCapture 未绑定到相机。
     */
    public static final int ERROR_INVALID_CAMERA = 5;
    /**
     * An error indicating the video file is too short.
     * <p> The output file will be deleted if the OutputFileOptions is backed by File or uri.
     *
     * 表示视频文件过短。
     * <p> 如果 OutputFileOptions 基于 File 或 uri，则输出文件将被删除。
     */
    public static final int ERROR_RECORDING_TOO_SHORT = 6;

    /**
     * Amount of time to wait for dequeuing a buffer from the videoEncoder.
     *
     * 等待从 videoEncoder 出队缓冲区的时间量。
     */
    private static final int DEQUE_TIMEOUT_USEC = 10000;
    /**
     * Android preferred mime type for AVC video.
     *
     * Android 推荐的 AVC 视频 mime 类型。
     */
    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";

    private final MediaCodec.BufferInfo mVideoBufferInfo = new MediaCodec.BufferInfo();
    private final MediaCodec.BufferInfo mAudioBufferInfo = new MediaCodec.BufferInfo();
    private final Object mMuxerLock = new Object();
    private final AtomicBoolean mEndOfVideoStreamSignal = new AtomicBoolean(true);
    private final AtomicBoolean mEndOfAudioStreamSignal = new AtomicBoolean(true);
    private final AtomicBoolean mEndOfAudioVideoSignal = new AtomicBoolean(true);
    /**
     * For record the first sample written time.
     *
     * 用于记录写入第一个样本的时间。
     */
    @VisibleForTesting()
    public final AtomicBoolean mIsFirstVideoKeyFrameWrite = new AtomicBoolean(false);
    @VisibleForTesting()
    public final AtomicBoolean mIsFirstAudioSampleWrite = new AtomicBoolean(false);

    /**
     * Thread on which all encoding occurs.
     *
     * 所有编码操作所在的线程。
     */
    private HandlerThread mVideoHandlerThread;
    private Handler mVideoHandler;
    /**
     * Thread on which audio encoding occurs.
     *
     * 音频编码所在的线程。
     */
    private HandlerThread mAudioHandlerThread;
    private Handler mAudioHandler;

    MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private FutureTask<String> mRecordingFuture = null;
    private final AtomicBoolean mRecordingWaitRelease = new AtomicBoolean(false);

    /**
     * The muxer that writes the encoding data to file.
     *
     * 将编码数据写入文件的 muxer。
     */
    @GuardedBy("mMuxerLock")
    private MediaMuxer mMuxer;
    private final AtomicBoolean mMuxerStarted = new AtomicBoolean(false);
    /**
     * The index of the video track used by the muxer.
     *
     * muxer 所使用的视频轨道的索引。
     */
    @GuardedBy("mMuxerLock")
    private int mVideoTrackIndex;
    /**
     * The index of the audio track used by the muxer.
     *
     * muxer 所使用的音频轨道的索引。
     */
    @GuardedBy("mMuxerLock")
    private int mAudioTrackIndex;
    /**
     * Surface the camera writes to, which the videoEncoder uses as input.
     *
     * 相机写入的 Surface，videoEncoder 将其用作输入。
     */
    Surface mCameraSurface;

    private Uri mSavedVideoUri;
    private ParcelFileDescriptor mParcelFileDescriptor;

    /**
     * audio raw data
     *
     * 音频原始数据
     */
    @Nullable
    private AudioRecord mAudioRecorder;
    private volatile int mAudioBufferSize;
    private volatile boolean mIsRecording = false;
    private int mAudioChannelCount;
    private int mAudioSampleRate;
    private int mAudioBitRate;
    private final AtomicBoolean mIsAudioEnabled = new AtomicBoolean(true);

    private VideoEncoderInitStatus mVideoEncoderInitStatus =
            VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_UNINITIALIZED;
    @Nullable
    private Throwable mVideoEncoderErrorMessage;

    private AudioEncoderInitStatus mAudioEncoderInitStatus =
            AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_UNINITIALIZED;
    @Nullable
    private Throwable mAudioEncoderErrorMessage;

    private WeakReference<ICameraRendererHolder> mRendererHolderWeak;
    private VideoCaptureConfig mConfig;
    private Size mResolution;

    private Handler mMainHandler;

    private ExecutorService mExecutor;

    VideoCapture(ICameraRendererHolder rendererHolder,
                 VideoCaptureConfig config,
                 Size resolution) {
        this.mRendererHolderWeak = new WeakReference<>(rendererHolder);
        this.mConfig = (VideoCaptureConfig) config.clone();
        this.mResolution = resolution;
        this.mMainHandler = new Handler(Looper.getMainLooper());

        this.mExecutor = Executors.newFixedThreadPool(1, new ThreadFactory() {
            private final AtomicInteger mId = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, TAG + "video_capture" + mId.getAndIncrement());
            }
        });

        initVideoAudioHandler();
        initVideoAudioEncoder();
    }

    void setConfig(VideoCaptureConfig config) {
        this.mConfig = (VideoCaptureConfig) config.clone();
        initVideoAudioEncoder();
    }

    void setResolution(Size resolution) {
        this.mResolution = resolution;
        initVideoAudioEncoder();
    }

    private void initVideoAudioHandler() {
        mVideoHandlerThread = new HandlerThread(TAG + "video encoding thread");
        mAudioHandlerThread = new HandlerThread(TAG + "audio encoding thread");

        // video thread start
        // 视频线程启动
        mVideoHandlerThread.start();
        mVideoHandler = new Handler(mVideoHandlerThread.getLooper());

        // audio thread start
        // 音频线程启动
        mAudioHandlerThread.start();
        mAudioHandler = new Handler(mAudioHandlerThread.getLooper());
    }

    /**
     * Creates a {@link MediaFormat} using size and default configs
     *
     * 使用尺寸和默认配置创建 {@link MediaFormat}
     */
    private MediaFormat createVideoMediaFormat() {
        MediaFormat format =
                MediaFormat.createVideoFormat(
                        VIDEO_MIME_TYPE, mResolution.width, mResolution.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE,
                mConfig.hasBitRate() ? mConfig.getBitRate() : 8 * mResolution.width * mResolution.height);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mConfig.getVideoFrameRate());
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mConfig.getIFrameInterval());

        return format;
    }

    private void initVideoAudioEncoder() {
        if (mCameraSurface != null) {
            if (mVideoEncoder != null) {
                mVideoEncoder.stop();
                mVideoEncoder.release();
            }
            if (mAudioEncoder != null) {
                mAudioEncoder.stop();
                mAudioEncoder.release();
            }
            releaseCameraSurface(false);
        }

        try {
            mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create Video MediaCodec due to: " + e.getCause());
        }
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        } catch (Exception e) {
            mIsAudioEnabled.set(false);
            Log.e(TAG, "Unable to create Audio MediaCodec, disable audio." + e.getMessage(), e);
        }

        setupEncoder();
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    /**
     * Starts recording video, which continues until {@link VideoCapture#stopRecording()} is
     * called.
     *
     * <p>StartRecording() is asynchronous. User needs to check if any error occurs by setting the
     * {@link OnVideoCaptureCallback#onError(int, String, Throwable)}.
     *
     * 开始录制视频，直到调用 {@link VideoCapture#stopRecording()} 为止。
     *
     * <p>StartRecording() 是异步的。用户需要通过设置 {@link OnVideoCaptureCallback#onError(int, String, Throwable)} 来检查是否发生错误。
     *
     * @param outputFileOptions Location to save the video capture / 保存视频采集的位置
     * @param callback          Callback for when the recorded video saving completion or failure. / 录制的视频保存完成或失败时的回调
     */
    @SuppressWarnings("ObjectToString")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void startRecording(
            @NonNull OutputFileOptions outputFileOptions,
            @NonNull OnVideoCaptureCallback callback) {
        Log.i(TAG, "startRecording");
        mIsFirstVideoKeyFrameWrite.set(false);
        mIsFirstAudioSampleWrite.set(false);

        OnVideoCaptureCallback postListener = new VideoCaptureListenerWrapper(callback);

        if (mRendererHolderWeak.get() == null) {
            // Not bound. Notify callback.
            // 未绑定。通知回调。
            postListener.onError(ERROR_INVALID_CAMERA,
                    "Not bound to a Camera", null);
            return;
        }

        // Check video encoder initialization status, if there is any error happened
        // return error callback directly.
        // 检查视频编码器的初始化状态，如果发生任何错误，
        // 直接返回错误回调。
        if (mVideoEncoderInitStatus
                == VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE
                || mVideoEncoderInitStatus
                == VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_INITIALIZED_FAILED
                || mVideoEncoderInitStatus
                == VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED) {
            postListener.onError(ERROR_ENCODER, "Video encoder initialization failed before start"
                    + " recording ", mVideoEncoderErrorMessage);
            return;
        }

        // Check audio encoder initialization status, if there is any error happened
        // return error callback directly.
        // 检查音频编码器的初始化状态，如果发生任何错误，
        // 直接返回错误回调。
        if (mAudioEncoderInitStatus
                == AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE
                || mAudioEncoderInitStatus
                == AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_INITIALIZED_FAILED
                || mAudioEncoderInitStatus
                == AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED) {
            postListener.onError(ERROR_ENCODER, "Audio encoder initialization failed before start"
                    + " recording ", mAudioEncoderErrorMessage);
            return;
        }

        if (!mEndOfAudioVideoSignal.get()) {
            postListener.onError(
                    ERROR_RECORDING_IN_PROGRESS, "It is still in video recording!",
                    null);
            return;
        }

        if (mIsAudioEnabled.get()) {
            try {
                // Audio input start
                // 音频输入启动
                if (mAudioRecorder != null && mAudioRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                    mAudioRecorder.startRecording();
                }
            } catch (IllegalStateException e) {
                // Disable the audio if the audio input cannot start. And Continue the recording
                // without audio.
                // 如果音频输入无法启动，则禁用音频，并在没有音频的情况下
                // 继续录制。
                Log.i(TAG,
                        "AudioRecorder cannot start recording, disable audio." + e.getMessage());
                mIsAudioEnabled.set(false);
                releaseAudioInputResource();
            }

            // Gets the AudioRecorder's state
            // 获取 AudioRecorder 的状态
            if (mAudioRecorder != null && mAudioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.i(TAG,
                        "AudioRecorder startRecording failed - incorrect state: "
                                + mAudioRecorder.getRecordingState());
                mIsAudioEnabled.set(false);
                releaseAudioInputResource();
            }
        }

        mRecordingFuture = new FutureTask<>(new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (mRendererHolderWeak.get() != null && mCameraSurface != null) {
                    mRendererHolderWeak.get().removeSlaveSurface(mCameraSurface.hashCode());
                }

                if (mRecordingWaitRelease.get()) {
                    mRecordingWaitRelease.set(false);
                    releaseResources();
                    mIsRecording = false;
                    return "releaseResources";
                }

                // Do the setup of the videoEncoder at the end of video recording instead of at the
                // start of recording because it requires attaching a new Surface. This causes a
                // glitch so we don't want that to incur latency at the start of capture.
                // 在视频录制结束时而不是录制开始时设置 videoEncoder，
                // 因为这需要附加一个新的 Surface。这会导致画面卡顿，
                // 所以我们不希望它在采集开始时造成延迟。
                setupEncoder();
                return "startRecording";
            }
        });

        try {
            // video encoder start
            // 视频编码器启动
            Log.i(TAG, "videoEncoder start");
            mVideoEncoder.start();

            // audio encoder start
            // 音频编码器启动
            if (mIsAudioEnabled.get()) {
                Log.i(TAG, "audioEncoder start");
                mAudioEncoder.start();
            }
        } catch (IllegalStateException e) {
            mExecutor.execute(mRecordingFuture);
            postListener.onError(ERROR_ENCODER, "Audio/Video encoder start fail", e);
            return;
        }

        try {
            synchronized (mMuxerLock) {
                mMuxer = initMediaMuxer(outputFileOptions);
                if (mMuxer == null) {
                    throw new IllegalArgumentException(
                            "MediaMuxer creation failed!");
                }
            }
        } catch (IOException e) {
            mExecutor.execute(mRecordingFuture);
            postListener.onError(ERROR_MUXER, "MediaMuxer creation failed!", e);
            return;
        }

        mEndOfVideoStreamSignal.set(false);
        mEndOfAudioStreamSignal.set(false);
        mEndOfAudioVideoSignal.set(false);
        mIsRecording = true;

        postListener.onStart();

        // Attach Surface to renderer holder.
        // 将 Surface 附加到 renderer holder。
        mRendererHolderWeak.get().addSlaveSurface(mCameraSurface.hashCode(), mCameraSurface, true);

        if (mIsAudioEnabled.get()) {
            mAudioHandler.post(() -> audioEncode(postListener));
        }

        mVideoHandler.post(
                () -> {
                    boolean errorOccurred = videoEncode(postListener, outputFileOptions);
                    if (!errorOccurred) {
                        scanMediaFile(mSavedVideoUri);
                        postListener.onVideoSaved(new OutputFileResults(mSavedVideoUri));
                        mSavedVideoUri = null;
                    }
                    mExecutor.execute(mRecordingFuture);
                });
    }

    /**
     * Stops recording video, this must be called after {@link
     * VideoCapture#startRecording(OutputFileOptions, OnVideoCaptureCallback)} is
     * called.
     *
     * <p>stopRecording() is asynchronous API. User need to check if {@link
     * OnVideoCaptureCallback#onVideoSaved(OutputFileResults)} or
     * {@link OnVideoCaptureCallback#onError(int, String, Throwable)} be called
     * before startRecording.
     *
     * 停止录制视频，必须在调用 {@link VideoCapture#startRecording(OutputFileOptions, OnVideoCaptureCallback)} 之后调用。
     *
     * <p>stopRecording() 是异步 API。用户需要检查在再次 startRecording 之前，是否已调用 {@link OnVideoCaptureCallback#onVideoSaved(OutputFileResults)} 或 {@link OnVideoCaptureCallback#onError(int, String, Throwable)}。
     */
    public void stopRecording() {
        Log.i(TAG, "stopRecording");

        if (mIsRecording) {
            if (mIsAudioEnabled.get()) {
                // Stop audio encoder thread, and wait video encoder and muxer stop.
                // 停止音频编码器线程，并等待视频编码器和 muxer 停止。
                mEndOfAudioStreamSignal.set(true);
            } else {
                // Audio is disabled, stop video encoder thread directly.
                // 音频已禁用，直接停止视频编码器线程。
                mEndOfVideoStreamSignal.set(true);
            }
        }
    }

    public void release() {
        stopRecording();

        if (mRecordingFuture != null) {
            mRecordingWaitRelease.set(true);
        } else {
            releaseResources();
        }
    }

    private void releaseResources() {
        mVideoHandlerThread.quitSafely();

        // audio encoder release
        // 音频编码器释放
        releaseAudioInputResource();

        if (mCameraSurface != null) {
            releaseCameraSurface(true);
        }
    }

    private void releaseAudioInputResource() {
        mAudioHandlerThread.quitSafely();
        if (mAudioEncoder != null) {
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mAudioRecorder != null) {
            mAudioRecorder.release();
            mAudioRecorder = null;
        }
    }

    @UiThread
    private void releaseCameraSurface(final boolean releaseVideoEncoder) {
        if (releaseVideoEncoder && mVideoEncoder != null) {
            mVideoEncoder.release();
            mVideoEncoder = null;
        }

        if (mCameraSurface != null) {
            mCameraSurface.release();
            mCameraSurface = null;
        }
    }

    /**
     * Setup the {@link MediaCodec} for encoding video from a camera {@link Surface} and encoding
     * audio from selected audio source.
     *
     * 设置 {@link MediaCodec}，用于对来自相机 {@link Surface} 的视频以及来自所选音频源的音频进行编码。
     */
    @UiThread
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    void setupEncoder() {

        // video encoder setup
        // 视频编码器设置
        mVideoEncoder.reset();
        mVideoEncoderInitStatus = VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_UNINITIALIZED;

        // Configures a Video encoder, if there is any exception, will abort follow up actions
        // 配置视频编码器，如果出现任何异常，将中止后续操作
        try {
            mVideoEncoder.configure(
                    createVideoMediaFormat(), /*surface*/
                    null, /*crypto*/
                    null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Exception e) {
            handleVideoEncoderInitError(e);
            return;
        }

        if (mCameraSurface != null) {
            releaseCameraSurface(false);
        }
        Surface cameraSurface = mVideoEncoder.createInputSurface();
        mCameraSurface = cameraSurface;

        // audio encoder setup
        // 音频编码器设置
        // reset audio inout flag
        // 重置音频输入标志
        mIsAudioEnabled.set(mConfig.getAudioCaptureEnable());

        if (mIsAudioEnabled.get()) {
            setAudioParameters();
            mAudioEncoder.reset();
            // Configures a Audio encoder, if there is any exception, will abort follow up actions
            // 配置音频编码器，如果出现任何异常，将中止后续操作
            try {
                mAudioEncoder.configure(
                        createAudioMediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (Exception e) {
                handleAudioEncoderInitError(e);
                return;
            }

            if (mAudioRecorder != null) {
                mAudioRecorder.release();
            }
            mAudioRecorder = autoConfigAudioRecordSource();
            // check mAudioRecorder
            // 检查 mAudioRecorder
            if (mAudioRecorder == null) {
                Log.e(TAG, "AudioRecord object cannot initialized correctly!");
                mIsAudioEnabled.set(false);
            }
        }

        synchronized (mMuxerLock) {
            mVideoTrackIndex = -1;
            mAudioTrackIndex = -1;
        }
        mIsRecording = false;
    }

    private void handleVideoEncoderInitError(Exception e) {
        mVideoEncoderInitStatus =
                VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_INITIALIZED_FAILED;
        mVideoEncoderErrorMessage = e;

        if (e instanceof MediaCodec.CodecException) {
            MediaCodec.CodecException e1 = (MediaCodec.CodecException) e;
            int errorCode = e1.getErrorCode();
            Log.i(TAG,
                    "CodecException: code: " + errorCode + " diagnostic: " + e1.getDiagnosticInfo());
            if (errorCode == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                mVideoEncoderInitStatus =
                        VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE;
            } else if (errorCode == MediaCodec.CodecException.ERROR_RECLAIMED) {
                mVideoEncoderInitStatus =
                        VideoEncoderInitStatus.VIDEO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED;
            }
        }
    }

    private void handleAudioEncoderInitError(Exception e) {
        mAudioEncoderInitStatus =
                AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_INITIALIZED_FAILED;
        mAudioEncoderErrorMessage = e;

        if (e instanceof MediaCodec.CodecException) {
            MediaCodec.CodecException e1 = (MediaCodec.CodecException) e;
            int errorCode = e1.getErrorCode();
            Log.i(TAG,
                    "CodecException: code: " + errorCode + " diagnostic: " + e1.getDiagnosticInfo());
            if (errorCode == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                mAudioEncoderInitStatus =
                        AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE;
            } else if (errorCode == MediaCodec.CodecException.ERROR_RECLAIMED) {
                mAudioEncoderInitStatus =
                        AudioEncoderInitStatus.AUDIO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED;
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
    private boolean writeVideoEncodedBuffer(int bufferIndex) {
        if (bufferIndex < 0) {
            Log.e(TAG, "Output buffer should not have negative index: " + bufferIndex);
            return false;
        }
        // Get data from buffer
        // 从缓冲区获取数据
        ByteBuffer outputBuffer = getOutputBuffer(mVideoEncoder, bufferIndex);

        // Check if buffer is valid, if not then return
        // 检查缓冲区是否有效，无效则返回
        if (outputBuffer == null) {
            Log.d(TAG, "OutputBuffer was null.");
            return false;
        }

        // Write data to mMuxer if available
        // 如果有可用数据，则写入 mMuxer
        if (mMuxerStarted.get()) {
            if (mVideoBufferInfo.size > 0) {
                outputBuffer.position(mVideoBufferInfo.offset);
                outputBuffer.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);
                mVideoBufferInfo.presentationTimeUs = (System.nanoTime() / 1000);

                synchronized (mMuxerLock) {

                    if (!mIsFirstVideoKeyFrameWrite.get()) {
                        boolean isKeyFrame =
                                (mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                        if (isKeyFrame) {
                            Log.i(TAG,
                                    "First video key frame written.");
                            mIsFirstVideoKeyFrameWrite.set(true);
                        } else {
                            // Request a sync frame immediately
                            // 立即请求同步帧
                            final Bundle syncFrame = new Bundle();
                            syncFrame.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                            mVideoEncoder.setParameters(syncFrame);
                        }
                    }

                    mMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, mVideoBufferInfo);
                }
            } else {
                Log.i(TAG, "mVideoBufferInfo.size <= 0, index " + bufferIndex);
            }
        }

        // Release data
        // 释放数据
        mVideoEncoder.releaseOutputBuffer(bufferIndex, false);

        // Return true if EOS is set
        // 如果设置了 EOS，则返回 true
        return (mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    private boolean writeAudioEncodedBuffer(int bufferIndex) {
        ByteBuffer buffer = getOutputBuffer(mAudioEncoder, bufferIndex);
        buffer.position(mAudioBufferInfo.offset);
        if (mMuxerStarted.get()) {
            try {
                if (mAudioBufferInfo.size > 0 && mAudioBufferInfo.presentationTimeUs > 0) {
                    synchronized (mMuxerLock) {
                        if (!mIsFirstAudioSampleWrite.get()) {
                            Log.i(TAG, "First audio sample written.");
                            mIsFirstAudioSampleWrite.set(true);
                        }
                        mMuxer.writeSampleData(mAudioTrackIndex, buffer, mAudioBufferInfo);
                    }
                } else {
                    Log.i(TAG, "mAudioBufferInfo size: " + mAudioBufferInfo.size + " "
                            + "presentationTimeUs: " + mAudioBufferInfo.presentationTimeUs);
                }
            } catch (Exception e) {
                Log.e(
                        TAG,
                        "audio error:size="
                                + mAudioBufferInfo.size
                                + "/offset="
                                + mAudioBufferInfo.offset
                                + "/timeUs="
                                + mAudioBufferInfo.presentationTimeUs);
                e.printStackTrace();
            }
        }
        mAudioEncoder.releaseOutputBuffer(bufferIndex, false);
        return (mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    /**
     * Encoding which runs indefinitely until end of stream is signaled. This should not run on the
     * main thread otherwise it will cause the application to block.
     *
     * 持续运行的编码，直到发出流结束信号。不应在主线程上运行，否则会导致应用程序阻塞。
     *
     * @return returns {@code true} if an error condition occurred, otherwise returns {@code false} / 如果发生错误则返回 {@code true}，否则返回 {@code false}
     */
    boolean videoEncode(@NonNull OnVideoCaptureCallback videoSavedCallback,
                        @NonNull OutputFileOptions outputFileOptions) {
        // Main encoding loop. Exits on end of stream.
        // 主编码循环。在流结束时退出。
        boolean errorOccurred = false;
        boolean videoEos = false;
        try {
            while (!videoEos && !errorOccurred) {
                // Check for end of stream from main thread
                // 检查来自主线程的流结束信号
                if (mEndOfVideoStreamSignal.get()) {
                    mVideoEncoder.signalEndOfInputStream();
                    mEndOfVideoStreamSignal.set(false);
                }

                // Deque buffer to check for processing step
                // 出队缓冲区以检查处理步骤
                int outputBufferId =
                        mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, DEQUE_TIMEOUT_USEC);
                switch (outputBufferId) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        if (mMuxerStarted.get()) {
                            videoSavedCallback.onError(
                                    ERROR_ENCODER,
                                    "Unexpected change in video encoding format.",
                                    null);
                            errorOccurred = true;
                        }

                        synchronized (mMuxerLock) {
                            mVideoTrackIndex = mMuxer.addTrack(mVideoEncoder.getOutputFormat());

                            if ((mIsAudioEnabled.get() && mAudioTrackIndex >= 0
                                    && mVideoTrackIndex >= 0)
                                    || (!mIsAudioEnabled.get() && mVideoTrackIndex >= 0)) {
                                Log.i(TAG, "MediaMuxer started on video encode thread and audio "
                                        + "enabled: " + mIsAudioEnabled);
                                mMuxer.start();
                                mMuxerStarted.set(true);
                            }
                        }
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        // Timed out. Just wait until next attempt to deque.
                        // 超时。等待下一次出队尝试。
                        break;
                    default:
                        videoEos = writeVideoEncodedBuffer(outputBufferId);
                }
            }
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_ENCODER,
                    "Video encoder encode failed!", e);
            errorOccurred = true;
        }

        try {
            Log.i(TAG, "videoEncoder stop");
            mVideoEncoder.stop();
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_ENCODER,
                    "Video encoder stop failed!", e);
            errorOccurred = true;
        }

        try {
            // new MediaMuxer instance required for each new file written, and release current one.
            // 每写入一个新文件都需要一个新的 MediaMuxer 实例，并释放当前的实例。
            synchronized (mMuxerLock) {
                if (mMuxer != null) {
                    if (mMuxerStarted.get()) {
                        Log.i(TAG, "Muxer already started");
                        mMuxer.stop();
                    }
                    mMuxer.release();
                    mMuxer = null;
                }
            }

            // A final checking for recording result, if the recorded file has no key
            // frame, then the video file is not playable, needs to call
            // onError() and will be removed.
            // 对录制结果做最终检查，如果录制的文件没有关键帧，
            // 则该视频文件无法播放，需要调用
            // onError()，并且该文件将被删除。

            boolean checkResult = removeRecordingResultIfNoVideoKeyFrameArrived(outputFileOptions);

            if (!checkResult) {
                videoSavedCallback.onError(ERROR_RECORDING_TOO_SHORT,
                        "The file has no video key frame.", null);
                errorOccurred = true;
            }
        } catch (IllegalStateException e) {
            // The video encoder has not got the key frame yet.
            // 视频编码器尚未获得关键帧。
            Log.i(TAG, "muxer stop IllegalStateException: " + System.currentTimeMillis());
            Log.i(TAG,
                    "muxer stop exception, mIsFirstVideoKeyFrameWrite: "
                            + mIsFirstVideoKeyFrameWrite.get());
            if (mIsFirstVideoKeyFrameWrite.get()) {
                // If muxer throws IllegalStateException at this moment and also the key frame
                // has received, this will reported as a Muxer stop failed.
                // Otherwise, this error will be ERROR_RECORDING_TOO_SHORT.
                // 如果此时 muxer 抛出 IllegalStateException 并且已经收到关键帧，
                // 则会报告为 Muxer 停止失败。
                // 否则，该错误将为 ERROR_RECORDING_TOO_SHORT。
                videoSavedCallback.onError(ERROR_MUXER, "Muxer stop failed!", e);
            } else {
                videoSavedCallback.onError(ERROR_RECORDING_TOO_SHORT,
                        "The file has no video key frame.", null);
            }
            errorOccurred = true;
        }

        if (mParcelFileDescriptor != null) {
            try {
                mParcelFileDescriptor.close();
                mParcelFileDescriptor = null;
            } catch (IOException e) {
                videoSavedCallback.onError(ERROR_MUXER, "File descriptor close failed!", e);
                errorOccurred = true;
            }
        }

        mMuxerStarted.set(false);

        // notify the UI thread that the video recording has finished
        // 通知 UI 线程视频录制已结束
        mEndOfAudioVideoSignal.set(true);
        mIsFirstVideoKeyFrameWrite.set(false);

        Log.i(TAG, "Video encode thread end.");
        return errorOccurred;
    }

    boolean audioEncode(OnVideoCaptureCallback videoSavedCallback) {
        // Audio encoding loop. Exits on end of stream.
        // 音频编码循环。在流结束时退出。
        boolean audioEos = false;
        int outIndex;
        long lastAudioTimestamp = 0;
        while (!audioEos && mIsRecording) {
            // Check for end of stream from main thread
            // 检查来自主线程的流结束信号
            if (mEndOfAudioStreamSignal.get()) {
                mEndOfAudioStreamSignal.set(false);
                mIsRecording = false;
            }

            // get audio deque input buffer
            // 获取音频出队输入缓冲区
            if (mAudioEncoder != null && mAudioRecorder != null) {
                try {
                    int index = mAudioEncoder.dequeueInputBuffer(-1);
                    if (index >= 0) {
                        final ByteBuffer buffer = getInputBuffer(mAudioEncoder, index);
                        buffer.clear();
                        int length = mAudioRecorder.read(buffer, mAudioBufferSize);
                        if (length > 0) {
                            mAudioEncoder.queueInputBuffer(
                                    index,
                                    0,
                                    length,
                                    (System.nanoTime() / 1000),
                                    mIsRecording ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof MediaCodec.CodecException) {
                        Log.i(TAG, "audio dequeueInputBuffer CodecException " + e.getMessage());
                    } else if (e instanceof IllegalStateException) {
                        Log.i(TAG,
                                "audio dequeueInputBuffer IllegalStateException " + e.getMessage());
                    } else {
                        throw e;
                    }
                }

                // start to dequeue audio output buffer
                // 开始出队音频输出缓冲区
                do {
                    outIndex = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, 0);
                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            synchronized (mMuxerLock) {
                                mAudioTrackIndex = mMuxer.addTrack(mAudioEncoder.getOutputFormat());
                                if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                                    Log.i(TAG, "MediaMuxer start on audio encoder thread.");
                                    mMuxer.start();
                                    mMuxerStarted.set(true);
                                }
                            }
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            break;
                        default:
                            // Drops out of order audio frame if the frame's earlier than last
                            // frame.
                            // 如果音频帧早于上一帧，
                            // 则丢弃该乱序音频帧。
                            if (mAudioBufferInfo.presentationTimeUs > lastAudioTimestamp) {
                                audioEos = writeAudioEncodedBuffer(outIndex);
                                lastAudioTimestamp = mAudioBufferInfo.presentationTimeUs;
                            } else {
                                Log.w(TAG,
                                        "Drops frame, current frame's timestamp "
                                                + mAudioBufferInfo.presentationTimeUs
                                                + " is earlier that last frame "
                                                + lastAudioTimestamp);
                                // Releases this frame from output buffer
                                // 从输出缓冲区释放此帧
                                mAudioEncoder.releaseOutputBuffer(outIndex, false);
                            }
                    }
                } while (outIndex >= 0 && !audioEos); // end of dequeue output buffer / 出队输出缓冲区结束
            }
        } // end of while loop / while 循环结束

        // Audio Stop
        // 音频停止
        try {
            Log.i(TAG, "audioRecorder stop");
            if (mAudioRecorder != null) {
                mAudioRecorder.stop();
            }
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(
                    ERROR_ENCODER, "Audio recorder stop failed!", e);
        }

        try {
            mAudioEncoder.stop();
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_ENCODER,
                    "Audio encoder stop failed!", e);
        }

        Log.i(TAG, "Audio encode thread end");
        // Use AtomicBoolean to signal because MediaCodec.signalEndOfInputStream() is not thread
        // safe
        // 使用 AtomicBoolean 发出信号，因为 MediaCodec.signalEndOfInputStream() 不是线程
        // 安全的
        mEndOfVideoStreamSignal.set(true);

        return false;
    }

    private ByteBuffer getInputBuffer(MediaCodec codec, int index) {
        return codec.getInputBuffer(index);
    }

    private ByteBuffer getOutputBuffer(MediaCodec codec, int index) {
        return codec.getOutputBuffer(index);
    }

    /**
     * Creates a {@link MediaFormat} using parameters for audio from the configuration
     *
     * 使用配置中的音频参数创建 {@link MediaFormat}
     */
    private MediaFormat createAudioMediaFormat() {
        MediaFormat format =
                MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mAudioSampleRate,
                        mAudioChannelCount);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitRate);

        return format;
    }

    /**
     * Create a AudioRecord object to get raw data
     *
     * 创建 AudioRecord 对象以获取原始数据
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private AudioRecord autoConfigAudioRecordSource() {
        // Use channel count to determine stereo vs mono
        // 使用声道数判断是立体声还是单声道
        int channelConfig =
                mAudioChannelCount == 1
                        ? AudioFormat.CHANNEL_IN_MONO
                        : AudioFormat.CHANNEL_IN_STEREO;

        try {
            // Use only ENCODING_PCM_16BIT because it mandatory supported.
            // 仅使用 ENCODING_PCM_16BIT，因为它是强制支持的。
            int bufferSize =
                    AudioRecord.getMinBufferSize(mAudioSampleRate, channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT);

            if (bufferSize <= 0) {
                bufferSize = mConfig.getAudioMinBufferSize();
            }

            AudioRecord recorder =
                    new AudioRecord(
                            MediaRecorder.AudioSource.CAMCORDER,
                            mAudioSampleRate,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize * 2);

            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                mAudioBufferSize = bufferSize;
                Log.i(
                        TAG,
                        "source: "
                                + MediaRecorder.AudioSource.CAMCORDER
                                + " audioSampleRate: "
                                + mAudioSampleRate
                                + " channelConfig: "
                                + channelConfig
                                + " audioFormat: "
                                + AudioFormat.ENCODING_PCM_16BIT
                                + " bufferSize: "
                                + bufferSize);
                return recorder;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception, keep trying.", e);
        }
        return null;
    }

    /**
     * Set audio record parameters
     *
     * 设置音频录制参数
     */
    private void setAudioParameters() {
        mAudioChannelCount = mConfig.getAudioChannelCount();
        mAudioSampleRate = mConfig.getAudioSampleRate();
        mAudioBitRate = mConfig.getAudioBitRate();
    }

    private boolean removeRecordingResultIfNoVideoKeyFrameArrived(
            @NonNull OutputFileOptions outputFileOptions) {
        boolean checkKeyFrame;

        // 1. There should be one video key frame at least.
        // 1. 至少应有一个视频关键帧。
        Log.i(TAG,
                "check Recording Result First Video Key Frame Write: "
                        + mIsFirstVideoKeyFrameWrite.get());
        if (!mIsFirstVideoKeyFrameWrite.get()) {
            Log.i(TAG, "The recording result has no key frame.");
            checkKeyFrame = false;
        } else {
            checkKeyFrame = true;
        }

        // 2. If no key frame, remove file except the target is a file descriptor case.
        // 2. 如果没有关键帧，则删除文件，但目标为 file descriptor 的情况除外。
        if (outputFileOptions.isSavingToFile()) {
            File outputFile = outputFileOptions.getFile();
            if (!checkKeyFrame) {
                Log.i(TAG, "Delete file.");
                outputFile.delete();
            }
        } else if (outputFileOptions.isSavingToMediaStore()) {
            if (!checkKeyFrame) {
                Log.i(TAG, "Delete file.");
                if (mSavedVideoUri != null) {
                    ContentResolver contentResolver = outputFileOptions.getContentResolver();
                    if (contentResolver != null) {
                        contentResolver.delete(mSavedVideoUri, null, null);
                    }
                }
            }
        }

        return checkKeyFrame;
    }

    private MediaMuxer initMediaMuxer(@NonNull OutputFileOptions outputFileOptions)
            throws IOException {
        MediaMuxer mediaMuxer = null;

        if (outputFileOptions.isSavingToFile()) {
            File savedVideoFile = outputFileOptions.getFile();
            mSavedVideoUri = Uri.fromFile(outputFileOptions.getFile());

            if (savedVideoFile != null) {
                mediaMuxer = new MediaMuxer(savedVideoFile.getAbsolutePath(),
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
        } else if (outputFileOptions.isSavingToFileDescriptor()) {
            if (outputFileOptions.getFileDescriptor() != null) {
                mediaMuxer = new MediaMuxer(outputFileOptions.getFileDescriptor(),
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
        } else if (outputFileOptions.isSavingToMediaStore()) {
            ContentResolver contentResolver = outputFileOptions.getContentResolver();
            Uri saveCollection = outputFileOptions.getSaveCollection();
            ContentValues values = outputFileOptions.getContentValues() != null
                    ? new ContentValues(outputFileOptions.getContentValues())
                    : new ContentValues();

            if (contentResolver != null && saveCollection != null) {
                mSavedVideoUri = contentResolver.insert(saveCollection, values);

                if (mSavedVideoUri == null) {
                    throw new IOException("Invalid Uri!");
                }

                try {
                    mParcelFileDescriptor =
                            outputFileOptions.getContentResolver().openFileDescriptor(
                                    mSavedVideoUri, "rw");
                    if (mParcelFileDescriptor != null) {
                        mediaMuxer = new MediaMuxer(
                                mParcelFileDescriptor.getFileDescriptor(),
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    }
                } catch (IOException e) {
                    mSavedVideoUri = null;
                    throw e;
                }
            }
        } else {
            throw new IllegalArgumentException(
                    "The OutputFileOptions should assign before recording");
        }

        return mediaMuxer;
    }

    private void scanMediaFile(final Uri uri) {
        if (uri == null) {
            return;
        }
        final Context context = UVCUtils.getApplication();
        String path = UriHelper.getPath(context, uri);

        try {
            // invoke scanFile to update size of media file in MediaStore
            // 调用 scanFile 以更新 MediaStore 中媒体文件的大小
            MediaScannerConnection.scanFile(context, new String[]{path}, null, null);
        } catch (final Exception e) {
            Log.e(TAG, "MediaScannerConnection:", e);
        }
    }

    /**
     * Describes the error that occurred during video capture operations.
     *
     * <p>This is a parameter sent to the error callback functions set in listeners such as {@link
     * OnVideoCaptureCallback#onError(int, String, Throwable)}.
     *
     * <p>See message parameter in onError callback or log for more details.
     *
     * 描述视频采集操作期间发生的错误。
     *
     * <p>这是发送给错误回调函数的参数，这些回调函数在监听器中设置，例如 {@link OnVideoCaptureCallback#onError(int, String, Throwable)}。
     *
     * <p>详细信息请参见 onError 回调中的 message 参数或日志。
     */
    @IntDef({ERROR_UNKNOWN, ERROR_ENCODER, ERROR_MUXER, ERROR_RECORDING_IN_PROGRESS,
            ERROR_FILE_IO, ERROR_INVALID_CAMERA, ERROR_RECORDING_TOO_SHORT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface VideoCaptureError {
    }

    enum VideoEncoderInitStatus {
        VIDEO_ENCODER_INIT_STATUS_UNINITIALIZED,
        VIDEO_ENCODER_INIT_STATUS_INITIALIZED_FAILED,
        VIDEO_ENCODER_INIT_STATUS_INSUFFICIENT_RESOURCE,
        VIDEO_ENCODER_INIT_STATUS_RESOURCE_RECLAIMED,
    }

    enum AudioEncoderInitStatus {
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
    public interface OnVideoCaptureCallback {
        /**
         * Called when started recording successfully.
         *
         * 成功开始录制时调用。
         */
        void onStart();

        /**
         * Called when the video has been successfully saved.
         *
         * 视频成功保存时调用。
         */
        void onVideoSaved(@NonNull OutputFileResults outputFileResults);

        /**
         * Called when an error occurs while attempting to save the video.
         *
         * 尝试保存视频时发生错误时调用。
         */
        void onError(@VideoCaptureError int videoCaptureError, @NonNull String message,
                     @Nullable Throwable cause);
    }

    private final class VideoCaptureListenerWrapper implements OnVideoCaptureCallback {

        @NonNull
        OnVideoCaptureCallback mOnVideoCaptureCallback;

        VideoCaptureListenerWrapper(@NonNull OnVideoCaptureCallback onVideoCaptureCallback) {
            mOnVideoCaptureCallback = onVideoCaptureCallback;
        }

        @Override
        public void onStart() {
            mMainHandler.post(() -> mOnVideoCaptureCallback.onStart());
        }

        @Override
        public void onVideoSaved(@NonNull OutputFileResults outputFileResults) {
            mMainHandler.post(() -> mOnVideoCaptureCallback.onVideoSaved(outputFileResults));
        }

        @Override
        public void onError(@VideoCaptureError int videoCaptureError, @NonNull String message,
                            @Nullable Throwable cause) {
            mMainHandler.post(() -> mOnVideoCaptureCallback.onError(videoCaptureError, message, cause));
        }
    }

    /**
     * Info about the saved video file.
     *
     * 已保存视频文件的信息。
     */
    public static class OutputFileResults {
        @Nullable
        private Uri mSavedUri;

        OutputFileResults(@Nullable Uri savedUri) {
            mSavedUri = savedUri;
        }

        /**
         * Returns the {@link Uri} of the saved video file.
         *
         * <p> This field is only returned if the {@link OutputFileOptions} is
         * backed by {@link MediaStore} constructed with
         * {@link OutputFileOptions}.
         *
         * 返回已保存视频文件的 {@link Uri}。
         *
         * <p> 仅当 {@link OutputFileOptions} 由使用 {@link OutputFileOptions} 构造的 {@link MediaStore} 支持时，才会返回此字段。
         */
        @Nullable
        public Uri getSavedUri() {
            return mSavedUri;
        }
    }

    /**
     * Options for saving newly captured video.
     *
     * <p> this class is used to configure save location and other options. Save location can be
     * either a {@link File}, {@link MediaStore}. The metadata will be
     * stored with the saved video.
     *
     * 保存新采集视频的选项。
     *
     * <p> 此类用于配置保存位置及其他选项。保存位置可以是 {@link File} 或 {@link MediaStore}。元数据将与保存的视频一起存储。
     */
    public static final class OutputFileOptions {

        @Nullable
        private final File mFile;
        @Nullable
        private final FileDescriptor mFileDescriptor;
        @Nullable
        private final ContentResolver mContentResolver;
        @Nullable
        private final Uri mSaveCollection;
        @Nullable
        private final ContentValues mContentValues;

        OutputFileOptions(@Nullable File file,
                          @Nullable FileDescriptor fileDescriptor,
                          @Nullable ContentResolver contentResolver,
                          @Nullable Uri saveCollection,
                          @Nullable ContentValues contentValues) {
            mFile = file;
            mFileDescriptor = fileDescriptor;
            mContentResolver = contentResolver;
            mSaveCollection = saveCollection;
            mContentValues = contentValues;
        }

        /**
         * Returns the File object which is set by the {@link Builder}.
         *
         * 返回由 {@link Builder} 设置的 File 对象。
         */
        @Nullable
        File getFile() {
            return mFile;
        }

        /**
         * Returns the FileDescriptor object which is set by the {@link Builder}.
         *
         * 返回由 {@link Builder} 设置的 FileDescriptor 对象。
         */
        @Nullable
        FileDescriptor getFileDescriptor() {
            return mFileDescriptor;
        }

        /**
         * Returns the content resolver which is set by the {@link Builder}.
         *
         * 返回由 {@link Builder} 设置的 content resolver。
         */
        @Nullable
        ContentResolver getContentResolver() {
            return mContentResolver;
        }

        /**
         * Returns the URI which is set by the {@link Builder}.
         *
         * 返回由 {@link Builder} 设置的 URI。
         */
        @Nullable
        Uri getSaveCollection() {
            return mSaveCollection;
        }

        /**
         * Returns the content values which is set by the {@link Builder}.
         *
         * 返回由 {@link Builder} 设置的 content values。
         */
        @Nullable
        ContentValues getContentValues() {
            return mContentValues;
        }

        /**
         * Checking the caller wants to save video to MediaStore.
         *
         * 检查调用方是否希望将视频保存到 MediaStore。
         */
        boolean isSavingToMediaStore() {
            return getSaveCollection() != null && getContentResolver() != null
                    && getContentValues() != null;
        }

        /**
         * Checking the caller wants to save video to a File.
         *
         * 检查调用方是否希望将视频保存到 File。
         */
        boolean isSavingToFile() {
            return getFile() != null;
        }

        /**
         * Checking the caller wants to save video to a FileDescriptor.
         *
         * 检查调用方是否希望将视频保存到 FileDescriptor。
         */
        boolean isSavingToFileDescriptor() {
            return getFileDescriptor() != null;
        }

        /**
         * Builder class for {@link OutputFileOptions}.
         *
         * {@link OutputFileOptions} 的 Builder 类。
         */
        public static final class Builder {
            @Nullable
            private File mFile;
            @Nullable
            private FileDescriptor mFileDescriptor;
            @Nullable
            private ContentResolver mContentResolver;
            @Nullable
            private Uri mSaveCollection;
            @Nullable
            private ContentValues mContentValues;

            /**
             * Creates options to write captured video to a {@link File}.
             *
             * 创建用于将采集的视频写入 {@link File} 的选项。
             *
             * @param file save location of the video. / 视频的保存位置
             */
            public Builder(@NonNull File file) {
                mFile = file;
            }

            /**
             * Creates options to write captured video to a {@link FileDescriptor}.
             *
             * <p>Using a FileDescriptor to record a video is only supported for Android 8.0 or
             * above.
             *
             * 创建用于将采集的视频写入 {@link FileDescriptor} 的选项。
             *
             * <p>使用 FileDescriptor 录制视频仅支持 Android 8.0 及以上版本。
             *
             * @param fileDescriptor to save the video. / 用于保存视频
             */
            public Builder(@NonNull FileDescriptor fileDescriptor) {
                mFileDescriptor = fileDescriptor;
            }

            /**
             * Creates options to write captured video to {@link MediaStore}.
             *
             * 创建用于将采集的视频写入 {@link MediaStore} 的选项。
             * <p>
             * Example: / 示例：
             *
             * <pre>{@code
             *
             * ContentValues contentValues = new ContentValues();
             * contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO");
             * contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
             *
             * OutputFileOptions options = new OutputFileOptions.Builder(
             *         getContentResolver(),
             *         MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
             *         contentValues).build();
             *
             * }</pre>
             *
             * @param contentResolver to access {@link MediaStore} / 用于访问 {@link MediaStore}
             * @param saveCollection  The URL of the table to insert into. / 要插入的表的 URL。
             * @param contentValues   to be included in the created video file. / 要包含在所创建视频文件中的内容值。
             */
            public Builder(@NonNull ContentResolver contentResolver,
                           @NonNull Uri saveCollection,
                           @NonNull ContentValues contentValues) {
                mContentResolver = contentResolver;
                mSaveCollection = saveCollection;
                mContentValues = contentValues;
            }

            /**
             * Builds {@link OutputFileOptions}.
             *
             * 构建 {@link OutputFileOptions}。
             */
            @NonNull
            public OutputFileOptions build() {
                return new OutputFileOptions(mFile, mFileDescriptor, mContentResolver,
                        mSaveCollection, mContentValues);
            }
        }
    }
}
