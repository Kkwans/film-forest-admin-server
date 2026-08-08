package com.filmforest.crawler.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CrawlerJobCoordinator {

    private final ConcurrentHashMap<Long, AtomicBoolean> runningJobs = new ConcurrentHashMap<>();
    private final Set<Long> pendingCancellations = ConcurrentHashMap.newKeySet();
    private final Set<Long> submittedJobs = ConcurrentHashMap.newKeySet();

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
