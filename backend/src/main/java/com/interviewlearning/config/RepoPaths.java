package com.interviewlearning.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * Resolves filesystem locations the backend needs, all relative to the
 * repository root. Kept in one place so dev (bootRun) and packaged runs behave
 * the same.
 */
@Component
public class RepoPaths {

    private static final Logger log = LoggerFactory.getLogger(RepoPaths.class);

    private final Path repoRoot;

    public RepoPaths(@Value("${app.repo-root:}") String configuredRoot) {
        Path root = (configuredRoot == null || configuredRoot.isBlank())
                ? Path.of(System.getProperty("user.dir"))
                : Path.of(configuredRoot);
        this.repoRoot = root.toAbsolutePath().normalize();
    }

    @PostConstruct
    void logResolved() {
        log.info("Repo root: {}", repoRoot);
        log.info("Topics dir: {}", topicsDir());
        log.info("visual-runtime jar: {}",
                visualRuntimeJar().map(Path::toString).orElse("<not built yet>"));
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public Path topicsDir() {
        return repoRoot.resolve("topics");
    }

    public Path promptsDir() {
        return repoRoot.resolve("prompts");
    }

    /** Locates the most recent visual-runtime*.jar under visual-runtime/build/libs. */
    public Optional<Path> visualRuntimeJar() {
        Path libs = repoRoot.resolve("visual-runtime").resolve("build").resolve("libs");
        if (!Files.isDirectory(libs)) {
            return Optional.empty();
        }
        try (var stream = Files.list(libs)) {
            return stream
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("visual-runtime") && n.endsWith(".jar");
                    })
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        } catch (IOException e) {
            log.warn("Failed to list {}: {}", libs, e.getMessage());
            return Optional.empty();
        }
    }
}
