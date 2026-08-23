package com.filmforest.crawler.source.pkmp4;

import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.ParsedResource;
import com.filmforest.crawler.model.ResourceParseStatus;
import com.filmforest.crawler.source.CrawlerResourceEnricher;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
        return enrich(parsed, httpFetcher, rateLimitMs, cancellation, progress -> { });
    }

    public ParsedContent enrich(ParsedContent parsed, HttpFetcher httpFetcher,
                                int rateLimitMs, AtomicBoolean cancellation,
                                Consumer<CrawlerResourceEnricher.Progress> progress) {
        List<ParsedResource> enriched = new ArrayList<>(parsed.resources().size());
        boolean partial = false;
        int onlineTotal = (int) parsed.resources().stream()
                .filter(resource -> resource.kind() == ParsedResource.Kind.ONLINE)
                .count();
        int onlineProcessed = 0;
        for (ParsedResource resource : parsed.resources()) {
            if (resource.kind() != ParsedResource.Kind.ONLINE) {
                enriched.add(resource);
                continue;
            }
            if (cancelled(cancellation)) {
                partial = true;
                enriched.add(resource);
                continue;
            }
            URI pageUri = trustedPlayerPage(resource.sourcePageUrl() == null
                    ? resource.url() : resource.sourcePageUrl());
            if (pageUri == null) {
                partial = true;
                enriched.add(resource);
                onlineProcessed++;
                report(progress, onlineProcessed, onlineTotal, "在线播放页面地址无效");
                continue;
            }
            report(progress, onlineProcessed, onlineTotal,
                    "正在解析在线播放 " + (onlineProcessed + 1) + "/" + onlineTotal);
            FetchResult fetch = httpFetcher.fetch(pageUri,
                    Map.of("Referer", parsed.sourceUrl()),
                    Math.max(MIN_PLAYBACK_PAGE_DELAY_MS, rateLimitMs), cancellation);
            onlineProcessed++;
            if (!fetch.successful()) {
                partial = true;
                enriched.add(resource);
                report(progress, onlineProcessed, onlineTotal,
                        "在线播放页面解析失败，已保留来源页");
                continue;
            }
            var playback = pageParser.parse(fetch.body(), fetch.finalUrl());
            if (playback.isEmpty()) {
                partial = true;
                enriched.add(resource);
                report(progress, onlineProcessed, onlineTotal,
                        "在线播放地址未找到，已保留来源页");
                continue;
            }
            enriched.add(new ParsedResource(
                    resource.kind(), resource.title(), playback.get().url(), resource.diskType(),
                    resource.password(), resource.resolution(), resource.hasSubtitle(),
                    resource.specialSubtitle(), resource.season(), resource.episodeNumber(),
                    resource.episodeTitle(), resource.sourceOrder(), resource.rawText(),
                pageUri.toString(), playback.get().playbackType()));
            report(progress, onlineProcessed, onlineTotal,
                    "在线播放解析完成 " + onlineProcessed + "/" + onlineTotal);
        }
        ParsedContent result = parsed.withResources(enriched);
        return partial ? result.withResourceStatus(ParsedResource.Kind.ONLINE,
                ResourceParseStatus.PARTIAL) : result;
    }

    private static void report(Consumer<CrawlerResourceEnricher.Progress> progress,
                               int completed, int total, String message) {
        if (progress == null) return;
        int percent = total <= 0 ? 85 : 65 + (completed * 20 / total);
        progress.accept(new CrawlerResourceEnricher.Progress("ONLINE", percent, message));
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
