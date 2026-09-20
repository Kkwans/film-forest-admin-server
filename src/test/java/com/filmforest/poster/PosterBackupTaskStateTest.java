package com.filmforest.poster;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PosterBackupTaskStateTest {

    @Test
    void snapshotUsesUniqueCatalogCountsInsteadOfAttemptCounters() {
        PosterBackupTaskState state = new PosterBackupTaskState(new PosterBackupProperties());

        state.requestStart();
        state.beginCycle();
        state.beginItem("movie", 7, "测试电影");
        state.finishItem(false, "源失效");
        state.finishCycle(0, 59);

        PosterBackupTaskState.Snapshot snapshot = state.snapshot(
                new PosterBackupScheduler.CatalogStats(123_391, 123_332, 59, 0));

        assertThat(snapshot.status()).isEqualTo("COMPLETED_WITH_FAILURES");
        assertThat(snapshot.total()).isEqualTo(123_391);
        assertThat(snapshot.processed()).isEqualTo(123_391);
        assertThat(snapshot.succeeded()).isEqualTo(123_332);
        assertThat(snapshot.failed()).isEqualTo(59);
        assertThat(snapshot.pending()).isZero();
        assertThat(snapshot.attempted()).isEqualTo(1);
        assertThat(snapshot.failedAttempts()).isEqualTo(1);
        assertThat(snapshot.progressPercent()).isEqualTo(123_332 * 100.0 / 123_391);
    }

    @Test
    void pauseAndResumeKeepCurrentRunAttemptCounts() {
        PosterBackupTaskState state = new PosterBackupTaskState(new PosterBackupProperties());

        assertThat(state.requestStart()).isTrue();
        assertThat(state.beginCycle()).isTrue();
        state.finishItem(true, null);
        assertThat(state.requestPause()).isTrue();
        assertThat(state.beginCycle()).isFalse();

        assertThat(state.requestStart()).isTrue();
        assertThat(state.beginCycle()).isTrue();
        PosterBackupTaskState.Snapshot snapshot = state.snapshot(
                new PosterBackupScheduler.CatalogStats(2, 1, 0, 1));

        assertThat(snapshot.status()).isEqualTo("RUNNING");
        assertThat(snapshot.attempted()).isEqualTo(1);
        assertThat(snapshot.successfulAttempts()).isEqualTo(1);
    }
}
