package com.herohan.uvcapp.activity

import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.google.gson.Gson
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcapp.IImageCapture
import com.herohan.uvcapp.R
import com.herohan.uvcapp.VideoCapture
import com.herohan.uvcapp.ui.MainMenuAction
import com.herohan.uvcapp.ui.MainScreen
import com.herohan.uvcapp.ui.dialog.CameraControlsDialog
import com.herohan.uvcapp.ui.dialog.DeviceListDialog
import com.herohan.uvcapp.ui.dialog.VideoFormatDialog
import com.herohan.uvcapp.ui.theme.UvcAppTheme
import com.herohan.uvcapp.ui.theme.enableEdgeToEdgeBelowAppBar
import com.herohan.uvcapp.utils.SaveHelper
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.serenegiant.opengl.renderer.MirrorMode
import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCControl
import com.serenegiant.utils.UriHelper
import java.io.File

class MainActivity : ComponentActivity() {

    private var cameraHelper: ICameraHelper? = null

    private var usbDevice: UsbDevice? = null

    private var previewRotation = 0

    /**
     * Surface of the preview view, while it exists.
     *
     * 预览视图的 surface(存在期间有效)。
     */
    private var previewSurface: SurfaceTexture? = null

    // UI state, read by the composables in setContent.
    // UI 状态，由 setContent 中的 composable 读取。
    private var isCameraConnected by mutableStateOf(false)
    private var isRecording by mutableStateOf(false)

    /** Camera preview width / 相机预览宽度 */
    private var previewWidth by mutableIntStateOf(DEFAULT_WIDTH)

    /** Camera preview height / 相机预览高度 */
    private var previewHeight by mutableIntStateOf(DEFAULT_HEIGHT)

    /**
     * [SystemClock.elapsedRealtime] when recording started, or null when not recording.
     *
     * 开始录制时的 [SystemClock.elapsedRealtime]；未在录制时为 null。
     */
    private var recordStartTime by mutableStateOf<Long?>(null)

    private var openDialog by mutableStateOf<MainDialog?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        initCameraHelper()

        setContent {
            UvcAppTheme {
                MainScreen(
                    isCameraConnected = isCameraConnected,
                    isRecording = isRecording,
                    previewWidth = previewWidth,
                    previewHeight = previewHeight,
                    recordStartTime = recordStartTime,
                    onSurfaceAvailable = ::onPreviewSurfaceAvailable,
                    onSurfaceDestroyed = ::onPreviewSurfaceDestroyed,
                    onMenuAction = ::onMenuAction,
                    onTakePicture = ::requestTakePicture,
                    onToggleRecord = ::requestToggleVideoRecord,
                )
                MainDialogs()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED && !isCameraConnected) {
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            if (device != null) {
                usbDevice = device
                selectDevice(device)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            toggleVideoRecord(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCameraHelper()
    }

    @Composable
    private fun MainDialogs() {
        val dismiss = { openDialog = null }
        when (val dialog = openDialog) {
            is MainDialog.CameraControls -> CameraControlsDialog(dialog.control, onDismiss = dismiss)
            is MainDialog.DeviceList -> DeviceListDialog(
                devices = dialog.devices,
                selectedDevice = dialog.selectedDevice,
                onDeviceSelected = ::onDeviceSelected,
                onDismiss = dismiss,
            )
            is MainDialog.VideoFormat -> VideoFormatDialog(
                formats = dialog.formats,
                currentSize = dialog.size,
                onFormatSelected = ::onVideoFormatSelected,
                onDismiss = dismiss,
            )
            null -> Unit
        }
    }

    private fun onMenuAction(action: MainMenuAction) {
        when (action) {
            MainMenuAction.CameraControls -> showCameraControlsDialog()
            MainMenuAction.Device -> showDeviceListDialog()
            MainMenuAction.SafelyEject -> cameraHelper?.closeCamera()
            MainMenuAction.VideoFormat -> showVideoFormatDialog()
            MainMenuAction.Rotate90Cw -> rotateBy(90)
            MainMenuAction.Rotate90Ccw -> rotateBy(-90)
            MainMenuAction.FlipHorizontally -> setMirror(MirrorMode.MIRROR_HORIZONTAL)
            MainMenuAction.FlipVertically -> setMirror(MirrorMode.MIRROR_VERTICAL)
        }
    }

    private fun showCameraControlsDialog() {
        val control = cameraHelper?.uvcControl ?: return
        openDialog = MainDialog.CameraControls(control)
    }

    private fun showDeviceListDialog() {
        val helper = cameraHelper ?: return
        openDialog = MainDialog.DeviceList(
            devices = helper.deviceList.orEmpty(),
            selectedDevice = if (isCameraConnected) usbDevice else null,
        )
    }

    private fun onDeviceSelected(device: UsbDevice) {
        if (isCameraConnected) {
            cameraHelper?.closeCamera()
        }
        usbDevice = device
        selectDevice(device)
    }

    private fun showVideoFormatDialog() {
        val helper = cameraHelper ?: return
        val formats = helper.supportedFormatList ?: return
        val size = helper.previewSize ?: return
        openDialog = MainDialog.VideoFormat(formats, size)
    }

    private fun onVideoFormatSelected(size: Size) {
        val helper = cameraHelper ?: return
        if (isCameraConnected && !helper.isRecording) {
            helper.stopPreview()
            helper.previewSize = size
            helper.startPreview()
            resizePreviewView(size)
            // save selected preview size
            // 保存所选的预览尺寸
            setSavedPreviewSize(size)
        }
    }

    private fun rotateBy(angle: Int) {
        previewRotation = ((previewRotation + angle) % 360 + 360) % 360

        cameraHelper?.let { it.previewConfig = it.previewConfig.setRotation(previewRotation) }
    }

    private fun setMirror(mirrorMode: Int) {
        cameraHelper?.let { it.previewConfig = it.previewConfig.setMirror(mirrorMode) }
    }

    private fun initCameraHelper() {
        if (cameraHelper == null) {
            cameraHelper = CameraHelper().apply {
                setStateCallback(stateCallback)
                setCustomImageCaptureConfig(this)
                setCustomVideoCaptureConfig(this)
            }
        }
    }

    private fun clearCameraHelper() {
        if (DEBUG) Log.v(TAG, "clearCameraHelper:")
        cameraHelper?.release()
        cameraHelper = null
    }

    private fun onPreviewSurfaceAvailable(surface: SurfaceTexture) {
        previewSurface = surface
        cameraHelper?.addSurface(surface, false)
    }

    private fun onPreviewSurfaceDestroyed(surface: SurfaceTexture) {
        // onCameraClose may have removed it already.
        // onCameraClose 可能已将其移除。
        if (previewSurface === surface) {
            cameraHelper?.removeSurface(surface)
            previewSurface = null
        }
    }

    private fun attachNewDevice(device: UsbDevice) {
        if (usbDevice == null) {
            usbDevice = device
            selectDevice(device)
        }
    }

    /**
     * In Android9+, connected to the UVC CAMERA, CAMERA permission is required
     *
     * 在 Android 9+ 上，连接 UVC 相机需要 CAMERA 权限
     */
    private fun selectDevice(device: UsbDevice) {
        if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.deviceName)

        XXPermissions.with(this)
            .permission(PermissionLists.getCameraPermission())
            .request { grantedList, _ ->
                if (grantedList.isEmpty()) {
                    return@request
                }
                isCameraConnected = false

                // Try to obtain device permission through the UsbDevice object
                // 通过UsbDevice对象，尝试获取设备权限
                cameraHelper?.selectDevice(device)
            }
    }

    private val stateCallback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onAttach:device=" + device.deviceName)

            attachNewDevice(device)
        }

        /**
         * After obtaining USB device permissions, connect the USB camera
         *
         * 获取 USB 设备权限后，连接 USB 相机
         */
        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:device=" + device.deviceName)

            val helper = cameraHelper ?: return
            helper.openCamera(getSavedPreviewSize())
            helper.setButtonCallback(IButtonCallback { button, state ->
                Toast.makeText(
                    this@MainActivity,
                    "onButton(button=$button; state=$state)",
                    Toast.LENGTH_SHORT,
                ).show()
            })
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:device=" + device.deviceName)

            val helper = cameraHelper ?: return
            helper.startPreview()

            // After connecting to the camera, you can get preview size of the camera
            // 连接相机后，即可获取相机的预览尺寸
            helper.previewSize?.let { resizePreviewView(it) }

            previewSurface?.let { helper.addSurface(it, false) }

            isCameraConnected = true
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraClose:device=" + device.deviceName)

            if (isRecording) {
                toggleVideoRecord(false)
            }

            previewSurface?.let { cameraHelper?.removeSurface(it) }
            previewSurface = null

            isCameraConnected = false
            openDialog = null
        }

        override fun onDeviceClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:device=" + device.deviceName)
        }

        override fun onDetach(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onDetach:device=" + device.deviceName)

            if (device == usbDevice) {
                usbDevice = null
            }
        }

        override fun onCancel(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCancel:device=" + device.deviceName)

            if (device == usbDevice) {
                usbDevice = null
            }
        }
    }

    private fun resizePreviewView(size: Size) {
        // Update the preview size; the preview keeps the camera's aspect ratio
        // 更新预览尺寸；预览会保持与相机一致的宽高比
        previewWidth = size.width
        previewHeight = size.height
    }

    private fun savedPreviewSizeKey(): String =
        getString(R.string.saved_preview_size) + USBMonitor.getProductKey(usbDevice)

    private fun getSavedPreviewSize(): Size? {
        val sizeStr = getPreferences(MODE_PRIVATE).getString(savedPreviewSizeKey(), null)
        if (sizeStr.isNullOrEmpty()) {
            return null
        }
        return Gson().fromJson(sizeStr, Size::class.java)
    }

    private fun setSavedPreviewSize(size: Size) {
        getPreferences(MODE_PRIVATE)
            .edit()
            .putString(savedPreviewSizeKey(), Gson().toJson(size))
            .apply()
    }

    private fun setCustomImageCaptureConfig(helper: ICameraHelper) {
//        helper.imageCaptureConfig =
//            helper.imageCaptureConfig.setCaptureMode(IImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        helper.imageCaptureConfig = helper.imageCaptureConfig.setJpegCompressionQuality(90)
    }

    private fun setCustomVideoCaptureConfig(helper: ICameraHelper) {
        helper.videoCaptureConfig = helper.videoCaptureConfig
//            .setAudioCaptureEnable(false)
            .setBitRate((1024 * 1024 * 25 * 0.25).toInt())
            .setVideoFrameRate(25)
            .setIFrameInterval(1)
    }

    /**
     * Saving a picture needs storage permission. XXPermissions reports every result, granted or
     * denied, so act only when at least one permission was granted.
     *
     * 保存照片需要存储权限。XXPermissions 会回调每次请求结果(无论授予与否)，
     * 因此仅在至少授予一项权限时执行操作。
     */
    private fun requestTakePicture() {
        XXPermissions.with(this)
            .permission(PermissionLists.getManageExternalStoragePermission())
            .request { grantedList, _ ->
                if (grantedList.isNotEmpty()) {
                    takePicture()
                }
            }
    }

    /**
     * Recording needs storage and microphone permissions; see [requestTakePicture].
     *
     * 录像需要存储和麦克风权限；参见 [requestTakePicture]。
     */
    private fun requestToggleVideoRecord() {
        XXPermissions.with(this)
            .permission(PermissionLists.getManageExternalStoragePermission())
            .permission(PermissionLists.getRecordAudioPermission())
            .request { grantedList, _ ->
                if (grantedList.isNotEmpty()) {
                    toggleVideoRecord(!isRecording)
                }
            }
    }

    private fun takePicture() {
        if (isRecording) {
            return
        }
        val helper = cameraHelper ?: return

        try {
            val file = File(SaveHelper.getSavePhotoPath())
            val options = IImageCapture.OutputFileOptions.Builder(file).build()
            helper.takePicture(options, object : IImageCapture.OnImageCaptureCallback {
                override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                    showSavedToast(outputFileResults.savedUri)
                }

                override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage, e)
        }
    }

    private fun toggleVideoRecord(record: Boolean) {
        val helper = cameraHelper
        try {
            if (record) {
                if (isCameraConnected && helper != null && !helper.isRecording) {
                    startRecord(helper)
                }
            } else {
                if (isCameraConnected && helper != null && helper.isRecording) {
                    helper.stopRecording()
                }
                recordStartTime = null
            }
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage, e)
            recordStartTime = null
        }

        isRecording = record
    }

    private fun startRecord(helper: ICameraHelper) {
        val file = File(SaveHelper.getSaveVideoPath())
        val options = VideoCapture.OutputFileOptions.Builder(file).build()
        helper.startRecording(options, object : VideoCapture.OnVideoCaptureCallback {
            override fun onStart() {
                recordStartTime = SystemClock.elapsedRealtime()
            }

            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                toggleVideoRecord(false)
                showSavedToast(outputFileResults.savedUri)
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                toggleVideoRecord(false)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showSavedToast(savedUri: android.net.Uri?) {
        Toast.makeText(
            this,
            "save \"" + UriHelper.getPath(this, savedUri) + "\"",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * The dialog shown over the preview, if any.
     *
     * 预览上方显示的对话框(如有)。
     */
    private sealed interface MainDialog {
        data class CameraControls(val control: UVCControl) : MainDialog
        data class DeviceList(val devices: List<UsbDevice>, val selectedDevice: UsbDevice?) : MainDialog
        data class VideoFormat(val formats: List<Format>, val size: Size) : MainDialog
    }

    private companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val DEBUG = true

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
