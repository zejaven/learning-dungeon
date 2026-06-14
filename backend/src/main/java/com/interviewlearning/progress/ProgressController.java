package com.interviewlearning.progress;

import com.interviewlearning.progress.ProgressDtos.BossAnswerRequest;
import com.interviewlearning.progress.ProgressDtos.BossAnswerResponse;
import com.interviewlearning.progress.ProgressDtos.MissionsRequest;
import com.interviewlearning.progress.ProgressDtos.TopicProgress;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reads and writes learner progress (missions, boss-fight answers, completion). */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressRepository progress;
    private final TopicRepository topics;

    public ProgressController(ProgressRepository progress, TopicRepository topics) {
        this.progress = progress;
        this.topics = topics;
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
        int total = topics.getTopic(topicId).map(t -> t.bossFight().size()).orElse(0);
        boolean completed = progress.recomputeCompletion(topicId, total);
        return new BossAnswerResponse(completed);
    }
}
