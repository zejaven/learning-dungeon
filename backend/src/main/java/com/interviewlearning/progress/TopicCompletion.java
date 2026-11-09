package com.interviewlearning.progress;

/**
 * When a topic counts as passed. A pure function, pinned by
 * {@code TopicCompletionTest}, so the rule is not buried in SQL.
 *
 * <p>Boss Fight has always been the gate, and it still is: failing a question
 * again takes the mark away. Code tasks are an extra gate for topics that are
 * not passed yet. They never take a mark away: tasks generated for a topic
 * someone finished months ago must not repaint the map as unfinished.
 */
public final class TopicCompletion {

    private TopicCompletion() {
    }

    /**
     * @param bossDone      every Boss Fight question is currently passed
     * @param tasksTotal    the topic's code tasks
     * @param tasksSolved   how many of them are solved
     * @param wasCompleted  the topic was already passed before this answer
     */
    public static boolean decide(boolean bossDone, int tasksTotal, int tasksSolved, boolean wasCompleted) {
        if (!bossDone) {
            return false;
        }
        return wasCompleted || tasksSolved >= tasksTotal;
    }
}
