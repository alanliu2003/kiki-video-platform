package com.kiki.video.worker.ffmpeg;

public record ProcessResult(int exitCode, boolean timedOut, String stdout, String stderr) {

    public static ProcessResult timeout(String stderr) {
        return new ProcessResult(-1, true, "", stderr);
    }

    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
