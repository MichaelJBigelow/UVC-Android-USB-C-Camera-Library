package com.serenegiant.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import com.serenegiant.uvccamera.BuildConfig
import java.io.File

/**
 * Uri of external storage for Android10 or later may not work properly
 *
 * 在 Android10 及更高版本上，外部存储的 Uri 可能无法正常工作
 */
object UriHelper {
    private val DEBUG = BuildConfig.DEBUG
    private const val TAG = "UriHelper"

    /**
     *  convert uri to path
     *
     *  将 uri 转换为路径
     *
     * @return String Null if the path is not found / 如果未找到路径则为 Null
     */
    @JvmStatic
    fun getAbsolutePath(cr: ContentResolver, uri: Uri?): String? {
        if (uri == null) {
            return null
        }
        return try {
            cr.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    @JvmField
    val STANDARD_DIRECTORIES: Array<String> = arrayOf(
        Environment.DIRECTORY_MUSIC,
        Environment.DIRECTORY_PODCASTS,
        Environment.DIRECTORY_RINGTONES,
        Environment.DIRECTORY_ALARMS,
        Environment.DIRECTORY_NOTIFICATIONS,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_DOCUMENTS,
    )

    @JvmStatic
    fun isStandardDirectory(dir: String): Boolean = STANDARD_DIRECTORIES.contains(dir)

    /**
     * Return path if the Uri can be converted to a local path
     *
     * 如果该 Uri 可以转换为本地路径，则返回该路径
     *
     * @param context The context. / 上下文
     * @param uri     The Uri to query. / 要查询的 Uri
     */
    @JvmStatic
    fun getPath(context: Context, uri: Uri?): String? {
        if (DEBUG) Log.i(TAG, "getPath:uri=$uri")

        if (uri != null && DocumentsContract.isDocumentUri(context, uri)) {
            // DocumentProvider
            if (DEBUG) Log.i(TAG, "getPath:isDocumentUri,getAuthority=" + uri.authority)
            if (isExternalStorageDocument(uri)) {
                // ExternalStorageProvider
                val docId = DocumentsContract.getDocumentId(uri)
                if (DEBUG) Log.i(TAG, "getPath:isDocumentUri,docId=$docId")
                if (DEBUG) {
                    Log.i(
                        TAG, "getPath:isDocumentUri,getTreeDocumentId="
                            + DocumentsContract.getTreeDocumentId(uri)
                    )
                }
                val split = docId.split(":")
                val type = split[0]

                if (DEBUG) Log.i(TAG, "getPath:type=$type")

                if ("primary".equals(type, ignoreCase = true)) {
                    val path = Environment.getExternalStorageDirectory().toString() + "/"
                    return if (split.size > 1) path + split[1] else path
                } else if ("home".equals(type, ignoreCase = true)) {
                    if (split.size > 1 && isStandardDirectory(split[1])) {
                        return Environment.getExternalStoragePublicDirectory(split[1]).toString() + "/"
                    }
                    val path = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS
                    ).toString() + "/"
                    return if (split.size > 1) path + split[1] else path
                } else {
                    // search the path from beginning
                    // 从头开始搜索路径
                    findOnSecondaryStorage(context, type, split)?.let { return it }
                }
            } else if (isDownloadsDocument(uri)) {
                // DownloadsProvider
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), id.toLong()
                )

                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                // MediaProvider
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val contentUri = when (split[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }

                if (contentUri != null) {
                    return getDataColumn(context, contentUri, "_id=?", arrayOf(split[1]))
                }
            }
        } else if (uri != null) {
            if ("content".equals(uri.scheme, ignoreCase = true)) {
                // MediaStore (and general)
                // MediaStore（及通用情况）
                if (isGooglePhotosUri(uri)) {
                    return uri.lastPathSegment
                }
                return getDataColumn(context, uri, null, null)
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                // File
                // 文件
                return uri.path
            }
        }

        Log.w(TAG, "unexpectedly not found,uri=$uri")
        return null
    }

    /**
     * Looks for a document of storage volume [type] among the app's external files directories on
     * volumes other than the primary one.
     *
     * 在主存储以外的存储卷上，从应用的外部文件目录中查找存储卷 [type] 的文档。
     */
    private fun findOnSecondaryStorage(context: Context, type: String, split: List<String>): String? {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        if (DEBUG) Log.i(TAG, "getPath:primary=$primary")
        val dirs = context.getExternalFilesDirs(null)
        val sb = StringBuilder()
        for ((i, dir) in dirs.withIndex()) {
            if (DEBUG) Log.i(TAG, "getPath:$i)dir=$dir")
            if (dir != null && dir.absolutePath.startsWith(primary)) {
                // skip primary storage
                // 跳过主存储
                continue
            }
            val dirPath = dir?.absolutePath
            if (TextUtils.isEmpty(dirPath)) {
                continue
            }
            val dirElements = dirPath!!.split("/")
            val m = dirElements.size
            if (m > 2 && "storage".equals(dirElements[1], ignoreCase = true)
                && type.equals(dirElements[2], ignoreCase = true)
            ) {
                var found = false
                sb.setLength(0)
                sb.append('/').append(dirElements[1])
                for (j in 2 until m) {
                    if ("Android".equals(dirElements[j], ignoreCase = true)) {
                        found = true
                        break
                    }
                    sb.append('/').append(dirElements[j])
                }
                if (found) {
                    val path = File(File(sb.toString()), split[1])
                    if (DEBUG) Log.i(TAG, "getPath:path=$path")
                    // return path directly no matter has permission to access the path or not
                    // 无论是否有权限访问该路径，都直接返回路径
                    return path.absolutePath
                }
            }
        }
        return null
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * 获取该 Uri 的 data 列的值。这对 MediaStore Uri 以及其他基于文件的 ContentProvider 很有用。
     *
     * @param context       The context. / 上下文
     * @param uri           The Uri to query. / 要查询的 Uri
     * @param selection     (Optional) Filter used in the query. / （可选）查询时使用的过滤条件
     * @param selectionArgs (Optional) Selection arguments used in the query. / （可选）查询时使用的选择参数
     * @return The value of the _data column, which is typically a file path. / _data 列的值，通常是文件路径
     */
    @JvmStatic
    fun getDataColumn(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): String? {
        val column = "_data"
        context.contentResolver.query(uri, arrayOf(column), selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(column))
            }
        }
        return null
    }

    /**
     * @param uri The Uri to check. / 要检查的 Uri
     * @return Whether the Uri authority is ExternalStorageProvider. / Uri 的 authority 是否为 ExternalStorageProvider
     */
    @JvmStatic
    fun isExternalStorageDocument(uri: Uri): Boolean =
        "com.android.externalstorage.documents" == uri.authority

    /**
     * @param uri The Uri to check. / 要检查的 Uri
     * @return Whether the Uri authority is DownloadsProvider. / Uri 的 authority 是否为 DownloadsProvider
     */
    @JvmStatic
    fun isDownloadsDocument(uri: Uri): Boolean =
        "com.android.providers.downloads.documents" == uri.authority

    /**
     * @param uri The Uri to check. / 要检查的 Uri
     * @return Whether the Uri authority is MediaProvider. / Uri 的 authority 是否为 MediaProvider
     */
    @JvmStatic
    fun isMediaDocument(uri: Uri): Boolean =
        "com.android.providers.media.documents" == uri.authority

    @JvmStatic
    fun isGooglePhotosUri(uri: Uri): Boolean =
        "com.google.android.apps.photos.content" == uri.authority
}
