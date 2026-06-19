package com.interviewlearning.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiDtos.AiProviderStatus;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Reports selected AI provider usage/status for the app header.
 *
 * Claude has a real session/weekly usage endpoint. Codex and Kimi are checked
 * for local CLI availability, but comparable quota windows are not exposed here.
 */
@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);
    private static final String USAGE_URL = "https://api.anthropic.com/api/oauth/usage";
    private static final long MIN_REFRESH_SECONDS = 180; // endpoint is unsafe to poll faster

    private final ObjectMapper mapper;
    private final AiCliService ai;
    private final boolean enabled;
    private final long refreshMillis;
    private final String userAgent;
    private final Path credentialsFile;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, UsageSnapshot> cached = new HashMap<>();
    private final Map<String, Long> lastFetchAt = new HashMap<>();
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    public UsageService(ObjectMapper mapper,
                        AiCliService ai,
                        @Value("${app.usage.enabled:true}") boolean enabled,
                        @Value("${app.usage.refresh-seconds:180}") long refreshSeconds,
                        @Value("${app.usage.user-agent:claude-code/2.1.177}") String userAgent,
                        @Value("${app.usage.credentials-file:}") String credentialsFile) {
        this.mapper = mapper;
        this.ai = ai;
        this.enabled = enabled;
        this.refreshMillis = Math.max(MIN_REFRESH_SECONDS, refreshSeconds) * 1000L;
        this.userAgent = userAgent;
        this.credentialsFile = (credentialsFile == null || credentialsFile.isBlank())
                ? Path.of(System.getProperty("user.home"), ".claude", ".credentials.json")
                : Path.of(credentialsFile);
    }

    /**
     * Returns the latest reading for the selected provider. For Claude this may
     * refresh from Anthropic; for other providers this returns CLI status.
     */
    public synchronized UsageSnapshot current(String providerId) {
        String provider = providerId == null || providerId.isBlank() ? "claude" : providerId.trim().toLowerCase();
        AiProviderStatus status = ai.status(provider);
        if (!status.available()) {
            return UsageSnapshot.unavailable(status.id(), status.label(), false, status.downloadUrl(),
                    status.label() + " CLI is not installed or cannot start.");
        }
        if (!"claude".equals(status.id())) {
            String message = "codex".equals(status.id())
                    ? "Codex CLI is available. Open Codex and run /status to see rate limits; "
                    + "this app cannot read them automatically yet."
                    : status.label() + " CLI is available. Usage limits are not exposed through this app yet.";
            return UsageSnapshot.unavailable(status.id(), status.label(), true, status.downloadUrl(),
                    message);
        }
        if (!enabled) {
            return unavailable(status, "Usage display disabled.");
        }

        long now = System.currentTimeMillis();
        UsageSnapshot current = cached.getOrDefault(status.id(), unavailable(status, "Not fetched yet."));
        long last = lastFetchAt.getOrDefault(status.id(), 0L);
        long cooldown = cooldownUntil.getOrDefault(status.id(), 0L);
        if (now < cooldown || (last != 0 && now - last < refreshMillis)) {
            return current;
        }
        lastFetchAt.put(status.id(), now);
        UsageSnapshot fetched = fetchClaude(status);
        cached.put(status.id(), fetched);
        return fetched;
    }

    private UsageSnapshot fetchClaude(AiProviderStatus status) {
        String token;
        try {
            token = readAccessToken();
        } catch (IOException e) {
            return unavailable(status, "No Claude credentials found.");
        }
        if (token == null || token.isBlank()) {
            return unavailable(status, "No Claude access token.");
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
                cooldownUntil.put(status.id(), System.currentTimeMillis() + refreshMillis);
                log.warn("Usage endpoint rate-limited (429); backing off ~{}s.", refreshMillis / 1000);
                UsageSnapshot current = cached.get(status.id());
                return current != null && current.available() ? current : unavailable(status, "Rate-limited.");
            }
            if (sc == 401 || sc == 403) {
                return unavailable(status, "Claude token expired - run claude once to refresh.");
            }
            if (sc != 200) {
                return unavailable(status, "Usage HTTP " + sc + ".");
            }
            return parseClaudeUsage(status, res.body());
        } catch (Exception e) {
            log.debug("Usage fetch failed: {}", e.getMessage());
            UsageSnapshot current = cached.get(status.id());
            return current != null && current.available() ? current : unavailable(status, "Usage fetch failed.");
        }
    }

    private String readAccessToken() throws IOException {
        byte[] bytes = Files.readAllBytes(credentialsFile);
        JsonNode root = mapper.readTree(bytes);
        JsonNode oauth = root.path("claudeAiOauth");
        JsonNode tokenNode = oauth.isMissingNode() ? root.path("accessToken") : oauth.path("accessToken");
        return tokenNode.isTextual() ? tokenNode.asText() : null;
    }

    private UsageSnapshot parseClaudeUsage(AiProviderStatus status, String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        UsageWindow session = window(root.path("five_hour"));
        UsageWindow weekly = window(root.path("seven_day"));
        if (session == null && weekly == null) {
            return unavailable(status, "Empty usage response.");
        }
        return new UsageSnapshot(status.id(), status.label(), true, status.downloadUrl(),
                true, session, weekly, null);
    }

    private UsageWindow window(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        double util = n.path("utilization").asDouble(0);
        JsonNode resets = n.path("resets_at");
        return new UsageWindow(util, resets.isTextual() ? resets.asText() : null);
    }

    private static UsageSnapshot unavailable(AiProviderStatus status, String error) {
        return UsageSnapshot.unavailable(status.id(), status.label(), status.available(), status.downloadUrl(), error);
    }
}
