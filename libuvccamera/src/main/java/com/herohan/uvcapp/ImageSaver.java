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

public class ImageSaver implements Runnable {
    private static final String TAG = ImageSaver.class.getSimpleName();

    private static final int PENDING = 1;
    private static final int NOT_PENDING = 0;

    // The image that was captured
    // 捕获到的图像
    private final ImageRawData mImage;
    // The compression quality level of the output JPEG image
    // 输出 JPEG 图像的压缩质量等级
    private final int mJpegQuality;
    // The target location to save the image to.
    // 图像的保存目标位置。
    @NonNull
    private final ImageCapture.OutputFileOptions mOutputFileOptions;
    // The callback to call on completion
    // 完成时调用的回调
    @NonNull
    private final OnImageSavedCallback mCallback;

    public ImageSaver(ImageRawData image,
                      @IntRange(from = 1, to = 100) int jpegQuality,
                      @NonNull ImageCapture.OutputFileOptions outputFileOptions,
                      @NonNull OnImageSavedCallback callback) {
        this.mImage = image;
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
            byte[] data = imageToJpegByteArray(mImage, mJpegQuality);
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
                        if (!copyByteArrayToUri(data, outputUri)) {
                            saveError = SaveError.FILE_IO_FAILED;
                            errorMessage = "Failed to save to URI.";
                        }
                        setUriNotPending(outputUri);
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
        }

        if (saveError != null) {
            mCallback.onError(saveError, errorMessage, exception);
        } else {
            mCallback.onImageSaved(new ImageCapture.OutputFileResults(outputUri));
        }
    }

    private byte[] imageToJpegByteArray(ImageRawData image, int jpegQuality) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final Bitmap bmp = Bitmap.createBitmap(
                    image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(image.getData()));
            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
            bmp.recycle();
            return out.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "failed to save file", e);
        }
        return null;
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

        void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults);

        void onError(@NonNull SaveError saveError, @NonNull String message,
                     @Nullable Throwable cause);
    }
}
