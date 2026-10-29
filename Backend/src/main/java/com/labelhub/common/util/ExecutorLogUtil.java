package com.labelhub.common.util;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池日志辅助工具。
 */
public final class ExecutorLogUtil {

    private ExecutorLogUtil() {
    }

    public static String snapshot(Executor executor) {
        if (!(executor instanceof ThreadPoolTaskExecutor taskExecutor)) {
            return "executorType=" + (executor == null ? "null" : executor.getClass().getSimpleName());
        }
        try {
            ThreadPoolExecutor threadPool = taskExecutor.getThreadPoolExecutor();
            return "poolSize=" + threadPool.getPoolSize()
                    + ", activeCount=" + threadPool.getActiveCount()
                    + ", corePoolSize=" + taskExecutor.getCorePoolSize()
                    + ", maxPoolSize=" + taskExecutor.getMaxPoolSize()
                    + ", queueSize=" + threadPool.getQueue().size()
                    + ", queueRemainingCapacity=" + threadPool.getQueue().remainingCapacity()
                    + ", taskCount=" + threadPool.getTaskCount()
                    + ", completedTaskCount=" + threadPool.getCompletedTaskCount();
        } catch (Exception e) {
            return "executorSnapshotUnavailable=" + e.getMessage();
        }
    }
}
