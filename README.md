#### task

##### startTask

设置了一些任务

设置方法就是重写Task中的任务（需要异步执行的任务设置needWait为true并交给对应的线程池执行，需要在主线程执行的任务，则设置runOnMainThread()为true，参考InitAlert任务）

这里把几个任务的needWait参数都设置为true的原因：因为他们都不在主线程执行，因此需要考虑同步问题，CountDownLatch(mNeedWaitCount.get())即等待这些任务完成才可以

- 初始化SumHelper
- 初始化MMKV
  - 要等InitSumHelperTask::class.java运行结束
- 初始化AppManager
- SmartRefreshLayout
- InitArouterTask()

#### SumApplication

在这里完成任务分发，使用线程池来执行。这里是作者自定义的全局Application，用来完成初始化操作

#### dispatcher

##### DelayInitDispatcher

- 延迟初始化
  * 利用IdleHandler的等待主线程空闲特性，在空闲时才去执行任务

##### TaskDispatcher

启动器调用类

内有静态内部类，createInstance()使得每次调用都会创建一个新的TaskDispatcher()，且要求必须初始化来设置context和是否允许在主线程



- 在这个类中添加任务并启动任务
- 该类记录执行计时，用一个装着执行耗时任务的future的list和普通任务的list
- 主线程是同步，因为存在在主线程更新的操作
- 其他线程使用CountDownLatch保证线程间同步，保证多个线程都执行完，某个线程才会结束操作
  - 该countDownLatch不能用来解决经典的消费者问题，因为它是一次性的

- start()方法在通过注解设置必须在UI线程，只有在UI线程上执行的代码才能访问和更新UI元素
  - TaskSortUtil.getSortResult(mAllTasks, mClsAllTasks).toMutableList()，得到任务执行顺序
    - 被别人依赖的————》需要提升自己优先级的————》需要被等待的————》没有依赖的

  - CountDownLatch(mNeedWaitCount.get())来控制需阻塞的数量，等待这些执行完成后UI线程的任务才能执行

  - 把mAllTasks这个列表中的所有任务都送去执行，sendAndExecuteAsyncTasks()遍历mAllTaak中的每一个任务
    - if该任务在主线程，则标记已完成并跳过

    - 不在主线程，则sendTaskReal(task)
      - 把这个任务提交到线程池，task.runOn()?.submit(DispatchRunnable(task, this))

      - 在futureList中添加该任务的执行结果

- await()方法通过注解@UiThread实现了必须在主线程使用CountDownLatch保证线程间同步
  - mCountDownLatch?.await(WAIT_TIME.toLong(), TimeUnit.MILLISECONDS) 方法会使当前线程等待，直到倒计时器减到0或者等待时间超过了指定的时间（WAIT_TIME）


#### sort

##### DirectionGraph

有向无环图的拓扑排序算法

经典算法BFS,先建表得到邻接表，然后统计入度为0的数组，再进入其进行一一对比

##### TaskSortUtil

任务排序

把排序后的列表以List<Task>发送出去，顺序：被别人依赖的————》需要提升自己优先级的————》需要被等待的————》没有依赖的

#### task

##### DispatchRunnable

任务真正执行的地方，两个构造器，在指定任务时可以选择是否指定TaskDispatcher

继承了Runnable，因此可以被线程池执行，但是没有返回结果

这里的run()方法

- 会指定该线程在进程中执行的顺序，在task抽象类默认设置优先级为后台任务等级
- 设置isWaiting为true
- mTask.waitToSatisfy()，让它依赖的任务先执行
- 记录等待时间并调用task.run（）
- 获取mTask.tailRunnable即task的尾部任务，并执行它（实际上都没有使用，我猜测是不需要）
- 如果该任务不需要回调或者不在主线程运行
  - 调用TaskDispatcher，通知需要等待它完成的子任务 它完成，并标记该任务已完成

##### ITask

接口，定义了什么是task

优先级的范围，可根据Task重要程度及工作量指定；之后根据实际情况决定是否有必要放更大

- priority -> Process.setThreadPriority(mTask.priority())设置该线程的优先级使用
- run()
- runOn()，Task执行所在的线程池
- dependsOn(): List<Class<out Task?>?>?，用于得到依赖关系
- needWait()，异步线程执行的Task是否需要在被调用await的时候等待，默认不需要
- runOnMainThread(): Boolean，是否需要运行在主线程上
- onlyInMainProcess(): Boolean 主进程
- tailRunnable: Runnable?，Task主任务执行完成之后需要执行的任务
- setTaskCallBack(callBack: TaskCallBack?)，设置任务的回调
  - 目前设置的回调是在call中设置当前任务已完成，并通知给被依赖的任务
  - 为什么不在回调中执行下个任务，形成递归
    - 回调地狱（Callback Hell）：如果任务之间存在复杂的依赖关系或者任务数量较多，使用回调来管理任务流程可能会导致代码嵌套层级过深，代码变得难以理解和维护，这被称为回调地狱。
    - 难以管理任务顺序：使用回调来协调任务顺序时，需要手动处理任务之间的依赖关系和执行顺序。这可能会导致逻辑混乱、容易出错，并且难以进行扩展和修改。
    - 可读性和可维护性差：使用回调来管理任务流程会使代码逻辑分散在各个回调函数中，增加了代码的复杂性和可读性。当任务之间存在复杂的依赖关系时，代码的理解和维护变得更加困难。
    - 难以处理错误和异常：在使用回调进行任务协调时，错误和异常处理变得复杂。如果一个任务发生错误，需要手动处理错误并决定是否继续执行下一个任务，这增加了出错的可能性和代码的复杂性。
    - 不利于并发控制：如果任务之间需要进行并发控制，例如限制同时执行的任务数量或者实现线程间同步，使用回调来管理任务流程可能会更加困难和复杂。

- needCall(): Boolean

##### Task

抽象类，继承接口ITask

用抽象接口来确定继承类的主次关系，用接口来表示实现某个特定功能

- mContext: Context? = context
- mIsMainProcess: Boolean = TaskDispatcher.isMainProcess
- 一些变量，比如isSend,isEnd,isWaiting,isRunning

- mDepends = CountDownLatch(
      dependsOn()?.size ?: 0
  )
  - 使用CountDownLatch来判断是否需要等待，它允许一个或多个线程等待其他线程完成一组操作后再继续执行，因此通过这个来保证能够按照拓扑排序得到的依赖顺序执行任务
    - 即在初始化任务时就应该输入

- waitToSatisfy()
  - mDepends.await()

- satisfy()
  - mDepends.countDown()

- override fun priority(): Int {
      return Process.THREAD_PRIORITY_BACKGROUND
  }
  - 重写了优先级，让优先级都一样，这样会按照列表中顺序来执行


#### Util

##### DispatcherExecutor

自己拟定参数的线程调度池
分别设置了IO线程池（使用自定义参数）和CPU线程池（使用系统的指定线程池，CachedThreadPool，可根据实际情况调整线程数量的线程池）

##### DispatcherLog

判断是不是debug环境

##### LaunchTimer

判断任务执行时间，暂未用到

##### StaterUtils

一些小方法，比如当前process的名字等等

#### TaskStat

任务开始，是单例，用来统计完成任务的数量

#### TaskStatBean

存放已开始任务的两个属性
