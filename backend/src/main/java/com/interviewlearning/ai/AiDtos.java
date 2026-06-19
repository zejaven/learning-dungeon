package com.interviewlearning.ai;

import java.util.List;

/** DTOs for AI provider selection and status. */
public final class AiDtos {

    private AiDtos() {
    }

    public record AiProviderStatus(
            String id,
            String label,
            boolean available,
            String version,
            String error,
            String downloadUrl,
            String assistantModel,
            String generateModel
    ) {
    }

    public record AiProvidersResponse(String defaultProvider, List<AiProviderStatus> providers) {
    }
}
