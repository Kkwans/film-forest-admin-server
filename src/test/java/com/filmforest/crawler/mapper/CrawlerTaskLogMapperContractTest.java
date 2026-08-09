package com.filmforest.crawler.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerTaskLogMapperContractTest {

    @Test
    void heartbeatMustNotPretendBusinessProgressWasMade() throws Exception {
        String sql = updateSql("touchHeartbeat", Long.class, LocalDateTime.class);

        assertThat(sql).contains("heartbeat_at = #{now}");
        assertThat(sql).doesNotContain("progress_updated_at");
    }

    @Test
    void stalledProgressRequestsSafeCancellationWithoutReleasingActiveScheduleLock() throws Exception {
        String sql = updateSql("requestProgressStalledCancellation",
                Long.class, LocalDateTime.class);

        assertThat(sql).contains("Job progress stalled", "progress_updated_at < #{stalledBefore}",
                "id = #{jobId}", "status = 'cancel_requested'", "cancel_requested = 1");
        assertThat(sql).doesNotContain("status = 'interrupted'", "finished_at");
    }

    private static String updateSql(String name, Class<?>... parameterTypes) throws Exception {
        Method method = CrawlerTaskLogMapper.class.getMethod(name, parameterTypes);
        return String.join("\n", method.getAnnotation(Update.class).value());
    }
}
