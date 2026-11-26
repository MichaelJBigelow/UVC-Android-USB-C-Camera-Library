package com.herohan.uvcapp

class CameraException : Exception {

    /**
     * Error code
     *
     * 错误码
     */
    val code: Int

    internal constructor(message: String?) : super(message) {
        code = 0
    }

    internal constructor(code: Int, message: String?) : super(message) {
        this.code = code
    }

    internal constructor(code: Int, cause: Exception?) : super(cause) {
        this.code = code
    }

    override fun toString(): String = "CameraException{code=$code,message=$message}"

    companion object {
        /**
         * Unknown error occurred  when open camera
         *
         * 打开相机时发生未知错误
         */
        const val CAMERA_OPEN_ERROR_UNKNOWN = 1

        /**
         * The device is occupied when open camera.
         *
         * 打开相机时设备被占用。
         */
        const val CAMERA_OPEN_ERROR_BUSY = 2
    }
}
