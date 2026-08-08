package com.filmforest.crawler.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
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
    private final ResourceMagnetMapper magnetMapper;
    private final ResourceCloudMapper cloudMapper;
    private final ResourceOnlineMapper onlineMapper;
    private final ObjectMapper objectMapper;

    public CrawlerContentPersistence(MovieService movieService, DramaService dramaService,
                                     VarietyService varietyService, AnimeService animeService,
                                     ShortDramaService shortDramaService,
                                     ResourceMagnetMapper magnetMapper, ResourceCloudMapper cloudMapper,
                                     ResourceOnlineMapper onlineMapper, ObjectMapper objectMapper) {
        this.movieService = movieService;
        this.dramaService = dramaService;
        this.varietyService = varietyService;
        this.animeService = animeService;
        this.shortDramaService = shortDramaService;
        this.magnetMapper = magnetMapper;
        this.cloudMapper = cloudMapper;
        this.onlineMapper = onlineMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersistResult persist(ParsedContent parsed) {
        long contentId = numericExternalId(parsed.externalId());
        boolean isNew = switch (parsed.contentType()) {
            case MOVIE -> persistMovie(contentId, parsed);
            case DRAMA -> persistDrama(contentId, parsed);
            case VARIETY -> persistVariety(contentId, parsed);
            case ANIME -> persistAnime(contentId, parsed);
            case SHORT_DRAMA -> persistShortDrama(contentId, parsed);
        };
        replaceResources(parsed.contentType().value(), contentId, parsed.resources());
        return new PersistResult(isNew, !isNew, false);
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

    private void replaceResources(String contentType, long contentId, List<ParsedResource> resources) {
        magnetMapper.delete(new LambdaQueryWrapper<ResourceMagnet>()
                .eq(ResourceMagnet::getContentType, contentType).eq(ResourceMagnet::getContentId, contentId));
        cloudMapper.delete(new LambdaQueryWrapper<ResourceCloud>()
                .eq(ResourceCloud::getContentType, contentType).eq(ResourceCloud::getContentId, contentId));
        onlineMapper.delete(new LambdaQueryWrapper<ResourceOnline>()
                .eq(ResourceOnline::getContentType, contentType).eq(ResourceOnline::getContentId, contentId));
        for (ParsedResource resource : resources) {
            switch (resource.kind()) {
                case MAGNET -> insertMagnet(contentType, contentId, resource);
                case CLOUD -> insertCloud(contentType, contentId, resource);
                case ONLINE -> insertOnline(contentType, contentId, resource);
            }
        }
    }

    private void insertMagnet(String contentType, long contentId, ParsedResource parsed) {
        ResourceMagnet entity = new ResourceMagnet();
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setTitle(limit(parsed.title(), 200));
        entity.setMagnetUrl(parsed.url());
        entity.setResolution(parsed.resolution());
        entity.setHasSubtitle(parsed.hasSubtitle());
        entity.setIsSpecialSub(parsed.specialSubtitle());
        entity.setSort(parsed.sourceOrder());
        magnetMapper.insert(entity);
    }

    private void insertCloud(String contentType, long contentId, ParsedResource parsed) {
        ResourceCloud entity = new ResourceCloud();
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setDiskType(parsed.diskType());
        entity.setTitle(limit(parsed.title(), 200));
        entity.setUrl(parsed.url());
        entity.setPassword(limit(parsed.password(), 50));
        entity.setSort(parsed.sourceOrder());
        cloudMapper.insert(entity);
    }

    private void insertOnline(String contentType, long contentId, ParsedResource parsed) {
        ResourceOnline entity = new ResourceOnline();
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setSeason(parsed.season());
        entity.setEpisodeNumber(parsed.episodeNumber());
        entity.setEpisodeTitle(limit(parsed.episodeTitle(), 200));
        entity.setSourceName(limit(parsed.title(), 50));
        entity.setSourceUrl(parsed.url());
        entity.setSort(parsed.sourceOrder());
        onlineMapper.insert(entity);
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

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
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

    public record PersistResult(boolean added, boolean updated, boolean unchanged) { }
}
