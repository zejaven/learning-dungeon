package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A teaching model of how Java source becomes running code. It is not a real
 * compiler or JVM: it models the interview-level pipeline from a .java file to
 * javac bytecode, class loading, bytecode verification, class initialization,
 * interpretation, JIT compilation and runtime memory areas.
 */
public class VisualJvmPipeline {

    private static final int JIT_THRESHOLD = 10;

    private final String className;
    private final String sourceFile;
    private final String bytecodeFile;
    private final List<String> output = new ArrayList<>();
    private final List<String> heapObjects = new ArrayList<>();
    private final Set<String> checks = new LinkedHashSet<>();

    private boolean compiled;
    private boolean loaded;
    private boolean verified;
    private boolean initialized;
    private boolean interpreted;
    private boolean nativeCompiled;
    private int objectCounter;
    private int hotCalls;
    private String currentStage = "source";
    private String hotMethod = "";
    private String activeMethod = "";

    public VisualJvmPipeline(String className) {
        this.className = className;
        String simple = simpleName(className);
        this.sourceFile = simple + ".java";
        this.bytecodeFile = simple + ".class";
        Trace.event("JVM_SOURCE_CREATED",
                sourceFile + " exists as human-readable Java source. The JVM still needs bytecode.",
                sourceFile + " существует как читаемый Java source. JVM все еще нужен bytecode.",
                List.of("stage:source", "artifact:source"),
                state());
    }

    /**
     * Demonstrates the common trap: a JVM run starts from bytecode, not from raw
     * source text.
     */
    public void tryRunSource() {
        currentStage = "source";
        Trace.event("JVM_SOURCE_REJECTED",
                "The JVM cannot execute " + sourceFile + " directly. Compile it with javac first.",
                "JVM не выполняет " + sourceFile + " напрямую. Сначала скомпилируйте его через javac.",
                List.of("stage:source", "artifact:source"),
                state());
    }

    /** Models javac turning source code into portable .class bytecode. */
    public void compile() {
        compiled = true;
        currentStage = "javac";
        Trace.event("JAVAC_COMPILED",
                "javac checked syntax and produced " + bytecodeFile + " with JVM bytecode.",
                "javac проверил синтаксис и создал " + bytecodeFile + " с JVM bytecode.",
                List.of("stage:javac", "artifact:bytecode"),
                state());
    }

    /** Models a ClassLoader locating the .class bytes. */
    public void load() {
        ensureCompiled();
        loaded = true;
        currentStage = "classloader";
        Trace.event("BYTECODE_LOADED",
                "ClassLoader found " + bytecodeFile + " and brought its bytes into the JVM.",
                "ClassLoader нашел " + bytecodeFile + " и передал его байты внутрь JVM.",
                List.of("stage:classloader", "artifact:bytecode", "memory:Metaspace"),
                state());
    }

    /** Models verifier checks before the class is allowed to run. */
    public void verify() {
        ensureLoaded();
        verified = true;
        checks.add("magic-number");
        checks.add("bytecode-version");
        checks.add("type-safety");
        checks.add("stack-map-frames");
        currentStage = "verifier";
        Trace.event("BYTECODE_VERIFIED",
                "The verifier accepted " + bytecodeFile + ": format, version and type-safety checks passed.",
                "Verifier принял " + bytecodeFile + ": проверки формата, версии и type-safety прошли.",
                List.of("stage:verifier", "artifact:bytecode"),
                state());
    }

    /** Models class initialization before the first active use. */
    public void initialize() {
        ensureVerified();
        initialized = true;
        currentStage = "runtime";
        Trace.event("CLASS_INITIALIZED",
                className + " is initialized: static fields and static blocks are ready for first use.",
                className + " инициализирован: static fields и static blocks готовы к первому использованию.",
                List.of("stage:runtime", "memory:Metaspace"),
                state());
    }

    /** Models the bytecode interpreter executing a method frame. */
    public void interpret(String method) {
        ensureInitialized();
        interpreted = true;
        activeMethod = method;
        currentStage = "interpreter";
        Trace.event("BYTECODE_INTERPRETED",
                "The interpreter executes bytecode for " + method + "() one instruction at a time.",
                "Interpreter выполняет bytecode метода " + method + "() по одной инструкции.",
                List.of("stage:interpreter", "memory:Stack"),
                state());
    }

    /**
     * Models repeated calls making a method hot. Once the threshold is reached,
     * the JIT compiler emits native code into the code cache.
     */
    public void callHotMethod(String method, int invocations) {
        ensureInitialized();
        interpreted = true;
        activeMethod = method;
        hotMethod = method;
        hotCalls += Math.max(0, invocations);
        if (!nativeCompiled && hotCalls >= JIT_THRESHOLD) {
            nativeCompiled = true;
            currentStage = "jit";
            Trace.event("METHOD_JIT_COMPILED",
                    method + "() became hot after " + hotCalls
                            + " calls, so the JIT compiled it to native machine code.",
                    method + "() стал hot после " + hotCalls
                            + " вызовов, поэтому JIT скомпилировал его в native machine code.",
                    List.of("stage:jit", "memory:Code Cache"),
                    state());
        } else {
            currentStage = "interpreter";
            Trace.event("BYTECODE_INTERPRETED",
                    method + "() has " + hotCalls + " call(s), so it still runs through the interpreter.",
                    method + "() имеет " + hotCalls + " вызов(ов), поэтому пока выполняется через interpreter.",
                    List.of("stage:interpreter", "memory:Stack"),
                    state());
        }
    }

    /** Models a normal object allocation while bytecode is running. */
    public void allocateObject(String type) {
        ensureInitialized();
        String objectId = type + "#" + (++objectCounter);
        heapObjects.add(objectId);
        currentStage = "runtime";
        Trace.event("OBJECT_ALLOCATED",
                "new " + type + "() allocated " + objectId + " on the heap while bytecode was running.",
                "new " + type + "() выделил " + objectId + " в heap во время выполнения bytecode.",
                List.of("stage:runtime", "memory:Heap", "object:" + objectId),
                state());
    }

    /** Adds visible program output as the final observable effect. */
    public void print(String line) {
        ensureInitialized();
        output.add(line);
        currentStage = nativeCompiled ? "jit" : "interpreter";
        Trace.event("PROGRAM_OUTPUT",
                "The running program produced output: " + line,
                "Запущенная программа вывела результат: " + line,
                List.of("stage:" + currentStage),
                state());
    }

    private void ensureCompiled() {
        if (!compiled) {
            compile();
        }
    }

    private void ensureLoaded() {
        ensureCompiled();
        if (!loaded) {
            load();
        }
    }

    private void ensureVerified() {
        ensureLoaded();
        if (!verified) {
            verify();
        }
    }

    private void ensureInitialized() {
        ensureVerified();
        if (!initialized) {
            initialize();
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("className", className);
        s.put("sourceFile", sourceFile);
        s.put("bytecodeFile", bytecodeFile);
        s.put("currentStage", currentStage);
        s.put("activeMethod", activeMethod);
        s.put("hotMethod", hotMethod);
        s.put("hotCalls", hotCalls);
        s.put("jitThreshold", JIT_THRESHOLD);
        s.put("nativeCompiled", nativeCompiled);
        s.put("stages", stages());
        s.put("artifacts", artifacts());
        s.put("checks", new ArrayList<>(checks));
        s.put("memory", memory());
        s.put("output", new ArrayList<>(output));
        return s;
    }

    private List<Object> stages() {
        List<Object> stages = new ArrayList<>();
        stages.add(stage("source", true, currentStage.equals("source")));
        stages.add(stage("javac", compiled, currentStage.equals("javac")));
        stages.add(stage("bytecode", compiled, false));
        stages.add(stage("classloader", loaded, currentStage.equals("classloader")));
        stages.add(stage("verifier", verified, currentStage.equals("verifier")));
        stages.add(stage("runtime", initialized || interpreted, currentStage.equals("runtime")
                || currentStage.equals("interpreter")));
        stages.add(stage("jit", nativeCompiled, currentStage.equals("jit")));
        return stages;
    }

    private Map<String, Object> stage(String id, boolean done, boolean active) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("id", id);
        stage.put("status", active ? "active" : done ? "done" : "waiting");
        return stage;
    }

    private List<Object> artifacts() {
        List<Object> artifacts = new ArrayList<>();
        artifacts.add(artifact("source", sourceFile, "source"));
        if (compiled) {
            artifacts.add(artifact("bytecode", bytecodeFile, "bytecode"));
        }
        if (loaded) {
            artifacts.add(artifact("metadata", className + " metadata", "metadata"));
        }
        if (nativeCompiled) {
            artifacts.add(artifact("native", hotMethod + "() native code", "native"));
        }
        return artifacts;
    }

    private Map<String, Object> artifact(String id, String label, String kind) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("id", id);
        artifact.put("label", label);
        artifact.put("kind", kind);
        return artifact;
    }

    private List<Object> memory() {
        List<Object> memory = new ArrayList<>();
        memory.add(memoryArea("Metaspace", loaded
                ? List.of(className + " metadata")
                : List.of()));
        memory.add(memoryArea("Stack", interpreted
                ? List.of(activeMethod.isBlank() ? "main() frame" : activeMethod + "() frame")
                : List.of()));
        memory.add(memoryArea("Heap", new ArrayList<>(heapObjects)));
        memory.add(memoryArea("Code Cache", nativeCompiled
                ? List.of(hotMethod + "() native code")
                : List.of()));
        return memory;
    }

    private Map<String, Object> memoryArea(String area, List<String> items) {
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("area", area);
        memory.put("items", items);
        return memory;
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }
}
