package com.interviewlearning.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Path-resolution safety of the topic asset endpoint. */
class TopicAssetControllerTest {

    private final Path topicsDir = Path.of("topics").toAbsolutePath();

    private Optional<Path> resolve(String id, String rel) {
        return TopicAssetController.resolveAsset(topicsDir, id, rel);
    }

    @Test
    void resolvesPlainImageInsideTopic() {
        Optional<Path> p = resolve("ndm-scalability", "/images/diagram.png");
        assertTrue(p.isPresent());
        assertEquals(topicsDir.resolve("ndm-scalability").resolve("images").resolve("diagram.png"),
                p.get());
    }

    @Test
    void rejectsTraversalInRelativePath() {
        assertTrue(resolve("hashmap", "/../../config/secret.yml").isEmpty());
        assertTrue(resolve("hashmap", "/images/../../other/images/x.png").isEmpty());
        assertTrue(resolve("hashmap", "/..%2Fx.png").isEmpty());
    }

    @Test
    void rejectsTraversalInTopicId() {
        assertTrue(resolve("../backend", "/images/x.png").isEmpty());
        assertTrue(resolve("a/b", "/images/x.png").isEmpty());
        assertTrue(resolve("a\\b", "/images/x.png").isEmpty());
        assertTrue(resolve("", "/images/x.png").isEmpty());
        assertTrue(resolve(null, "/images/x.png").isEmpty());
    }

    @Test
    void rejectsNonImageExtensions() {
        assertTrue(resolve("hashmap", "/quiz.yaml").isEmpty());
        assertTrue(resolve("hashmap", "/harness/Main.java").isEmpty());
        assertTrue(resolve("hashmap", "/images/noext").isEmpty());
        assertTrue(resolve("hashmap", "/").isEmpty());
    }

    @Test
    void rejectsBackslashesInRelativePath() {
        assertTrue(resolve("hashmap", "/images\\x.png").isEmpty());
    }

    @Test
    void acceptsAllAllowlistedExtensionsCaseInsensitively() {
        for (String name : new String[]{"a.png", "a.PNG", "a.jpg", "a.jpeg", "a.gif", "a.svg", "a.webp"}) {
            assertTrue(resolve("hashmap", "/images/" + name).isPresent(), name);
        }
    }
}
