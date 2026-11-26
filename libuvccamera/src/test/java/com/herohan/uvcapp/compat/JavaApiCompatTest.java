package com.herohan.uvcapp.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.os.Environment;

import com.herohan.uvcapp.CameraException;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.CameraPreviewConfig;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.IImageCapture;
import com.herohan.uvcapp.ImageCapture;
import com.herohan.uvcapp.ImageCapture2;
import com.herohan.uvcapp.VideoCapture;
import com.herohan.uvcapp.VideoCaptureConfig;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCControl;
import com.serenegiant.utils.FileUtils;
import com.serenegiant.utils.HandlerThreadHandler;
import com.serenegiant.utils.Time;
import com.serenegiant.utils.UVCUtils;
import com.serenegiant.utils.UriHelper;

import org.junit.Test;

import java.io.File;
import java.util.List;

/**
 * Checks that Java callers can still use the library the way they did before it was written in
 * Kotlin. It sits outside the library's packages, like an app would. Most of the check is that this
 * file compiles; {@link #useTheApi} is never run, since the Android classes it calls only exist as
 * stubs in unit tests.
 *
 * 检查 Java 调用方仍可像库改用 Kotlin 编写之前那样使用它。它与应用一样位于库的包之外。
 * 主要的检查是本文件能够编译；{@link #useTheApi} 不会被执行，因为它调用的 Android 类在单元测试中只有桩实现。
 */
public class JavaApiCompatTest {

    @Test
    public void constantsKeepTheirJavaNamesAndValues() {
        assertEquals(1, CameraException.CAMERA_OPEN_ERROR_UNKNOWN);
        assertEquals(2, CameraException.CAMERA_OPEN_ERROR_BUSY);

        // Constants of IImageCapture are still reachable through the capture classes.
        // IImageCapture 的常量仍可通过拍照类访问。
        assertEquals(4, ImageCapture.ERROR_INVALID_CAMERA);
        assertEquals(1, ImageCapture2.CAPTURE_STRATEGY_IMAGE_READER);
        assertEquals(95, IImageCapture.JPEG_QUALITY_MINIMIZE_LATENCY_MODE);

        assertEquals(5, VideoCapture.ERROR_INVALID_CAMERA);
        assertEquals(6, VideoCapture.ERROR_RECORDING_TOO_SHORT);
        assertEquals(0, CameraPreviewConfig.DEFAULT_ROTATION);
    }

    @Test
    public void timeStaysStatic() {
        Time.prohibitElapsedRealtimeNanos = true;
        Time.reset();
        long before = System.nanoTime();
        assertTrue(Time.nanoTime() >= before);
    }

    @SuppressWarnings("unused")
    private static void useTheApi(ICameraHelper helper, Context context, UsbDevice device) {
        List<UsbDevice> devices = helper.getDeviceList();
        helper.selectDevice(device);
        List<Format> formats = helper.getSupportedFormatList();
        List<Size> sizes = helper.getSupportedSizeList();
        Size size = helper.getPreviewSize();
        helper.setPreviewSize(size);
        UVCControl control = helper.getUVCControl();
        boolean recording = helper.isRecording();
        boolean opened = helper.isCameraOpened();
        helper.openCamera(size);
        helper.removeSurface(new Object(), () -> { });

        helper.setPreviewConfig(helper.getPreviewConfig().setRotation(90).setMirror(0));
        int rotation = helper.getPreviewConfig().getRotation();
        helper.setImageCaptureConfig(helper.getImageCaptureConfig()
                .setCaptureStrategy(ImageCapture.CAPTURE_STRATEGY_IMAGE_READER)
                .setJpegCompressionQuality(90));
        VideoCaptureConfig videoConfig = helper.getVideoCaptureConfig().setBitRate(1_000_000);
        int interval = videoConfig.getIFrameInterval();
        boolean audio = videoConfig.getAudioCaptureEnable();
        helper.setVideoCaptureConfig(videoConfig);

        File file = new File("picture.jpg");
        ImageCapture.OutputFileOptions imageOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        helper.takePicture(imageOptions, new ImageCapture.OnImageCaptureCallback() {
            @Override
            public void onImageSaved(IImageCapture.OutputFileResults outputFileResults) {
                outputFileResults.getSavedUri();
            }

            @Override
            public void onError(int imageCaptureError, String message, Throwable cause) {
            }
        });

        VideoCapture.OutputFileOptions videoOptions = new VideoCapture.OutputFileOptions.Builder(file).build();
        helper.startRecording(videoOptions, new VideoCapture.OnVideoCaptureCallback() {
            @Override
            public void onStart() {
            }

            @Override
            public void onVideoSaved(VideoCapture.OutputFileResults outputFileResults) {
                outputFileResults.getSavedUri();
            }

            @Override
            public void onError(int videoCaptureError, String message, Throwable cause) {
            }
        });

        // onError keeps its default implementation, so it needn't be overridden.
        // onError 保留其默认实现，因此无需重写。
        helper.setStateCallback(new ICameraHelper.StateCallback() {
            @Override
            public void onAttach(UsbDevice device) {
            }

            @Override
            public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            }

            @Override
            public void onCameraOpen(UsbDevice device) {
            }

            @Override
            public void onCameraClose(UsbDevice device) {
            }

            @Override
            public void onDeviceClose(UsbDevice device) {
            }

            @Override
            public void onDetach(UsbDevice device) {
            }

            @Override
            public void onCancel(UsbDevice device) {
            }
        });
        ICameraHelper concrete = new CameraHelper();
        concrete.release();

        UVCUtils.init(context);
        long now = Time.nanoTime();
        HandlerThreadHandler handler = HandlerThreadHandler.createHandler("compat");
        File capture = FileUtils.getCaptureFile(context, Environment.DIRECTORY_DCIM, ".jpg");
        String path = UriHelper.getPath(context, null);
        String[] directories = UriHelper.STANDARD_DIRECTORIES;
    }
}
