package com.interviewlearning.api;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiDtos.AiProvidersResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides AI CLI availability and default-provider selection to the frontend. */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiCliService ai;

    public AiController(AiCliService ai) {
        this.ai = ai;
    }

    @GetMapping("/providers")
    public AiProvidersResponse providers() {
        return ai.providers();
    }
}
