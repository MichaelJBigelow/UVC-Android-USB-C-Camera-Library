package com.herohan.uvcapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Checks that a captured picture is encoded as JPEG and saved to a file, an output stream or
 * MediaStore, and that failures are reported. Needs no camera: the picture is made up.
 *
 * 检查拍摄的照片被编码为 JPEG 并保存到文件、输出流或 MediaStore，以及失败会被报告。
 * 无需相机：照片是构造出来的。
 */
@RunWith(AndroidJUnit4::class)
class ImageSaverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val mediaStoreUris = mutableListOf<Uri>()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "ImageSaverTest").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        mediaStoreUris.forEach { resolver.delete(it, null, null) }
        dir.deleteRecursively()
    }

    /** What the saver reported, and how many times. / 保存器报告的结果及报告次数。 */
    private class Result(val savedUri: Uri?, val error: ImageSaver.SaveError?, val calls: Int)

    private class RecordingCallback : ImageSaver.OnImageSavedCallback {
        var savedUri: Uri? = null
        var error: ImageSaver.SaveError? = null
        var calls = 0

        override fun onImageSaved(outputFileResults: IImageCapture.OutputFileResults) {
            savedUri = outputFileResults.savedUri
            calls++
        }

        override fun onError(saveError: ImageSaver.SaveError, message: String, cause: Throwable?) {
            error = saveError
            calls++
        }
    }

    private fun runSaver(saver: (ImageSaver.OnImageSavedCallback) -> ImageSaver): Result {
        val callback = RecordingCallback()
        saver(callback).run()
        return Result(callback.savedUri, callback.error, callback.calls)
    }

    /**
     * A picture as an ImageReader returns it: rows padded past [WIDTH].
     *
     * ImageReader 返回的照片：每行在 [WIDTH] 之后带有填充。
     */
    private fun paddedBitmap(): Bitmap =
        Bitmap.createBitmap(WIDTH + 4, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    private fun saveBitmap(options: IImageCapture.OutputFileOptions, bitmap: Bitmap = paddedBitmap()): Result =
        runSaver { callback -> ImageSaver(bitmap, WIDTH, HEIGHT, QUALITY, options, callback) }

    /** Size of the JPEG in [bytes], or null if they aren't a picture. / [bytes] 中 JPEG 的尺寸；不是图片时为 null。 */
    private fun jpegSize(bytes: ByteArray?): String? {
        if (bytes == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return if (bounds.outMimeType == "image/jpeg") "${bounds.outWidth}x${bounds.outHeight}" else null
    }

    @Test
    fun savesTheCroppedPictureToAFile() {
        val file = File(dir, "picture.jpg")

        val result = saveBitmap(IImageCapture.OutputFileOptions.Builder(file).build())

        assertNull(result.error)
        assertEquals(1, result.calls)
        assertEquals(Uri.fromFile(file), result.savedUri)
        assertEquals("${WIDTH}x$HEIGHT", jpegSize(file.readBytes()))
    }

    @Test
    fun savesToAnOutputStreamWithoutAUri() {
        val out = ByteArrayOutputStream()

        val result = saveBitmap(IImageCapture.OutputFileOptions.Builder(out).build())

        assertNull(result.error)
        assertEquals(1, result.calls)
        assertNull(result.savedUri)
        assertEquals("${WIDTH}x$HEIGHT", jpegSize(out.toByteArray()))
    }

    @Test
    fun savesToMediaStoreAndPublishesTheEntry() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ImageSaverTest_${System.nanoTime()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/UVCAndroidTest/")
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val result = saveBitmap(IImageCapture.OutputFileOptions.Builder(resolver, collection, values).build())
        result.savedUri?.let { mediaStoreUris += it }

        assertNull(result.error)
        assertEquals(1, result.calls)
        val uri = checkNotNull(result.savedUri)
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null).use { cursor ->
            assertTrue(cursor!!.moveToFirst())
            assertEquals("the entry is published", 0, cursor.getInt(0))
        }
        assertEquals("${WIDTH}x$HEIGHT", jpegSize(resolver.openInputStream(uri)!!.use { it.readBytes() }))
    }

    @Test
    fun recyclesTheBitmapOnceSaved() {
        val bitmap = paddedBitmap()

        saveBitmap(IImageCapture.OutputFileOptions.Builder(File(dir, "picture.jpg")).build(), bitmap)

        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun reportsAFileThatCantBeWritten() {
        val file = File(dir, "missing-folder/picture.jpg")

        val result = saveBitmap(IImageCapture.OutputFileOptions.Builder(file).build())

        assertEquals(ImageSaver.SaveError.FILE_IO_FAILED, result.error)
        assertEquals(1, result.calls)
        assertFalse(file.exists())
    }

    @Test
    fun encodesRgbaPixelsReadFromTheRenderer() {
        val pixels = ByteArray(WIDTH * HEIGHT * 4) { if (it % 4 == 3) -1 else 0x40 }
        val file = File(dir, "picture.jpg")

        val result = runSaver { callback ->
            ImageSaver(ImageRawData(pixels, WIDTH, HEIGHT), QUALITY, IImageCapture.OutputFileOptions.Builder(file).build(), callback)
        }

        assertNull(result.error)
        assertEquals("${WIDTH}x$HEIGHT", jpegSize(file.readBytes()))
    }

    @Test
    fun reportsPixelsThatCantBeEncoded() {
        // Fewer bytes than WIDTH x HEIGHT RGBA pixels need
        // 字节数少于 WIDTH x HEIGHT 个 RGBA 像素所需
        val pixels = ByteArray(10)
        val file = File(dir, "picture.jpg")

        val result = runSaver { callback ->
            ImageSaver(ImageRawData(pixels, WIDTH, HEIGHT), QUALITY, IImageCapture.OutputFileOptions.Builder(file).build(), callback)
        }

        assertEquals(ImageSaver.SaveError.ENCODE_FAILED, result.error)
        assertEquals(1, result.calls)
        assertFalse(file.exists())
    }

    private companion object {
        const val WIDTH = 8
        const val HEIGHT = 6
        const val QUALITY = 90
    }
}
