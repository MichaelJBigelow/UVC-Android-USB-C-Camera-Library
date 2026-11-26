package com.herohan.uvcsample.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.herohan.uvcapp.IImageCapture
import com.herohan.uvcapp.VideoCapture
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Where a picture or video is saved: a new MediaStore entry in shared storage, under
 * `Pictures/<app name>` or `Movies/<app name>`. Saving through MediaStore needs no storage
 * permission; the library hides the entry (IS_PENDING) until the file is complete.
 *
 * 照片或视频的保存位置：共享存储中的一个新 MediaStore 条目，位于
 * `Pictures/<应用名>` 或 `Movies/<应用名>` 下。通过 MediaStore 保存不需要存储权限；
 * 库会在文件写完之前隐藏该条目(IS_PENDING)。
 *
 * @param collection the MediaStore collection to insert into / 要插入的 MediaStore 集合
 * @param relativePath folder of the new entry, ending with "/" / 新条目所在的文件夹，以 "/" 结尾
 * @param displayName file name of the new entry / 新条目的文件名
 * @param mimeType MIME type of the new entry / 新条目的 MIME 类型
 */
class MediaStoreOutput private constructor(
    private val resolver: ContentResolver,
    private val collection: Uri,
    val relativePath: String,
    val displayName: String,
    private val mimeType: String,
) {

    /** Where the file is, for messages, e.g. "Pictures/UVCApp/2026-11-26_09-30-00.jpg". / 文件位置，用于提示信息。 */
    val description: String get() = relativePath + displayName

    private fun contentValues() = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
    }

    fun imageOptions(): IImageCapture.OutputFileOptions =
        IImageCapture.OutputFileOptions.Builder(resolver, collection, contentValues()).build()

    fun videoOptions(): VideoCapture.OutputFileOptions =
        VideoCapture.OutputFileOptions.Builder(resolver, collection, contentValues()).build()

    companion object {
        private val FILE_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        /**
         * File name for a capture taken at [time], e.g. "2026-11-26_09-30-00.jpg".
         *
         * 在 [time] 拍摄的文件名，例如 "2026-11-26_09-30-00.jpg"。
         */
        fun fileName(time: LocalDateTime, extension: String): String =
            FILE_NAME_FORMAT.format(time) + extension

        /**
         * Folder for the captures of [appName] in [directory], e.g. "Pictures/UVCApp/".
         *
         * [appName] 的拍摄文件在 [directory] 中的文件夹，例如 "Pictures/UVCApp/"。
         */
        fun relativePath(directory: String, appName: String): String {
            // Characters MediaStore doesn't accept in a folder name / MediaStore 不接受的文件夹名字符
            val folder = appName.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "UVC" }
            return "$directory/$folder/"
        }

        /** A new JPEG picture in `Pictures/<app name>`. / `Pictures/<应用名>` 中的一张新 JPEG 照片。 */
        fun picture(context: Context): MediaStoreOutput = create(
            context,
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            Environment.DIRECTORY_PICTURES,
            ".jpg",
            "image/jpeg",
        )

        /** A new MP4 video in `Movies/<app name>`. / `Movies/<应用名>` 中的一段新 MP4 视频。 */
        fun video(context: Context): MediaStoreOutput = create(
            context,
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            Environment.DIRECTORY_MOVIES,
            ".mp4",
            "video/mp4",
        )

        private fun create(
            context: Context,
            collection: Uri,
            directory: String,
            extension: String,
            mimeType: String,
        ): MediaStoreOutput {
            val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
            return MediaStoreOutput(
                resolver = context.contentResolver,
                collection = collection,
                relativePath = relativePath(directory, appName),
                displayName = fileName(LocalDateTime.now(), extension),
                mimeType = mimeType,
            )
        }
    }
}
