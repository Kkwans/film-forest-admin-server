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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CrawlerContentPersistence {

    private final MovieService movieService;
    private final DramaService dramaService;
    private final VarietyService varietyService;
    private final AnimeService animeService;
    private final ShortDramaService shortDramaService;
    private final CrawlerResourceDiffService resourceDiffService;
    private final ObjectMapper objectMapper;

    public CrawlerContentPersistence(MovieService movieService, DramaService dramaService,
                                     VarietyService varietyService, AnimeService animeService,
                                     ShortDramaService shortDramaService,
                                     CrawlerResourceDiffService resourceDiffService,
                                     ObjectMapper objectMapper) {
        this.movieService = movieService;
        this.dramaService = dramaService;
        this.varietyService = varietyService;
        this.animeService = animeService;
        this.shortDramaService = shortDramaService;
        this.resourceDiffService = resourceDiffService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersistResult persist(String sourceCode, ParsedContent parsed) {
        long contentId = numericExternalId(parsed.externalId());
        boolean isNew = switch (parsed.contentType()) {
            case MOVIE -> persistMovie(contentId, parsed);
            case DRAMA -> persistDrama(contentId, parsed);
            case VARIETY -> persistVariety(contentId, parsed);
            case ANIME -> persistAnime(contentId, parsed);
            case SHORT_DRAMA -> persistShortDrama(contentId, parsed);
        };
        CrawlerResourceDiffService.ResourceDiffResult resourceDiff = resourceDiffService.apply(
                sourceCode, parsed.contentType().value(), contentId, parsed.resources());
        return new PersistResult(isNew, !isNew, false, resourceDiff);
    }

    private boolean persistMovie(long id, ParsedContent parsed) {
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
        entity.setGenre(json(parsed.genres(), isNew));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        entity.setStatus(1);
        saveOrUpdate(movieService, entity, isNew);
        return isNew;
    }

    private boolean persistDrama(long id, ParsedContent parsed) {
        boolean isNew = dramaService.getById(id) == null;
        Drama entity = new Drama();
        entity.setId(id);
        applySeries(entity, parsed, isNew);
        saveOrUpdate(dramaService, entity, isNew);
        return isNew;
    }

    private boolean persistVariety(long id, ParsedContent parsed) {
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
        entity.setGenre(json(parsed.genres(), isNew));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        entity.setStatus(1);
        saveOrUpdate(varietyService, entity, isNew);
        return isNew;
    }

    private boolean persistAnime(long id, ParsedContent parsed) {
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
        entity.setGenre(json(parsed.genres(), isNew));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        entity.setStatus(1);
        saveOrUpdate(animeService, entity, isNew);
        return isNew;
    }

    private boolean persistShortDrama(long id, ParsedContent parsed) {
        boolean isNew = shortDramaService.getById(id) == null;
        ShortDrama entity = new ShortDrama();
        entity.setId(id);
        entity.setTitle(parsed.title());
        entity.setAlias(json(parsed.aliases(), isNew));
        entity.setPosterUrl(parsed.sourcePosterUrl());
        entity.setYear(parsed.year());
        entity.setDirector(json(parsed.directors(), isNew));
        entity.setActor(json(parsed.actors(), isNew));
        entity.setGenre(json(parsed.genres(), isNew));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        entity.setStatus(1);
        saveOrUpdate(shortDramaService, entity, isNew);
        return isNew;
    }

    private void applySeries(Drama entity, ParsedContent parsed, boolean isNew) {
        entity.setTitle(parsed.title());
        entity.setAlias(json(parsed.aliases(), isNew));
        entity.setPosterUrl(parsed.sourcePosterUrl());
        entity.setYear(parsed.year());
        entity.setDirector(json(parsed.directors(), isNew));
        entity.setWriter(json(parsed.writers(), isNew));
        entity.setActor(json(parsed.actors(), isNew));
        entity.setGenre(json(parsed.genres(), isNew));
        entity.setRegion(json(parsed.regions(), isNew));
        entity.setLanguage(json(parsed.languages(), isNew));
        entity.setReleaseDate(firstNonBlank(parsed.rawReleaseDate()));
        entity.setDuration(parsed.durationMinutes());
        entity.setTotalEpisode(parsed.totalEpisodes());
        entity.setStoryline(firstNonBlank(parsed.storyline()));
        entity.setScoreDouban(parsed.doubanScore());
        entity.setScoreImdb(parsed.imdbScore());
        entity.setStatus(1);
    }

    private String json(List<String> values, boolean isNew) {
        if (values == null || values.isEmpty()) return isNew ? "[]" : null;
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize parsed content list", error);
        }
    }

    private static long numericExternalId(String externalId) {
        try {
            return Long.parseLong(externalId);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Source external ID is not numeric: " + externalId, invalid);
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

    public record PersistResult(boolean added, boolean updated, boolean unchanged,
                                CrawlerResourceDiffService.ResourceDiffResult resourceDiff) {
        public PersistResult(boolean added, boolean updated, boolean unchanged) {
            this(added, updated, unchanged,
                    new CrawlerResourceDiffService.ResourceDiffResult(0, 0, 0, 0, false));
        }
    }
}
