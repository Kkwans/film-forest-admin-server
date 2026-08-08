package com.filmforest.crawler.core;

import com.filmforest.crawler.model.ParsedResource;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerResourceDiffServiceTest {

    @Mock private ResourceMagnetMapper magnetMapper;
    @Mock private ResourceCloudMapper cloudMapper;
    @Mock private ResourceOnlineMapper onlineMapper;

    private ResourceNormalizer normalizer;
    private CrawlerResourceDiffService service;

    @BeforeEach
    void setUp() {
        normalizer = new ResourceNormalizer();
        service = new CrawlerResourceDiffService(normalizer, magnetMapper, cloudMapper, onlineMapper);
    }

    @Test
    void emptyParsedSetNeverRemovesExistingResources() {
        var result = service.apply("pkmp4", "movie", 42L, List.of());

        assertThat(result.protectedFromEmptyRemoval()).isTrue();
        verify(magnetMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
        verify(cloudMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
        verify(onlineMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
    }

    @Test
    void identicalManagedResourceIsTouchedInsteadOfReinserted() {
        ParsedResource parsed = magnet();
        String key = normalizer.normalize("pkmp4", parsed).resourceKey();
        ResourceMagnet existing = new ResourceMagnet();
        existing.setId(7L);
        existing.setContentType("movie");
        existing.setContentId(42L);
        existing.setSourceCode("pkmp4");
        existing.setResourceKey(key);
        existing.setTitle(parsed.title());
        existing.setMagnetUrl(parsed.url());
        existing.setResolution(parsed.resolution());
        existing.setHasSubtitle(false);
        existing.setIsSpecialSub(false);
        existing.setSort(0);
        existing.setRawText(parsed.rawText());
        existing.setDeleted(0);
        when(magnetMapper.selectManagedForUpdate("movie", 42L, "pkmp4"))
                .thenReturn(List.of(existing));
        when(magnetMapper.selectLegacyForUpdate("movie", 42L)).thenReturn(List.of());
        when(cloudMapper.selectManagedForUpdate("movie", 42L, "pkmp4")).thenReturn(List.of());
        when(cloudMapper.selectLegacyForUpdate("movie", 42L)).thenReturn(List.of());
        when(onlineMapper.selectManagedForUpdate("movie", 42L, "pkmp4")).thenReturn(List.of());
        when(onlineMapper.selectLegacyForUpdate("movie", 42L)).thenReturn(List.of());

        var result = service.apply("pkmp4", "movie", 42L, List.of(parsed));

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.changed()).isFalse();
        verify(magnetMapper).touchCrawlerResource(org.mockito.ArgumentMatchers.eq(7L), any());
        verify(magnetMapper, never()).insert(any());
        verify(magnetMapper, never()).updateCrawlerResource(any());
    }

    private static ParsedResource magnet() {
        return new ParsedResource(ParsedResource.Kind.MAGNET, "资源",
                "magnet:?xt=urn:btih:abcdef123&dn=Movie", null, null, "1080P",
                false, false, null, null, null, 0, "资源");
    }
}
