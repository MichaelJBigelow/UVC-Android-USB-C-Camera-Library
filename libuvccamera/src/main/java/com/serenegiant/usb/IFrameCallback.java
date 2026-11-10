/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usb;

import java.nio.ByteBuffer;

/**
 * Callback interface for UVCCamera class
 * If you need frame data as ByteBuffer, you can use this callback interface with UVCCamera#setFrameCallback
 *
 * UVCCamera 类的回调接口
 * 如果需要以 ByteBuffer 形式获取帧数据，可以配合 UVCCamera#setFrameCallback 使用此回调接口
 */
public interface IFrameCallback {
    /**
     * This method is called from native library via JNI on the same thread as UVCCamera#startCapture.
     * You can use both UVCCamera#startCapture and #setFrameCallback
     * but it is better to use either for better performance.
     * You can also pass pixel format type to UVCCamera#setFrameCallback for this method.
     * Some frames may drops if this method takes a time.
     * When you use some color format like NV21, this library never execute color space conversion,
     * just execute pixel format conversion. If you want to get same result as on screen, please try to
     * consider to get images via texture(SurfaceTexture) and read pixel buffer from it using OpenGL|ES2/3
     * instead of using IFrameCallback(this way is much efficient in most case than using IFrameCallback).
     *
     * 此方法由原生库通过 JNI 在与 UVCCamera#startCapture 相同的线程上调用。
     * 可以同时使用 UVCCamera#startCapture 和 #setFrameCallback,
     * 但为了获得更好的性能，建议只使用其中一种。
     * 还可以通过 UVCCamera#setFrameCallback 为此方法指定像素格式类型。
     * 如果此方法耗时较长，可能会丢帧。
     * 使用 NV21 等颜色格式时，本库不会执行色彩空间转换，
     * 只会执行像素格式转换。如果希望获得与屏幕显示相同的结果，请考虑
     * 通过纹理(SurfaceTexture)获取图像，并使用 OpenGL|ES2/3 从中读取像素缓冲区
     * (而不是使用 IFrameCallback，这种方式在大多数情况下比使用 IFrameCallback 高效得多)。
     *
     * @param frame this is direct ByteBuffer from JNI layer and you should handle it's byte order and limitation.
     *              / 这是来自 JNI 层的直接 ByteBuffer，需要自行处理其字节序和 limit 限制
     */
    void onFrame(ByteBuffer frame);
}
