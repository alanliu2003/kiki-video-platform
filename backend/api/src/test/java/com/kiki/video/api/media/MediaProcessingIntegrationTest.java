package com.kiki.video.api.media;

import com.kiki.video.api.media.mapper.MediaProcessingOutboxMapper;
import com.kiki.video.api.media.model.MediaProcessingOutbox;
import com.kiki.video.api.media.model.OutboxStatus;
import com.kiki.video.api.support.AbstractIntegrationTest;
import com.kiki.video.api.upload.UploadMath;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.common.media.MediaProcessingStatus;
import com.kiki.video.common.media.ProcessedObjectKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaProcessingIntegrationTest extends AbstractIntegrationTest {

    private static final int CHUNK_SIZE = 256 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaObjectMapper mediaObjectMapper;

    @Autowired
    private MediaProcessingOutboxMapper outboxMapper;

    @Autowired
    private MediaProcessingRequestService requestService;

    @Test
    void newUploadCompletionCreatesPendingProcessingRequest() throws Exception {
        byte[] file = fixture(CHUNK_SIZE + 40, 21);
        long videoId = uploadFile(registerAndLogin(unique("proc")), file, "Processing video");

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("PENDING"));
        mockMvc.perform(get("/api/videos/" + videoId + "/playback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("NONE"));

        MediaObject media = mediaObjectMapper.findBySha256(sha256(file));
        assertThat(media.getProcessingStatus()).isEqualTo(MediaProcessingStatus.PENDING);
        MediaProcessingOutbox outbox = outboxMapper.findActiveByMediaObjectId(media.getId());
        assertThat(outbox).isNotNull();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getEventType()).isEqualTo("MEDIA_PROCESSING_REQUESTED");
        assertThat(outbox.getPayload()).contains(media.getSha256());
        assertThat(outbox.getPayload()).doesNotContain("minioadmin");
    }

    @Test
    void readyMediaDoesNotCreateDuplicateProcessingJob() throws Exception {
        byte[] file = fixture(CHUNK_SIZE + 80, 22);
        String hash = sha256(file);
        String alice = registerAndLogin(unique("readyA"));
        uploadFile(alice, file, "First");

        MediaObject media = mediaObjectMapper.findBySha256(hash);
        markReady(media);
        assertThat(outboxMapper.findActiveByMediaObjectId(media.getId())).isNotNull();
        long firstOutboxId = outboxMapper.findLatestByMediaObjectId(media.getId()).getId();

        String bob = registerAndLogin(unique("readyB"));
        long bobVideo = uploadDeduped(bob, file, "Second");
        MediaObject after = mediaObjectMapper.findBySha256(hash);
        assertThat(after.getId()).isEqualTo(media.getId());
        assertThat(after.getProcessingStatus()).isEqualTo(MediaProcessingStatus.READY);
        assertThat(outboxMapper.findLatestByMediaObjectId(media.getId()).getId()).isEqualTo(firstOutboxId);

        mockMvc.perform(get("/api/videos/" + bobVideo + "/playback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.type").value("HLS"))
                .andExpect(jsonPath("$.manifestUrl").value("/api/videos/" + bobVideo + "/hls/master.m3u8"));
    }

    @Test
    void pendingMediaDoesNotCreateSecondOutboxRow() throws Exception {
        byte[] file = fixture(CHUNK_SIZE + 16, 23);
        uploadFile(registerAndLogin(unique("pendingA")), file, "First");
        MediaObject media = mediaObjectMapper.findBySha256(sha256(file));
        assertThat(requestService.requestIfNeeded(media)).isFalse();
        assertThat(outboxMapper.findActiveByMediaObjectId(media.getId())).isNotNull();
    }

    @Test
    void hlsServingValidatesPathsAndContentTypes() throws Exception {
        byte[] file = fixture(CHUNK_SIZE, 24);
        String token = registerAndLogin(unique("hls"));
        long videoId = uploadFile(token, file, "HLS video");
        MediaObject media = mediaObjectMapper.findBySha256(sha256(file));
        markReady(media);

        String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\n360p/index.m3u8\n";
        String variant = "#EXTM3U\n#EXTINF:6,\nsegment000.ts\n";
        byte[] segment = new byte[] {0, 1, 2, 3, 4};
        byte[] thumb = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

        putObject(ProcessedObjectKeys.master(media.getId()), master.getBytes(), "application/vnd.apple.mpegurl");
        putObject(ProcessedObjectKeys.renditionPlaylist(media.getId(), "360p"), variant.getBytes(), "application/vnd.apple.mpegurl");
        putObject(ProcessedObjectKeys.prefix(media.getId()) + "360p/segment000.ts", segment, "video/mp2t");
        putObject(ProcessedObjectKeys.thumbnail(media.getId()), thumb, "image/jpeg");

        mockMvc.perform(get("/api/videos/" + videoId + "/hls/master.m3u8"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("360p/index.m3u8"));
        mockMvc.perform(get("/api/videos/" + videoId + "/hls/360p/index.m3u8"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl"));
        mockMvc.perform(get("/api/videos/" + videoId + "/hls/360p/segment000.ts"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp2t"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(segment));
        mockMvc.perform(get("/api/videos/" + videoId + "/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
        mockMvc.perform(get("/api/videos/" + videoId + "/hls/360p/notes.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/videos/" + videoId + "/hls/processed/1/master.m3u8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/videos/" + videoId + "/content")
                        .header(HttpHeaders.RANGE, "bytes=0-15"))
                .andExpect(status().isPartialContent());
    }

    @Autowired
    private com.kiki.video.api.video.storage.VideoStorage videoStorage;

    private void putObject(String key, byte[] bytes, String contentType) {
        videoStorage.put(key, new java.io.ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    private void markReady(MediaObject media) {
        org.springframework.jdbc.core.JdbcTemplate jdbc = jdbc();
        jdbc.update("""
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    private long uploadFile(String token, byte[] file, String title) throws Exception {
        JsonNode init = init(token, file.length, sha256(file));
        uploadAll(token, init.get("uploadId").asString(), file);
        return complete(token, init.get("uploadId").asString(), title);
    }

    private long uploadDeduped(String token, byte[] file, String title) throws Exception {
        JsonNode init = init(token, file.length, sha256(file));
        assertThat(init.get("deduplicated").asBoolean()).isTrue();
        return complete(token, init.get("uploadId").asString(), title);
    }

    private JsonNode init(String token, long fileSize, String sha256) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"demo.mp4","fileSizeBytes":%d,"contentType":"video/mp4","fileSha256":"%s"}
                                """.formatted(fileSize, sha256))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void uploadAll(String token, String uploadId, byte[] file) throws Exception {
        int total = UploadMath.totalChunks(file.length, CHUNK_SIZE);
        for (int i = 0; i < total; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, file.length);
            byte[] part = java.util.Arrays.copyOfRange(file, start, end);
            mockMvc.perform(put("/api/uploads/" + uploadId + "/chunks/" + i)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(part)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNoContent());
        }
    }

    private long complete(String token, String uploadId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/uploads/" + uploadId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/video/id").asLong();
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String unique(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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
