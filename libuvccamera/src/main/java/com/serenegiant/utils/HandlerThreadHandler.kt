package com.serenegiant.utils
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2018 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process

/**
 * A [Handler] running on its own new [HandlerThread].
 *
 * 运行在其自己新建的 [HandlerThread] 上的 [Handler]。
 */
class HandlerThreadHandler private constructor(looper: Looper, callback: Handler.Callback?) :
    Handler(looper, callback) {

    companion object {
        private const val TAG = "HandlerThreadHandler"

        @JvmStatic
        @JvmOverloads
        fun createHandler(name: String = TAG): HandlerThreadHandler {
            val thread = HandlerThread(name)
            thread.start()
            return HandlerThreadHandler(thread.looper, null)
        }

        @JvmStatic
        fun createHandler(callback: Handler.Callback?): HandlerThreadHandler = createHandler(TAG, callback)

        @JvmStatic
        fun createHandler(name: String, callback: Handler.Callback?): HandlerThreadHandler {
            val thread = HandlerThread(name, Process.THREAD_PRIORITY_MORE_FAVORABLE)
            thread.start()
            return HandlerThreadHandler(thread.looper, callback)
        }
    }
}
