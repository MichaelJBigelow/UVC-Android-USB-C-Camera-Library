package com.serenegiant.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import com.serenegiant.uvccamera.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale

object FileUtils {
    private const val TAG = "FileUtils"

    private const val MIME_PREFIX_IMAGE = "image/"
    private const val MIME_PREFIX_VIDEO = "video/"
    private const val MIME_PREFIX_AUDIO = "audio/"

    private val mDateTimeFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

    @JvmStatic
    fun getAppName(): String = UVCUtils.getApplication().getString(R.string.app_name)

    /**
     * generate output file
     * use media store to create media file directly, updating media store manually by MediaScannerConnection.scanFile is unnecessary.
     *
     * 生成输出文件。
     * 直接使用 media store 创建媒体文件，无需再通过 MediaScannerConnection.scanFile 手动更新 media store。
     *
     * @param type Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM etc. / Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM 等
     * @param ext  .mp4(.m4a for audio) or .png / .mp4（音频为 .m4a）或 .png
     * @return return null when this app has no writing permission to external storage, or the
     * extension is not an image, video or audio type. / 当本应用没有外部存储的写入权限，
     * 或扩展名不是图片、视频或音频类型时返回 null
     */
    @JvmStatic
    fun getCaptureUri(context: Context, type: String, ext: String): Uri? =
        getCaptureUri(context, type, null, ext)

    @JvmStatic
    fun getCaptureUri(context: Context, type: String, prefix: String?, ext: String): Uri? {
        var fileName = getDateTimeString() + ext
        if (!TextUtils.isEmpty(prefix)) {
            fileName = prefix + fileName
        }

        var extension = ""
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot > 0 && lastDot < fileName.length - 1) {
            extension = fileName.substring(lastDot + 1).lowercase(Locale.getDefault())
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: return null

        // Add a specific media item.
        // 添加指定的媒体项。
        val resolver = context.contentResolver

        // Find all media files on the primary external storage device.
        // 查找主外部存储设备上的所有媒体文件。
        val volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val mediaTable = when {
            mimeType.startsWith(MIME_PREFIX_IMAGE) -> MediaStore.Images.Media.getContentUri(volumeName)
            mimeType.startsWith(MIME_PREFIX_VIDEO) -> MediaStore.Video.Media.getContentUri(volumeName)
            mimeType.startsWith(MIME_PREFIX_AUDIO) -> MediaStore.Audio.Media.getContentUri(volumeName)
            else -> return null
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, type + File.separator + getAppName())
        }

        return resolver.insert(mediaTable, contentValues)
    }

    /**
     * create file for saving movie / still image file
     *
     * need WRITE_EXTERNAL_STORAGE permission
     *
     * 创建用于保存视频/静态图片的文件。
     *
     * 需要 WRITE_EXTERNAL_STORAGE 权限
     *
     * @param type Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM
     * @param ext  .mp4 .png or .jpeg / .mp4、.png 或 .jpeg
     * @return  return null when this app has no writing permission to external storage. / 当本应用没有外部存储的写入权限时返回 null
     */
    @JvmStatic
    fun getCaptureFile(context: Context, type: String, ext: String): File? =
        getCaptureFile(context, type, null, ext)

    @JvmStatic
    fun getCaptureFile(context: Context, type: String, prefix: String?, ext: String): File? {
        val fileName = (if (TextUtils.isEmpty(prefix)) getDateTimeString() else prefix + getDateTimeString()) + ext
        val dir = getCaptureDir(context, type) ?: return null
        dir.mkdirs()
        return if (dir.canWrite()) File(dir, fileName) else null
    }

    @JvmStatic
    fun getCaptureDir(context: Context, type: String): File? {
        val dir = File(Environment.getExternalStoragePublicDirectory(type), getAppName())
        dir.mkdirs()
        return if (dir.canWrite()) dir else null
    }

    /**
     * @return formatted string of current time / 当前时间的格式化字符串
     */
    @JvmStatic
    fun getDateTimeString(): String = mDateTimeFormat.format(GregorianCalendar().time)

    @JvmStatic
    fun getExternalMounts(): String? {
        var externalPath: String? = null

        try {
            val proc = Runtime.getRuntime().exec("mount")
            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.contains("secure")) continue
                    if (line.contains("asec")) continue

                    // external card / 外部存储卡
                    if (line.contains("fat")) {
                        val columns = line.split(" ")
                        if (columns.size > 1 && !TextUtils.isEmpty(columns[1])) {
                            externalPath = columns[1].let { if (it.endsWith("/")) it else "$it/" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
        }
        return externalPath
    }
}
