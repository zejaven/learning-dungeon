package com.interviewlearning.progress;

import com.interviewlearning.progress.ProgressDtos.AssistantQa;
import com.interviewlearning.progress.ProgressDtos.AssistantQaRequest;
import com.interviewlearning.progress.ProgressDtos.BossAnswerRequest;
import com.interviewlearning.progress.ProgressDtos.BossAnswerResponse;
import com.interviewlearning.progress.ProgressDtos.MissionsRequest;
import com.interviewlearning.progress.ProgressDtos.TopicProgress;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reads and writes learner progress (missions, boss-fight answers, completion). */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressRepository progress;
    private final TopicCompletionService completion;

    public ProgressController(ProgressRepository progress, TopicCompletionService completion) {
        this.progress = progress;
        this.completion = completion;
    }

    @GetMapping("/{topicId}")
    public TopicProgress get(@PathVariable String topicId) {
        return new TopicProgress(
                progress.missions(topicId),
                progress.currentBossAnswers(topicId),
                progress.isCompleted(topicId));
    }

    @PostMapping("/{topicId}/missions")
    public void missions(@PathVariable String topicId, @RequestBody MissionsRequest req) {
        if (req.missionIds() != null && !req.missionIds().isEmpty()) {
            progress.completeMissions(topicId, req.missionIds());
        }
    }

    @PostMapping("/{topicId}/boss-fight")
    public BossAnswerResponse bossFight(@PathVariable String topicId,
                                        @RequestBody BossAnswerRequest req) {
        progress.saveBossAnswer(topicId, req);
        return new BossAnswerResponse(completion.recompute(topicId));
    }

    @GetMapping("/{topicId}/assistant")
    public List<AssistantQa> assistantHistory(@PathVariable String topicId) {
        return progress.assistantHistory(topicId);
    }

    @PostMapping("/{topicId}/assistant")
    public AssistantQa saveAssistant(@PathVariable String topicId,
                                     @RequestBody AssistantQaRequest req) {
        return progress.saveAssistantQa(topicId, req);
    }

    @DeleteMapping("/{topicId}/assistant/{id}")
    public void deleteAssistant(@PathVariable String topicId, @PathVariable long id) {
        progress.deleteAssistantQa(topicId, id);
    }
}
