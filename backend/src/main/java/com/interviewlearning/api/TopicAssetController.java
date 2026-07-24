package com.interviewlearning.api;

import com.interviewlearning.config.RepoPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Serves image assets referenced by a topic's explanation markdown (by
 * convention {@code topics/<id>/images/*}). Only a small allowlist of image
 * extensions is served, so topic internals like quiz.yaml or harness/ can
 * never leak through this endpoint.
 */
@RestController
@RequestMapping("/api/topics")
public class TopicAssetController {

    private static final Logger log = LoggerFactory.getLogger(TopicAssetController.class);

    /** Explicit content types — Files.probeContentType is unreliable on Windows. */
    private static final Map<String, MediaType> CONTENT_TYPES = Map.of(
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "svg", MediaType.valueOf("image/svg+xml"),
            "webp", MediaType.valueOf("image/webp"));

    private final RepoPaths repoPaths;

    public TopicAssetController(RepoPaths repoPaths) {
        this.repoPaths = repoPaths;
    }

    @GetMapping("/{id}/assets/{*path}")
    public ResponseEntity<byte[]> asset(@PathVariable String id, @PathVariable String path) {
        Optional<Path> resolved = resolveAsset(repoPaths.topicsDir(), id, path);
        if (resolved.isEmpty() || !Files.isRegularFile(resolved.get())) {
            return ResponseEntity.notFound().build();
        }
        Path file = resolved.get();
        try {
            return ResponseEntity.ok()
                    .contentType(CONTENT_TYPES.get(extensionOf(file.getFileName().toString())))
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                    .body(Files.readAllBytes(file));
        } catch (IOException e) {
            log.warn("Failed to read asset {}: {}", file, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Resolves a topic asset path defensively: the topic id must be a plain
     * folder name, the relative path must stay inside the topic folder after
     * normalization, and the extension must be on the image allowlist.
     * Returns empty on any violation; existence is checked by the caller.
     */
    static Optional<Path> resolveAsset(Path topicsDir, String id, String relPath) {
        if (id == null || id.isBlank() || id.contains("/") || id.contains("\\") || id.contains("..")) {
            return Optional.empty();
        }
        // The {*path} pattern captures the leading slash; tolerate both forms.
        String rel = relPath == null ? "" : relPath.replaceFirst("^/+", "");
        if (rel.isBlank() || rel.contains("\\") || rel.contains("..")) {
            return Optional.empty();
        }
        if (!CONTENT_TYPES.containsKey(extensionOf(rel))) {
            return Optional.empty();
        }
        Path base = topicsDir.toAbsolutePath().normalize();
        Path topicDir = base.resolve(id).normalize();
        if (!topicDir.getParent().equals(base)) {
            return Optional.empty();
        }
        Path target = topicDir.resolve(rel).normalize();
        if (!target.startsWith(topicDir) || target.equals(topicDir)) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
