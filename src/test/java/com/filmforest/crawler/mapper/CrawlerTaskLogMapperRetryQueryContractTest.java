package com.filmforest.crawler.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerTaskLogMapperRetryQueryContractTest {

    @Test
    void retryAllRanksLatestJobBeforeFilteringRetryableStatusAndAppliesLimit() throws Exception {
        Select annotation = CrawlerTaskLogMapper.class
                .getMethod("selectLatestRetryableJobs", int.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", annotation.value()).replaceAll("\\s+", " ");

        assertThat(sql).contains(
                "ROW_NUMBER() OVER ( PARTITION BY log.schedule_id ORDER BY log.queued_at DESC, log.id DESC )",
                "WHERE ranked.row_num = 1",
                "ranked.status IN ('failed', 'partial_success', 'cancelled', 'interrupted')",
                "ORDER BY ranked.schedule_id ASC",
                "LIMIT #{limit}");
        assertThat(sql.indexOf("WHERE ranked.row_num = 1"))
                .isGreaterThan(sql.indexOf(") ranked"));
    }

    @Test
    void retryCandidateCountUsesTheSameLatestJobSemantics() throws Exception {
        Select annotation = CrawlerTaskLogMapper.class
                .getMethod("countLatestRetryableJobs")
                .getAnnotation(Select.class);
        String sql = Arrays.stream(annotation.value())
                .reduce("", (left, right) -> left + " " + right)
                .replaceAll("\\s+", " ");

        assertThat(sql).contains(
                "PARTITION BY log.schedule_id",
                "ORDER BY log.queued_at DESC, log.id DESC",
                "WHERE ranked.row_num = 1",
                "ranked.status IN ('failed', 'partial_success', 'cancelled', 'interrupted')");
    }
}
