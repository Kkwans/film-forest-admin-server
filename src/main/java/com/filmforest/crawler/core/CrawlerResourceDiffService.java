package com.filmforest.crawler.core;

import com.filmforest.crawler.model.ParsedResource;
import com.filmforest.crawler.service.CrawlerTime;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

@Service
public class CrawlerResourceDiffService {

    private final ResourceNormalizer normalizer;
    private final ResourceMagnetMapper magnetMapper;
    private final ResourceCloudMapper cloudMapper;
    private final ResourceOnlineMapper onlineMapper;

    public CrawlerResourceDiffService(ResourceNormalizer normalizer,
                                      ResourceMagnetMapper magnetMapper,
                                      ResourceCloudMapper cloudMapper,
                                      ResourceOnlineMapper onlineMapper) {
        this.normalizer = normalizer;
        this.magnetMapper = magnetMapper;
        this.cloudMapper = cloudMapper;
        this.onlineMapper = onlineMapper;
    }

    /**
     * 解析结果为空时拒绝推断“来源已删除全部资源”，从而保护现存资源。
     */
    @Transactional
    public ResourceDiffResult apply(String sourceCode, String contentType, long contentId,
                                    List<ParsedResource> parsedResources) {
        if (parsedResources == null || parsedResources.isEmpty()) {
            return ResourceDiffResult.emptyProtected();
        }
        String normalizedSource = requireText(sourceCode, "sourceCode").toLowerCase(Locale.ROOT);
        String normalizedContentType = requireText(contentType, "contentType").toLowerCase(Locale.ROOT);
        Map<ParsedResource.Kind, List<ResourceNormalizer.NormalizedResource>> normalized =
                normalizer.normalizeAll(normalizedSource, parsedResources);
        LocalDateTime now = CrawlerTime.nowUtc();
        ResourceDiffResult result = diffMagnets(normalizedSource, normalizedContentType, contentId,
                normalized.getOrDefault(ParsedResource.Kind.MAGNET, List.of()), now);
        result = result.plus(diffCloud(normalizedSource, normalizedContentType, contentId,
                normalized.getOrDefault(ParsedResource.Kind.CLOUD, List.of()), now));
        return result.plus(diffOnline(normalizedSource, normalizedContentType, contentId,
                normalized.getOrDefault(ParsedResource.Kind.ONLINE, List.of()), now));
    }

    private ResourceDiffResult diffMagnets(String sourceCode, String contentType, long contentId,
                                           List<ResourceNormalizer.NormalizedResource> incoming,
                                           LocalDateTime now) {
        List<ResourceMagnet> managed = magnetMapper.selectManagedForUpdate(contentType, contentId, sourceCode);
        List<ResourceMagnet> legacy = new ArrayList<>(magnetMapper.selectLegacyForUpdate(contentType, contentId));
        Map<String, ResourceMagnet> current = byMagnetKey(managed);
        MutableDiff stats = new MutableDiff();
        for (ResourceNormalizer.NormalizedResource normalized : incoming) {
            ParsedResource parsed = normalized.resource();
            ResourceMagnet existing = current.remove(normalized.resourceKey());
            if (existing == null) {
                existing = removeMatchingLegacyMagnet(legacy, sourceCode, normalized.resourceKey());
            }
            if (existing == null) {
                magnetMapper.insert(toMagnet(null, sourceCode, contentType, contentId,
                        normalized.resourceKey(), parsed, now));
                stats.added++;
            } else if (!isActive(existing) || magnetChanged(existing, parsed)
                    || !sourceCode.equals(existing.getSourceCode())) {
                magnetMapper.updateCrawlerResource(toMagnet(existing.getId(), sourceCode,
                        contentType, contentId, normalized.resourceKey(), parsed, now));
                stats.updated++;
            } else {
                magnetMapper.touchCrawlerResource(existing.getId(), now);
                stats.unchanged++;
            }
        }
        current.values().stream().filter(CrawlerResourceDiffService::isActive).forEach(resource -> {
            stats.removed += magnetMapper.markCrawlerResourceRemoved(resource.getId(), now);
        });
        return stats.result();
    }

    private ResourceDiffResult diffCloud(String sourceCode, String contentType, long contentId,
                                         List<ResourceNormalizer.NormalizedResource> incoming,
                                         LocalDateTime now) {
        List<ResourceCloud> managed = cloudMapper.selectManagedForUpdate(contentType, contentId, sourceCode);
        List<ResourceCloud> legacy = new ArrayList<>(cloudMapper.selectLegacyForUpdate(contentType, contentId));
        Map<String, ResourceCloud> current = byCloudKey(managed);
        MutableDiff stats = new MutableDiff();
        for (ResourceNormalizer.NormalizedResource normalized : incoming) {
            ParsedResource parsed = normalized.resource();
            ResourceCloud existing = current.remove(normalized.resourceKey());
            if (existing == null) {
                existing = removeMatchingLegacyCloud(legacy, sourceCode, normalized.resourceKey());
            }
            if (existing == null) {
                cloudMapper.insert(toCloud(null, sourceCode, contentType, contentId,
                        normalized.resourceKey(), parsed, now));
                stats.added++;
            } else if (!isActive(existing) || cloudChanged(existing, parsed)
                    || !sourceCode.equals(existing.getSourceCode())) {
                cloudMapper.updateCrawlerResource(toCloud(existing.getId(), sourceCode,
                        contentType, contentId, normalized.resourceKey(), parsed, now));
                stats.updated++;
            } else {
                cloudMapper.touchCrawlerResource(existing.getId(), now);
                stats.unchanged++;
            }
        }
        current.values().stream().filter(CrawlerResourceDiffService::isActive).forEach(resource -> {
            stats.removed += cloudMapper.markCrawlerResourceRemoved(resource.getId(), now);
        });
        return stats.result();
    }

    private ResourceDiffResult diffOnline(String sourceCode, String contentType, long contentId,
                                          List<ResourceNormalizer.NormalizedResource> incoming,
                                          LocalDateTime now) {
        List<ResourceOnline> managed = onlineMapper.selectManagedForUpdate(contentType, contentId, sourceCode);
        List<ResourceOnline> legacy = new ArrayList<>(onlineMapper.selectLegacyForUpdate(contentType, contentId));
        Map<String, ResourceOnline> current = byOnlineKey(managed);
        MutableDiff stats = new MutableDiff();
        for (ResourceNormalizer.NormalizedResource normalized : incoming) {
            ParsedResource parsed = normalized.resource();
            ResourceOnline existing = current.remove(normalized.resourceKey());
            if (existing == null) {
                existing = removeMatchingLegacyOnline(legacy, sourceCode, normalized.resourceKey());
            }
            if (existing == null) {
                onlineMapper.insert(toOnline(null, sourceCode, contentType, contentId,
                        normalized.resourceKey(), parsed, now));
                stats.added++;
            } else if (!isActive(existing) || onlineChanged(existing, parsed)
                    || !sourceCode.equals(existing.getSourceCode())) {
                onlineMapper.updateCrawlerResource(toOnline(existing.getId(), sourceCode,
                        contentType, contentId, normalized.resourceKey(), parsed, now));
                stats.updated++;
            } else {
                onlineMapper.touchCrawlerResource(existing.getId(), now);
                stats.unchanged++;
            }
        }
        current.values().stream().filter(CrawlerResourceDiffService::isActive).forEach(resource -> {
            stats.removed += onlineMapper.markCrawlerResourceRemoved(resource.getId(), now);
        });
        return stats.result();
    }

    private ResourceMagnet removeMatchingLegacyMagnet(List<ResourceMagnet> legacy,
                                                       String sourceCode, String key) {
        for (int index = 0; index < legacy.size(); index++) {
            ResourceMagnet candidate = legacy.get(index);
            ParsedResource parsed = new ParsedResource(ParsedResource.Kind.MAGNET,
                    candidate.getTitle(), candidate.getMagnetUrl(), null, null,
                    candidate.getResolution(), Boolean.TRUE.equals(candidate.getHasSubtitle()),
                    Boolean.TRUE.equals(candidate.getIsSpecialSub()), null, null, null,
                    valueOrZero(candidate.getSort()), candidate.getRawText(), null, null);
            if (normalizer.normalize(sourceCode, parsed).resourceKey().equals(key)) {
                legacy.remove(index);
                return candidate;
            }
        }
        return null;
    }

    private ResourceCloud removeMatchingLegacyCloud(List<ResourceCloud> legacy,
                                                    String sourceCode, String key) {
        for (int index = 0; index < legacy.size(); index++) {
            ResourceCloud candidate = legacy.get(index);
            ParsedResource parsed = new ParsedResource(ParsedResource.Kind.CLOUD,
                    candidate.getTitle(), candidate.getUrl(), candidate.getDiskType(),
                    candidate.getPassword(), null, false, false, null, null, null,
                    valueOrZero(candidate.getSort()), candidate.getRawText(), null, null);
            if (normalizer.normalize(sourceCode, parsed).resourceKey().equals(key)) {
                legacy.remove(index);
                return candidate;
            }
        }
        return null;
    }

    private ResourceOnline removeMatchingLegacyOnline(List<ResourceOnline> legacy,
                                                       String sourceCode, String key) {
        for (int index = 0; index < legacy.size(); index++) {
            ResourceOnline candidate = legacy.get(index);
            ParsedResource parsed = new ParsedResource(ParsedResource.Kind.ONLINE,
                    candidate.getSourceName(), candidate.getSourceUrl(), null, null,
                    null, false, false, candidate.getSeason(), candidate.getEpisodeNumber(),
                    candidate.getEpisodeTitle(), valueOrZero(candidate.getSort()), candidate.getRawText(),
                    candidate.getSourcePageUrl(), candidate.getPlaybackType());
            if (normalizer.normalize(sourceCode, parsed).resourceKey().equals(key)) {
                legacy.remove(index);
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, ResourceMagnet> byMagnetKey(List<ResourceMagnet> resources) {
        Map<String, ResourceMagnet> result = new HashMap<>();
        resources.forEach(resource -> result.put(resource.getResourceKey(), resource));
        return result;
    }

    private static Map<String, ResourceCloud> byCloudKey(List<ResourceCloud> resources) {
        Map<String, ResourceCloud> result = new HashMap<>();
        resources.forEach(resource -> result.put(resource.getResourceKey(), resource));
        return result;
    }

    private static Map<String, ResourceOnline> byOnlineKey(List<ResourceOnline> resources) {
        Map<String, ResourceOnline> result = new HashMap<>();
        resources.forEach(resource -> result.put(resource.getResourceKey(), resource));
        return result;
    }

    private static ResourceMagnet toMagnet(Long id, String sourceCode, String contentType,
                                           long contentId, String key, ParsedResource parsed,
                                           LocalDateTime now) {
        ResourceMagnet entity = new ResourceMagnet();
        entity.setId(id);
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setSourceCode(sourceCode);
        entity.setResourceKey(key);
        entity.setRawText(parsed.rawText());
        entity.setLastSeenAt(now);
        entity.setTitle(limit(parsed.title(), 200));
        entity.setMagnetUrl(parsed.url());
        entity.setResolution(parsed.resolution());
        entity.setHasSubtitle(parsed.hasSubtitle());
        entity.setIsSpecialSub(parsed.specialSubtitle());
        entity.setSort(parsed.sourceOrder());
        entity.setDeleted(0);
        return entity;
    }

    private static ResourceCloud toCloud(Long id, String sourceCode, String contentType,
                                         long contentId, String key, ParsedResource parsed,
                                         LocalDateTime now) {
        ResourceCloud entity = new ResourceCloud();
        entity.setId(id);
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setSourceCode(sourceCode);
        entity.setResourceKey(key);
        entity.setRawText(parsed.rawText());
        entity.setLastSeenAt(now);
        entity.setDiskType(parsed.diskType());
        entity.setTitle(limit(parsed.title(), 200));
        entity.setUrl(parsed.url());
        entity.setPassword(limit(parsed.password(), 50));
        entity.setSort(parsed.sourceOrder());
        entity.setDeleted(0);
        return entity;
    }

    private static ResourceOnline toOnline(Long id, String sourceCode, String contentType,
                                           long contentId, String key, ParsedResource parsed,
                                           LocalDateTime now) {
        ResourceOnline entity = new ResourceOnline();
        entity.setId(id);
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setSourceCode(sourceCode);
        entity.setResourceKey(key);
        entity.setRawText(parsed.rawText());
        entity.setLastSeenAt(now);
        entity.setSeason(parsed.season());
        entity.setEpisodeNumber(parsed.episodeNumber());
        entity.setEpisodeTitle(limit(parsed.episodeTitle(), 200));
        entity.setSourceName(limit(parsed.title(), 50));
        entity.setSourceUrl(parsed.url());
        entity.setSourcePageUrl(parsed.sourcePageUrl());
        entity.setPlaybackType(parsed.playbackType() == null ? "EXTERNAL_PAGE" : parsed.playbackType());
        entity.setSort(parsed.sourceOrder());
        entity.setDeleted(0);
        return entity;
    }

    private static boolean magnetChanged(ResourceMagnet existing, ParsedResource parsed) {
        return !Objects.equals(existing.getTitle(), limit(parsed.title(), 200))
                || !Objects.equals(existing.getMagnetUrl(), parsed.url())
                || !Objects.equals(existing.getResolution(), parsed.resolution())
                || !Objects.equals(Boolean.TRUE.equals(existing.getHasSubtitle()), parsed.hasSubtitle())
                || !Objects.equals(Boolean.TRUE.equals(existing.getIsSpecialSub()), parsed.specialSubtitle())
                || !Objects.equals(existing.getSort(), parsed.sourceOrder())
                || !Objects.equals(existing.getRawText(), parsed.rawText());
    }

    private static boolean cloudChanged(ResourceCloud existing, ParsedResource parsed) {
        return !Objects.equals(existing.getDiskType(), parsed.diskType())
                || !Objects.equals(existing.getTitle(), limit(parsed.title(), 200))
                || !Objects.equals(existing.getUrl(), parsed.url())
                || !Objects.equals(existing.getPassword(), limit(parsed.password(), 50))
                || !Objects.equals(existing.getSort(), parsed.sourceOrder())
                || !Objects.equals(existing.getRawText(), parsed.rawText());
    }

    private static boolean onlineChanged(ResourceOnline existing, ParsedResource parsed) {
        return !Objects.equals(existing.getSeason(), parsed.season())
                || !Objects.equals(existing.getEpisodeNumber(), parsed.episodeNumber())
                || !Objects.equals(existing.getEpisodeTitle(), limit(parsed.episodeTitle(), 200))
                || !Objects.equals(existing.getSourceName(), limit(parsed.title(), 50))
                || !Objects.equals(existing.getSourceUrl(), parsed.url())
                || !Objects.equals(existing.getSourcePageUrl(), parsed.sourcePageUrl())
                || !Objects.equals(existing.getPlaybackType(), parsed.playbackType() == null
                        ? "EXTERNAL_PAGE" : parsed.playbackType())
                || !Objects.equals(existing.getSort(), parsed.sourceOrder())
                || !Objects.equals(existing.getRawText(), parsed.rawText());
    }

    private static boolean isActive(ResourceMagnet resource) {
        return resource.getDeleted() == null || resource.getDeleted() == 0;
    }

    private static boolean isActive(ResourceCloud resource) {
        return resource.getDeleted() == null || resource.getDeleted() == 0;
    }

    private static boolean isActive(ResourceOnline resource) {
        return resource.getDeleted() == null || resource.getDeleted() == 0;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static final class MutableDiff {
        private int added;
        private int updated;
        private int removed;
        private int unchanged;

        private ResourceDiffResult result() {
            return new ResourceDiffResult(added, updated, removed, unchanged, false);
        }
    }

    public record ResourceDiffResult(int added, int updated, int removed, int unchanged,
                                     boolean protectedFromEmptyRemoval) {
        public static ResourceDiffResult emptyProtected() {
            return new ResourceDiffResult(0, 0, 0, 0, true);
        }

        public ResourceDiffResult plus(ResourceDiffResult other) {
            return new ResourceDiffResult(added + other.added, updated + other.updated,
                    removed + other.removed, unchanged + other.unchanged,
                    protectedFromEmptyRemoval || other.protectedFromEmptyRemoval);
        }

        public boolean changed() {
            return added > 0 || updated > 0 || removed > 0;
        }
    }
}
