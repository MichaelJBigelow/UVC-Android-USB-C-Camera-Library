package com.herohan.uvcapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.serenegiant.usb.Size;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Takes pictures by attaching an {@link ImageReader} to the camera
 * ({@link IImageCapture#CAPTURE_STRATEGY_IMAGE_READER}).
 *
 * <p>One reader is kept for the current preview size. It is attached to the camera only while
 * pictures are waiting, and each frame it receives answers the oldest waiting request.
 *
 * 通过将 {@link ImageReader} 挂接到相机来拍照({@link IImageCapture#CAPTURE_STRATEGY_IMAGE_READER})。
 *
 * <p>为当前预览尺寸保留一个 reader。仅在有等待中的拍照请求时才将其挂接到相机，
 * 它收到的每一帧都用于响应最早的等待请求。
 */
public class ImageCapture2 extends BaseImageCapture {

    private static final String TAG = ImageCapture2.class.getSimpleName();

    private final WeakReference<ICameraInternal> mCameraInternalWeak;

    /**
     * Guards the reader, the resolution and the waiting requests.
     *
     * 保护 reader、分辨率和等待中的请求。
     */
    private final Object mSync = new Object();
    private Size mResolution;
    private ImageReader mImageReader;
    /**
     * Whether the reader's surface is attached to the camera.
     *
     * reader 的 surface 是否已挂接到相机。
     */
    private boolean mReaderAttached;
    private final ArrayDeque<PendingCapture> mPendingCaptures = new ArrayDeque<>();
    private boolean mReleased;

    /**
     * Thread on which the reader reports new frames.
     *
     * reader 报告新帧所在的线程。
     */
    private final HandlerThread mReaderThread;
    private final Handler mReaderHandler;

    /**
     * A picture that was asked for and has not been taken yet.
     *
     * 已请求但尚未拍摄的照片。
     */
    private static final class PendingCapture {
        final OutputFileOptions options;
        final OnImageCaptureCallback callback;
        final int jpegQuality;

        PendingCapture(OutputFileOptions options, OnImageCaptureCallback callback, int jpegQuality) {
            this.options = options;
            this.callback = callback;
            this.jpegQuality = jpegQuality;
        }
    }

    ImageCapture2(ICameraInternal cameraInternal,
                  ImageCaptureConfig config,
                  Size resolution) {
        super(config, TAG);
        this.mCameraInternalWeak = new WeakReference<>(cameraInternal);

        mReaderThread = new HandlerThread(TAG + "_reader");
        mReaderThread.start();
        mReaderHandler = new Handler(mReaderThread.getLooper());

        synchronized (mSync) {
            mResolution = resolution;
            createImageReader();
        }
    }

    /**
     * Creates the reader for the current resolution. The caller holds mSync.
     *
     * 为当前分辨率创建 reader。调用方需持有 mSync。
     */
    @SuppressLint("WrongConstant")
    private void createImageReader() {
        if (mResolution != null) {
            mImageReader = ImageReader.newInstance(mResolution.width, mResolution.height, PixelFormat.RGBA_8888, 2);
            mImageReader.setOnImageAvailableListener(this::onImageAvailable, mReaderHandler);
        }
    }

    /**
     * Detaches the reader from the camera and closes it. The caller holds mSync.
     *
     * 将 reader 从相机上移除并关闭它。调用方需持有 mSync。
     */
    private void closeImageReader() {
        if (mImageReader != null) {
            detachReader();
            mImageReader.setOnImageAvailableListener(null, null);
            mImageReader.close();
            mImageReader = null;
        }
    }

    /**
     * Stops sending camera frames to the reader. The caller holds mSync.
     *
     * 停止向 reader 发送相机帧。调用方需持有 mSync。
     */
    private void detachReader() {
        if (mReaderAttached && mImageReader != null) {
            final ICameraInternal cameraInternal = mCameraInternalWeak.get();
            if (cameraInternal != null) {
                cameraInternal.removeSurface(mImageReader.getSurface());
            }
        }
        mReaderAttached = false;
    }

    /**
     * Fails every waiting request. The caller holds mSync.
     *
     * 使所有等待中的请求失败。调用方需持有 mSync。
     */
    private void failPendingCaptures(@ImageCaptureError int error, String message) {
        PendingCapture capture;
        while ((capture = mPendingCaptures.poll()) != null) {
            postError(capture.callback, error, message, null);
        }
    }

    /**
     * Follows a new preview size: waiting pictures fail, since the reader is recreated at the new size.
     *
     * 跟随新的预览尺寸：等待中的照片会失败，因为 reader 会按新尺寸重新创建。
     */
    void setResolution(Size resolution) {
        synchronized (mSync) {
            if (mReleased) {
                return;
            }
            failPendingCaptures(ERROR_CAPTURE_FAILED, "The preview size changed before the picture was taken");
            closeImageReader();
            mResolution = resolution;
            createImageReader();
        }
    }

    /**
     * Captures a new still image and saves to a file.
     *
     * <p> The callback will be called only once for every invocation of this method.
     *
     * <p> 拍摄一张新的静态图像并保存到文件。
     *
     * <p> 此方法每调用一次，回调只会被调用一次。
     *
     * @param outputFileOptions  Options to store the newly captured image. / 用于保存新拍摄图像的选项。
     * @param imageSavedCallback Callback to be called for the newly captured image. / 新图像拍摄完成后调用的回调。
     */
    @Override
    public void takePicture(
            final @NonNull OutputFileOptions outputFileOptions,
            final @NonNull OnImageCaptureCallback imageSavedCallback) {
        final ICameraInternal cameraInternal = mCameraInternalWeak.get();
        if (cameraInternal == null) {
            postError(imageSavedCallback, ERROR_INVALID_CAMERA, "Not bound to a Camera", null);
            return;
        }
        synchronized (mSync) {
            if (mReleased) {
                postError(imageSavedCallback, ERROR_CAMERA_CLOSED, "Camera is closed", null);
                return;
            }
            if (mImageReader == null) {
                postError(imageSavedCallback, ERROR_UNKNOWN, "ImageReader is null", null);
                return;
            }
            mPendingCaptures.add(new PendingCapture(outputFileOptions, imageSavedCallback,
                    getJpegQualityInternal()));
            if (!mReaderAttached) {
                cameraInternal.addSurface(mImageReader.getSurface(), false);
                mReaderAttached = true;
            }
        }
    }

    /**
     * Answers the oldest waiting request with the newest frame. Runs on the reader thread.
     *
     * 用最新的帧响应最早的等待请求。在 reader 线程上运行。
     */
    private void onImageAvailable(final ImageReader reader) {
        final PendingCapture capture;
        final int width;
        final int height;
        final Image image;
        synchronized (mSync) {
            // A frame of a reader that was closed or replaced since
            // 来自之后已被关闭或替换的 reader 的帧
            if (reader != mImageReader) {
                return;
            }
            try {
                image = reader.acquireLatestImage();
            } catch (final IllegalStateException e) {
                Log.w(TAG, "onImageAvailable:", e);
                return;
            }
            if (image == null) {
                return;
            }
            capture = mPendingCaptures.poll();
            if (mPendingCaptures.isEmpty()) {
                detachReader();
            }
            width = mResolution.width;
            height = mResolution.height;
        }
        if (capture == null) {
            // A frame that was already on its way when the reader was detached
            // 移除 reader 时已在途中的帧
            image.close();
            return;
        }
        try {
            final Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) {
                postError(capture.callback, ERROR_CAPTURE_FAILED, "The captured image has no data", null);
                return;
            }
            // Since we requested RGBA data, everything is stored in planes[0]
            // 因为我们要求的是RGBA格式的数据，所以全部的存储在planes[0]中
            final Image.Plane plane = planes[0];
            final ByteBuffer buffer = plane.getBuffer();
            // Because the buffer in the Image is data-aligned, its size is not necessarily the size specified when the ImageReader instance was created.
            // ImageReader automatically adds padding to the right side of each row for alignment, and the number of aligned bytes may vary by hardware,
            // so this padding data must be ignored when reading the data out.
            // 由于Image中的缓冲区存在数据对齐，所以其大小不一定是我们生成ImageReader实例时指定的大小，
            // ImageReader会自动为画面每一行最右侧添加一个padding，以进行对齐，对齐多少字节可能因硬件而异，
            // 所以我们在取出数据时需要忽略这一部分数据。
            final int pixelStride = plane.getPixelStride();
            final int rowStride = plane.getRowStride();
            final int rowPadding = rowStride - pixelStride * width;

            // create bitmap
            // 创建 bitmap
            final Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride,
                    height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);

            mExecutor.execute(new ImageSaver(bmp, width, height, capture.jpegQuality, capture.options,
                    wrapCallback(capture.callback)));
        } catch (final Exception e) {
            String message = e.getMessage();
            if (message == null) {
                message = "captureImage failed with an unknown exception";
            }
            postError(capture.callback, ERROR_UNKNOWN, message, e);
        } finally {
            image.close();
        }
    }

    @Override
    public void release() {
        synchronized (mSync) {
            if (mReleased) {
                return;
            }
            mReleased = true;
            failPendingCaptures(ERROR_CAMERA_CLOSED, "Camera is closed");
            closeImageReader();
        }
        mReaderThread.quitSafely();
        super.release();
    }
}
