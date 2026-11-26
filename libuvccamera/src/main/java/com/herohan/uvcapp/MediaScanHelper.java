package com.herohan.uvcapp;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

import com.serenegiant.utils.UVCUtils;

/**
 * Asks the media scanner to index a picture or video saved to a file.
 *
 * <p>Files saved through MediaStore are indexed when they are inserted, so only {@code file://}
 * outputs are scanned.
 *
 * 请求媒体扫描器为保存到文件的照片或视频建立索引。
 *
 * <p>通过 MediaStore 保存的文件在插入时即已建立索引，因此只扫描 {@code file://} 输出。
 */
final class MediaScanHelper {
    private static final String TAG = MediaScanHelper.class.getSimpleName();

    private MediaScanHelper() {
    }

    /**
     * Invokes scanFile so the saved file shows up in MediaStore with its final size.
     *
     * 调用 scanFile，使已保存的文件以其最终大小出现在 MediaStore 中。
     *
     * @param uri the saved file; null and non-file URIs are ignored / 已保存的文件；忽略 null 和非文件 URI
     */
    static void scanMediaFile(final Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return;
        }
        final String path = uri.getPath();
        if (path == null) {
            return;
        }
        final Context context = UVCUtils.getApplication();
        try {
            MediaScannerConnection.scanFile(context, new String[]{path}, null, null);
        } catch (final Exception e) {
            Log.e(TAG, "MediaScannerConnection:", e);
        }
    }
}
