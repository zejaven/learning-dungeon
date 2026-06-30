package visual;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A teaching model of the Java Stream API that makes the difference between
 * intermediate (pipeline) operations and terminal operations visible.
 *
 * <p>Intermediate operations ({@code filter}, {@code map}, {@code peek},
 * {@code distinct}, {@code limit}) only append a stage to the pipeline and emit a
 * {@code STREAM_PIPELINE_OP} event &mdash; nothing is processed yet (laziness).
 * A terminal operation ({@code collectToList}, {@code forEach}, {@code count},
 * {@code anyMatch}, {@code findFirst}, {@code reduce}) starts execution: each
 * source element is pulled through the whole pipeline one at a time, the terminal
 * produces a single result, and the stream is marked consumed.
 *
 * <p>The model keeps {@code visual-runtime} dependency-free and hand-builds a
 * {@link LinkedHashMap} state for deterministic ordering.
 */
public class VisualStream<T> {

    private final String name;
    private final List<Object> source;
    private final List<Stage> stages;

    private boolean consumed;
    private boolean shortCircuited;
    private String phase = "building";

    // Terminal display + per-run cursor (rebuilt for every state snapshot).
    private String terminalOp = "";
    private String terminalLabel = "";
    private String result = "";
    private int currentIndex = -1;
    private int currentStage = -2; // -2 none, -1 entering, 0..n-1 stage, n terminal
    private String currentValue = "";
    private final List<String> output = new ArrayList<>();
    private final List<String> status; // per source element

    private VisualStream(String name, List<Object> source, List<Stage> stages) {
        this.name = name;
        this.source = source;
        this.stages = stages;
        this.status = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            status.add("pending");
        }
    }

    @SafeVarargs
    public static <T> VisualStream<T> of(String name, T... elements) {
        List<Object> src = new ArrayList<>();
        for (T e : elements) {
            src.add(e);
        }
        VisualStream<T> stream = new VisualStream<>(name, src, new ArrayList<>());
        Trace.event("STREAM_SOURCE",
                "Source stream " + name + " holds " + render(src) + ". No operation has run yet.",
                "Исходный стрим " + name + " содержит " + render(src) + ". Пока ничего не выполнялось.",
                List.of("source"), stream.state());
        return stream;
    }

    // --- Intermediate (pipeline) operations: lazy, only append a stage. ---

    public VisualStream<T> filter(String label, Predicate<? super T> predicate) {
        Stage st = new Stage(label, "filter");
        st.predicate = asObjectPredicate(predicate);
        return chain(st);
    }

    public <R> VisualStream<R> map(String label, Function<? super T, ? extends R> mapper) {
        Stage st = new Stage(label, "map");
        st.mapper = asObjectFunction(mapper);
        return chainTyped(st);
    }

    public VisualStream<T> peek(String label, Consumer<? super T> action) {
        Stage st = new Stage(label, "peek");
        st.peeker = asObjectConsumer(action);
        return chain(st);
    }

    public VisualStream<T> distinct(String label) {
        return chain(new Stage(label, "distinct"));
    }

    public VisualStream<T> limit(String label, long maxSize) {
        Stage st = new Stage(label, "limit");
        st.limit = maxSize;
        return chain(st);
    }

    private VisualStream<T> chain(Stage st) {
        VisualStream<T> next = appendStage(st);
        emitPipelineOp(next, st);
        return next;
    }

    private <R> VisualStream<R> chainTyped(Stage st) {
        VisualStream<R> next = new VisualStream<>(name, source, appendList(st));
        emitPipelineOp(next, st);
        return next;
    }

    private VisualStream<T> appendStage(Stage st) {
        return new VisualStream<>(name, source, appendList(st));
    }

    private List<Stage> appendList(Stage st) {
        List<Stage> copy = new ArrayList<>(stages);
        copy.add(st);
        return copy;
    }

    private void emitPipelineOp(VisualStream<?> next, Stage st) {
        Trace.event("STREAM_PIPELINE_OP",
                "Added intermediate operation " + st.op + "(" + st.label
                        + "). It is lazy: it only extends the pipeline, nothing runs yet.",
                "Добавлена промежуточная операция " + st.op + "(" + st.label
                        + "). Она ленивая: лишь удлиняет конвейер, выполнение не запускается.",
                List.of("stage:" + (next.stages.size() - 1)), next.state());
    }

    // --- Terminal operations: trigger execution and produce a result. ---

    public List<T> collectToList(String label) {
        List<Object> out = new ArrayList<>();
        runTerminal("collect", label, out::add, null, () -> render(out));
        return castList(out);
    }

    public void forEach(String label, Consumer<? super T> action) {
        Consumer<Object> sink = asObjectConsumer(action);
        runTerminal("forEach", label, sink, null, () -> "void");
    }

    public long count(String label) {
        long[] c = {0};
        runTerminal("count", label, v -> c[0]++, null, () -> String.valueOf(c[0]));
        return c[0];
    }

    public boolean anyMatch(String label, Predicate<? super T> predicate) {
        Predicate<Object> p = asObjectPredicate(predicate);
        boolean[] matched = {false};
        runTerminal("anyMatch", label,
                v -> { if (p.test(v)) matched[0] = true; },
                v -> matched[0],
                () -> String.valueOf(matched[0]));
        return matched[0];
    }

    public Optional<T> findFirst(String label) {
        List<Object> first = new ArrayList<>(1);
        runTerminal("findFirst", label,
                v -> { if (first.isEmpty()) first.add(v); },
                v -> true,
                () -> first.isEmpty() ? "empty" : show(first.get(0)));
        return first.isEmpty() ? Optional.empty() : Optional.of(castElement(first.get(0)));
    }

    public T reduce(String label, T identity, BinaryOperator<T> op) {
        Object[] acc = {identity};
        runTerminal("reduce", label,
                v -> acc[0] = op.apply(castElement(acc[0]), castElement(v)),
                null,
                () -> show(acc[0]));
        return castElement(acc[0]);
    }

    /**
     * Drives the terminal: pulls every source element through the pipeline one at
     * a time, feeds survivors to {@code sink}, and short-circuits when
     * {@code stopAfterSink} (or a satisfied {@code limit}) says so.
     */
    private void runTerminal(String op, String label, Consumer<Object> sink,
                             Predicate<Object> stopAfterSink, Supplier<String> resultText) {
        this.terminalOp = op;
        this.terminalLabel = label;
        this.phase = "running";
        Trace.event("STREAM_TERMINAL_START",
                "Terminal operation " + op + "(" + label + ") starts execution; only now does the pipeline run.",
                "Терминальная операция " + op + "(" + label + ") запускает выполнение; только сейчас конвейер начинает работать.",
                List.of("stage:" + stages.size()), state());

        long[] limitRemaining = new long[stages.size()];
        List<Set<Object>> seen = new ArrayList<>();
        for (int s = 0; s < stages.size(); s++) {
            Stage st = stages.get(s);
            limitRemaining[s] = st.limit;
            seen.add(st.op.equals("distinct") ? new HashSet<>() : null);
        }

        boolean shortCircuit = false;
        for (int i = 0; i < source.size() && !shortCircuit; i++) {
            Object value = source.get(i);
            currentIndex = i;
            currentStage = -1;
            currentValue = show(value);
            status.set(i, "active");
            Trace.event("STREAM_ELEMENT_IN",
                    "Element " + show(value) + " enters the pipeline and travels through every stage on its own.",
                    "Элемент " + show(value) + " входит в конвейер и проходит все стадии по отдельности.",
                    List.of("source:" + i), state());

            boolean dropped = false;
            boolean limitStop = false;
            for (int s = 0; s < stages.size() && !dropped; s++) {
                Stage st = stages.get(s);
                currentStage = s;
                switch (st.op) {
                    case "filter" -> {
                        if (st.predicate.test(value)) {
                            currentValue = show(value);
                            emitPass(s, "filter " + st.label + " kept " + show(value),
                                    "filter " + st.label + " пропустил " + show(value));
                        } else {
                            dropped = true;
                            currentValue = show(value);
                            emitDrop(s, "filter " + st.label + " dropped " + show(value)
                                            + "; it leaves the pipeline now",
                                    "filter " + st.label + " отбросил " + show(value)
                                            + "; он покидает конвейер");
                        }
                    }
                    case "map" -> {
                        value = st.mapper.apply(value);
                        currentValue = show(value);
                        Trace.event("STREAM_MAP",
                                "map " + st.label + " transforms the element into " + show(value) + ".",
                                "map " + st.label + " преобразует элемент в " + show(value) + ".",
                                List.of("stage:" + s, "source:" + i), state());
                    }
                    case "peek" -> {
                        st.peeker.accept(value);
                        currentValue = show(value);
                        Trace.event("STREAM_MAP",
                                "peek " + st.label + " observes " + show(value) + " without changing it.",
                                "peek " + st.label + " наблюдает " + show(value) + ", не меняя его.",
                                List.of("stage:" + s, "source:" + i), state());
                    }
                    case "distinct" -> {
                        if (seen.get(s).add(value)) {
                            emitPass(s, "distinct kept first " + show(value),
                                    "distinct пропустил первый " + show(value));
                        } else {
                            dropped = true;
                            emitDrop(s, "distinct dropped duplicate " + show(value),
                                    "distinct отбросил дубликат " + show(value));
                        }
                    }
                    case "limit" -> {
                        if (limitRemaining[s] > 0) {
                            limitRemaining[s]--;
                            emitPass(s, "limit " + st.label + " let " + show(value) + " through",
                                    "limit " + st.label + " пропустил " + show(value));
                            if (limitRemaining[s] == 0) {
                                limitStop = true;
                            }
                        } else {
                            dropped = true;
                            emitDrop(s, "limit " + st.label + " is full; " + show(value) + " is dropped",
                                    "limit " + st.label + " заполнен; " + show(value) + " отброшен");
                        }
                    }
                    default -> { /* no-op */ }
                }
            }

            if (!dropped) {
                currentStage = stages.size();
                sink.accept(value);
                output.add(show(value));
                status.set(i, "used");
                Trace.event("STREAM_ELEMENT_COLLECTED",
                        "Element " + show(value) + " reaches the terminal " + terminalOp + " and is consumed there.",
                        "Элемент " + show(value) + " доходит до терминала " + terminalOp + " и потребляется им.",
                        List.of("stage:" + stages.size(), "output"), state());
                if (stopAfterSink != null && stopAfterSink.test(value)) {
                    shortCircuit = true;
                    shortCircuited = true;
                    Trace.event("STREAM_SHORT_CIRCUIT",
                            "Terminal " + terminalOp + " is satisfied and short-circuits; remaining elements are never touched.",
                            "Терминал " + terminalOp + " удовлетворён и замыкается; оставшиеся элементы даже не трогаются.",
                            List.of("stage:" + stages.size()), state());
                }
            } else {
                status.set(i, "skipped");
            }

            if (limitStop && !shortCircuit) {
                shortCircuit = true;
                shortCircuited = true;
                Trace.event("STREAM_SHORT_CIRCUIT",
                        "limit is full and short-circuits the pipeline; the source is not read any further.",
                        "limit заполнен и замыкает конвейер; источник дальше не читается.",
                        List.of("output"), state());
            }
        }

        currentStage = -2;
        currentIndex = -1;
        this.result = resultText.get();
        this.phase = "done";
        Trace.event("STREAM_TERMINAL_RESULT",
                "Terminal " + terminalOp + " produced a single result: " + result + ".",
                "Терминал " + terminalOp + " выдал единственный результат: " + result + ".",
                List.of("output"), state());

        consumed = true;
        Trace.event("STREAM_CONSUMED",
                "Stream " + name + " is now consumed. A stream is single-use; reusing it throws IllegalStateException.",
                "Стрим " + name + " теперь потреблён. Стрим одноразовый; повторное использование бросит IllegalStateException.",
                List.of("source"), state());
    }

    private void emitPass(int stageIndex, String descEn, String descRu) {
        Trace.event("STREAM_FILTER_PASS", descEn + ".", descRu + ".",
                List.of("stage:" + stageIndex), state());
    }

    private void emitDrop(int stageIndex, String descEn, String descRu) {
        Trace.event("STREAM_FILTER_DROP", descEn + ".", descRu + ".",
                List.of("stage:" + stageIndex), state());
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("phase", phase);
        s.put("consumed", consumed);
        s.put("shortCircuited", shortCircuited);

        List<Object> src = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("value", show(source.get(i)));
            cell.put("status", status.get(i));
            src.add(cell);
        }
        s.put("source", src);

        List<Object> stageList = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            stageList.add(stageState(stages.get(i), "intermediate", i));
        }
        if (!terminalOp.isEmpty()) {
            Map<String, Object> term = new LinkedHashMap<>();
            term.put("label", terminalLabel);
            term.put("kind", "terminal");
            term.put("op", terminalOp);
            term.put("active", currentStage == stages.size());
            stageList.add(term);
        }
        s.put("stages", stageList);

        if (currentIndex >= 0) {
            Map<String, Object> cur = new LinkedHashMap<>();
            cur.put("value", currentValue);
            cur.put("stageIndex", currentStage);
            s.put("current", cur);
        } else {
            s.put("current", null);
        }

        s.put("output", new ArrayList<>(output));
        s.put("result", result.isEmpty() ? null : result);
        return s;
    }

    private Map<String, Object> stageState(Stage st, String kind, int index) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", st.label);
        m.put("kind", kind);
        m.put("op", st.op);
        m.put("active", currentStage == index);
        return m;
    }

    private static String render(List<Object> values) {
        List<String> parts = new ArrayList<>();
        for (Object v : values) {
            parts.add(show(v));
        }
        return parts.toString();
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Object> asObjectPredicate(Predicate<?> p) {
        return (Predicate<Object>) p;
    }

    @SuppressWarnings("unchecked")
    private static Function<Object, Object> asObjectFunction(Function<?, ?> f) {
        return (Function<Object, Object>) f;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> asObjectConsumer(Consumer<?> c) {
        return (Consumer<Object>) c;
    }

    @SuppressWarnings("unchecked")
    private T castElement(Object value) {
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private List<T> castList(List<Object> values) {
        return (List<T>) values;
    }

    /** One pipeline stage: an intermediate operation plus its display label. */
    private static final class Stage {
        final String label;
        final String op;
        Predicate<Object> predicate;
        Function<Object, Object> mapper;
        Consumer<Object> peeker;
        long limit;

        Stage(String label, String op) {
            this.label = label;
            this.op = op;
        }
    }
}
