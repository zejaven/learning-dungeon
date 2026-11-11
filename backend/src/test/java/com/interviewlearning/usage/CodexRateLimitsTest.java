package com.interviewlearning.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.usage.CodexRateLimits.Reading;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CodexRateLimitsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text.replace('\'', '"'));
    }

    /** Trimmed from a real codex-cli 0.167 answer for a Plus account. */
    @Test
    void readsTheFiveHourAndWeeklyWindows() throws Exception {
        Reading r = CodexRateLimits.parse(json("""
                {'ordinaryUsageAllowed': true,
                 'rateLimits': {'limitId': 'codex', 'planType': 'plus',
                   'primary': {'usedPercent': 0, 'windowDurationMins': 300, 'resetsAt': 1794449124},
                   'secondary': {'usedPercent': 3, 'windowDurationMins': 10080, 'resetsAt': 1794880117},
                   'credits': {'hasCredits': false, 'unlimited': false, 'balance': '0'}}}
                """));
        assertEquals(0.0, r.session().utilization());
        assertEquals("2026-11-12T02:05:24Z", r.session().resetsAt());
        assertEquals(3.0, r.weekly().utilization());
        assertEquals("2026-11-17T01:48:37Z", r.weekly().resetsAt());
    }

    @Test
    void tellsTheWindowsApartByLengthNotByName() throws Exception {
        Reading r = CodexRateLimits.parse(json("""
                {'rateLimits': {
                   'primary': {'usedPercent': 40, 'windowDurationMins': 10080},
                   'secondary': {'usedPercent': 7, 'windowDurationMins': 300}}}
                """));
        assertEquals(7.0, r.session().utilization());
        assertEquals(40.0, r.weekly().utilization());
        assertNull(r.session().resetsAt());
    }

    @Test
    void withoutLengthsPrimaryIsTheSession() throws Exception {
        Reading r = CodexRateLimits.parse(json("""
                {'rateLimits': {'primary': {'usedPercent': 12}, 'secondary': {'usedPercent': 30}}}
                """));
        assertEquals(12.0, r.session().utilization());
        assertEquals(30.0, r.weekly().utilization());
    }

    @Test
    void aSingleWindowLeavesTheOtherEmpty() throws Exception {
        Reading r = CodexRateLimits.parse(json("""
                {'rateLimits': {'primary': {'usedPercent': 55, 'windowDurationMins': 300}, 'secondary': null}}
                """));
        assertEquals(55.0, r.session().utilization());
        assertNull(r.weekly());
    }

    @Test
    void percentagesAreClampedToTheMeter() throws Exception {
        Reading r = CodexRateLimits.parse(json("""
                {'rateLimits': {'primary': {'usedPercent': 130, 'windowDurationMins': 300}}}
                """));
        assertEquals(100.0, r.session().utilization());
    }

    /** app-server is experimental: an answer of another shape means no reading, not a crash. */
    @Test
    void anUnexpectedAnswerIsNoReading() throws Exception {
        assertNull(CodexRateLimits.parse(json("{}")));
        assertNull(CodexRateLimits.parse(json("{'rateLimits': null}")));
        assertNull(CodexRateLimits.parse(json("{'rateLimits': {'primary': {'used': 5}}}")));
        assertNull(CodexRateLimits.parse(null));
    }
}
