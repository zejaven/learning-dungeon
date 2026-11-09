package com.interviewlearning.progress;

import com.interviewlearning.codetask.CodeTaskDtos.CodeTask;
import com.interviewlearning.codetask.CodeTaskDtos.TaskProgress;
import com.interviewlearning.codetask.CodeTaskProgressRepository;
import com.interviewlearning.codetask.CodeTaskRepository;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Recomputes whether a topic is passed from everything that gates it: its Boss
 * Fight and its code tasks (see {@link TopicCompletion}). Called after a boss
 * answer and after a solved task, whichever closes the topic.
 */
@Service
public class TopicCompletionService {

    private final ProgressRepository progress;
    private final TopicRepository topics;
    private final CodeTaskRepository tasks;
    private final CodeTaskProgressRepository taskProgress;

    public TopicCompletionService(ProgressRepository progress, TopicRepository topics,
                                  CodeTaskRepository tasks, CodeTaskProgressRepository taskProgress) {
        this.progress = progress;
        this.topics = topics;
        this.tasks = tasks;
        this.taskProgress = taskProgress;
    }

    public boolean recompute(String topicId) {
        int bossTotal = topics.getTopic(topicId).map(t -> t.bossFight().size()).orElse(0);
        List<CodeTask> list = tasks.list(topicId);
        Map<String, TaskProgress> saved = list.isEmpty() ? Map.of() : taskProgress.forTopic(topicId);
        int solved = (int) list.stream()
                .filter(t -> saved.getOrDefault(t.id(), TaskProgress.NONE).solved())
                .count();
        return progress.recomputeCompletion(topicId, bossTotal, list.size(), solved);
    }

    public boolean isCompleted(String topicId) {
        return progress.isCompleted(topicId);
    }
}
