package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dependency-free teaching model of Spring bean creation. It is not Spring
 * itself: it reproduces the interview mental model of singleton bean definitions,
 * constructor dependency resolution, circular dependency detection, and deferred
 * references such as {@code @Lazy} or {@code ObjectProvider}.
 *
 * <p>Every step emits {@link Trace} events so a topic visualizer can replay the
 * bean graph without depending on Spring libraries in learner code.
 */
public class VisualSpringBeanFactory {

    private final String name;
    private final Map<String, Bean> beans = new LinkedHashMap<>();
    private final List<String> creationStack = new ArrayList<>();
    private String phase = "defined";

    public VisualSpringBeanFactory() {
        this("applicationContext");
    }

    public VisualSpringBeanFactory(String name) {
        this.name = name;
        Trace.event("SPRING_CONTEXT_CREATED",
                "Created teaching ApplicationContext '" + name + "' with no bean definitions yet",
                "Создан учебный ApplicationContext '" + name + "' без bean definitions",
                List.of(), state());
    }

    /**
     * Registers a bean definition and returns a small builder for its dependencies.
     */
    public BeanSpec bean(String beanName) {
        Bean bean = ensureBean(beanName);
        if (!bean.explicit) {
            bean.explicit = true;
            Trace.event("SPRING_BEAN_REGISTERED",
                    "Registered bean definition '" + beanName + "'",
                    "Зарегистрирован bean definition '" + beanName + "'",
                    List.of(beanToken(beanName)), state());
        }
        return new BeanSpec(bean);
    }

    /**
     * Creates all known singleton beans in registration order. Returns false when
     * a constructor dependency cycle prevents startup.
     */
    public boolean refresh() {
        phase = "refreshing";
        Trace.event("SPRING_REFRESH_STARTED",
                "ApplicationContext starts creating singleton beans in dependency order",
                "ApplicationContext начинает создавать singleton beans в порядке зависимостей",
                List.of(), state());

        for (Bean bean : beans.values()) {
            if (!"ready".equals(bean.status)) {
                if (!createBean(bean.name)) {
                    phase = "failed";
                    Trace.event("SPRING_CONTEXT_FAILED",
                            "Context refresh failed because the constructor dependency graph contains a cycle",
                            "Context refresh завершился ошибкой, потому что в constructor dependency graph есть цикл",
                            List.of(), state());
                    return false;
                }
            }
        }

        phase = "ready";
        Trace.event("SPRING_CONTEXT_READY",
                "All singleton beans are ready; deferred lazy/provider references can be used later",
                "Все singleton beans готовы; отложенные lazy/provider references можно использовать позже",
                List.of(), state());
        return true;
    }

    /**
     * Simulates the first method call through a {@code @Lazy} proxy.
     */
    public boolean useLazy(String fromBean, String targetBean) {
        return resolveDeferred(fromBean, targetBean, "lazy");
    }

    /**
     * Simulates calling {@code ObjectProvider.getObject()} or a similar provider.
     */
    public boolean requestProvider(String fromBean, String targetBean) {
        Dependency dependency = dependency(fromBean, targetBean, "provider");
        dependency.status = "resolving";
        Trace.event("SPRING_PROVIDER_REQUESTED",
                "'" + fromBean + "' calls ObjectProvider.getObject() for '" + targetBean
                        + "'; the lookup happens after startup",
                "'" + fromBean + "' вызывает ObjectProvider.getObject() для '" + targetBean
                        + "'; lookup происходит после startup",
                List.of(beanToken(fromBean), beanToken(targetBean), edgeToken(dependency)),
                state());

        boolean ok = resolveDeferredDependency(dependency);
        if (ok) {
            Trace.event("SPRING_PROVIDER_TARGET_RETURNED",
                    "ObjectProvider returned ready bean '" + targetBean + "' to '" + fromBean + "'",
                    "ObjectProvider вернул готовый bean '" + targetBean + "' в '" + fromBean + "'",
                    List.of(beanToken(fromBean), beanToken(targetBean), edgeToken(dependency)),
                    state());
        }
        return ok;
    }

    private boolean resolveDeferred(String fromBean, String targetBean, String kind) {
        Dependency dependency = dependency(fromBean, targetBean, kind);
        dependency.status = "resolving";
        Trace.event("SPRING_LAZY_TARGET_REQUESTED",
                "'" + fromBean + "' first touches the @Lazy proxy for '" + targetBean
                        + "'; now the real bean is needed",
                "'" + fromBean + "' впервые обращается к @Lazy proxy для '" + targetBean
                        + "'; теперь нужен настоящий bean",
                List.of(beanToken(fromBean), beanToken(targetBean), edgeToken(dependency)),
                state());

        boolean ok = resolveDeferredDependency(dependency);
        if (ok) {
            Trace.event("SPRING_LAZY_TARGET_RESOLVED",
                    "The @Lazy proxy from '" + fromBean + "' now points to ready bean '" + targetBean + "'",
                    "@Lazy proxy из '" + fromBean + "' теперь указывает на готовый bean '" + targetBean + "'",
                    List.of(beanToken(fromBean), beanToken(targetBean), edgeToken(dependency)),
                    state());
        }
        return ok;
    }

    private boolean resolveDeferredDependency(Dependency dependency) {
        Bean target = ensureBean(dependency.to);
        if (!"ready".equals(target.status)) {
            if (!createBean(target.name)) {
                dependency.status = "failed";
                return false;
            }
        }
        dependency.status = "resolved";
        return true;
    }

    private boolean createBean(String beanName) {
        Bean bean = ensureBean(beanName);

        if ("ready".equals(bean.status)) {
            return true;
        }

        if (creationStack.contains(beanName)) {
            for (String nameOnStack : creationStack) {
                ensureBean(nameOnStack).status = "failed";
            }
            bean.status = "failed";
            Trace.event("SPRING_CYCLE_DETECTED",
                    "Cycle detected: " + cyclePath(beanName)
                            + ". Spring cannot finish constructor injection for this graph",
                    "Обнаружен цикл: " + cyclePath(beanName)
                            + ". Spring не может завершить constructor injection для такого graph",
                    cycleHighlight(beanName), state());
            return false;
        }

        bean.status = "creating";
        creationStack.add(beanName);
        Trace.event("SPRING_BEAN_CREATION_STARTED",
                "Creating bean '" + beanName + "'; it is pushed onto the creation stack",
                "Создаётся bean '" + beanName + "'; он добавлен в creation stack",
                List.of(beanToken(beanName), stackToken(beanName)), state());

        for (Dependency dependency : bean.dependencies) {
            if ("constructor".equals(dependency.kind)) {
                dependency.status = "resolving";
                Trace.event("SPRING_DEPENDENCY_RESOLVE",
                        "'" + dependency.from + "' needs constructor dependency '" + dependency.to + "' now",
                        "'" + dependency.from + "' сейчас нужен constructor dependency '" + dependency.to + "'",
                        List.of(beanToken(dependency.from), beanToken(dependency.to), edgeToken(dependency)),
                        state());
                if (!createBean(dependency.to)) {
                    dependency.status = "failed";
                    bean.status = "failed";
                    pop(beanName);
                    return false;
                }
                dependency.status = "resolved";
            } else if ("lazy".equals(dependency.kind)) {
                dependency.status = "deferred";
                Trace.event("SPRING_LAZY_PROXY_INJECTED",
                        "'" + dependency.from + "' receives a @Lazy proxy for '" + dependency.to
                                + "' instead of creating that bean immediately",
                        "'" + dependency.from + "' получает @Lazy proxy для '" + dependency.to
                                + "' вместо немедленного создания этого bean",
                        List.of(beanToken(dependency.from), beanToken(dependency.to), edgeToken(dependency)),
                        state());
            } else if ("provider".equals(dependency.kind)) {
                dependency.status = "deferred";
                Trace.event("SPRING_PROVIDER_INJECTED",
                        "'" + dependency.from + "' receives an ObjectProvider for '" + dependency.to
                                + "'; the actual lookup is deferred",
                        "'" + dependency.from + "' получает ObjectProvider для '" + dependency.to
                                + "'; настоящий lookup отложен",
                        List.of(beanToken(dependency.from), beanToken(dependency.to), edgeToken(dependency)),
                        state());
            }
        }

        pop(beanName);
        bean.status = "ready";
        Trace.event("SPRING_BEAN_READY",
                "Bean '" + beanName + "' is fully constructed and ready for injection",
                "Bean '" + beanName + "' полностью создан и готов к injection",
                List.of(beanToken(beanName)), state());
        return true;
    }

    private void pop(String beanName) {
        int last = creationStack.size() - 1;
        if (last >= 0 && creationStack.get(last).equals(beanName)) {
            creationStack.remove(last);
        }
    }

    private Bean ensureBean(String beanName) {
        return beans.computeIfAbsent(beanName, Bean::new);
    }

    private void addDependency(Bean from, String targetBean, String kind) {
        ensureBean(targetBean);
        Dependency dependency = new Dependency(from.name, targetBean, kind);
        from.dependencies.add(dependency);
        Trace.event("SPRING_DEPENDENCY_LINKED",
                "Linked '" + from.name + "' to '" + targetBean + "' as a "
                        + labelFor(kind) + " dependency",
                "Связали '" + from.name + "' с '" + targetBean + "' как "
                        + labelFor(kind) + " dependency",
                List.of(beanToken(from.name), beanToken(targetBean), edgeToken(dependency)),
                state());
    }

    private Dependency dependency(String fromBean, String targetBean, String kind) {
        Bean from = ensureBean(fromBean);
        for (Dependency dependency : from.dependencies) {
            if (dependency.to.equals(targetBean) && dependency.kind.equals(kind)) {
                return dependency;
            }
        }
        throw new IllegalArgumentException("No " + kind + " dependency from "
                + fromBean + " to " + targetBean);
    }

    private String cyclePath(String repeatedBean) {
        List<String> path = new ArrayList<>(creationStack);
        path.add(repeatedBean);
        return String.join(" -> ", path);
    }

    private List<String> cycleHighlight(String repeatedBean) {
        List<String> highlight = new ArrayList<>();
        for (String beanName : creationStack) {
            highlight.add(beanToken(beanName));
            highlight.add(stackToken(beanName));
        }
        highlight.add(beanToken(repeatedBean));
        if (!creationStack.isEmpty()) {
            highlight.add("edge:" + creationStack.get(creationStack.size() - 1) + "->" + repeatedBean);
        }
        return highlight;
    }

    private static String labelFor(String kind) {
        return switch (kind) {
            case "lazy" -> "@Lazy";
            case "provider" -> "ObjectProvider";
            default -> "constructor";
        };
    }

    private static String beanToken(String beanName) {
        return "bean:" + beanName;
    }

    private static String stackToken(String beanName) {
        return "stack:" + beanName;
    }

    private static String edgeToken(Dependency dependency) {
        return "edge:" + dependency.from + "->" + dependency.to;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("phase", phase);

        List<Object> beanList = new ArrayList<>();
        for (Bean bean : beans.values()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("name", bean.name);
            b.put("status", bean.status);
            beanList.add(b);
        }
        s.put("beans", beanList);

        List<Object> dependencyList = new ArrayList<>();
        for (Bean bean : beans.values()) {
            for (Dependency dependency : bean.dependencies) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("from", dependency.from);
                d.put("to", dependency.to);
                d.put("kind", dependency.kind);
                d.put("status", dependency.status);
                dependencyList.add(d);
            }
        }
        s.put("dependencies", dependencyList);
        s.put("stack", new ArrayList<>(creationStack));
        return s;
    }

    public final class BeanSpec {
        private final Bean bean;

        private BeanSpec(Bean bean) {
            this.bean = bean;
        }

        public BeanSpec dependsOn(String targetBean) {
            addDependency(bean, targetBean, "constructor");
            return this;
        }

        public BeanSpec lazyDependsOn(String targetBean) {
            addDependency(bean, targetBean, "lazy");
            return this;
        }

        public BeanSpec providerDependsOn(String targetBean) {
            addDependency(bean, targetBean, "provider");
            return this;
        }
    }

    private static final class Bean {
        final String name;
        final List<Dependency> dependencies = new ArrayList<>();
        boolean explicit;
        /** defined | creating | ready | failed */
        String status = "defined";

        Bean(String name) {
            this.name = name;
        }
    }

    private static final class Dependency {
        final String from;
        final String to;
        final String kind;
        /** pending | resolving | resolved | deferred | failed */
        String status = "pending";

        Dependency(String from, String to, String kind) {
            this.from = from;
            this.to = to;
            this.kind = kind;
        }
    }
}
