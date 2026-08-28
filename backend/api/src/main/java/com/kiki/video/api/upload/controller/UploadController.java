package com.kiki.video.api.upload.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.upload.dto.CompleteUploadRequest;
import com.kiki.video.api.upload.dto.CompleteUploadResponse;
import com.kiki.video.api.upload.dto.InitUploadRequest;
import com.kiki.video.api.upload.dto.InitUploadResponse;
import com.kiki.video.api.upload.dto.UploadStatusResponse;
import com.kiki.video.api.upload.service.UploadService;
import com.kiki.video.common.ApiConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/init")
    public InitUploadResponse init(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody InitUploadRequest request
    ) {
        return uploadService.init(principal.userId(), request);
    }

    @GetMapping("/{uploadId}")
    public UploadStatusResponse status(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String uploadId
    ) {
        return uploadService.status(principal.userId(), parseUploadId(uploadId));
    }

    @PutMapping(path = "/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> uploadChunk(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String uploadId,
            @PathVariable String chunkIndex,
            HttpServletRequest request
    ) throws IOException {
        uploadService.uploadChunk(
                principal.userId(),
                parseUploadId(uploadId),
                parseChunkIndex(chunkIndex),
                request.getInputStream(),
                request.getContentLengthLong() < 0 ? null : request.getContentLengthLong()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uploadId}/complete")
    public CompleteUploadResponse complete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String uploadId,
            @RequestBody(required = false) CompleteUploadRequest request
    ) {
        return uploadService.complete(principal.userId(), parseUploadId(uploadId), request);
    }

    private static UUID parseUploadId(String uploadId) {
        try {
            return UUID.fromString(uploadId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.UPLOAD_NOT_FOUND, HttpStatus.NOT_FOUND, "Upload was not found");
        }
    }

    private static int parseChunkIndex(String chunkIndex) {
        try {
            return Integer.parseInt(chunkIndex);
        } catch (NumberFormatException ex) {
            throw new ApiException(
                    ErrorCode.UPLOAD_CHUNK_OUT_OF_RANGE,
                    HttpStatus.BAD_REQUEST,
                    "Chunk index is out of range"
            );
        }
    }
}
