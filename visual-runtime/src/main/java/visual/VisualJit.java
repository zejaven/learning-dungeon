package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deterministic teaching model of HotSpot-style JIT compilation. It does not
 * benchmark real code. Instead, it shows the lifecycle interviewers care about:
 * interpreted calls, profiling, hot-method compilation, optimized execution,
 * inlining, escape analysis and deoptimization.
 */
public class VisualJit {

    private static final int DEFAULT_COMPILE_THRESHOLD = 4;
    private static final String[] STAGES = {
            "bytecode", "interpreter", "profiler", "compiler", "machine-code"
    };

    private final String vmName;
    private final int compileThreshold;
    private final Map<String, MethodProfile> methods = new LinkedHashMap<>();

    private int totalCalls;
    private String activeMethod;
    private String phase = "ready";

    public VisualJit() {
        this("HotSpot teaching JVM", DEFAULT_COMPILE_THRESHOLD);
    }

    public VisualJit(String vmName) {
        this(vmName, DEFAULT_COMPILE_THRESHOLD);
    }

    public VisualJit(String vmName, int compileThreshold) {
        if (compileThreshold < 1) {
            throw new IllegalArgumentException("compileThreshold must be positive");
        }
        this.vmName = vmName;
        this.compileThreshold = compileThreshold;
        Trace.event("JIT_VM_STARTED",
                "Started " + vmName + ": bytecode will run in the interpreter until a method becomes hot.",
                "Запущена " + vmName + ": bytecode будет выполняться интерпретатором, пока метод не станет hot.",
                List.of("stage:bytecode"),
                state("bytecode"));
    }

    /**
     * Simulates one method call. Cold calls are interpreted and profiled. When a
     * method reaches the threshold, the model emits hot-profile and compile
     * events. Later calls run as optimized machine code.
     */
    public void call(String methodName) {
        MethodProfile method = method(methodName);
        activeMethod = method.name;
        totalCalls++;
        method.calls++;

        if (method.compiled) {
            phase = "optimized";
            method.mode = "compiled";
            Trace.event("JIT_OPTIMIZED_CALL",
                    "Call " + method.calls + " of " + method.name
                            + " jumps to optimized machine code instead of interpreting bytecode again.",
                    "Вызов " + method.calls + " метода " + method.name
                            + " переходит в optimized machine code вместо повторной интерпретации bytecode.",
                    List.of("method:" + method.name, "stage:machine-code"),
                    state("machine-code"));
            return;
        }

        phase = "interpreting";
        method.mode = method.calls >= compileThreshold ? "hot" : "interpreted";
        Trace.event("JIT_INTERPRET",
                "Call " + method.calls + " of " + method.name
                        + " runs in the interpreter; the profiler records counters and observed types.",
                "Вызов " + method.calls + " метода " + method.name
                        + " выполняется интерпретатором; profiler записывает счетчики и замеченные типы.",
                List.of("method:" + method.name, "stage:interpreter", "stage:profiler"),
                state("interpreter"));

        if (method.calls >= compileThreshold) {
            phase = "profiling";
            Trace.event("JIT_PROFILE_HOT",
                    method.name + " is at or above the compile threshold (" + compileThreshold
                            + " calls), so the JVM treats it as hot code.",
                    method.name + " находится на уровне compile threshold или выше (" + compileThreshold
                            + " вызова), поэтому JVM считает его hot code.",
                    List.of("method:" + method.name, "stage:profiler"),
                    state("profiler"));
            compile(method);
        }
    }

    /**
     * Shows method inlining: the compiled caller can absorb a small callee and
     * remove part of the call overhead.
     */
    public void inline(String callerName, String calleeName) {
        MethodProfile caller = method(callerName);
        MethodProfile callee = method(calleeName);
        activeMethod = caller.name;
        if (!caller.compiled) {
            compile(caller);
        }
        phase = "optimized";
        if (!caller.inlinedMethods.contains(callee.name)) {
            caller.inlinedMethods.add(callee.name);
        }
        Trace.event("JIT_INLINE",
                "JIT inlined " + callee.name + " into " + caller.name
                        + ", so the optimized caller can avoid a separate method dispatch.",
                "JIT встроил " + callee.name + " в " + caller.name
                        + ", поэтому optimized caller избегает отдельного method dispatch.",
                List.of("method:" + caller.name, "method:" + callee.name, "stage:compiler", "stage:machine-code"),
                state("machine-code"));
    }

    /**
     * Shows escape analysis: an allocation that never escapes the method can be
     * optimized away in the compiled version.
     */
    public void eliminateAllocation(String methodName, String allocationName) {
        MethodProfile method = method(methodName);
        activeMethod = method.name;
        if (!method.compiled) {
            compile(method);
        }
        phase = "optimized";
        if (!method.eliminatedAllocations.contains(allocationName)) {
            method.eliminatedAllocations.add(allocationName);
        }
        Trace.event("JIT_ESCAPE_ELIMINATION",
                "Escape analysis proved " + allocationName + " stays inside " + method.name
                        + ", so the compiled code can remove that allocation.",
                "Escape analysis доказал, что " + allocationName + " остается внутри " + method.name
                        + ", поэтому compiled code может убрать эту аллокацию.",
                List.of("method:" + method.name, "stage:machine-code"),
                state("machine-code"));
    }

    /**
     * Shows speculative optimization being rolled back when a runtime
     * assumption turns out to be wrong.
     */
    public void deoptimize(String methodName, String reason) {
        MethodProfile method = method(methodName);
        activeMethod = method.name;
        phase = "deoptimized";
        method.compiled = false;
        method.mode = "interpreted";
        method.deoptimizations++;
        method.assumptions.clear();
        Trace.event("JIT_DEOPTIMIZE",
                "An assumption for " + method.name + " broke (" + reason
                        + "); the JVM discards optimized code and falls back to the interpreter.",
                "Предположение для " + method.name + " сломалось (" + reason
                        + "); JVM отбрасывает optimized code и возвращается к интерпретатору.",
                List.of("method:" + method.name, "stage:interpreter"),
                state("interpreter"));
    }

    public boolean isCompiled(String methodName) {
        return method(methodName).compiled;
    }

    public int calls(String methodName) {
        return method(methodName).calls;
    }

    private void compile(MethodProfile method) {
        if (method.compiled) {
            return;
        }
        activeMethod = method.name;
        phase = "compiling";
        method.compiled = true;
        method.mode = "compiled";
        method.assumptions.add("stable counters and types");
        Trace.event("JIT_COMPILE",
                "JIT compiled " + method.name + " after " + method.calls
                        + " observed call(s). Later calls can use native machine code.",
                "JIT скомпилировал " + method.name + " после " + method.calls
                        + " наблюдаемых вызов(ов). Следующие вызовы могут использовать native machine code.",
                List.of("method:" + method.name, "stage:compiler", "stage:machine-code"),
                state("compiler"));
    }

    private MethodProfile method(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        return methods.computeIfAbsent(methodName, MethodProfile::new);
    }

    private Object state(String activeStage) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("vmName", vmName);
        state.put("compileThreshold", compileThreshold);
        state.put("totalCalls", totalCalls);
        state.put("phase", phase);
        state.put("activeMethod", activeMethod);
        state.put("stages", stages(activeStage));
        state.put("methods", methodStates());
        return state;
    }

    private List<Object> stages(String activeStage) {
        List<Object> result = new ArrayList<>();
        for (String id : STAGES) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("id", id);
            stage.put("active", id.equals(activeStage));
            stage.put("ready", "machine-code".equals(id) && hasCompiledMethod());
            result.add(stage);
        }
        return result;
    }

    private boolean hasCompiledMethod() {
        for (MethodProfile method : methods.values()) {
            if (method.compiled) {
                return true;
            }
        }
        return false;
    }

    private List<Object> methodStates() {
        List<Object> result = new ArrayList<>();
        for (MethodProfile method : methods.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", method.name);
            entry.put("calls", method.calls);
            entry.put("mode", method.mode);
            entry.put("compiled", method.compiled);
            entry.put("inlinedMethods", new ArrayList<>(method.inlinedMethods));
            entry.put("eliminatedAllocations", new ArrayList<>(method.eliminatedAllocations));
            entry.put("deoptimizations", method.deoptimizations);
            entry.put("assumptions", new ArrayList<>(method.assumptions));
            result.add(entry);
        }
        return result;
    }

    private static final class MethodProfile {
        final String name;
        int calls;
        String mode = "cold";
        boolean compiled;
        int deoptimizations;
        final List<String> inlinedMethods = new ArrayList<>();
        final List<String> eliminatedAllocations = new ArrayList<>();
        final List<String> assumptions = new ArrayList<>();

        MethodProfile(String name) {
            this.name = name;
        }
    }
}
