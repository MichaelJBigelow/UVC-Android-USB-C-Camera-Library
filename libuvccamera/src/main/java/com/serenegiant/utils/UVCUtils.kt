package com.serenegiant.utils

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException

/**
 * @author admin
 */
object UVCUtils {
    private const val TAG = "UVCUtils"

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var sApplication: Application? = null

    /**
     * Init utils.
     * <p>Init it in the class of Application.</p>
     * <p>初始化工具类。请在 Application 类中初始化。</p>
     *
     * @param context context / 上下文
     */
    @JvmStatic
    fun init(context: Context?) {
        if (context == null) {
            init(getApplicationByReflect())
            return
        }
        init(context.applicationContext as Application)
    }

    /**
     * Init utils.
     * <p>Init it in the class of Application.</p>
     * <p>初始化工具类。请在 Application 类中初始化。</p>
     *
     * @param app application / 应用程序
     */
    @JvmStatic
    fun init(app: Application?) {
        val current = sApplication
        if (current == null) {
            sApplication = app ?: getApplicationByReflect()
        } else if (app != null && app.javaClass != current.javaClass) {
            sApplication = app
        }
    }

    /**
     * Return the context of Application object.
     * <p>返回 Application 对象的上下文。</p>
     *
     * @return the context of Application object / Application 对象的上下文
     */
    @JvmStatic
    fun getApplication(): Application {
        sApplication?.let { return it }
        val app = getApplicationByReflect()
        init(app)
        return app
    }

    private fun getApplicationByReflect(): Application {
        try {
            @SuppressLint("PrivateApi")
            val activityThread = Class.forName("android.app.ActivityThread")
            val thread = activityThread.getMethod("currentActivityThread").invoke(null)
            val app = activityThread.getMethod("getApplication").invoke(thread)
            return app as? Application ?: throw NullPointerException("u should init first")
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "getApplicationByReflect:", e)
        } catch (e: IllegalAccessException) {
            Log.w(TAG, "getApplicationByReflect:", e)
        } catch (e: InvocationTargetException) {
            Log.w(TAG, "getApplicationByReflect:", e)
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "getApplicationByReflect:", e)
        }
        throw NullPointerException("u should init first")
    }
}
