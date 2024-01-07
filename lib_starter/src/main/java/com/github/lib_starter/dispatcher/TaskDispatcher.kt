package com.github.lib_starter.dispatcher

import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.annotation.UiThread
import com.github.lib_starter.TaskStat
import com.github.lib_starter.sort.TaskSortUtil
import com.github.lib_starter.task.DispatchRunnable
import com.github.lib_starter.task.Task
import com.github.lib_starter.task.TaskCallBack
import com.github.lib_starter.utils.DispatcherExecutor
import com.github.lib_starter.utils.DispatcherLog
import com.github.lib_starter.utils.StaterUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 启动器调用类
 * 使用私有构造函数可以限制该类的实例化，通常用于创建单例模式的类
 */
class TaskDispatcher private constructor() {
    private var mStartTime: Long = 0
    private val mFutures: MutableList<Future<*>> = ArrayList()
    private var mAllTasks: MutableList<Task> = ArrayList()
    /*
    Class<out Task> 表示一个 Task 类的子类，即它是 Task 类或 Task 类的子类之一。
    通过使用 out 关键字，表示该类型是协变的，即可以将其赋值给 Task 类型的变量。
     */
    private val mClsAllTasks: MutableList<Class<out Task>> = ArrayList()

    @Volatile
    private var mMainThreadTasks: MutableList<Task> = ArrayList()
    private var mCountDownLatch: CountDownLatch? = null

    //保存需要Wait的Task的数量
    private val mNeedWaitCount = AtomicInteger()

    //调用了await的时候还没结束的且需要等待的Task
    private val mNeedWaitTasks: MutableList<Task> = ArrayList()

    //已经结束了的Task
    @Volatile
    private var mFinishedTasks: MutableList<Class<out Task>> = ArrayList(100)
    private val mDependedHashMap = HashMap<Class<out Task>, ArrayList<Task>?>()

    //启动器分析的次数，统计下分析的耗时
    private val mAnalyseCount = AtomicInteger()

    //返回类型是TaskDispatcher是因为后续使用.Start()启动
    fun addTask(task: Task?): TaskDispatcher {
        //let函数：返回值 = 最后一行 / return的表达式,用let则在这个函数范围内不需判空
        task?.let {
            collectDepends(it)
            mAllTasks.add(it)
            mClsAllTasks.add(it.javaClass)
            // 非主线程且需要wait的，主线程不需要CountDownLatch也是同步的
            if (ifNeedWait(it)) {
                mNeedWaitTasks.add(it)
                mNeedWaitCount.getAndIncrement()
            }
        }
        return this
    }

    /**
     * 建立该任务和其依赖任务的邻接表,使用哈希表来存储
     * 在这里将已完成的任务从任务的mDepends属性中删除，即mDepends.countDown()
     */
    private fun collectDepends(task: Task) {
        task.dependsOn()?.let { list ->
            for (cls in list) {
                cls?.let { cls ->
                    if (mDependedHashMap[cls] == null) {
                        mDependedHashMap[cls] = ArrayList()
                    }
                    mDependedHashMap[cls]?.add(task)
                    if (mFinishedTasks.contains(cls)) {
                        task.satisfy()
                    }
                }
            }
        }
    }

    private fun ifNeedWait(task: Task): Boolean {
        return !task.runOnMainThread() && task.needWait()
    }

    @UiThread
    fun start() {
        mStartTime = System.currentTimeMillis()
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw RuntimeException("must be called from UiThread")
        }

        if (!mAllTasks.isNullOrEmpty()) {
            mAnalyseCount.getAndIncrement()
            printDependedMsg(false)
            mAllTasks = TaskSortUtil.getSortResult(mAllTasks, mClsAllTasks).toMutableList()
            mCountDownLatch = CountDownLatch(mNeedWaitCount.get())
            sendAndExecuteAsyncTasks()
            DispatcherLog.i("task analyse cost ${(System.currentTimeMillis() - mStartTime)} begin main ")
            //因为上一个方法只执行不在main线程的task
            executeTaskMain()
        }
        DispatcherLog.i("task analyse cost startTime cost ${(System.currentTimeMillis() - mStartTime)}")
    }

    fun cancel() {
        for (future in mFutures) {
            future.cancel(true)
        }
    }

    //若在主线程则执行下列任务
    private fun executeTaskMain() {
        mStartTime = System.currentTimeMillis()
        for (task in mMainThreadTasks) {
            val time = System.currentTimeMillis()
            //不用调用线程池，是因为本就该在主线程执行该任务
            DispatchRunnable(task, this).run()
            DispatcherLog.i(
                "real main ${task.javaClass.simpleName} cost ${(System.currentTimeMillis() - time)}"
            )
        }
        DispatcherLog.i("mainTask cost ${(System.currentTimeMillis() - mStartTime)}")
    }

    /**
     * 把mAllTasks这个列表中的所有任务都送去执行
     * 注：执行任务的线程不在主进程，任务会被标记已经完成
     */
    private fun sendAndExecuteAsyncTasks() {
        for (task in mAllTasks) {
            if (task.onlyInMainProcess() && !isMainProcess) {
                markTaskDone(task)
            } else {
                sendTaskReal(task)
            }
            task.isSend = true
        }
    }

    /**
     * 查看被依赖的信息
     */
    private fun printDependedMsg(isPrintAllTask: Boolean) {
        DispatcherLog.i("needWait size : ${mNeedWaitCount.get()}")
        if (isPrintAllTask) {
            for (cls in mDependedHashMap.keys) {
                DispatcherLog.i("cls: ${cls.simpleName} ${mDependedHashMap[cls]?.size}")
                mDependedHashMap[cls]?.let {
                    for (task in it) {
                        DispatcherLog.i("cls:${task.javaClass.simpleName}")
                    }
                }
            }
        }
    }

    /**
     * 通知Children一个前置任务已完成
     *
     * @param launchTask
     */
    fun satisfyChildren(launchTask: Task) {
        val arrayList = mDependedHashMap[launchTask.javaClass]
        if (!arrayList.isNullOrEmpty()) {
            for (task in arrayList) {
                task.satisfy()
            }
        }
    }

    fun markTaskDone(task: Task) {
        if (ifNeedWait(task)) {
            mFinishedTasks.add(task.javaClass)
            mNeedWaitTasks.remove(task)
            mCountDownLatch?.countDown()
            mNeedWaitCount.getAndDecrement()
        }
    }

    private fun sendTaskReal(task: Task) {
        if (task.runOnMainThread()) {
            mMainThreadTasks.add(task)
            if (task.needCall()) {
                task.setTaskCallBack(object : TaskCallBack {
                    override fun call() {
                        //在call中设置当前任务已完成
                        TaskStat.markTaskDone()
                        task.isFinished = true
                        satisfyChildren(task)
                        markTaskDone(task)
                        DispatcherLog.i("${task.javaClass.simpleName} finish")
                        Log.i("testLog", "call")
                    }
                })
            }
        } else {
            // 直接发，是否执行取决于具体线程池
            val future = task.runOn()?.submit(DispatchRunnable(task, this))
            //适用于CPU密集操作
            val c = DispatcherExecutor.cPUExecutor?.submit(DispatchRunnable(task, this))
            future?.let {
                mFutures.add(it)
            }
        }
    }

    fun executeTask(task: Task) {
        if (ifNeedWait(task)) {
            mNeedWaitCount.getAndIncrement()
        }
        task.runOn()?.execute(DispatchRunnable(task, this))
    }

    /**
     * 阻塞当前线程，直到计数器减到0或者等待时间达到指定值为止。
     */
    @UiThread
    fun await() {
        try {
            if (DispatcherLog.isDebug) {
                DispatcherLog.i("still has ${mNeedWaitCount.get()}")
                for (task in mNeedWaitTasks) {
                    DispatcherLog.i("needWait: ${task.javaClass.simpleName}")
                }
            }
            if (mNeedWaitCount.get() > 0) {
                mCountDownLatch?.await(WAIT_TIME.toLong(), TimeUnit.MILLISECONDS)
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val WAIT_TIME = 10000
        var context: Application? = null
            private set
        var isMainProcess = false
            private set

        @Volatile
        private var sHasInit = false

        fun init(context: Application?) {
            context?.let {
                Companion.context = it
                sHasInit = true
                isMainProcess = StaterUtils.isMainProcess(context)
            }
        }

        /**
         * 注意：每次获取的都是新对象
         *
         * @return
         */
        fun createInstance(): TaskDispatcher {
            if (!sHasInit) {
                throw RuntimeException("must call TaskDispatcher.init first")
            }
            return TaskDispatcher()
        }
    }
}