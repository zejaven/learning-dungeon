package com.interviewlearning.runner;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Outcome of compiling and running a user snippet.
 *
 * @param success      true if the program compiled and ran to completion
 * @param output       combined program stdout/stderr with trace lines removed
 * @param traceEvents  decoded {@code @@TRACE@@} events, in order
 * @param error        compile errors / timeout / failure message, or null
 */
public record RunResult(
        boolean success,
        String output,
        List<JsonNode> traceEvents,
        String error
) {
    public static RunResult failure(String error) {
        return new RunResult(false, "", List.of(), error);
    }
}
