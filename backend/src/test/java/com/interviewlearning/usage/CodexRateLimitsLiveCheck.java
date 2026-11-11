package com.interviewlearning.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.usage.CodexRateLimits.Reading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Asks the real codex CLI for the account's rate limits. Off by default (it
 * needs codex installed and logged in); run it after a codex-cli update, since
 * app-server is experimental and its answer may change shape:
 * {@code .\gradlew.bat :backend:test --tests "*CodexRateLimitsLiveCheck" -Dverify.codexLimits=codex.cmd}
 */
@EnabledIfSystemProperty(named = "verify.codexLimits", matches = ".+")
class CodexRateLimitsLiveCheck {

    @Test
    void theRealCliReportsTheSessionWindow() throws Exception {
        CodexRateLimits limits = new CodexRateLimits(new ObjectMapper(), new RepoPaths(""));
        Reading reading = limits.read(System.getProperty("verify.codexLimits"));
        System.out.println("codex rate limits: " + reading);
        assertNotNull(reading, "codex answered without rate-limit windows");
        assertNotNull(reading.session(), "no 5-hour window");
    }
}
