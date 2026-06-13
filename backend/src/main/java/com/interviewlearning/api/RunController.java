package com.interviewlearning.api;

import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.runner.RunResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/run")
public class RunController {

    private final JavaCodeRunner runner;

    public RunController(JavaCodeRunner runner) {
        this.runner = runner;
    }

    public record RunRequest(String topicId, String code) {
    }

    @PostMapping
    public RunResult run(@RequestBody RunRequest request) {
        return runner.run(request.code());
    }
}
