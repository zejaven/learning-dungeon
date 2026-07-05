package com.interviewlearning.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.lesson.LessonDtos.AtomsResponse;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Reads {@code topics/<id>/learning-atoms.json} on each request (same
 * no-restart philosophy as {@link com.interviewlearning.topics.TopicRepository}).
 * Lenient at runtime: a missing or broken file just means "no lesson yet", so a
 * failed generation leaves the Generate button available instead of breaking
 * the topic. {@code LessonAtomsContractTest} is the strict validator.
 */
@Repository
public class LearningAtomsRepository {

    public static final String FILE_NAME = "learning-atoms.json";

    private static final Logger log = LoggerFactory.getLogger(LearningAtomsRepository.class);

    private final RepoPaths repoPaths;
    private final ObjectMapper mapper;

    public LearningAtomsRepository(RepoPaths repoPaths, ObjectMapper mapper) {
        this.repoPaths = repoPaths;
        this.mapper = mapper;
    }

    public boolean exists(String topicId) {
        return Files.exists(file(topicId));
    }

    /** Loads the atoms file with the sha-256 of its bytes (the progress scope token). */
    public Optional<AtomsResponse> load(String topicId) {
        Path path = file(topicId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            LearningAtoms atoms = mapper.readValue(bytes, LearningAtoms.class);
            if (atoms == null || atoms.atoms() == null || atoms.atoms().isEmpty()) {
                log.warn("Ignoring {}: no atoms", path);
                return Optional.empty();
            }
            return Optional.of(new AtomsResponse(sha256(bytes), atoms));
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to read {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private Path file(String topicId) {
        return repoPaths.topicsDir().resolve(topicId).resolve(FILE_NAME);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
