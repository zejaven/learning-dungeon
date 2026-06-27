package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dependency-free teaching model of Spring-style AOP proxying. It is not
 * Spring and it does not parse real AspectJ pointcut expressions. It keeps the
 * core interview mental model small:
 *
 * <ul>
 *   <li>cross-cutting code can appear in many business methods,</li>
 *   <li>a proxy intercepts calls before the target object runs,</li>
 *   <li>pointcuts decide which methods get advice,</li>
 *   <li>before, after, after-returning, after-throwing and around advice live
 *       outside the target method,</li>
 *   <li>calls that skip the proxy also skip the advice chain.</li>
 * </ul>
 */
public class VisualAopProxy {

    private final String target;
    private final String proxy;
    private final List<Advice> advices = new ArrayList<>();
    private final Map<String, List<String>> businessMethods = new LinkedHashMap<>();
    private final List<Frame> stack = new ArrayList<>();
    private final List<Step> executed = new ArrayList<>();
    private List<Advice> currentAdvices = new ArrayList<>();
    private String activeMethod = "";
    private String callMode = "none";
    private String phase = "idle";
    private boolean pointcutMatched;
    private boolean targetOnStack;
    private int stepId;

    public VisualAopProxy() {
        this("OrderService");
    }

    public VisualAopProxy(String target) {
        this.target = target;
        this.proxy = target + "$$AopProxy";
        Trace.event("AOP_PROXY_CREATED",
                "Spring exposes proxy '" + proxy + "' in front of target bean '" + target + "'",
                "Spring выставляет proxy '" + proxy + "' перед целевым bean '" + target + "'",
                List.of("proxy", "target"), state());
    }

    /**
     * Marks that the same cross-cutting concern is repeated in several methods.
     * This represents the code smell AOP tries to remove.
     */
    public VisualAopProxy duplicatedConcern(String concern, String... methods) {
        phase = "duplication";
        for (String method : methods) {
            businessMethods.computeIfAbsent(method, ignored -> new ArrayList<>()).add(concern);
        }
        List<String> highlight = new ArrayList<>();
        for (String method : methods) {
            highlight.add("method:" + method);
        }
        Trace.event("AOP_DUPLICATION_FOUND",
                "Concern '" + concern + "' is repeated in " + methods.length
                        + " methods; AOP can move it to one advice",
                "Сквозная логика '" + concern + "' повторяется в " + methods.length
                        + " методах; AOP может вынести её в один advice",
                highlight, state());
        return this;
    }

    public VisualAopProxy before(String adviceName, String pointcut) {
        return addAdvice(adviceName, "before", pointcut);
    }

    public VisualAopProxy after(String adviceName, String pointcut) {
        return addAdvice(adviceName, "after", pointcut);
    }

    public VisualAopProxy afterReturning(String adviceName, String pointcut) {
        return addAdvice(adviceName, "afterReturning", pointcut);
    }

    public VisualAopProxy afterThrowing(String adviceName, String pointcut) {
        return addAdvice(adviceName, "afterThrowing", pointcut);
    }

    public VisualAopProxy around(String adviceName, String pointcut) {
        return addAdvice(adviceName, "around", pointcut);
    }

    /**
     * Starts a call through the injected proxy reference. Matching advice runs
     * around the target method; non-matching advice is ignored.
     */
    public VisualAopProxy call(String method) {
        beginCall(method, "proxy");
        stack.add(new Frame(proxy, "proxy"));
        record("proxy", proxy, method, "intercept");
        Trace.event("AOP_PROXY_INTERCEPT",
                "Client calls proxy for '" + method + "'; the proxy checks pointcuts before invoking the target",
                "Клиент вызывает proxy для '" + method + "'; proxy проверяет pointcut перед вызовом target",
                List.of("proxy", "method:" + method), state());

        currentAdvices = matchingAdvices(method);
        pointcutMatched = !currentAdvices.isEmpty();
        phase = "pointcut";
        if (pointcutMatched) {
            Trace.event("AOP_POINTCUT_MATCH",
                    "Pointcut matched '" + method + "'; " + currentAdvices.size()
                            + " advice item(s) are attached to this call",
                    "Pointcut подошёл к '" + method + "'; к вызову подключено "
                            + currentAdvices.size() + " advice",
                    adviceHighlights(currentAdvices, "method:" + method), state());
            runStartingAdvice(method);
        } else {
            Trace.event("AOP_POINTCUT_MISS",
                    "No pointcut matches '" + method + "'; the target will run without advice",
                    "Ни один pointcut не подходит к '" + method + "'; target выполнится без advice",
                    List.of("method:" + method), state());
        }
        return this;
    }

    /**
     * Starts a call directly on the target object. This bypasses the proxy, so
     * registered advice cannot run.
     */
    public VisualAopProxy directCall(String method) {
        beginCall(method, "direct");
        phase = "direct";
        stack.add(new Frame(target, "target"));
        targetOnStack = true;
        record("target", target, method, "direct");
        Trace.event("AOP_DIRECT_CALL",
                "'" + method + "' is called on the target object directly; the proxy and advice chain are skipped",
                "'" + method + "' вызывается напрямую на target; proxy и цепочка advice пропущены",
                List.of("target", "method:" + method), state());
        return this;
    }

    /**
     * Records one business step inside the target method.
     */
    public VisualAopProxy targetLine(String action) {
        phase = "target";
        if (!targetOnStack) {
            stack.add(new Frame(activeMethod, "target"));
            targetOnStack = true;
        }
        record("target", activeMethod, activeMethod, action);
        Trace.event("AOP_TARGET_CALL",
                "Target method '" + activeMethod + "' runs business step " + action
                        + " without embedding cross-cutting code",
                "Target method '" + activeMethod + "' выполняет бизнес-шаг " + action
                        + " без встроенной сквозной логики",
                List.of("target", "method:" + activeMethod), state());
        return this;
    }

    /**
     * Completes the call successfully, then runs success-oriented advice.
     */
    public VisualAopProxy returnNormally() {
        if ("proxy".equals(callMode) && pointcutMatched) {
            for (Advice advice : currentAdvices) {
                if ("afterReturning".equals(advice.kind)) {
                    advice.applied = true;
                    phase = "afterReturning";
                    record("advice", advice.name, activeMethod, "afterReturning");
                    Trace.event("AOP_ADVICE_AFTER_RETURNING",
                            "After-returning advice '" + advice.name + "' runs after '"
                                    + activeMethod + "' returns successfully",
                            "After-returning advice '" + advice.name + "' выполняется после успешного возврата '"
                                    + activeMethod + "'",
                            List.of("advice:" + advice.name, "method:" + activeMethod), state());
                }
            }
            runAfterAdvice(false);
            finishAroundAdvice(false);
        }
        phase = "done";
        stack.clear();
        Trace.event("AOP_RETURN",
                "Call to '" + activeMethod + "' is complete; business code stayed focused on the business task",
                "Вызов '" + activeMethod + "' завершён; бизнес-код остался сосредоточен на бизнес-задаче",
                List.of("method:" + activeMethod), state());
        return this;
    }

    /**
     * Completes the call by throwing an exception, then runs failure-oriented
     * advice.
     */
    public VisualAopProxy throwException(String exceptionName) {
        phase = "exception";
        record("target", activeMethod, activeMethod, "exception");
        Trace.event("AOP_EXCEPTION",
                "Target method '" + activeMethod + "' throws " + exceptionName,
                "Target method '" + activeMethod + "' бросает " + exceptionName,
                List.of("target", "method:" + activeMethod), state());

        if ("proxy".equals(callMode) && pointcutMatched) {
            for (Advice advice : currentAdvices) {
                if ("afterThrowing".equals(advice.kind)) {
                    advice.applied = true;
                    phase = "afterThrowing";
                    record("advice", advice.name, activeMethod, "afterThrowing");
                    Trace.event("AOP_ADVICE_AFTER_THROWING",
                            "After-throwing advice '" + advice.name + "' handles exception from '"
                                    + activeMethod + "'",
                            "After-throwing advice '" + advice.name + "' обрабатывает exception из '"
                                    + activeMethod + "'",
                            List.of("advice:" + advice.name, "method:" + activeMethod), state());
                }
            }
            runAfterAdvice(true);
            finishAroundAdvice(true);
        }

        phase = "done";
        stack.clear();
        Trace.event("AOP_RETURN",
                "Exception leaves '" + activeMethod + "' through the proxy; the call is complete",
                "Exception выходит из '" + activeMethod + "' через proxy; вызов завершён",
                List.of("method:" + activeMethod), state());
        return this;
    }

    private VisualAopProxy addAdvice(String adviceName, String kind, String pointcut) {
        phase = "setup";
        Advice advice = new Advice(adviceName, kind, pointcut);
        advices.add(advice);
        Trace.event("AOP_ADVICE_REGISTERED",
                "Registered " + kind + " advice '" + adviceName + "' for pointcut '" + pointcut + "'",
                "Зарегистрирован " + kind + " advice '" + adviceName + "' для pointcut '" + pointcut + "'",
                List.of("advice:" + adviceName), state());
        return this;
    }

    private void beginCall(String method, String mode) {
        activeMethod = method;
        callMode = mode;
        phase = mode;
        pointcutMatched = false;
        targetOnStack = false;
        currentAdvices = List.of();
        stack.clear();
        executed.clear();
        stepId = 0;
        for (Advice advice : advices) {
            advice.applied = false;
        }
        businessMethods.computeIfAbsent(method, ignored -> new ArrayList<>());
    }

    private void runStartingAdvice(String method) {
        for (Advice advice : currentAdvices) {
            if ("around".equals(advice.kind)) {
                advice.applied = true;
                phase = "around";
                stack.add(new Frame(advice.name, "advice"));
                record("advice", advice.name, method, "around.before");
                Trace.event("AOP_ADVICE_AROUND_START",
                        "Around advice '" + advice.name + "' starts before proceed() to '"
                                + method + "'",
                        "Around advice '" + advice.name + "' стартует перед proceed() к '"
                                + method + "'",
                        List.of("advice:" + advice.name, "method:" + method), state());
            }
        }
        for (Advice advice : currentAdvices) {
            if ("before".equals(advice.kind)) {
                advice.applied = true;
                phase = "before";
                record("advice", advice.name, method, "before");
                Trace.event("AOP_ADVICE_BEFORE",
                        "Before advice '" + advice.name + "' runs before target method '"
                                + method + "'",
                        "Before advice '" + advice.name + "' выполняется перед target method '"
                                + method + "'",
                        List.of("advice:" + advice.name, "method:" + method), state());
            }
        }
    }

    private void finishAroundAdvice(boolean exceptional) {
        for (int i = currentAdvices.size() - 1; i >= 0; i--) {
            Advice advice = currentAdvices.get(i);
            if ("around".equals(advice.kind)) {
                advice.applied = true;
                phase = "around";
                record("advice", advice.name, activeMethod, exceptional ? "around.exception" : "around.after");
                Trace.event("AOP_ADVICE_AROUND_FINISH",
                        "Around advice '" + advice.name + "' resumes after proceed() from '"
                                + activeMethod + "'",
                        "Around advice '" + advice.name + "' продолжает работу после proceed() из '"
                                + activeMethod + "'",
                        List.of("advice:" + advice.name, "method:" + activeMethod), state());
            }
        }
    }

    private void runAfterAdvice(boolean exceptional) {
        for (Advice advice : currentAdvices) {
            if ("after".equals(advice.kind)) {
                advice.applied = true;
                phase = "after";
                record("advice", advice.name, activeMethod, "after");
                Trace.event("AOP_ADVICE_AFTER",
                        "After advice '" + advice.name + "' runs after '"
                                + activeMethod + "' finishes, regardless of "
                                + (exceptional ? "the exception" : "the return value"),
                        "After advice '" + advice.name + "' \u0432\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u0442\u0441\u044f \u043f\u043e\u0441\u043b\u0435 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0438\u044f '"
                                + activeMethod + "', \u043d\u0435\u0437\u0430\u0432\u0438\u0441\u0438\u043c\u043e \u043e\u0442 "
                                + (exceptional ? "exception" : "\u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0430\u0435\u043c\u043e\u0433\u043e \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f"),
                        List.of("advice:" + advice.name, "method:" + activeMethod), state());
            }
        }
    }

    private List<Advice> matchingAdvices(String method) {
        List<Advice> matches = new ArrayList<>();
        for (Advice advice : advices) {
            if (matches(advice.pointcut, method)) {
                matches.add(advice);
            }
        }
        return matches;
    }

    private static boolean matches(String pointcut, String method) {
        if ("*".equals(pointcut)) return true;
        if (pointcut.endsWith("*")) {
            return method.startsWith(pointcut.substring(0, pointcut.length() - 1));
        }
        if (pointcut.startsWith("*")) {
            return method.endsWith(pointcut.substring(1));
        }
        return pointcut.equals(method);
    }

    private void record(String role, String name, String method, String action) {
        executed.add(new Step("step-" + (++stepId), role, name, method, action));
    }

    private static List<String> adviceHighlights(List<Advice> advices, String extra) {
        List<String> highlight = new ArrayList<>();
        highlight.add(extra);
        for (Advice advice : advices) {
            highlight.add("advice:" + advice.name);
        }
        return highlight;
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("target", target);
        s.put("proxy", proxy);
        s.put("activeMethod", activeMethod);
        s.put("callMode", callMode);
        s.put("phase", phase);
        s.put("pointcutMatched", pointcutMatched);
        s.put("advices", adviceState());
        s.put("stack", stackState());
        s.put("businessMethods", businessMethodState());
        s.put("executed", executedState());
        return s;
    }

    private List<Object> adviceState() {
        List<Object> out = new ArrayList<>();
        for (Advice advice : advices) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", advice.name);
            m.put("kind", advice.kind);
            m.put("pointcut", advice.pointcut);
            m.put("applied", advice.applied);
            out.add(m);
        }
        return out;
    }

    private List<Object> stackState() {
        List<Object> out = new ArrayList<>();
        for (Frame frame : stack) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", frame.name);
            m.put("role", frame.role);
            out.add(m);
        }
        return out;
    }

    private List<Object> businessMethodState() {
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : businessMethods.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", entry.getKey());
            m.put("concerns", new ArrayList<>(entry.getValue()));
            out.add(m);
        }
        return out;
    }

    private List<Object> executedState() {
        List<Object> out = new ArrayList<>();
        for (Step step : executed) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", step.id);
            m.put("role", step.role);
            m.put("name", step.name);
            m.put("method", step.method);
            m.put("action", step.action);
            out.add(m);
        }
        return out;
    }

    private static final class Advice {
        final String name;
        final String kind;
        final String pointcut;
        boolean applied;

        Advice(String name, String kind, String pointcut) {
            this.name = name;
            this.kind = kind;
            this.pointcut = pointcut;
        }
    }

    private record Frame(String name, String role) {
    }

    private record Step(String id, String role, String name, String method, String action) {
    }
}
