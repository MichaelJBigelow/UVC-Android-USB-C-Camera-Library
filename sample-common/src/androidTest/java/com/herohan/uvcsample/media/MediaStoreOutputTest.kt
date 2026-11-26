package com.herohan.uvcsample.media

import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.herohan.uvcapp.IImageCapture
import com.herohan.uvcapp.ImageSaver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saves a picture where the sample apps save theirs, through the library's [ImageSaver], and checks
 * the MediaStore entry it makes. Needs no camera and no storage permission.
 *
 * 通过库的 [ImageSaver] 将照片保存到示例应用保存照片的位置，并检查生成的 MediaStore 条目。
 * 无需相机，也无需存储权限。
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreOutputTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private var savedUri: Uri? = null

    private val appName: String get() = context.applicationInfo.loadLabel(context.packageManager).toString()

    @After
    fun tearDown() {
        savedUri?.let { resolver.delete(it, null, null) }
    }

    @Test
    fun picture_isSavedAsAJpegInTheAppsPicturesFolder() {
        val output = MediaStoreOutput.picture(context)
        var error: ImageSaver.SaveError? = null
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        ImageSaver(bitmap, 4, 4, 90, output.imageOptions(), object : ImageSaver.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
                savedUri = outputFileResults.savedUri
            }

            override fun onError(saveError: ImageSaver.SaveError, message: String, cause: Throwable?) {
                error = saveError
            }
        }).run()

        assertNull(error)
        val columns = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_PENDING,
        )
        resolver.query(savedUri!!, columns, null, null, null).use { cursor ->
            assertTrue(cursor!!.moveToFirst())
            assertEquals(output.displayName, cursor.getString(0))
            assertEquals(output.relativePath, cursor.getString(1))
            assertEquals("image/jpeg", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
        assertEquals(MediaStoreOutput.relativePath(Environment.DIRECTORY_PICTURES, appName), output.relativePath)
        assertTrue(output.displayName, output.displayName.endsWith(".jpg"))
    }

    @Test
    fun video_isAnMp4InTheAppsMoviesFolder() {
        val output = MediaStoreOutput.video(context)

        assertEquals(MediaStoreOutput.relativePath(Environment.DIRECTORY_MOVIES, appName), output.relativePath)
        assertTrue(output.displayName, output.displayName.endsWith(".mp4"))
        assertEquals(output.relativePath + output.displayName, output.description)
    }
}
