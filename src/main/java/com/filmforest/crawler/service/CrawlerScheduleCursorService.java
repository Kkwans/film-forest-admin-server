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
     * 为 Job 获取游标。资源抓取范围不参与游标 profile，切换 DOWNLOADS/ONLINE/ALL 不会
     * 让来源列表分页位置失效；真正的来源、排序、筛选变化仍然需要人工重置。
     */
    public CrawlerScheduleCursor prepare(CrawlerSchedule schedule, CrawlerTaskLog job) {
        CrawlerScheduleCursor cursor = cursorMapper.selectByScheduleId(schedule.getId());
        if (cursor == null) {
            cursor = newCursor(schedule, job.getQueryProfileHash());
            cursorMapper.insert(cursor);
            return cursor;
        }
        String profileHash = CrawlerQueryProfile.cursorHash(schedule);
        if (profileHash != null && !profileHash.equals(cursor.getProfileHash())) {
            cursor.setProfileHash(profileHash);
            cursor.setSourceCode(schedule.getAdapterCode());
            cursor.setContentType(schedule.getContentType());
            cursor.setSourceSort(schedule.getSourceSort());
            cursor.setTraversalMode(schedule.getTraversalMode());
            cursor.setQuerySnapshot(CrawlerQueryProfile.cursorSnapshot(schedule));
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
        if (CrawlerCursorState.RECOVERY_REQUIRED.getCode().equals(cursor.getState())
                && isAnchorDriftError(cursor.getLastError())) {
            resetAfterAnchorDrift(cursor, schedule);
        }
        return cursor;
    }

    /**
     * 来源分页排序会随新数据或评分变化而漂移；锚点彻底消失时，从第 1 页安全重建游标。
     * 仅针对已确认的分页锚点漂移自动恢复；来源结构异常仍保留人工复核状态。
     */
    public void resetAfterAnchorDrift(CrawlerScheduleCursor cursor, CrawlerSchedule schedule) {
        cursor.setProfileHash(CrawlerQueryProfile.cursorHash(schedule));
        cursor.setSourceCode(schedule.getAdapterCode());
        cursor.setContentType(schedule.getContentType());
        cursor.setSourceSort(schedule.getSourceSort());
        cursor.setTraversalMode(schedule.getTraversalMode());
        cursor.setQuerySnapshot(CrawlerQueryProfile.cursorSnapshot(schedule));
        cursor.setNextPage(1);
        cursor.setNextItemIndex(0);
        cursor.setNextExternalId(null);
        cursor.setLastCommittedExternalId(null);
        cursor.setHeadWatermark(null);
        cursor.setState(CrawlerCursorState.ACTIVE.getCode());
        cursor.setLastError(null);
        cursor.setLastRunAt(CrawlerTime.nowUtc());
        cursor.setVersion(safeVersion(cursor.getVersion()) + 1);
        cursorMapper.updateById(cursor);
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
            cursor = newCursor(schedule, CrawlerQueryProfile.cursorHash(schedule));
            cursorMapper.insert(cursor);
        } else {
            cursor.setProfileHash(CrawlerQueryProfile.cursorHash(schedule));
            cursor.setSourceCode(schedule.getAdapterCode());
            cursor.setContentType(schedule.getContentType());
            cursor.setSourceSort(schedule.getSourceSort());
            cursor.setTraversalMode(schedule.getTraversalMode());
            cursor.setQuerySnapshot(CrawlerQueryProfile.cursorSnapshot(schedule));
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
        cursor.setProfileHash(profileHash == null ? CrawlerQueryProfile.cursorHash(schedule) : profileHash);
        cursor.setSourceCode(schedule.getAdapterCode());
        cursor.setContentType(schedule.getContentType());
        cursor.setSourceSort(schedule.getSourceSort());
        cursor.setTraversalMode(schedule.getTraversalMode());
        cursor.setQuerySnapshot(CrawlerQueryProfile.cursorSnapshot(schedule));
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

    private static boolean isAnchorDriftError(String error) {
        return error != null && (error.contains("分页锚点") || error.contains("分页发生漂移"));
    }
}
