package com.interviewlearning.api;

import com.interviewlearning.progress.ProgressRepository;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicDtos.TopicSummary;
import com.interviewlearning.topics.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private static final Logger log = LoggerFactory.getLogger(TopicController.class);

    private final TopicRepository repository;
    private final ProgressRepository progress;

    public TopicController(TopicRepository repository, ProgressRepository progress) {
        this.repository = repository;
        this.progress = progress;
    }

    @GetMapping
    public List<TopicSummary> list() {
        Set<String> done = completedIds();
        return repository.listTopics().stream()
                .map(s -> done.contains(s.id())
                        ? new TopicSummary(s.id(), s.title(), s.category(), s.type(), s.summary(),
                                true, s.categoryId(), s.categoryName(), s.difficulty(), s.catalogId(), s.mode(),
                                s.aiProvider(), s.aiModel(), s.hasAtoms())
                        : s)
                .toList();
    }

    /** Completion flags are a nice-to-have; never let a DB hiccup break browsing. */
    private Set<String> completedIds() {
        try {
            return progress.completedTopicIds();
        } catch (RuntimeException e) {
            log.warn("Could not read completed topics: {}", e.getMessage());
            return Set.of();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TopicDetail> get(@PathVariable String id) {
        return repository.getTopic(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
