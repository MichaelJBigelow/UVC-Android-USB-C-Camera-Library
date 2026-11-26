package com.herohan.uvcapp

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.HandlerThread
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serenegiant.usb.DeviceFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.serenegiant.uvccamera.R as LibraryR

/**
 * Checks what [CameraHelper] does before a camera is selected: it reports no camera, fails captures
 * on the main thread instead of hanging, and rejects surfaces it can't draw on. No test selects a
 * device, so they pass with or without a USB camera attached.
 *
 * 检查 [CameraHelper] 在选择相机之前的行为：报告没有相机，在主线程上让拍摄失败而不是挂起，
 * 并拒绝无法绘制的 surface。所有测试都不选择设备，因此无论是否连接 USB 相机都能通过。
 */
@RunWith(AndroidJUnit4::class)
class CameraHelperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var helper: CameraHelper

    @Before
    fun setUp() {
        helper = CameraHelper()
    }

    @After
    fun tearDown() {
        helper.release()
    }

    @Test
    fun withoutADevice_reportsNoCamera() {
        assertFalse(helper.isCameraOpened)
        assertFalse(helper.isRecording)
        assertNull(helper.previewSize)
        assertNull(helper.supportedSizeList)
        assertNull(helper.supportedFormatList)
        assertNull(helper.getUVCControl())
    }

    @Test
    fun withACallback_listsOnlyDevicesTheLibraryFilterAccepts() {
        helper.setStateCallback(NoOpStateCallback())

        // An emulator has no USB devices, so the list is empty there; on a phone with other USB
        // devices attached, those must be left out.
        // 模拟器没有 USB 设备，因此在其上列表为空；在连接了其他 USB 设备的手机上，这些设备必须被排除。
        val filters = DeviceFilter.getDeviceFilters(context, LibraryR.xml.device_filter)
        val devices = helper.deviceList!!
        devices.forEach { device ->
            assertTrue(device.deviceName, filters.any { !it.isExclude && it.matches(device) })
        }

        helper.setStateCallback(null)
    }

    @Test
    fun takePicture_withoutADevice_failsOnTheMainThread() {
        val error = AtomicInteger(-1)
        val onMainThread = AtomicBoolean()
        val done = CountDownLatch(1)
        val options = IImageCapture.OutputFileOptions.Builder(tempFile("picture.jpg")).build()

        helper.takePicture(options, object : IImageCapture.OnImageCaptureCallback {
            override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                done.countDown()
            }

            override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
                error.set(imageCaptureError)
                onMainThread.set(Looper.myLooper() == Looper.getMainLooper())
                done.countDown()
            }
        })

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(IImageCapture.ERROR_CAMERA_CLOSED, error.get())
        assertTrue(onMainThread.get())
    }

    @Test
    fun startRecording_withoutADevice_failsOnTheMainThread() {
        val error = AtomicInteger(-1)
        val onMainThread = AtomicBoolean()
        val done = CountDownLatch(1)
        val options = VideoCapture.OutputFileOptions.Builder(tempFile("video.mp4")).build()

        helper.startRecording(options, object : VideoCapture.OnVideoCaptureCallback {
            override fun onStart() {}

            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                done.countDown()
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                error.set(videoCaptureError)
                onMainThread.set(Looper.myLooper() == Looper.getMainLooper())
                done.countDown()
            }
        })

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(VideoCapture.ERROR_INVALID_CAMERA, error.get())
        assertTrue(onMainThread.get())
    }

    @Test
    fun addSurface_rejectsWhatItCantDrawOn() {
        assertThrows(IllegalArgumentException::class.java) { helper.addSurface(Any(), false) }
        assertThrows(IllegalArgumentException::class.java) { helper.removeSurface("not a surface") }
    }

    @Test
    fun removeSurface_afterRelease_stillReportsTheRemoval() {
        val texture = SurfaceTexture(false)
        try {
            val onMainThread = AtomicBoolean()
            val removed = CountDownLatch(1)

            helper.release()
            awaitHelperThreadStopped()
            helper.removeSurface(texture) {
                onMainThread.set(Looper.myLooper() == Looper.getMainLooper())
                removed.countDown()
            }

            assertTrue(removed.await(5, TimeUnit.SECONDS))
            assertTrue(onMainThread.get())
        } finally {
            texture.release()
        }
    }

    @Test
    fun selectDevice_ignoresNull() {
        helper.selectDevice(null)

        assertFalse(helper.isCameraOpened)
    }

    @Test
    fun keepsSettingsSetBeforeACameraOpens() {
        helper.previewConfig = helper.previewConfig.setRotation(90)
        helper.imageCaptureConfig = helper.imageCaptureConfig.setJpegCompressionQuality(70)
        helper.videoCaptureConfig = helper.videoCaptureConfig.setAudioCaptureEnable(false)

        assertEquals(90, helper.previewConfig.rotation)
        assertEquals(70, helper.imageCaptureConfig.jpegCompressionQuality)
        assertFalse(helper.videoCaptureConfig.audioCaptureEnable)
    }

    /**
     * Waits until release() has stopped the helper's own thread, so nothing can be posted to it any
     * more. The thread is private, so it is read by reflection.
     *
     * 等待 release() 停止 helper 自身的线程，使其不再能接收投递。该线程是私有的，因此通过反射读取。
     */
    private fun awaitHelperThreadStopped() {
        val field = CameraHelper::class.java.getDeclaredField("mAsyncHandlerThread")
        field.isAccessible = true
        val thread = field.get(helper) as HandlerThread
        thread.join(5_000)
        assertFalse("the helper thread has stopped", thread.isAlive)
    }

    private fun tempFile(name: String): File = File(context.cacheDir, name)

    private class NoOpStateCallback : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {}
        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {}
        override fun onCameraOpen(device: UsbDevice) {}
        override fun onCameraClose(device: UsbDevice) {}
        override fun onDeviceClose(device: UsbDevice) {}
        override fun onDetach(device: UsbDevice) {}
        override fun onCancel(device: UsbDevice) {}
    }
}
