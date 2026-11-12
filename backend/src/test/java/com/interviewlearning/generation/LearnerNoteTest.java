package com.interviewlearning.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnerNoteTest {

    @Test
    void aNoteGoesLastUnderItsHeading() {
        StringBuilder sb = new StringBuilder("CONTRACT");
        LearnerNote.append(sb, "  keep the basics short\n", "follow it where it fits");
        assertTrue(sb.toString().endsWith(
                "THE LEARNER ASKS FOR (follow it where it fits):\nkeep the basics short\n"), sb.toString());
    }

    @Test
    void aBlankNoteAddsNothing() {
        StringBuilder sb = new StringBuilder("CONTRACT");
        LearnerNote.append(sb, null, "x");
        LearnerNote.append(sb, "   ", "x");
        assertEquals("CONTRACT", sb.toString());
    }
}
