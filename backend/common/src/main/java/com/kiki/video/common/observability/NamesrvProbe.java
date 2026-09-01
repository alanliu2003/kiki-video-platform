package com.kiki.video.common.observability;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class NamesrvProbe {

    private NamesrvProbe() {
    }

    public static boolean reachable(String namesrvAddr, int timeoutMs) {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            return false;
        }
        for (String endpoint : namesrvAddr.split(";")) {
            String trimmed = endpoint.trim();
            int colon = trimmed.lastIndexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                continue;
            }
            String host = trimmed.substring(0, colon);
            int port;
            try {
                port = Integer.parseInt(trimmed.substring(colon + 1));
            } catch (NumberFormatException ex) {
                continue;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), Math.max(50, timeoutMs));
                return true;
            } catch (IOException ignored) {
                // try the next namesrv address
            }
        }
        return false;
    }
}
