package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerTaskLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内 SSE 广播器。admin-server 当前为单实例，Job 状态本身仍以数据库为准，
 * SSE 断线后由客户端重新订阅并读取快照，不依赖事件不丢失。
 */
@Slf4j
@Component
public class CrawlerProgressEventHub {

    static final long ALL_JOBS_STREAM = 0L;
    private static final long SSE_TIMEOUT_MS = 0L;
    private static final long KEEP_ALIVE_INTERVAL_MS = 15_000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter registerJob(Long jobId) {
        return register(jobId == null ? ALL_JOBS_STREAM : jobId);
    }

    public SseEmitter registerAllJobs() {
        return register(ALL_JOBS_STREAM);
    }

    public void sendSnapshot(SseEmitter emitter, CrawlerProgressEvent event) {
        sendEvent(emitter, event);
    }

    public void publish(CrawlerProgressEvent event) {
        if (event == null) return;
        if (event.jobId() != null) {
            broadcast(event.jobId(), event);
        }
        broadcast(ALL_JOBS_STREAM, event);
    }

    @Scheduled(fixedDelay = KEEP_ALIVE_INTERVAL_MS)
    public void sendKeepAlive() {
        subscribers.forEach((stream, emitters) -> emitters.removeIf(emitter -> !sendKeepAlive(emitter)));
    }

    private SseEmitter register(long stream) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> streamSubscribers = subscribers.computeIfAbsent(
                stream, ignored -> new CopyOnWriteArrayList<>());
        streamSubscribers.add(emitter);
        Runnable cleanup = () -> remove(stream, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        return emitter;
    }

    private void broadcast(long stream, CrawlerProgressEvent event) {
        List<SseEmitter> emitters = subscribers.get(stream);
        if (emitters == null || emitters.isEmpty()) return;
        emitters.removeIf(emitter -> !sendEvent(emitter, event));
    }

    private boolean sendEvent(SseEmitter emitter, CrawlerProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("crawler")
                    .data(event, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException failure) {
            return false;
        }
    }

    private boolean sendKeepAlive(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("keepalive"));
            return true;
        } catch (IOException | IllegalStateException failure) {
            return false;
        }
    }

    private void remove(long stream, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(stream);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) subscribers.remove(stream, emitters);
    }
}
