package com.filmforest.content.service;

import com.filmforest.content.dto.ContentTagTarget;
import com.filmforest.content.entity.ContentTag;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.mapper.ContentTagMapper;
import com.filmforest.content.service.impl.TagServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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

    @Test
    void batchLookupUsesOneRelationQueryAndPreservesRequestedKeys() {
        ContentTagMapper contentTagMapper = mock(ContentTagMapper.class);
        TagServiceImpl service = spy(new TagServiceImpl(contentTagMapper, null, null, null));
        ContentTag movieRelation = relation("movie", 1L, 3L);
        ContentTag shortRelation = relation("short_drama", 2L, 7L);
        when(contentTagMapper.selectList(any())).thenReturn(List.of(movieRelation, shortRelation));
        doReturn(List.of(genre(3L, "剧情"), genre(7L, "短剧")))
                .when(service).listByIds(any(Collection.class));

        Map<String, List<Tag>> result = service.getContentTagsBatch(List.of(
                new ContentTagTarget("movie", 1L),
                new ContentTagTarget("short", 2L)));

        assertThat(result.keySet()).containsExactly("movie-1", "short-2");
        assertThat(result.get("movie-1")).extracting(Tag::getName).containsExactly("剧情");
        assertThat(result.get("short-2")).extracting(Tag::getName).containsExactly("短剧");
    }

    private static Tag genre(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSystem(1);
        return tag;
    }

    private static ContentTag relation(String contentType, Long contentId, Long tagId) {
        ContentTag relation = new ContentTag();
        relation.setContentType(contentType);
        relation.setContentId(contentId);
        relation.setTagId(tagId);
        return relation;
    }
}
