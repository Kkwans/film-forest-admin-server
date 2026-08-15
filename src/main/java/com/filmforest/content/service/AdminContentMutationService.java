package com.filmforest.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.model.ContentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端内容与标准题材的原子写入边界。
 *
 * <p>genre JSON 仅作为兼容投影，由标准题材生成；管理端 API 不再接受自由文本 genre。</p>
 */
@Service
public class AdminContentMutationService {

    private final MovieService movieService;
    private final DramaService dramaService;
    private final VarietyService varietyService;
    private final AnimeService animeService;
    private final ShortDramaService shortDramaService;
    private final TagService tagService;
    private final ObjectMapper objectMapper;

    public AdminContentMutationService(MovieService movieService,
                                       DramaService dramaService,
                                       VarietyService varietyService,
                                       AnimeService animeService,
                                       ShortDramaService shortDramaService,
                                       TagService tagService,
                                       ObjectMapper objectMapper) {
        this.movieService = movieService;
        this.dramaService = dramaService;
        this.varietyService = varietyService;
        this.animeService = animeService;
        this.shortDramaService = shortDramaService;
        this.tagService = tagService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Movie createMovie(Movie content) {
        GenreSelection genres = prepareCreate("movie", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setStatus(genres.status());
        content.setGenre(genres.projection());
        requireSaved(movieService.save(content));
        tagService.setContentGenres(content.getId(), "movie", genres.tagIds());
        content.setGenreTagIds(genres.tagIds());
        return content;
    }

    @Transactional
    public Movie updateMovie(Long id, Movie content) {
        Movie existing = requireContent(movieService.getDetail(id));
        GenreSelection genres = prepareUpdate("movie", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setId(id);
        prepareReplacement(content::setGenre, content::setStatus, existing.getGenre(), existing.getStatus(),
                content.getStatus(), genres);
        requireUpdated(replaceMovie(id, content));
        if (genres != null) tagService.setContentGenres(id, "movie", genres.tagIds());
        Movie updated = requireContent(movieService.getDetail(id));
        updated.setGenreTagIds(currentGenreIds(id, "movie"));
        return updated;
    }

    @Transactional
    public Drama createDrama(Drama content) {
        GenreSelection genres = prepareCreate("drama", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setStatus(genres.status());
        content.setGenre(genres.projection());
        requireSaved(dramaService.save(content));
        tagService.setContentGenres(content.getId(), "drama", genres.tagIds());
        content.setGenreTagIds(genres.tagIds());
        return content;
    }

    @Transactional
    public Drama updateDrama(Long id, Drama content) {
        Drama existing = requireContent(dramaService.getDetail(id));
        GenreSelection genres = prepareUpdate("drama", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setId(id);
        prepareReplacement(content::setGenre, content::setStatus, existing.getGenre(), existing.getStatus(),
                content.getStatus(), genres);
        requireUpdated(replaceDrama(id, content));
        if (genres != null) tagService.setContentGenres(id, "drama", genres.tagIds());
        Drama updated = requireContent(dramaService.getDetail(id));
        updated.setGenreTagIds(currentGenreIds(id, "drama"));
        return updated;
    }

    @Transactional
    public Variety createVariety(Variety content) {
        GenreSelection genres = prepareCreate("variety", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setStatus(genres.status());
        content.setGenre(genres.projection());
        requireSaved(varietyService.save(content));
        tagService.setContentGenres(content.getId(), "variety", genres.tagIds());
        content.setGenreTagIds(genres.tagIds());
        return content;
    }

    @Transactional
    public Variety updateVariety(Long id, Variety content) {
        Variety existing = requireContent(varietyService.getDetail(id));
        GenreSelection genres = prepareUpdate("variety", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setId(id);
        prepareReplacement(content::setGenre, content::setStatus, existing.getGenre(), existing.getStatus(),
                content.getStatus(), genres);
        requireUpdated(replaceVariety(id, content));
        if (genres != null) tagService.setContentGenres(id, "variety", genres.tagIds());
        Variety updated = requireContent(varietyService.getDetail(id));
        updated.setGenreTagIds(currentGenreIds(id, "variety"));
        return updated;
    }

    @Transactional
    public Anime createAnime(Anime content) {
        GenreSelection genres = prepareCreate("anime", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setStatus(genres.status());
        content.setGenre(genres.projection());
        requireSaved(animeService.save(content));
        tagService.setContentGenres(content.getId(), "anime", genres.tagIds());
        content.setGenreTagIds(genres.tagIds());
        return content;
    }

    @Transactional
    public Anime updateAnime(Long id, Anime content) {
        Anime existing = requireContent(animeService.getDetail(id));
        GenreSelection genres = prepareUpdate("anime", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setId(id);
        prepareReplacement(content::setGenre, content::setStatus, existing.getGenre(), existing.getStatus(),
                content.getStatus(), genres);
        requireUpdated(replaceAnime(id, content));
        if (genres != null) tagService.setContentGenres(id, "anime", genres.tagIds());
        Anime updated = requireContent(animeService.getDetail(id));
        updated.setGenreTagIds(currentGenreIds(id, "anime"));
        return updated;
    }

    @Transactional
    public ShortDrama createShortDrama(ShortDrama content) {
        GenreSelection genres = prepareCreate("short_drama", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setStatus(genres.status());
        content.setGenre(genres.projection());
        requireSaved(shortDramaService.save(content));
        tagService.setContentGenres(content.getId(), "short_drama", genres.tagIds());
        content.setGenreTagIds(genres.tagIds());
        return content;
    }

    @Transactional
    public ShortDrama updateShortDrama(Long id, ShortDrama content) {
        ShortDrama existing = requireContent(shortDramaService.getDetail(id));
        GenreSelection genres = prepareUpdate("short_drama", content.getStatus(), content.getGenre(),
                content.getGenreTagIds());
        content.setId(id);
        prepareReplacement(content::setGenre, content::setStatus, existing.getGenre(), existing.getStatus(),
                content.getStatus(), genres);
        requireUpdated(replaceShortDrama(id, content));
        if (genres != null) tagService.setContentGenres(id, "short_drama", genres.tagIds());
        ShortDrama updated = requireContent(shortDramaService.getDetail(id));
        updated.setGenreTagIds(currentGenreIds(id, "short_drama"));
        return updated;
    }

    private GenreSelection prepareCreate(String type, Integer status, String rawGenre, List<Long> tagIds) {
        int normalizedStatus = status == null ? ContentStatus.DRAFT.code() : requireStatus(status);
        List<Long> normalizedIds = tagIds == null ? List.of() : tagIds;
        return prepareGenres(type, normalizedStatus, rawGenre, normalizedIds);
    }

    private GenreSelection prepareUpdate(String type, Integer status, String rawGenre, List<Long> tagIds) {
        if (status != null) requireStatus(status);
        if (tagIds == null) {
            rejectFreeTextGenre(rawGenre);
            return null;
        }
        return prepareGenres(type, status, rawGenre, tagIds);
    }

    private GenreSelection prepareGenres(String type, Integer status, String rawGenre, List<Long> tagIds) {
        rejectFreeTextGenre(rawGenre);
        List<Tag> genres = tagService.requireStandardGenres(type, tagIds);
        List<Long> normalizedIds = genres.stream().map(Tag::getId).toList();
        List<String> names = genres.stream().map(Tag::getName).toList();
        try {
            return new GenreSelection(status, normalizedIds, objectMapper.writeValueAsString(names));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("标准题材投影生成失败", exception);
        }
    }

    private List<Long> currentGenreIds(Long contentId, String contentType) {
        return tagService.getContentTags(contentId, contentType).stream()
                .filter(tag -> Integer.valueOf(1).equals(tag.getSystem()))
                .map(Tag::getId)
                .toList();
    }

    private static int requireStatus(int status) {
        if (!ContentStatus.isValid(status)) {
            throw new IllegalArgumentException("非法的状态值: " + status + "，只允许 0、1 或 2");
        }
        return status;
    }

    private static void rejectFreeTextGenre(String rawGenre) {
        if (rawGenre != null) {
            throw new IllegalArgumentException("不再接受自由文本题材，请提交 genreTagIds");
        }
    }

    private static void prepareReplacement(java.util.function.Consumer<String> genreSetter,
                                           java.util.function.Consumer<Integer> statusSetter,
                                           String existingGenre,
                                           Integer existingStatus,
                                           Integer requestedStatus,
                                           GenreSelection genres) {
        genreSetter.accept(genres == null ? existingGenre : genres.projection());
        if (requestedStatus == null) statusSetter.accept(existingStatus);
    }

    private boolean replaceMovie(Long id, Movie content) {
        return movieService.update(new UpdateWrapper<Movie>()
                .eq("id", id)
                .set("title", content.getTitle())
                .set("alias", content.getAlias())
                .set("poster_url", content.getPosterUrl())
                .set("year", content.getYear())
                .set("director", content.getDirector())
                .set("writer", content.getWriter())
                .set("actor", content.getActor())
                .set("genre", content.getGenre())
                .set("region", content.getRegion())
                .set("language", content.getLanguage())
                .set("release_date", content.getReleaseDate())
                .set("duration", content.getDuration())
                .set("storyline", content.getStoryline())
                .set("score_douban", content.getScoreDouban())
                .set("score_imdb", content.getScoreImdb())
                .set("score_rt", content.getScoreRt())
                .set("series_name", content.getSeriesName())
                .set("series_order", content.getSeriesOrder())
                .set("status", content.getStatus()));
    }

    private boolean replaceDrama(Long id, Drama content) {
        return dramaService.update(new UpdateWrapper<Drama>()
                .eq("id", id)
                .set("title", content.getTitle())
                .set("alias", content.getAlias())
                .set("poster_url", content.getPosterUrl())
                .set("year", content.getYear())
                .set("director", content.getDirector())
                .set("writer", content.getWriter())
                .set("actor", content.getActor())
                .set("genre", content.getGenre())
                .set("region", content.getRegion())
                .set("language", content.getLanguage())
                .set("release_date", content.getReleaseDate())
                .set("duration", content.getDuration())
                .set("total_episode", content.getTotalEpisode())
                .set("storyline", content.getStoryline())
                .set("score_douban", content.getScoreDouban())
                .set("score_imdb", content.getScoreImdb())
                .set("status", content.getStatus()));
    }

    private boolean replaceVariety(Long id, Variety content) {
        return varietyService.update(new UpdateWrapper<Variety>()
                .eq("id", id)
                .set("title", content.getTitle())
                .set("alias", content.getAlias())
                .set("poster_url", content.getPosterUrl())
                .set("year", content.getYear())
                .set("director", content.getDirector())
                .set("writer", content.getWriter())
                .set("actor", content.getActor())
                .set("genre", content.getGenre())
                .set("region", content.getRegion())
                .set("language", content.getLanguage())
                .set("release_date", content.getReleaseDate())
                .set("duration", content.getDuration())
                .set("total_episode", content.getTotalEpisode())
                .set("storyline", content.getStoryline())
                .set("score_douban", content.getScoreDouban())
                .set("score_imdb", content.getScoreImdb())
                .set("status", content.getStatus()));
    }

    private boolean replaceAnime(Long id, Anime content) {
        return animeService.update(new UpdateWrapper<Anime>()
                .eq("id", id)
                .set("title", content.getTitle())
                .set("alias", content.getAlias())
                .set("poster_url", content.getPosterUrl())
                .set("year", content.getYear())
                .set("director", content.getDirector())
                .set("writer", content.getWriter())
                .set("actor", content.getActor())
                .set("genre", content.getGenre())
                .set("region", content.getRegion())
                .set("language", content.getLanguage())
                .set("release_date", content.getReleaseDate())
                .set("duration", content.getDuration())
                .set("total_episode", content.getTotalEpisode())
                .set("storyline", content.getStoryline())
                .set("score_douban", content.getScoreDouban())
                .set("score_imdb", content.getScoreImdb())
                .set("status", content.getStatus()));
    }

    private boolean replaceShortDrama(Long id, ShortDrama content) {
        return shortDramaService.update(new UpdateWrapper<ShortDrama>()
                .eq("id", id)
                .set("title", content.getTitle())
                .set("alias", content.getAlias())
                .set("poster_url", content.getPosterUrl())
                .set("year", content.getYear())
                .set("director", content.getDirector())
                .set("writer", content.getWriter())
                .set("actor", content.getActor())
                .set("genre", content.getGenre())
                .set("region", content.getRegion())
                .set("language", content.getLanguage())
                .set("release_date", content.getReleaseDate())
                .set("duration", content.getDuration())
                .set("total_episode", content.getTotalEpisode())
                .set("storyline", content.getStoryline())
                .set("score_douban", content.getScoreDouban())
                .set("score_imdb", content.getScoreImdb())
                .set("status", content.getStatus()));
    }

    private static void requireSaved(boolean saved) {
        if (!saved) throw new IllegalStateException("内容创建失败");
    }

    private static void requireUpdated(boolean updated) {
        if (!updated) throw new IllegalArgumentException("内容不存在或更新失败");
    }

    private static <T> T requireContent(T content) {
        if (content == null) throw new IllegalArgumentException("内容不存在");
        return content;
    }

    private record GenreSelection(Integer status, List<Long> tagIds, String projection) {
    }
}
