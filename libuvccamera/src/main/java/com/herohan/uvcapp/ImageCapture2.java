package com.herohan.uvcapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;

import androidx.annotation.NonNull;

import com.serenegiant.usb.Size;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Takes pictures by attaching an {@link ImageReader} to the camera
 * ({@link IImageCapture#CAPTURE_STRATEGY_IMAGE_READER}).
 *
 * 通过将 {@link ImageReader} 挂接到相机来拍照({@link IImageCapture#CAPTURE_STRATEGY_IMAGE_READER})。
 */
public class ImageCapture2 extends BaseImageCapture {

    private static final String TAG = ImageCapture2.class.getSimpleName();

    private final WeakReference<ICameraInternal> mCameraInternalWeak;
    private Size mResolution;

    private ImageReader mImageReader;

    ImageCapture2(ICameraInternal cameraInternal,
                  ImageCaptureConfig config,
                  Size resolution) {
        super(config, TAG);
        this.mCameraInternalWeak = new WeakReference<>(cameraInternal);
        this.mResolution = resolution;

        initImageReader();
    }

    @SuppressLint("WrongConstant")
    private void initImageReader() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mResolution != null) {
            mImageReader = ImageReader.newInstance(mResolution.width, mResolution.height, PixelFormat.RGBA_8888, 1);
        }
    }

    void setResolution(Size resolution) {
        this.mResolution = resolution;
        initImageReader();
    }

    void clearImageReader() {
        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(null, null);

            ICameraInternal cameraInternal = mCameraInternalWeak.get();
            if (cameraInternal != null) {
                cameraInternal.removeSurface(mImageReader.getSurface());
            }
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
        ImageSaver.OnImageSavedCallback imageSavedCallbackWrapper = wrapCallback(imageSavedCallback);

        ICameraInternal cameraInternal = mCameraInternalWeak.get();

        if (cameraInternal == null) {
            imageSavedCallback.onError(ERROR_INVALID_CAMERA,
                    "Not bound to a Camera", null);
        } else if (mImageReader == null) {
            imageSavedCallback.onError(ERROR_UNKNOWN,
                    "ImageReader is null", null);
        } else {
            int outputJpegQuality = getJpegQualityInternal();
            int width = mResolution.width;
            int height = mResolution.height;

            cameraInternal.addSurface(mImageReader.getSurface(), false);
            mImageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = mImageReader.acquireLatestImage();
                    if (image != null) {
                        final Image.Plane[] planes = image.getPlanes();
                        if (planes.length > 0) {
                            // Since we requested RGBA data, everything is stored in planes[0]
                            // 因为我们要求的是RGBA格式的数据，所以全部的存储在planes[0]中
                            Image.Plane plane = planes[0];
                            final ByteBuffer buffer = plane.getBuffer();
                            // Because the buffer in the Image is data-aligned, its size is not necessarily the size specified when the ImageReader instance was created.
                            // ImageReader automatically adds padding to the right side of each row for alignment, and the number of aligned bytes may vary by hardware,
                            // so this padding data must be ignored when reading the data out.
                            // 由于Image中的缓冲区存在数据对齐，所以其大小不一定是我们生成ImageReader实例时指定的大小，
                            // ImageReader会自动为画面每一行最右侧添加一个padding，以进行对齐，对齐多少字节可能因硬件而异，
                            // 所以我们在取出数据时需要忽略这一部分数据。
                            int pixelStride = plane.getPixelStride();
                            int rowStride = plane.getRowStride();
                            int rowPadding = rowStride - pixelStride * width;

                            // create bitmap
                            // 创建 bitmap
                            Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride,
                                    height, Bitmap.Config.ARGB_8888);
                            bmp.copyPixelsFromBuffer(buffer);

                            mExecutor.execute(new ImageSaver(bmp, width, height, outputJpegQuality, outputFileOptions,
                                    imageSavedCallbackWrapper));
                        }
                    }

                } catch (Exception e) {
                    String message = e.getMessage();
                    if (message == null) {
                        message = "captureImage failed with an unknown exception";
                    }
                    imageSavedCallback.onError(ERROR_UNKNOWN, message, e);
                } finally {
                    if (image != null) {
                        image.close();
                    }

                    clearImageReader();
                    initImageReader();
                }
            }, null);
        }
    }
}
