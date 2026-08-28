package com.filmforest.crawler.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CrawlerJobCoordinator {

    private final ConcurrentHashMap<Long, AtomicBoolean> runningJobs = new ConcurrentHashMap<>();
    private final Set<Long> pendingCancellations = ConcurrentHashMap.newKeySet();
    private final Set<Long> submittedJobs = ConcurrentHashMap.newKeySet();
    private final ReentrantLock launchLock = new ReentrantLock();

    /**
     * 串行化 Job 创建，并把锁保持到当前事务完成，避免手动和定时触发在提交前互相看不到对方。
     */
    public LaunchLock acquireLaunchLock() {
        launchLock.lock();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    launchLock.unlock();
                }
            });
            return new LaunchLock(false);
        }
        return new LaunchLock(true);
    }

    public final class LaunchLock implements AutoCloseable {
        private final boolean releaseOnClose;
        private boolean closed;

        private LaunchLock(boolean releaseOnClose) {
            this.releaseOnClose = releaseOnClose;
        }

        @Override
        public void close() {
            if (!closed && releaseOnClose) {
                closed = true;
                launchLock.unlock();
            }
        }
    }

    /**
     * 防止维护线程和事务提交事件把同一个 QUEUED Job 重复放入执行器。
     */
    public boolean reserveSubmission(Long jobId) {
        return submittedJobs.add(jobId);
    }

    public void releaseSubmission(Long jobId) {
        submittedJobs.remove(jobId);
    }

    public AtomicBoolean register(Long jobId) {
        AtomicBoolean cancellation = new AtomicBoolean(pendingCancellations.remove(jobId));
        runningJobs.put(jobId, cancellation);
        return cancellation;
    }

    public void unregister(Long jobId) {
        runningJobs.remove(jobId);
        pendingCancellations.remove(jobId);
        submittedJobs.remove(jobId);
    }

    public void requestCancellation(Long jobId) {
        AtomicBoolean cancellation = runningJobs.get(jobId);
        if (cancellation != null) {
            cancellation.set(true);
        } else {
            pendingCancellations.add(jobId);
        }
    }

    public Set<Long> runningJobIds() {
        return Set.copyOf(runningJobs.keySet());
    }
}
