package com.filmforest.common.type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypeTest {

    @Test
    void resolvesOnlyKnownContentTypes() {
        assertThat(ContentType.fromValue("movie")).contains(ContentType.MOVIE);
        assertThat(ContentType.fromValue("short_drama")).contains(ContentType.SHORT_DRAMA);
        assertThat(ContentType.fromValue("movie; DROP TABLE user")).isEmpty();
        assertThat(ContentType.fromValue(null)).isEmpty();
    }
}
