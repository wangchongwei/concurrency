# 并发



## 线程池
线程池是一个线程容器，可以对线程进行调度、复用。

创建线程池的几种方法：
```
ExecutorService pool = Executors.newCachedThreadPool();
ExecutorService pool1 = Executors.newFixedThreadPool(1);
ExecutorService pool2 = Executors.newScheduledThreadPool(1);
ExecutorService pool3 = Executors.newSingleThreadScheduledExecutor();

ExecutorService pool4 = new ThreadPoolExecutor(0, 10, 60,
            TimeUnit.SECONDS, new LinkedBlockingDeque<>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
```
最后都会执行到ThreadPoolExecutor的构造函数中
```
public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }
```
即时是ThreadPoolExecutor的其他几个构造函数，最后也会调用到这个7参的构造函数

### ThreadPoolExecutor

#### 构造函数入参
下面解释一下这几个参数的含义

* int corePoolSize : 核心线程数，一般最大取系统处理器数量，因为 时间片轮转算法，这样可以减少时间片中切换线程的耗时
* int maximumPoolSize： 最大线程数，最大能同时存在的线程数量
* long keepAliveTime： 线程能空闲的最大时长
* TimeUnit unit： 时长单位
* BlockingQueue<Runnable> workQueue： 阻塞队列 当核心线程数满时，提交任务会优先将任务提交到队列
* ThreadFactory threadFactory： 线程工厂，当有任务执行需要新建线程时，会调用线程工厂来创建线程
* RejectedExecutionHandler handler： 拒绝策略，当核心线程数满了、阻塞队列满了、最大线程数也满了，此时还有提交的任务时，会执行该拒绝策略



#### ThreadPoolExecutor工作流程
经过上面几个入参的讲解，对任务的提交可能有一个大致的了解，接下来对ThreadPoolExecutor中一个任务到执行完毕的整体流程进行一个讲述。

##### execute 提交任务

```
public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        else if (!addWorker(command, false))
            reject(command);
    }
```
ctl是一个原子Integer对象，存储的是正在运行的线程数
看英文注释也写的很清楚了，分三部，
1、判断正在运行的线程是否小于核心线程数，是的话则 调用 addWorker(command, true)；并不再向下执行
2、判断队列能否添加command 以及 线程池是否还在运行，而且双重检测，成功将加任务加入到阻塞队列，
    再进行第二次判断，如果线程池没有再运行 && 移除这个任务，执行拒绝
    或者工作线程数 == 0， 则直接 addWorker(null, false);
3、尝试添加失败，执行 reject(command)


##### addWorker
查看添加任务的函数

```
retry:
    for (;;) {
        int c = ctl.get();
        int rs = runStateOf(c);
            
        // Check if queue empty only if necessary.
        if (rs >= SHUTDOWN &&
            ! (rs == SHUTDOWN &&
            firstTask == null &&
            ! workQueue.isEmpty()))
                return false;
            
        for (;;) {
            int wc = workerCountOf(c);
            if (wc >= CAPACITY ||
                wc >= (core ? corePoolSize : maximumPoolSize))
                return false;
            if (compareAndIncrementWorkerCount(c))
                break retry;
            c = ctl.get();  // Re-read ctl
            if (runStateOf(c) != rs)
                continue retry;
            // else CAS failed due to workerCount change; retry inner loop
        }
    }
```


外层for循环： 当线程池已经 SHUTDOWN时，不能添加任务
内层for循环：当前线程数大于总容量 ｜｜ 为添加核心线程时，> 核心线程数 当为非核心线程时，> 最大线程数，不能添加任务
    如果能添加时，终止外层for循环
    
compareAndIncrementWorkerCount 函数是将当前的正在运行的线程数 + 1；
    
```
        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int rs = runStateOf(ctl.get());

                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                addWorkerFailed(w);
        }


```
Worker 是ThreadPoolExecutor中的内部类，implements Runnable
此处就是在构建一个Worker，并启动线程执行。

所以整体流程大概是：

* 1、当工作线程小于核心线程数时，新建一个Work对象，启动线程执行任务
* 2、 当工作线程大于等于核心线程数，将任务加入阻塞队列成功，线程池被终止了则移除该任务，并执行拒绝策略，
    如果此时工作线程数为0，则也新建一个Work对象，启动线程执行任务