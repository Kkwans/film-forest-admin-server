package com.filmforest.poster.tmdb;

import java.util.List;

public record TmdbImageConfiguration(
        String secureBaseUrl,
        List<String> posterSizes
) {
    public String preferredPosterSize() {
        if (posterSizes.contains("w500")) return "w500";
        if (posterSizes.contains("w342")) return "w342";
        return posterSizes.contains("original") ? "original"
                : posterSizes.stream().findFirst().orElse("original");
    }

    public String imageUrl(String posterPath) {
        if (posterPath == null || posterPath.isBlank()) return null;
        String base = secureBaseUrl.endsWith("/") ? secureBaseUrl : secureBaseUrl + "/";
        String path = posterPath.startsWith("/") ? posterPath.substring(1) : posterPath;
        return base + preferredPosterSize() + "/" + path;
    }
}
