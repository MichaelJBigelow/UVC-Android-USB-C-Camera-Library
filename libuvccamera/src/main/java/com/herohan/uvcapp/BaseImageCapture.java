package com.herohan.uvcapp;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Code shared by the two capture strategies, {@link ImageCapture} and {@link ImageCapture2}: the
 * configuration and JPEG quality, the executor that saves the pictures, and the callback that
 * reports the result on the main thread.
 *
 * 两种拍照策略 {@link ImageCapture} 和 {@link ImageCapture2} 共用的代码：
 * 配置和 JPEG 质量、保存照片的执行器，以及在主线程上报告结果的回调。
 */
abstract class BaseImageCapture implements IImageCapture {

    protected ImageCaptureConfig mConfig;

    protected final Handler mMainHandler = new Handler(Looper.getMainLooper());

    protected ExecutorService mExecutor;

    BaseImageCapture(ImageCaptureConfig config, final String threadNamePrefix) {
        this.mConfig = (ImageCaptureConfig) config.clone();
        this.mExecutor = Executors.newFixedThreadPool(1, new ThreadFactory() {
            private final AtomicInteger mId = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, threadNamePrefix + "image_capture" + mId.getAndIncrement());
            }
        });
    }

    @Override
    public void setConfig(ImageCaptureConfig config) {
        this.mConfig = (ImageCaptureConfig) config.clone();
    }

    /**
     * Gets the JPEG quality .
     *
     * <p> Range is 1-100; larger is higher quality.
     *
     * 获取 JPEG 质量。
     *
     * <p> 范围为 1-100;值越大质量越高。
     *
     * @return Compression quality of the captured JPEG image. / 所拍摄 JPEG 图像的压缩质量。
     */
    @IntRange(from = 1, to = 100)
    int getJpegQualityInternal() {
        if (mConfig.hasJpegCompressionQuality()) {
            return mConfig.getJpegCompressionQuality();
        }

        switch (mConfig.getCaptureMode()) {
            case CAPTURE_MODE_MAXIMIZE_QUALITY:
                return JPEG_QUALITY_MAXIMIZE_QUALITY_MODE;
            case CAPTURE_MODE_MINIMIZE_LATENCY:
                return JPEG_QUALITY_MINIMIZE_LATENCY_MODE;
            default:
                throw new IllegalStateException("CaptureMode " + mConfig.getCaptureMode() + " is invalid");
        }
    }

    /**
     * Converts the {@link ImageSaver.OnImageSavedCallback} to the caller's
     * {@link IImageCapture.OnImageCaptureCallback}: scans the saved file and reports the result on
     * the main thread.
     *
     * 将 {@link ImageSaver.OnImageSavedCallback} 转换为调用方的
     * {@link IImageCapture.OnImageCaptureCallback}：扫描已保存的文件，并在主线程上报告结果。
     */
    ImageSaver.OnImageSavedCallback wrapCallback(final @NonNull OnImageCaptureCallback imageSavedCallback) {
        return new ImageSaver.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull OutputFileResults outputFileResults) {
                MediaScanHelper.scanMediaFile(outputFileResults.getSavedUri());

                mMainHandler.post(() -> imageSavedCallback.onImageSaved(outputFileResults));
            }

            @Override
            public void onError(@NonNull ImageSaver.SaveError error, @NonNull String message, @Nullable Throwable cause) {
                mMainHandler.post(() -> {
                    @ImageCaptureError int imageCaptureError = ERROR_UNKNOWN;
                    switch (error) {
                        case FILE_IO_FAILED:
                            imageCaptureError = ERROR_FILE_IO;
                            break;
                        default:
                            // Keep the imageCaptureError as UNKNOWN_ERROR
                            // 将 imageCaptureError 保持为 UNKNOWN_ERROR
                            break;
                    }

                    imageSavedCallback.onError(imageCaptureError, message, cause);
                });
            }
        };
    }

    /**
     * Reports an error to the caller on the main thread, like every other capture result.
     *
     * 与其他拍照结果一样，在主线程上向调用方报告错误。
     */
    void postError(final @NonNull OnImageCaptureCallback callback, @ImageCaptureError final int error,
                   final @NonNull String message, final @Nullable Throwable cause) {
        mMainHandler.post(() -> callback.onError(error, message, cause));
    }

    @Override
    public void release() {
        if (mExecutor != null) {
            mExecutor.shutdown();
            mExecutor = null;
        }
    }
}
