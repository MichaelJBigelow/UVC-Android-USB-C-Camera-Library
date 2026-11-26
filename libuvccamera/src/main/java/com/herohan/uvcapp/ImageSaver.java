package com.herohan.uvcapp;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Encodes a captured picture as JPEG and saves it to the requested destination.
 *
 * 将拍摄的照片编码为 JPEG，并保存到指定的目标位置。
 */
public class ImageSaver implements Runnable {
    private static final String TAG = ImageSaver.class.getSimpleName();

    private static final int PENDING = 1;
    private static final int NOT_PENDING = 0;

    /**
     * Encodes the captured picture as JPEG at the given quality.
     *
     * 以给定的质量将拍摄的照片编码为 JPEG。
     */
    private interface JpegEncoder {
        byte[] encode(int jpegQuality) throws Exception;
    }

    // Encodes the image that was captured
    // 编码捕获到的图像
    private final JpegEncoder mEncoder;
    // The compression quality level of the output JPEG image
    // 输出 JPEG 图像的压缩质量等级
    private final int mJpegQuality;
    // The target location to save the image to.
    // 图像的保存目标位置。
    @NonNull
    private final IImageCapture.OutputFileOptions mOutputFileOptions;
    // The callback to call on completion
    // 完成时调用的回调
    @NonNull
    private final OnImageSavedCallback mCallback;

    /**
     * Saves a picture read back from the renderer, as RGBA pixels.
     *
     * 保存从渲染器读回的照片(RGBA 像素)。
     */
    public ImageSaver(ImageRawData image,
                      @IntRange(from = 1, to = 100) int jpegQuality,
                      @NonNull IImageCapture.OutputFileOptions outputFileOptions,
                      @NonNull OnImageSavedCallback callback) {
        this(quality -> rawDataToJpeg(image, quality), jpegQuality, outputFileOptions, callback);
    }

    /**
     * Saves the top-left [width] x [height] pixels of a bitmap read from an ImageReader, whose rows
     * may be padded. The bitmap is recycled once it is encoded.
     *
     * 保存从 ImageReader 读取的位图左上角 [width] x [height] 的像素(其每行可能带有填充)。
     * 位图在编码后会被回收。
     */
    public ImageSaver(Bitmap image, int width, int height,
                      @IntRange(from = 1, to = 100) int jpegQuality,
                      @NonNull IImageCapture.OutputFileOptions outputFileOptions,
                      @NonNull OnImageSavedCallback callback) {
        this(quality -> bitmapToJpeg(image, width, height, quality), jpegQuality, outputFileOptions, callback);
    }

    private ImageSaver(JpegEncoder encoder,
                       @IntRange(from = 1, to = 100) int jpegQuality,
                       @NonNull IImageCapture.OutputFileOptions outputFileOptions,
                       @NonNull OnImageSavedCallback callback) {
        this.mEncoder = encoder;
        this.mJpegQuality = jpegQuality;
        this.mOutputFileOptions = outputFileOptions;
        this.mCallback = callback;
    }

    @Override
    public void run() {
        SaveError saveError = null;
        String errorMessage = null;
        Exception exception = null;
        Uri outputUri = null;

        try {
            byte[] data = encodeJpeg();
            if (data == null) {
                saveError = SaveError.ENCODE_FAILED;
                errorMessage = "Failed to encode mImage";
            } else {
                if (isSaveToMediaStore()) {
                    ContentValues values = mOutputFileOptions.getContentValues() != null
                            ? new ContentValues(mOutputFileOptions.getContentValues())
                            : new ContentValues();
                    setContentValuePending(values, PENDING);
                    outputUri = mOutputFileOptions.getContentResolver().insert(
                            mOutputFileOptions.getSaveCollection(),
                            values);
                    if (outputUri == null) {
                        saveError = SaveError.FILE_IO_FAILED;
                        errorMessage = "Failed to insert URI.";
                    } else {
                        // Only true once the image is written and published
                        // 仅在图片写入并发布之后才为 true
                        boolean saved = false;
                        try {
                            if (copyByteArrayToUri(data, outputUri)) {
                                setUriNotPending(outputUri);
                                saved = true;
                            } else {
                                saveError = SaveError.FILE_IO_FAILED;
                                errorMessage = "Failed to save to URI.";
                            }
                        } finally {
                            if (!saved) {
                                // Delete the pending entry instead of publishing an empty or broken image
                                // 删除待处理的条目，而不是发布一张空白或损坏的图片
                                deleteUri(outputUri);
                            }
                        }
                    }
                } else if (isSaveToOutputStream()) {
                    mOutputFileOptions.getOutputStream().write(data);
                } else if (isSaveToFile()) {
                    File targetFile = mOutputFileOptions.getFile();
                    copyByteArrayToFile(data, targetFile);
                    outputUri = Uri.fromFile(targetFile);
                }
            }
        } catch (IOException e) {
            saveError = SaveError.FILE_IO_FAILED;
            errorMessage = "Failed to write destination file.";
            exception = e;
        } catch (RuntimeException e) {
            // e.g. SecurityException or IllegalArgumentException from ContentResolver
            // 例如 ContentResolver 抛出的 SecurityException 或 IllegalArgumentException
            saveError = SaveError.UNKNOWN;
            errorMessage = "Failed to save image.";
            exception = e;
        }

        if (saveError != null) {
            mCallback.onError(saveError, errorMessage, exception);
        } else {
            mCallback.onImageSaved(new IImageCapture.OutputFileResults(outputUri));
        }
    }

    /**
     * @return the JPEG data, or null if encoding failed / JPEG 数据；编码失败时返回 null
     */
    private byte[] encodeJpeg() {
        try {
            return mEncoder.encode(mJpegQuality);
        } catch (Exception e) {
            Log.e(TAG, "failed to save file", e);
        }
        return null;
    }

    private static byte[] rawDataToJpeg(ImageRawData image, int jpegQuality) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final Bitmap bmp = Bitmap.createBitmap(
                    image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(image.getData()));
                bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
            } finally {
                bmp.recycle();
            }
            return out.toByteArray();
        }
    }

    private static byte[] bitmapToJpeg(Bitmap image, int width, int height, int jpegQuality) throws IOException {
        Bitmap bmp = null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            bmp = Bitmap.createBitmap(image, 0, 0, width, height);
            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
            return out.toByteArray();
        } finally {
            if (bmp != null && bmp != image) {
                bmp.recycle();
            }
            image.recycle();
        }
    }

    private boolean isSaveToMediaStore() {
        return mOutputFileOptions.getSaveCollection() != null
                && mOutputFileOptions.getContentResolver() != null
                && mOutputFileOptions.getContentValues() != null;
    }

    private boolean isSaveToFile() {
        return mOutputFileOptions.getFile() != null;
    }

    private boolean isSaveToOutputStream() {
        return mOutputFileOptions.getOutputStream() != null;
    }

    /**
     * Set IS_PENDING flag to {@link ContentValues}.
     *
     * 为 {@link ContentValues} 设置 IS_PENDING 标志。
     */
    private void setContentValuePending(@NonNull ContentValues values, int isPending) {
        values.put(MediaStore.Images.Media.IS_PENDING, isPending);
    }

    /**
     * Removes IS_PENDING flag during the writing to {@link Uri}.
     *
     * 在写入 {@link Uri} 的过程中移除 IS_PENDING 标志。
     */
    private void setUriNotPending(@NonNull Uri outputUri) {
        ContentValues values = new ContentValues();
        setContentValuePending(values, NOT_PENDING);
        mOutputFileOptions.getContentResolver().update(outputUri, values, null, null);
    }

    private void deleteUri(@NonNull Uri outputUri) {
        try {
            mOutputFileOptions.getContentResolver().delete(outputUri, null, null);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to delete " + outputUri, e);
        }
    }

    /**
     * Copies  byte array to {@link Uri}.
     *
     * 将字节数组复制到 {@link Uri}。
     *
     * @return false if the {@link Uri} is not writable. / 如果 {@link Uri} 不可写则返回 false。
     */
    private boolean copyByteArrayToUri(@NonNull byte[] data, @NonNull Uri uri) throws IOException {
        try (OutputStream outputStream =
                     mOutputFileOptions.getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                // The URI is not writable.
                // 该 URI 不可写。
                return false;
            }
            outputStream.write(data);
        }
        return true;
    }

    /**
     * Copies  byte array to {@link File}.
     *
     * 将字节数组复制到 {@link File}。
     *
     * @return false if the {@link File} is not writable. / 如果 {@link File} 不可写则返回 false。
     */
    private boolean copyByteArrayToFile(@NonNull byte[] data, @NonNull File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(data);
        }
        return true;
    }

    /**
     * Type of error that occurred during save
     *
     * 保存过程中发生的错误类型
     */
    public enum SaveError {
        /**
         * Failed to write to or close the file
         *
         * 写入或关闭文件失败
         */
        FILE_IO_FAILED,
        /**
         * Failure when attempting to encode image
         *
         * 尝试编码图像时失败
         */
        ENCODE_FAILED,
        UNKNOWN
    }

    public interface OnImageSavedCallback {

        void onImageSaved(@NonNull IImageCapture.OutputFileResults outputFileResults);

        void onError(@NonNull SaveError saveError, @NonNull String message,
                     @Nullable Throwable cause);
    }
}
