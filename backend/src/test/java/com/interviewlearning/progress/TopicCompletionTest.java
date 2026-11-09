package com.interviewlearning.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins {@link TopicCompletion}: Boss Fight gates everything, tasks gate only what is not passed yet. */
class TopicCompletionTest {

    @Test
    void aTopicWithoutTasksIsPassedByItsBossFight() {
        assertTrue(TopicCompletion.decide(true, 0, 0, false));
        assertFalse(TopicCompletion.decide(false, 0, 0, false));
    }

    @Test
    void openTasksKeepANewTopicUnpassed() {
        assertFalse(TopicCompletion.decide(true, 2, 1, false));
        assertTrue(TopicCompletion.decide(true, 2, 2, false));
    }

    @Test
    void solvedTasksDoNotReplaceTheBossFight() {
        assertFalse(TopicCompletion.decide(false, 2, 2, false));
    }

    @Test
    void tasksAddedLaterDoNotTakeThePassAway() {
        assertTrue(TopicCompletion.decide(true, 3, 0, true));
    }

    @Test
    void failingTheBossFightAgainStillTakesThePassAway() {
        assertFalse(TopicCompletion.decide(false, 0, 0, true));
        assertFalse(TopicCompletion.decide(false, 2, 2, true));
    }
}
