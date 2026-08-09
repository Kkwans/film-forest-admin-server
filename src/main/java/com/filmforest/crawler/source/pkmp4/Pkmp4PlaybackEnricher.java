package com.filmforest.crawler.source.pkmp4;

import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.ParsedResource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 低频解析七味网公开播放页；失败时保留来源页作为明确降级。 */
@Component
public class Pkmp4PlaybackEnricher {

    static final int MIN_PLAYBACK_PAGE_DELAY_MS = 3_000;
    private final Pkmp4PlaybackPageParser pageParser;

    public Pkmp4PlaybackEnricher(Pkmp4PlaybackPageParser pageParser) {
        this.pageParser = pageParser;
    }

    public ParsedContent enrich(ParsedContent parsed, HttpFetcher httpFetcher,
                                int rateLimitMs, AtomicBoolean cancellation) {
        List<ParsedResource> enriched = new ArrayList<>(parsed.resources().size());
        for (ParsedResource resource : parsed.resources()) {
            if (cancelled(cancellation) || resource.kind() != ParsedResource.Kind.ONLINE) {
                enriched.add(resource);
                continue;
            }
            URI pageUri = trustedPlayerPage(resource.sourcePageUrl() == null
                    ? resource.url() : resource.sourcePageUrl());
            if (pageUri == null) {
                enriched.add(resource);
                continue;
            }
            FetchResult fetch = httpFetcher.fetch(pageUri,
                    Map.of("Referer", parsed.sourceUrl()),
                    Math.max(MIN_PLAYBACK_PAGE_DELAY_MS, rateLimitMs), cancellation);
            if (!fetch.successful()) {
                enriched.add(resource);
                continue;
            }
            var playback = pageParser.parse(fetch.body(), fetch.finalUrl());
            if (playback.isEmpty()) {
                enriched.add(resource);
                continue;
            }
            enriched.add(new ParsedResource(
                    resource.kind(), resource.title(), playback.get().url(), resource.diskType(),
                    resource.password(), resource.resolution(), resource.hasSubtitle(),
                    resource.specialSubtitle(), resource.season(), resource.episodeNumber(),
                    resource.episodeTitle(), resource.sourceOrder(), resource.rawText(),
                    pageUri.toString(), playback.get().playbackType()));
        }
        return parsed.withResources(enriched);
    }

    private static URI trustedPlayerPage(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("www.pkmp4.xyz") || host.equalsIgnoreCase("pkmp4.xyz"))
                    || uri.getPath() == null || !uri.getPath().startsWith("/py/")) {
                return null;
            }
            return uri;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean cancelled(AtomicBoolean cancellation) {
        return cancellation != null && cancellation.get();
    }
}
