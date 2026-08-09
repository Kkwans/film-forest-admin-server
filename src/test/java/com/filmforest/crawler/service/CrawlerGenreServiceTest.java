package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlerGenreServiceTest {

    @Test
    void resolvesSourceAliasesToStandardGenreIdsAndNames() {
        TagService tags = mock(TagService.class);
        Tag scienceFiction = tag(5L, "科幻");
        when(tags.resolveSourceGenres("pkmp4", "movie", List.of("科幻片", "英语")))
                .thenReturn(List.of(scienceFiction));
        CrawlerGenreService service = new CrawlerGenreService(tags);

        var resolved = service.resolve("pkmp4", ContentType.MOVIE, List.of("科幻片", "英语"));

        assertThat(resolved.tagIds()).containsExactly(5L);
        assertThat(resolved.names()).containsExactly("科幻");
        service.replaceContentGenres(42L, ContentType.MOVIE, resolved);
        verify(tags).setContentGenres(42L, "movie", List.of(5L));
    }

    private static Tag tag(long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }
}
