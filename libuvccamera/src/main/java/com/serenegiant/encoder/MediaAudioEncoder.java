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
import java.nio.ByteOrder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import com.serenegiant.uvccamera.BuildConfig;

public class MediaAudioEncoder extends MediaEncoder implements IAudioEncoder {
    private static final boolean DEBUG = BuildConfig.DEBUG;    // TODO set false on release / 发布时请设为 false
    private static final String TAG = "MediaAudioEncoder";

    private static final String MIME_TYPE = "audio/mp4a-latm";
    // 44.1[KHz] is only setting guaranteed to be available on all devices.
    // 44.1[KHz] 是唯一保证在所有设备上都可用的设置。
    private static final int SAMPLE_RATE = 44100;
    private static final int BIT_RATE = 64000;
    public static final int SAMPLES_PER_FRAME = 1024;    // AAC, bytes/frame/channel / AAC,每个声道每帧的字节数
    public static final int FRAMES_PER_BUFFER = 25;    // AAC, frame/buffer/sec / AAC,帧/缓冲区/秒

    private AudioThread mAudioThread = null;

    public MediaAudioEncoder(final MediaMuxerWrapper muxer, final MediaEncoderListener listener) {
        super(muxer, listener);
    }

    @Override
    protected void prepare() throws IOException {
        if (DEBUG) Log.v(TAG, "prepare:");
        mTrackIndex = -1;
        mMuxerStarted = false;
        // prepare MediaCodec for AAC encoding of audio data from inernal mic.
        // 准备 MediaCodec,用于对来自内置麦克风的音频数据进行 AAC 编码。
        final MediaCodecInfo audioCodecInfo = selectAudioCodec(MIME_TYPE);
        if (audioCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (DEBUG) Log.i(TAG, "selected codec: " + audioCodecInfo.getName());

        final MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//		audioFormat.setLong(MediaFormat.KEY_MAX_INPUT_SIZE, inputFile.length());
//      audioFormat.setLong(MediaFormat.KEY_DURATION, (long)durationInMs );
        if (DEBUG) Log.i(TAG, "format: " + audioFormat);
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
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

    @Override
    protected void startRecording() {
        super.startRecording();
        // create and execute audio capturing thread using internal mic
        // 创建并运行使用内置麦克风的音频采集线程
        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
            mAudioThread.start();
        }
    }

    @Override
    protected void release() {
        mAudioThread = null;
        super.release();
    }

    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
    };

    /**
     * Thread to capture audio data from internal mic as uncompressed 16bit PCM data
     * and write them to the MediaCodec encoder
     *
     * 从内置麦克风以未压缩的 16 位 PCM 数据采集音频数据
     * 并将其写入 MediaCodec 编码器的线程
     */
    private class AudioThread extends Thread {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO); // THREAD_PRIORITY_URGENT_AUDIO
            int cnt = 0;
            final int minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
            if (bufferSize < minBufferSize) {
                bufferSize = ((minBufferSize / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
            }
            final ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME).order(ByteOrder.nativeOrder());
            AudioRecord audioRecord = null;
            for (final int src : AUDIO_SOURCES) {
                try {
                    audioRecord = new AudioRecord(src,
                            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                    if (audioRecord != null) {
                        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                            audioRecord.release();
                            audioRecord = null;
                        }
                    }
                } catch (final Exception e) {
                    audioRecord = null;
                }
                if (audioRecord != null) {
                    break;
                }
            }
            if (audioRecord != null) {
                try {
                    if (mIsCapturing) {
                        if (DEBUG) Log.v(TAG, "AudioThread:start audio recording");
                        int readBytes;
                        audioRecord.startRecording();
                        try {
                            for (; mIsCapturing; ) {
                                // read audio data from internal mic
                                // 从内置麦克风读取音频数据
                                buf.clear();
                                try {
                                    readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);
                                } catch (final Exception e) {
                                    break;
                                }
                                if (readBytes > 0) {
                                    // set audio data to encoder
                                    // 将音频数据设置给编码器
                                    buf.position(readBytes);
                                    buf.flip();
                                    encode(buf, readBytes, getPTSUs());
                                    frameAvailableSoon();
                                    cnt++;
                                }
                            }
                            if (cnt > 0) {
                                frameAvailableSoon();
                            }
                        } finally {
                            audioRecord.stop();
                        }
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "AudioThread#run", e);
                } finally {
                    audioRecord.release();
                }
            }
            if (cnt == 0) {
                for (int i = 0; mIsCapturing && (i < 5); i++) {
                    buf.position(SAMPLES_PER_FRAME);
                    buf.flip();
                    try {
                        encode(buf, SAMPLES_PER_FRAME, getPTSUs());
                        frameAvailableSoon();
                    } catch (final Exception e) {
                        break;
                    }
                    synchronized (this) {
                        try {
                            wait(50);
                        } catch (final InterruptedException e) {
                        }
                    }
                }
            }
            if (DEBUG) Log.v(TAG, "AudioThread:finished");
        }
    }

    /**
     * select the first codec that match a specific MIME type
     *
     * 选择第一个与指定 MIME 类型匹配的编解码器
     *
     * @param mimeType
     * @return
     */
    private static final MediaCodecInfo selectAudioCodec(final String mimeType) {
        if (DEBUG) Log.v(TAG, "selectAudioCodec:");

        MediaCodecInfo result = null;
        // get the list of available codecs
        // 获取可用编解码器列表
        final int numCodecs = MediaCodecList.getCodecCount();
        LOOP:
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {    // skipp decoder / 跳过解码器
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (DEBUG) Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (result == null) {
                        result = codecInfo;
                        break LOOP;
                    }
                }
            }
        }
        return result;
    }

}
