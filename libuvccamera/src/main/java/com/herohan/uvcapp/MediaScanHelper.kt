package com.herohan.uvcapp

import android.content.ContentResolver
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.serenegiant.utils.UVCUtils

/**
 * Asks the media scanner to index a picture or video saved to a file.
 *
 * Files saved through MediaStore are indexed when they are inserted, so only `file://`
 * outputs are scanned.
 *
 * 请求媒体扫描器为保存到文件的照片或视频建立索引。
 *
 * 通过 MediaStore 保存的文件在插入时即已建立索引，因此只扫描 `file://` 输出。
 */
internal object MediaScanHelper {
    private const val TAG = "MediaScanHelper"

    /**
     * Invokes scanFile so the saved file shows up in MediaStore with its final size.
     *
     * 调用 scanFile，使已保存的文件以其最终大小出现在 MediaStore 中。
     *
     * @param uri the saved file; null and non-file URIs are ignored / 已保存的文件；忽略 null 和非文件 URI
     */
    fun scanMediaFile(uri: Uri?) {
        if (uri == null || ContentResolver.SCHEME_FILE != uri.scheme) {
            return
        }
        val path = uri.path ?: return
        try {
            MediaScannerConnection.scanFile(UVCUtils.getApplication(), arrayOf(path), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "MediaScannerConnection:", e)
        }
    }
}
