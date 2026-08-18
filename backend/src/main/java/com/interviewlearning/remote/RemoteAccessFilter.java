package com.interviewlearning.remote;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gate for requests that do not originate from this machine.
 *
 * <p>The app is a single-user local tool: the run/sql/challenge/analyze
 * endpoints compile and execute whatever code the caller sends, and the
 * generation endpoints drive an AI CLI with file-writing permissions. None of
 * that is safe to expose, so by default anything that is not a loopback request
 * is refused outright — even if the server was bound to a public interface by
 * mistake.
 *
 * <p>To use the app from a phone, set {@code app.remote.mode} (see
 * {@link RemoteAccessMode}) and a shared {@code app.remote.token}. The token is
 * presented once as {@code ?token=...}; the filter stores it in an HttpOnly
 * cookie and redirects to the clean URL, so every later request — including the
 * SPA's same-origin fetches — carries it automatically.
 *
 * <p>Code-execution endpoints stay local-only even for an authenticated remote
 * client unless {@code app.remote.allow-code-execution} is on: a leaked token
 * should not be a remote shell.
 */
@Component
public class RemoteAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RemoteAccessFilter.class);

    /** Query parameter that hands the token over on the first visit. */
    static final String TOKEN_PARAM = "token";
    /** Cookie the token is kept in afterwards. */
    static final String TOKEN_COOKIE = "dungeon_remote";
    /** A token short enough to guess is worse than none, so refuse to start with one. */
    static final int MIN_TOKEN_LENGTH = 16;

    /**
     * Endpoints that compile, execute or query with caller-supplied code. The
     * mobile profile does not use any of them (practice is a desktop screen).
     */
    private static final List<String> CODE_EXECUTION_PATHS =
            List.of("/api/run", "/api/sql", "/api/challenge", "/api/analyze");

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    /** What the filter does with a request, once the mode and token are known. */
    enum Decision {
        ALLOW,
        /** Valid token in the query: store it in a cookie, then redirect to the clean URL. */
        BOOTSTRAP,
        /** Remote client, missing or wrong token. */
        UNAUTHORIZED,
        /** Remote client while remote access is off. */
        REMOTE_DISABLED,
        /** Authenticated remote client asking to run code. */
        CODE_EXECUTION_BLOCKED,
    }

    private final RemoteAccessMode mode;
    private final String token;
    private final boolean allowCodeExecution;

    public RemoteAccessFilter(@Value("${app.remote.mode:off}") String mode,
                              @Value("${app.remote.token:}") String token,
                              @Value("${app.remote.allow-code-execution:false}") boolean allowCodeExecution) {
        this.mode = RemoteAccessMode.parse(mode);
        this.token = token == null ? "" : token.trim();
        this.allowCodeExecution = allowCodeExecution;

        if (this.mode != RemoteAccessMode.OFF && this.token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "app.remote.mode=" + this.mode.name().toLowerCase()
                            + " needs app.remote.token of at least " + MIN_TOKEN_LENGTH
                            + " characters (set 'remote.token' in config/secret.yml)");
        }
        if (this.mode == RemoteAccessMode.OFF) {
            log.info("Remote access: off (loopback only)");
        } else {
            log.warn("Remote access: {} mode, token auth on, code execution {}",
                    this.mode.name().toLowerCase(), allowCodeExecution ? "ALLOWED" : "blocked");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean local = isLocal(mode, request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        // Deliberately not request.getParameter(): that would parse (and consume)
        // a form-encoded body. Only the query string can carry the token.
        String queryToken = queryParam(request.getQueryString(), TOKEN_PARAM);
        Decision decision = decide(mode, token, allowCodeExecution, local,
                request.getRequestURI(), cookieToken(request), queryToken);

        switch (decision) {
            case ALLOW -> chain.doFilter(request, response);
            case BOOTSTRAP -> {
                response.addCookie(tokenCookie(queryToken, request.isSecure()));
                // Only a typed/opened link should be redirected; an API call that
                // carried the token in its query just proceeds.
                if ("GET".equalsIgnoreCase(request.getMethod())) {
                    response.sendRedirect(withoutToken(request.getRequestURI(), request.getQueryString()));
                } else {
                    chain.doFilter(request, response);
                }
            }
            case UNAUTHORIZED -> deny(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Remote access needs a token. Open this address once as ?" + TOKEN_PARAM + "=<token>.");
            case REMOTE_DISABLED -> deny(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "This app answers local requests only (app.remote.mode=off).");
            case CODE_EXECUTION_BLOCKED -> deny(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "Running code is disabled for remote clients "
                            + "(set app.remote.allow-code-execution=true to permit it).");
        }
    }

    /**
     * The whole policy, as a pure function of the request's classification.
     * Order matters: an unauthenticated caller must not be able to tell which
     * paths exist, so the token is checked before the code-execution rule.
     */
    static Decision decide(RemoteAccessMode mode, String configuredToken, boolean allowCodeExecution,
                           boolean local, String path, String cookieToken, String queryToken) {
        if (local) return Decision.ALLOW;
        if (mode == RemoteAccessMode.OFF) return Decision.REMOTE_DISABLED;

        boolean fromQuery = queryToken != null && !queryToken.isBlank();
        String presented = fromQuery ? queryToken : cookieToken;
        if (!matches(configuredToken, presented)) return Decision.UNAUTHORIZED;

        if (!allowCodeExecution && isCodeExecution(path)) return Decision.CODE_EXECUTION_BLOCKED;
        return fromQuery ? Decision.BOOTSTRAP : Decision.ALLOW;
    }

    /**
     * Whether the request came from this machine. In {@link RemoteAccessMode#PROXIED}
     * the real client sits in the first X-Forwarded-For entry, because the proxy
     * (Tailscale Serve, the Vite dev server) connects over loopback itself. That
     * header is trusted ONLY in that mode — a directly exposed server would let
     * anyone claim to be local by sending it.
     */
    static boolean isLocal(RemoteAccessMode mode, String remoteAddr, String forwardedFor) {
        String client = remoteAddr;
        if (mode == RemoteAccessMode.PROXIED && forwardedFor != null && !forwardedFor.isBlank()) {
            client = forwardedFor.split(",")[0];
        }
        return isLoopback(client);
    }

    /**
     * Loopback test over an address literal. Deliberately does not go through
     * {@code InetAddress.getByName}: that resolves hostnames, and this string can
     * come from a request header.
     */
    static boolean isLoopback(String addr) {
        if (addr == null) return false;
        String a = addr.trim();
        if (a.startsWith("[") && a.endsWith("]")) a = a.substring(1, a.length() - 1);
        int zone = a.indexOf('%');
        if (zone > 0) a = a.substring(0, zone);
        if (a.startsWith("::ffff:")) a = a.substring("::ffff:".length()); // IPv4-mapped IPv6
        if (a.equals("::1") || a.equals("0:0:0:0:0:0:0:1")) return true;

        Matcher m = IPV4.matcher(a);
        if (!m.matches()) return false;
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) return false;
        }
        return Integer.parseInt(m.group(1)) == 127; // the whole 127.0.0.0/8
    }

    static boolean isCodeExecution(String path) {
        if (path == null) return false;
        return CODE_EXECUTION_PATHS.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    /** Reads one parameter out of a raw query string, without touching the body. */
    static String queryParam(String queryString, String name) {
        if (queryString == null || queryString.isEmpty()) return null;
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (!key.equals(name)) continue;
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }

    /** The same URL with the token parameter dropped, so it stays out of history. */
    static String withoutToken(String uri, String queryString) {
        if (queryString == null || queryString.isEmpty()) return uri;
        StringBuilder kept = new StringBuilder();
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (key.equals(TOKEN_PARAM)) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(pair);
        }
        return kept.length() == 0 ? uri : uri + "?" + kept;
    }

    private static boolean matches(String configured, String presented) {
        if (configured == null || configured.isEmpty() || presented == null) return false;
        return MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private static String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (TOKEN_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static Cookie tokenCookie(String value, boolean secure) {
        Cookie cookie = new Cookie(TOKEN_COOKIE, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure); // over plain http (LAN) a Secure cookie would never come back
        cookie.setMaxAge(365 * 24 * 60 * 60);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    private void deny(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws IOException {
        log.warn("Refused {} {} from {} ({})", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr(), status);
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(message);
    }
}
