package com.kiki.video.api.observability;

import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestIdIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesRequestIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestId.HEADER, matchesPattern("^[A-Za-z0-9._-]{8,128}$")));
    }

    @Test
    void echoesValidIncomingRequestId() throws Exception {
        String requestId = "550e8400-e29b-41d4-a716-446655440000";
        mockMvc.perform(get("/api/health").header(RequestId.HEADER, requestId))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestId.HEADER, requestId))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void replacesInvalidAndOversizedRequestIds() throws Exception {
        mockMvc.perform(get("/api/health").header(RequestId.HEADER, "no"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestId.HEADER, not("no")));

        String oversized = "x".repeat(400);
        mockMvc.perform(get("/api/health").header(RequestId.HEADER, oversized))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestId.HEADER, not(oversized)));
    }
}
