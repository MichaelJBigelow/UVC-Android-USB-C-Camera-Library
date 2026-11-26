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

import android.os.SystemClock

/**
 * Monotonic time source: [System.nanoTime], or [SystemClock.elapsedRealtimeNanos] when
 * [prohibitElapsedRealtimeNanos] is false at the last [reset].
 *
 * 单调时间源：[System.nanoTime]；如果上次 [reset] 时 [prohibitElapsedRealtimeNanos] 为 false，
 * 则使用 [SystemClock.elapsedRealtimeNanos]。
 */
object Time {

    @JvmField
    var prohibitElapsedRealtimeNanos = true

    @Volatile
    private var useElapsedRealtime = false

    init {
        reset()
    }

    @JvmStatic
    fun nanoTime(): Long =
        if (useElapsedRealtime) SystemClock.elapsedRealtimeNanos() else System.nanoTime()

    @JvmStatic
    fun reset() {
        useElapsedRealtime = !prohibitElapsedRealtimeNanos
    }
}
