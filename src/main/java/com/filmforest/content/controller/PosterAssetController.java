package com.filmforest.content.controller;

import com.filmforest.content.config.PosterStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 管理端也提供本地海报，保证内容列表和爬虫详情使用同一份备份。 */
@RestController
@RequestMapping("/api/poster/assets")
public class PosterAssetController {

    private static final Set<String> CONTENT_TYPES = Set.of("movie", "drama", "variety", "anime", "short_drama");
    private static final Pattern FILE_NAME = Pattern.compile("\\d+-[0-9a-f]{16}\\.(?:jpg|jpeg|png|gif|webp|avif)");
    private static final Map<String, MediaType> MEDIA_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"),
            "avif", MediaType.parseMediaType("image/avif")
    );

    private final PosterStorageProperties properties;

    public PosterAssetController(PosterStorageProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/{contentType}/{fileName:.+}")
    public ResponseEntity<Resource> get(@PathVariable String contentType,
                                        @PathVariable String fileName) throws IOException {
        if (!CONTENT_TYPES.contains(contentType) || !FILE_NAME.matcher(fileName).matches()) {
            return ResponseEntity.notFound().build();
        }
        Path file = properties.root().resolve(contentType).resolve(fileName).normalize();
        if (!file.startsWith(properties.root()) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MEDIA_TYPES.get(extension));
        headers.setContentLength(Files.size(file));
        headers.setCacheControl("public, max-age=31536000, immutable");
        return new ResponseEntity<>(new FileSystemResource(file), headers, HttpStatus.OK);
    }
}
