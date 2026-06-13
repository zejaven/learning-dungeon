package com.interviewlearning.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a program's raw output lines into normal output and decoded trace
 * events. A trace line is any line starting with {@code @@TRACE@@} followed by
 * a JSON object (see visual.Trace).
 */
public final class TraceCollector {

    private static final Logger log = LoggerFactory.getLogger(TraceCollector.class);
    private static final String PREFIX = "@@TRACE@@";

    private final ObjectMapper mapper;
    private final StringBuilder output = new StringBuilder();
    private final List<JsonNode> events = new ArrayList<>();

    public TraceCollector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void accept(String line) {
        if (line.startsWith(PREFIX)) {
            String json = line.substring(PREFIX.length());
            try {
                events.add(mapper.readTree(json));
            } catch (Exception e) {
                log.warn("Skipping malformed trace line: {}", e.getMessage());
            }
        } else {
            output.append(line).append('\n');
        }
    }

    public String output() {
        return output.toString();
    }

    public List<JsonNode> events() {
        return events;
    }
}
