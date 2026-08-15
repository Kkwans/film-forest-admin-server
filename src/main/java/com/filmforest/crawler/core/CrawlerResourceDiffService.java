package com.filmforest.crawler.core;

import com.filmforest.crawler.model.ParsedResource;
import com.filmforest.crawler.model.ResourceParseStatus;
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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CrawlerResourceDiffService {

    private final ResourceNormalizer normalizer;
    private final ResourceMagnetMapper magnetMapper;
    private final ResourceCloudMapper cloudMapper;
    private final ResourceOnlineMapper onlineMapper;
    private final Map<EmptyObservationKey, Integer> completeEmptyStreaks = new ConcurrentHashMap<>();

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
     * Compatibility entry point for callers that do not yet provide parser diagnostics.
     * A non-empty kind is considered complete; omitted kinds remain partial and therefore
     * cannot remove existing resources.
     */
    @Transactional
    public ResourceDiffResult apply(String sourceCode, String contentType, long contentId,
                                    List<ParsedResource> parsedResources) {
        if (parsedResources == null || parsedResources.isEmpty()) {
            return ResourceDiffResult.emptyProtected();
        }
        return apply(sourceCode, contentType, contentId, parsedResources, Map.of());
    }

    /**
     * Applies each resource kind independently. Empty COMPLETE observations are intentionally
     * conservative: the same content/source/kind must report empty twice in this worker before
     * active rows are marked removed. PARTIAL, FAILED and NOT_SUPPORTED never remove rows.
     */
    @Transactional
    public ResourceDiffResult apply(String sourceCode, String contentType, long contentId,
                                    List<ParsedResource> parsedResources,
                                    Map<ParsedResource.Kind, ResourceParseStatus> statuses) {
        String normalizedSource = requireText(sourceCode, "sourceCode").toLowerCase(Locale.ROOT);
        String normalizedContentType = requireText(contentType, "contentType").toLowerCase(Locale.ROOT);
        List<ParsedResource> resources = parsedResources == null ? List.of() : parsedResources;
        EnumMap<ParsedResource.Kind, ResourceParseStatus> effectiveStatuses = statusesFor(resources, statuses);
        EnumMap<ParsedResource.Kind, List<ResourceNormalizer.NormalizedResource>> normalized =
                normalizePerKind(normalizedSource, resources, effectiveStatuses);
        LocalDateTime now = CrawlerTime.nowUtc();

        ResourceDiffResult result = diffMagnets(normalizedSource, normalizedContentType, contentId,
                normalized.getOrDefault(ParsedResource.Kind.MAGNET, List.of()),
                effectiveStatuses.get(ParsedResource.Kind.MAGNET), now);
        result = result.plus(diffCloud(normalizedSource, normalizedContentType, contentId,
                normalized.getOrDefault(ParsedResource.Kind.CLOUD, List.of()),
                effectiveStatuses.get(ParsedResource.Kind.CLOUD), now));
        return result.plus(diffOnline(normalizedSource, normalizedContentType, contentId,
                normalized.getOrDefault(ParsedResource.Kind.ONLINE, List.of()),
                effectiveStatuses.get(ParsedResource.Kind.ONLINE), now));
    }

    private EnumMap<ParsedResource.Kind, ResourceParseStatus> statusesFor(
            List<ParsedResource> resources,
            Map<ParsedResource.Kind, ResourceParseStatus> requested) {
        EnumMap<ParsedResource.Kind, ResourceParseStatus> result =
                new EnumMap<>(ParsedResource.Kind.class);
        for (ParsedResource.Kind kind : ParsedResource.Kind.values()) {
            result.put(kind, ResourceParseStatus.PARTIAL);
        }
        if (requested != null && !requested.isEmpty()) {
            requested.forEach((kind, status) -> {
                if (kind != null && status != null) result.put(kind, status);
            });
        } else {
            boolean invalid = false;
            for (ParsedResource resource : resources) {
                if (resource == null || resource.kind() == null) {
                    invalid = true;
                    continue;
                }
                result.put(resource.kind(), ResourceParseStatus.COMPLETE);
            }
            if (invalid) markAllPartial(result);
        }
        return result;
    }

    private EnumMap<ParsedResource.Kind, List<ResourceNormalizer.NormalizedResource>> normalizePerKind(
            String sourceCode,
            List<ParsedResource> resources,
            EnumMap<ParsedResource.Kind, ResourceParseStatus> statuses) {
        EnumMap<ParsedResource.Kind, LinkedHashMap<String, ResourceNormalizer.NormalizedResource>> byKind =
                new EnumMap<>(ParsedResource.Kind.class);
        for (ParsedResource.Kind kind : ParsedResource.Kind.values()) {
            byKind.put(kind, new LinkedHashMap<>());
        }
        Map<String, String> keyMaterials = new HashMap<>();
        for (ParsedResource resource : resources) {
            if (resource == null || resource.kind() == null) {
                markAllPartial(statuses);
                continue;
            }
            ResourceParseStatus status = statuses.get(resource.kind());
            if (status == ResourceParseStatus.FAILED || status == ResourceParseStatus.NOT_SUPPORTED) {
                continue;
            }
            try {
                ResourceNormalizer.NormalizedResource normalized = normalizer.normalize(sourceCode, resource);
                String previousMaterial = keyMaterials.putIfAbsent(normalized.resourceKey(),
                        normalized.keyMaterial());
                if (previousMaterial != null && !previousMaterial.equals(normalized.keyMaterial())) {
                    statuses.put(resource.kind(), ResourceParseStatus.PARTIAL);
                    continue;
                }
                byKind.get(resource.kind()).merge(normalized.resourceKey(), normalized,
                        ResourceNormalizer::preferEarlierSourceOrder);
            } catch (RuntimeException invalidResource) {
                statuses.put(resource.kind(), ResourceParseStatus.PARTIAL);
            }
        }
        EnumMap<ParsedResource.Kind, List<ResourceNormalizer.NormalizedResource>> result =
                new EnumMap<>(ParsedResource.Kind.class);
        byKind.forEach((kind, values) -> result.put(kind, values.values().stream()
                .sorted(Comparator.comparingInt(value -> value.resource().sourceOrder()))
                .toList()));
        return result;
    }

    private ResourceDiffResult diffMagnets(String sourceCode, String contentType, long contentId,
                                           List<ResourceNormalizer.NormalizedResource> incoming,
                                           ResourceParseStatus status, LocalDateTime now) {
        EmptyObservationKey streakKey = new EmptyObservationKey(sourceCode, contentType, contentId,
                ParsedResource.Kind.MAGNET);
        if (!acceptsIncoming(status)) {
            completeEmptyStreaks.remove(streakKey);
            return ResourceDiffResult.emptyProtected();
        }
        if (incoming.isEmpty()) {
            if (status != ResourceParseStatus.COMPLETE || !isSecondCompleteEmpty(streakKey)) {
                return ResourceDiffResult.emptyProtected();
            }
        } else {
            completeEmptyStreaks.remove(streakKey);
        }

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
            ResourceMagnet candidate = toMagnet(existing, sourceCode, contentType, contentId,
                    normalized.resourceKey(), parsed, now);
            if (existing == null) {
                magnetMapper.insert(candidate);
                stats.added++;
            } else if (!isActive(existing) || magnetChanged(existing, candidate)
                    || !sourceCode.equals(existing.getSourceCode())) {
                magnetMapper.updateCrawlerResource(candidate);
                stats.updated++;
            } else {
                magnetMapper.touchCrawlerResource(existing.getId(), now);
                stats.unchanged++;
            }
        }
        if (status == ResourceParseStatus.COMPLETE) {
            current.values().stream().filter(CrawlerResourceDiffService::isActive).forEach(resource -> {
                stats.removed += magnetMapper.markCrawlerResourceRemoved(resource.getId(), now);
            });
        }
        return stats.result();
    }

    private ResourceDiffResult diffCloud(String sourceCode, String contentType, long contentId,
                                         List<ResourceNormalizer.NormalizedResource> incoming,
                                         ResourceParseStatus status, LocalDateTime now) {
        EmptyObservationKey streakKey = new EmptyObservationKey(sourceCode, contentType, contentId,
                ParsedResource.Kind.CLOUD);
        if (!acceptsIncoming(status)) {
            completeEmptyStreaks.remove(streakKey);
            return ResourceDiffResult.emptyProtected();
        }
        if (incoming.isEmpty()) {
            if (status != ResourceParseStatus.COMPLETE || !isSecondCompleteEmpty(streakKey)) {
                return ResourceDiffResult.emptyProtected();
            }
        } else {
            completeEmptyStreaks.remove(streakKey);
        }

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
            ResourceCloud candidate = toCloud(existing, sourceCode, contentType, contentId,
                    normalized.resourceKey(), parsed, now);
            if (existing == null) {
                cloudMapper.insert(candidate);
                stats.added++;
            } else if (!isActive(existing) || cloudChanged(existing, candidate)
                    || !sourceCode.equals(existing.getSourceCode())) {
                cloudMapper.updateCrawlerResource(candidate);
                stats.updated++;
            } else {
                cloudMapper.touchCrawlerResource(existing.getId(), now);
                stats.unchanged++;
            }
        }
        if (status == ResourceParseStatus.COMPLETE) {
            current.values().stream().filter(CrawlerResourceDiffService::isActive).forEach(resource -> {
                stats.removed += cloudMapper.markCrawlerResourceRemoved(resource.getId(), now);
            });
        }
        return stats.result();
    }

    private ResourceDiffResult diffOnline(String sourceCode, String contentType, long contentId,
                                          List<ResourceNormalizer.NormalizedResource> incoming,
                                          ResourceParseStatus status, LocalDateTime now) {
        EmptyObservationKey streakKey = new EmptyObservationKey(sourceCode, contentType, contentId,
                ParsedResource.Kind.ONLINE);
        if (!acceptsIncoming(status)) {
            completeEmptyStreaks.remove(streakKey);
            return ResourceDiffResult.emptyProtected();
        }
        if (incoming.isEmpty()) {
            if (status != ResourceParseStatus.COMPLETE || !isSecondCompleteEmpty(streakKey)) {
                return ResourceDiffResult.emptyProtected();
            }
        } else {
            completeEmptyStreaks.remove(streakKey);
        }

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
            ResourceOnline candidate = toOnline(existing, sourceCode, contentType, contentId,
                    normalized.resourceKey(), parsed, now);
            if (existing == null) {
                onlineMapper.insert(candidate);
                stats.added++;
            } else if (!isActive(existing) || onlineChanged(existing, candidate)
                    || !sourceCode.equals(existing.getSourceCode())) {
                onlineMapper.updateCrawlerResource(candidate);
                stats.updated++;
            } else {
                onlineMapper.touchCrawlerResource(existing.getId(), now);
                stats.unchanged++;
            }
        }
        if (status == ResourceParseStatus.COMPLETE) {
            current.values().stream().filter(CrawlerResourceDiffService::isActive).forEach(resource -> {
                stats.removed += onlineMapper.markCrawlerResourceRemoved(resource.getId(), now);
            });
        }
        return stats.result();
    }

    private boolean isSecondCompleteEmpty(EmptyObservationKey key) {
        return completeEmptyStreaks.merge(key, 1, Integer::sum) >= 2;
    }

    private static boolean acceptsIncoming(ResourceParseStatus status) {
        return status == ResourceParseStatus.COMPLETE || status == ResourceParseStatus.PARTIAL;
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

    private static ResourceMagnet toMagnet(ResourceMagnet existing, String sourceCode, String contentType,
                                           long contentId, String key, ParsedResource parsed,
                                           LocalDateTime now) {
        ResourceMagnet entity = new ResourceMagnet();
        entity.setId(existing == null ? null : existing.getId());
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setSourceCode(sourceCode);
        entity.setResourceKey(key);
        entity.setRawText(firstNonBlank(parsed.rawText(), existing == null ? null : existing.getRawText()));
        entity.setLastSeenAt(now);
        entity.setTitle(firstNonBlank(limit(parsed.title(), 200), existing == null ? null : existing.getTitle()));
        entity.setMagnetUrl(firstNonBlank(parsed.url(), existing == null ? null : existing.getMagnetUrl()));
        entity.setResolution(firstNonBlank(parsed.resolution(), existing == null ? null : existing.getResolution()));
        entity.setHasSubtitle(parsed.hasSubtitle()
                || (existing != null && Boolean.TRUE.equals(existing.getHasSubtitle())));
        entity.setIsSpecialSub(parsed.specialSubtitle()
                || (existing != null && Boolean.TRUE.equals(existing.getIsSpecialSub())));
        entity.setSort(parsed.sourceOrder());
        entity.setDeleted(0);
        return entity;
    }

    private static ResourceCloud toCloud(ResourceCloud existing, String sourceCode, String contentType,
                                         long contentId, String key, ParsedResource parsed,
                                         LocalDateTime now) {
        ResourceCloud entity = new ResourceCloud();
        entity.setId(existing == null ? null : existing.getId());
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setSourceCode(sourceCode);
        entity.setResourceKey(key);
        entity.setRawText(firstNonBlank(parsed.rawText(), existing == null ? null : existing.getRawText()));
        entity.setLastSeenAt(now);
        entity.setDiskType(firstNonBlank(parsed.diskType(), existing == null ? null : existing.getDiskType()));
        entity.setTitle(firstNonBlank(limit(parsed.title(), 200), existing == null ? null : existing.getTitle()));
        entity.setUrl(firstNonBlank(parsed.url(), existing == null ? null : existing.getUrl()));
        entity.setPassword(firstNonBlank(limit(parsed.password(), 50),
                existing == null ? null : existing.getPassword()));
        entity.setSort(parsed.sourceOrder());
        entity.setDeleted(0);
        return entity;
    }

    private static ResourceOnline toOnline(ResourceOnline existing, String sourceCode, String contentType,
                                           long contentId, String key, ParsedResource parsed,
                                           LocalDateTime now) {
        ResourceOnline entity = new ResourceOnline();
        entity.setId(existing == null ? null : existing.getId());
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setSourceCode(sourceCode);
        entity.setResourceKey(key);
        entity.setRawText(firstNonBlank(parsed.rawText(), existing == null ? null : existing.getRawText()));
        entity.setLastSeenAt(now);
        entity.setSeason(parsed.season() == null && existing != null ? existing.getSeason() : parsed.season());
        entity.setEpisodeNumber(parsed.episodeNumber() == null && existing != null
                ? existing.getEpisodeNumber() : parsed.episodeNumber());
        entity.setEpisodeTitle(firstNonBlank(limit(parsed.episodeTitle(), 200),
                existing == null ? null : existing.getEpisodeTitle()));
        entity.setSourceName(firstNonBlank(limit(parsed.title(), 50),
                existing == null ? null : existing.getSourceName()));
        entity.setSourceUrl(firstNonBlank(parsed.url(), existing == null ? null : existing.getSourceUrl()));
        entity.setSourcePageUrl(firstNonBlank(parsed.sourcePageUrl(),
                existing == null ? null : existing.getSourcePageUrl()));
        String playbackType = firstNonBlank(parsed.playbackType(),
                existing == null ? null : existing.getPlaybackType());
        entity.setPlaybackType(playbackType == null ? "EXTERNAL_PAGE" : playbackType);
        entity.setSort(parsed.sourceOrder());
        entity.setDeleted(0);
        return entity;
    }

    private static boolean magnetChanged(ResourceMagnet existing, ResourceMagnet candidate) {
        return !Objects.equals(existing.getTitle(), candidate.getTitle())
                || !Objects.equals(existing.getMagnetUrl(), candidate.getMagnetUrl())
                || !Objects.equals(existing.getResolution(), candidate.getResolution())
                || !Objects.equals(existing.getHasSubtitle(), candidate.getHasSubtitle())
                || !Objects.equals(existing.getIsSpecialSub(), candidate.getIsSpecialSub())
                || !Objects.equals(existing.getSort(), candidate.getSort())
                || !Objects.equals(existing.getRawText(), candidate.getRawText());
    }

    private static boolean cloudChanged(ResourceCloud existing, ResourceCloud candidate) {
        return !Objects.equals(existing.getDiskType(), candidate.getDiskType())
                || !Objects.equals(existing.getTitle(), candidate.getTitle())
                || !Objects.equals(existing.getUrl(), candidate.getUrl())
                || !Objects.equals(existing.getPassword(), candidate.getPassword())
                || !Objects.equals(existing.getSort(), candidate.getSort())
                || !Objects.equals(existing.getRawText(), candidate.getRawText());
    }

    private static boolean onlineChanged(ResourceOnline existing, ResourceOnline candidate) {
        return !Objects.equals(existing.getSeason(), candidate.getSeason())
                || !Objects.equals(existing.getEpisodeNumber(), candidate.getEpisodeNumber())
                || !Objects.equals(existing.getEpisodeTitle(), candidate.getEpisodeTitle())
                || !Objects.equals(existing.getSourceName(), candidate.getSourceName())
                || !Objects.equals(existing.getSourceUrl(), candidate.getSourceUrl())
                || !Objects.equals(existing.getSourcePageUrl(), candidate.getSourcePageUrl())
                || !Objects.equals(existing.getPlaybackType(), candidate.getPlaybackType())
                || !Objects.equals(existing.getSort(), candidate.getSort())
                || !Objects.equals(existing.getRawText(), candidate.getRawText());
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

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static void markAllPartial(EnumMap<ParsedResource.Kind, ResourceParseStatus> statuses) {
        for (ParsedResource.Kind kind : ParsedResource.Kind.values()) {
            statuses.put(kind, ResourceParseStatus.PARTIAL);
        }
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

    private record EmptyObservationKey(String sourceCode, String contentType, long contentId,
                                       ParsedResource.Kind kind) {
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
