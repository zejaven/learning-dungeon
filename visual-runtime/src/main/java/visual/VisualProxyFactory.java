package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dependency-free teaching model of how a Spring AOP {@code ProxyFactory}
 * chooses between a JDK dynamic proxy and a CGLIB proxy, and how a call is
 * dispatched through the resulting proxy. It is not Spring: it does not call
 * {@code java.lang.reflect.Proxy} or generate real bytecode. It keeps the core
 * interview mental model small:
 *
 * <ul>
 *   <li>a JDK dynamic proxy is a sibling that <em>implements</em> the target's
 *       interface(s) and routes every call to an {@code InvocationHandler};</li>
 *   <li>a CGLIB proxy is a generated <em>subclass</em> of the target and routes
 *       every call to a {@code MethodInterceptor};</li>
 *   <li>Spring picks JDK when the bean implements an interface, and CGLIB when it
 *       has none — unless {@code proxyTargetClass=true} forces CGLIB;</li>
 *   <li>CGLIB cannot subclass a final class, and cannot intercept a final or
 *       private method, so such calls run unadvised.</li>
 * </ul>
 */
public class VisualProxyFactory {

    private final String target;
    private final List<String> interfaces = new ArrayList<>();
    private final List<Method> methods = new ArrayList<>();
    private final List<Step> log = new ArrayList<>();

    private boolean finalClass;
    private boolean proxyTargetClass;
    private String strategy = "";      // "JDK" | "CGLIB"
    private String reason = "";        // "has-interface" | "no-interface" | "force-cglib"
    private String proxyName = "";
    private String relation = "";      // "implements" | "extends"
    private String supertype = "";
    private boolean created;
    private boolean blocked;
    private String phase = "define";
    private String activeMethod = "";
    private int stepId;

    public VisualProxyFactory(String target) {
        this.target = target;
        Trace.event("PROXY_TARGET_DEFINED",
                "Target bean '" + target + "' is registered with the container",
                "Целевой bean '" + target + "' зарегистрирован в контейнере",
                List.of("target"), state());
    }

    /** Marks the target class as final, so CGLIB cannot subclass it. */
    public VisualProxyFactory finalClass() {
        finalClass = true;
        Trace.event("PROXY_TARGET_DEFINED",
                "Target class '" + target + "' is final; it cannot be subclassed",
                "Целевой класс '" + target + "' помечен final; его нельзя наследовать",
                List.of("target"), state());
        return this;
    }

    /** Declares that the target bean implements the given interface. */
    public VisualProxyFactory implementsInterface(String name) {
        interfaces.add(name);
        Trace.event("PROXY_INTERFACE_ADDED",
                "Target '" + target + "' implements interface '" + name + "'",
                "Target '" + target + "' реализует interface '" + name + "'",
                List.of("interface:" + name), state());
        return this;
    }

    /** Declares an ordinary (overridable) business method on the target. */
    public VisualProxyFactory method(String name) {
        return defineMethod(name, false);
    }

    /** Declares a final business method that CGLIB cannot override. */
    public VisualProxyFactory finalMethod(String name) {
        return defineMethod(name, true);
    }

    /** Forces CGLIB even when the bean implements an interface. */
    public VisualProxyFactory proxyTargetClass(boolean value) {
        proxyTargetClass = value;
        Trace.event("PROXY_CONFIG",
                "proxyTargetClass=" + value + (value ? " forces CGLIB" : " keeps the default choice"),
                "proxyTargetClass=" + value + (value ? " форсирует CGLIB" : " оставляет выбор по умолчанию"),
                List.of("config"), state());
        return this;
    }

    /**
     * Decides the proxy strategy the way Spring AOP does and creates the proxy
     * object (unless CGLIB is blocked by a final target class).
     */
    public VisualProxyFactory createProxy() {
        phase = "create";
        boolean hasInterface = !interfaces.isEmpty();
        boolean useCglib = proxyTargetClass || !hasInterface;

        if (useCglib) {
            strategy = "CGLIB";
            reason = proxyTargetClass ? "force-cglib" : "no-interface";
            relation = "extends";
            supertype = target;
            proxyName = target + "$$EnhancerBySpringCGLIB";
            String why = proxyTargetClass
                    ? "proxyTargetClass=true forces a class-based proxy"
                    : "the bean implements no interface, so a class-based proxy is required";
            String whyRu = proxyTargetClass
                    ? "proxyTargetClass=true форсирует class-based proxy"
                    : "у bean нет interface, поэтому нужен class-based proxy";
            Trace.event("PROXY_CGLIB_SELECTED",
                    "CGLIB selected: " + why,
                    "Выбран CGLIB: " + whyRu,
                    List.of("strategy:CGLIB"), state());

            if (finalClass) {
                blocked = true;
                phase = "blocked";
                Trace.event("PROXY_FINAL_CLASS_BLOCKED",
                        "CGLIB cannot subclass final class '" + target + "'; proxy creation fails",
                        "CGLIB не может наследовать final-класс '" + target + "'; создание proxy падает",
                        List.of("target", "strategy:CGLIB"), state());
                return this;
            }
        } else {
            strategy = "JDK";
            reason = "has-interface";
            relation = "implements";
            supertype = interfaces.get(0);
            proxyName = "$Proxy0";
            Trace.event("PROXY_JDK_SELECTED",
                    "JDK dynamic proxy selected: the bean implements '" + supertype
                            + "', so a sibling proxy can implement the same interface",
                    "Выбран JDK dynamic proxy: bean реализует '" + supertype
                            + "', поэтому proxy-сосед может реализовать тот же interface",
                    List.of("strategy:JDK", "interface:" + supertype), state());
        }

        created = true;
        phase = "ready";
        String shape = "JDK".equals(strategy)
                ? "'" + proxyName + "' implements '" + supertype + "' and holds an InvocationHandler"
                : "'" + proxyName + "' extends '" + supertype + "' and holds a MethodInterceptor";
        String shapeRu = "JDK".equals(strategy)
                ? "'" + proxyName + "' реализует '" + supertype + "' и держит InvocationHandler"
                : "'" + proxyName + "' наследует '" + supertype + "' и держит MethodInterceptor";
        Trace.event("PROXY_CREATED",
                "Proxy created: " + shape,
                "Proxy создан: " + shapeRu,
                List.of("proxy", "strategy:" + strategy), state());
        return this;
    }

    /**
     * Dispatches a call through the proxy: interception, advice, and delegation
     * to the target. A final method under CGLIB is not intercepted and runs
     * unadvised.
     */
    public VisualProxyFactory invoke(String method) {
        activeMethod = method;
        log.clear();
        stepId = 0;
        Method declared = find(method);

        if (!created) {
            phase = "blocked";
            record("client", "no-proxy", method);
            Trace.event("PROXY_INVOKE",
                    "No proxy exists for '" + target + "'; the call cannot be intercepted",
                    "Для '" + target + "' нет proxy; вызов невозможно перехватить",
                    List.of("method:" + method), state());
            return this;
        }

        boolean unadvised = "CGLIB".equals(strategy) && declared != null && declared.isFinal;
        phase = "invoke";
        record("proxy", "intercept", method);
        Trace.event("PROXY_INVOKE",
                "Client calls '" + method + "()' on proxy '" + proxyName + "'",
                "Клиент вызывает '" + method + "()' на proxy '" + proxyName + "'",
                List.of("proxy", "method:" + method), state());

        if (unadvised) {
            phase = "unadvised";
            record("target", "unadvised", method);
            Trace.event("PROXY_FINAL_METHOD_SKIPPED",
                    "'" + method + "()' is final; CGLIB cannot override it, so it runs unadvised on the target",
                    "'" + method + "()' помечен final; CGLIB не может его переопределить, метод выполняется на target без advice",
                    List.of("target", "method:" + method), state());
            phase = "done";
            return this;
        }

        phase = "advice";
        String handler = "JDK".equals(strategy) ? "InvocationHandler" : "MethodInterceptor";
        record("advice", "advice", method);
        Trace.event("PROXY_ADVICE",
                "The " + handler + " runs the advice chain before '" + method + "()'",
                handler + " выполняет цепочку advice перед '" + method + "()'",
                List.of("advice", "method:" + method), state());

        phase = "delegate";
        String how = "JDK".equals(strategy)
                ? "the handler invokes '" + method + "()' on the wrapped target reference"
                : "the interceptor calls super to reach the real '" + method + "()'";
        String howRu = "JDK".equals(strategy)
                ? "handler вызывает '" + method + "()' на завёрнутой ссылке target"
                : "interceptor вызывает super, чтобы дойти до настоящего '" + method + "()'";
        record("target", "execute", method);
        Trace.event("PROXY_DELEGATE",
                "Delegation: " + how,
                "Делегирование: " + howRu,
                List.of("target", "method:" + method), state());

        phase = "return";
        record("proxy", "return", method);
        Trace.event("PROXY_RETURN",
                "'" + method + "()' returns through the proxy; the call is complete",
                "'" + method + "()' возвращается через proxy; вызов завершён",
                List.of("proxy", "method:" + method), state());
        phase = "done";
        return this;
    }

    private VisualProxyFactory defineMethod(String name, boolean isFinal) {
        methods.add(new Method(name, isFinal));
        Trace.event("PROXY_METHOD_DEFINED",
                "Target declares " + (isFinal ? "final " : "") + "method '" + name + "()'",
                "Target объявляет " + (isFinal ? "final " : "") + "метод '" + name + "()'",
                List.of("method:" + name), state());
        return this;
    }

    private Method find(String name) {
        for (Method m : methods) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        return null;
    }

    private void record(String actor, String action, String method) {
        log.add(new Step("s" + (++stepId), actor, action, method));
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("target", target);
        s.put("finalClass", finalClass);
        s.put("proxyTargetClass", proxyTargetClass);
        s.put("interfaces", new ArrayList<>(interfaces));
        s.put("methods", methodState());
        s.put("strategy", strategy);
        s.put("reason", reason);
        s.put("proxyName", proxyName);
        s.put("relation", relation);
        s.put("supertype", supertype);
        s.put("created", created);
        s.put("blocked", blocked);
        s.put("phase", phase);
        s.put("activeMethod", activeMethod);
        s.put("log", logState());
        return s;
    }

    private List<Object> methodState() {
        List<Object> out = new ArrayList<>();
        for (Method m : methods) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", m.name);
            e.put("isFinal", m.isFinal);
            out.add(e);
        }
        return out;
    }

    private List<Object> logState() {
        List<Object> out = new ArrayList<>();
        for (Step step : log) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", step.id);
            e.put("actor", step.actor);
            e.put("action", step.action);
            e.put("method", step.method);
            out.add(e);
        }
        return out;
    }

    private static final class Method {
        final String name;
        final boolean isFinal;

        Method(String name, boolean isFinal) {
            this.name = name;
            this.isFinal = isFinal;
        }
    }

    private record Step(String id, String actor, String action, String method) {
    }
}
