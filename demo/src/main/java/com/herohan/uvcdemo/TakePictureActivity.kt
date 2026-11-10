package com.herohan.uvcdemo

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.herohan.uvcapp.IImageCapture
import com.herohan.uvcdemo.ui.CameraSurfacePreview
import com.herohan.uvcdemo.ui.DemoScaffold
import com.herohan.uvcdemo.ui.TestTags
import com.herohan.uvcdemo.ui.theme.UvcDemoTheme
import com.herohan.uvcdemo.ui.theme.enableEdgeToEdgeBelowAppBar
import com.serenegiant.utils.FileUtils
import com.serenegiant.utils.UriHelper
import com.serenegiant.uvccamera.R as UvcR

class TakePictureActivity : ComponentActivity() {

    private var cameraHelper: ICameraHelper? = null

    private var previewSurface: Surface? = null

    private var previewWidth by mutableIntStateOf(DEFAULT_WIDTH)
    private var previewHeight by mutableIntStateOf(DEFAULT_HEIGHT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeBelowAppBar()

        setContent {
            UvcDemoTheme {
                DemoScaffold(title = stringResource(R.string.entry_take_picture)) {
                    CameraSurfacePreview(
                        previewWidth = previewWidth,
                        previewHeight = previewHeight,
                        onSurfaceCreated = { surface ->
                            previewSurface = surface
                            cameraHelper?.addSurface(surface, false)
                        },
                        onSurfaceDestroyed = { surface ->
                            cameraHelper?.removeSurface(surface)
                            previewSurface = null
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    IconButton(
                        onClick = ::takePicture,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = dimensionResource(UvcR.dimen.vertical_margin))
                            .testTag(TestTags.CAPTURE_IMAGE),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_switch_camera),
                            contentDescription = stringResource(R.string.entry_take_picture),
                            tint = Color.Unspecified,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        initCameraHelper()
    }

    override fun onStop() {
        super.onStop()
        clearCameraHelper()
    }

    private fun initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:")
        if (cameraHelper == null) {
            cameraHelper = CameraHelper().apply { setStateCallback(stateListener) }
//            cameraHelper?.imageCaptureConfig?.setCaptureStrategy(IImageCapture.CAPTURE_STRATEGY_IMAGE_READER)
        }
    }

    private fun clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:")
        cameraHelper?.release()
        cameraHelper = null
    }

    private fun selectDevice(device: UsbDevice) {
        if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.deviceName)
        cameraHelper?.selectDevice(device)
    }

    private fun takePicture() {
        val helper = cameraHelper ?: return

        val file = FileUtils.getCaptureFile(this, Environment.DIRECTORY_DCIM, ".jpg")
        val options = IImageCapture.OutputFileOptions.Builder(file).build()

//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_IMAGE")
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//        }
//        val options = IImageCapture.OutputFileOptions.Builder(
//            contentResolver,
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            contentValues,
//        ).build()

//        val outputStream = ByteArrayOutputStream()
//        val options = IImageCapture.OutputFileOptions.Builder(outputStream).build()

        helper.takePicture(options, object : IImageCapture.OnImageCaptureCallback {
            override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                Toast.makeText(
                    this@TakePictureActivity,
                    "save \"" + UriHelper.getPath(this@TakePictureActivity, outputFileResults.savedUri) + "\"",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
                Toast.makeText(this@TakePictureActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private val stateListener = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onAttach:")
            selectDevice(device)
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:")
            cameraHelper?.openCamera()
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:")
            val helper = cameraHelper ?: return

            helper.startPreview()

            // auto aspect ratio
            // 自动宽高比
            helper.previewSize?.let {
                previewWidth = it.width
                previewHeight = it.height
            }

            previewSurface?.let { helper.addSurface(it, false) }
        }

        override fun onCameraClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCameraClose:")
            previewSurface?.let { cameraHelper?.removeSurface(it) }
        }

        override fun onDeviceClose(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:")
        }

        override fun onDetach(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onDetach:")
        }

        override fun onCancel(device: UsbDevice) {
            if (DEBUG) Log.v(TAG, "onCancel:")
        }
    }

    private companion object {
        private const val DEBUG = true
        private val TAG = TakePictureActivity::class.java.simpleName

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}
