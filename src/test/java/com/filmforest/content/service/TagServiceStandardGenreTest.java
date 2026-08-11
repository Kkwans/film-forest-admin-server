package com.filmforest.content.service;

import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.impl.TagServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class TagServiceStandardGenreTest {

    @Test
    void returnsRequestedGenresInCanonicalDisplayOrder() {
        TagServiceImpl service = spy(new TagServiceImpl(null, null, null, null));
        Tag drama = genre(3L, "剧情");
        Tag scienceFiction = genre(7L, "科幻");
        doReturn(List.of(drama, scienceFiction)).when(service).getStandardGenres("movie");

        assertThat(service.requireStandardGenres("movie", List.of(7L, 3L, 7L)))
                .containsExactly(drama, scienceFiction);
    }

    @Test
    void rejectsIdsOutsideTheContentTypesStandardSet() {
        TagServiceImpl service = spy(new TagServiceImpl(null, null, null, null));
        doReturn(List.of(genre(3L, "剧情"))).when(service).getStandardGenres("movie");

        assertThatThrownBy(() -> service.requireStandardGenres("movie", List.of(3L, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("系统标准选项");
    }

    private static Tag genre(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSystem(1);
        return tag;
    }
}
