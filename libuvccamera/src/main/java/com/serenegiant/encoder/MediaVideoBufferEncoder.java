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

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.serenegiant.uvccamera.BuildConfig;

/**
 * This class receives video images as ByteBuffer(strongly recommend direct ByteBuffer) as NV21(YUV420SP)
 * and encode them to h.264.
 * If you use this directly with IFrameCallback, you should know UVCCamera and it backend native libraries
 * never execute color space conversion. This means that color tone of resulted movie will be different
 * from that you expected/can see on screen.
 *
 * 此类接收 ByteBuffer(强烈建议使用 direct ByteBuffer)形式的 NV21(YUV420SP) 视频图像,
 * 并将其编码为 h.264。
 * 如果直接将其与 IFrameCallback 配合使用,您应当了解 UVCCamera 及其后端原生库
 * 从不执行色彩空间转换。这意味着生成的视频的色调将与
 * 您预期的/在屏幕上看到的不同。
 */
public class MediaVideoBufferEncoder extends MediaEncoder implements IVideoEncoder {
    private static final boolean DEBUG = BuildConfig.DEBUG;    // TODO set false on release / 发布时设为 false
    private static final String TAG = "MediaVideoBufferEncoder";

    private static final String MIME_TYPE = "video/avc";
    // parameters for recording
    // 录制参数
    private static final int FRAME_RATE = 25;
    private static final int I_FRAME_INTERVAL = 10;
    private static final float BPP = 0.25f;

    private final int mWidth, mHeight;
    protected int mColorFormat;

    public MediaVideoBufferEncoder(final MediaMuxerWrapper muxer, final int width, final int height, final MediaEncoderListener listener) {
        super(muxer, listener);
        if (DEBUG) Log.i(TAG, "MediaVideoEncoder: ");
        mWidth = width;
        mHeight = height;
    }

    public void encode(final ByteBuffer buffer) {
//    	if (DEBUG) Log.v(TAG, "encode:buffer=" + buffer);
        if (!mIsCapturing) return;
        encode(buffer, buffer.capacity(), getPTSUs());
    }

    @Override
    protected void prepare() throws IOException {
        if (DEBUG) Log.i(TAG, "prepare: ");
        mTrackIndex = -1;
        mMuxerStarted = false;

        final MediaCodecInfo videoCodecInfo = selectVideoCodec(MIME_TYPE);
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (DEBUG) Log.i(TAG, "selected codec: " + videoCodecInfo.getName());

        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        if (DEBUG) Log.i(TAG, "format: " + format);

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        if (DEBUG) Log.i(TAG, "prepare finishing");
        if (mListener != null) {
            try {
                mListener.onPrepared(this);
            } catch (final Exception e) {
                Log.e(TAG, "prepare:", e);
            }
        }
    }

    private int calcBitRate() {
        final int bitrate = (int) (BPP * FRAME_RATE * mWidth * mHeight);
        Log.i(TAG, String.format("bitrate=%5.2f[Mbps]", bitrate / 1024f / 1024f));
        return bitrate;
    }

    /**
     * select the first codec that match a specific MIME type
     *
     * 选择第一个与指定 MIME 类型匹配的编解码器
     *
     * @param mimeType
     * @return null if no codec matched / 没有匹配的编解码器时返回 null
     */
    @SuppressWarnings("deprecation")
    protected final MediaCodecInfo selectVideoCodec(final String mimeType) {
        if (DEBUG) Log.v(TAG, "selectVideoCodec:");

        // get the list of available codecs
        // 获取可用编解码器的列表
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {    // skipp decoder / 跳过解码器
                continue;
            }
            // select first codec that match a specific MIME type and color format
            // 选择第一个与指定 MIME 类型和颜色格式匹配的编解码器
            final String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    if (DEBUG) Log.i(TAG, "codec:" + codecInfo.getName() + ",MIME=" + type);
                    final int format = selectColorFormat(codecInfo, mimeType);
                    if (format > 0) {
                        mColorFormat = format;
                        return codecInfo;
                    }
                }
            }
        }
        return null;
    }

    /**
     * select color format available on specific codec and we can use.
     *
     * 选择指定编解码器上可用且我们可以使用的颜色格式。
     *
     * @return 0 if no colorFormat is matched / 没有匹配的 colorFormat 时返回 0
     */
    protected static int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
        if (DEBUG) Log.i(TAG, "selectColorFormat: ");
        int result = 0;
        final MediaCodecInfo.CodecCapabilities caps;
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            caps = codecInfo.getCapabilitiesForType(mimeType);
        } finally {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
        int colorFormat;
        for (int i = 0; i < caps.colorFormats.length; i++) {
            colorFormat = caps.colorFormats[i];
            if (isRecognizedViewFormat(colorFormat)) {
                result = colorFormat;
                break;
            }
        }
        if (result == 0) {
            Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        }
        return result;
    }

    /**
     * color formats that we can use in this class
     *
     * 此类中可以使用的颜色格式
     */
    protected static int[] recognizedFormats;

    static {
        recognizedFormats = new int[]{
//        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
        };
    }

    private static final boolean isRecognizedViewFormat(final int colorFormat) {
        if (DEBUG) Log.i(TAG, "isRecognizedViewFormat:colorFormat=" + colorFormat);
        final int n = recognizedFormats != null ? recognizedFormats.length : 0;
        for (int i = 0; i < n; i++) {
            if (recognizedFormats[i] == colorFormat) {
                return true;
            }
        }
        return false;
    }

}
