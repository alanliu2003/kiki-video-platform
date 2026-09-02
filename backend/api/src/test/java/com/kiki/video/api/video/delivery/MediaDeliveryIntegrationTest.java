package com.kiki.video.api.video.delivery;

import com.kiki.video.api.support.AbstractIntegrationTest;
import com.kiki.video.api.support.MockMvcStreaming;
import com.kiki.video.api.upload.UploadMath;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.api.video.storage.VideoStorage;
import com.kiki.video.common.media.ProcessedObjectKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.media-delivery.mode=presigned",
        "app.media-delivery.url-ttl=15m"
})
@AutoConfigureMockMvc
class MediaDeliveryIntegrationTest extends AbstractIntegrationTest {

    private static final int CHUNK_SIZE = 256 * 1024;

    @DynamicPropertySource
    static void presignedMode(DynamicPropertyRegistry registry) {
        registry.add("app.media-delivery.mode", () -> "presigned");
        registry.add("app.media-delivery.url-ttl", () -> "15m");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaObjectMapper mediaObjectMapper;

    @Autowired
    private VideoStorage videoStorage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MediaDeliveryService mediaDeliveryService;

    @Test
    void playbackDescriptorUsesPresignedContentAndKeepsPlaylistOnApi() throws Exception {
        byte[] file = uniqueFixture(CHUNK_SIZE, 31);
        long videoId = uploadFile(registerAndLogin(unique("deliv")), file, "Delivery video");
        MediaObject media = mediaObjectMapper.findBySha256(sha256(file));
        Long videoMediaId = jdbcTemplate.queryForObject(
                "SELECT media_object_id FROM videos WHERE id = ?", Long.class, videoId);
        assertThat(videoMediaId).isEqualTo(media.getId());
        assertThat(mediaDeliveryService.mode().isPresigned()).isTrue();
        markReady(media);
        putObject(ProcessedObjectKeys.master(media.getId()),
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\n360p/index.m3u8\n".getBytes(),
                "application/vnd.apple.mpegurl");
        putObject(ProcessedObjectKeys.renditionPlaylist(media.getId(), "360p"),
                "#EXTM3U\n#EXTINF:6,\nsegment000.ts\n".getBytes(),
                "application/vnd.apple.mpegurl");
        putObject(ProcessedObjectKeys.prefix(media.getId()) + "360p/segment000.ts",
                new byte[] {9, 8, 7, 6},
                "video/mp2t");
        putObject(ProcessedObjectKeys.thumbnail(media.getId()),
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                "image/jpeg");

        String playlistKey = ProcessedObjectKeys.renditionPlaylist(media.getId(), "360p");
        assertThat(mediaDeliveryService.rewritePlaylistIfNeeded(media.getId(), "360p/index.m3u8", playlistKey))
                .contains("X-Amz-");

        MvcResult playback = mockMvc.perform(get("/api/videos/" + videoId + "/playback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("HLS"))
                .andExpect(jsonPath("$.processingStatus").value("READY"))
                .andExpect(jsonPath("$.deliveryMode").value("presigned"))
                .andExpect(jsonPath("$.url").value("/api/videos/" + videoId + "/hls/master.m3u8"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn();
        JsonNode body = objectMapper.readTree(playback.getResponse().getContentAsString());
        assertThat(body.get("contentUrl").asString()).contains("X-Amz-");
        assertThat(body.get("thumbnailUrl").asString()).contains("X-Amz-");
        assertThat(body.get("contentUrl").asString()).doesNotContain("MINIO_ROOT_PASSWORD");
        assertThat(body.get("contentUrl").asString()).doesNotContain("secret-key");

        String variant = hlsPlaylistBody(videoId, "360p/index.m3u8");
        assertThat(variant).contains("X-Amz-");
        assertThat(variant).doesNotContain("\nsegment000.ts\n");

        String segmentUrl = variant.lines()
                .filter(line -> line.startsWith("http"))
                .findFirst()
                .orElseThrow();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<byte[]> signed = client.send(
                HttpRequest.newBuilder(URI.create(segmentUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(signed.statusCode()).isEqualTo(200);
        assertThat(signed.body()).isEqualTo(new byte[] {9, 8, 7, 6});

        String unsigned = segmentUrl.replaceAll("\\?.*", "");
        HttpResponse<byte[]> rejected = client.send(
                HttpRequest.newBuilder(URI.create(unsigned)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(rejected.statusCode()).isGreaterThanOrEqualTo(400);

        HttpResponse<byte[]> ranged = client.send(
                HttpRequest.newBuilder(URI.create(body.get("contentUrl").asString()))
                        .header("Range", "bytes=0-15")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(ranged.statusCode()).isIn(200, 206);
        assertThat(ranged.body().length).isLessThanOrEqualTo(16);

        mockMvc.perform(get("/api/media/presign").param("key", "raw/anything"))
                .andExpect(status().isUnauthorized());
        assertThat(mediaDeliveryService.urlTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void legacyPlaybackUrlIsPresignedAndProxyContentStillWorks() throws Exception {
        byte[] file = uniqueFixture(CHUNK_SIZE + 8, 32);
        long videoId = uploadFile(registerAndLogin(unique("legacyD")), file, "Legacy delivery");
        MediaObject media = mediaObjectMapper.findBySha256(sha256(file));
        jdbcTemplate.update("UPDATE media_objects SET processing_status = 'NOT_REQUESTED' WHERE id = ?", media.getId());
        MvcResult playback = mockMvc.perform(get("/api/videos/" + videoId + "/playback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LEGACY"))
                .andExpect(jsonPath("$.type").value("ORIGINAL"))
                .andReturn();
        String url = objectMapper.readTree(playback.getResponse().getContentAsString()).get("url").asString();
        assertThat(url).contains("X-Amz-");

        mockMvc.perform(get("/api/videos/" + videoId + "/content")
                        .header(HttpHeaders.RANGE, "bytes=0-15"))
                .andExpect(status().isPartialContent());
    }

    private String hlsPlaylistBody(long videoId, String assetPath) throws Exception {
        MvcResult result = MockMvcStreaming.awaitStreamingResponse(
                        mockMvc, get("/api/videos/" + videoId + "/hls/" + assetPath))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private void putObject(String key, byte[] bytes, String contentType) {
        videoStorage.put(key, new java.io.ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    private void markReady(MediaObject media) {
        jdbcTemplate.update("""
                UPDATE media_objects
                SET processing_status = 'READY',
                    processed_prefix = ?,
                    master_playlist_key = ?,
                    thumbnail_key = ?,
                    updated_at = NOW(),
                    processed_at = NOW()
                WHERE id = ?
                """,
                ProcessedObjectKeys.prefix(media.getId()),
                ProcessedObjectKeys.master(media.getId()),
                ProcessedObjectKeys.thumbnail(media.getId()),
                media.getId());
    }

    private long uploadFile(String token, byte[] file, String title) throws Exception {
        JsonNode init = objectMapper.readTree(mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"demo.mp4","fileSizeBytes":%d,"contentType":"video/mp4","fileSha256":"%s"}
                                """.formatted(file.length, sha256(file)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        int total = UploadMath.totalChunks(file.length, CHUNK_SIZE);
        for (int i = 0; i < total; i++) {
            int start = i * CHUNK_SIZE;
            byte[] part = java.util.Arrays.copyOfRange(file, start, Math.min(start + CHUNK_SIZE, file.length));
            mockMvc.perform(put("/api/uploads/" + init.get("uploadId").asString() + "/chunks/" + i)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(part)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNoContent());
        }
        MvcResult complete = mockMvc.perform(post("/api/uploads/" + init.get("uploadId").asString() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(complete.getResponse().getContentAsString()).at("/video/id").asLong();
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"StrongPassword123"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"StrongPassword123"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private static String unique(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] uniqueFixture(int size, int seed) {
        byte[] bytes = fixture(size, seed);
        long stamp = System.nanoTime();
        for (int i = 0; i < 8 && bytes.length - 1 - i > 7; i++) {
            bytes[bytes.length - 1 - i] = (byte) (stamp >>> (8 * i));
        }
        return bytes;
    }

    private static byte[] fixture(int size, int seed) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) seed;
        if (size > 7) {
            bytes[4] = 'f';
            bytes[5] = 't';
            bytes[6] = 'y';
            bytes[7] = 'p';
        }
        for (int i = 8; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 31) + seed);
        }
        return bytes;
    }
}
