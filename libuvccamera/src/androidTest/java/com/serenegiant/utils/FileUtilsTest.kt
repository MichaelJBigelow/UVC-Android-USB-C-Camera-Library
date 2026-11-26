package com.serenegiant.utils

import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.serenegiant.uvccamera.R as LibraryR

/**
 * Checks the names and MediaStore entries made for captures.
 *
 * 检查为拍摄文件生成的名称和 MediaStore 条目。
 */
@RunWith(AndroidJUnit4::class)
class FileUtilsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private var insertedUri: Uri? = null

    @After
    fun tearDown() {
        insertedUri?.let { resolver.delete(it, null, null) }
    }

    @Test
    fun dateTimeStringIsYearToSecond() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}""").matches(FileUtils.getDateTimeString()))
    }

    @Test
    fun appNameIsTheLibraryName() {
        assertEquals(context.getString(LibraryR.string.app_name), FileUtils.getAppName())
    }

    @Test
    fun captureUriIsAMediaStoreEntryInTheAppFolder() {
        val uri = FileUtils.getCaptureUri(context, Environment.DIRECTORY_PICTURES, "IMG_", ".jpg")
        insertedUri = uri

        val columns = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        resolver.query(uri!!, columns, null, null, null).use { cursor ->
            assertTrue(cursor!!.moveToFirst())
            val name = cursor.getString(0)
            assertTrue(name, Regex("""IMG_\d{4}(-\d{2}){5}\.jpg""").matches(name))
            assertEquals("image/jpeg", cursor.getString(1))
            assertEquals("Pictures/${FileUtils.getAppName()}/", cursor.getString(2))
        }
    }

    @Test
    fun captureUriIsOnlyForMedia() {
        // Text isn't a picture, video or sound / 文本不是图片、视频或声音
        assertNull(FileUtils.getCaptureUri(context, Environment.DIRECTORY_DOCUMENTS, ".txt"))
        // No MIME type / 没有 MIME 类型
        assertNull(FileUtils.getCaptureUri(context, Environment.DIRECTORY_PICTURES, ".uvcunknown"))
    }
}
