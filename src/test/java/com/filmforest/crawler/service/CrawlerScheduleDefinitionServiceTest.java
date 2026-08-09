package com.filmforest.crawler.service;

import com.filmforest.crawler.dto.CrawlerSchedulePreviewRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerScheduleDefinitionServiceTest {

    private final CrawlerScheduleDefinitionService service = new CrawlerScheduleDefinitionService();

    @Test
    void intervalMinutesGeneratesCronAndFiveRuns() {
        var preview = service.preview(new CrawlerSchedulePreviewRequest(
                "INTERVAL", Map.of("unit", "minutes", "interval", 15), null, "Asia/Shanghai"));

        assertThat(preview.cronExpression()).isEqualTo("0 */15 * * * *");
        assertThat(preview.scheduleMode()).isEqualTo("INTERVAL");
        assertThat(preview.nextRuns()).hasSize(5);
        assertThat(preview.nextRuns()).allMatch(run -> "Asia/Shanghai".equals(run.getZone().getId()));
    }

    @Test
    void recognizedCronReturnsWeeklyGuide() {
        var preview = service.preview(new CrawlerSchedulePreviewRequest(
                null, null, "0 30 9 * * MON,WED,FRI", "Asia/Shanghai"));

        assertThat(preview.scheduleMode()).isEqualTo("WEEKLY");
        assertThat(preview.scheduleConfig().get("days")).isEqualTo(List.of("MON", "WED", "FRI"));
    }

    @Test
    void complexCronIsPreservedAsCustom() {
        var preview = service.preview(new CrawlerSchedulePreviewRequest(
                null, null, "0 0 9-18 * * MON-FRI", "Asia/Shanghai"));

        assertThat(preview.scheduleMode()).isEqualTo("CUSTOM_CRON");
        assertThat(preview.cronExpression()).isEqualTo("0 0 9-18 * * MON-FRI");
    }

    @Test
    void rejectsInvalidIntervalAndTimezone() {
        assertThatThrownBy(() -> service.preview(new CrawlerSchedulePreviewRequest(
                "INTERVAL", Map.of("unit", "minutes", "interval", 0), null, "Asia/Shanghai")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分钟间隔");
        assertThatThrownBy(() -> service.preview(new CrawlerSchedulePreviewRequest(
                "DAILY", Map.of(), null, "Mars/Base")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效时区");
    }
}
