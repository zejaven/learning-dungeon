package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A teaching model for the Java {@code static} keyword. It is not a JVM: it
 * tracks the interview mental model that static members belong to the class,
 * instance fields belong to each object, the class initializes once on first
 * active use, static methods have no {@code this}, and static nested classes do
 * not require an outer object.
 */
public class VisualStatic {

    private final String className;
    private boolean initialized;
    private int initializationCount;
    private final Map<String, StaticField> staticFields = new LinkedHashMap<>();
    private final Map<String, Instance> instances = new LinkedHashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final List<NestedClass> nestedClasses = new ArrayList<>();

    public VisualStatic(String className) {
        this.className = className;
        Trace.event("STATIC_SCENE",
                "Class " + className + " is known, but its static initialization has not run yet",
                "Класс " + className + " известен, но его static-инициализация ещё не запускалась",
                List.of("class:" + className), state());
    }

    /**
     * Models first active use of the class. A second direct call shows that the
     * class was already initialized; normal helper methods silently reuse it.
     */
    public void initialize(String trigger) {
        if (!initialized) {
            doInitialize(trigger);
        } else {
            Trace.event("STATIC_CLASS_ALREADY_INIT",
                    "Another use of " + trigger + " does not initialize " + className
                            + " again; the class is already ready",
                    "Повторное использование " + trigger + " не инициализирует " + className
                            + " заново; класс уже готов",
                    List.of("class:" + className), state());
        }
    }

    /** Models assigning or updating a mutable static field. */
    public void staticField(String name, String value) {
        ensureInitialized(className + "." + name);
        staticFields.put(name, new StaticField(name, value, false));
        Trace.event("STATIC_FIELD_WRITE",
                "Shared static field " + className + "." + name + " = " + value
                        + " lives in the class area, not inside any one object",
                "Общее static-поле " + className + "." + name + " = " + value
                        + " живёт в области класса, а не внутри отдельного объекта",
                List.of("class:" + className, "static:" + name), state());
    }

    /**
     * Models a primitive/String compile-time constant. Reading such constants
     * can be inlined by Java code and does not necessarily initialize the class.
     */
    public void constant(String name, String value) {
        staticFields.put(name, new StaticField(name, value, true));
        Trace.event("STATIC_CONSTANT",
                className + "." + name + " is a static final compile-time constant; callers can inline it",
                className + "." + name + " — static final константа времени компиляции; вызывающий код может встроить её значение",
                List.of("static:" + name), state());
    }

    /** Models {@code new ClassName(...)} and the object's own instance fields. */
    public void newInstance(String instanceName, String... fields) {
        ensureInitialized("new " + className + "(...)");
        Instance instance = new Instance(instanceName, parseFields(fields));
        instances.put(instanceName, instance);
        Trace.event("INSTANCE_CREATED",
                "Created object " + instanceName + " from new " + className
                        + "(...). Its instance fields belong to this object",
                "Создан объект " + instanceName + " через new " + className
                        + "(...). Его instance-поля принадлежат именно этому объекту",
                List.of("class:" + className, "obj:" + instanceName), state());
    }

    /** Models changing one object's instance field. */
    public void instanceField(String instanceName, String fieldName, String value) {
        Instance instance = requireInstance(instanceName);
        instance.fields.put(fieldName, value);
        Trace.event("INSTANCE_FIELD_WRITE",
                instanceName + "." + fieldName + " = " + value
                        + " changes only this object; static fields remain shared by the class",
                instanceName + "." + fieldName + " = " + value
                        + " меняет только этот объект; static-поля остаются общими для класса",
                List.of("obj:" + instanceName, "field:" + instanceName + "." + fieldName), state());
    }

    /** Models calling a static method through the class. */
    public void callStatic(String methodName, String note) {
        ensureInitialized(className + "." + methodName);
        calls.add(new Call("class", className, methodName, note));
        Trace.event("STATIC_METHOD_CALL",
                "Called " + className + "." + methodName
                        + ". A static method is selected through the class and has no this",
                "Вызван " + className + "." + methodName
                        + ". Static-метод выбирается через класс и не имеет this",
                List.of("class:" + className, "call:" + methodName), state());
    }

    /** Models calling an instance method on one object. */
    public void callInstance(String instanceName, String methodName, String note) {
        requireInstance(instanceName);
        calls.add(new Call("object", instanceName, methodName, note));
        Trace.event("INSTANCE_METHOD_CALL",
                "Called " + instanceName + "." + methodName
                        + ". An instance method receives this for that object",
                "Вызван " + instanceName + "." + methodName
                        + ". Instance-метод получает this для этого объекта",
                List.of("obj:" + instanceName, "call:" + methodName), state());
    }

    /** Models a static nested class: namespaced inside the outer class, no outer this. */
    public void staticNestedClass(String nestedName, String note) {
        nestedClasses.add(new NestedClass(nestedName, note));
        Trace.event("STATIC_NESTED_CLASS",
                nestedName + " is a static nested class: it is grouped under "
                        + className + " but does not need an outer " + className + " object",
                nestedName + " — static nested class: он сгруппирован внутри "
                        + className + ", но не требует внешнего объекта " + className,
                List.of("class:" + className, "nested:" + nestedName), state());
    }

    private void ensureInitialized(String trigger) {
        if (!initialized) {
            doInitialize(trigger);
        }
    }

    private void doInitialize(String trigger) {
        initialized = true;
        initializationCount++;
        Trace.event("STATIC_CLASS_INIT",
                "First active use of " + trigger + " initializes " + className
                        + " once and prepares its static members",
                "Первое активное использование " + trigger + " один раз инициализирует "
                        + className + " и подготавливает его static-члены",
                List.of("class:" + className), state());
    }

    private Instance requireInstance(String instanceName) {
        Instance instance = instances.get(instanceName);
        if (instance == null) {
            throw new IllegalArgumentException("No instance named " + instanceName);
        }
        return instance;
    }

    private static Map<String, String> parseFields(String... fields) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String field : fields) {
            int eq = field.indexOf('=');
            if (eq < 0) {
                parsed.put(field.trim(), "");
            } else {
                parsed.put(field.substring(0, eq).trim(), field.substring(eq + 1).trim());
            }
        }
        return parsed;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("className", className);
        s.put("initialized", initialized);
        s.put("initializationCount", initializationCount);

        List<Object> fieldList = new ArrayList<>();
        for (StaticField field : staticFields.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", field.name);
            m.put("value", field.value);
            m.put("constant", field.constant);
            fieldList.add(m);
        }
        s.put("staticFields", fieldList);

        List<Object> objectList = new ArrayList<>();
        for (Instance instance : instances.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", instance.name);
            m.put("type", className);
            m.put("fields", fields(instance.fields));
            objectList.add(m);
        }
        s.put("instances", objectList);

        List<Object> callList = new ArrayList<>();
        for (Call call : calls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("target", call.target);
            m.put("receiver", call.receiver);
            m.put("method", call.method);
            m.put("note", call.note);
            callList.add(m);
        }
        s.put("calls", callList);

        List<Object> nestedList = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (NestedClass nested : nestedClasses) {
            if (!seen.add(nested.name)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", nested.name);
            m.put("note", nested.note);
            nestedList.add(m);
        }
        s.put("nestedClasses", nestedList);
        return s;
    }

    private static List<Object> fields(Map<String, String> fields) {
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("value", e.getValue());
            list.add(m);
        }
        return list;
    }

    private static final class StaticField {
        final String name;
        final String value;
        final boolean constant;

        StaticField(String name, String value, boolean constant) {
            this.name = name;
            this.value = value;
            this.constant = constant;
        }
    }

    private static final class Instance {
        final String name;
        final Map<String, String> fields;

        Instance(String name, Map<String, String> fields) {
            this.name = name;
            this.fields = fields;
        }
    }

    private static final class Call {
        final String target;
        final String receiver;
        final String method;
        final String note;

        Call(String target, String receiver, String method, String note) {
            this.target = target;
            this.receiver = receiver;
            this.method = method;
            this.note = note;
        }
    }

    private static final class NestedClass {
        final String name;
        final String note;

        NestedClass(String name, String note) {
            this.name = name;
            this.note = note;
        }
    }
}
