package com.interviewlearning.api;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

/**
 * Streams an answer from the selected AI provider to a question about the current topic.
 * Read-only: no file-editing permissions are granted.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AiCliService ai;
    private final TopicRepository topics;

    public AssistantController(AiCliService ai,
                              TopicRepository topics) {
        this.ai = ai;
        this.topics = topics;
    }

    public record AskRequest(String topicId, String question, String code, String lang, String provider) {
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody AskRequest request) {
        return ai.stream(request.provider(), buildPrompt(request), AiTask.ASSISTANT);
    }

    public record EvaluateRequest(String topicId, String question, String answer, String lang, String provider) {
    }

    /**
     * Grades the user's spoken answer to one boss-fight interview question, using
     * the topic theory as ground truth. The reply starts with a machine-readable
     * {@code SCORE: <n>/10} line the frontend parses into a badge.
     */
    @PostMapping(value = "/evaluate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter evaluate(@RequestBody EvaluateRequest request) {
        return ai.stream(request.provider(), buildEvaluationPrompt(request), AiTask.EVALUATE);
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

    private String buildEvaluationPrompt(EvaluateRequest request) {
        Optional<TopicDetail> topic = request.topicId() == null
                ? Optional.empty()
                : topics.getTopic(request.topicId());
        boolean ru = "ru".equalsIgnoreCase(request.lang());

        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict but fair Java technical interviewer grading a ")
                .append("candidate's answer to ONE interview question. Use the topic ")
                .append("reference material below as the ground truth.\n\n");
        sb.append("Grade the answer from 0 to 10, where 0 = empty or wrong, ")
                .append("6 = acceptable pass, 10 = excellent, complete and precise.\n");
        sb.append("Your VERY FIRST line must be exactly `SCORE: <n>/10` where <n> is a ")
                .append("single integer from 0 to 10. Then a blank line, then a short, ")
                .append("specific explanation: what was correct, what was missing or wrong, ")
                .append("and the key point a strong answer needs. Do not create or edit files.\n");
        sb.append(ru
                ? "Write the explanation in Russian, but keep code, identifiers and "
                        + "technical terms like Java, HashMap, hashCode in their original form. "
                        + "The SCORE line stays in English exactly as specified.\n\n"
                : "Write the explanation in English.\n\n");

        topic.ifPresent(t -> {
            String title = ru ? t.title().ru() : t.title().en();
            String category = ru ? t.category().ru() : t.category().en();
            String explanation = ru ? t.explanation().ru() : t.explanation().en();
            sb.append("Topic: ").append(title).append(" (").append(category).append(").\n\n");
            if (!explanation.isBlank()) {
                sb.append("Topic reference material (ground truth):\n")
                        .append(explanation).append("\n\n");
            }
        });

        sb.append("Interview question:\n")
                .append(request.question() == null ? "" : request.question().trim())
                .append("\n\n");
        sb.append("Candidate's answer:\n")
                .append(request.answer() == null ? "" : request.answer().trim())
                .append("\n");
        return sb.toString();
    }
}
