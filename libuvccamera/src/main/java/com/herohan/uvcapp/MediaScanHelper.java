package com.herohan.uvcapp;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

import com.serenegiant.utils.UVCUtils;
import com.serenegiant.utils.UriHelper;

/**
 * Asks the media scanner to index a saved picture or video.
 *
 * 请求媒体扫描器为已保存的照片或视频建立索引。
 */
final class MediaScanHelper {
    private static final String TAG = MediaScanHelper.class.getSimpleName();

    private MediaScanHelper() {
    }

    /**
     * Invokes scanFile to update the size of the media file in MediaStore.
     *
     * 调用 scanFile 以更新 MediaStore 中媒体文件的大小。
     *
     * @param uri the saved file, or null to do nothing / 已保存的文件；为 null 时不做任何操作
     */
    static void scanMediaFile(final Uri uri) {
        if (uri == null) {
            return;
        }
        final Context context = UVCUtils.getApplication();
        String path = UriHelper.getPath(context, uri);

        try {
            MediaScannerConnection.scanFile(context, new String[]{path}, null, null);
        } catch (final Exception e) {
            Log.e(TAG, "MediaScannerConnection:", e);
        }
    }
}
