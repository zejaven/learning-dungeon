package com.interviewlearning.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.lesson.LessonDtos.LocalizedList;
import com.interviewlearning.lesson.LessonDtos.Option;
import com.interviewlearning.lesson.LessonDtos.Pair;
import com.interviewlearning.lesson.LessonDtos.Step;
import com.interviewlearning.topics.TopicDtos.Localized;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract check for every {@code topics/<id>/learning-atoms.json}. One dynamic
 * test per topic that has the file, so a failure names the offending topic. It
 * parses with the SAME Jackson mapping the app uses (strictly — a parse error
 * fails the test, unlike the lenient runtime loader which reports "no lesson"),
 * then checks the structure the lesson UI relies on: bilingual fields, the
 * per-type exercise payloads, and unique ids.
 *
 * <p>Run just this: {@code ./gradlew :backend:test --tests "*LessonAtomsContractTest"}.
 */
class LessonAtomsContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern KEBAB = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Set<String> CHOICE_TYPES = Set.of("multiple_choice", "predict_output", "spot_bug");
    private static final Set<String> ALL_TYPES = Set.of(
            "multiple_choice", "true_false", "fill_blank", "word_bank",
            "sort_steps", "match_pairs", "predict_output", "spot_bug");

    @TestFactory
    Stream<DynamicTest> everyAtomsFileMatchesTheContract() throws IOException {
        Path topicsDir = topicsDir();
        try (Stream<Path> dirs = Files.list(topicsDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve(LearningAtomsRepository.FILE_NAME)))
                    .sorted()
                    .map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> validate(dir)))
                    // Collect eagerly so the directory stream can close.
                    .toList()
                    .stream();
        }
    }

    private void validate(Path dir) {
        String name = dir.getFileName().toString();
        List<String> errs = new ArrayList<>();

        LearningAtoms atoms = parse(dir.resolve(LearningAtomsRepository.FILE_NAME), errs);
        if (atoms != null) {
            if (atoms.schemaVersion() != 1) {
                errs.add("schemaVersion must be 1, got " + atoms.schemaVersion());
            }
            if (!name.equals(atoms.topicId())) {
                errs.add("topicId '" + atoms.topicId() + "' must equal folder name '" + name + "'");
            }
            List<Atom> list = atoms.atoms() == null ? List.of() : atoms.atoms();
            if (list.size() < 8 || list.size() > 15) {
                errs.add("must have 8-15 atoms, got " + list.size());
            }
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                validateAtom(list.get(i), "atoms[" + i + "]", ids, errs);
            }
        }

        if (!errs.isEmpty()) {
            fail("learning-atoms.json of '" + name + "' has " + errs.size() + " contract violation(s):\n  - "
                    + String.join("\n  - ", errs));
        }
    }

    private void validateAtom(Atom atom, String where, Set<String> ids, List<String> errs) {
        kebabId(atom.id(), where + ".id", ids, errs);
        localized(atom.title(), where + ".title", errs);
        localized(atom.summary(), where + ".summary", errs);
        List<Exercise> discovery = atom.discovery() == null ? List.of() : atom.discovery();
        List<Exercise> practice = atom.practice() == null ? List.of() : atom.practice();
        if (discovery.isEmpty()) {
            errs.add(where + ": needs at least 1 discovery exercise");
        }
        if (practice.size() < 2) {
            errs.add(where + ": needs at least 2 practice exercises, got " + practice.size());
        }
        for (int i = 0; i < discovery.size(); i++) {
            validateExercise(discovery.get(i), where + ".discovery[" + i + "]", ids, errs);
        }
        for (int i = 0; i < practice.size(); i++) {
            validateExercise(practice.get(i), where + ".practice[" + i + "]", ids, errs);
        }
    }

    private void validateExercise(Exercise ex, String where, Set<String> ids, List<String> errs) {
        kebabId(ex.id(), where + ".id", ids, errs);
        String type = ex.type() == null ? "" : ex.type();
        if (!ALL_TYPES.contains(type)) {
            errs.add(where + ": unknown type '" + type + "'");
            return;
        }
        localized(ex.prompt(), where + ".prompt", errs);
        if (ex.feedback() == null) {
            errs.add(where + ".feedback: missing");
        } else {
            localized(ex.feedback().correct(), where + ".feedback.correct", errs);
            localized(ex.feedback().incorrect(), where + ".feedback.incorrect", errs);
        }
        if (ex.mermaid() != null) {
            localized(ex.mermaid(), where + ".mermaid", errs);
        }
        if (ex.reveal() != null) {
            localized(ex.reveal(), where + ".reveal", errs);
        }

        switch (type) {
            case "multiple_choice", "predict_output", "spot_bug" -> validateOptions(ex, where, errs);
            case "true_false" -> {
                if (ex.answer() == null) errs.add(where + ": true_false needs 'answer'");
            }
            case "fill_blank" -> validateFillBlank(ex, where, errs);
            case "word_bank" -> {
                localizedList(ex.tokens(), where + ".tokens", errs);
                // distractors may be empty but must be present per-language if given.
            }
            case "sort_steps" -> validateSteps(ex.steps(), where, errs);
            case "match_pairs" -> validatePairs(ex.pairs(), where, errs);
            default -> {
            }
        }
        if (CHOICE_TYPES.contains(type) && !"multiple_choice".equals(type) && blank(ex.code())) {
            errs.add(where + ": " + type + " needs a 'code' snippet");
        }
    }

    private void validateOptions(Exercise ex, String where, List<String> errs) {
        List<Option> options = ex.options();
        if (options == null || options.size() < 2) {
            errs.add(where + ": needs at least 2 options");
            return;
        }
        Set<String> optIds = new HashSet<>();
        long correct = 0;
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            String ow = where + ".options[" + i + "]";
            if (blank(o.id())) errs.add(ow + ": missing id");
            else if (!optIds.add(o.id())) errs.add(ow + ": duplicate id '" + o.id() + "'");
            localized(o.text(), ow + ".text", errs);
            if (o.correct()) correct++;
            else if (o.feedback() != null) localized(o.feedback(), ow + ".feedback", errs);
        }
        if (correct != 1) {
            errs.add(where + ": exactly one option must be correct, got " + correct);
        }
    }

    private void validateFillBlank(Exercise ex, String where, List<String> errs) {
        localized(ex.text(), where + ".text", errs);
        // Accepted answers come from `blanks` (one entry per ___, supports
        // several blanks) or the legacy single-blank `answers`.
        List<LocalizedList> blanks = ex.blanks();
        int expected;
        if (blanks != null && !blanks.isEmpty()) {
            expected = blanks.size();
            for (int i = 0; i < blanks.size(); i++) {
                localizedList(blanks.get(i), where + ".blanks[" + i + "]", errs);
            }
        } else if (ex.answers() != null) {
            expected = 1;
            localizedList(ex.answers(), where + ".answers", errs);
        } else {
            errs.add(where + ": fill_blank needs 'blanks' or 'answers'");
            return;
        }
        if (ex.text() != null) {
            if (countBlanks(ex.text().en()) != expected) {
                errs.add(where + ".text.en: must contain exactly " + expected + " ___");
            }
            if (countBlanks(ex.text().ru()) != expected) {
                errs.add(where + ".text.ru: must contain exactly " + expected + " ___");
            }
        }
    }

    private void validateSteps(List<Step> steps, String where, List<String> errs) {
        if (steps == null || steps.size() < 3) {
            errs.add(where + ": sort_steps needs at least 3 steps");
            return;
        }
        Set<String> stepIds = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (blank(s.id())) errs.add(where + ".steps[" + i + "]: missing id");
            else if (!stepIds.add(s.id())) errs.add(where + ".steps[" + i + "]: duplicate id '" + s.id() + "'");
            localized(s.text(), where + ".steps[" + i + "].text", errs);
        }
    }

    private void validatePairs(List<Pair> pairs, String where, List<String> errs) {
        if (pairs == null || pairs.size() < 3 || pairs.size() > 5) {
            errs.add(where + ": match_pairs needs 3-5 pairs, got " + (pairs == null ? 0 : pairs.size()));
            return;
        }
        Set<String> pairIds = new HashSet<>();
        for (int i = 0; i < pairs.size(); i++) {
            Pair p = pairs.get(i);
            if (blank(p.id())) errs.add(where + ".pairs[" + i + "]: missing id");
            else if (!pairIds.add(p.id())) errs.add(where + ".pairs[" + i + "]: duplicate id '" + p.id() + "'");
            localized(p.left(), where + ".pairs[" + i + "].left", errs);
            localized(p.right(), where + ".pairs[" + i + "].right", errs);
        }
    }

    // --- helpers -----------------------------------------------------------

    /** Ids are kebab-case and unique across the whole file (atoms + exercises + saved answers key off them). */
    private void kebabId(String id, String where, Set<String> ids, List<String> errs) {
        if (blank(id)) {
            errs.add(where + ": missing");
        } else if (!KEBAB.matcher(id).matches()) {
            errs.add(where + ": '" + id + "' is not kebab-case");
        } else if (!ids.add(id)) {
            errs.add(where + ": duplicate id '" + id + "'");
        }
    }

    private void localized(Localized v, String where, List<String> errs) {
        if (v == null) {
            errs.add(where + ": missing");
            return;
        }
        if (blank(v.en())) errs.add(where + ".en: missing");
        if (blank(v.ru())) errs.add(where + ".ru: missing");
    }

    private void localizedList(LocalizedList v, String where, List<String> errs) {
        if (v == null) {
            errs.add(where + ": missing");
            return;
        }
        if (v.en() == null || v.en().isEmpty() || v.en().stream().anyMatch(LessonAtomsContractTest::blank)) {
            errs.add(where + ".en: must be a non-empty list of non-blank strings");
        }
        if (v.ru() == null || v.ru().isEmpty() || v.ru().stream().anyMatch(LessonAtomsContractTest::blank)) {
            errs.add(where + ".ru: must be a non-empty list of non-blank strings");
        }
    }

    private static int countBlanks(String s) {
        if (s == null) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf("___", idx)) >= 0) {
            count++;
            idx += 3;
        }
        return count;
    }

    private static boolean blank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    private LearningAtoms parse(Path path, List<String> errs) {
        try {
            return JSON.readValue(Files.readAllBytes(path), LearningAtoms.class);
        } catch (IOException e) {
            errs.add("invalid JSON — " + firstLine(e.getMessage()));
            return null;
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "(no message)";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    /** Walks up from the working dir until a folder containing topics/ is found. */
    private static Path topicsDir() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path t = p.resolve("topics");
            if (Files.isDirectory(t)) return t;
            p = p.getParent();
        }
        throw new IllegalStateException("Could not locate topics/ from " + Paths.get("").toAbsolutePath());
    }
}
