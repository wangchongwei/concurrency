package com.justin.concurrency;
/*
 * created by Justin on 2020/12/23
 * email: wcw1992yu@163.com
 * github: https://github.com/wangchongwei
 */

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class ThreadPool {

    ExecutorService pool = Executors.newCachedThreadPool();
    ExecutorService pool1 = Executors.newFixedThreadPool(1);
    ExecutorService pool2 = Executors.newScheduledThreadPool(1);
    ExecutorService pool3 = Executors.newSingleThreadScheduledExecutor();

    ExecutorService pool4 = new ThreadPoolExecutor(0, 10, 60,
            TimeUnit.SECONDS, new LinkedBlockingDeque<>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

}
