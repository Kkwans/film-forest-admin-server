package com.filmforest.poster;

import com.filmforest.common.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端海报本地化任务控制与进度查询。 */
@RestController
@RequestMapping("/api/poster-backup")
public class PosterBackupController {

    private final PosterBackupScheduler scheduler;
    private final PosterBackupTaskState taskState;
    private final PosterBackupProperties properties;

    public PosterBackupController(PosterBackupScheduler scheduler,
                                  PosterBackupTaskState taskState,
                                  PosterBackupProperties properties) {
        this.scheduler = scheduler;
        this.taskState = taskState;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Result<PosterBackupTaskState.Snapshot> status() {
        return Result.ok(taskState.snapshot());
    }

    @PostMapping("/start")
    public Result<PosterBackupTaskState.Snapshot> start() {
        if (!properties.isEnabled()) {
            return Result.fail(409, "海报本地化任务已被配置禁用");
        }
        if (taskState.requestStart()) {
            // 从 Controller 调用 @Async 方法会经过 Spring 代理，立即返回给管理端。
            scheduler.backupPending();
        }
        return Result.ok(taskState.snapshot());
    }

    @PostMapping("/pause")
    public Result<PosterBackupTaskState.Snapshot> pause() {
        taskState.requestPause();
        return Result.ok(taskState.snapshot());
    }
}
