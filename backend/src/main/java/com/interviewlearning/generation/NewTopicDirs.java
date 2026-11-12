package com.interviewlearning.generation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Undoes a stopped topic generation: the run scaffolds {@code topics/<new-id>/}
 * file by file, so stopping it halfway leaves a topic the contract test rejects
 * and the tree shows broken. The folders that did not exist when the run started
 * are deleted, unless another topic generation is running, since the new folder
 * might then be that run's.
 */
public final class NewTopicDirs {

    private static final Logger log = LoggerFactory.getLogger(NewTopicDirs.class);

    private NewTopicDirs() {
    }

    /** Topic generations are keyed {@code catalog:<id>} or {@code add-topic:<domain>}. */
    public static boolean isTopicKey(String key) {
        return key.startsWith("catalog:") || key.startsWith("add-topic:");
    }

    /**
     * Records the topic folders now and returns the cleanup for {@code key}'s run.
     *
     * @param runningKeys the keys of the tasks running when the cleanup fires
     */
    public static Runnable snapshot(Path topicsDir, String key, Supplier<List<String>> runningKeys) {
        Set<String> before = names(topicsDir);
        return () -> {
            List<String> others = runningKeys.get().stream()
                    .filter(k -> !k.equals(key) && isTopicKey(k))
                    .toList();
            Set<String> added = names(topicsDir);
            added.removeAll(before);
            if (added.isEmpty()) {
                return;
            }
            if (!others.isEmpty()) {
                log.warn("Stopped {}: left {} in place, since {} is also generating a topic.", key, added, others);
                return;
            }
            for (String name : added) {
                deleteRecursively(topicsDir.resolve(name));
                log.info("Stopped {}: deleted the half-written topics/{}.", key, name);
            }
        };
    }

    private static Set<String> names(Path topicsDir) {
        if (!Files.isDirectory(topicsDir)) {
            return new HashSet<>();
        }
        try (Stream<Path> s = Files.list(topicsDir)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
