package com.filmforest.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import com.filmforest.crawler.entity.CrawlerScheduleGenre;
import com.filmforest.crawler.mapper.CrawlerScheduleGenreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerScheduleGenreServiceTest {

    @Mock private CrawlerScheduleGenreMapper genreMapper;
    @Mock private TagService tagService;

    private CrawlerScheduleGenreService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerScheduleGenreService(genreMapper, tagService, new ObjectMapper());
    }

    @Test
    void acceptsOnlyStandardGenresForCurrentContentType() {
        when(tagService.getStandardGenres("movie")).thenReturn(List.of(tag(1L, "剧情"), tag(2L, "科幻")));

        var result = service.validate("movie", List.of(2L, 2L, 1L));

        assertThat(result.tagIds()).containsExactly(2L, 1L);
        assertThat(result.compatibilityJson()).isEqualTo("[\"剧情\",\"科幻\"]");
        assertThatThrownBy(() -> service.validate("movie", List.of(9L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("系统标准题材");
    }

    @Test
    void replacesScheduleRelationsTransactionally() {
        service.replace(7L, List.of(2L, 4L));

        verify(genreMapper).delete(any());
        ArgumentCaptor<CrawlerScheduleGenre> captor = ArgumentCaptor.forClass(CrawlerScheduleGenre.class);
        verify(genreMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(CrawlerScheduleGenre::getScheduleId)
                .containsOnly(7L);
        assertThat(captor.getAllValues()).extracting(CrawlerScheduleGenre::getTagId)
                .containsExactly(2L, 4L);
    }

    private static Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSystem(1);
        return tag;
    }
}
