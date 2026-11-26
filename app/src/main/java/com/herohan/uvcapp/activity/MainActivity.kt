package com.herohan.uvcapp.activity

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcapp.IImageCapture
import com.herohan.uvcapp.R
import com.herohan.uvcapp.VideoCapture
import com.herohan.uvcapp.ui.MainMenuAction
import com.herohan.uvcapp.ui.MainScreen
import com.herohan.uvcsample.CameraSession
import com.herohan.uvcsample.media.MediaStoreOutput
import com.herohan.uvcsample.ui.dialog.CameraControlsDialog
import com.herohan.uvcsample.ui.dialog.DeviceListDialog
import com.herohan.uvcsample.ui.dialog.VideoFormatDialog
import com.herohan.uvcsample.ui.theme.UvcSampleTheme
import com.herohan.uvcsample.ui.theme.enableEdgeToEdgeBelowAppBar
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.serenegiant.opengl.renderer.MirrorMode
import com.serenegiant.usb.Format
import com.serenegiant.usb.IButtonCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCControl
import com.herohan.uvcsample.R as SampleR

class MainActivity : ComponentActivity() {

    /**
     * The camera, kept from onCreate to onDestroy. The activity handles rotation itself
     * (configChanges in the manifest), so rotating keeps the camera, its settings and any open
     * dialog; in the background the camera stays open but stops streaming.
     *
     * 相机，在 onCreate 到 onDestroy 期间保留。Activity 自行处理旋转(清单中的 configChanges)，
     * 因此旋转时相机、其设置以及已打开的对话框都会保留；在后台时相机保持打开但停止推流。
     */
    private val camera = CameraSession(
        owner = this,
        callback = cameraCallback(),
        activeState = Lifecycle.State.CREATED,
        logTag = TAG,
        configure = { helper ->
            setCustomImageCaptureConfig(helper)
            setCustomVideoCaptureConfig(helper)
        },
    )

    private val cameraHelper: ICameraHelper? get() = camera.cameraHelper

    private val isCameraConnected: Boolean get() = camera.isCameraOpened

    private var previewRotation = 0

    // UI state, read by the composables in setContent.
    // UI 状态，由 setContent 中的 composable 读取。
    private var isRecording by mutableStateOf(false)

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

        setContent {
            UvcSampleTheme {
                MainScreen(
                    isCameraConnected = isCameraConnected,
                    isRecording = isRecording,
                    previewWidth = camera.previewWidth,
                    previewHeight = camera.previewHeight,
                    recordStartTime = recordStartTime,
                    onSurfaceAvailable = camera::addPreviewSurface,
                    onSurfaceDestroyed = { texture ->
                        // Release the texture once the camera no longer draws on it.
                        // 在相机不再向其绘制后释放该纹理。
                        camera.removePreviewSurface(texture) { texture.release() }
                    },
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
                camera.usbDevice = device
                selectDevice(device)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        camera.resumePreview()
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            toggleVideoRecord(false)
        }
        camera.pausePreview()
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
        val control = cameraHelper?.getUVCControl() ?: return
        openDialog = MainDialog.CameraControls(control)
    }

    private fun showDeviceListDialog() {
        val helper = cameraHelper ?: return
        openDialog = MainDialog.DeviceList(
            devices = helper.deviceList.orEmpty(),
            selectedDevice = if (isCameraConnected) camera.usbDevice else null,
        )
    }

    private fun onDeviceSelected(device: UsbDevice) {
        if (isCameraConnected) {
            cameraHelper?.closeCamera()
        }
        camera.usbDevice = device
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
            camera.resizePreview(size)
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

    private fun attachNewDevice(device: UsbDevice) {
        if (camera.usbDevice == null) {
            camera.usbDevice = device
            selectDevice(device)
        }
    }

    /**
     * In Android9+, connected to the UVC CAMERA, CAMERA permission is required
     *
     * 在 Android 9+ 上，连接 UVC 相机需要 CAMERA 权限
     */
    private fun selectDevice(device: UsbDevice) {
        XXPermissions.with(this)
            .permission(PermissionLists.getCameraPermission())
            .request { grantedList, _ ->
                if (grantedList.isEmpty()) {
                    return@request
                }
                // Try to obtain device permission through the UsbDevice object
                // 通过UsbDevice对象，尝试获取设备权限
                camera.selectDevice(device)
            }
    }

    private fun cameraCallback() = object : CameraSession.Callback {
        override fun onAttach(session: CameraSession, device: UsbDevice) {
            attachNewDevice(device)
        }

        /**
         * After obtaining USB device permissions, connect the USB camera
         *
         * 获取 USB 设备权限后，连接 USB 相机
         */
        override fun onDeviceOpen(session: CameraSession, device: UsbDevice, isFirstOpen: Boolean) {
            val helper = session.cameraHelper ?: return
            helper.openCamera(getSavedPreviewSize())
            helper.setButtonCallback(IButtonCallback { button, state ->
                Toast.makeText(
                    this@MainActivity,
                    "onButton(button=$button; state=$state)",
                    Toast.LENGTH_SHORT,
                ).show()
            })
        }

        override fun onCameraClose(session: CameraSession, device: UsbDevice) {
            if (isRecording) {
                toggleVideoRecord(false)
            }
            openDialog = null
        }
    }

    private fun savedPreviewSizeKey(): String =
        getString(R.string.saved_preview_size) + USBMonitor.getProductKey(camera.usbDevice)

    // The preview size is stored as plain preference values, one per Size field.
    // 预览尺寸以普通的偏好设置值保存，Size 的每个字段对应一个值。
    private fun getSavedPreviewSize(): Size? {
        val key = savedPreviewSizeKey()
        val prefs = getPreferences(MODE_PRIVATE)
        if (!prefs.contains("$key.width") || !prefs.contains("$key.height")) {
            return null
        }
        val fpsList = prefs.getString("$key.fpsList", null)
            .orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
        return Size(
            prefs.getInt("$key.type", 0),
            prefs.getInt("$key.width", 0),
            prefs.getInt("$key.height", 0),
            prefs.getInt("$key.fps", 0),
            fpsList,
        )
    }

    private fun setSavedPreviewSize(size: Size) {
        val key = savedPreviewSizeKey()
        getPreferences(MODE_PRIVATE)
            .edit()
            // Drop the Gson JSON value that older versions stored under the bare key.
            // 删除旧版本以裸键保存的 Gson JSON 值。
            .remove(key)
            .putInt("$key.type", size.type)
            .putInt("$key.width", size.width)
            .putInt("$key.height", size.height)
            .putInt("$key.fps", size.fps)
            .putString("$key.fpsList", size.fpsList.orEmpty().joinToString(","))
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
     * Pictures are saved through MediaStore, which needs no permission.
     *
     * 照片通过 MediaStore 保存，不需要任何权限。
     */
    private fun requestTakePicture() {
        takePicture()
    }

    /**
     * Recording needs the microphone permission. XXPermissions reports every result, granted or
     * denied, so act only when it was not denied. Stopping a recording needs no permission check.
     *
     * 录像需要麦克风权限。XXPermissions 会回调每次请求结果(无论授予与否)，
     * 因此仅在未被拒绝时执行操作。停止录像不需要检查权限。
     */
    private fun requestToggleVideoRecord() {
        if (isRecording) {
            toggleVideoRecord(false)
            return
        }
        XXPermissions.with(this)
            .permission(PermissionLists.getRecordAudioPermission())
            .request { _, deniedList ->
                if (deniedList.isEmpty()) {
                    toggleVideoRecord(true)
                }
            }
    }

    private fun takePicture() {
        if (isRecording) {
            return
        }
        val helper = cameraHelper ?: return

        try {
            val output = MediaStoreOutput.picture(this)
            helper.takePicture(output.imageOptions(), object : IImageCapture.OnImageCaptureCallback {
                override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                    showSavedToast(output)
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
        val output = MediaStoreOutput.video(this)
        helper.startRecording(output.videoOptions(), object : VideoCapture.OnVideoCaptureCallback {
            override fun onStart() {
                recordStartTime = SystemClock.elapsedRealtime()
            }

            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                toggleVideoRecord(false)
                showSavedToast(output)
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                toggleVideoRecord(false)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showSavedToast(output: MediaStoreOutput) {
        Toast.makeText(this, getString(SampleR.string.saved_to, output.description), Toast.LENGTH_SHORT).show()
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
    }
}
