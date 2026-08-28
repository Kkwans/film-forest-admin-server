package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerQueryProfileTest {

    @Test
    void resourceScopeDoesNotChangeCursorProfile() {
        CrawlerSchedule schedule = schedule("DOWNLOADS");
        String cursorHash = CrawlerQueryProfile.cursorHash(schedule);
        String fullHash = CrawlerQueryProfile.hash(schedule);

        schedule.setResourceScope("ONLINE");

        assertThat(CrawlerQueryProfile.cursorHash(schedule)).isEqualTo(cursorHash);
        assertThat(CrawlerQueryProfile.hash(schedule)).isNotEqualTo(fullHash);
    }

    @Test
    void cursorSnapshotMatchesLegacyAndCurrentLayouts() {
        CrawlerSchedule schedule = schedule("DOWNLOADS");
        String current = CrawlerQueryProfile.snapshot(schedule);
        String legacy = CrawlerQueryProfile.cursorSnapshot(schedule);

        assertThat(CrawlerQueryProfile.cursorSnapshotMatches(current, schedule)).isTrue();
        assertThat(CrawlerQueryProfile.cursorSnapshotMatches(legacy, schedule)).isTrue();
    }

    private CrawlerSchedule schedule(String resourceScope) {
        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setSourceSite("pkmp4");
        schedule.setAdapterCode("pkmp4");
        schedule.setContentType("drama");
        schedule.setResourceScope(resourceScope);
        schedule.setSourceSort("TIME");
        schedule.setTraversalMode("CONTINUOUS_SYNC");
        schedule.setEndPolicy("HOLD_COMPLETED");
        schedule.setGenreFilter("[]");
        return schedule;
    }
}
