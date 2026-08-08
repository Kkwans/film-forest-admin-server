package com.filmforest.poster.tmdb;

import java.util.List;

public interface TmdbGateway {

    List<TmdbSearchCandidate> search(TmdbMediaType mediaType, String query, Integer year,
                                     TmdbCredential credential);

    List<TmdbPosterAsset> posters(TmdbMediaType mediaType, long tmdbId, TmdbCredential credential);

    TmdbImageConfiguration configuration(TmdbCredential credential);
}
