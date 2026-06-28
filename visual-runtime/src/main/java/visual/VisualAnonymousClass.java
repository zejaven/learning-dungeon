package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A teaching model for Java anonymous classes. It records the target type,
 * compiler-generated runtime class, object reference, captured locals, and
 * method calls so the frontend can replay the mental model step by step.
 */
public class VisualAnonymousClass {

    private final String targetType;
    private String targetKind = "";
    private String method = "";
    private String variableName = "";
    private String generatedClassName = "";
    private boolean anonymousClass;
    private final List<Map<String, Object>> captured = new ArrayList<>();
    private Map<String, Object> lastCall;
    private Map<String, Object> handoff;

    public VisualAnonymousClass(String targetType) {
        this.targetType = targetType;
    }

    public void target(String targetKind, String method) {
        this.targetKind = targetKind;
        this.method = method;
        Trace.event("ANON_TARGET_DECLARED",
                "Target type " + targetType + " defines the contract; the anonymous class must implement "
                        + method + ".",
                "Целевой тип " + targetType + " задает контракт; анонимный класс должен реализовать "
                        + method + ".",
                List.of("card:target"), state());
    }

    public void created(String variableName, Object object) {
        this.variableName = variableName;
        Class<?> runtimeClass = object == null ? null : object.getClass();
        this.generatedClassName = runtimeClass == null ? "null" : runtimeClass.getName();
        this.anonymousClass = runtimeClass != null && runtimeClass.isAnonymousClass();
        Trace.event("ANON_CLASS_CREATED",
                "Created anonymous runtime class " + generatedClassName + " and stored its object in "
                        + variableName + ".",
                "Создан анонимный runtime-класс " + generatedClassName + ", его объект сохранен в "
                        + variableName + ".",
                List.of("card:generated", "card:object"), state());
    }

    public void captured(String name, Object value) {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("name", name);
        local.put("value", show(value));
        captured.add(local);
        Trace.event("ANON_LOCAL_CAPTURED",
                "The anonymous class reads effectively final local variable " + name + " = " + show(value) + ".",
                "Анонимный класс читает effectively final локальную переменную " + name + " = "
                        + show(value) + ".",
                List.of("card:capture"), state());
    }

    public void called(String method, Object result) {
        lastCall = new LinkedHashMap<>();
        lastCall.put("method", method);
        lastCall.put("result", show(result));
        Trace.event("ANON_METHOD_CALLED",
                "Called the overridden method " + method + " on the anonymous object.",
                "Вызван переопределенный метод " + method + " у анонимного объекта.",
                List.of("card:call"), state());
    }

    public void passed(String api, String argumentName) {
        handoff = new LinkedHashMap<>();
        handoff.put("api", api);
        handoff.put("argument", argumentName);
        Trace.event("ANON_OBJECT_PASSED",
                "Passed the anonymous object " + argumentName + " into " + api + ".",
                "Анонимный объект " + argumentName + " передан в " + api + ".",
                List.of("card:handoff", "card:object"), state());
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("targetType", targetType);
        s.put("targetKind", targetKind);
        s.put("method", method);
        s.put("relation", relation());
        s.put("variableName", variableName);
        s.put("generatedClassName", generatedClassName);
        s.put("anonymousClass", anonymousClass);
        s.put("captured", new ArrayList<>(captured));
        s.put("lastCall", lastCall == null ? null : new LinkedHashMap<>(lastCall));
        s.put("handoff", handoff == null ? null : new LinkedHashMap<>(handoff));
        return s;
    }

    private String relation() {
        return "interface".equals(targetKind) ? "implements" : "extends";
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
