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
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The topic folders a topic generation adds. The run scaffolds
 * {@code topics/<new-id>/} file by file, so:
 * <ul>
 *   <li>stopping it halfway leaves a topic the contract test rejects and the tree
 *       shows broken, and {@link #deleteAdded} removes it;</li>
 *   <li>a finished run is committed by {@link GitAutoCommit} through {@link #added}.</li>
 * </ul>
 * Both act only while no other topic generation runs: a new folder might then be
 * that run's, half-written.
 */
public final class NewTopicDirs {

    private static final Logger log = LoggerFactory.getLogger(NewTopicDirs.class);

    private final Path topicsDir;
    private final String key;
    private final Supplier<List<String>> runningKeys;
    private final Set<String> before;

    private NewTopicDirs(Path topicsDir, String key, Supplier<List<String>> runningKeys) {
        this.topicsDir = topicsDir;
        this.key = key;
        this.runningKeys = runningKeys;
        this.before = names(topicsDir);
    }

    /** Topic generations are keyed {@code catalog:<id>} or {@code add-topic:<domain>}. */
    public static boolean isTopicKey(String key) {
        return key.startsWith("catalog:") || key.startsWith("add-topic:");
    }

    /**
     * Records the topic folders now, for {@code key}'s run.
     *
     * @param runningKeys the keys of the tasks running when the run ends
     */
    public static NewTopicDirs snapshot(Path topicsDir, String key, Supplier<List<String>> runningKeys) {
        return new NewTopicDirs(topicsDir, key, runningKeys);
    }

    public Path topicsDir() {
        return topicsDir;
    }

    /**
     * The folder names that appeared since the snapshot, or empty when another
     * topic generation is running and they cannot be told apart.
     */
    public Optional<Set<String>> added() {
        List<String> others = runningKeys.get().stream()
                .filter(k -> !k.equals(key) && isTopicKey(k))
                .toList();
        Set<String> added = names(topicsDir);
        added.removeAll(before);
        if (!others.isEmpty() && !added.isEmpty()) {
            log.warn("{}: cannot tell whether {} is its own, since {} is also generating a topic.",
                    key, added, others);
            return Optional.empty();
        }
        return Optional.of(added);
    }

    /** Undoes a stopped run: deletes the folders it added. */
    public void deleteAdded() {
        for (String name : added().orElse(Set.of())) {
            deleteRecursively(topicsDir.resolve(name));
            log.info("Stopped {}: deleted the half-written topics/{}.", key, name);
        }
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
