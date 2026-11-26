package com.herohan.uvcapp;

import androidx.annotation.NonNull;

import com.herohan.uvcapp.ICameraRendererHolder.OnImageCapturedCallback;

import java.lang.ref.WeakReference;

/**
 * Takes pictures by reading the frame back from the renderer (the default capture strategy).
 *
 * 通过从渲染器读回画面来拍照(默认的拍照策略)。
 */
public class ImageCapture extends BaseImageCapture {

    private static final String TAG = ImageCapture.class.getSimpleName();

    private final WeakReference<ICameraRendererHolder> mRendererHolderWeak;

    ImageCapture(ICameraRendererHolder rendererHolder,
                 ImageCaptureConfig config) {
        super(config, TAG);
        this.mRendererHolderWeak = new WeakReference<>(rendererHolder);
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
        ImageSaver.OnImageSavedCallback imageSavedCallbackWrapper = wrapCallback(imageSavedCallback);

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
}
