package com.filmforest.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentMutationServiceTest {

    @Mock private MovieService movieService;
    @Mock private DramaService dramaService;
    @Mock private VarietyService varietyService;
    @Mock private AnimeService animeService;
    @Mock private ShortDramaService shortDramaService;
    @Mock private TagService tagService;

    private AdminContentMutationService service;

    @BeforeEach
    void setUp() {
        service = new AdminContentMutationService(movieService, dramaService, varietyService,
                animeService, shortDramaService, tagService, new ObjectMapper());
    }

    @Test
    void createsDraftAndProjectsOnlyValidatedStandardGenres() {
        Movie movie = new Movie();
        movie.setTitle("测试电影");
        movie.setGenreTagIds(List.of(7L, 3L));
        Tag drama = genre(3L, "剧情");
        Tag scienceFiction = genre(7L, "科幻");
        when(tagService.requireStandardGenres("movie", List.of(7L, 3L)))
                .thenReturn(List.of(drama, scienceFiction));
        when(movieService.save(movie)).thenAnswer(invocation -> {
            movie.setId(42L);
            return true;
        });

        Movie created = service.createMovie(movie);

        assertThat(created.getStatus()).isZero();
        assertThat(created.getGenre()).isEqualTo("[\"剧情\",\"科幻\"]");
        assertThat(created.getGenreTagIds()).containsExactly(3L, 7L);
        verify(tagService).setContentGenres(42L, "movie", List.of(3L, 7L));
    }

    @Test
    void rejectsFreeTextGenreBeforeWritingContent() {
        Movie movie = new Movie();
        movie.setTitle("测试电影");
        movie.setGenre("[\"剧情\"]");

        assertThatThrownBy(() -> service.createMovie(movie))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("genreTagIds");
        verify(movieService, never()).save(any());
    }

    @Test
    void updateCanExplicitlyClearStandardGenres() {
        Movie request = new Movie();
        request.setTitle("更新后");
        request.setStatus(2);
        request.setGenreTagIds(List.of());
        Movie stored = new Movie();
        stored.setId(8L);
        stored.setTitle("更新后");
        stored.setStatus(2);
        stored.setGenre("[]");
        when(tagService.requireStandardGenres("movie", List.of())).thenReturn(List.of());
        when(movieService.updateById(request)).thenReturn(true);
        when(movieService.getDetail(8L)).thenReturn(stored);
        when(tagService.getContentTags(8L, "movie")).thenReturn(List.of());

        Movie updated = service.updateMovie(8L, request);

        assertThat(request.getGenre()).isEqualTo("[]");
        assertThat(updated.getGenreTagIds()).isEmpty();
        verify(tagService).setContentGenres(8L, "movie", List.of());
    }

    @Test
    void rejectsUnknownContentStatus() {
        Movie movie = new Movie();
        movie.setTitle("测试电影");
        movie.setStatus(9);
        movie.setGenreTagIds(List.of());

        assertThatThrownBy(() -> service.createMovie(movie))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0、1 或 2");
        verify(movieService, never()).save(any());
    }

    private static Tag genre(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSystem(1);
        return tag;
    }
}
