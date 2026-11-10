package com.herohan.uvcdemo;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * 在 Android 设备上执行的插桩测试。
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        // 被测应用的 Context。
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.herohan.uvcdemo", appContext.getPackageName());
    }
}