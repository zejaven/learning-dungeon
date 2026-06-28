package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A teaching model for Java static nested classes. It does not inspect bytecode:
 * example programs call it next to real nested-class code so the UI can show the
 * mental model interviewers usually expect.
 */
public class VisualStaticNestedClass {

    private final String outerType;
    private final String staticNestedType;
    private final String innerType;
    private final List<Obj> outerObjects = new ArrayList<>();
    private final List<Obj> nestedObjects = new ArrayList<>();
    private final Map<String, String> staticMembers = new LinkedHashMap<>();

    private int outerCounter;
    private int nestedCounter;
    private String note = "Static nested class belongs to the enclosing type, not to an enclosing object.";

    public VisualStaticNestedClass() {
        this("Outer", "Outer.Helper", "Outer.Inner");
    }

    public VisualStaticNestedClass(String outerType, String staticNestedType, String innerType) {
        this.outerType = outerType;
        this.staticNestedType = staticNestedType;
        this.innerType = innerType;
        Trace.event("STATIC_NESTED_DECLARED",
                "Declared " + staticNestedType + " inside " + outerType
                        + ". The nested type is in the outer type's namespace, not inside each outer object.",
                "Объявлен " + staticNestedType + " внутри " + outerType
                        + ". Вложенный тип находится в пространстве имён внешнего типа, а не внутри каждого объекта внешнего класса.",
                List.of("type:static"), state());
    }

    public String createOuter(String label, String... fields) {
        Obj obj = new Obj("outer" + (++outerCounter), outerType, label, null, fields);
        outerObjects.add(obj);
        note = "A normal outer object exists on the heap. Static nested objects do not require it.";
        Trace.event("OUTER_INSTANCE_CREATED",
                "Created outer object " + obj.label + ". A static nested object does not need this object, but a regular inner object does.",
                "Создан объект внешнего класса " + obj.label + ". Объект static nested class не требует этот объект, а обычный inner class требует.",
                List.of("outer:" + obj.id, "type:outer"), state());
        return obj.id;
    }

    public String createStaticNested(String label, String... fields) {
        Obj obj = new Obj("nested" + (++nestedCounter), staticNestedType, label, null, fields);
        nestedObjects.add(obj);
        note = "The object has no hidden outer reference.";
        Trace.event("STATIC_NESTED_CREATED",
                "Created " + obj.label + " as a " + staticNestedType
                        + " object. It has no hidden reference to a " + outerType + " instance.",
                "Создан " + obj.label + " как объект " + staticNestedType
                        + ". У него нет скрытой ссылки на экземпляр " + outerType + ".",
                List.of("nested:" + obj.id, "type:static"), state());
        return obj.id;
    }

    public String createInner(String outerId, String label, String... fields) {
        Obj outer = findOuter(outerId);
        Obj obj = new Obj("nested" + (++nestedCounter), innerType, label, outerId, fields);
        nestedObjects.add(obj);
        note = "A non-static inner object carries a hidden reference to its enclosing outer object.";
        Trace.event("INNER_CLASS_CREATED",
                "Created " + obj.label + " as a non-static " + innerType
                        + " object. It carries a hidden reference to " + outer.label + ".",
                "Создан " + obj.label + " как объект обычного inner class " + innerType
                        + ". Он хранит скрытую ссылку на " + outer.label + ".",
                List.of("nested:" + obj.id, "outer:" + outer.id, "type:inner"), state());
        return obj.id;
    }

    public void setStaticField(String ownerType, String field, String value) {
        String key = ownerType + "." + field;
        staticMembers.put(key, value);
        note = "Static fields are class state, not fields of each object.";
        Trace.event("STATIC_MEMBER_SET",
                "Set " + key + " = " + value
                        + ". This state belongs to the class, not to each nested object.",
                "Установлено " + key + " = " + value
                        + ". Это состояние принадлежит классу, а не каждому вложенному объекту.",
                List.of("member:" + key, "type:static"), state());
    }

    public String accessStaticField(String ownerType, String field) {
        String key = ownerType + "." + field;
        String value = staticMembers.get(key);
        note = "Access static members through the type name.";
        Trace.event("STATIC_MEMBER_ACCESS",
                "Accessed " + key + " through the type name. The value " + value
                        + " is shared class state.",
                "Доступ к " + key + " выполнен через имя типа. Значение " + value
                        + " является общим состоянием класса.",
                List.of("member:" + key, "type:static"), state());
        return value;
    }

    private Obj findOuter(String id) {
        for (Obj obj : outerObjects) {
            if (obj.id.equals(id)) return obj;
        }
        throw new IllegalArgumentException("Unknown outer object id: " + id);
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", "staticNestedClass");
        s.put("outerType", outerType);
        s.put("staticNestedType", staticNestedType);
        s.put("innerType", innerType);
        s.put("note", note);

        List<Object> types = new ArrayList<>();
        types.add(type("type:outer", outerType, "enclosing class", false));
        types.add(type("type:static", staticNestedType, "static nested class", false));
        types.add(type("type:inner", innerType, "inner class", true));
        s.put("types", types);

        List<Object> members = new ArrayList<>();
        for (Map.Entry<String, String> e : staticMembers.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "member:" + e.getKey());
            m.put("name", e.getKey());
            m.put("value", e.getValue());
            members.add(m);
        }
        s.put("staticMembers", members);
        s.put("outerObjects", objects(outerObjects));
        s.put("nestedObjects", objects(nestedObjects));
        return s;
    }

    private static Map<String, Object> type(String id, String name, String role, boolean needsOuter) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", id);
        t.put("name", name);
        t.put("role", role);
        t.put("needsOuter", needsOuter);
        return t;
    }

    private static List<Object> objects(List<Obj> source) {
        List<Object> result = new ArrayList<>();
        for (Obj obj : source) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", obj.id);
            m.put("type", obj.type);
            m.put("label", obj.label);
            m.put("outerRef", obj.outerRef);
            List<Object> fields = new ArrayList<>();
            for (Map.Entry<String, String> e : obj.fields.entrySet()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("name", e.getKey());
                f.put("value", e.getValue());
                fields.add(f);
            }
            m.put("fields", fields);
            result.add(m);
        }
        return result;
    }

    private static final class Obj {
        final String id;
        final String type;
        final String label;
        final String outerRef;
        final Map<String, String> fields = new LinkedHashMap<>();

        Obj(String id, String type, String label, String outerRef, String... fieldPairs) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.outerRef = outerRef;
            for (String pair : fieldPairs) {
                int eq = pair.indexOf('=');
                if (eq >= 0) {
                    fields.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
    }
}
