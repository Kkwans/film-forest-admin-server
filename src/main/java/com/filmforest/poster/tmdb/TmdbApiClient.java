package com.filmforest.poster.tmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.crawler.http.FetchResult;
import com.filmforest.crawler.http.HttpFetcher;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TmdbApiClient implements TmdbGateway {

    private static final URI API_BASE = URI.create("https://api.themoviedb.org/3/");
    private static final Set<String> SENSITIVE_QUERY_PARAMETERS = Set.of("api_key");

    private final HttpFetcher httpFetcher;
    private final ObjectMapper objectMapper;

    public TmdbApiClient(HttpFetcher httpFetcher, ObjectMapper objectMapper) {
        this.httpFetcher = httpFetcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TmdbSearchCandidate> search(TmdbMediaType mediaType, String query, Integer year,
                                            TmdbCredential credential) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("query", query);
        parameters.put("include_adult", "false");
        parameters.put("language", "zh-CN");
        parameters.put("page", "1");
        if (year != null) {
            parameters.put(mediaType == TmdbMediaType.MOVIE
                    ? "primary_release_year" : "first_air_date_year", year.toString());
        }
        JsonNode root = request("search/" + mediaType.apiValue(), parameters, credential);
        List<TmdbSearchCandidate> candidates = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            String title = text(item, mediaType == TmdbMediaType.MOVIE ? "title" : "name");
            String originalTitle = text(item,
                    mediaType == TmdbMediaType.MOVIE ? "original_title" : "original_name");
            String date = text(item,
                    mediaType == TmdbMediaType.MOVIE ? "release_date" : "first_air_date");
            candidates.add(new TmdbSearchCandidate(item.path("id").asLong(), mediaType, title,
                    originalTitle, year(date), text(item, "poster_path"),
                    text(item, "original_language"), number(item, "vote_average"),
                    integer(item, "vote_count")));
        }
        return List.copyOf(candidates);
    }

    @Override
    public List<TmdbPosterAsset> posters(TmdbMediaType mediaType, long tmdbId,
                                         TmdbCredential credential) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("language", "zh-CN");
        parameters.put("include_image_language", "zh,en,null");
        JsonNode root = request(mediaType.apiValue() + "/" + tmdbId + "/images",
                parameters, credential);
        List<TmdbPosterAsset> posters = new ArrayList<>();
        for (JsonNode item : root.path("posters")) {
            posters.add(new TmdbPosterAsset(text(item, "file_path"),
                    text(item, "iso_639_1"), item.path("vote_average").asDouble(),
                    item.path("vote_count").asInt(), item.path("width").asInt(),
                    item.path("height").asInt()));
        }
        return List.copyOf(posters);
    }

    @Override
    public TmdbImageConfiguration configuration(TmdbCredential credential) {
        JsonNode images = request("configuration", Map.of(), credential).path("images");
        List<String> sizes = new ArrayList<>();
        images.path("poster_sizes").forEach(node -> sizes.add(node.asText()));
        return new TmdbImageConfiguration(images.path("secure_base_url").asText(), List.copyOf(sizes));
    }

    private JsonNode request(String path, Map<String, String> parameters,
                             TmdbCredential credential) {
        Map<String, String> query = new LinkedHashMap<>(parameters);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (credential.type() == TmdbCredential.Type.API_KEY) {
            query.put("api_key", credential.value());
        } else {
            headers.put("Authorization", "Bearer " + credential.value());
        }
        URI uri = uri(path, query);
        FetchResult result = httpFetcher.fetch(uri, Map.copyOf(headers), 0,
                new AtomicBoolean(false), SENSITIVE_QUERY_PARAMETERS);
        if (!result.successful()) {
            throw new TmdbApiException(result.category(), result.statusCode());
        }
        try {
            return objectMapper.readTree(result.body());
        } catch (Exception invalidResponse) {
            throw new IllegalArgumentException("TMDB returned invalid JSON", invalidResponse);
        }
    }

    private static URI uri(String path, Map<String, String> parameters) {
        StringBuilder value = new StringBuilder(API_BASE.resolve(path).toString());
        if (!parameters.isEmpty()) value.append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!first) value.append('&');
            first = false;
            value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return URI.create(value.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.asDouble();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isIntegralNumber() ? null : value.asInt();
    }

    private static Integer year(String date) {
        if (date == null || date.length() < 4) return null;
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
