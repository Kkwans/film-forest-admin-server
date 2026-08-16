package com.filmforest.crawler.core;

import com.filmforest.crawler.model.ParsedResource;
import com.filmforest.crawler.model.ResourceParseStatus;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
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

        var result = service.apply("pkmp4", "movie", 42L, List.of(parsed));

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.changed()).isFalse();
        verify(magnetMapper).touchCrawlerResource(org.mockito.ArgumentMatchers.eq(7L), any());
        verify(magnetMapper, never()).insert(any(ResourceMagnet.class));
        verify(magnetMapper, never()).updateCrawlerResource(any());
    }

    @Test
    void completeEmptyRequiresTwoObservationsBeforeRemoval() {
        ResourceMagnet existing = new ResourceMagnet();
        existing.setId(9L);
        existing.setDeleted(0);

        var first = service.apply("pkmp4", "movie", 43L, List.of(),
                Map.of(ParsedResource.Kind.MAGNET, ResourceParseStatus.COMPLETE));
        assertThat(first.protectedFromEmptyRemoval()).isTrue();
        verify(magnetMapper, never()).markCrawlerResourceRemoved(anyLong(), any());

        when(magnetMapper.selectManagedForUpdate("movie", 43L, "pkmp4"))
                .thenReturn(List.of(existing));
        when(magnetMapper.selectLegacyForUpdate("movie", 43L)).thenReturn(List.of());
        when(magnetMapper.markCrawlerResourceRemoved(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        var second = service.apply("pkmp4", "movie", 43L, List.of(),
                Map.of(ParsedResource.Kind.MAGNET, ResourceParseStatus.COMPLETE));

        assertThat(second.removed()).isEqualTo(1);
        verify(magnetMapper).markCrawlerResourceRemoved(org.mockito.ArgumentMatchers.eq(9L), any());
    }

    @Test
    void partialEmptyObservationBreaksCompleteEmptyStreak() {
        var first = service.apply("pkmp4", "movie", 47L, List.of(),
                Map.of(ParsedResource.Kind.MAGNET, ResourceParseStatus.COMPLETE));
        assertThat(first.protectedFromEmptyRemoval()).isTrue();

        var partial = service.apply("pkmp4", "movie", 47L, List.of(),
                Map.of(ParsedResource.Kind.MAGNET, ResourceParseStatus.PARTIAL));
        assertThat(partial.protectedFromEmptyRemoval()).isTrue();

        var afterReset = service.apply("pkmp4", "movie", 47L, List.of(),
                Map.of(ParsedResource.Kind.MAGNET, ResourceParseStatus.COMPLETE));
        assertThat(afterReset.protectedFromEmptyRemoval()).isTrue();
        verify(magnetMapper, never()).selectManagedForUpdate(any(), anyLong(), any());
        verify(magnetMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
    }

    @Test
    void completeEmptyRemovalRequiresConsecutiveObservations() {
        var first = service.apply("pkmp4", "movie", 47L, List.of(),
                Map.of(ParsedResource.Kind.CLOUD, ResourceParseStatus.COMPLETE));
        assertThat(first.protectedFromEmptyRemoval()).isTrue();

        var partial = service.apply("pkmp4", "movie", 47L, List.of(),
                Map.of(ParsedResource.Kind.CLOUD, ResourceParseStatus.PARTIAL));
        assertThat(partial.protectedFromEmptyRemoval()).isTrue();

        var third = service.apply("pkmp4", "movie", 47L, List.of(),
                Map.of(ParsedResource.Kind.CLOUD, ResourceParseStatus.COMPLETE));
        assertThat(third.protectedFromEmptyRemoval()).isTrue();
        verify(cloudMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
    }

    @Test
    void partialCloudResultUpdatesKnownFieldsButPreservesMissingPassword() {
        ParsedResource parsed = new ParsedResource(ParsedResource.Kind.CLOUD, "新标题",
                "https://pan.example.test/s/abc", "other", null, null,
                false, false, null, null, null, 0, null, null, null);
        String key = normalizer.normalize("pkmp4", parsed).resourceKey();
        ResourceCloud existing = new ResourceCloud();
        existing.setId(11L);
        existing.setResourceKey(key);
        existing.setSourceCode("pkmp4");
        existing.setDiskType("other");
        existing.setTitle("旧标题");
        existing.setUrl(parsed.url());
        existing.setPassword("stored-code");
        existing.setSort(0);
        existing.setDeleted(0);
        existing.setEnabled(0);
        when(cloudMapper.selectManagedForUpdate("movie", 44L, "pkmp4"))
                .thenReturn(List.of(existing));
        when(cloudMapper.selectLegacyForUpdate("movie", 44L)).thenReturn(List.of());

        var result = service.apply("pkmp4", "movie", 44L, List.of(parsed),
                Map.of(ParsedResource.Kind.CLOUD, ResourceParseStatus.PARTIAL));

        assertThat(result.updated()).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(ResourceCloud.class);
        verify(cloudMapper).updateCrawlerResource(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
        assertThat(captor.getValue().getPassword()).isEqualTo("stored-code");
        assertThat(captor.getValue().getEnabled()).isZero();
        verify(cloudMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
    }

    @Test
    void partialCloudResultDoesNotRemoveMissingExistingResource() {
        ParsedResource parsed = new ParsedResource(ParsedResource.Kind.CLOUD, "新资源",
                "https://cloud.example.test/s/new", "other", "new-code", null,
                false, false, null, null, null, 0, "新资源", null, null);
        ResourceCloud existing = new ResourceCloud();
        existing.setId(12L);
        existing.setResourceKey(normalizer.normalize("pkmp4", new ParsedResource(
                ParsedResource.Kind.CLOUD, "旧资源", "https://cloud.example.test/s/old", "other",
                "old-code", null, false, false, null, null, null, 0, "旧资源", null, null)).resourceKey());
        existing.setDeleted(0);
        when(cloudMapper.selectManagedForUpdate("movie", 48L, "pkmp4"))
                .thenReturn(List.of(existing));
        when(cloudMapper.selectLegacyForUpdate("movie", 48L)).thenReturn(List.of());

        service.apply("pkmp4", "movie", 48L, List.of(parsed),
                Map.of(ParsedResource.Kind.CLOUD, ResourceParseStatus.PARTIAL));

        verify(cloudMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
    }

    @Test
    void failedAndNotSupportedKindsNeverClearResources() {
        service.apply("pkmp4", "movie", 45L, List.of(),
                Map.of(ParsedResource.Kind.CLOUD, ResourceParseStatus.FAILED,
                        ParsedResource.Kind.ONLINE, ResourceParseStatus.NOT_SUPPORTED));

        verify(cloudMapper, never()).selectManagedForUpdate(any(), anyLong(), any());
        verify(onlineMapper, never()).selectManagedForUpdate(any(), anyLong(), any());
        verify(cloudMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
        verify(onlineMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
    }

    @Test
    void invalidResourceFieldDowngradesCompleteObservationToProtectedPartial() {
        ParsedResource invalid = new ParsedResource(ParsedResource.Kind.ONLINE, "线路",
                "", null, null, null, false, false, 1, 1, "第一集", 0,
                "第一集", null, "EXTERNAL_PAGE");

        var result = service.apply("pkmp4", "movie", 46L, List.of(invalid),
                Map.of(ParsedResource.Kind.ONLINE, ResourceParseStatus.COMPLETE));

        assertThat(result.protectedFromEmptyRemoval()).isTrue();
        verify(onlineMapper, never()).markCrawlerResourceRemoved(anyLong(), any());
        verify(onlineMapper, never()).selectManagedForUpdate(any(), anyLong(), any());
    }

    private static ParsedResource magnet() {
        return new ParsedResource(ParsedResource.Kind.MAGNET, "资源",
                "magnet:?xt=urn:btih:abcdef123&dn=Movie", null, null, "1080P",
                false, false, null, null, null, 0, "资源", null, null);
    }
}
