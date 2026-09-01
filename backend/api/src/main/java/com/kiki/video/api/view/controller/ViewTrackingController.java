package com.kiki.video.api.view.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.view.ViewerIdentity;
import com.kiki.video.api.view.dto.QualifyViewRequest;
import com.kiki.video.api.view.dto.QualifyViewResponse;
import com.kiki.video.api.view.service.ViewTrackingService;
import com.kiki.video.api.openapi.OpenApiTags;
import com.kiki.video.common.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping(ApiConstants.API_PREFIX)
@Tag(name = OpenApiTags.PLAYBACK)
public class ViewTrackingController {

    private static final Duration ANON_COOKIE_TTL = Duration.ofDays(365);

    private final ViewTrackingService viewTrackingService;

    public ViewTrackingController(ViewTrackingService viewTrackingService) {
        this.viewTrackingService = viewTrackingService;
    }

    @PostMapping("/videos/{videoId}/views/qualify")
    @Operation(summary = "Qualify a view")
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    schema = @Schema(implementation = QualifyViewResponse.class),
                    examples = @ExampleObject(value = """
                            { "counted": true, "viewCount": 185, "alreadyCounted": false }
                            """)
            )
    )
    public ResponseEntity<QualifyViewResponse> qualify(
            @PathVariable Long videoId,
            @RequestBody(required = false) QualifyViewRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(value = ViewerIdentity.ANON_COOKIE, required = false) String anonCookie
    ) {
        ViewerIdentity viewer = viewTrackingService.resolveViewer(principal, anonCookie);
        QualifyViewResponse body = viewTrackingService.qualify(videoId, request, viewer);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        viewer.issuedAnonId().ifPresent(id -> builder.header(HttpHeaders.SET_COOKIE, anonCookie(id).toString()));
        return builder.body(body);
    }

    private static ResponseCookie anonCookie(java.util.UUID id) {
        return ResponseCookie.from(ViewerIdentity.ANON_COOKIE, id.toString())
                .httpOnly(true)
                .path("/")
                .maxAge(ANON_COOKIE_TTL)
                .sameSite("Lax")
                .build();
    }
}
