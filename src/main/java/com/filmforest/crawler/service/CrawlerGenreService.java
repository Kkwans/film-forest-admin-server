package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CrawlerGenreService {

    private final TagService tagService;

    public CrawlerGenreService(TagService tagService) {
        this.tagService = tagService;
    }

    public ResolvedGenres resolve(String sourceCode, ContentType contentType,
                                  List<String> sourceGenres) {
        List<Tag> tags = tagService.resolveSourceGenres(
                sourceCode, contentType.value(), sourceGenres == null ? List.of() : sourceGenres);
        return new ResolvedGenres(
                tags.stream().map(Tag::getId).toList(),
                tags.stream().map(Tag::getName).toList());
    }

    public void replaceContentGenres(long contentId, ContentType contentType,
                                     ResolvedGenres genres) {
        tagService.setContentTags(contentId, contentType.value(), genres.tagIds());
    }

    public record ResolvedGenres(List<Long> tagIds, List<String> names) {
        public ResolvedGenres {
            tagIds = List.copyOf(tagIds == null ? List.of() : tagIds);
            names = List.copyOf(names == null ? List.of() : names);
        }
    }
}
