package com.herohan.uvcapp;

import android.content.Context;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.opengl.renderer.RendererHolderCallback;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCControl;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.utils.UVCUtils;
import com.serenegiant.uvccamera.BuildConfig;
import com.serenegiant.uvccamera.R;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class CameraInternal implements ICameraInternal {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = CameraInternal.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private int mFrameWidth = DEFAULT_WIDTH;
    private int mFrameHeight = DEFAULT_HEIGHT;

    /**
     * Guards the native camera of this instance only, so cameras don't wait for each other.
     *
     * 仅保护本实例的 native 相机，使不同相机之间无需互相等待。
     */
    private final Object mSync = new Object();

    private final UsbControlBlock mCtrlBlock;

    private ICameraRendererHolder mRendererHolder;
    private volatile boolean mIsPreviewing = false;

    /**
     * for accessing UVC camera
     *
     * 用于访问 UVC 相机
     */
    private volatile UVCCamera mUVCCamera;

    // Registered and called on different threads / 在不同线程上注册和调用
    private final List<StateCallback> mCallbacks = new CopyOnWriteArrayList<>();

    private volatile IImageCapture mImageCapture;
    private volatile VideoCapture mVideoCapture;

    public CameraInternal(final UsbControlBlock ctrlBlock, final int vid, final int pid) {
        if (DEBUG) Log.d(TAG, "Constructor:");
        mCtrlBlock = ctrlBlock;

        mRendererHolder = new CameraRendererHolder(mFrameWidth, mFrameHeight, new RendererHolderCallback() {
            @Override
            public void onPrimarySurfaceCreate(Surface surface) {
                // After primary surface has been created during previewing, invoking startPreview method again.
                // 预览过程中主 surface 创建完成后，再次调用 startPreview 方法。
                if (mIsPreviewing) {
                    startPreview();
                }
            }

            @Override
            public void onFrameAvailable() {

            }

            @Override
            public void onPrimarySurfaceDestroy() {

            }
        });
    }

    @Override
    public void registerCallback(final StateCallback callback) {
        if (DEBUG) Log.d(TAG, "registerCallback:");
        mCallbacks.add(callback);
    }

    @Override
    public void unregisterCallback(final StateCallback callback) {
        if (DEBUG) Log.d(TAG, "unregisterCallback:");
        mCallbacks.remove(callback);
    }

    @Override
    public void clearCallbacks() {
        if (DEBUG) Log.d(TAG, "clearCallbacks:");
        mCallbacks.clear();
    }

    @Override
    public List<Format> getSupportedFormatList() {
        if (mUVCCamera != null) {
            return mUVCCamera.getSupportedFormatList();
        }
        return null;
    }

    @Override
    public List<Size> getSupportedSizeList() {
        if (mUVCCamera != null) {
            return mUVCCamera.getSupportedSizeList();
        }
        return null;
    }

    @Override
    public Size getPreviewSize() {
        if (mUVCCamera != null) {
            return mUVCCamera.getPreviewSize();
        }
        return null;
    }

    /**
     * Changes the preview size. Refused while recording, since the encoders are sized to the
     * stream; if the camera rejects the size, the camera is closed.
     *
     * 更改预览尺寸。录像期间拒绝更改，因为编码器的尺寸与视频流一致；
     * 如果相机不接受该尺寸，则关闭相机。
     *
     * @throws IllegalStateException while recording / 录像期间
     */
    @Override
    public void setPreviewSize(Size size) {
        if (DEBUG) Log.d(TAG, "setPreviewSize:" + size);
        if (isRecording()) {
            throw new IllegalStateException("Can't change the preview size while recording");
        }
        try {
            synchronized (mSync) {
                if (mUVCCamera == null) {
                    return;
                }
                mUVCCamera.setPreviewSize(size);
            }

            // The preview size may have changed: resize the captures that depend on it.
            // 预览尺寸可能已改变：调整依赖它的拍照和录像组件的尺寸。
            final Size previewSize = getPreviewSize();
            final VideoCapture videoCapture = mVideoCapture;
            if (videoCapture != null) {
                videoCapture.setResolution(previewSize);
            }
            final IImageCapture imageCapture = mImageCapture;
            if (imageCapture instanceof ImageCapture2) {
                ((ImageCapture2) imageCapture).setResolution(previewSize);
            }
        } catch (final Exception e) {
            Log.e(TAG, "setPreviewSize:", e);
            // unexpectedly #setPreviewSize failed: close the camera the usual way, so the app hears
            // about it and the captures are released
            // #setPreviewSize 意外失败：按常规方式关闭相机，使应用收到通知，并释放拍照和录像组件
            closeCamera();
        }
    }

    private void updateRendererSize(final int width, final int height) {
        if (DEBUG) Log.d(TAG, "updateRendererSize:");
        mFrameWidth = width;
        mFrameHeight = height;
        if (mRendererHolder != null) {
            mRendererHolder.updatePrimarySize(width, height);
        }
    }

    @Override
    public void addSurface(final Object surface, final boolean isRecordable) {
        if (DEBUG) Log.d(TAG, "addSurface:surface=" + surface);
        if (mRendererHolder != null) {
            mRendererHolder.addSlaveSurface(surface.hashCode(), surface, isRecordable);
        }
    }

    @Override
    public void removeSurface(final Object surface) {
        if (DEBUG) Log.d(TAG, "removeSurface:surface=" + surface);
        if (mRendererHolder != null) {
            mRendererHolder.removeSlaveSurface(surface.hashCode());
        }
    }

    @Override
    public void setButtonCallback(IButtonCallback callback) {
        if (DEBUG) Log.d(TAG, "setButtonCallback:callback=" + callback);
        try {
            if (mUVCCamera != null) {
                mUVCCamera.setButtonCallback(callback);
            }
        } catch (final Exception e) {
            Log.e(TAG, "setButtonCallback:", e);
        }
    }

    @Override
    public void setFrameCallback(final IFrameCallback callback, final int pixelFormat) {
        if (DEBUG) Log.d(TAG, "setFrameCallback:surface=" + callback);
        try {
            if (mUVCCamera != null) {
                mUVCCamera.setFrameCallback(callback, pixelFormat);
            }
        } catch (final Exception e) {
            Log.e(TAG, "setFrameCallback:", e);
        }
    }

    @Override
    public void openCamera(UVCParam param,
                           CameraPreviewConfig previewConfig,
                           ImageCaptureConfig imageCaptureConfig,
                           VideoCaptureConfig videoCaptureConfig) {
        if (DEBUG) Log.d(TAG, "openCamera:");
        if (!isCameraOpened()) {
            openUVCCamera(param,
                    previewConfig, imageCaptureConfig, videoCaptureConfig);
        } else {
            if (DEBUG) Log.d(TAG, "have already opened camera, just call callback");
            processOnCameraOpen();
        }
    }

    private void resetUVCCamera() {
        if (DEBUG) Log.d(TAG, "resetUVCCamera: " + this);
        stopRecording();
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
                mUVCCamera.destroy(true);
                mUVCCamera = null;
            }
            mRendererHolder.removeSlaveSurfaceAll();
        }
    }

    private void openUVCCamera(UVCParam param,
                               CameraPreviewConfig previewConfig,
                               ImageCaptureConfig imageCaptureConfig,
                               VideoCaptureConfig videoCaptureConfig) {
        resetUVCCamera();
        if (DEBUG) Log.d(TAG, "openUVCCamera: " + this);
        try {
            int result = 0;
            synchronized (mSync) {
                mUVCCamera = new UVCCamera(param);
                result = mUVCCamera.open(mCtrlBlock);
            }
            if (result != 0) {
                Context context = UVCUtils.getApplication();
                switch (result) {
                    case UVCCamera.UVC_ERROR_BUSY:
                        throw new CameraException(CameraException.CAMERA_OPEN_ERROR_BUSY, context.getString(R.string.error_messge_camera_open_busy));
                    default:
                        throw new CameraException(CameraException.CAMERA_OPEN_ERROR_UNKNOWN, context.getString(R.string.error_messge_camera_open_unknown));
                }
            }
            if (DEBUG) Log.i(TAG, "supportedSize:" + mUVCCamera.getSupportedSize());

            setPreviewConfig(previewConfig);

            createImageCapture(imageCaptureConfig);
            mVideoCapture = new VideoCapture(mRendererHolder, videoCaptureConfig, getPreviewSize());

            processOnCameraOpen();
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "openUVCCamera:", e);
            // Undo the partial open, so isCameraOpened() stays false and the native camera is freed
            // 撤销未完成的打开操作，使 isCameraOpened() 保持为 false，并释放 native 相机
            releaseAfterOpenFailure();
            CameraException ex = null;
            if (e instanceof CameraException) {
                ex = (CameraException) e;
            } else {
                ex = new CameraException(CameraException.CAMERA_OPEN_ERROR_UNKNOWN, e);
            }
            processOnError(ex);
        }
    }

    private void releaseAfterOpenFailure() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                try {
                    mUVCCamera.destroy(true);
                } catch (Exception e) {
                    Log.w(TAG, "releaseAfterOpenFailure:", e);
                }
                mUVCCamera = null;
            }
        }
        if (mImageCapture != null) {
            mImageCapture.release();
            mImageCapture = null;
        }
        if (mVideoCapture != null) {
            mVideoCapture.release();
            mVideoCapture = null;
        }
    }

    private void createImageCapture(ImageCaptureConfig config) {
        if (mImageCapture != null) {
            mImageCapture.release();
            mImageCapture = null;
        }
        switch (config.getCaptureStrategy()) {
            case IImageCapture.CAPTURE_STRATEGY_IMAGE_READER:
                mImageCapture = new ImageCapture2(this, config, getPreviewSize());
                break;
            case IImageCapture.CAPTURE_STRATEGY_OPENGL_ES:
            default:
                mImageCapture = new ImageCapture(mRendererHolder, config);
                break;
        }
    }

    @Override
    public void closeCamera() {
        if (DEBUG) Log.d(TAG, "closeCamera: " + this);
        stopRecording();
        boolean closed = false;
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
                mUVCCamera.destroy();
                mUVCCamera = null;
                closed = true;
            }
            mIsPreviewing = false;
        }
        if (closed) {
            processOnCameraClose();
        }

        if (mImageCapture != null) {
            mImageCapture.release();
            mImageCapture = null;
        }
        if (mVideoCapture != null) {
            mVideoCapture.release();
            mVideoCapture = null;
        }
    }

    @Override
    public void startPreview() {
        if (DEBUG) Log.d(TAG, "startPreview: " + this);
        synchronized (mSync) {
            if (mUVCCamera == null) return;

//				mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV);

            final Size size = mUVCCamera.getPreviewSize();
            if (size != null) {
                updateRendererSize(size.width, size.height);
            }

            mUVCCamera.setPreviewDisplay(mRendererHolder.getPrimarySurface());
            mUVCCamera.startPreview();

            mIsPreviewing = true;
        }
    }

    @Override
    public void stopPreview() {
        if (DEBUG) Log.d(TAG, "stopPreview: " + this);
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
            }

            mIsPreviewing = false;
        }
    }

    @Override
    public UVCControl getUVCControl() {
        if (mUVCCamera != null) {
            return mUVCCamera.getControl();
        }
        return null;
    }

    /**
     * Captures a new still image and saves to a file.
     *
     * <p> The callback will be called only once for every invocation of this method.
     *
     * <p> 拍摄一张新的静态图片并保存到文件。每次调用此方法，回调只会被调用一次。
     *
     * @param options  Options to store the newly captured image. / 用于保存新拍摄图片的选项。
     * @param callback Callback to be called for the newly captured image. / 新拍摄图片时调用的回调。
     */
    @Override
    public void takePicture(ImageCapture.OutputFileOptions options, ImageCapture.OnImageCaptureCallback callback) {
        if (isCameraOpened() && mImageCapture != null) {
            mImageCapture.takePicture(options, callback);
        } else {
            String message = "Not bound to a Camera";
            callback.onError(ImageCapture.ERROR_INVALID_CAMERA, message, new IllegalStateException(message));
        }
    }

    @Override
    public boolean isRecording() {
        return mVideoCapture != null && mVideoCapture.isRecording();
    }

    @Override
    public void startRecording(VideoCapture.OutputFileOptions options, VideoCapture.OnVideoCaptureCallback callback) {
        if (isCameraOpened() && mVideoCapture != null) {
            mVideoCapture.startRecording(options, callback);
        } else {
            String message = "Not bound to a Camera";
            callback.onError(VideoCapture.ERROR_INVALID_CAMERA, message, new IllegalStateException(message));
        }
    }

    @Override
    public void stopRecording() {
        if (mVideoCapture != null) {
            mVideoCapture.stopRecording();
        }
    }

    @Override
    public boolean isCameraOpened() {
        return mUVCCamera != null;
    }

    @Override
    public void release() {
        if (DEBUG) Log.d(TAG, "release:");
        if (isCameraOpened()) {
            closeCamera();
        }
        releaseResource();
    }

    @Override
    public void setPreviewConfig(CameraPreviewConfig config) {
        if (DEBUG) Log.d(TAG, "setCameraPreviewConfig:");

        int rotation = config.getRotation();
        if (DEBUG) Log.d(TAG, "rotateTo:" + rotation);
        if (mRendererHolder != null) {
            mRendererHolder.rotateTo(rotation);
        }

        int mirror = config.getMirror();
        if (DEBUG) Log.d(TAG, "setMirrorMode:" + mirror);
        if (mRendererHolder != null) {
            mRendererHolder.setMirrorMode(mirror);
        }
    }

    @Override
    public void setImageCaptureConfig(ImageCaptureConfig config) {
        if (DEBUG) Log.d(TAG, "setImageCaptureConfig:");

        createImageCapture(config);
    }

    @Override
    public void setVideoCaptureConfig(VideoCaptureConfig config) {
        if (DEBUG) Log.d(TAG, "setVideoCaptureConfig:");

        if (mVideoCapture != null) {
            mVideoCapture.setConfig(config);
        }
    }

    private void releaseResource() {
        if (DEBUG) Log.d(TAG, "releaseResource: " + this);
        synchronized (mSync) {
            clearCallbacks();

            if (mRendererHolder != null) {
                mRendererHolder.release();
                mRendererHolder = null;
            }
        }
    }

    //********************************************************************************
    private void processOnCameraOpen() {
        if (DEBUG) Log.d(TAG, "processOnCameraOpen:");
        try {
            for (StateCallback callback : mCallbacks) {
                try {
                    callback.onCameraOpen();
                } catch (Exception e) {
                    Log.w(TAG, "processOnCameraOpen:", e);
                }
            }
        } catch (final Exception e) {
            Log.w(TAG, e);
        }
    }

    private void processOnCameraClose() {
        if (DEBUG) Log.d(TAG, "processOnCameraClose:");
        for (StateCallback callback : mCallbacks) {
            try {
                callback.onCameraClose();
            } catch (Exception e) {
                Log.w(TAG, "processOnCameraClose:", e);
            }
        }
    }

    private void processOnError(CameraException ex) {
        if (DEBUG) Log.d(TAG, "processOnError:" + ex);
        for (StateCallback callback : mCallbacks) {
            try {
                callback.onError(ex);
            } catch (Exception e) {
                Log.w(TAG, "processOnError:", e);
            }
        }
    }
}
