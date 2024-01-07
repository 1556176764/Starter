package com.github.lib_starter.utils

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 线程调度池
 */
object DispatcherExecutor {
    //获取当前设备的 CPU 核心数。
    private val CPU_COUNT = Runtime.getRuntime().availableProcessors()
    //计算线程池的核心线程数，取值为 2 或 CPU 核心数减 1 的最小值，最大不超过 5。
    private val CORE_POOL_SIZE = 2.coerceAtLeast((CPU_COUNT - 1).coerceAtMost(5))
    //线程池的最大线程数，与核心线程数相同
    private val MAXIMUM_POOL_SIZE = CORE_POOL_SIZE

    // We want at least 2 threads and at most 4 threads in the core pool,
    // preferring to have 1 less than the CPU count to avoid saturating
    // the CPU with background work

    //空闲线程的存活时间，即超过核心线程数的线程在空闲一段时间后会被回收。
    private const val KEEP_ALIVE_SECONDS = 5
    //用于存储待执行的任务的阻塞队列，这里使用 LinkedBlockingQueue。
    /*
    相比`ArrayBlockingQueue`在插入删除节点性能方面更优，但是二者在`put()`, `take()`任务的时均需要加锁，
    `SynchronousQueue`使用无锁算法，根据节点的状态判断执行，而不需要用到锁
     */
    private val sPoolWorkQueue: BlockingQueue<Runnable> = LinkedBlockingQueue()
    //线程工厂，用于创建新的线程实例，这里自定义了一个
    private val sThreadFactory = DefaultThreadFactory()
    //拒绝执行处理程序，当线程池无法执行任务时，会使用该处理程序处理拒绝执行的任务。在这里，如果发生拒绝执行的情况，会创建一个新的缓存线程池，并将任务提交给它执行。
    private val sHandler = RejectedExecutionHandler { r, executor ->
        // 一般不会到这里
        Executors.newCachedThreadPool().execute(r)
    }

    /**
     * 获取CPU线程池
     *
     * @return
     */
    var cPUExecutor: ThreadPoolExecutor? = null
        private set

    /**
     * 获取IO线程池
     *
     * @return
     */
    var iOExecutor: ExecutorService? = null
        private set

    /**
     * The default thread factory.
     */
    private class DefaultThreadFactory : ThreadFactory {
        private val group: ThreadGroup?
        private val threadNumber = AtomicInteger(1)
        private val namePrefix: String
        override fun newThread(r: Runnable): Thread {
            val t = Thread(
                group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0
            )
            if (t.isDaemon) {
                t.isDaemon = false
            }
            if (t.priority != Thread.NORM_PRIORITY) {
                t.priority = Thread.NORM_PRIORITY
            }
            return t
        }

        companion object {
            private val poolNumber = AtomicInteger(1)
        }

        init {
            val s = System.getSecurityManager()
            group = s?.threadGroup ?: Thread.currentThread().threadGroup ?: null
            namePrefix = "TaskDispatcherPool-${poolNumber.getAndIncrement()}-Thread-"
        }
    }

    init {
        cPUExecutor = ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS.toLong(), TimeUnit.SECONDS,
            sPoolWorkQueue, sThreadFactory, sHandler
        )
        //对cPUExecutor 线程池设置允许核心线程超时。这意味着在核心线程处于空闲状态并且没有任务可执行时，允许这些核心线程自动超时关闭，从而释放资源。
        cPUExecutor?.allowCoreThreadTimeOut(true)
        /*
        创建了一个基于缓存的线程池 iOExecutor。
        该线程池会根据需要自动创建新的线程，并且在线程空闲一定时间后会自动回收线程，适用于执行大量的短期任务或者异步IO操作。
其中，sThreadFactory 是一个 DefaultThreadFactory 对象，用于创建线程。通过这个线程工厂，可以对线程的属性进行设置，如线程组、线程名称等。
         */
        iOExecutor = Executors.newCachedThreadPool(sThreadFactory)
    }
}