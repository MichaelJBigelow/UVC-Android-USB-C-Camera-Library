package com.herohan.uvcapp.utils;

import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Watchdog extends Thread {
    static final String TAG = "Watchdog";

    /**
     * Debug flag.
     *
     * 调试标志。
     */
    public static final boolean DEBUG = false;

    // Set this to true to use debug default values.
    // 设置为 true 以使用调试默认值。
    static final boolean DB = true;

    static final long DEFAULT_TIMEOUT = DB ? 10 * 1000 : 60 * 1000;
    static final long CHECK_INTERVAL = DEFAULT_TIMEOUT / 2;

    // These are temporally ordered: larger values as lateness increases
    // 这些值按时间顺序排列：延迟越大，值越大
    static final int COMPLETED = 0;
    static final int WAITING = 1;
    static final int WAITED_HALF = 2;
    static final int OVERDUE = 3;

    static Watchdog sWatchdog;

    /*
     * This handler will be used to post message back onto the main thread
     * 此 handler 将用于把消息回投到主线程
     */
    final ArrayList<HandlerChecker> mHandlerCheckers = new ArrayList<>();
//    final HandlerChecker mMonitorChecker;

    /**
     * Used for checking status of handle threads and scheduling monitor callbacks.
     *
     * 用于检查 handler 线程的状态并调度监视器回调。
     */
    public final class HandlerChecker implements Runnable {
        private final Handler mHandler;
        private final String mName;
        private final long mWaitMax;
        private final ArrayList<Monitor> mMonitors = new ArrayList<Monitor>();
        private final ArrayList<Monitor> mMonitorQueue = new ArrayList<Monitor>();
        private boolean mCompleted;
        private Monitor mCurrentMonitor;
        private long mStartTime;
        private int mPauseCount;

        HandlerChecker(Handler handler, String name, long waitMaxMillis) {
            mHandler = handler;
            mName = name;
            mWaitMax = waitMaxMillis;
            mCompleted = true;
        }

        void addMonitorLocked(Monitor monitor) {
            // We don't want to update mMonitors when the Handler is in the middle of checking
            // all monitors. We will update mMonitors on the next schedule if it is safe
            // 当 Handler 正在检查所有监视器时，我们不希望更新 mMonitors。
            // 如果安全，我们将在下一次调度时更新 mMonitors
            mMonitorQueue.add(monitor);
        }

        public void scheduleCheckLocked() {
            if (mCompleted) {
                // Safe to update monitors in queue, Handler is not in the middle of work
                // 可以安全地更新队列中的监视器，Handler 当前没有在工作
                mMonitors.addAll(mMonitorQueue);
                mMonitorQueue.clear();
            }

            boolean isIdle = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                isIdle = mHandler.getLooper().getQueue().isIdle();
            }

            if ((mMonitors.size() == 0 && isIdle)
                    || (mPauseCount > 0)) {
                // Don't schedule until after resume OR
                // If the target looper has not recently been idling, then
                // there is no reason to enqueue our checker on it since that
                // is as good as it not being deadlocked.  This avoid having
                // to do a context switch to check the thread. Note that we
                // only do this if we have no monitors since those would need to
                // be executed at this point.
                // 在 resume 之前不进行调度，或者
                // 如果目标 looper 最近没有处于空闲状态，那么
                // 就没有必要把我们的 checker 加入其队列，因为这
                // 等同于它没有发生死锁。这样可以避免
                // 为了检查该线程而进行上下文切换。注意，我们
                // 只在没有监视器时才这样做，因为监视器在此时
                // 需要被执行。
                mCompleted = true;
                return;
            }

            if (!mCompleted) {
                // we already have a check in flight, so no need
                // 已有一个检查正在进行中，因此无需再次检查
                return;
            }

            mCompleted = false;
            mCurrentMonitor = null;
            mStartTime = SystemClock.uptimeMillis();
            if (mHandler.getLooper().getThread().isAlive()) {
                mHandler.postAtFrontOfQueue(this);
            }
        }

        boolean isOverdueLocked() {
            return (!mCompleted) && (SystemClock.uptimeMillis() > mStartTime + mWaitMax);
        }

        public int getCompletionStateLocked() {
            if (mCompleted) {
                return COMPLETED;
            } else {
                long latency = SystemClock.uptimeMillis() - mStartTime;
                if (latency < mWaitMax / 2) {
                    return WAITING;
                } else if (latency < mWaitMax) {
                    return WAITED_HALF;
                }
            }
            return OVERDUE;
        }

        public Thread getThread() {
            return mHandler.getLooper().getThread();
        }

        public String getName() {
            return mName;
        }

        String describeBlockedStateLocked() {
            if (mCurrentMonitor == null) {
                return "Blocked in handler on " + mName + " (" + getThread().getName() + ")";
            } else {
                return "Blocked in monitor " + mCurrentMonitor.getClass().getName()
                        + " on " + mName + " (" + getThread().getName() + ")";
            }
        }

        @Override
        public void run() {
            // Once we get here, we ensure that mMonitors does not change even if we call
            // #addMonitorLocked because we first add the new monitors to mMonitorQueue and
            // move them to mMonitors on the next schedule when mCompleted is true, at which
            // point we have completed execution of this method.
            // 执行到这里时，即使调用
            // #addMonitorLocked，也能确保 mMonitors 不会改变，因为我们会先把新的监视器添加到 mMonitorQueue，
            // 然后在下一次调度且 mCompleted 为 true 时再将它们移入 mMonitors，
            // 而此时本方法已经执行完毕。
            final int size = mMonitors.size();
            for (int i = 0; i < size; i++) {
                synchronized (Watchdog.this) {
                    mCurrentMonitor = mMonitors.get(i);
                }
                mCurrentMonitor.monitor();
            }

            synchronized (Watchdog.this) {
                mCompleted = true;
                mCurrentMonitor = null;
            }
        }

        /**
         * Pause the HandlerChecker.
         *
         * 暂停 HandlerChecker。
         */
        public void pauseLocked(String reason) {
            mPauseCount++;
            // Mark as completed, because there's a chance we called this after the watchog
            // thread loop called Object#wait after 'WAITED_HALF'. In that case we want to ensure
            // the next call to #getCompletionStateLocked for this checker returns 'COMPLETED'
            // 标记为已完成，因为我们有可能是在 watchdog
            // 线程循环于 'WAITED_HALF' 之后调用 Object#wait 之后才调用本方法的。这种情况下，我们要确保
            // 下一次对该 checker 调用 #getCompletionStateLocked 时返回 'COMPLETED'
            mCompleted = true;
            Log.i(TAG, "Pausing HandlerChecker: " + mName + " for reason: "
                    + reason + ". Pause count: " + mPauseCount);
        }

        /**
         * Resume the HandlerChecker from the last {@link #pauseLocked}.
         *
         * 从上一次 {@link #pauseLocked} 处恢复 HandlerChecker。
         */
        public void resumeLocked(String reason) {
            if (mPauseCount > 0) {
                mPauseCount--;
                Log.i(TAG, "Resuming HandlerChecker: " + mName + " for reason: "
                        + reason + ". Pause count: " + mPauseCount);
            } else {
                Log.wtf(TAG, "Already resumed HandlerChecker: " + mName);
            }
        }
    }

    public interface Monitor {
        void monitor();
    }

    public static Watchdog getInstance() {
        if (sWatchdog == null) {
            synchronized (Watchdog.class) {
                if (sWatchdog == null) {
                    sWatchdog = new Watchdog();
                }
            }
        }

        return sWatchdog;
    }

    private Watchdog() {
        super("watchdog");
        // Initialize handler checkers for each common thread we want to check.  Note
        // that we are not currently checking the background thread, since it can
        // potentially hold longer running operations with no guarantees about the timeliness
        // of operations there.
        // 为我们想要检查的每个常用线程初始化 handler checker。注意，
        // 我们目前不检查后台线程，因为它
        // 可能持有运行时间较长的操作，且无法保证
        // 其中操作的及时性。

//        // The shared foreground thread is the main checker.  It is where we
//        // will also dispatch monitor checks and do other work.
//        mMonitorChecker = new HandlerChecker(FgThread.getHandler(),
//                "foreground thread", DEFAULT_TIMEOUT);
//        mHandlerCheckers.add(mMonitorChecker);
//        // Add checker for main thread.  We only do a quick check since there
//        // can be UI running on the thread.
//        mHandlerCheckers.add(new HandlerChecker(new Handler(Looper.getMainLooper()),
//                "main thread", DEFAULT_TIMEOUT));
//        // Add checker for shared UI thread.
//        mHandlerCheckers.add(new HandlerChecker(UiThread.getHandler(),
//                "ui thread", DEFAULT_TIMEOUT));
//
//        // Initialize monitor for Binder threads.
//        addMonitor(new BinderThreadMonitor());
    }

//    public void addMonitor(Monitor monitor) {
//        synchronized (this) {
//            mMonitorChecker.addMonitorLocked(monitor);
//        }
//    }

    public void addThread(Handler thread) {
        addThread(thread, DEFAULT_TIMEOUT);
    }

    public void addThread(Handler thread, long timeoutMillis) {
        synchronized (this) {
            final String name = thread.getLooper().getThread().getName();
            mHandlerCheckers.add(new HandlerChecker(thread, name, timeoutMillis));
        }
    }

    public void removeThread(Handler thread) {
        for (HandlerChecker hc : mHandlerCheckers) {
            if (hc.mHandler == thread) {
                mHandlerCheckers.remove(hc);
                break;
            }
        }
    }

    /**
     * Pauses Watchdog action for the currently running thread. Useful before executing long running
     * operations that could falsely trigger the watchdog. Each call to this will require a matching
     * call to {@link #resumeWatchingCurrentThread}.
     *
     * <p>If the current thread has not been added to the Watchdog, this call is a no-op.
     *
     * <p>If the Watchdog is already paused for the current thread, this call adds
     * adds another pause and will require an additional resumeCurrentThread to resume.
     *
     * <p>Note: Use with care, as any deadlocks on the current thread will be undetected until all
     * pauses have been resumed.
     *
     * <p>暂停 Watchdog 对当前正在运行的线程的监控。适用于在执行
     * 可能误触发 watchdog 的长时间运行操作之前。每调用一次本方法，都需要对应调用一次
     * {@link #resumeWatchingCurrentThread}。
     *
     * <p>如果当前线程尚未添加到 Watchdog，则本调用不执行任何操作。
     *
     * <p>如果 Watchdog 对当前线程已处于暂停状态，则本调用会再增加
     * 一次暂停，并且需要额外调用一次 resumeCurrentThread 才能恢复。
     *
     * <p>注意：请谨慎使用，因为在所有暂停都被恢复之前，当前线程上的任何死锁都不会被检测到。
     */
    public void pauseWatchingCurrentThread(String reason) {
        synchronized (this) {
            for (HandlerChecker hc : mHandlerCheckers) {
                if (Thread.currentThread().equals(hc.getThread())) {
                    hc.pauseLocked(reason);
                }
            }
        }
    }

    /**
     * Resumes the last pause from {@link #pauseWatchingCurrentThread} for the currently running
     * thread.
     *
     * <p>If the current thread has not been added to the Watchdog, this call is a no-op.
     *
     * <p>If the Watchdog action for the current thread is already resumed, this call logs a wtf.
     *
     * <p>If all pauses have been resumed, the Watchdog action is finally resumed, otherwise,
     * the Watchdog action for the current thread remains paused until resume is called at least
     * as many times as the calls to pause.
     *
     * <p>从 {@link #pauseWatchingCurrentThread} 的上一次暂停处恢复对当前正在运行的
     * 线程的监控。
     *
     * <p>如果当前线程尚未添加到 Watchdog，则本调用不执行任何操作。
     *
     * <p>如果 Watchdog 对当前线程的监控已经处于恢复状态，则本调用会记录一条 wtf 日志。
     *
     * <p>如果所有暂停都已被恢复，则最终恢复 Watchdog 的监控；否则，
     * 对当前线程的 Watchdog 监控将一直保持暂停，直到 resume 的调用次数
     * 至少与 pause 的调用次数相同。
     */
    public void resumeWatchingCurrentThread(String reason) {
        synchronized (this) {
            for (HandlerChecker hc : mHandlerCheckers) {
                if (Thread.currentThread().equals(hc.getThread())) {
                    hc.resumeLocked(reason);
                }
            }
        }
    }

    private int evaluateCheckerCompletionLocked() {
        int state = COMPLETED;
        for (int i = 0; i < mHandlerCheckers.size(); i++) {
            HandlerChecker hc = mHandlerCheckers.get(i);
            state = Math.max(state, hc.getCompletionStateLocked());
        }
        return state;
    }

    private ArrayList<HandlerChecker> getBlockedCheckersLocked() {
        ArrayList<HandlerChecker> checkers = new ArrayList<HandlerChecker>();
        for (int i = 0; i < mHandlerCheckers.size(); i++) {
            HandlerChecker hc = mHandlerCheckers.get(i);
            if (hc.isOverdueLocked()) {
                checkers.add(hc);
            }
        }
        return checkers;
    }

    private String describeCheckersLocked(List<HandlerChecker> checkers) {
        StringBuilder builder = new StringBuilder(128);
        for (int i = 0; i < checkers.size(); i++) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(checkers.get(i).describeBlockedStateLocked());
        }
        return builder.toString();
    }

    @Override
    public void run() {
        boolean waitedHalf = false;
        while (true) {
            final List<HandlerChecker> blockedCheckers;
            final String subject;
            int debuggerWasConnected = 0;
            synchronized (this) {
                long timeout = CHECK_INTERVAL;
                // Make sure we (re)spin the checkers that have become idle within
                // this wait-and-check interval
                // 确保在本次等待并检查的间隔内
                // 重新启动已变为空闲的 checker
                for (int i = 0; i < mHandlerCheckers.size(); i++) {
                    HandlerChecker hc = mHandlerCheckers.get(i);
                    hc.scheduleCheckLocked();
                }
                Log.d(TAG, "mHandlerCheckers count:" + mHandlerCheckers.size());

                if (debuggerWasConnected > 0) {
                    debuggerWasConnected--;
                }

                // NOTE: We use uptimeMillis() here because we do not want to increment the time we
                // wait while asleep. If the device is asleep then the thing that we are waiting
                // to timeout on is asleep as well and won't have a chance to run, causing a false
                // positive on when to kill things.
                // 注意：这里使用 uptimeMillis()，因为我们不希望在休眠期间增加等待时间。
                // 如果设备处于休眠状态，那么我们正在等待其超时的对象也处于休眠状态，
                // 没有机会运行，这会导致在何时终止进程上出现误判。
                long start = SystemClock.uptimeMillis();
                while (timeout > 0) {
                    if (Debug.isDebuggerConnected()) {
                        debuggerWasConnected = 2;
                    }
                    try {
                        wait(timeout);
                        // Note: mHandlerCheckers and mMonitorChecker may have changed after waiting
                        // 注意：等待之后，mHandlerCheckers 和 mMonitorChecker 可能已经改变
                    } catch (InterruptedException e) {
                        Log.wtf(TAG, e);
                    }
                    if (Debug.isDebuggerConnected()) {
                        debuggerWasConnected = 2;
                    }
                    timeout = CHECK_INTERVAL - (SystemClock.uptimeMillis() - start);
                }

                final int waitState = evaluateCheckerCompletionLocked();
                if (waitState == COMPLETED) {
                    // The monitors have returned; reset
                    // 监视器已返回；重置
                    waitedHalf = false;
                    continue;
                } else if (waitState == WAITING) {
                    // still waiting but within their configured intervals; back off and recheck
                    // 仍在等待，但仍在其配置的间隔内；退避并重新检查
                    continue;
                } else if (waitState == WAITED_HALF) {
                    if (!waitedHalf) {
                        Log.i(TAG, "WAITED_HALF");
                        // We've waited half the deadlock-detection interval.  Pull a stack
                        // trace and wait another half.
                        // 我们已经等待了死锁检测间隔的一半。获取堆栈
                        // 跟踪，然后再等待另一半。
                        waitedHalf = true;
                    }
                    continue;
                }

                // something is overdue!
                // 有东西超时了！
                blockedCheckers = getBlockedCheckersLocked();
                subject = describeCheckersLocked(blockedCheckers);
            }

            // If we got here, that means that the system is most likely hung.
            // First collect stack traces from all threads of the system process.
            // Then kill this process so that the system will restart.
            // 如果执行到这里，说明系统很可能已经挂起。
            // 首先收集系统进程所有线程的堆栈跟踪。
            // 然后终止此进程，以便系统重新启动。

            // Give some extra time to make sure the stack traces get written.
            // The system's been hanging for a minute, another second or two won't hurt much.
            // 多给一些时间，确保堆栈跟踪被写入。
            // 系统已经挂起了一分钟，再多等一两秒也没有太大影响。
            SystemClock.sleep(5000);

            // Trigger the kernel to dump all blocked threads, and backtraces on all CPUs to the kernel log
            // 触发内核将所有被阻塞的线程以及所有 CPU 上的回溯转储到内核日志
            doSysRq('w');
            doSysRq('l');

            // Only kill the process if the debugger is not attached.
            // 仅在未连接调试器时才终止进程。
            if (Debug.isDebuggerConnected()) {
                debuggerWasConnected = 2;
            }
            if (debuggerWasConnected >= 2) {
                Log.w(TAG, "Debugger connected");
            } else if (debuggerWasConnected > 0) {
                Log.w(TAG, "Debugger was connected");
            } else {
                Log.w(TAG, "*** WATCHDOG DIAGNOSE: " + subject);
                if (WatchdogDiagnostics.diagnoseCheckers(blockedCheckers)) {
                    break;
                }
            }

            waitedHalf = false;
        }
    }

    private void doSysRq(char c) {
        FileWriter sysrqTrigger = null;
        try {
            sysrqTrigger = new FileWriter("/proc/sysrq-trigger");
            sysrqTrigger.write(c);
        } catch (IOException e) {
            Log.w(TAG, "Failed to write to /proc/sysrq-trigger", e);
        } finally {
            try {
                if (sysrqTrigger != null) {
                    sysrqTrigger.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
