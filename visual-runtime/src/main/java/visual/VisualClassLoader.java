package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of the JVM class-loading mechanism. It is NOT the
 * real {@link ClassLoader}: it reproduces the ideas an interviewer cares about —
 * the built-in loader hierarchy (Bootstrap, Platform, Application), the
 * <em>parent-delegation</em> model, the per-loader cache, custom loaders and
 * {@link ClassNotFoundException} — while emitting {@link Trace} events so the UI
 * can visualize every step.
 *
 * <p>Each loader holds a small fixed set of class names it "knows how to find"
 * (its search path) instead of really reading bytes from disk, and a cache of
 * the classes it has already defined. {@code loadClass} runs the standard
 * algorithm: check the cache, delegate to the parent first, and only define the
 * class itself if no ancestor could.
 */
public class VisualClassLoader {

    private final String name;
    private final VisualClassLoader parent;
    private final int level;
    /** Shared, ordered top-to-bottom (Bootstrap first) so state() can render the whole chain. */
    private final List<VisualClassLoader> all;
    /** Class names this loader can find on its own path. */
    private final Set<String> knows = new LinkedHashSet<>();
    /** Cache of classes this loader has already defined. */
    private final Set<String> loaded = new LinkedHashSet<>();

    private VisualClassLoader(String name, VisualClassLoader parent,
                             List<VisualClassLoader> all, String... knows) {
        this.name = name;
        this.parent = parent;
        this.all = all;
        this.level = parent == null ? 0 : parent.level + 1;
        for (String k : knows) {
            this.knows.add(k);
        }
        all.add(this);
    }

    /**
     * Builds the standard three-level JVM loader hierarchy and returns the
     * Application (system) loader at the bottom — the usual entry point.
     */
    public static VisualClassLoader standardHierarchy() {
        List<VisualClassLoader> all = new ArrayList<>();
        new VisualClassLoader("Bootstrap", null, all,
                "java.lang.Object", "java.lang.String", "java.util.HashMap");
        VisualClassLoader bootstrap = all.get(0);
        VisualClassLoader platform = new VisualClassLoader("Platform", bootstrap, all,
                "javax.sql.DataSource", "javax.crypto.Cipher");
        VisualClassLoader application = new VisualClassLoader("Application", platform, all,
                "com.app.Main", "com.app.Service", "com.app.Repository");

        application.emit("CLASSLOADER_CREATED",
                "Built the standard hierarchy: Bootstrap -> Platform -> Application "
                        + "(each loader knows its parent)",
                "Построена стандартная иерархия: Bootstrap -> Platform -> Application "
                        + "(каждый загрузчик знает своего родителя)",
                List.of(), null, null, "idle");
        return application;
    }

    /**
     * Creates a custom child loader under this one and returns it. The custom
     * loader sits at the bottom of the chain and knows the given classes.
     */
    public VisualClassLoader withChild(String childName, String... childKnows) {
        VisualClassLoader child = new VisualClassLoader(childName, this, all, childKnows);
        child.emit("CLASSLOADER_CREATED",
                "Added custom loader '" + childName + "' as a child of " + name,
                "Добавлен пользовательский загрузчик «" + childName + "» как потомок " + name,
                List.of("loader:" + childName), null, childName, "idle");
        return child;
    }

    /**
     * Loads a class starting from this loader, following the parent-delegation
     * model. Emits the request, the delegation up the chain and the outcome.
     */
    public void loadClass(String className) {
        emit("LOAD_REQUEST",
                "Request: load " + className + ", starting at the " + name + " loader",
                "Запрос: загрузить " + className + ", начиная с загрузчика " + name,
                List.of("loader:" + name), className, name, "request");
        VisualClassLoader who = doLoad(className);
        if (who == null) {
            emit("CLASS_NOT_FOUND",
                    "No loader in the chain could find " + className
                            + " — throws ClassNotFoundException",
                    "Ни один загрузчик в цепочке не нашёл " + className
                            + " — выбрасывается ClassNotFoundException",
                    List.of(), className, name, "notfound");
        }
    }

    /** @return the loader that loaded the class, or null if none could. */
    private VisualClassLoader doLoad(String className) {
        // 1. Already loaded by this loader? Return it from the cache.
        if (loaded.contains(className)) {
            emit("ALREADY_LOADED",
                    name + " already has " + className
                            + " in its cache — returns it without reloading",
                    name + " уже держит " + className
                            + " в кэше — возвращает без повторной загрузки",
                    List.of("loader:" + name, "class:" + className),
                    className, name, "cache");
            return this;
        }
        // 2. Delegate to the parent FIRST (parent-delegation model).
        if (parent != null) {
            emit("DELEGATE_UP",
                    name + " delegates to its parent " + parent.name + " before trying itself",
                    name + " делегирует своему родителю " + parent.name
                            + ", прежде чем пробовать сам",
                    List.of("loader:" + parent.name), className, parent.name, "delegate");
            VisualClassLoader byAncestor = parent.doLoad(className);
            if (byAncestor != null) {
                return byAncestor;
            }
        }
        // 3. No ancestor could load it: try to define it here.
        if (knows.contains(className)) {
            loaded.add(className);
            emit("CLASS_DEFINED",
                    name + " found " + className + " on its own path and defined the class",
                    name + " нашёл " + className + " на своём пути и определил класс",
                    List.of("loader:" + name, "class:" + className),
                    className, name, "define");
            return this;
        }
        // 4. Not here either — let the caller (the level below) try.
        return null;
    }

    private void emit(String event, String en, String ru, List<String> highlight,
                      String requested, String active, String phase) {
        Trace.event(event, en, ru, highlight, state(requested, active, phase));
    }

    private Object state(String requested, String active, String phase) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("requested", requested);
        s.put("active", active);
        s.put("phase", phase);
        List<Object> loaders = new ArrayList<>();
        for (VisualClassLoader cl : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", cl.name);
            m.put("level", cl.level);
            m.put("knows", new ArrayList<>(cl.knows));
            m.put("loaded", new ArrayList<>(cl.loaded));
            loaders.add(m);
        }
        s.put("loaders", loaders);
        return s;
    }
}
