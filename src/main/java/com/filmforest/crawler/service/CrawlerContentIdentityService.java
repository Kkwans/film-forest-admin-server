package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerContentIdentity;
import com.filmforest.crawler.mapper.CrawlerContentIdentityMapper;
import com.filmforest.crawler.model.ParsedContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CrawlerContentIdentityService {

    private final CrawlerContentIdentityMapper mapper;

    public CrawlerContentIdentityService(CrawlerContentIdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Identity resolve(ParsedContent content, Long knownInternalContentId) {
        String contentType = content.contentType().value();
        String normalizedTitle = SourceFingerprint.normalizeTitle(content.title());
        String canonicalKey = SourceFingerprint.forCanonicalContent(
                content.contentType(), content.title(), content.year());

        CrawlerContentIdentity canonical = mapper.selectByCanonicalKey(contentType, canonicalKey);
        if (canonical != null) {
            return identity(canonical);
        }

        if (knownInternalContentId != null) {
            CrawlerContentIdentity known = mapper.selectById(knownInternalContentId);
            if (known != null) {
                if (!Objects.equals(contentType, known.getContentType())) {
                    throw new IllegalStateException("来源条目的内部内容类型与身份表不一致");
                }
                known.setCanonicalKey(canonicalKey);
                known.setNormalizedTitle(normalizedTitle);
                known.setReleaseYear(content.year());
                mapper.updateById(known);
                return identity(known);
            }
            CrawlerContentIdentity migrated = value(contentType, canonicalKey,
                    normalizedTitle, content.year());
            migrated.setId(knownInternalContentId);
            mapper.insert(migrated);
            return identity(migrated);
        }

        CrawlerContentIdentity reserved = value(contentType, canonicalKey,
                normalizedTitle, content.year());
        mapper.reserve(reserved);
        if (reserved.getId() == null) {
            CrawlerContentIdentity loaded = mapper.selectByCanonicalKey(contentType, canonicalKey);
            if (loaded == null) {
                throw new IllegalStateException("无法分配规范化内容身份");
            }
            reserved = loaded;
        }
        return identity(reserved);
    }

    private static CrawlerContentIdentity value(String contentType, String canonicalKey,
                                                 String normalizedTitle, Integer releaseYear) {
        CrawlerContentIdentity identity = new CrawlerContentIdentity();
        identity.setContentType(contentType);
        identity.setCanonicalKey(canonicalKey);
        identity.setNormalizedTitle(normalizedTitle);
        identity.setReleaseYear(releaseYear);
        return identity;
    }

    private static Identity identity(CrawlerContentIdentity value) {
        return new Identity(value.getId(), value.getCanonicalKey(), value.getNormalizedTitle(),
                value.getReleaseYear());
    }

    public record Identity(long contentId, String canonicalKey,
                           String normalizedTitle, Integer releaseYear) {
    }
}
