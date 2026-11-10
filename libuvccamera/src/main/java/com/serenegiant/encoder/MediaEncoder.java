/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.encoder;

import static com.serenegiant.uvccamera.BuildConfig.DEBUG;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.uvccamera.BuildConfig;

public abstract class MediaEncoder extends Thread {
    private static final boolean DEBUG = BuildConfig.DEBUG;    // TODO set false on release / 发布时请设为 false
    private static final String TAG = MediaEncoder.class.getSimpleName();

    //10[milliseconds]
    //10[毫秒]
    protected static final int TIMEOUT_US = 10000;

    public interface MediaEncoderListener {
        void onPrepared(MediaEncoder encoder);

        void onStopped(MediaEncoder encoder);
    }

    /**
     * Flag that indicate this encoder is capturing now.
     *
     * 指示此编码器当前是否正在采集的标志。
     */
    protected volatile boolean mIsCapturing;
    /**
     * Flag the indicate the muxer is running
     *
     * 指示 muxer 是否正在运行的标志
     */
    protected boolean mMuxerStarted;
    /**
     * Flag the indicate the muxer is paused
     *
     * 指示 muxer 是否已暂停的标志
     */
    protected volatile boolean mIsPaused;
    /**
     * The start time of encoding. in nanoseconds
     *
     * 编码开始时间,单位为纳秒
     */
    protected volatile long mStartTime;
    /**
     * The last time of encoder paused. in nanoseconds
     *
     * 编码器最近一次暂停的时间,单位为纳秒
     */
    protected volatile long mLastPauseTime;
    /**
     * The total time of encoder paused duration. in microseconds
     *
     * 编码器累计暂停时长,单位为微秒
     */
    protected volatile long mPauseTotalTime;

    /**
     * Track Number
     *
     * 轨道编号
     */
    protected int mTrackIndex;
    /**
     * MediaCodec instance for encoding
     *
     * 用于编码的 MediaCodec 实例
     */
    protected MediaCodec mMediaCodec;                // API >= 16(Android4.1.2)
    /**
     * Weak refarence of MediaMuxerWarapper instance
     *
     * MediaMuxerWrapper 实例的弱引用
     */
    protected final WeakReference<MediaMuxerWrapper> mWeakMuxer;
    /**
     * BufferInfo instance for dequeuing
     *
     * 用于出队的 BufferInfo 实例
     */
    private MediaCodec.BufferInfo mBufferInfo;        // API >= 16(Android4.1.2)

    protected final MediaEncoderListener mListener;

    /**
     * for a video encoder, create a destination Surface for your input data
     * using createInputSurface() after configuration
     *
     * 对于视频编码器,请在配置完成后
     * 使用 createInputSurface() 为输入数据创建目标 Surface
     */
    protected Surface mSurface;

    public MediaEncoder(final MediaMuxerWrapper muxer, final MediaEncoderListener listener) {
        if (listener == null) throw new NullPointerException("MediaEncoderListener is null");
        if (muxer == null) throw new NullPointerException("MediaMuxerWrapper is null");
        mWeakMuxer = new WeakReference<MediaMuxerWrapper>(muxer);
        muxer.addEncoder(this);
        mListener = listener;
        // create BufferInfo here for effectiveness(to reduce GC)
        // 在此创建 BufferInfo 以提高效率(减少 GC)
        mBufferInfo = new MediaCodec.BufferInfo();

        if (DEBUG) Log.v(TAG, "MediaEncoder:" + this.getClass().getSimpleName());
    }

    public Uri getOutputUri() {
        final MediaMuxerWrapper muxer = mWeakMuxer.get();
        return muxer != null ? muxer.getOutputUri() : null;
    }

    /**
     * Returns the encoder's input surface.
     *
     * 返回编码器的输入 Surface。
     */
    public Surface getInputSurface() {
        return mSurface;
    }

    /**
     * the method to indicate frame data is soon available or already available
     *
     * 用于表示帧数据即将可用或已经可用的方法
     *
     * @return return true if encoder is ready to encode. / 如果编码器已准备好编码则返回 true
     */
    public boolean frameAvailableSoon() {
//        if (DEBUG) Log.v(TAG, "frameAvailableSoon");
        if (!mIsCapturing) {
            return false;
        }
        return true;
    }

    /**
     * encoding loop on private thread
     *
     * 在私有线程上运行的编码循环
     */
    @Override
    public void run() {
        if (DEBUG) Log.v(TAG, "Encoder thread start:" + this.getClass().getCanonicalName());
        drain();
        if (DEBUG) Log.v(TAG, "release:" + this.getClass().getCanonicalName());
        // release all related objects
        // 释放所有相关对象
        release();
        if (DEBUG) Log.d(TAG, "Encoder thread exiting:" + this.getClass().getCanonicalName());
    }

    /*
     * preparing method for each sub class
     * this method should be implemented in sub class, so set this as abstract method
     *
     * 各子类的准备方法
     * 此方法应在子类中实现,因此声明为抽象方法
     * @throws IOException
     */
    /*package*/
    abstract void prepare() throws IOException;

    /*package*/ void startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording");
        mIsCapturing = true;

        mIsPaused = false;

        mStartTime = System.nanoTime();
        mLastPauseTime = 0L;
        mPauseTotalTime = 0L;

        start();
    }

    /*package*/ void pauseRecording() {
        if (DEBUG) Log.v(TAG, "pauseRecording");
        mIsPaused = true;
        mLastPauseTime = System.nanoTime();
        if (DEBUG) Log.v(TAG, "pauseRecording:" + mLastPauseTime);
    }

    /*package*/ void resumeRecording() {
        if (DEBUG) Log.v(TAG, "resumeRecording");
        mIsPaused = false;
        long pauseTime = System.nanoTime() - mLastPauseTime;
        if (pauseTime > 0) {
            mPauseTotalTime += pauseTime / 1000L;
        }
        if (DEBUG) Log.v(TAG, "resumeRecording:" + mPauseTotalTime);
    }

    /**
     * the method to request stop encoding
     *
     * 请求停止编码的方法
     */
    /*package*/ void stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording");
        if (!mIsCapturing) {
            return;
        }
        mIsPaused = false;
        // send EOS for rejecting newer frame
        // 发送 EOS 以拒绝后续的新帧
        signalEndOfInputStream();
        if (DEBUG)
            Log.v(TAG, "stopRecording " + this.getClass().getCanonicalName() + ":" + mIsCapturing);
    }

//********************************************************************************
//********************************************************************************

    /**
     * Release all related objects
     *
     * 释放所有相关对象
     */
    protected void release() {
        if (DEBUG) Log.d(TAG, "release:");
        try {
            mListener.onStopped(this);
        } catch (final Exception e) {
            Log.e(TAG, "failed onStopped", e);
        }
        mIsCapturing = false;
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (final Exception e) {
                Log.e(TAG, "failed releasing MediaCodec", e);
            }
        }
        if (mMuxerStarted) {
            final MediaMuxerWrapper muxer = mWeakMuxer.get();
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (final Exception e) {
                    Log.e(TAG, "failed stopping muxer", e);
                }
            }
            mMuxerStarted = false;
        }
        mBufferInfo = null;

        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    /**
     * When using an input Surface, there are no accessible input buffers,
     * as buffers are automatically passed from the input surface to the codec.
     * Calling dequeueInputBuffer will throw an IllegalStateException,
     * and getInputBuffers() returns a bogus ByteBuffer[] array that MUST NOT be written into.
     *
     * 使用输入 Surface 时,没有可访问的输入缓冲区,
     * 因为缓冲区会自动从输入 Surface 传递给编解码器。
     * 调用 dequeueInputBuffer 会抛出 IllegalStateException,
     * 而 getInputBuffers() 返回的是一个虚假的 ByteBuffer[] 数组,绝对不能向其写入数据。
     */
    protected void signalEndOfInputStream() {
        if (DEBUG) Log.d(TAG, "sending EOS to encoder");
        try {
            if (mSurface != null) {
                // Call signalEndOfInputStream() to signal end-of-stream.
                // The input surface will stop submitting data to the codec immediately after this call
                // 调用 signalEndOfInputStream() 以通知流结束。
                // 此调用之后,输入 Surface 将立即停止向编解码器提交数据
                mMediaCodec.signalEndOfInputStream();    // API >= 18
            } else {
                encode((ByteBuffer) null, 0, getPTSUs());
            }
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "failed to send EOS to encoder", e);
        }
    }

    /**
     * Method to set ByteBuffer to the MediaCodec encoder
     *
     * 向 MediaCodec 编码器设置 ByteBuffer 的方法
     *
     * @param buffer
     * @param length             　length of byte array, zero means EOS. / 字节数组的长度,为零表示 EOS
     * @param presentationTimeUs
     */
    protected void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
//    	if (DEBUG) Log.v(TAG, "encode:buffer=" + buffer);
        if (!mIsCapturing || mIsPaused) return;
        try {
            if (length <= 0) {
                while (mIsCapturing) {
                    final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        if (DEBUG) Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                        // send EOS
                        // 发送 EOS
                        mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                                presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        break;
                    }
                }
            } else {
                int current = 0;
//            final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                while (mIsCapturing && current < length) {
                    final int inputBufferId = mMediaCodec.dequeueInputBuffer(TIMEOUT_US);
                    if (inputBufferId >= 0) {
                        final ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferId);
                        inputBuffer.clear();
                        int inputSize = inputBuffer.capacity();
                        inputSize = (current + inputSize < length) ? inputSize : length - current;
                        if (inputSize > 0 && (buffer != null)) {
                            buffer.position(current);
                            buffer.limit(current + inputSize);
                            inputBuffer.put(buffer);
                        }
                        current += inputSize;

                        mMediaCodec.queueInputBuffer(inputBufferId, 0, inputSize,
                                presentationTimeUs, 0);
                    } else if (inputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // wait for MediaCodec encoder is ready to encode
                        // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_US)
                        // will wait for maximum TIMEOUT_US(10msec) on each call
                        // 等待 MediaCodec 编码器准备好编码
                        // 这里无需处理,因为 MediaCodec#dequeueInputBuffer(TIMEOUT_US)
                        // 每次调用最多会等待 TIMEOUT_US(10 毫秒)
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    /**
     * drain encoded data and write them to muxer
     *
     * 取出编码后的数据并写入 muxer
     */
    protected void drain() {
        if (DEBUG) Log.v(TAG, "drain start:");
        if (mMediaCodec == null) return;
//        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        final MediaMuxerWrapper muxer = mWeakMuxer.get();
        if (muxer == null) {
            Log.w(TAG, "muxer is unexpectedly null");
            return;
        }

        LOOP:
        while (mIsCapturing) {
            try {
                // get encoded data with maximum timeout duration of TIMEOUT_US(=10[msec])
                // 获取编码数据,最长超时时间为 TIMEOUT_US(=10[毫秒])
                int outputBufferId = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
//            Log.d(TAG, "dequeueOutputBuffer:" + outputBufferId);
                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                if (DEBUG) Log.v(TAG, "INFO_TRY_AGAIN_LATER");
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    if (DEBUG) Log.v(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    // this should not come when encoding
                    // 编码期间不应出现此状态
//                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (DEBUG) Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                    // this status indicate the output format of codec is changed
                    // this should come only once before actual encoded data
                    // but this status never come on Android4.3 or less
                    // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
                    // 此状态表示编解码器的输出格式已改变
                    // 它应当只在实际编码数据之前出现一次
                    // 但在 Android 4.3 及以下版本上此状态从不出现
                    // 这种情况下,应在 MediaCodec.BUFFER_FLAG_CODEC_CONFIG 出现时进行处理。
                    if (mMuxerStarted) {    // second time request is error / 第二次请求属于错误
                        throw new RuntimeException("format changed twice");
                    }
                    // get output format from codec and pass them to muxer
                    // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                    // 从编解码器获取输出格式并传给 muxer
                    // getOutputFormat 必须在 INFO_OUTPUT_FORMAT_CHANGED 之后调用,否则会崩溃。
                    final MediaFormat format = mMediaCodec.getOutputFormat(); // API >= 16
                    mTrackIndex = muxer.addTrack(format);
                    mMuxerStarted = true;
                    if (!muxer.start()) {
                        // we should wait until muxer is ready
                        // 应等待 muxer 准备就绪
                        synchronized (muxer) {
                            while (!muxer.isStarted()) {
                                try {
                                    muxer.wait(100);
                                } catch (final InterruptedException e) {
                                    break LOOP;
                                }
                            }
                        }
                    }
                } else if (outputBufferId < 0) {
                    // unexpected status
                    // 意外状态
                    if (DEBUG)
                        Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + outputBufferId);
                } else {
                    final ByteBuffer encodedData = mMediaCodec.getOutputBuffer(outputBufferId);
                    if (encodedData == null) {
                        // this never should come...may be a MediaCodec internal error
                        // 这种情况不应出现...可能是 MediaCodec 内部错误
                        throw new RuntimeException("encoderOutputBuffer " + outputBufferId + " was null");
                    }
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // You should set output format to muxer here when you target Android4.3 or less
                        // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                        // therefore we should expand and prepare output format from buffer data.
                        // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                        // 如果目标为 Android 4.3 及以下版本,应在此处为 muxer 设置输出格式
                        // 但此处无法调用 MediaCodec#getOutputFormat(因为 INFO_OUTPUT_FORMAT_CHANGED 尚未出现)
                        // 因此需要根据缓冲区数据展开并准备输出格式。
                        // 本示例面向 API>=18(>=Android 4.3),此处直接忽略该标志
                        if (DEBUG) Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG");
                    }
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0) {
                        if (DEBUG) Log.d(TAG, "drain:BUFFER_FLAG_SYNC_FRAME");
                    }

                    // encoded data is ready, clear waiting counter
                    // 编码数据已就绪,清除等待计数
                    if (!mMuxerStarted) {
                        // muxer is not ready...this will programing failure.
                        // muxer 尚未就绪...这属于编程错误。
                        throw new RuntimeException("drain:muxer hasn't started");
                    }

                    //adjust presentationTimeUs
                    //调整 presentationTimeUs
                    mBufferInfo.presentationTimeUs = getPTSUs();
                    if (DEBUG) Log.v(TAG, this +":presentationTimeUs:" +  mBufferInfo.presentationTimeUs);

//                if (DEBUG) Log.v(TAG, "drain presentationTimeUs:" + mBufferInfo.presentationTimeUs);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        // when EOS come.
                        // 收到 EOS 时。
                        mIsCapturing = false;
                    } else {
                        //write encoded data to muxer
                        //将编码数据写入 muxer
                        muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    }
                    // return buffer to encoder
                    // 将缓冲区归还给编码器
                    mMediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                break;
            }
        }
        if (DEBUG) Log.v(TAG, "drain end:");
    }

    /**
     * previous presentationTimeUs for writing
     *
     * 上一次写入使用的 presentationTimeUs
     */
    long prevPTS = 0;

    /**
     * get next encoding presentationTimeUs of input data (micro second)
     *
     * 获取输入数据下一次编码的 presentationTimeUs(微秒)
     *
     * @return
     */
    protected long getPTSUs() {
        long currentTime = System.nanoTime();
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        // presentationTimeUs 应当单调递增
        // 否则 muxer 写入会失败
        long pts = (currentTime - mStartTime - mPauseTotalTime) / 1000L;
        if (pts < prevPTS) {
            pts = prevPTS;
        }
        return pts;
    }

}
