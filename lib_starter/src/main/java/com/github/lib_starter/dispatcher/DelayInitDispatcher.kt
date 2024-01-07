package com.github.lib_starter.dispatcher

import android.os.Looper
import android.os.MessageQueue.IdleHandler
import com.github.lib_starter.task.DispatchRunnable
import com.github.lib_starter.task.Task
import java.util.LinkedList
import java.util.Queue

/**
 * 延迟初始化
 * 利用IdleHandler的等待主线程空闲特性，在空闲时才去执行任务
 *
 */
class DelayInitDispatcher {
    /**
     * 这是一个延迟初始化调度器的 Kotlin 类。它利用 IdleHandler 的特性，在主线程空闲时执行任务。以下是代码的解释：

    首先，定义了一个 DelayInitDispatcher 类，它包含了一个任务队列 mDelayTasks 和一个 IdleHandler 对象 mIdleHandler。
    mDelayTasks 是一个 Queue 的实例，用于存储延迟初始化的任务。
    mIdleHandler 是一个 IdleHandler 的实例，用于在主线程空闲时执行任务。
    在 mIdleHandler 的回调方法中，如果任务队列中有待执行的任务，就取出队列头部的任务并执行它。执行任务的过程是通过创建一个 DispatchRunnable 的实例并调用其 run() 方法实现的。
    最后，start() 方法用于启动延迟初始化调度器。它将 mIdleHandler 添加到当前线程的消息队列中的 IdleHandler 列表中，以便在主线程空闲时触发任务的执行。
    使用该延迟初始化调度器的步骤如下：

    创建一个 DelayInitDispatcher 的实例。
    使用 addTask() 方法向任务队列中添加延迟初始化的任务。
    最后，调用 start() 方法启动延迟初始化调度器，它会将 mIdleHandler 添加到主线程的消息队列中。
    这样，在主线程空闲时，延迟初始化调度器将逐个执行任务队列中的任务。
     */
    private val mDelayTasks: Queue<Task> = LinkedList()
    private val mIdleHandler = IdleHandler {
        if (mDelayTasks.size > 0) {
            val task = mDelayTasks.poll()
            DispatchRunnable(task).run()
        }
        !mDelayTasks.isEmpty()
    }

    fun addTask(task: Task): DelayInitDispatcher {
        mDelayTasks.add(task)
        return this
    }

    fun start() {
        Looper.myQueue().addIdleHandler(mIdleHandler)
    }
}