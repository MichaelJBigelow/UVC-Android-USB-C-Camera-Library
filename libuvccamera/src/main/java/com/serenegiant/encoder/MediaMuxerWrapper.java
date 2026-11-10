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

import java.io.IOException;
import java.nio.ByteBuffer;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;

import com.serenegiant.utils.UVCUtils;
import com.serenegiant.utils.UriHelper;
import com.serenegiant.uvccamera.BuildConfig;


public class MediaMuxerWrapper {
    private static final boolean DEBUG = BuildConfig.DEBUG;    // TODO set false on release / 发布时设为 false
    private static final String TAG = MediaMuxerWrapper.class.getSimpleName();

    private Uri mOutputUri;
    private final MediaMuxer mMediaMuxer;    // API >= 18 / API 级别 >= 18
    private int mEncoderCount, mStartedCount;
    private boolean mIsStarted;
    private MediaEncoder mVideoEncoder, mAudioEncoder;

    /**
     * Constructor
     *
     * 构造函数
     *
     * @param uri uri of output file / 输出文件的 uri
     * @throws IOException
     */
    public MediaMuxerWrapper(Uri uri) throws IOException {
        Context context = UVCUtils.getApplication();
        mOutputUri = uri;

        mMediaMuxer = new MediaMuxer(context.getContentResolver().openFileDescriptor(mOutputUri, "rw").getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mEncoderCount = mStartedCount = 0;
        mIsStarted = false;
    }

    public Uri getOutputUri() {
        return mOutputUri;
    }

    public void prepare() throws IOException {
        if (mVideoEncoder != null)
            mVideoEncoder.prepare();
        if (mAudioEncoder != null)
            mAudioEncoder.prepare();
    }

    public void startRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder.startRecording();
        if (mAudioEncoder != null)
            mAudioEncoder.startRecording();
    }

    public void pauseRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder.pauseRecording();
        if (mAudioEncoder != null)
            mAudioEncoder.pauseRecording();
    }

    public void resumeRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder.resumeRecording();
        if (mAudioEncoder != null)
            mAudioEncoder.resumeRecording();
    }

    public void stopRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder.stopRecording();
        mVideoEncoder = null;
        if (mAudioEncoder != null)
            mAudioEncoder.stopRecording();
        mAudioEncoder = null;
    }

    public synchronized boolean isStarted() {
        return mIsStarted;
    }

//**********************************************************************
//**********************************************************************

    /**
     * assign encoder to this class. this is called from encoder.
     *
     * 将编码器分配给此类。此方法由编码器调用。
     *
     * @param encoder instance of MediaVideoEncoder or MediaAudioEncoder / MediaVideoEncoder 或 MediaAudioEncoder 的实例
     */
    /*package*/ void addEncoder(final MediaEncoder encoder) {
        if (encoder instanceof MediaSurfaceEncoder
                || encoder instanceof MediaVideoEncoder
                || encoder instanceof MediaVideoBufferEncoder) {
            if (mVideoEncoder != null) {
                throw new IllegalArgumentException("Video encoder already added.");
            }
            mVideoEncoder = encoder;
        } else if (encoder instanceof MediaAudioEncoder) {
            if (mAudioEncoder != null) {
                throw new IllegalArgumentException("Video encoder already added.");
            }
            mAudioEncoder = encoder;
        } else {
            throw new IllegalArgumentException("unsupported encoder");
        }
        mEncoderCount = (mVideoEncoder != null ? 1 : 0) + (mAudioEncoder != null ? 1 : 0);
    }

    /**
     * request start recording from encoder
     *
     * 由编码器请求开始录制
     *
     * @return true when muxer is ready to write / muxer 已准备好写入时返回 true
     */
    /*package*/
    synchronized boolean start() {
        if (DEBUG) Log.v(TAG, "start:");
        mStartedCount++;
        if ((mEncoderCount > 0) && (mStartedCount == mEncoderCount)) {
            mMediaMuxer.start();
            mIsStarted = true;
            notifyAll();
            if (DEBUG) Log.v(TAG, "MediaMuxer started:");
        }
        return mIsStarted;
    }

    /**
     * request stop recording from encoder when encoder received EOS
     *
     * 编码器收到 EOS 时,由编码器请求停止录制
     */
    /*package*/
    synchronized void stop() {
        if (DEBUG) Log.v(TAG, "stop:mStartedCount=" + mStartedCount);
        mStartedCount--;
        if ((mEncoderCount > 0) && (mStartedCount <= 0)) {
            try {
                mMediaMuxer.stop();
            } catch (final Exception e) {
                Log.e(TAG, " MediaMuxer stop error", e);
            } finally {
                mMediaMuxer.release();
            }
            mIsStarted = false;
            if (DEBUG) Log.v(TAG, "MediaMuxer stopped:");
        }
    }

    /**
     * assign encoder to muxer
     *
     * 将编码器分配给 muxer
     *
     * @param format
     * @return minus value indicate error / 负值表示出错
     */
    /*package*/
    synchronized int addTrack(final MediaFormat format) {
        if (mIsStarted) {
            throw new IllegalStateException("muxer already started");
        }
        final int trackIx = mMediaMuxer.addTrack(format);
        if (DEBUG)
            Log.i(TAG, "addTrack:trackNum=" + mEncoderCount + ",trackIx=" + trackIx + ",format=" + format);
        return trackIx;
    }

    /**
     * write encoded data to muxer
     *
     * 将编码后的数据写入 muxer
     *
     * @param trackIndex
     * @param byteBuf
     * @param bufferInfo
     */
    /*package*/
    synchronized void writeSampleData(final int trackIndex, final ByteBuffer byteBuf, final MediaCodec.BufferInfo bufferInfo) {
        if (mStartedCount > 0) {
            mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
        }
    }

}
