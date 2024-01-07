package com.github.lib_starter

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * 任务开始
 */
object TaskStat {


    private const val TAG = "TaskStat"

    @Volatile
    private var sCurrentSituation = ""
    private val sBeans: MutableList<TaskStatBean> = ArrayList()
    private var sTaskDoneCount = AtomicInteger()
    private const val sOpenLaunchStat = false // 是否开启统计
    var currentSituation: String
        get() = sCurrentSituation
        //放入一个字符串
        set(currentSituation) {
            //判断是否开启统计
            if (!sOpenLaunchStat) {
                return
            }
            Log.i(TAG, "currentSituation   $currentSituation")
            sCurrentSituation = currentSituation
            setLaunchStat()
        }

    //标记某个任务已被完成
    fun markTaskDone() {
        sTaskDoneCount.getAndIncrement()
    }

    fun setLaunchStat() {
        val bean = TaskStatBean()
        bean.situation = sCurrentSituation
        bean.count = sTaskDoneCount.get()
        sBeans.add(bean)
        sTaskDoneCount = AtomicInteger(0)
    }
}