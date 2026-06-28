package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A teaching resource for try-with-resources examples. It implements
 * {@link AutoCloseable}, emits trace events on open/use/close, and lets examples
 * show how Java keeps the main exception while attaching close failures as
 * suppressed exceptions.
 */
public final class VisualResource implements AutoCloseable {

    private static final Map<String, ResourceState> RESOURCES = new LinkedHashMap<>();
    private static final List<String> CLOSE_SEQUENCE = new ArrayList<>();
    private static final List<String> SUPPRESSED_EXCEPTIONS = new ArrayList<>();
    private static int nextOpenOrder = 0;
    private static String primaryException;
    private static String caughtException;

    private final String name;
    private final boolean failOnClose;
    private boolean open = true;

    private VisualResource(String name, boolean failOnClose) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("resource name must not be blank");
        }
        synchronized (VisualResource.class) {
            if (RESOURCES.containsKey(name)) {
                throw new IllegalArgumentException("resource already exists: " + name);
            }
            this.name = name;
            this.failOnClose = failOnClose;
            RESOURCES.put(name, new ResourceState(name, ++nextOpenOrder, failOnClose));
        }
        Trace.event("RESOURCE_OPENED",
                "Opened resource '" + name + "'",
                "Открыт ресурс '" + name + "'",
                List.of("resource:" + name),
                state("open"));
    }

    public static VisualResource open(String name) {
        return new VisualResource(name, false);
    }

    public static VisualResource openFailingClose(String name) {
        return new VisualResource(name, true);
    }

    public void use(String action) {
        ensureOpen();
        synchronized (VisualResource.class) {
            ResourceState resource = RESOURCES.get(name);
            resource.lastAction = action;
        }
        Trace.event("RESOURCE_USED",
                "Used resource '" + name + "' for " + action,
                "Ресурс '" + name + "' использован для " + action,
                List.of("resource:" + name),
                state("work"));
    }

    public void failDuringUse(String action, String message) throws Exception {
        ensureOpen();
        synchronized (VisualResource.class) {
            ResourceState resource = RESOURCES.get(name);
            resource.lastAction = action;
            primaryException = "Exception: " + message;
        }
        Trace.event("RESOURCE_PRIMARY_EXCEPTION",
                "Work with resource '" + name + "' failed: " + message
                        + ". Java remembers this as the primary exception.",
                "Работа с ресурсом '" + name + "' завершилась ошибкой: " + message
                        + ". Java запоминает это как основное исключение.",
                List.of("resource:" + name, "exception:primary"),
                state("body-failed"));
        throw new Exception(message);
    }

    @Override
    public void close() throws Exception {
        ensureOpen();

        boolean closesLastOpened;
        synchronized (VisualResource.class) {
            closesLastOpened = countOpenResources() > 1 && name.equals(lastOpenResourceName());
            ResourceState resource = RESOURCES.get(name);
            resource.closeOrder = CLOSE_SEQUENCE.size() + 1;
            CLOSE_SEQUENCE.add(name);
            open = false;

            if (failOnClose) {
                resource.status = "close failed";
            } else {
                resource.status = "closed";
            }
        }

        if (failOnClose) {
            String message = "close failed: " + name;
            Trace.event("RESOURCE_CLOSE_FAILED",
                    "Closing resource '" + name + "' threw: " + message,
                    "Закрытие ресурса '" + name + "' выбросило: " + message,
                    List.of("resource:" + name, "exception:close"),
                    state("close-failed"));
            throw new Exception(message);
        }

        String event = closesLastOpened ? "RESOURCE_REVERSE_CLOSE" : "RESOURCE_CLOSED";
        Trace.event(event,
                "Closed resource '" + name + "' automatically",
                "Ресурс '" + name + "' автоматически закрыт",
                List.of("resource:" + name),
                state("closed"));
    }

    public static void reportCaught(Throwable throwable) {
        synchronized (VisualResource.class) {
            caughtException = format(throwable);
            SUPPRESSED_EXCEPTIONS.clear();
            for (Throwable suppressed : throwable.getSuppressed()) {
                SUPPRESSED_EXCEPTIONS.add(format(suppressed));
            }
        }

        Trace.event("RESOURCE_CAUGHT_EXCEPTION",
                "Caught " + format(throwable),
                "Поймано " + format(throwable),
                List.of("exception:caught"),
                state("caught"));

        if (throwable.getSuppressed().length > 0) {
            Trace.event("RESOURCE_SUPPRESSED_EXCEPTION",
                    "Close failure is attached to the primary exception as suppressed",
                    "Ошибка закрытия прикреплена к основному исключению как suppressed",
                    List.of("exception:suppressed"),
                    state("suppressed"));
        }
    }

    public static void runFinally(String label) {
        Trace.event("RESOURCE_FINALLY",
                "finally block ran after resource closing: " + label,
                "Блок finally выполнился после закрытия ресурсов: " + label,
                List.of("phase:finally"),
                state("finally"));
    }

    static void resetForTesting() {
        synchronized (VisualResource.class) {
            RESOURCES.clear();
            CLOSE_SEQUENCE.clear();
            SUPPRESSED_EXCEPTIONS.clear();
            nextOpenOrder = 0;
            primaryException = null;
            caughtException = null;
        }
    }

    private void ensureOpen() {
        if (!open) {
            throw new IllegalStateException("resource already closed: " + name);
        }
    }

    private static synchronized int countOpenResources() {
        int count = 0;
        for (ResourceState resource : RESOURCES.values()) {
            if ("open".equals(resource.status)) {
                count++;
            }
        }
        return count;
    }

    private static synchronized String lastOpenResourceName() {
        String result = null;
        int maxOrder = -1;
        for (ResourceState resource : RESOURCES.values()) {
            if ("open".equals(resource.status) && resource.openOrder > maxOrder) {
                maxOrder = resource.openOrder;
                result = resource.name;
            }
        }
        return result;
    }

    private static Object state(String phase) {
        Map<String, Object> s = new LinkedHashMap<>();
        synchronized (VisualResource.class) {
            s.put("phase", phase);
            s.put("primaryException", primaryException);
            s.put("caughtException", caughtException);
            s.put("suppressedExceptions", new ArrayList<>(SUPPRESSED_EXCEPTIONS));
            s.put("closeSequence", new ArrayList<>(CLOSE_SEQUENCE));

            List<Object> resources = new ArrayList<>();
            for (ResourceState resource : RESOURCES.values()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("name", resource.name);
                r.put("status", resource.status);
                r.put("openOrder", resource.openOrder);
                r.put("closeOrder", resource.closeOrder);
                r.put("closeFails", resource.closeFails);
                r.put("lastAction", resource.lastAction);
                resources.add(r);
            }
            s.put("resources", resources);
        }
        return s;
    }

    private static String format(Throwable throwable) {
        return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    }

    private static final class ResourceState {
        final String name;
        final int openOrder;
        final boolean closeFails;
        String status = "open";
        Integer closeOrder;
        String lastAction = "";

        ResourceState(String name, int openOrder, boolean closeFails) {
            this.name = name;
            this.openOrder = openOrder;
            this.closeFails = closeFails;
        }
    }
}
