package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Teaching probe for Java loops and Stream pipelines.
 *
 * <p>The class does not benchmark code. It instruments small examples so the UI
 * can show when a loop visits an element, when a Stream intermediate operation
 * actually runs, where boxing appears, and what extra split/merge work a
 * parallel-style plan needs.
 */
public class VisualStreamProbe {

    private final String name;
    private final List<Integer> source;
    private final List<String> itemStatuses;
    private final Map<String, Stage> stages = new LinkedHashMap<>();
    private final List<Chunk> chunks = new ArrayList<>();

    private String mode = "idle";
    private String plan = "";
    private int loopIterations;
    private int stageCalls;
    private int boxedConversions;
    private int primitiveSteps;
    private int splits;
    private int merges;
    private String result = "";

    public VisualStreamProbe(String name, List<Integer> source) {
        this.name = name;
        this.source = List.copyOf(source);
        this.itemStatuses = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            itemStatuses.add("idle");
        }
        Trace.event("STREAM_SOURCE_CREATED",
                "Created probe '" + name + "' for " + source.size() + " source element(s).",
                "Создан probe '" + name + "' для " + source.size() + " исходных элемент(ов).",
                List.of(), state());
    }

    public int loopVisit(int value) {
        mode = "loop";
        loopIterations++;
        markItem(value, "current");
        setStage("loop", "loop", "active", value);
        Trace.event("LOOP_ITERATION",
                "The loop visits value " + value + " directly in the loop body.",
                "Цикл напрямую обрабатывает значение " + value + " в теле цикла.",
                List.of("item:" + indexOf(value), "stage:loop", "counter:loopIterations"),
                state());
        markItem(value, "done");
        return value;
    }

    public void finishLoop(int sum) {
        mode = "loop";
        result = String.valueOf(sum);
        setStage("loop", "loop", "done", null);
        Trace.event("LOOP_DONE",
                "Loop finished with result " + sum + ".",
                "Цикл завершился с результатом " + sum + ".",
                List.of("stage:loop"),
                state());
    }

    public void pipelineDeclared(String plan) {
        mode = "stream";
        this.plan = plan;
        stages.clear();
        setStage("source", "source", "ready", null);
        setStage("filter", "filter", "waiting", null);
        setStage("map", "map", "waiting", null);
        setStage("reduce", "terminal", "waiting", null);
        Trace.event("STREAM_PIPELINE_DECLARED",
                "Declared Stream pipeline '" + plan + "'; no element is processed before a terminal operation.",
                "Объявлен Stream pipeline '" + plan + "'; до terminal operation элементы не обрабатываются.",
                List.of("stage:filter", "stage:map", "stage:reduce"),
                state());
    }

    public boolean filterEven(int value) {
        mode = "stream";
        stageCalls++;
        boolean accepted = value % 2 == 0;
        markItem(value, accepted ? "accepted" : "rejected");
        setStage("filter", "filter", "active", value);
        Trace.event("STREAM_FILTER",
                "filter checks " + value + " and " + (accepted ? "keeps" : "drops") + " it.",
                "filter проверяет " + value + " и " + (accepted ? "оставляет" : "отбрасывает") + " его.",
                List.of("item:" + indexOf(value), "stage:filter", "counter:stageCalls"),
                state());
        return accepted;
    }

    public int mapSquare(int value) {
        mode = "stream";
        stageCalls++;
        int mapped = value * value;
        markItem(value, "mapped");
        setStage("map", "map", "active", mapped);
        Trace.event("STREAM_MAP",
                "map transforms " + value + " into " + mapped + ".",
                "map преобразует " + value + " в " + mapped + ".",
                List.of("item:" + indexOf(value), "stage:map", "counter:stageCalls"),
                state());
        return mapped;
    }

    public int reduceSum(int left, int right) {
        mode = "stream";
        stageCalls++;
        int sum = left + right;
        result = String.valueOf(sum);
        setStage("reduce", "terminal", "active", sum);
        Trace.event("STREAM_REDUCE",
                "terminal reduce combines " + left + " and " + right + " into " + sum + ".",
                "terminal reduce складывает " + left + " и " + right + " в " + sum + ".",
                List.of("stage:reduce", "counter:stageCalls"),
                state());
        return sum;
    }

    public void shortCircuitFound(int value) {
        mode = "stream";
        result = String.valueOf(value);
        markItem(value, "done");
        setStage("terminal", "terminal", "done", value);
        Trace.event("STREAM_SHORT_CIRCUIT",
                "findFirst found " + value + " and stops pulling more source elements.",
                "findFirst нашёл " + value + " и больше не запрашивает элементы из источника.",
                List.of("item:" + indexOf(value), "stage:terminal"),
                state());
    }

    public void finishStream(Object value) {
        mode = "stream";
        result = String.valueOf(value);
        setStage("terminal", "terminal", "done", value);
        Trace.event("STREAM_DONE",
                "Stream terminal operation produced " + value + ".",
                "Terminal operation у Stream получила результат " + value + ".",
                List.of("stage:terminal"),
                state());
    }

    public int unbox(Integer value) {
        mode = "boxing";
        boxedConversions++;
        int unboxed = value;
        markItem(unboxed, "boxed");
        setStage("boxing", "boxing", "active", unboxed);
        Trace.event("STREAM_BOXING",
                "Stream<Integer> passes boxed value " + value + "; numeric work must unbox it.",
                "Stream<Integer> передаёт boxed значение " + value + "; для числовой работы его нужно unbox.",
                List.of("item:" + indexOf(unboxed), "stage:boxing", "counter:boxedConversions"),
                state());
        return unboxed;
    }

    public void primitiveVisit(int value) {
        mode = "primitive";
        primitiveSteps++;
        markItem(value, "primitive");
        setStage("primitive", "IntStream", "active", value);
        Trace.event("PRIMITIVE_STREAM_STEP",
                "IntStream handles primitive int value " + value + " without boxing.",
                "IntStream обрабатывает primitive int " + value + " без boxing.",
                List.of("item:" + indexOf(value), "stage:primitive", "counter:primitiveSteps"),
                state());
    }

    public int simulateParallelSum(int workers) {
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive");
        }
        mode = "parallel";
        chunks.clear();
        splits++;
        int chunkSize = Math.max(1, (int) Math.ceil(source.size() / (double) workers));
        for (int start = 0; start < source.size(); start += chunkSize) {
            int end = Math.min(source.size(), start + chunkSize);
            List<Integer> values = source.subList(start, end);
            chunks.add(new Chunk("chunk-" + chunks.size(), values, sum(values), "split"));
        }
        setStage("split", "split", "active", chunks.size());
        Trace.event("PARALLEL_SPLIT",
                "Parallel-style work is split into " + chunks.size() + " chunk(s) before useful summing starts.",
                "Parallel-style работа делится на " + chunks.size() + " часть(и) до начала полезного суммирования.",
                List.of("stage:split", "counter:splits"),
                state());

        int total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            chunks.set(i, chunk.withStatus("merged"));
            merges++;
            total += chunk.sum();
            result = String.valueOf(total);
            setStage("merge", "merge", "active", total);
            Trace.event("PARALLEL_MERGE",
                    "Merged " + chunk.id() + " with partial sum " + chunk.sum() + "; running total is " + total + ".",
                    "Объединили " + chunk.id() + " с partial sum " + chunk.sum() + "; текущий итог " + total + ".",
                    List.of("stage:merge", "counter:merges"),
                    state());
        }
        setStage("merge", "merge", "done", total);
        return total;
    }

    private void setStage(String id, String label, String status, Object value) {
        stages.put(id, new Stage(id, label, status, value == null ? "" : String.valueOf(value)));
    }

    private void markItem(int value, String status) {
        int index = indexOf(value);
        if (index >= 0) {
            itemStatuses.set(index, status);
        }
    }

    private int indexOf(int value) {
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i) == value) {
                return i;
            }
        }
        return -1;
    }

    private static int sum(List<Integer> values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("mode", mode);
        s.put("plan", plan);
        s.put("source", sourceState());
        s.put("stages", stageState());
        s.put("chunks", chunkState());
        s.put("counters", countersState());
        s.put("result", result);
        return s;
    }

    private List<Object> sourceState() {
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("value", source.get(i));
            item.put("status", itemStatuses.get(i));
            items.add(item);
        }
        return items;
    }

    private List<Object> stageState() {
        List<Object> rows = new ArrayList<>();
        for (Stage stage : stages.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", stage.id());
            row.put("label", stage.label());
            row.put("status", stage.status());
            row.put("value", stage.value());
            rows.add(row);
        }
        return rows;
    }

    private List<Object> chunkState() {
        List<Object> rows = new ArrayList<>();
        for (Chunk chunk : chunks) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", chunk.id());
            row.put("values", chunk.values());
            row.put("sum", chunk.sum());
            row.put("status", chunk.status());
            rows.add(row);
        }
        return rows;
    }

    private Object countersState() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("loopIterations", loopIterations);
        c.put("stageCalls", stageCalls);
        c.put("boxedConversions", boxedConversions);
        c.put("primitiveSteps", primitiveSteps);
        c.put("splits", splits);
        c.put("merges", merges);
        return c;
    }

    private record Stage(String id, String label, String status, String value) {
    }

    private record Chunk(String id, List<Integer> values, int sum, String status) {
        Chunk withStatus(String newStatus) {
            return new Chunk(id, values, sum, newStatus);
        }
    }
}
