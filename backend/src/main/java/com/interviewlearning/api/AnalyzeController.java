package com.interviewlearning.api;

import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.structure.StructureAnalyzer;
import com.interviewlearning.structure.StructureDtos.ClassGraph;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Analyzes a structural (design-pattern) topic's project: compiles all files for
 * validity (no execution) and parses them into a class-relationship graph used to
 * draw the class diagram and check structural missions on the frontend.
 */
@RestController
@RequestMapping("/api/analyze")
public class AnalyzeController {

    private final JavaCodeRunner runner;
    private final StructureAnalyzer analyzer;

    public AnalyzeController(JavaCodeRunner runner, StructureAnalyzer analyzer) {
        this.runner = runner;
        this.analyzer = analyzer;
    }

    public record AnalyzeRequest(String topicId, List<ProjectFile> files) {
    }

    /**
     * @param ok    true when every file compiled
     * @param error compiler diagnostics when {@code ok} is false, else null
     * @param graph the class graph (best-effort — built even if compilation failed)
     */
    public record AnalyzeResponse(boolean ok, String error, ClassGraph graph) {
    }

    @PostMapping
    public AnalyzeResponse analyze(@RequestBody AnalyzeRequest request) {
        List<ProjectFile> files = request.files() == null ? List.of() : request.files();
        String error = runner.compileFiles(files);
        ClassGraph graph = analyzer.analyze(files);
        return new AnalyzeResponse(error == null, error, graph);
    }
}
