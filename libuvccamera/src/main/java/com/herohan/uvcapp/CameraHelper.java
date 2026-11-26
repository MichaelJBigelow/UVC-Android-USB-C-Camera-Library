package com.herohan.uvcapp;

import static com.herohan.uvcapp.ImageCapture.ERROR_CAMERA_CLOSED;
import static com.herohan.uvcapp.ImageCapture.ERROR_UNKNOWN;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.serenegiant.usb.Format;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCControl;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.uvccamera.BuildConfig;

import java.util.List;
import java.util.WeakHashMap;

public class CameraHelper implements ICameraHelper {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = CameraHelper.class.getSimpleName();

    private HandlerThread mAsyncHandlerThread;
    private Handler mAsyncHandler;

    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Set on the helper thread and read on the caller's thread / 在辅助线程上设置，在调用方线程上读取
    protected volatile ICameraConnection mService;
    protected volatile StateCallback mCallbackWrapper;

    private volatile UsbDevice mUsbDevice;
    private final WeakHashMap<UsbDevice, Object> mDetachedDeviceMap = new WeakHashMap<>();

    private CameraPreviewConfig mCameraPreviewConfig = new CameraPreviewConfig();
    private ImageCaptureConfig mImageCaptureConfig = new ImageCaptureConfig();
    private VideoCaptureConfig mVideoCaptureConfig = new VideoCaptureConfig();

    public CameraHelper() {
        if (DEBUG) Log.d(TAG, "Constructor:");

        mAsyncHandlerThread = new HandlerThread(TAG);
        mAsyncHandlerThread.start();
        mAsyncHandler = new Handler(mAsyncHandlerThread.getLooper());

        mService = CameraConnectionService.getInstance().newConnection();
    }

    @Override
    public void setStateCallback(StateCallback callback) {
        if (callback != null) {
            mCallbackWrapper = new StateCallbackWrapper(callback);
            registerCallback();
        } else {
            unregisterCallback();
            mCallbackWrapper = null;
        }
    }

    @Override
    public List<UsbDevice> getDeviceList() {
        if (DEBUG) Log.d(TAG, "getDeviceList:");
        if (mService != null) {
            try {
                return mService.getDeviceList();
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "getDeviceList:", e);
            }
        }
        return null;
    }

    private boolean isDetached(UsbDevice usbDevice) {
        return mDetachedDeviceMap.containsKey(usbDevice);
    }

    @Override
    public void selectDevice(final UsbDevice device) {
        if (DEBUG)
            Log.d(TAG, "selectDevice:device=" + (device != null ? device.getDeviceName() : null) + " " + this);
        mAsyncHandler.post(() -> {
            if (mService != null && !isDetached(device)) {
                mUsbDevice = device;
                try {
                    mService.selectDevice(device);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "selectDevice:", e);
                }
            }
        });
    }

    @Override
    public List<Format> getSupportedFormatList() {
        if (DEBUG) Log.d(TAG, "getSupportedFormatList:");
        if (mService != null && mUsbDevice != null) {
            try {
                return mService.getSupportedFormatList(mUsbDevice);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "getSupportedFormatList:", e);
            }
        }
        return null;
    }

    @Override
    public List<Size> getSupportedSizeList() {
        if (DEBUG) Log.d(TAG, "getSupportedSizeList:");
        if (mService != null && mUsbDevice != null) {
            try {
                return mService.getSupportedSizeList(mUsbDevice);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "getSupportedSizeList:", e);
            }
        }
        return null;
    }

    @Override
    public Size getPreviewSize() {
        if (DEBUG) Log.d(TAG, "getPreviewSize:");
        if (mService != null && mUsbDevice != null) {
            try {
                return mService.getPreviewSize(mUsbDevice);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "getPreviewSize:", e);
            }
        }
        return null;
    }

    @Override
    public void setPreviewSize(final Size size) {
        if (DEBUG) Log.d(TAG, "setPreviewSize:" + size);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    mService.setPreviewSize(mUsbDevice, size);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "setPreviewSize:", e);
                }
            }
        });
    }

    /**
     * Checks the type of a preview surface on the caller's thread, so a wrong type is reported to
     * the caller instead of being thrown on the helper thread.
     *
     * 在调用方线程上检查预览 surface 的类型，使类型错误报告给调用方，而不是在辅助线程上抛出。
     */
    private static void checkSurfaceType(final Object surface) {
        if (!(surface instanceof SurfaceView || surface instanceof SurfaceHolder
                || surface instanceof Surface || surface instanceof SurfaceTexture)) {
            throw new IllegalArgumentException(
                    "addSurface() can only be called with an instance of " +
                            "Surface, SurfaceView, SurfaceTexture or SurfaceHolder at the moment.");
        }
    }

    /**
     * The Surface or SurfaceTexture to draw on, or null if a SurfaceView or SurfaceHolder has none yet.
     *
     * 用于绘制的 Surface 或 SurfaceTexture；如果 SurfaceView 或 SurfaceHolder 尚无 surface，则为 null。
     */
    private static Object fetchSurface(final Object surface) {
        if (surface instanceof SurfaceView) {
            return ((SurfaceView) surface).getHolder().getSurface();
        } else if (surface instanceof SurfaceHolder) {
            return ((SurfaceHolder) surface).getSurface();
        }
        return surface;
    }

    @Override
    public void addSurface(final Object surface, final boolean isRecordable) {
        if (DEBUG) Log.d(TAG, "addSurface:surface=" + surface + ",isRecordable=" + isRecordable);
        checkSurfaceType(surface);
        mAsyncHandler.post(() -> {
            Object sur = fetchSurface(surface);
            if (sur == null) {
                Log.w(TAG, "addSurface: surface is not ready");
            } else {
                if (mService != null && mUsbDevice != null) {
                    try {
                        mService.addSurface(mUsbDevice, sur, isRecordable);
                    } catch (final Exception e) {
                        if (DEBUG) Log.e(TAG, "addSurface:", e);
                    }
                }
            }
        });
    }

    @Override
    public void removeSurface(final Object surface) {
        removeSurface(surface, null);
    }

    @Override
    public void removeSurface(final Object surface, final Runnable onRemoved) {
        if (DEBUG) Log.d(TAG, "removeSurface:surface=" + surface);
        checkSurfaceType(surface);
        final boolean posted = mAsyncHandler.post(() -> {
            Object sur = fetchSurface(surface);
            if (sur != null) {
                if (mService != null && mUsbDevice != null) {
                    try {
                        mService.removeSurface(mUsbDevice, sur);
                    } catch (final Exception e) {
                        if (DEBUG) Log.e(TAG, "handleRemoveSurface:", e);
                    }
                }
            }
            if (onRemoved != null) {
                mMainHandler.post(onRemoved);
            }
        });
        // Released helper: nothing draws on the surface any more
        // helper 已释放：不再有任何内容绘制到该 surface 上
        if (!posted && onRemoved != null) {
            mMainHandler.post(onRemoved);
        }
    }

    @Override
    public void setButtonCallback(IButtonCallback callback) {
        if (DEBUG) Log.d(TAG, "setButtonCallback:" + callback);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    mService.setButtonCallback(mUsbDevice, callback);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "setButtonCallback:", e);
                }
            }
        });
    }

    @Override
    public void setFrameCallback(IFrameCallback callback, int pixelFormat) {
        if (DEBUG) Log.d(TAG, "setFrameCallback:" + pixelFormat);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    mService.setFrameCallback(mUsbDevice, callback, pixelFormat);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "setFrameCallback:", e);
                }
            }
        });
    }

    @Override
    public void openCamera() {
        openCamera(new UVCParam());
    }

    @Override
    public void openCamera(Size size) {
        openCamera(new UVCParam(size, 0));
    }

    @Override
    public void openCamera(UVCParam param) {
        if (DEBUG) Log.d(TAG, "openCamera: " + this);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null && !isDetached(mUsbDevice)) {
                try {
                    if (!mService.isCameraOpened(mUsbDevice)) {
                        mService.openCamera(mUsbDevice, param,
                                mCameraPreviewConfig,
                                mImageCaptureConfig,
                                mVideoCaptureConfig);
                    }
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "openCamera:", e);
                }
            }
        });
    }

    @Override
    public void closeCamera() {
        if (DEBUG) Log.d(TAG, "closeCamera: " + this);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    if (mService.isCameraOpened(mUsbDevice)) {
                        mService.closeCamera(mUsbDevice);
                    }
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "closeCamera:", e);
                }
            }
        });
    }

    @Override
    public void startPreview() {
        if (DEBUG) Log.d(TAG, "startPreview: " + this);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    mService.startPreview(mUsbDevice);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "startPreview:", e);
                }
            }
        });
    }

    @Override
    public void stopPreview() {
        if (DEBUG) Log.d(TAG, "stopPreview: " + this);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    mService.stopPreview(mUsbDevice);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "stopPreview:", e);
                }
            }
        });
    }

    @Override
    public UVCControl getUVCControl() {
        if (DEBUG) Log.d(TAG, "getUVCControl:");
        if (mService != null && mUsbDevice != null) {
            try {
                return mService.getUVCControl(mUsbDevice);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "getUVCControl:", e);
            }
        }
        return null;
    }

    @Override
    public void takePicture(ImageCapture.OutputFileOptions options, ImageCapture.OnImageCaptureCallback imageCallback) {
        if (DEBUG) Log.d(TAG, "takePicture");
        final ImageCapture.OnImageCaptureCallback callback = new MainThreadImageCallback(imageCallback);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    mService.takePicture(mUsbDevice, options, callback);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "takePicture", e);
                    String message = e.getMessage();
                    if (message == null) {
                        message = "takePicture failed with an unknown exception";
                    }
                    callback.onError(ERROR_UNKNOWN, message, e);
                }
            } else {
                String message = "Camera is released";
                callback.onError(ERROR_CAMERA_CLOSED, message, new IllegalStateException(message));
            }
        });
    }

    @Override
    public boolean isRecording() {
        if (DEBUG) Log.d(TAG, "isRecording:");
        if (mService != null && mUsbDevice != null) {
            try {
                return mService.isRecording(mUsbDevice);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "isRecording:", e);
            }
        }
        return false;
    }

    @Override
    public void startRecording(VideoCapture.OutputFileOptions options, VideoCapture.OnVideoCaptureCallback videoCallback) {
        if (DEBUG) Log.d(TAG, "startRecording");
        final VideoCapture.OnVideoCaptureCallback callback = new MainThreadVideoCallback(videoCallback);
        mAsyncHandler.post(() -> {
            if (mService != null && mUsbDevice != null) {
                try {
                    mService.startRecording(mUsbDevice, options, callback);
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "startRecording", e);
                    String message = e.getMessage();
                    if (message == null) {
                        message = "startRecording failed with an unknown exception";
                    }
                    callback.onError(VideoCapture.ERROR_UNKNOWN, message, e);
                }
            } else {
                String message = "Camera is released";
                callback.onError(VideoCapture.ERROR_INVALID_CAMERA, message, new IllegalStateException(message));
            }
        });
    }

    @Override
    public void stopRecording() {
        if (DEBUG) Log.d(TAG, "stopRecording:");
        mAsyncHandler.post(() -> {
            if (isRecording()) {
                if (mService != null && mUsbDevice != null) {
                    try {
                        mService.stopRecording(mUsbDevice);
                    } catch (final Exception e) {
                        if (DEBUG) Log.e(TAG, "stopRecording:", e);
                    }
                }
            }
        });
    }

    @Override
    public boolean isCameraOpened() {
        if (DEBUG) Log.d(TAG, "isCameraOpened:");
        if (mService != null && mUsbDevice != null) {
            try {
                return mService.isCameraOpened(mUsbDevice);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "isCameraOpened:", e);
            }
        }
        return false;
    }

    @Override
    public void release() {
        if (DEBUG) Log.d(TAG, "release: " + this);
        cancelServiceWaits();
        mAsyncHandler.post(() -> {
            if (mService != null) {
                try {
                    if (mUsbDevice != null) {
                        mService.releaseCamera(mUsbDevice);
                    }
                    mService.release();
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "release:", e);
                }

                mCallbackWrapper = null;
                mService = null;
            }

            mUsbDevice = null;
            mAsyncHandlerThread.quitSafely();
            mDetachedDeviceMap.clear();
        });
    }

    @Override
    public void releaseAll() {
        if (DEBUG) Log.d(TAG, "releaseAll: " + this);
        cancelServiceWaits();
        mAsyncHandler.post(() -> {
            if (mService != null) {
                try {
                    mService.releaseAllCamera();
                    mService.release();
                } catch (final Exception e) {
                    if (DEBUG) Log.e(TAG, "release:", e);
                }

                mCallbackWrapper = null;
                mService = null;
            }

            mUsbDevice = null;
            mAsyncHandlerThread.quitSafely();
            mDetachedDeviceMap.clear();
        });
    }

    /**
     * Returns the current preview settings for this Camera.
     * If modifications are made to the returned Config, they must be passed
     * to {@link #setPreviewConfig(CameraPreviewConfig)} to take effect.
     *
     * 返回此 Camera 当前的预览设置。
     * 如果对返回的 Config 进行了修改,必须将其传递给
     * {@link #setPreviewConfig(CameraPreviewConfig)} 才能生效。
     */
    @Override
    public CameraPreviewConfig getPreviewConfig() {
        return mCameraPreviewConfig;
    }

    /**
     * Changes the preview settings for this Camera.
     *
     * 更改此 Camera 的预览设置。
     *
     * @param config the Parameters to use for this Camera / 此 Camera 使用的参数
     */
    @Override
    public void setPreviewConfig(CameraPreviewConfig config) {
        if (DEBUG) Log.d(TAG, "setCameraPreviewConfig:");
        mCameraPreviewConfig = config;
        mAsyncHandler.post(() -> {
            if (isCameraOpened()) {
                if (mService != null && mUsbDevice != null) {
                    try {
                        mService.setPreviewConfig(mUsbDevice, mCameraPreviewConfig);
                    } catch (final Exception e) {
                        if (DEBUG) Log.e(TAG, "setCameraPreviewConfig:", e);
                    }
                }
            }
        });
    }

    /**
     * Returns the current ImageCapture settings for this Camera.
     * If modifications are made to the returned Config, they must be passed
     * to {@link #setImageCaptureConfig(ImageCaptureConfig)} to take effect.
     *
     * 返回此 Camera 当前的 ImageCapture 设置。
     * 如果对返回的 Config 进行了修改,必须将其传递给
     * {@link #setImageCaptureConfig(ImageCaptureConfig)} 才能生效。
     */
    @Override
    public ImageCaptureConfig getImageCaptureConfig() {
        return mImageCaptureConfig;
    }

    /**
     * Changes the ImageCapture settings for this Camera.
     *
     * 更改此 Camera 的 ImageCapture 设置。
     *
     * @param config the Parameters to use for this Camera / 此 Camera 使用的参数
     */
    @Override
    public void setImageCaptureConfig(ImageCaptureConfig config) {
        if (DEBUG) Log.d(TAG, "setImageCaptureConfig:");
        mImageCaptureConfig = config;
        mAsyncHandler.post(() -> {
            if (isCameraOpened()) {
                if (mService != null && mUsbDevice != null) {
                    try {
                        mService.setImageCaptureConfig(mUsbDevice, mImageCaptureConfig);
                    } catch (final Exception e) {
                        if (DEBUG) Log.e(TAG, "setImageCaptureConfig:", e);
                    }
                }
            }
        });
    }

    /**
     * Returns the current VideoCapture settings for this Camera.
     * If modifications are made to the returned Config, they must be passed
     * to {@link #setVideoCaptureConfig(VideoCaptureConfig)} to take effect.
     *
     * 返回此 Camera 当前的 VideoCapture 设置。
     * 如果对返回的 Config 进行了修改,必须将其传递给
     * {@link #setVideoCaptureConfig(VideoCaptureConfig)} 才能生效。
     */
    @Override
    public VideoCaptureConfig getVideoCaptureConfig() {
        return mVideoCaptureConfig;
    }

    /**
     * Changes the VideoCapture settings for this Camera.
     *
     * 更改此 Camera 的 VideoCapture 设置。
     *
     * @param config the Parameters to use for this Camera / 此 Camera 使用的参数
     */
    @Override
    public void setVideoCaptureConfig(VideoCaptureConfig config) {
        if (DEBUG) Log.d(TAG, "setVideoCaptureConfig:");
        mVideoCaptureConfig = config;
        mAsyncHandler.post(() -> {
            if (isCameraOpened()) {
                if (mService != null && mUsbDevice != null) {
                    try {
                        mService.setVideoCaptureConfig(mUsbDevice, mVideoCaptureConfig);
                    } catch (final Exception e) {
                        if (DEBUG) Log.e(TAG, "setVideoCaptureConfig:", e);
                    }
                }
            }
        });
    }

    public void registerCallback() {
        if (DEBUG) Log.d(TAG, "registerCallback:");
        if (mService != null) {
            try {
                mService.register(mCallbackWrapper);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "registerCallback:", e);
            }
        }
    }

    public void unregisterCallback() {
        if (DEBUG) Log.d(TAG, "unregisterCallback:");
        if (mService != null) {
            try {
                mService.unregister(mCallbackWrapper);
            } catch (final Exception e) {
                if (DEBUG) Log.e(TAG, "unregisterCallback:", e);
            }
        }
    }

    /**
     * Ends a wait for a camera or permission on the helper thread now, so the release posted after
     * it runs without waiting for that wait to time out.
     *
     * 立即结束辅助线程上对相机或权限的等待，使随后投递的释放操作无需等待其超时。
     */
    private void cancelServiceWaits() {
        final ICameraConnection service = mService;
        if (service != null) {
            service.cancelWaits();
        }
    }

    /**
     * Runs {@code action} on the main thread: now if this is the main thread, otherwise posted.
     *
     * 在主线程上运行 {@code action}：若当前即为主线程则立即运行，否则投递到主线程。
     */
    private void runOnMainThread(final Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mMainHandler.post(action);
        }
    }

    /**
     * Delivers every picture result on the main thread, whichever thread reports it.
     *
     * 无论由哪个线程报告，都在主线程上分发每个拍照结果。
     */
    private final class MainThreadImageCallback implements ImageCapture.OnImageCaptureCallback {
        private final ImageCapture.OnImageCaptureCallback mCallback;

        MainThreadImageCallback(ImageCapture.OnImageCaptureCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onImageSaved(@NonNull IImageCapture.OutputFileResults outputFileResults) {
            runOnMainThread(() -> mCallback.onImageSaved(outputFileResults));
        }

        @Override
        public void onError(int imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
            runOnMainThread(() -> mCallback.onError(imageCaptureError, message, cause));
        }
    }

    /**
     * Delivers every recording event on the main thread, whichever thread reports it.
     *
     * 无论由哪个线程报告，都在主线程上分发每个录像事件。
     */
    private final class MainThreadVideoCallback implements VideoCapture.OnVideoCaptureCallback {
        private final VideoCapture.OnVideoCaptureCallback mCallback;

        MainThreadVideoCallback(VideoCapture.OnVideoCaptureCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onStart() {
            runOnMainThread(mCallback::onStart);
        }

        @Override
        public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
            runOnMainThread(() -> mCallback.onVideoSaved(outputFileResults));
        }

        @Override
        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
            runOnMainThread(() -> mCallback.onError(videoCaptureError, message, cause));
        }
    }

    final class StateCallbackWrapper implements StateCallback {
        private StateCallback mCallback;

        StateCallbackWrapper(StateCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.d(TAG, "onAttach:");
            synchronized (mDetachedDeviceMap) {
                mDetachedDeviceMap.remove(device);
            }
            mMainHandler.post(() -> mCallback.onAttach(device));
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.d(TAG, "onDeviceOpen:");
            mMainHandler.post(() -> mCallback.onDeviceOpen(device, isFirstOpen));
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.d(TAG, "onCameraOpen:");
            mMainHandler.post(() -> mCallback.onCameraOpen(device));
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.d(TAG, "onCameraClose:");
            mMainHandler.post(() -> mCallback.onCameraClose(device));
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.d(TAG, "onDeviceClose:");
            mMainHandler.post(() -> mCallback.onDeviceClose(device));
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.d(TAG, "onDetach:");
            synchronized (mDetachedDeviceMap) {
                mDetachedDeviceMap.put(device, null);
            }
            mMainHandler.post(() -> mCallback.onDetach(device));
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.d(TAG, "onCancel:");
            mMainHandler.post(() -> mCallback.onCancel(device));
        }

        @Override
        public void onError(UsbDevice device, CameraException e) {
            if (DEBUG) Log.d(TAG, "onError:");
            mMainHandler.post(() -> mCallback.onError(device, e));
        }
    }
}
