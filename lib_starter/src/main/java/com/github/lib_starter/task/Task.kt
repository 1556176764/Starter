package com.github.lib_starter.task

import android.content.Context
import android.os.Process
import com.github.lib_starter.dispatcher.TaskDispatcher
import com.github.lib_starter.dispatcher.TaskDispatcher.Companion.context
import com.github.lib_starter.utils.DispatcherExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService

/**
 * 任务抽象类
 */
abstract class Task : ITask {
    protected var mContext: Context? = context

    // 当前进程是否是主进程
    protected var mIsMainProcess: Boolean = TaskDispatcher.isMainProcess

    // 是否正在等待
    @Volatile
    var isWaiting = false

    // 是否正在执行
    @Volatile
    var isRunning = false

    // Task是否执行完成
    @Volatile
    var isFinished = false

    // Task是否已经被分发
    @Volatile
    var isSend = false

    // 当前Task依赖的Task数量（需要等待被依赖的Task执行完毕才能执行自己），默认没有依赖
    /*
    CountDownLatch 是 Java 中的一个并发工具类，它提供了一种机制，允许一个或多个线程等待其他线程完成一组操作后再继续执行。
在这里，mDepends 的初始计数值被设置为 dependsOn()?.size ?: 0。dependsOn() 是一个方法调用，它返回一个可能为空的列表对象
并通过 ?.size 来获取列表的大小。如果列表为空，那么计数值就为 0。
在使用 CountDownLatch 进行线程协作时，其他线程可以调用 mDepends.await() 方法来等待计数值变为 0。当其他线程完成其任务后，可以通过调用 mDepends.countDown() 方法来减少计数值。
当计数值变为 0 时，等待 mDepends.await() 的线程将被唤醒并继续执行。
在这段代码中，mDepends 的计数值的设置可能依赖于某个列表的大小，以确保在列表中的所有操作完成之后才继续执行。
     */
    private val mDepends = CountDownLatch(
        dependsOn()?.size ?: 0
    )

    /**
     * 当前Task等待，让依赖的Task先执行
     */
    fun waitToSatisfy() {
        try {
            mDepends.await()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /**
     * 依赖的Task执行完一个
     */
    fun satisfy() {
        mDepends.countDown()
    }

    /**
     * 是否需要尽快执行，解决特殊场景的问题：一个Task耗时非常多但是优先级却一般，很有可能开始的时间较晚，
     * 导致最后只是在等它，这种可以早开始。
     *
     * @return
     */
    fun needRunAsSoon(): Boolean {
        return false
    }

    /**
     * Task的优先级，运行在主线程则不要去改优先级
     *
     * @return
     */
    override fun priority(): Int {
        return Process.THREAD_PRIORITY_BACKGROUND
    }

    /**
     * Task执行在哪个线程池，默认在IO的线程池；
     * CPU 密集型的一定要切换到DispatcherExecutor.getCPUExecutor();
     *
     * @return
     */
    override fun runOn(): ExecutorService? {
        return DispatcherExecutor.iOExecutor
    }

    /**
     * 异步线程执行的Task是否需要在被调用await的时候等待，默认不需要
     *
     * @return
     */
    override fun needWait(): Boolean {
        return false
    }

    /**
     * 当前Task依赖的Task集合（需要等待被依赖的Task执行完毕才能执行自己），默认没有依赖
     *
     * @return
     */
    override fun dependsOn(): List<Class<out Task?>?>? {
        return null
    }

    /**
     * 运行在主线程
     */
    override fun runOnMainThread(): Boolean {
        return false
    }


    override val tailRunnable: Runnable?
        get() = null

    override fun setTaskCallBack(callBack: TaskCallBack?) {}

    override fun needCall(): Boolean {
        return false
    }

    /**
     * 是否只在主进程，默认是
     *
     * @return
     */
    override fun onlyInMainProcess(): Boolean {
        return true
    }
}

/**
 * 主线程任务
 */
abstract class MainTask : Task() {
    override fun runOnMainThread(): Boolean {
        return true
    }
}