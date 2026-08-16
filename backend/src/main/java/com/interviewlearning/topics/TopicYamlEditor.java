package com.interviewlearning.topics;

import com.interviewlearning.lang.ContentLanguages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds a content language to a topic.yaml {@code languages:} list by editing
 * that one line, leaving every other byte alone.
 *
 * <p>Deliberately textual: topic.yaml files are hand-maintained UTF-8 with
 * comments, quoting and block scalars ({@code summary: >}), all of which a
 * snakeyaml load-and-dump round trip would destroy. Anything this editor does
 * not recognise is refused rather than guessed at.
 */
@Component
public class TopicYamlEditor {

    private static final Logger log = LoggerFactory.getLogger(TopicYamlEditor.class);

    /** {@code languages: [en, ru]} — the shape every existing topic uses. */
    private static final Pattern FLOW_LIST = Pattern.compile("^([ \\t]*)languages:[ \\t]*\\[(.*)\\][ \\t]*$");
    /** {@code languages:} followed by {@code   - en} item lines. */
    private static final Pattern BLOCK_HEAD = Pattern.compile("^([ \\t]*)languages:[ \\t]*$");
    private static final Pattern BLOCK_ITEM = Pattern.compile("^([ \\t]*)-[ \\t]*(\\S+)[ \\t]*$");
    private static final Pattern ID_LINE = Pattern.compile("^id:[ \\t]*\\S.*$");

    /**
     * Declares {@code lang} in the topic's {@code languages:}.
     *
     * @return true when the file now declares it (including when it already did)
     */
    public boolean addLanguage(Path topicYaml, String lang) throws IOException {
        String code = ContentLanguages.normalizeCode(lang);
        if (code == null) {
            log.warn("Refusing to declare unsupported language '{}' in {}", lang, topicYaml);
            return false;
        }
        String content = Files.readString(topicYaml, StandardCharsets.UTF_8);
        List<String> lines = splitKeepingLineEndings(content);

        int keyIndex = indexOfLanguagesKey(lines);
        if (keyIndex == DUPLICATE_KEY) {
            log.warn("Refusing to edit {}: more than one `languages:` key", topicYaml);
            return false;
        }
        if (keyIndex < 0) {
            return writeIfChanged(topicYaml, content, insertKey(lines, code));
        }
        String edited = editExistingKey(lines, keyIndex, code, topicYaml);
        return edited != null && writeIfChanged(topicYaml, content, edited);
    }

    private static final int DUPLICATE_KEY = -2;

    private int indexOfLanguagesKey(List<String> lines) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = stripEol(lines.get(i));
            if (FLOW_LIST.matcher(line).matches() || BLOCK_HEAD.matcher(line).matches()) {
                if (found >= 0) {
                    return DUPLICATE_KEY; // refuse rather than guess which one to edit
                }
                found = i;
            }
        }
        return found;
    }

    /**
     * No {@code languages:} key: the topic implicitly carries the legacy pair, so
     * only a language outside that pair needs the key spelled out. Unreachable
     * while the registry holds exactly the legacy languages.
     */
    private String insertKey(List<String> lines, String code) {
        List<String> declared = new ArrayList<>(ContentLanguages.LEGACY_DEFAULT);
        if (declared.contains(code)) {
            return String.join("", lines); // already covered by the default
        }
        declared.add(code);
        int at = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (ID_LINE.matcher(stripEol(lines.get(i))).matches()) {
                at = i + 1;
                break;
            }
        }
        String eol = lineEnding(lines, at);
        lines.add(at, "languages: [" + String.join(", ", declared) + "]" + eol);
        return String.join("", lines);
    }

    /** Returns the edited file, or null when the shape is not one we handle. */
    private String editExistingKey(List<String> lines, int keyIndex, String code, Path topicYaml) {
        String raw = lines.get(keyIndex);
        String line = stripEol(raw);
        String eol = raw.substring(line.length());

        Matcher flow = FLOW_LIST.matcher(line);
        if (flow.matches()) {
            List<String> codes = new ArrayList<>();
            for (String part : flow.group(2).split(",")) {
                String trimmed = part.trim().replaceAll("^['\"]|['\"]$", "");
                if (!trimmed.isEmpty()) {
                    codes.add(trimmed);
                }
            }
            if (codes.contains(code)) {
                return String.join("", lines);
            }
            codes.add(code);
            lines.set(keyIndex, flow.group(1) + "languages: [" + String.join(", ", codes) + "]" + eol);
            return String.join("", lines);
        }

        // Block list: append an item after the last existing one.
        int last = keyIndex;
        String itemIndent = null;
        for (int i = keyIndex + 1; i < lines.size(); i++) {
            Matcher item = BLOCK_ITEM.matcher(stripEol(lines.get(i)));
            if (!item.matches()) {
                break;
            }
            if (item.group(2).equals(code)) {
                return String.join("", lines);
            }
            itemIndent = item.group(1);
            last = i;
        }
        if (itemIndent == null) {
            log.warn("Refusing to edit {}: `languages:` has no list items", topicYaml);
            return null;
        }
        lines.add(last + 1, itemIndent + "- " + code + lineEnding(lines, last));
        return String.join("", lines);
    }

    private boolean writeIfChanged(Path topicYaml, String before, String after) throws IOException {
        if (after == null) {
            return false;
        }
        if (after.equals(before)) {
            return true; // already declared
        }
        // Temp file + atomic move: a half-written topic.yaml would break the topic.
        Path tmp = topicYaml.resolveSibling(topicYaml.getFileName() + ".tmp");
        Files.writeString(tmp, after, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, topicYaml, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, topicYaml, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    /** Splits into lines that still carry their own line ending. */
    private static List<String> splitKeepingLineEndings(String content) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines.add(content.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < content.length()) {
            lines.add(content.substring(start));
        }
        return lines;
    }

    private static String stripEol(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) {
            end--;
        }
        return line.substring(0, end);
    }

    /** The line ending used around {@code index}, defaulting to the file's first. */
    private static String lineEnding(List<String> lines, int index) {
        for (int i = Math.min(index, lines.size() - 1); i >= 0; i--) {
            String line = lines.get(i);
            String eol = line.substring(stripEol(line).length());
            if (!eol.isEmpty()) {
                return eol;
            }
        }
        return System.lineSeparator();
    }
}
