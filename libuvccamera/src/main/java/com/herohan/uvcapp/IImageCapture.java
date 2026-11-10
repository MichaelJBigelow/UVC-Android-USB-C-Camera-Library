package com.herohan.uvcapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface IImageCapture {
    /**
     * An unknown error occurred.
     *
     * <p>See message parameter in onError callback or log for more details.
     *
     * 发生未知错误。
     *
     * <p>详情请参见 onError 回调中的 message 参数或日志。
     */
    int ERROR_UNKNOWN = 0;
    /**
     * An error occurred while attempting to read or write a file, such as when saving an image
     * to a File.
     *
     * 尝试读取或写入文件时发生错误，例如将图像保存到 File 时。
     */
    int ERROR_FILE_IO = 1;

    /**
     * An error reported by camera framework indicating the capture request is failed.
     *
     * 由相机框架报告的错误，表示拍摄请求失败。
     */
    int ERROR_CAPTURE_FAILED = 2;

    /**
     * An error indicating the request cannot be done due to camera is closed.
     *
     * 表示由于相机已关闭而无法完成该请求的错误。
     */
    int ERROR_CAMERA_CLOSED = 3;

    /**
     * An error indicating this ImageCapture is not bound to a valid camera.
     *
     * 表示此 ImageCapture 未绑定到有效相机的错误。
     */
    int ERROR_INVALID_CAMERA = 4;

    /**
     * Implementation based on OPENGL ES
     *
     * 基于 OPENGL ES 的实现
     */
    int CAPTURE_STRATEGY_OPENGL_ES = 0;

    /**
     * Implementation based on ImageReader
     *
     * 基于 ImageReader 的实现
     */
    int CAPTURE_STRATEGY_IMAGE_READER = 1;

    /**
     * Optimizes capture pipeline to prioritize image quality over latency. When the capture
     * mode is set to MAX_QUALITY, images may take longer to capture.
     *
     * 优化拍摄流水线，优先保证图像质量而非延迟。当拍摄模式设置为 MAX_QUALITY 时，
     * 图像的拍摄可能需要更长时间。
     */
    int CAPTURE_MODE_MAXIMIZE_QUALITY = 0;
    /**
     * Optimizes capture pipeline to prioritize latency over image quality. When the capture
     * mode is set to MIN_LATENCY, images may capture faster but the image quality may be
     * reduced.
     *
     * 优化拍摄流水线，优先保证延迟而非图像质量。当拍摄模式设置为 MIN_LATENCY 时，
     * 图像拍摄可能更快，但图像质量可能会降低。
     */
    int CAPTURE_MODE_MINIMIZE_LATENCY = 1;

    int JPEG_QUALITY_MAXIMIZE_QUALITY_MODE = 100;
    int JPEG_QUALITY_MINIMIZE_LATENCY_MODE = 95;

    void setConfig(ImageCaptureConfig config);

    void takePicture(ImageCapture.OutputFileOptions options, ImageCapture.OnImageCaptureCallback callback);

    void release();

    /**
     * Options for saving newly captured image.
     *
     * <p> this class is used to configure save location and other options.
     * Save location can be either a {@link File}, {@link MediaStore} or a {@link OutputStream}.
     *
     * 用于保存新拍摄图像的选项。
     *
     * <p> 此类用于配置保存位置及其他选项。
     * 保存位置可以是 {@link File}、{@link MediaStore} 或 {@link OutputStream}。
     */
    final class OutputFileOptions {
        @Nullable
        private File mFile;
        @Nullable
        private ContentResolver mContentResolver;
        @Nullable
        private Uri mSaveCollection;
        @Nullable
        private ContentValues mContentValues;
        @Nullable
        private OutputStream mOutputStream;

        OutputFileOptions(@Nullable File file,
                          @Nullable ContentResolver contentResolver,
                          @Nullable Uri saveCollection,
                          @Nullable ContentValues contentValues,
                          @Nullable OutputStream outputStream) {
            this.mFile = file;
            this.mContentResolver = contentResolver;
            this.mSaveCollection = saveCollection;
            this.mContentValues = contentValues;
            this.mOutputStream = outputStream;
        }

        @Nullable
        public File getFile() {
            return mFile;
        }

        @Nullable
        public ContentResolver getContentResolver() {
            return mContentResolver;
        }

        @Nullable
        public Uri getSaveCollection() {
            return mSaveCollection;
        }

        @Nullable
        public ContentValues getContentValues() {
            return mContentValues;
        }

        @Nullable
        public OutputStream getOutputStream() {
            return mOutputStream;
        }

        public static final class Builder {
            @Nullable
            private File mFile;
            @Nullable
            private ContentResolver mContentResolver;
            @Nullable
            private Uri mSaveCollection;
            @Nullable
            private ContentValues mContentValues;
            @Nullable
            private OutputStream mOutputStream;

            /**
             * Creates options to write captured image to a {@link File}.
             *
             * 创建将拍摄的图像写入 {@link File} 的选项。
             *
             * @param file save location of the image. / 图像的保存位置。
             */
            public Builder(@Nullable File file) {
                this.mFile = file;
            }

            /**
             * Creates options to write captured image to {@link MediaStore}.
             * <p>
             * Example:
             * <p>
             * 创建将拍摄的图像写入 {@link MediaStore} 的选项。
             * <p>
             * 示例：
             *
             * <pre>{@code
             *
             * ContentValues contentValues = new ContentValues();
             * contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_IMAGE");
             * contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
             *
             * ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
             *         getContentResolver(),
             *         MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
             *         contentValues).build();
             *
             * }</pre>
             *
             * @param contentResolver to access {@link MediaStore} / 用于访问 {@link MediaStore}
             * @param saveCollection  The URL of the table to insert into. / 要插入的表的 URL。
             * @param contentValues   to be included in the created image file. / 要包含在所创建图像文件中的内容值。
             */
            public Builder(@Nullable ContentResolver contentResolver,
                           @Nullable Uri saveCollection,
                           @Nullable ContentValues contentValues) {
                this.mContentResolver = contentResolver;
                this.mSaveCollection = saveCollection;
                this.mContentValues = contentValues;
            }

            /**
             * Creates options that write captured image to a {@link OutputStream}.
             *
             * 创建将拍摄的图像写入 {@link OutputStream} 的选项。
             *
             * @param outputStream save location of the image. / 图像的保存位置。
             */
            public Builder(@Nullable OutputStream outputStream) {
                this.mOutputStream = outputStream;
            }

            /**
             * Builds {@link OutputFileOptions}.
             *
             * 构建 {@link OutputFileOptions}。
             */
            public OutputFileOptions build() {
                return new OutputFileOptions(mFile, mContentResolver,
                        mSaveCollection, mContentValues, mOutputStream);
            }
        }
    }

    /**
     * Info about the saved image file.
     *
     * 已保存图像文件的相关信息。
     */
    class OutputFileResults {
        @Nullable
        private Uri mSavedUri;

        OutputFileResults(@Nullable Uri SavedUri) {
            this.mSavedUri = SavedUri;
        }

        /**
         * Returns the {@link Uri} of the saved file.
         *
         * <p> This field is only returned if the {@link OutputFileOptions} is backed by
         * {@link MediaStore} constructed with
         * <p>
         * {@link OutputFileOptions.Builder
         * #Builder(ContentResolver, Uri, ContentValues)}.
         *
         * 返回已保存文件的 {@link Uri}。
         *
         * <p> 仅当 {@link OutputFileOptions} 由 {@link MediaStore} 支持，并且是通过
         * {@link OutputFileOptions.Builder#Builder(ContentResolver, Uri, ContentValues)} 构造时，
         * 才会返回此字段。
         */
        @Nullable
        public Uri getSavedUri() {
            return mSavedUri;
        }
    }

    /**
     * Describes the error that occurred during an image capture operation
     *
     * 描述图像拍摄操作期间发生的错误
     */
    @IntDef({ERROR_UNKNOWN, ERROR_FILE_IO, ERROR_CAPTURE_FAILED,
            ERROR_CAMERA_CLOSED, ERROR_INVALID_CAMERA})
    @Retention(RetentionPolicy.SOURCE)
    @interface ImageCaptureError {
    }

    /**
     * Capture strategy options for ImageCapture.
     *
     * ImageCapture 的拍摄策略选项。
     */
    @IntDef({CAPTURE_STRATEGY_OPENGL_ES, CAPTURE_STRATEGY_IMAGE_READER})
    @Retention(RetentionPolicy.SOURCE)
    @interface CaptureStrategy {
    }

    /**
     * Capture mode options for ImageCapture.
     *
     * ImageCapture 的拍摄模式选项。
     */
    @IntDef({CAPTURE_MODE_MAXIMIZE_QUALITY, CAPTURE_MODE_MINIMIZE_LATENCY})
    @Retention(RetentionPolicy.SOURCE)
    @interface CaptureMode {
    }

    /**
     * Listener containing callbacks for image file I/O events.
     *
     * 包含图像文件 I/O 事件回调的监听器。
     */
    interface OnImageCaptureCallback {

        /**
         * Called when an image has been successfully saved.
         *
         * 在图像成功保存后调用。
         */
        void onImageSaved(@NonNull OutputFileResults outputFileResults);

        /**
         * Called when an error occurs while attempting to save an image.
         *
         * 在尝试保存图像时发生错误时调用。
         */
        void onError(@ImageCaptureError int imageCaptureError, @NonNull String message,
                     @Nullable Throwable cause);
    }
}
