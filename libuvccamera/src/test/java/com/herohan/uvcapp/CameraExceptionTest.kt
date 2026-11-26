package com.herohan.uvcapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CameraExceptionTest {

    @Test
    fun messageOnlyHasNoCode() {
        val e = CameraException("failed")

        assertEquals(0, e.code)
        assertEquals("failed", e.message)
    }

    @Test
    fun keepsTheCodeAndMessage() {
        val e = CameraException(CameraException.CAMERA_OPEN_ERROR_BUSY, "busy")

        assertEquals(CameraException.CAMERA_OPEN_ERROR_BUSY, e.code)
        assertEquals("CameraException{code=2,message=busy}", e.toString())
    }

    @Test
    fun keepsTheCodeAndCause() {
        val cause = IllegalStateException("no permission")

        val e = CameraException(CameraException.CAMERA_OPEN_ERROR_UNKNOWN, cause)

        assertEquals(CameraException.CAMERA_OPEN_ERROR_UNKNOWN, e.code)
        assertSame(cause, e.cause)
        assertEquals(cause.toString(), e.message)
    }
}
