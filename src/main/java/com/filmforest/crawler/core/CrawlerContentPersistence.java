package com.filmforest.crawler.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.service.AnimeService;
import com.filmforest.content.service.DramaService;
import com.filmforest.content.service.MovieService;
import com.filmforest.content.service.ShortDramaService;
import com.filmforest.content.service.VarietyService;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.ParsedResource;
import com.filmforest.crawler.model.ResourceParseStatus;
import com.filmforest.crawler.service.CrawlerContentIdentityService;
import com.filmforest.crawler.service.CrawlerGenreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.EnumMap;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;

@Service
public class CrawlerContentPersistence {

    private final MovieService movieService;
    private final DramaService dramaService;
    private final VarietyService varietyService;
    private final AnimeService animeService;
    private final ShortDramaService shortDramaService;
    private final CrawlerResourceDiffService resourceDiffService;
    private final CrawlerGenreService genreService;
    private final CrawlerContentIdentityService identityService;
    private final ObjectMapper objectMapper;

    public CrawlerContentPersistence(MovieService movieService, DramaService dramaService,
                                     VarietyService varietyService, AnimeService animeService,
                                     ShortDramaService shortDramaService,
                                     CrawlerResourceDiffService resourceDiffService,
                                     CrawlerGenreService genreService,
                                     CrawlerContentIdentityService identityService,
                                     ObjectMapper objectMapper) {
        this.movieService = movieService;
        this.dramaService = dramaService;
        this.varietyService = varietyService;
        this.animeService = animeService;
        this.shortDramaService = shortDramaService;
        this.resourceDiffService = resourceDiffService;
        this.genreService = genreService;
        this.identityService = identityService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersistResult persist(String sourceCode, ParsedContent parsed) {
        return persist(sourceCode, parsed,
                genreService.resolve(sourceCode, parsed.contentType(), parsed.genres()), null);
    }

    @Transactional
    public PersistResult persist(String sourceCode, ParsedContent parsed,
                                 CrawlerGenreService.ResolvedGenres genres,
                                 Long knownInternalContentId) {
        CrawlerContentIdentityService.Identity identity = identityService.resolve(
                parsed, knownInternalContentId);
        long contentId = identity.contentId();
        boolean isNew = switch (parsed.contentType()) {
            case MOVIE -> persistMovie(contentId, parsed, genres.names());
            case DRAMA -> persistDrama(contentId, parsed, genres.names());
            case VARIETY -> persistVariety(contentId, parsed, genres.names());
            case ANIME -> persistAnime(contentId, parsed, genres.names());
            case SHORT_DRAMA -> persistShortDrama(contentId, parsed, genres.names());
        };
        genreService.replaceContentGenres(contentId, parsed.contentType(), genres);
        CrawlerResourceDiffService.ResourceDiffResult resourceDiff;
        if (parsed.diagnostics().resourceStatuses().isEmpty()) {
            resourceDiff = resourceDiffService.apply(
                    sourceCode, parsed.contentType().value(), contentId, parsed.resources());
        } else {
            EnumMap<ParsedResource.Kind, ResourceParseStatus> statuses =
                    new EnumMap<>(ParsedResource.Kind.class);
            statuses.putAll(parsed.diagnostics().resourceStatuses());
            if (!parsed.diagnostics().missingRequiredFields().isEmpty()) {
                statuses.replaceAll((kind, status) -> status == ResourceParseStatus.COMPLETE
                        ? ResourceParseStatus.PARTIAL : status);
            }
            resourceDiff = resourceDiffService.apply(sourceCode, parsed.contentType().value(),
                    contentId, parsed.resources(), statuses);
        }
        return new PersistResult(contentId, identity.canonicalKey(), isNew, !isNew, false, resourceDiff);
    }

    /**
     * 为列表指纹短路的成功条目读取当前内容快照，保证 Job 明细仍能展示可识别的信息。
     * 该方法只读内容表，不触碰资源差异、题材关联或任何爬虫状态。
     */
    public ParsedContent snapshot(com.filmforest.common.type.ContentType contentType,
                                  long contentId, String externalId, String sourceUrl) {
        return switch (contentType) {
            case MOVIE -> {
                Movie item = movieService.getById(contentId);
                yield item == null
                        ? emptySnapshot(contentType, contentId, externalId, sourceUrl)
                        : snapshot(contentType, item.getTitle(), item.getPosterUrl(), item.getYear(),
                        item.getRegion(), item.getGenre(), item.getDirector(), item.getWriter(),
                        item.getActor(), item.getLanguage(), item.getDuration(), item.getReleaseDate(),
                        item.getAlias(), item.getScoreDouban(), item.getScoreImdb(), item.getScoreRt(),
                        null, externalId, sourceUrl);
            }
            case DRAMA -> {
                Drama item = dramaService.getById(contentId);
                yield item == null
                        ? emptySnapshot(contentType, contentId, externalId, sourceUrl)
                        : snapshot(contentType, item.getTitle(), item.getPosterUrl(), item.getYear(),
                        item.getRegion(), item.getGenre(), item.getDirector(), item.getWriter(),
                        item.getActor(), item.getLanguage(), item.getDuration(), item.getReleaseDate(),
                        item.getAlias(), item.getScoreDouban(), item.getScoreImdb(), null,
                        item.getTotalEpisode(), externalId, sourceUrl);
            }
            case VARIETY -> {
                Variety item = varietyService.getById(contentId);
                yield item == null
                        ? emptySnapshot(contentType, contentId, externalId, sourceUrl)
                        : snapshot(contentType, item.getTitle(), item.getPosterUrl(), item.getYear(),
                        item.getRegion(), item.getGenre(), item.getDirector(), item.getWriter(),
                        item.getActor(), item.getLanguage(), item.getDuration(), item.getReleaseDate(),
                        item.getAlias(), item.getScoreDouban(), item.getScoreImdb(), null,
                        item.getTotalEpisode(), externalId, sourceUrl);
            }
            case ANIME -> {
                Anime item = animeService.getById(contentId);
                yield item == null
                        ? emptySnapshot(contentType, contentId, externalId, sourceUrl)
                        : snapshot(contentType, item.getTitle(), item.getPosterUrl(), item.getYear(),
                        item.getRegion(), item.getGenre(), item.getDirector(), item.getWriter(),
                        item.getActor(), item.getLanguage(), item.getDuration(), item.getReleaseDate(),
                        item.getAlias(), item.getScoreDouban(), item.getScoreImdb(), null,
                        item.getTotalEpisode(), externalId, sourceUrl);
            }
            case SHORT_DRAMA -> {
                ShortDrama item = shortDramaService.getById(contentId);
                yield item == null
                        ? emptySnapshot(contentType, contentId, externalId, sourceUrl)
                        : snapshot(contentType, item.getTitle(), item.getPosterUrl(), item.getYear(),
                        item.getRegion(), item.getGenre(), item.getDirector(), item.getWriter(),
                        item.getActor(), item.getLanguage(), item.getDuration(), item.getReleaseDate(),
                        item.getAlias(), item.getScoreDouban(), item.getScoreImdb(), null,
                        item.getTotalEpisode(), externalId, sourceUrl);
            }
        };
    }

    private ParsedContent snapshot(com.filmforest.common.type.ContentType contentType,
                                   String title, String posterUrl, Integer year,
                                   String regions, String genres, String directors,
                                   String writers, String actors, String languages,
                                   Integer duration, String releaseDate, String aliases,
                                   BigDecimal doubanScore, BigDecimal imdbScore,
                                   BigDecimal rottenTomatoesScore, Integer totalEpisodes,
                                   String externalId, String sourceUrl) {
        return new ParsedContent(
                externalId, contentType, sourceUrl,
                title == null || title.isBlank() ? "内容" : title,
                posterUrl, year, list(regions), list(genres), list(directors), list(writers),
                list(actors), list(languages), duration, parseDate(releaseDate), releaseDate,
                list(aliases), doubanScore, imdbScore, rottenTomatoesScore, null,
                totalEpisodes, List.of(),
                new com.filmforest.crawler.model.ParseDiagnostics(
                        List.of(), List.of(), List.of(), null, Map.of()));
    }

    private ParsedContent emptySnapshot(com.filmforest.common.type.ContentType contentType,
                                        long contentId, String externalId, String sourceUrl) {
        return snapshot(contentType, "内容 #" + contentId, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                externalId, sourceUrl);
    }

    private List<String> list(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return List.of(value);
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean persistMovie(long id, ParsedContent parsed, List<String> genres) {
        boolean isNew = movieService.getById(id) == null;
        Movie entity = new Movie();
        entity.setId(id);
        entity.setTitle(parsed.title());
        entity.setAlias(json(parsed.aliases(), isNew));
        entity.setPosterUrl(parsed.sourcePosterUrl());
        entity.setYear(parsed.year());
        entity.setDirector(json(parsed.directors(), isNew));
        entity.setWriter(json(parsed.writers(), isNew));
        entity.setActor(json(parsed.actors(), isNew));
        entity.setGenre(json(genres, true));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        entity.setScoreRt(parsed.rottenTomatoesScore());
        if (isNew) entity.setStatus(0);
        saveOrUpdate(movieService, entity, isNew);
        return isNew;
    }

    private boolean persistDrama(long id, ParsedContent parsed, List<String> genres) {
        boolean isNew = dramaService.getById(id) == null;
        Drama entity = new Drama();
        entity.setId(id);
        applySeries(entity, parsed, genres, isNew);
        saveOrUpdate(dramaService, entity, isNew);
        return isNew;
    }

    private boolean persistVariety(long id, ParsedContent parsed, List<String> genres) {
        boolean isNew = varietyService.getById(id) == null;
        Variety entity = new Variety();
        entity.setId(id);
        entity.setTitle(parsed.title());
        entity.setAlias(json(parsed.aliases(), isNew));
        entity.setPosterUrl(parsed.sourcePosterUrl());
        entity.setYear(parsed.year());
        entity.setDirector(json(parsed.directors(), isNew));
        entity.setWriter(json(parsed.writers(), isNew));
        entity.setActor(json(parsed.actors(), isNew));
        entity.setGenre(json(genres, true));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        if (isNew) entity.setStatus(0);
        saveOrUpdate(varietyService, entity, isNew);
        return isNew;
    }

    private boolean persistAnime(long id, ParsedContent parsed, List<String> genres) {
        boolean isNew = animeService.getById(id) == null;
        Anime entity = new Anime();
        entity.setId(id);
        entity.setTitle(parsed.title());
        entity.setAlias(json(parsed.aliases(), isNew));
        entity.setPosterUrl(parsed.sourcePosterUrl());
        entity.setYear(parsed.year());
        entity.setDirector(json(parsed.directors(), isNew));
        entity.setWriter(json(parsed.writers(), isNew));
        entity.setActor(json(parsed.actors(), isNew));
        entity.setGenre(json(genres, true));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        if (isNew) entity.setStatus(0);
        saveOrUpdate(animeService, entity, isNew);
        return isNew;
    }

    private boolean persistShortDrama(long id, ParsedContent parsed, List<String> genres) {
        boolean isNew = shortDramaService.getById(id) == null;
        ShortDrama entity = new ShortDrama();
        entity.setId(id);
        entity.setTitle(parsed.title());
        entity.setAlias(json(parsed.aliases(), isNew));
        entity.setPosterUrl(parsed.sourcePosterUrl());
        entity.setYear(parsed.year());
        entity.setDirector(json(parsed.directors(), isNew));
        entity.setWriter(json(parsed.writers(), isNew));
        entity.setActor(json(parsed.actors(), isNew));
        entity.setGenre(json(genres, true));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        if (isNew) entity.setStatus(0);
        saveOrUpdate(shortDramaService, entity, isNew);
        return isNew;
    }

    private void applySeries(Drama entity, ParsedContent parsed, List<String> genres, boolean isNew) {
        entity.setTitle(parsed.title());
        entity.setAlias(json(parsed.aliases(), isNew));
        entity.setPosterUrl(parsed.sourcePosterUrl());
        entity.setYear(parsed.year());
        entity.setDirector(json(parsed.directors(), isNew));
        entity.setWriter(json(parsed.writers(), isNew));
        entity.setActor(json(parsed.actors(), isNew));
        entity.setGenre(json(genres, true));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        if (isNew) entity.setStatus(0);
    }

    private String json(List<String> values, boolean isNew) {
        if (values == null || values.isEmpty()) return isNew ? "[]" : null;
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize parsed content list", error);
        }
    }

    private static String firstNonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void saveOrUpdate(MovieService service, Movie entity, boolean isNew) {
        if (isNew) service.save(entity); else service.updateById(entity);
    }

    private static void saveOrUpdate(DramaService service, Drama entity, boolean isNew) {
        if (isNew) service.save(entity); else service.updateById(entity);
    }

    private static void saveOrUpdate(VarietyService service, Variety entity, boolean isNew) {
        if (isNew) service.save(entity); else service.updateById(entity);
    }

    private static void saveOrUpdate(AnimeService service, Anime entity, boolean isNew) {
        if (isNew) service.save(entity); else service.updateById(entity);
    }

    private static void saveOrUpdate(ShortDramaService service, ShortDrama entity, boolean isNew) {
        if (isNew) service.save(entity); else service.updateById(entity);
    }

    public record PersistResult(long contentId, String canonicalKey,
                                boolean added, boolean updated, boolean unchanged,
                                CrawlerResourceDiffService.ResourceDiffResult resourceDiff) {
        public PersistResult(boolean added, boolean updated, boolean unchanged) {
            this(-1L, null, added, updated, unchanged,
                    new CrawlerResourceDiffService.ResourceDiffResult(0, 0, 0, 0, false));
        }
    }
}
