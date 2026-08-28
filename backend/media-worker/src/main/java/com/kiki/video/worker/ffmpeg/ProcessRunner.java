package com.kiki.video.worker.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {

    private static final int MAX_CAPTURE_CHARS = 16_384;

    public ProcessResult run(List<String> command, Duration timeout, Path workDir) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command is required");
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start process " + command.getFirst(), ex);
        }
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return ProcessResult.timeout(stderr.getNow(""));
            }
            return new ProcessResult(process.exitValue(), false, stdout.join(), stderr.join());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Process was interrupted", ex);
        }
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                stream.transferTo(buffer);
                return truncate(buffer.toString(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                return "";
            }
        });
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').strip();
        if (normalized.length() <= MAX_CAPTURE_CHARS) {
            return normalized;
        }
        return normalized.substring(normalized.length() - MAX_CAPTURE_CHARS);
    }
}
