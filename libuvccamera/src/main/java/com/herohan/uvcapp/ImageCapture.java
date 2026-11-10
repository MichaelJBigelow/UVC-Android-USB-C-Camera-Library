package com.herohan.uvcapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.herohan.uvcapp.ICameraRendererHolder.OnImageCapturedCallback;
import com.serenegiant.utils.UVCUtils;
import com.serenegiant.utils.UriHelper;

import java.io.File;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageCapture implements IImageCapture {

    private static final String TAG = ImageCapture.class.getSimpleName();

    private WeakReference<ICameraRendererHolder> mRendererHolderWeak;
    private ImageCaptureConfig mConfig;

    private Handler mMainHandler;

    private ExecutorService mExecutor;

    ImageCapture(ICameraRendererHolder rendererHolder,
                 ImageCaptureConfig config) {
        this.mRendererHolderWeak = new WeakReference<>(rendererHolder);
        this.mConfig = (ImageCaptureConfig) config.clone();
        this.mMainHandler = new Handler(Looper.getMainLooper());
        this.mExecutor = Executors.newFixedThreadPool(1, new ThreadFactory() {
            private final AtomicInteger mId = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, TAG + "image_capture" + mId.getAndIncrement());
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
     * Captures a new still image and saves to a file.
     *
     * <p> The callback will be called only once for every invocation of this method.
     *
     * 拍摄一张新的静态图像并保存到文件。
     *
     * <p> 每次调用此方法,回调只会被调用一次。
     *
     * @param outputFileOptions  Options to store the newly captured image. / 用于保存新拍摄图像的选项。
     * @param imageSavedCallback Callback to be called for the newly captured image. / 新拍摄图像保存后调用的回调。
     */
    @Override
    public void takePicture(
            final @NonNull OutputFileOptions outputFileOptions,
            final @NonNull OnImageCaptureCallback imageSavedCallback) {
        // Convert the ImageSaver.OnImageSavedCallback to ImageCapture.OnImageSavedCallback
        // 将 ImageSaver.OnImageSavedCallback 转换为 ImageCapture.OnImageSavedCallback
        ImageSaver.OnImageSavedCallback imageSavedCallbackWrapper = new ImageSaver.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                scanMediaFile(outputFileResults.getSavedUri());

                mMainHandler.post(() -> imageSavedCallback.onImageSaved(outputFileResults));
            }

            @Override
            public void onError(@NonNull ImageSaver.SaveError error, @NonNull String message, @Nullable Throwable cause) {
                mMainHandler.post(() -> {
                    @ImageCapture.ImageCaptureError int imageCaptureError = ERROR_UNKNOWN;
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

        ICameraRendererHolder cameraRendererHolder = mRendererHolderWeak.get();

        if (cameraRendererHolder == null) {
            imageSavedCallback.onError(ERROR_INVALID_CAMERA,
                    "Not bound to a Camera", null);
        } else {
            int outputJpegQuality = getJpegQualityInternal();
            cameraRendererHolder.captureImage(new OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(ImageRawData image) {
                    mExecutor.execute(new ImageSaver(image, outputJpegQuality, outputFileOptions,
                            imageSavedCallbackWrapper));
                }

                @Override
                public void onError(Exception e) {
                    String message = e.getMessage();
                    if (message == null) {
                        message = "captureImage failed with an unknown exception";
                    }
                    imageSavedCallback.onError(ERROR_UNKNOWN, message, e);
                }
            });
        }
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

    @Override
    public void release() {
        if (mExecutor != null) {
            mExecutor.shutdown();
            mExecutor = null;
        }
    }
}
