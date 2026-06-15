package com.interviewlearning.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.usage.UsageDtos.UsageSnapshot;
import com.interviewlearning.usage.UsageDtos.UsageWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Reports the local user's Claude subscription usage (current 5-hour session and
 * 7-day weekly windows) for the app header. It reads the OAuth access token from
 * the same {@code ~/.claude/.credentials.json} the Claude Code CLI uses, then
 * calls Anthropic's undocumented usage endpoint — the same one that powers the
 * CLI's {@code /usage} command.
 *
 * <p>That endpoint rate-limits aggressively (HTTP 429) and is only safe to poll
 * roughly every 180 seconds. So this service caches its last reading and never
 * hits the endpoint more often than {@code app.usage.refresh-seconds} (floored at
 * 180s), backing off further on a 429. The frontend can therefore poll this local
 * endpoint as often as it likes — upstream calls stay within the safe rate.
 */
@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);
    private static final String USAGE_URL = "https://api.anthropic.com/api/oauth/usage";
    private static final long MIN_REFRESH_SECONDS = 180; // endpoint is unsafe to poll faster

    private final ObjectMapper mapper;
    private final boolean enabled;
    private final long refreshMillis;
    private final String userAgent;
    private final Path credentialsFile;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile UsageSnapshot cached = UsageSnapshot.unavailable("Not fetched yet.");
    private long lastFetchAt = 0;
    private long cooldownUntil = 0; // extended after a 429 to back off an extra window

    public UsageService(ObjectMapper mapper,
                        @Value("${app.usage.enabled:true}") boolean enabled,
                        @Value("${app.usage.refresh-seconds:180}") long refreshSeconds,
                        @Value("${app.usage.user-agent:claude-code/2.1.177}") String userAgent,
                        @Value("${app.usage.credentials-file:}") String credentialsFile) {
        this.mapper = mapper;
        this.enabled = enabled;
        // Never poll faster than the endpoint tolerates, even if misconfigured.
        this.refreshMillis = Math.max(MIN_REFRESH_SECONDS, refreshSeconds) * 1000L;
        this.userAgent = userAgent;
        this.credentialsFile = (credentialsFile == null || credentialsFile.isBlank())
                ? Path.of(System.getProperty("user.home"), ".claude", ".credentials.json")
                : Path.of(credentialsFile);
    }

    /**
     * Returns the latest usage reading, refreshing from Anthropic at most once per
     * refresh window. Cheap to call repeatedly: between refreshes it returns the
     * cached snapshot without any network I/O.
     */
    public synchronized UsageSnapshot current() {
        if (!enabled) {
            return UsageSnapshot.unavailable("Usage display disabled.");
        }
        long now = System.currentTimeMillis();
        // Within a refresh window (or backing off after a 429): serve cache.
        if (now < cooldownUntil || (lastFetchAt != 0 && now - lastFetchAt < refreshMillis)) {
            return cached;
        }
        lastFetchAt = now;
        cached = fetch();
        return cached;
    }

    private UsageSnapshot fetch() {
        String token;
        try {
            token = readAccessToken();
        } catch (IOException e) {
            return UsageSnapshot.unavailable("No Claude credentials found.");
        }
        if (token == null || token.isBlank()) {
            return UsageSnapshot.unavailable("No Claude access token.");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(USAGE_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .header("anthropic-beta", "oauth-2025-04-20")
                    .header("User-Agent", userAgent)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = res.statusCode();
            if (sc == 429) {
                // Back off an extra window so we don't compound the rate limit.
                cooldownUntil = System.currentTimeMillis() + refreshMillis;
                log.warn("Usage endpoint rate-limited (429); backing off ~{}s.", refreshMillis / 1000);
                return cached.available() ? cached : UsageSnapshot.unavailable("Rate-limited.");
            }
            if (sc == 401 || sc == 403) {
                return UsageSnapshot.unavailable("Claude token expired — run claude once to refresh.");
            }
            if (sc != 200) {
                return UsageSnapshot.unavailable("Usage HTTP " + sc + ".");
            }
            return parse(res.body());
        } catch (Exception e) {
            log.debug("Usage fetch failed: {}", e.getMessage());
            // Keep showing the last good reading if we have one.
            return cached.available() ? cached : UsageSnapshot.unavailable("Usage fetch failed.");
        }
    }

    /** Extracts the OAuth access token from the Claude Code credentials file. */
    private String readAccessToken() throws IOException {
        byte[] bytes = Files.readAllBytes(credentialsFile);
        JsonNode root = mapper.readTree(bytes);
        JsonNode oauth = root.path("claudeAiOauth");
        JsonNode tokenNode = oauth.isMissingNode() ? root.path("accessToken") : oauth.path("accessToken");
        return tokenNode.isTextual() ? tokenNode.asText() : null;
    }

    private UsageSnapshot parse(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        UsageWindow session = window(root.path("five_hour"));
        UsageWindow weekly = window(root.path("seven_day"));
        if (session == null && weekly == null) {
            return UsageSnapshot.unavailable("Empty usage response.");
        }
        return new UsageSnapshot(true, session, weekly, null);
    }

    private UsageWindow window(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        double util = n.path("utilization").asDouble(0);
        JsonNode resets = n.path("resets_at");
        return new UsageWindow(util, resets.isTextual() ? resets.asText() : null);
    }
}
