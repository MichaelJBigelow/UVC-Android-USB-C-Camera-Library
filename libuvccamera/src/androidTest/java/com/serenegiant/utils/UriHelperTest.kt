package com.serenegiant.utils

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Checks turning file and MediaStore URIs into paths.
 *
 * 检查将文件 URI 和 MediaStore URI 转换为路径。
 */
@RunWith(AndroidJUnit4::class)
class UriHelperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private var insertedUri: Uri? = null

    @After
    fun tearDown() {
        insertedUri?.let { resolver.delete(it, null, null) }
    }

    @Test
    fun fileUriIsItsPath() {
        val file = File(context.cacheDir, "picture.jpg")

        assertEquals(file.path, UriHelper.getPath(context, Uri.fromFile(file)))
    }

    @Test
    fun noUriHasNoPath() {
        assertNull(UriHelper.getPath(context, null))
        assertNull(UriHelper.getAbsolutePath(resolver, null))
    }

    @Test
    fun mediaStoreUriIsTheFileItNames() {
        val name = "UriHelperTest_${System.nanoTime()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/UVCAndroidTest/")
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        insertedUri = uri

        val path = UriHelper.getPath(context, uri)

        assertTrue("$path", path!!.endsWith("/Pictures/UVCAndroidTest/$name"))
        assertEquals(path, UriHelper.getAbsolutePath(resolver, uri))
    }

    @Test
    fun recognisesDocumentProviders() {
        val externalStorage = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADCIM")
        val downloads = Uri.parse("content://com.android.providers.downloads.documents/document/12")
        val media = Uri.parse("content://com.android.providers.media.documents/document/image%3A34")
        val photos = Uri.parse("content://com.google.android.apps.photos.content/0/1")

        assertTrue(UriHelper.isExternalStorageDocument(externalStorage))
        assertTrue(UriHelper.isDownloadsDocument(downloads))
        assertTrue(UriHelper.isMediaDocument(media))
        assertTrue(UriHelper.isGooglePhotosUri(photos))
        assertFalse(UriHelper.isExternalStorageDocument(media))
        assertFalse(UriHelper.isDownloadsDocument(externalStorage))
        assertFalse(UriHelper.isMediaDocument(downloads))
        assertFalse(UriHelper.isGooglePhotosUri(media))
    }

    @Test
    fun knowsTheStandardDirectories() {
        assertTrue(UriHelper.isStandardDirectory(Environment.DIRECTORY_DCIM))
        assertTrue(UriHelper.isStandardDirectory(Environment.DIRECTORY_MOVIES))
        assertFalse(UriHelper.isStandardDirectory("UVCAndroidTest"))
    }
}
