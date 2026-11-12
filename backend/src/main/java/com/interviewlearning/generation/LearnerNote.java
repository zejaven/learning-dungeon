package com.interviewlearning.generation;

/**
 * The optional "note for the AI" a generation row carries: what the learner
 * wants from this run (a focus, things to skip, a missing case). It goes last in
 * the prompt, after the contract, so it reads as a request within the rules.
 */
public final class LearnerNote {

    private LearnerNote() {
    }

    /** Appends the note under the heading code tasks and lessons already use; nothing when blank. */
    public static void append(StringBuilder sb, String note, String howToTreat) {
        if (note == null || note.isBlank()) {
            return;
        }
        sb.append("\n---\n\nTHE LEARNER ASKS FOR (").append(howToTreat).append("):\n")
                .append(note.strip()).append("\n");
    }
}
