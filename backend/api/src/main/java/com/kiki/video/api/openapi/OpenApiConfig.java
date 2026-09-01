package com.kiki.video.api.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kikiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("kiki-video-platform API")
                        .version("pre-v1")
                        .description("""
                                Unversioned / pre-v1 HTTP API for local demos and external evaluation.
                                Backward compatibility is best-effort until a stable version is declared.
                                This document is not a compatibility guarantee.

                                Authenticate with `Authorization: Bearer <accessToken>` from `POST /api/auth/login`.
                                Public reads do not require a token. Actuator is intentionally excluded.
                                """))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token from POST /api/auth/login. Do not paste production secrets.")
                        ))
                .tags(List.of(
                        new Tag().name(OpenApiTags.AUTH).description("Register and login"),
                        new Tag().name(OpenApiTags.VIDEOS).description("Video metadata and upload"),
                        new Tag().name(OpenApiTags.DISCOVERY).description("Trending and recent public feeds"),
                        new Tag().name(OpenApiTags.SEARCH).description("Elasticsearch video search"),
                        new Tag().name(OpenApiTags.USERS).description("Current user and public profiles"),
                        new Tag().name(OpenApiTags.SOCIAL).description("Likes, favorites, and follows"),
                        new Tag().name(OpenApiTags.COMMENTS).description("Threaded comments"),
                        new Tag().name(OpenApiTags.DANMAKU).description("Danmaku history"),
                        new Tag().name(OpenApiTags.RECOMMENDATIONS).description("Authenticated deterministic recommendations"),
                        new Tag().name(OpenApiTags.NOTIFICATIONS).description("Authenticated activity inbox"),
                        new Tag().name(OpenApiTags.PLAYBACK).description("Playback descriptors, HLS, views, and media")
                ));
    }

    @Bean
    public OpenApiCustomizer kikiOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().entrySet().removeIf(entry -> entry.getKey().startsWith("/actuator"));
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                annotateErrors(operation);
                addExamples(path, method, operation);
            }));
        };
    }

    private static void annotateErrors(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().addApiResponse("400", errorResponse("Validation or invalid input"));
        operation.getResponses().addApiResponse("401", errorResponse("Authentication is required"));
        operation.getResponses().addApiResponse("404", errorResponse("Resource was not found"));
    }

    private static ApiResponse errorResponse(String description) {
        MediaType mediaType = new MediaType();
        mediaType.addExamples(
                "error",
                new Example().value("""
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Request validation failed",
                          "timestamp": "2026-09-01T08:00:00Z",
                          "requestId": "550e8400-e29b-41d4-a716-446655440000"
                        }
                        """)
        );
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", mediaType));
    }

    private static void addExamples(String path, PathItem.HttpMethod method, Operation operation) {
        if (method != PathItem.HttpMethod.GET || operation.getResponses() == null) {
            return;
        }
        String example = switch (path) {
            case "/api/videos/{videoId}" -> """
                    {
                      "id": 12,
                      "title": "City walk",
                      "description": "A short clip",
                      "owner": { "id": 3, "username": "alice", "displayName": "Alice" },
                      "contentType": "video/mp4",
                      "fileSizeBytes": 1048576,
                      "status": "UPLOADED",
                      "processingStatus": "READY",
                      "createdAt": "2026-08-20T10:00:00Z",
                      "viewCount": 184,
                      "durationSeconds": 42.5
                    }
                    """;
            case "/api/search/videos" -> """
                    {
                      "items": [
                        {
                          "videoId": 12,
                          "title": "City walk",
                          "descriptionSnippet": "A short clip",
                          "owner": { "id": 3, "username": "alice", "displayName": "Alice" },
                          "createdAt": "2026-08-20T10:00:00Z",
                          "durationSeconds": 42.5,
                          "thumbnailUrl": "/api/videos/12/thumbnail",
                          "processingStatus": "READY",
                          "highlights": { "title": [], "description": [], "ownerUsername": [], "ownerDisplayName": [] },
                          "viewCount": 184
                        }
                      ],
                      "page": 0,
                      "size": 20,
                      "total": 1,
                      "tookMs": 8
                    }
                    """;
            case "/api/videos/trending", "/api/videos/recent" -> """
                    {
                      "items": [
                        {
                          "id": 12,
                          "title": "City walk",
                          "owner": { "id": 3, "username": "alice", "displayName": "Alice" },
                          "createdAt": "2026-08-20T10:00:00Z",
                          "durationSeconds": 42.5,
                          "thumbnailUrl": "/api/videos/12/thumbnail",
                          "processingStatus": "READY",
                          "viewCount": 184,
                          "likeCount": 9
                        }
                      ],
                      "page": 0,
                      "size": 20,
                      "total": 1
                    }
                    """;
            case "/api/recommendations/videos" -> """
                    {
                      "items": [
                        {
                          "id": 12,
                          "title": "City walk",
                          "owner": { "id": 3, "username": "alice", "displayName": "Alice" },
                          "createdAt": "2026-08-20T10:00:00Z",
                          "durationSeconds": 42.5,
                          "thumbnailUrl": "/api/videos/12/thumbnail",
                          "processingStatus": "READY",
                          "viewCount": 184,
                          "likeCount": 9,
                          "recommendationReason": "Because you follow this creator"
                        }
                      ],
                      "page": 0,
                      "size": 20,
                      "total": 1,
                      "coldStart": false
                    }
                    """;
            case "/api/notifications" -> """
                    {
                      "items": [
                        {
                          "id": 40,
                          "type": "USER_FOLLOWED",
                          "read": false,
                          "createdAt": "2026-08-31T01:00:00Z",
                          "actor": { "id": 5, "username": "bob", "displayName": "Bob" },
                          "video": null,
                          "comment": null
                        }
                      ],
                      "page": 0,
                      "size": 20,
                      "total": 1
                    }
                    """;
            case "/api/videos/{videoId}/playback" -> """
                    {
                      "status": "READY",
                      "type": "HLS",
                      "mode": "HLS",
                      "url": "/api/videos/12/hls/master.m3u8",
                      "expiresAt": "2026-09-01T06:15:00Z",
                      "processingStatus": "READY",
                      "deliveryMode": "presigned",
                      "manifestUrl": "/api/videos/12/hls/master.m3u8",
                      "contentUrl": "http://127.0.0.1:9000/videos/raw/example?X-Amz-Algorithm=AWS4-HMAC-SHA256",
                      "thumbnailUrl": "http://127.0.0.1:9000/videos/processed/1/thumbnail.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                    }
                    """;
            default -> null;
        };
        if (example == null) {
            return;
        }
        ApiResponse ok = operation.getResponses().get("200");
        if (ok == null) {
            ok = new ApiResponse().description("OK");
            operation.getResponses().addApiResponse("200", ok);
        }
        if (ok.getContent() == null) {
            ok.setContent(new Content());
        }
        MediaType mediaType = ok.getContent().getOrDefault("application/json", new MediaType());
        mediaType.addExamples("sample", new Example().value(example));
        ok.getContent().addMediaType("application/json", mediaType);
    }

}
