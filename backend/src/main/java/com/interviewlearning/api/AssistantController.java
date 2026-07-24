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

import java.util.Map;
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

    /**
     * Domain-specific wording for the mentor/grader prompts, keyed by
     * {@link TopicDetail#domainId()}. Unknown domains fall back to a neutral
     * voice; no topic at all keeps the historical Java voice.
     */
    private record DomainVoice(String mentorRole, String graderIntro, String termsExample) {
    }

    private static final Map<String, DomainVoice> VOICES = Map.of(
            "java", new DomainVoice(
                    "concise, friendly Java interview mentor",
                    "You are a strict but fair Java technical interviewer grading a "
                            + "candidate's answer to ONE specific interview question. ",
                    "Java, HashMap, hashCode"),
            "ndm", new DomainVoice(
                    "concise, friendly mentor for a Network Design and Management course",
                    "You are a strict but fair examiner in a Network Design and Management "
                            + "course grading a student's answer to ONE specific discussion question. ",
                    "OSPF, TCAM, leaf-spine"));

    private static final DomainVoice GENERIC_VOICE = new DomainVoice(
            "concise, friendly technical mentor",
            "You are a strict but fair technical interviewer grading a "
                    + "candidate's answer to ONE specific question. ",
            "HTTP, JSON");

    private static DomainVoice voiceFor(Optional<TopicDetail> topic) {
        return topic.map(t -> VOICES.getOrDefault(t.domainId(), GENERIC_VOICE))
                .orElse(VOICES.get("java"));
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
        DomainVoice voice = voiceFor(topic);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a ").append(voice.mentorRole()).append(". ");
        sb.append("Answer the user's question directly and practically, with a short ")
                .append("interview-ready explanation and a tiny example if helpful. ")
                .append("Do not create or edit any files; just answer in text. ");
        sb.append(ru
                ? "Reply in Russian, but keep code, identifiers and technical terms "
                        + "like " + voice.termsExample() + " in their original form.\n\n"
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
        DomainVoice voice = voiceFor(topic);

        StringBuilder sb = new StringBuilder();
        sb.append(voice.graderIntro());
        sb.append("Judge ONLY ")
                .append("how well the answer addresses THAT question as asked. The topic ")
                .append("reference material below is provided so you can verify factual ")
                .append("correctness — it is NOT a checklist the candidate must cover. Do ")
                .append("NOT lower the score for omitting facts that belong to the broader ")
                .append("topic but fall outside the scope of THIS question.\n\n");
        sb.append("Grade the answer from 0 to 10 based on the question asked: 0 = empty or ")
                .append("wrong, 6 = correct core, 10 = correct, precise and complete FOR ")
                .append("THIS QUESTION — a concise answer that fully and accurately addresses ")
                .append("the question earns a 10 even if it never mentions adjacent topics.\n");
        sb.append("Derive the score from in-scope content only. You MAY add an optional ")
                .append("extras section with related points for the candidate's broader ")
                .append("learning, but it MUST NOT affect the score and must be clearly ")
                .append("marked as optional, not as a gap.\n");
        sb.append("Your VERY FIRST line must be exactly `SCORE: <n>/10` where <n> is a ")
                .append("single integer from 0 to 10. Then a blank line, then a short, ")
                .append("specific explanation: what was correct, what was wrong or missing ")
                .append("WITHIN THE SCOPE of the question, and — only if relevant — an ")
                .append("optional extras note. Do not create or edit files.\n");
        sb.append(ru
                ? "Write the explanation in Russian, but keep code, identifiers and "
                        + "technical terms like " + voice.termsExample() + " in their original form. "
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
