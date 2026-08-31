package com.kiki.video.api.danmaku;

import com.kiki.video.api.danmaku.ws.DanmakuRoomRegistry;
import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DanmakuWebSocketIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DanmakuRoomRegistry rooms;

    @Test
    void authenticatedSendReachesSameRoomAndNotOtherVideo() throws Exception {
        long videoA = upload(registerAndLogin(unique("wsaowner")), "Room A");
        long videoB = upload(registerAndLogin(unique("wsbowner")), "Room B");
        String senderToken = registerAndLogin(unique("wssender"));

        TestClient sender = connect(videoA);
        TestClient viewer = connect(videoA);
        TestClient other = connect(videoB);
        sender.auth(senderToken);

        String clientMessageId = UUID.randomUUID().toString();
        sender.sendDanmaku(clientMessageId, "hello", 5000);

        JsonNode ack = sender.awaitType("DANMAKU_ACK", Duration.ofSeconds(5));
        assertThat(ack.get("clientMessageId").asString()).isEqualTo(clientMessageId);
        long danmakuId = ack.get("danmakuId").asLong();

        JsonNode liveA = sender.awaitType("DANMAKU", Duration.ofSeconds(5));
        JsonNode liveB = viewer.awaitType("DANMAKU", Duration.ofSeconds(5));
        assertThat(liveA.get("danmaku").get("id").asLong()).isEqualTo(danmakuId);
        assertThat(liveB.get("danmaku").get("content").asString()).isEqualTo("hello");
        assertThat(other.pollType("DANMAKU", Duration.ofMillis(400))).isNull();

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM danmaku WHERE id = ?",
                Integer.class,
                danmakuId
        );
        assertThat(rows).isEqualTo(1);

        sender.close();
        viewer.close();
        other.close();
        awaitRoomEmpty(videoA);
        awaitRoomEmpty(videoB);
    }

    @Test
    void anonymousCannotSendAndInvalidMessageKeepsConnection() throws Exception {
        long videoId = upload(registerAndLogin(unique("anonowner")), "Anon room");
        TestClient anon = connect(videoId);
        anon.send("""
                {"type":"DANMAKU_SEND","clientMessageId":"x","content":"nope","videoTimeMs":1000}
                """);
        JsonNode error = anon.awaitType("ERROR", Duration.ofSeconds(5));
        assertThat(error.get("code").asString()).isEqualTo("DANMAKU_AUTH_REQUIRED");

        anon.send("not-json");
        JsonNode invalid = anon.awaitType("ERROR", Duration.ofSeconds(5));
        assertThat(invalid.get("code").asString()).isEqualTo("DANMAKU_INVALID_MESSAGE");
        assertThat(anon.session.isOpen()).isTrue();
        anon.close();
    }

    @Test
    void rateLimitReturnsErrorWithoutDisconnect() throws Exception {
        long videoId = upload(registerAndLogin(unique("rateowner")), "Rate room");
        String token = registerAndLogin(unique("rater"));
        TestClient client = connect(videoId);
        client.auth(token);
        for (int i = 0; i < 10; i++) {
            client.sendDanmaku("rate-" + i, "msg " + i, 1000 + i);
            client.awaitType("DANMAKU_ACK", Duration.ofSeconds(5));
        }
        client.sendDanmaku("rate-over", "too many", 2000);
        JsonNode error = client.awaitType("ERROR", Duration.ofSeconds(5));
        assertThat(error.get("code").asString()).isEqualTo("DANMAKU_RATE_LIMITED");
        assertThat(client.session.isOpen()).isTrue();
        client.close();
    }

    private TestClient connect(long videoId) throws Exception {
        TestClient client = new TestClient(objectMapper);
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        client.session = wsClient.execute(
                client,
                "ws://127.0.0.1:" + port + "/ws/videos/" + videoId + "/danmaku"
        ).get(5, TimeUnit.SECONDS);
        assertThat(client.session).isNotNull();
        return client;
    }

    private void awaitRoomEmpty(long videoId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (rooms.count(videoId) == 0) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(rooms.count(videoId)).isZero();
    }

    private long upload(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "demo.mp4", "video/mp4", FIXTURE))
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s@example.com",
                                  "password": "StrongPassword123"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "%s",
                                  "password": "StrongPassword123"
                                }
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

    private static byte[] fixtureVideo() {
        byte[] bytes = new byte[2048];
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        for (int i = 8; i < bytes.length; i++) {
            bytes[i] = (byte) (i & 0xFF);
        }
        return bytes;
    }

    private static final class TestClient extends TextWebSocketHandler {
        private final ObjectMapper objectMapper;
        private final BlockingQueue<JsonNode> inbound = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        private TestClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            inbound.add(objectMapper.readTree(message.getPayload()));
        }

        private void auth(String token) throws Exception {
            send("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}");
            JsonNode ok = awaitType("AUTH_OK", Duration.ofSeconds(5));
            assertThat(ok.get("type").asString()).isEqualTo("AUTH_OK");
        }

        private void sendDanmaku(String clientMessageId, String content, long videoTimeMs) throws Exception {
            send("""
                    {"type":"DANMAKU_SEND","clientMessageId":"%s","content":"%s","videoTimeMs":%d}
                    """.formatted(clientMessageId, content, videoTimeMs));
        }

        private void send(String payload) throws Exception {
            session.sendMessage(new TextMessage(payload));
        }

        private JsonNode awaitType(String type, Duration timeout) throws InterruptedException {
            JsonNode node = pollType(type, timeout);
            assertThat(node).as("expected %s", type).isNotNull();
            return node;
        }

        private JsonNode pollType(String type, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            List<JsonNode> skipped = new ArrayList<>();
            while (System.nanoTime() < deadline) {
                JsonNode next = inbound.poll(50, TimeUnit.MILLISECONDS);
                if (next == null) {
                    continue;
                }
                if (type.equals(text(next, "type"))) {
                    inbound.addAll(skipped);
                    return next;
                }
                skipped.add(next);
            }
            inbound.addAll(skipped);
            return null;
        }

        private void close() throws Exception {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null ? null : value.asString();
        }
    }
}
