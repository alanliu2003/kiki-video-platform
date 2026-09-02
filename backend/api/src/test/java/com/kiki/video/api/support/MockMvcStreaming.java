package com.kiki.video.api.support;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Completes a {@code StreamingResponseBody} before MockMvc assertions read the body.
 * Reading {@code getContentAsByteArray()} / {@code getContentAsString()} too early
 * yields an empty payload under CI load.
 */
public final class MockMvcStreaming {

    private MockMvcStreaming() {
    }

    public static ResultActions awaitStreamingResponse(MockMvc mockMvc, RequestBuilder requestBuilder)
            throws Exception {
        return mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andDo(MvcResult::getAsyncResult);
    }
}
