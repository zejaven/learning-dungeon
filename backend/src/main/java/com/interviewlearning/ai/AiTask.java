package com.interviewlearning.ai;

/** AI work categories mapped to provider-specific model and permission choices. */
public enum AiTask {
    ASSISTANT(false, false),
    EVALUATE(false, false),
    CLASSIFY(true, false),
    GENERATE_TOPIC(true, true),
    REGENERATE_VERSION(true, true),
    GENERATE_ATOMS(true, true);

    private final boolean writesFiles;
    private final boolean needsStrongModel;

    AiTask(boolean writesFiles, boolean needsStrongModel) {
        this.writesFiles = writesFiles;
        this.needsStrongModel = needsStrongModel;
    }

    public boolean writesFiles() {
        return writesFiles;
    }

    public boolean needsStrongModel() {
        return needsStrongModel;
    }
}
