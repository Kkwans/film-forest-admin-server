package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerScheduleCursor;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerScheduleCursorMapper;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.system.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerScheduleCursorServiceTest {

    @Mock private CrawlerScheduleCursorMapper cursorMapper;
    @Mock private CrawlerScheduleMapper scheduleMapper;
    @Mock private CrawlerTaskLogMapper jobMapper;
    @Mock private OperationLogService operationLogService;

    @Test
    void manualRetryAutomaticallyResetsAnchorDriftCursor() {
        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setId(35L);
        schedule.setSourceSite("pkmp4");
        schedule.setAdapterCode("pkmp4");
        schedule.setContentType("drama");
        schedule.setSourceSort("RATING");
        schedule.setTraversalMode("BACKFILL_CONTINUE");
        schedule.setEndPolicy("HOLD_COMPLETED");
        schedule.setSourceFilters(java.util.Map.of());

        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setTriggerType("manual");

        CrawlerScheduleCursor cursor = new CrawlerScheduleCursor();
        cursor.setScheduleId(35L);
        cursor.setProfileHash(CrawlerQueryProfile.cursorHash(schedule));
        cursor.setState("RECOVERY_REQUIRED");
        cursor.setNextPage(1);
        cursor.setNextItemIndex(0);
        cursor.setNextExternalId("154614");
        cursor.setLastCommittedExternalId("154631");
        cursor.setLastError("分页锚点在当前页前后 2 页内均未找到，需人工确认后重置游标");
        cursor.setVersion(987L);
        when(cursorMapper.selectByScheduleId(35L)).thenReturn(cursor);

        CrawlerScheduleCursor prepared = new CrawlerScheduleCursorService(
                cursorMapper, scheduleMapper, jobMapper, operationLogService).prepare(schedule, job);

        assertThat(prepared.getState()).isEqualTo("ACTIVE");
        assertThat(prepared.getNextPage()).isEqualTo(1);
        assertThat(prepared.getNextItemIndex()).isZero();
        assertThat(prepared.getNextExternalId()).isNull();
        assertThat(prepared.getLastCommittedExternalId()).isNull();
        assertThat(prepared.getLastError()).isNull();
        verify(cursorMapper).updateById(cursor);
    }
}
