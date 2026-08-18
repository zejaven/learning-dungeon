package com.interviewlearning.remote;

import java.util.Locale;

/**
 * How the app is reachable from other devices. Chosen with
 * {@code app.remote.mode} (via {@code remote.mode} in config/secret.yml).
 */
public enum RemoteAccessMode {

    /**
     * Loopback only, the default: anything else is refused regardless of what
     * the server is bound to.
     */
    OFF,

    /**
     * The server itself listens on a reachable interface
     * ({@code app.bind-address: 0.0.0.0}) — home Wi-Fi. The peer address is the
     * client, and X-Forwarded-For is ignored because a direct caller can forge it.
     */
    DIRECT,

    /**
     * A trusted proxy on this machine forwards to the loopback port — Tailscale
     * Serve, or the Vite dev server. The client address is read from the first
     * X-Forwarded-For entry, so keep the server bound to 127.0.0.1 in this mode:
     * anything that can reach the port directly could forge that header.
     */
    PROXIED;

    public static RemoteAccessMode parse(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "", "off", "false", "none" -> OFF;
            case "direct", "lan" -> DIRECT;
            case "proxied", "proxy", "tailscale" -> PROXIED;
            default -> throw new IllegalStateException(
                    "Unknown app.remote.mode '" + value + "' (expected off, direct or proxied)");
        };
    }
}
