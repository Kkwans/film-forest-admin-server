package com.filmforest.poster;

import com.filmforest.common.type.ContentType;
import com.filmforest.content.config.PosterStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 下载并原子写入爬虫海报。失败是可重试的，不会覆盖已有本地海报。 */
@Slf4j
@Service
public class PosterBackupService {

    private static final String LOCAL_PREFIX = "/api/poster/assets/";
    private static final int MAX_REDIRECTS = 3;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Map<ContentType, String> TABLES = Map.of(
            ContentType.MOVIE, "movie",
            ContentType.DRAMA, "drama",
            ContentType.VARIETY, "variety",
            ContentType.ANIME, "anime",
            ContentType.SHORT_DRAMA, "short_drama"
    );

    private final JdbcTemplate jdbc;
    private final PosterStorageProperties storage;
    private final PosterBackupProperties properties;
    private final HttpClient httpClient;
    private final PosterBackupRateLimiter rateLimiter;

    public PosterBackupService(JdbcTemplate jdbc, PosterStorageProperties storage,
                               PosterBackupProperties properties) {
        this(jdbc, storage, properties, createHttpClient(properties),
                new PosterBackupRateLimiter(properties));
    }

    PosterBackupService(JdbcTemplate jdbc, PosterStorageProperties storage,
                        PosterBackupProperties properties, HttpClient httpClient) {
        this(jdbc, storage, properties, httpClient, new PosterBackupRateLimiter(properties));
    }

    @Autowired
    public PosterBackupService(JdbcTemplate jdbc, PosterStorageProperties storage,
                        PosterBackupProperties properties, PosterBackupRateLimiter rateLimiter) {
        this(jdbc, storage, properties, createHttpClient(properties), rateLimiter);
    }

    PosterBackupService(JdbcTemplate jdbc, PosterStorageProperties storage,
                        PosterBackupProperties properties, HttpClient httpClient,
                        PosterBackupRateLimiter rateLimiter) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
        this.httpClient = httpClient;
        this.rateLimiter = rateLimiter;
    }

    public boolean backup(ContentType contentType, long contentId, String sourceUrl) {
        if (contentId <= 0 || !TABLES.containsKey(contentType)) return false;
        URI initial = safeUri(sourceUrl);
        if (initial == null) return false;

        Path directory = storage.root().resolve(contentType.value()).normalize();
        Path temporary = null;
        boolean moved = false;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".poster-", ".part");
            DownloadedImage image = download(initial, temporary);
            if (image == null) return false;

            String fileName = contentId + "-" + image.digest().substring(0, 16)
                    + "." + image.extension();
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(storage.root())) return false;
            if (!Files.exists(target)) {
                moveAtomically(image.temporary(), target);
                moved = true;
            }

            String localUrl = LOCAL_PREFIX + contentType.value() + "/" + fileName;
            String table = TABLES.get(contentType);
            int updated = jdbc.update(
                    "UPDATE " + table
                            + " SET poster_url = ?, poster_backup_source_url = ?"
                            + " WHERE id = ? AND poster_source_url = ?"
                            + " AND (poster_url IS NULL OR poster_url NOT LIKE ?"
                            + " OR poster_backup_source_url IS NULL OR poster_backup_source_url <> poster_source_url)",
                    localUrl, sourceUrl, contentId, sourceUrl, LOCAL_PREFIX + "%");
            return updated > 0;
        } catch (IOException error) {
            log.warn("海报本地写入失败: type={}, id={}, reason={}", contentType.value(), contentId,
                    error.getClass().getSimpleName());
            return false;
        } finally {
            if (temporary != null && !moved) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupError) {
                    log.debug("海报临时文件清理失败: type={}, id={}, reason={}",
                            contentType.value(), contentId, cleanupError.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * 流式写入临时文件并同步计算摘要，不把整张图片读入 JVM 内存。
     * 用户明确要求不限制图片大小，因此这里不检查 Content-Length，也不设置字节上限。
     */
    private DownloadedImage download(URI initial, Path temporary) {
        URI current = initial;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .GET()
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8")
                    .header("User-Agent", properties.getUserAgent())
                    .build();
            try {
                rateLimiter.acquire();
                HttpResponse<InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() >= 300 && response.statusCode() < 400) {
                        if (redirect == MAX_REDIRECTS) return null;
                        String location = response.headers().firstValue("location").orElse(null);
                        current = location == null ? null : safeUri(current.resolve(location));
                        if (current == null) return null;
                        continue;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
                    byte[] header = body.readNBytes(32);
                    if (header.length == 0) return null;
                    ImageType type = ImageType.detect(header);
                    if (type == null) return null;

                    MessageDigest digest = sha256Digest();
                    try (OutputStream output = Files.newOutputStream(temporary,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                        output.write(header);
                        digest.update(header);
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = body.read(buffer)) != -1) {
                            if (read == 0) continue;
                            output.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                        }
                    }
                    return new DownloadedImage(temporary, type.extension(),
                            HexFormat.of().formatHex(digest.digest()));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException | RuntimeException error) {
                return null;
            }
        }
        return null;
    }

    private URI safeUri(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return safeUri(URI.create(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private URI safeUri(URI uri) {
        if (uri == null || uri.getScheme() == null
                || !ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
                || uri.getHost() == null || uri.getUserInfo() != null) return null;
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) return null;
            }
        } catch (IOException error) {
            return null;
        }
        return uri;
    }

    private static HttpClient createHttpClient(PosterBackupProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 不可用", impossible);
        }
    }

    private record DownloadedImage(Path temporary, String extension, String digest) {}

    private enum ImageType {
        JPEG("jpg"), PNG("png"), GIF("gif"), WEBP("webp"), AVIF("avif");

        private final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }

        String extension() {
            return extension;
        }

        static ImageType detect(byte[] bytes) {
            if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return JPEG;
            if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})) return PNG;
            if (startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII))) return GIF;
            if (bytes.length >= 12 && startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && startsWithAt(bytes, "WEBP".getBytes(StandardCharsets.US_ASCII), 8)) return WEBP;
            if (bytes.length >= 12 && startsWithAt(bytes, "ftyp".getBytes(StandardCharsets.US_ASCII), 4)
                    && (startsWithAt(bytes, "avif".getBytes(StandardCharsets.US_ASCII), 8)
                    || startsWithAt(bytes, "avis".getBytes(StandardCharsets.US_ASCII), 8))) return AVIF;
            return null;
        }

        private static boolean startsWith(byte[] value, byte[] prefix) {
            return startsWithAt(value, prefix, 0);
        }

        private static boolean startsWithAt(byte[] value, byte[] prefix, int offset) {
            if (value.length < offset + prefix.length) return false;
            for (int index = 0; index < prefix.length; index++) {
                if (value[offset + index] != prefix[index]) return false;
            }
            return true;
        }
    }
}
