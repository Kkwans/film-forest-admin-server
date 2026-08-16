package com.filmforest.crawler.core;

import com.filmforest.crawler.model.ParsedResource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResourceNormalizer {

    private static final Pattern INFO_HASH = Pattern.compile(
            "(?i)(?:[?&])xt=urn:btih:([a-z0-9]+)(?:&|$)");
    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "pwd", "pw", "pass", "password", "passwd", "passcode", "code",
            "accesscode", "access_code", "accesskey", "access_key",
            "accesspassword", "access_password", "extractioncode", "extraction_code",
            "sharecode", "share_code", "sharepassword", "share_password",
            "提取码", "提取碼", "密码", "密碼", "访问码", "訪問碼",
            "访问密码", "訪問密碼", "分享码", "分享碼", "分享密码", "分享密碼");
    private static final Set<String> KNOWN_DISK_TYPES = Set.of(
            "baidu", "quark", "lanzou", "xunlei", "uc", "ali", "123", "other");

    public Map<ParsedResource.Kind, List<NormalizedResource>> normalizeAll(
            String sourceCode, List<ParsedResource> resources) {
        String normalizedSource = requireText(sourceCode, "sourceCode").toLowerCase(Locale.ROOT);
        Map<ParsedResource.Kind, LinkedHashMap<String, NormalizedResource>> byKind =
                new EnumMap<>(ParsedResource.Kind.class);
        Map<String, String> keyMaterials = new LinkedHashMap<>();
        for (ParsedResource.Kind kind : ParsedResource.Kind.values()) {
            byKind.put(kind, new LinkedHashMap<>());
        }
        for (ParsedResource resource : resources == null ? List.<ParsedResource>of() : resources) {
            if (resource == null || resource.kind() == null) {
                throw new IllegalArgumentException("Parsed resource kind must not be null");
            }
            NormalizedResource normalized = normalize(normalizedSource, resource);
            String previousMaterial = keyMaterials.putIfAbsent(normalized.resourceKey(),
                    normalized.keyMaterial());
            if (previousMaterial != null && !previousMaterial.equals(normalized.keyMaterial())) {
                throw new ResourceNormalizationCollisionException(normalized.resourceKey());
            }
            byKind.get(resource.kind()).merge(normalized.resourceKey(), normalized,
                    ResourceNormalizer::preferEarlierSourceOrder);
        }

        Map<ParsedResource.Kind, List<NormalizedResource>> result =
                new EnumMap<>(ParsedResource.Kind.class);
        byKind.forEach((kind, values) -> result.put(kind, values.values().stream()
                .sorted(Comparator.comparingInt(value -> value.resource().sourceOrder()))
                .toList()));
        return Map.copyOf(result);
    }

    public NormalizedResource normalize(String sourceCode, ParsedResource resource) {
        String normalizedSource = requireText(sourceCode, "sourceCode").toLowerCase(Locale.ROOT);
        String url = requireText(resource.url(), "resource.url");
        String material;
        String normalizedUrl;
        switch (resource.kind()) {
            case MAGNET -> {
                normalizedUrl = normalizeMagnet(url);
                Matcher matcher = INFO_HASH.matcher('?' + queryPart(url));
                String identity = matcher.find()
                        ? matcher.group(1).toLowerCase(Locale.ROOT) : normalizedUrl;
                material = "magnet\u0000" + identity;
            }
            case CLOUD -> {
                String diskType = normalizeDiskType(resource.diskType(), url);
                resource = new ParsedResource(resource.kind(), resource.title(), resource.url(),
                        diskType, resource.password(), resource.resolution(), resource.hasSubtitle(),
                        resource.specialSubtitle(), resource.season(), resource.episodeNumber(),
                        resource.episodeTitle(), resource.sourceOrder(), resource.rawText(),
                        resource.sourcePageUrl(), resource.playbackType());
                normalizedUrl = normalizeUrl(url, true);
                material = "cloud\u0000" + diskType + '\u0000' + normalizedUrl;
            }
            case ONLINE -> {
                normalizedUrl = normalizeUrl(url, false);
                String stableUrl = resource.sourcePageUrl() == null || resource.sourcePageUrl().isBlank()
                        ? normalizedUrl : normalizeUrl(resource.sourcePageUrl(), false);
                material = "online\u0000" + normalizedSource + '\u0000'
                        + nullableNumber(resource.season()) + '\u0000'
                        + nullableNumber(resource.episodeNumber()) + '\u0000' + stableUrl;
            }
            default -> throw new IllegalArgumentException("Unsupported resource kind: " + resource.kind());
        }
        return new NormalizedResource(resource, sha256(material), material, normalizedUrl);
    }

    static NormalizedResource preferEarlierSourceOrder(NormalizedResource first,
                                                       NormalizedResource second) {
        return first.resource().sourceOrder() <= second.resource().sourceOrder() ? first : second;
    }

    private static String normalizeMagnet(String url) {
        String trimmed = url.trim();
        if (!trimmed.regionMatches(true, 0, "magnet:?", 0, 8)) {
            throw new IllegalArgumentException("Invalid magnet URL");
        }
        List<String> parameters = sortedQuery(queryPart(trimmed), false);
        return "magnet:?" + String.join("&", parameters);
    }

    private static String normalizeUrl(String rawUrl, boolean removeCredentialParameters) {
        String trimmed = rawUrl.trim();
        try {
            URI parsed = new URI(trimmed).normalize();
            String scheme = parsed.getScheme() == null ? null
                    : parsed.getScheme().toLowerCase(Locale.ROOT);
            String host = parsed.getHost() == null ? null
                    : parsed.getHost().toLowerCase(Locale.ROOT);
            if (scheme == null || host == null) {
                throw new IllegalArgumentException("Resource URL must be absolute");
            }
            int port = parsed.getPort();
            if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
                port = -1;
            }
            String path = parsed.getRawPath();
            if (path == null || path.isBlank()) path = "/";
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            List<String> query = sortedQuery(parsed.getRawQuery(), removeCredentialParameters);
            return new URI(scheme, null, host, port, path,
                    query.isEmpty() ? null : String.join("&", query), null).toASCIIString();
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Invalid resource URL", invalid);
        }
    }

    private static List<String> sortedQuery(String rawQuery, boolean removeCredentialParameters) {
        if (rawQuery == null || rawQuery.isBlank()) return List.of();
        List<String> values = new ArrayList<>();
        for (String parameter : rawQuery.split("&")) {
            if (parameter.isBlank()) continue;
            String rawKey = parameter.split("=", 2)[0];
            String key = decodeQueryPart(rawKey).toLowerCase(Locale.ROOT);
            if (key.startsWith("utm_") || "spm".equals(key)) continue;
            if (removeCredentialParameters && SENSITIVE_QUERY_KEYS.contains(key)) continue;
            values.add(parameter);
        }
        values.sort(String::compareTo);
        return List.copyOf(values);
    }

    private static String queryPart(String url) {
        int question = url.indexOf('?');
        if (question < 0 || question == url.length() - 1) return "";
        int fragment = url.indexOf('#', question);
        return fragment < 0 ? url.substring(question + 1) : url.substring(question + 1, fragment);
    }

    private static String normalizeDiskType(String diskType, String url) {
        if (diskType != null && !diskType.isBlank()) {
            String normalized = diskType.trim().toLowerCase(Locale.ROOT);
            return KNOWN_DISK_TYPES.contains(normalized) ? normalized : "other";
        }
        String value = url.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("thunder:")) return "xunlei";
        String host = host(value);
        if (isDomain(host, "baidu.com")) return "baidu";
        if (isDomain(host, "quark.cn")) return "quark";
        if (isLanzouHost(host)) return "lanzou";
        if (isDomain(host, "xunlei.com")) return "xunlei";
        if (isDomain(host, "uc.cn")) return "uc";
        if (isDomain(host, "alipan.com") || isDomain(host, "aliyundrive.com")
                || isDomain(host, "ali.com")) return "ali";
        if (isDomain(host, "123pan.com") || isDomain(host, "123.com")) return "123";
        return "other";
    }

    private static String host(String value) {
        try {
            return URI.create(value).getHost();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean isDomain(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }

    private static boolean isLanzouHost(String host) {
        return List.of("lanzou.com", "lanzouk.com", "lanzoui.com", "lanzouv.com",
                        "lanzoux.com", "lanzouj.com", "lanzoum.com")
                .stream().anyMatch(domain -> isDomain(host, domain));
    }

    private static String decodeQueryPart(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidEncoding) {
            return value;
        }
    }

    private static String nullableNumber(Integer number) {
        return number == null ? "?" : number.toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record NormalizedResource(ParsedResource resource, String resourceKey,
                                     String keyMaterial, String normalizedUrl) {
    }

    public static final class ResourceNormalizationCollisionException extends RuntimeException {
        public ResourceNormalizationCollisionException(String resourceKey) {
            super("Normalized resource key collision: " + resourceKey);
        }
    }
}
