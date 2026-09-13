package com.filmforest.poster;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PosterBackupTaskStateTest {

    @Test
    void pauseAndResumeKeepTheProgressOfTheCurrentBackfill() {
        PosterBackupTaskState state = new PosterBackupTaskState(new PosterBackupProperties());

        assertThat(state.requestStart()).isTrue();
        assertThat(state.beginCycle(2)).isTrue();
        state.beginItem("movie", 7, "测试电影");
        state.finishItem(true, null);
        assertThat(state.requestPause()).isTrue();

        PosterBackupTaskState.Snapshot paused = state.snapshot();
        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(paused.total()).isEqualTo(2);
        assertThat(paused.pending()).isEqualTo(1);
        assertThat(paused.succeeded()).isEqualTo(1);
        assertThat(paused.progressPercent()).isEqualTo(50.0);
        assertThat(state.beginCycle(1)).isFalse();

        assertThat(state.requestStart()).isTrue();
        assertThat(state.beginCycle(1)).isTrue();
        state.finishCycle(0);
        assertThat(state.snapshot().status()).isEqualTo("COMPLETED");
        assertThat(state.snapshot().progressPercent()).isEqualTo(100.0);
    }

    @Test
    void startingAfterCompletionStartsAFreshProgressWindow() {
        PosterBackupTaskState state = new PosterBackupTaskState(new PosterBackupProperties());

        state.requestStart();
        state.beginCycle(1);
        state.finishCycle(0);
        assertThat(state.requestStart()).isTrue();

        PosterBackupTaskState.Snapshot snapshot = state.snapshot();
        assertThat(snapshot.status()).isEqualTo("RUNNING");
        assertThat(snapshot.total()).isZero();
        assertThat(snapshot.succeeded()).isZero();
        assertThat(snapshot.failed()).isZero();
    }
}
