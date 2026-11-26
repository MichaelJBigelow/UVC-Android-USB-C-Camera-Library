package com.serenegiant.utils

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HandlerThreadHandlerTest {

    @Test
    fun runsWorkOnItsOwnNamedThread() {
        val handler = HandlerThreadHandler.createHandler("HandlerThreadHandlerTest")
        try {
            val threadName = AtomicReference<String>()
            val done = CountDownLatch(1)

            handler.post {
                threadName.set(Thread.currentThread().name)
                done.countDown()
            }

            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("HandlerThreadHandlerTest", threadName.get())
            assertNotEquals(Looper.getMainLooper(), handler.looper)
        } finally {
            handler.looper.quitSafely()
        }
    }

    @Test
    fun deliversMessagesToTheCallback() {
        val what = AtomicInteger()
        val done = CountDownLatch(1)
        val handler = HandlerThreadHandler.createHandler { message ->
            what.set(message.what)
            done.countDown()
            true
        }
        try {
            handler.sendEmptyMessage(42)

            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals(42, what.get())
            assertEquals("HandlerThreadHandler", handler.looper.thread.name)
        } finally {
            handler.looper.quitSafely()
        }
    }
}
