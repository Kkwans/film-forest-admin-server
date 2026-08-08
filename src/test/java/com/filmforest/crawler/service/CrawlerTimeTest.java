package com.filmforest.crawler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Crawler UTC 时间语义")
class CrawlerTimeTest {

    @Test
    @DisplayName("Cron 按 Asia/Shanghai 解释并转换为 UTC 保存")
    void nextRunUtc_shouldConvertShanghaiCronToUtc() {
        LocalDateTime referenceUtc = LocalDateTime.of(2026, 8, 8, 16, 0);

        LocalDateTime nextUtc = CrawlerTime.nextRunUtc("0 0 2 * * *", referenceUtc);

        assertThat(nextUtc).isEqualTo(LocalDateTime.of(2026, 8, 8, 18, 0));
    }

    @Test
    @DisplayName("上海自然日零点会转换为前一日 16:00 UTC")
    void startOfScheduleDayUtc_shouldConvertShanghaiBoundary() {
        assertThat(CrawlerTime.startOfScheduleDayUtc(java.time.LocalDate.of(2026, 8, 9)))
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 16, 0));
    }

    @Test
    @DisplayName("UTC 时间按上海时区归属自然日")
    void toScheduleDate_shouldUseShanghaiCalendarDate() {
        assertThat(CrawlerTime.toScheduleDate(LocalDateTime.of(2026, 8, 8, 16, 0)))
                .isEqualTo(java.time.LocalDate.of(2026, 8, 9));
    }
}
