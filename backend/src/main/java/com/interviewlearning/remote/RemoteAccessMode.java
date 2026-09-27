package com.interviewlearning.remote;

import java.util.Locale;

/**
 * How the app is reachable from other devices. Chosen with
 * {@code app.remote.mode} (via {@code remote.mode} in config/secret.yml).
 *
 * <p>{@link #DIRECT} and {@link #PROXIED} are gated identically — both mean
 * "remote access on, token required". Who counts as local is decided by the TCP
 * peer, not by the mode (see {@link RemoteAccessFilter#isLocal}), so the server
 * can be bound to the LAN and sit behind Tailscale Serve at once. The name only
 * records how the device is expected to arrive, which the launcher uses to print
 * the right address.
 */
public enum RemoteAccessMode {

    /**
     * Loopback only, the default: anything else is refused regardless of what
     * the server is bound to.
     */
    OFF,

    /** The server itself listens on a reachable interface ({@code app.bind-address: 0.0.0.0}) — home Wi-Fi. */
    DIRECT,

    /**
     * A proxy on this machine forwards to the loopback port — Tailscale Serve, or
     * the Vite dev server. May be combined with {@code app.bind-address: 0.0.0.0}.
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
