package com.interviewlearning.api;

import com.interviewlearning.claude.ClaudeCodeService;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

/**
 * Streams an answer from Claude Code to a question about the current topic.
 * Read-only: no file-editing permissions are granted.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final ClaudeCodeService claude;
    private final TopicRepository topics;
    private final String model;

    public AssistantController(ClaudeCodeService claude,
                              TopicRepository topics,
                              @Value("${app.claude.assistant-model:}") String model) {
        this.claude = claude;
        this.topics = topics;
        this.model = model;
    }

    public record AskRequest(String topicId, String question, String code, String lang) {
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody AskRequest request) {
        List<String> args = model == null || model.isBlank()
                ? List.of()
                : List.of("--model", model);
        return claude.stream(buildPrompt(request), args);
    }

    private String buildPrompt(AskRequest request) {
        Optional<TopicDetail> topic = request.topicId() == null
                ? Optional.empty()
                : topics.getTopic(request.topicId());
        boolean ru = "ru".equalsIgnoreCase(request.lang());

        StringBuilder sb = new StringBuilder();
        sb.append("You are a concise, friendly Java interview mentor. ");
        sb.append("Answer the user's question directly and practically, with a short ")
                .append("interview-ready explanation and a tiny example if helpful. ")
                .append("Do not create or edit any files; just answer in text. ");
        sb.append(ru
                ? "Reply in Russian, but keep code, identifiers and technical terms "
                        + "like Java, HashMap, hashCode in their original form.\n\n"
                : "Reply in English.\n\n");

        topic.ifPresent(t -> {
            String title = ru ? t.title().ru() : t.title().en();
            String category = ru ? t.category().ru() : t.category().en();
            String explanation = ru ? t.explanation().ru() : t.explanation().en();
            sb.append("Current topic: ").append(title)
                    .append(" (").append(category).append(").\n\n");
            if (!explanation.isBlank()) {
                sb.append("Topic reference material:\n").append(explanation).append("\n\n");
            }
        });

        if (request.code() != null && !request.code().isBlank()) {
            sb.append("The user is currently looking at this code:\n```java\n")
                    .append(request.code()).append("\n```\n\n");
        }

        sb.append("Question: ").append(request.question() == null ? "" : request.question());
        return sb.toString();
    }
}
