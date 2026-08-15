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
