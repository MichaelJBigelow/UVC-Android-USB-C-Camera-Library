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

package com.serenegiant.usb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.uvccamera.BuildConfig;

public class UVCCamera {
    private static final boolean DEBUG = BuildConfig.DEBUG;    // TODO set false when releasing / 发布时设为 false
    private static final String TAG = UVCCamera.class.getSimpleName();

    // uvc_vs_desc_subtype from libuvc.h
    // 来自 libuvc.h 的 uvc_vs_desc_subtype
    /**
     * Resource busy
     *
     * 资源忙
     */
    public static final int UVC_ERROR_BUSY = -6;

    public static final int UVC_VS_FORMAT_UNCOMPRESSED = 0x04;
    public static final int UVC_VS_FRAME_UNCOMPRESSED = 0x05;
    public static final int UVC_VS_FORMAT_MJPEG = 0x06;
    public static final int UVC_VS_FRAME_MJPEG = 0x07;

    public static final int DEFAULT_PREVIEW_WIDTH = 640;
    public static final int DEFAULT_PREVIEW_HEIGHT = 480;
    public static final int DEFAULT_PREVIEW_FRAME_FORMAT = UVC_VS_FRAME_MJPEG;
    public static final int DEFAULT_PREVIEW_FPS = 30;

    public static final int FRAME_FORMAT_YUYV = 0;
    public static final int FRAME_FORMAT_MJPEG = 1;

    public static final int PIXEL_FORMAT_RAW = 0;
    public static final int PIXEL_FORMAT_YUV = 1;
    public static final int PIXEL_FORMAT_NV12 = 2;        // one format of YUV420SemiPlanar / YUV420SemiPlanar 的一种格式
    public static final int PIXEL_FORMAT_NV21 = 3;        // one format of YUV420SemiPlanar / YUV420SemiPlanar 的一种格式
    public static final int PIXEL_FORMAT_RGB = 4;
    public static final int PIXEL_FORMAT_RGB565 = 5;
    public static final int PIXEL_FORMAT_RGBX = 6;
    public static final int PIXEL_FORMAT_BGR = 7;

    /**
     * This quirk makes the assumption that the device calculated bandwidth is wrong
     * and instead the library calculates its own value based off the frame size, frame rate and bits per pixel.
     *
     * 该 quirk 假定设备计算出的带宽有误,
     * 转而由库根据帧大小、帧率和每像素位数自行计算该值。
     */
    public static final int UVC_QUIRK_FIX_BANDWIDTH = 0x00000080;


    /**
     * libuvc quirks recommended for this device's USB host. MediaTek platforms
     * often need {@link #UVC_QUIRK_FIX_BANDWIDTH} for isochronous uncompressed
     * video so that {@code dwMaxPayloadTransferSize} is recalculated before commit.
     *
     * 针对此设备 USB 主机推荐使用的 libuvc quirk。MediaTek 平台
     * 在处理等时传输的未压缩视频时,通常需要 {@link #UVC_QUIRK_FIX_BANDWIDTH},
     * 以便在提交前重新计算 {@code dwMaxPayloadTransferSize}。
     */
    public static int getRecommendedPlatformQuirks() {
        final String hw = Build.HARDWARE != null ? Build.HARDWARE.toLowerCase(Locale.US) : "";
        if (hw.startsWith("mt") || hw.contains("mediatek")) {
            return UVC_QUIRK_FIX_BANDWIDTH;
        }
        return 0;
    }


    static {
        System.loadLibrary("jpeg-turbo212");
        System.loadLibrary("usb1.0");
        System.loadLibrary("uvc");
        System.loadLibrary("UVCCamera");
    }

    private UsbControlBlock mCtrlBlock;
    private UVCControl mControl = null;

    // these fields from here are accessed from native code and do not change name and remove
    // 从这里开始的这些字段会被原生代码访问,请勿更改名称或删除
    protected long mNativePtr;
    protected String mSupportedFormats;
    protected List<Format> mSupportedFormatList;
    protected List<Size> mSupportedSizeList;
    protected Size mCurrentSize;
    protected UVCParam mParam;
    // until here
    // 到此为止

    /**
     * the constructor of this class should be call within the thread that has a looper
     * (UI thread or a thread that called Looper.prepare)
     *
     * 此类的构造函数应在拥有 looper 的线程中调用
     * (UI 线程或调用过 Looper.prepare 的线程)
     */
    public UVCCamera(UVCParam param) {
        mNativePtr = nativeCreate();
        mParam = param != null ? (UVCParam) param.clone() : new UVCParam();
    }

    /**
     * connect to a UVC camera
     * USB permission is necessary before this method is called
     *
     * 连接到 UVC 相机
     * 调用此方法之前需要获得 USB 权限
     *
     * @param ctrlBlock
     */
    public synchronized int open(final UsbControlBlock ctrlBlock) {
        int result;
        try {
            mCtrlBlock = ctrlBlock.clone();
            mCtrlBlock.open();
            result = nativeConnect(mNativePtr, mCtrlBlock.getFileDescriptor(), mParam.getQuirks());
        } catch (final Exception e) {
            Log.w(TAG, e);
            result = -1;
        }
        if (result != 0) {
//            throw new UnsupportedOperationException("open failed:result=" + result);
            return result;
        }

        updateSupportedFormats();

        Size size = mParam.getPreviewSize();
        if (size == null || !checkSizeValid(size.width, size.height, size.type, size.fps)) {
            size = getSupportedSizeOne();
            if (size == null) {
                size = new Size(
                        DEFAULT_PREVIEW_FRAME_FORMAT,
                        DEFAULT_PREVIEW_WIDTH,
                        DEFAULT_PREVIEW_HEIGHT,
                        DEFAULT_PREVIEW_FPS,
                        new ArrayList<>(DEFAULT_PREVIEW_FPS));
            }
        }

        int r = nativeSetPreviewSize(mNativePtr, size.width, size.height, size.type, size.fps);
        if (DEBUG) Log.d(TAG, "setPreviewSize:" + r + ":" + size);

        mCurrentSize = size;

        mControl = new UVCControl(nativeGetControl(mNativePtr));

        return result;
    }

    /**
     * set status callback
     *
     * 设置状态回调
     *
     * @param callback
     */
    public void setQuirks(final IStatusCallback callback) {
        if (mNativePtr != 0) {
            nativeSetStatusCallback(mNativePtr, callback);
        }
    }

    /**
     * set status callback
     *
     * 设置状态回调
     *
     * @param callback
     */
    public void setStatusCallback(final IStatusCallback callback) {
        if (mNativePtr != 0) {
            nativeSetStatusCallback(mNativePtr, callback);
        }
    }

    /**
     * set button callback
     *
     * 设置按钮回调
     *
     * @param callback
     */
    public void setButtonCallback(final IButtonCallback callback) {
        if (mNativePtr != 0) {
            nativeSetButtonCallback(mNativePtr, callback);
        }
    }

    /**
     * close and release UVC camera
     *
     * 关闭并释放 UVC 相机
     */
    public synchronized void close() {
        close(false);
    }

    /**
     * close and release UVC camera
     *
     * 关闭并释放 UVC 相机
     */
    public synchronized void close(boolean isSilent) {
        if (DEBUG) Log.v(TAG, "close");
        stopPreview();
        if (mNativePtr != 0) {
            nativeRelease(mNativePtr);
//    		mNativePtr = 0;	// Do not clear here because nativeDestroy is called / 因为会调用 nativeDestroy,所以不能在这里清除
        }
        if (mCtrlBlock != null) {
            mCtrlBlock.close(isSilent);
            mCtrlBlock = null;
        }

        mSupportedFormats = null;
        mSupportedFormatList = null;
        mSupportedSizeList = null;
        mCurrentSize = null;

        if (mControl != null) {
            mControl.release();
            mControl = null;
        }
    }

    public UsbDevice getDevice() {
        return mCtrlBlock != null ? mCtrlBlock.getDevice() : null;
    }

    public String getDeviceName() {
        return mCtrlBlock != null ? mCtrlBlock.getDeviceName() : null;
    }

    public UsbControlBlock getUsbControlBlock() {
        return mCtrlBlock;
    }

    private void updateSupportedFormats() {
        if (mNativePtr != 0) {
            mSupportedFormats = nativeGetSupportedFormats(mNativePtr);
            mSupportedFormatList = parseSupportedFormats(mSupportedFormats);
            mSupportedSizeList = fetchSupportedSizeList(mSupportedFormatList);
        }
    }

    public synchronized String getSupportedSize() {
        if (TextUtils.isEmpty(mSupportedFormats)) {
            updateSupportedFormats();
        }
        return mSupportedFormats;
    }

    public List<Format> getSupportedFormatList() {
        List<Format> list = new ArrayList<>();
        if (mSupportedFormatList != null) {
            for (Format format :
                    mSupportedFormatList) {
                list.add(format.clone());
            }
        }
        return list;
    }

    public List<Size> getSupportedSizeList() {
        List<Size> list = new ArrayList<>();
        if (mSupportedSizeList != null) {
            for (Size size :
                    mSupportedSizeList) {
                list.add(size.clone());
            }
        }
        return list;
    }

    /**
     * get supported size that has max pixel and match default frame format
     *
     * 获取像素数最大且与默认帧格式匹配的受支持尺寸
     *
     * @return
     */
    public Size getSupportedSizeOne() {
        Size maxSize = null;
        List<Size> sizeList = getSupportedSizeList();
        if (sizeList != null && sizeList.size() > 0) {
            Collections.sort(sizeList, (o1, o2) -> {
                return o2.width * o2.height - o1.width * o1.height;
            });
            for (Size size : sizeList) {
                if (size.type == DEFAULT_PREVIEW_FRAME_FORMAT) {
                    maxSize = size;
                    break;
                }
            }
            if (maxSize == null) {
                maxSize = sizeList.get(0);
            }
//            return sizeList.get(sizeList.size() - 1);
//            return sizeList.get(0);
        }
        return maxSize;
    }

    private List<Format> parseSupportedFormats(final String supportedFormats) {
        List<Format> formatList = new ArrayList<>();
        if (!TextUtils.isEmpty(supportedFormats)) {
            try {
                final JSONObject json = new JSONObject(supportedFormats);
                final JSONArray formatsJSON = json.getJSONArray("formats");
                for (int i = 0; i < formatsJSON.length(); i++) {
                    final JSONObject formatJSON = formatsJSON.getJSONObject(i);
                    if (formatJSON.has("subType") && formatJSON.has("frameDescriptors")) {
                        final int index = formatJSON.getInt("index");
                        final int formatType = formatJSON.getInt("subType");
                        // this uvc library support for mjpeg and uncompressed format
                        // 此 uvc 库支持 mjpeg 和未压缩格式
                        if (formatType == UVC_VS_FORMAT_MJPEG
                                || formatType == UVC_VS_FORMAT_UNCOMPRESSED) {
                            List<Format.Descriptor> descriptorList = parseFrameDescriptors(formatJSON, index);
                            formatList.add(new Format(index, formatType, descriptorList));
                        }
                    }
                }
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
        return formatList;
    }

    private List<Format.Descriptor> parseFrameDescriptors(final JSONObject format, final int index) throws JSONException {
        List<Format.Descriptor> descriptorList = new ArrayList<>();
        final JSONArray frameDescs = format.getJSONArray("frameDescriptors");
        for (int j = 0; j < frameDescs.length(); j++) {
            JSONObject frameDesc = frameDescs.getJSONObject(j);
            int width = frameDesc.getInt("width");
            int height = frameDesc.getInt("height");
            int formatType = frameDesc.getInt("subType");
            int defaultFps = frameDesc.getInt("defaultFps");
            int defaultFrameInterval = frameDesc.getInt("defaultFrameInterval");
            List<Format.Interval> intervals = new ArrayList<>();
            JSONArray intervalJSONArray = frameDesc.getJSONArray("intervals");

            if (intervalJSONArray.length() > 0) {
                int maxFrameInterval = 0;
                int maxFps = 0;
                for (int k = 0; k < intervalJSONArray.length(); k++) {
                    JSONObject jsonObject = intervalJSONArray.getJSONObject(k);

                    Format.Interval interval = new Format.Interval(
                            jsonObject.getInt("index"),
                            jsonObject.getInt("value"),
                            jsonObject.getInt("fps"));
                    intervals.add(interval);

                    if (maxFps < interval.fps) {
                        maxFrameInterval = interval.value;
                        maxFps = interval.fps;
                    }
                }
                if (maxFps > 0) {
                    defaultFrameInterval = maxFrameInterval;
                    defaultFps = maxFps;
                }
            }

            try {
                descriptorList.add(new Format.Descriptor(index, formatType, width, height, defaultFps, defaultFrameInterval, intervals));
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        return descriptorList;
    }

    private List<Size> fetchSupportedSizeList(List<Format> formatList) {
        List<Size> sizeList = new ArrayList<>();
        for (Format format : formatList) {
            for (Format.Descriptor descriptor : format.frameDescriptors) {
                List<Integer> fpsList = new ArrayList<>();
                for (Format.Interval interval : descriptor.intervals) {
                    fpsList.add(interval.fps);
                }
                sizeList.add(new Size(descriptor.type, descriptor.width, descriptor.height, descriptor.fps, fpsList));
            }
        }
        return sizeList;
    }

    /**
     * Check for valid size
     *
     * 检查尺寸是否有效
     *
     * @param width
     * @param height
     * @param frameType
     * @param fps
     * @return
     */
    private boolean checkSizeValid(final int width, final int height, final int frameType, final int fps) {
        if (mNativePtr != 0) {
            List<Size> sizeList = getSupportedSizeList();
            for (Size size : sizeList) {
                if (size.width == width && size.height == height
                        && size.type == frameType
                        && (size.fps == fps || size.fpsList.contains(fps))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Size getPreviewSize() {
        return mCurrentSize;
    }

    /**
     * Set preview size and preview mode
     *
     * 设置预览尺寸和预览模式
     *
     * @param width
     * @param height
     */
    public void setPreviewSize(final int width, final int height) {
        setPreviewSize(width, height, DEFAULT_PREVIEW_FRAME_FORMAT, DEFAULT_PREVIEW_FPS);
    }

    /**
     * Set preview size and preview mode
     *
     * 设置预览尺寸和预览模式
     *
     * @param width
     * @param height
     * @param frameType either UVC_VS_FRAME_UNCOMPRESSED or UVC_VS_FRAME_MJPEG / UVC_VS_FRAME_UNCOMPRESSED 或 UVC_VS_FRAME_MJPEG 之一
     */
    public void setPreviewSize(final int width, final int height, final int frameType) {
        setPreviewSize(width, height, frameType, DEFAULT_PREVIEW_FPS);
    }

    /**
     * Set preview size and preview mode
     *
     * 设置预览尺寸和预览模式
     *
     * @param width
     * @param height
     * @param frameType either UVC_VS_FRAME_UNCOMPRESSED or UVC_VS_FRAME_MJPEG / UVC_VS_FRAME_UNCOMPRESSED 或 UVC_VS_FRAME_MJPEG 之一
     * @param fps
     */
    public void setPreviewSize(final int width, final int height, final int frameType, final int fps) {
        setPreviewSize(new Size(frameType, width, height, fps, new ArrayList<>(fps)));
    }

    /**
     * Set preview size and preview mode
     *
     * 设置预览尺寸和预览模式
     *
     * @param size
     */
    public void setPreviewSize(Size size) {
        if ((size.width == 0) || (size.height == 0)) {
            throw new IllegalArgumentException("invalid preview size");
        }

        if (mNativePtr != 0) {
            if (!checkSizeValid(size.width, size.height, size.type, size.fps)) {
                throw new IllegalArgumentException("invalid preview size");
            }

            //set preview size
            //设置预览尺寸
            int result = nativeSetPreviewSize(mNativePtr, size.width, size.height, size.type, size.fps);
            if (result != 0) {
                // Because _uvc_stream_params_negotiated method use dwMaxPayloadTransferSize to confirm result of format negotiation.
                // If frameType is changed, result of nativeSetPreviewSize will be UVC_ERROR_INVALID_MODE, we need try again
                // 由于 _uvc_stream_params_negotiated 方法使用 dwMaxPayloadTransferSize 来确认格式协商的结果。
                // 如果 frameType 发生了变化,nativeSetPreviewSize 的结果将是 UVC_ERROR_INVALID_MODE,我们需要重试
                result = nativeSetPreviewSize(mNativePtr, size.width, size.height, size.type, size.fps);
            }

            if (result != 0) {
                throw new IllegalArgumentException("Failed to set preview size");
            }

            mCurrentSize = size;
        }
    }

    /**
     * set preview surface with SurfaceHolder</br>
     * you can use SurfaceHolder came from SurfaceView/GLSurfaceView
     *
     * 使用 SurfaceHolder 设置预览 surface</br>
     * 可以使用来自 SurfaceView/GLSurfaceView 的 SurfaceHolder
     *
     * @param holder
     */
    public synchronized void setPreviewDisplay(final SurfaceHolder holder) {
        nativeSetPreviewDisplay(mNativePtr, holder.getSurface());
    }

    /**
     * set preview surface with SurfaceTexture.
     * this method require API >= 14
     *
     * 使用 SurfaceTexture 设置预览 surface。
     * 此方法要求 API >= 14
     *
     * @param texture
     */
    public synchronized void setPreviewTexture(final SurfaceTexture texture) {    // API >= 11 / API 级别 >= 11
        final Surface surface = new Surface(texture);    // XXX API >= 14 / XXX API 级别 >= 14
        nativeSetPreviewDisplay(mNativePtr, surface);
    }

    /**
     * set preview surface with Surface
     *
     * 使用 Surface 设置预览 surface
     *
     * @param surface
     */
    public synchronized void setPreviewDisplay(final Surface surface) {
        nativeSetPreviewDisplay(mNativePtr, surface);
    }

    /**
     * set frame callback
     *
     * 设置帧回调
     *
     * @param callback    The callback that receive frame data in pixelFormat / 以 pixelFormat 格式接收帧数据的回调
     * @param pixelFormat The frame format of callback. / 回调的帧格式。
     *                    Can be {@link #PIXEL_FORMAT_RAW}, {@link #PIXEL_FORMAT_YUV}, {@link #PIXEL_FORMAT_NV12}, {@link #PIXEL_FORMAT_NV21}, {@link #PIXEL_FORMAT_RGB}, {@link #PIXEL_FORMAT_RGB565}, {@link #PIXEL_FORMAT_BGR}. / 可以是 PIXEL_FORMAT_RAW、PIXEL_FORMAT_YUV、PIXEL_FORMAT_NV12、PIXEL_FORMAT_NV21、PIXEL_FORMAT_RGB、PIXEL_FORMAT_RGB565、PIXEL_FORMAT_BGR 之一
     */
    public void setFrameCallback(final IFrameCallback callback, final int pixelFormat) {
        if (mNativePtr != 0) {
            nativeSetFrameCallback(mNativePtr, callback, pixelFormat);
        }
    }

    /**
     * start preview
     *
     * 开始预览
     */
    public synchronized void startPreview() {
        if (mCtrlBlock != null) {
            nativeStartPreview(mNativePtr);
        }
    }

    /**
     * stop preview
     *
     * 停止预览
     */
    public synchronized void stopPreview() {
//        setFrameCallback(null, 0);
        if (mCtrlBlock != null) {
            nativeStopPreview(mNativePtr);
        }
    }

    /**
     * start movie capturing(this should call while previewing)
     *
     * 开始录制视频(应在预览期间调用)
     *
     * @param surface
     */
    public void startCapture(final Surface surface) {
        if (mCtrlBlock != null && surface != null) {
            nativeSetCaptureDisplay(mNativePtr, surface);
        } else {
            throw new NullPointerException("startCapture");
        }
    }

    /**
     * stop movie capturing
     *
     * 停止录制视频
     */
    public void stopCapture() {
        if (mCtrlBlock != null) {
            nativeSetCaptureDisplay(mNativePtr, null);
        }
    }

    /**
     * destroy UVCCamera object
     *
     * 销毁 UVCCamera 对象
     */
    public synchronized void destroy() {
        destroy(false);
    }

    /**
     * destroy UVCCamera object
     *
     * 销毁 UVCCamera 对象
     */
    public synchronized void destroy(boolean isSilent) {
        close(isSilent);
        if (mNativePtr != 0) {
            nativeDestroy(mNativePtr);
            mNativePtr = 0;
        }
    }

    /**
     * Returns true if UVCCamera is opened.
     *
     * 如果 UVCCamera 已打开则返回 true。
     */
    public UVCControl getControl() {
        return mControl;
    }

    public boolean isOpened() {
        return mControl != null;
    }

    private native long nativeCreate();

    private native void nativeDestroy(final long id_camera);

    private native int nativeConnect(long id_camera, int fileDescriptor, int quirks);

    private native int nativeRelease(final long id_camera);

    private native long nativeGetControl(final long id_camera);

    private native int nativeSetStatusCallback(final long id_camera, final IStatusCallback callback);

    private native int nativeSetButtonCallback(final long id_camera, final IButtonCallback callback);

    private native int nativeSetPreviewSize(final long id_camera, final int width, final int height, final int frameType, final int fps);

    private native String nativeGetSupportedFormats(final long id_camera);

    private native int nativeStartPreview(final long id_camera);

    private native int nativeStopPreview(final long id_camera);

    private native int nativeSetPreviewDisplay(final long id_camera, final Surface surface);

    private native int nativeSetFrameCallback(final long id_camera, final IFrameCallback callback, final int pixelFormat);

    private native int nativeSetCaptureDisplay(final long id_camera, final Surface surface);

}
