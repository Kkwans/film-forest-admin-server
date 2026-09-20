package com.filmforest.poster;

import com.filmforest.common.type.ContentType;
import com.filmforest.content.config.PosterStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PosterBackupServiceTest {

    @Test
    void streamsAnImageLargerThanTheFormerLimitToDisk(@TempDir Path tempDir) throws Exception {
        byte[] image = new byte[8 * 1024 * 1024 + 1];
        image[0] = (byte) 0x89;
        image[1] = 'P';
        image[2] = 'N';
        image[3] = 'G';
        image[4] = '\r';
        image[5] = '\n';
        image[6] = 0x1a;
        image[7] = '\n';

        PosterBackupProperties properties = new PosterBackupProperties();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(image));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        PosterBackupService service = new PosterBackupService(
                jdbc,
                new PosterStorageProperties(tempDir.toString()),
                properties,
                httpClient,
                new PosterBackupRateLimiter(properties));

        assertThat(service.backup(ContentType.MOVIE, 7, "https://192.0.2.1/poster.png").success()).isTrue();

        try (Stream<Path> files = Files.list(tempDir.resolve("movie"))) {
            Path stored = files.findFirst().orElseThrow();
            assertThat(stored.getFileName().toString()).matches("7-[0-9a-f]{16}\\.png");
            assertThat(Files.size(stored)).isEqualTo(image.length);
        }
    }

    @Test
    void classifiesNotFoundAsTerminalFailure(@TempDir Path tempDir) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn(new ByteArrayInputStream("not found".getBytes()));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        PosterBackupService.BackupResult result = service(tempDir, httpClient)
                .backup(ContentType.MOVIE, 7, "https://192.0.2.1/missing.jpg");

        assertThat(result.success()).isFalse();
        assertThat(result.terminal()).isTrue();
        assertThat(result.failureCode()).isEqualTo("HTTP_404");
        assertThat(result.httpStatus()).isEqualTo(404);
    }

    @Test
    void rejectsHtmlDisguisedAsImageAsTerminalFailure(@TempDir Path tempDir) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(
                "<html><title>404 Not Found</title></html>".getBytes()));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        PosterBackupService.BackupResult result = service(tempDir, httpClient)
                .backup(ContentType.MOVIE, 7, "https://192.0.2.1/fake.jpg");

        assertThat(result.success()).isFalse();
        assertThat(result.terminal()).isTrue();
        assertThat(result.failureCode()).isEqualTo("INVALID_IMAGE");
    }

    private PosterBackupService service(Path tempDir, HttpClient httpClient) {
        PosterBackupProperties properties = new PosterBackupProperties();
        return new PosterBackupService(
                mock(JdbcTemplate.class),
                new PosterStorageProperties(tempDir.toString()),
                properties,
                httpClient,
                new PosterBackupRateLimiter(properties));
    }
}
