package com.github.lib_starter.sort

import androidx.collection.ArraySet
import com.github.lib_starter.task.Task
import com.github.lib_starter.utils.DispatcherLog

/**
 * 任务排序
 */
object TaskSortUtil {
    // 高优先级的Task
    private val sNewTasksHigh: MutableList<Task> = ArrayList()

    /**
     * 任务的有向无环图的拓扑排序
     *
     * @return
     */
    @Synchronized
    fun getSortResult(
        originTasks: List<Task>,
        clsLaunchTasks: List<Class<out Task>>
    ): List<Task> {
        val makeTime = System.currentTimeMillis()
        val dependSet: MutableSet<Int> = ArraySet()
        val graph = DirectionGraph(originTasks.size)

        //遍历每一个任务，创建其中依赖项和当前任务的邻接表
        for (i in originTasks.indices) {
            val task = originTasks[i]
            //如果该任务已经在处理或者它不需要等待别的任务做完就可以进行，则跳过
            if (task.isSend || task.dependsOn().isNullOrEmpty()) {
                continue
            }

            task.dependsOn()?.let { list ->
                for (clazz in list) {
                    clazz?.let { cls ->
                        val indexOfDepend = getIndexOfTask(originTasks, clsLaunchTasks, cls)
                        //index小于0时抛出异常
                        check(indexOfDepend >= 0) {
                            task.javaClass.simpleName +
                                    " depends on " + cls?.simpleName + " can not be found in task list "
                        }
                        //把该需要依赖的任务加入set
                        dependSet.add(indexOfDepend)
                        //建立该任务和i（被依赖的任务）的邻接表
                        graph.addEdge(indexOfDepend, i)
                    }
                }
            }
        }

        //创建拓扑排序，上面已经把邻接表加入进去了
        val indexList: List<Int> = graph.topologicalSort()
        val newTasksAll = getResultTasks(originTasks, dependSet, indexList)
        DispatcherLog.i("task analyse cost makeTime " + (System.currentTimeMillis() - makeTime))
        printAllTaskName(newTasksAll, false)
        return newTasksAll
    }

    /**
     * 获取最终任务列表
     * 顺序：被别人依赖的————》需要提升自己优先级的————》需要被等待的————》没有依赖的
     */
    private fun getResultTasks(
        originTasks: List<Task>,
        dependSet: Set<Int>,
        indexList: List<Int>
    ): List<Task> {
        val newTasksAll: MutableList<Task> = ArrayList(originTasks.size)
        // 被其他任务依赖的
        val newTasksDepended: MutableList<Task> = ArrayList()
        // 没有依赖的
        val newTasksWithOutDepend: MutableList<Task> = ArrayList()
        // 需要提升自己优先级的，先执行（这个先是相对于没有依赖的先）
        val newTasksRunAsSoon: MutableList<Task> = ArrayList()

        for (index in indexList) {
            if (dependSet.contains(index)) {
                newTasksDepended.add(originTasks[index])
            } else {
                val task = originTasks[index]
                if (task.needRunAsSoon()) {
                    newTasksRunAsSoon.add(task)
                } else {
                    newTasksWithOutDepend.add(task)
                }
            }
        }
        // 顺序：被别人依赖的————》需要提升自己优先级的————》需要被等待的————》没有依赖的
        sNewTasksHigh.addAll(newTasksDepended)
        sNewTasksHigh.addAll(newTasksRunAsSoon)
        newTasksAll.addAll(sNewTasksHigh)
        newTasksAll.addAll(newTasksWithOutDepend)
        return newTasksAll
    }

    private fun printAllTaskName(newTasksAll: List<Task>, isPrintName: Boolean) {
        if (!isPrintName) {
            return
        }
        for (task in newTasksAll) {
            DispatcherLog.i(task.javaClass.simpleName)
        }
    }

    val tasksHigh: List<Task>
        get() = sNewTasksHigh

    /**
     * 获取任务在任务列表中的index
     *
     * @param originTasks
     * @param clsLaunchTasks
     * @param cls
     * @return
     */
    private fun getIndexOfTask(
        originTasks: List<Task>,
        clsLaunchTasks: List<Class<out Task>>,
        cls: Class<*>
    ): Int {
        val index = clsLaunchTasks.indexOf(cls)
        if (index >= 0) {
            return index
        }

        // 仅仅是保护性代码
        val size = originTasks.size
        for (i in 0 until size) {
            if (cls.simpleName == originTasks[i].javaClass.simpleName) {
                return i
            }
        }
        return index
    }
}