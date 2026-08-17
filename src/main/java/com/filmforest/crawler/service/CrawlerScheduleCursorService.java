package com.filmforest.crawler.service;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.crawler.entity.CrawlerConfigurationStatus;
import com.filmforest.crawler.entity.CrawlerCursorState;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerScheduleCursor;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerScheduleCursorMapper;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.system.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CrawlerScheduleCursorService {

    private final CrawlerScheduleCursorMapper cursorMapper;
    private final CrawlerScheduleMapper scheduleMapper;
    private final CrawlerTaskLogMapper jobMapper;
    private final OperationLogService operationLogService;

    public CrawlerScheduleCursorService(CrawlerScheduleCursorMapper cursorMapper,
                                        CrawlerScheduleMapper scheduleMapper,
                                        CrawlerTaskLogMapper jobMapper,
                                        OperationLogService operationLogService) {
        this.cursorMapper = cursorMapper;
        this.scheduleMapper = scheduleMapper;
        this.jobMapper = jobMapper;
        this.operationLogService = operationLogService;
    }

    public CrawlerScheduleCursor get(Long scheduleId) {
        return cursorMapper.selectByScheduleId(scheduleId);
    }

    /**
     * 为 Job 获取游标。profile 变化只标记 INVALIDATED，不自动回到第 1 页。
     */
    public CrawlerScheduleCursor prepare(CrawlerSchedule schedule, CrawlerTaskLog job) {
        CrawlerScheduleCursor cursor = cursorMapper.selectByScheduleId(schedule.getId());
        if (cursor == null) {
            cursor = newCursor(schedule, job.getQueryProfileHash());
            cursorMapper.insert(cursor);
            return cursor;
        }
        String profileHash = job.getQueryProfileHash();
        if (profileHash != null && !profileHash.equals(cursor.getProfileHash())) {
            cursor.setProfileHash(profileHash);
            cursor.setSourceCode(job.getSourceCode());
            cursor.setContentType(job.getContentType());
            cursor.setSourceSort(job.getSourceSort());
            cursor.setTraversalMode(job.getTraversalMode());
            cursor.setQuerySnapshot(job.getQuerySnapshot());
            cursor.setNextPage(1);
            cursor.setNextItemIndex(0);
            cursor.setNextExternalId(null);
            cursor.setLastCommittedExternalId(null);
            cursor.setHeadWatermark(null);
            cursor.setState(CrawlerCursorState.INVALIDATED.getCode());
            cursor.setLastError("查询 profile 已变化，需人工重置游标后继续");
            cursor.setVersion(safeVersion(cursor.getVersion()) + 1);
            cursorMapper.updateById(cursor);
        }
        return cursor;
    }

    public void advance(CrawlerScheduleCursor cursor, String nextExternalId,
                        String lastCommittedExternalId, int nextPage, int nextItemIndex,
                        String currentItem, String state, String error) {
        cursor.setNextPage(Math.max(1, nextPage));
        cursor.setNextItemIndex(Math.max(0, nextItemIndex));
        cursor.setNextExternalId(nextExternalId);
        cursor.setLastCommittedExternalId(lastCommittedExternalId);
        cursor.setState(state == null ? CrawlerCursorState.ACTIVE.getCode() : state);
        cursor.setLastError(error);
        cursor.setLastRunAt(CrawlerTime.nowUtc());
        cursor.setVersion(safeVersion(cursor.getVersion()) + 1);
        cursorMapper.updateById(cursor);
    }

    public void mark(CrawlerScheduleCursor cursor, CrawlerCursorState state, String error) {
        cursor.setState(state.getCode());
        cursor.setLastError(error);
        cursor.setLastRunAt(CrawlerTime.nowUtc());
        cursor.setVersion(safeVersion(cursor.getVersion()) + 1);
        cursorMapper.updateById(cursor);
    }

    @Transactional
    public CrawlerScheduleCursor reset(Long scheduleId) {
        if (jobMapper.selectActiveByScheduleId(scheduleId) != null) {
            throw new BusinessException(409, "存在活动 Job，不能重置游标");
        }
        CrawlerSchedule schedule = scheduleMapper.selectByIdForUpdate(scheduleId);
        if (schedule == null) {
            throw new BusinessException(404, "爬虫配置不存在");
        }
        CrawlerScheduleCursor cursor = cursorMapper.selectByScheduleIdForUpdate(scheduleId);
        if (cursor == null) {
            cursor = newCursor(schedule, CrawlerQueryProfile.hash(schedule));
            cursorMapper.insert(cursor);
        } else {
            cursor.setProfileHash(CrawlerQueryProfile.hash(schedule));
            cursor.setSourceCode(schedule.getAdapterCode());
            cursor.setContentType(schedule.getContentType());
            cursor.setSourceSort(schedule.getSourceSort());
            cursor.setTraversalMode(schedule.getTraversalMode());
            cursor.setQuerySnapshot(CrawlerQueryProfile.snapshot(schedule));
            cursor.setNextPage(1);
            cursor.setNextItemIndex(0);
            cursor.setNextExternalId(null);
            cursor.setLastCommittedExternalId(null);
            cursor.setHeadWatermark(null);
            cursor.setState(CrawlerCursorState.ACTIVE.getCode());
            cursor.setCycle((cursor.getCycle() == null ? 0 : cursor.getCycle()) + 1);
            cursor.setLastError(null);
            cursor.setVersion(safeVersion(cursor.getVersion()) + 1);
            cursorMapper.updateById(cursor);
        }
        operationLogService.log(null, null, "RESET", "CRAWLER",
                "schedule:" + scheduleId, "重置爬虫续爬游标", null, 1, null);
        return cursor;
    }

    private CrawlerScheduleCursor newCursor(CrawlerSchedule schedule, String profileHash) {
        CrawlerScheduleCursor cursor = new CrawlerScheduleCursor();
        cursor.setScheduleId(schedule.getId());
        cursor.setProfileHash(profileHash == null ? CrawlerQueryProfile.hash(schedule) : profileHash);
        cursor.setSourceCode(schedule.getAdapterCode());
        cursor.setContentType(schedule.getContentType());
        cursor.setSourceSort(schedule.getSourceSort());
        cursor.setTraversalMode(schedule.getTraversalMode());
        cursor.setQuerySnapshot(CrawlerQueryProfile.snapshot(schedule));
        cursor.setNextPage(1);
        cursor.setNextItemIndex(0);
        cursor.setState(CrawlerCursorState.ACTIVE.getCode());
        cursor.setCycle(0);
        cursor.setVersion(0L);
        return cursor;
    }

    private static long safeVersion(Long version) {
        return version == null ? 0L : version;
    }
}
