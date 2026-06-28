package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of <strong>Java's data types and how they work</strong>.
 * It is NOT a real JVM: it reproduces the mental model interviewers ask about —
 * Java has exactly two families of types. A <em>primitive</em> (one of the eight:
 * {@code byte}, {@code short}, {@code int}, {@code long}, {@code float},
 * {@code double}, {@code char}, {@code boolean}) holds its value directly, has a
 * fixed size in bits and a fixed value range, and is never {@code null}. A
 * <em>reference</em> type (classes, arrays, interfaces, wrappers like
 * {@code Integer}) holds a handle to an object on the heap and can be {@code null}.
 *
 * <p>From that split everything else follows: a fixed-width integer
 * <em>overflows</em> (wraps around) instead of growing; {@code char} is really an
 * unsigned 16-bit number; binary floating point is inexact; widening conversions
 * are implicit while narrowing ones need an explicit cast and can lose data;
 * autoboxing turns a primitive into a wrapper <em>object</em>, and the small
 * {@code Integer} cache makes {@code ==} on boxed values surprising.
 *
 * <p>The model keeps an ordered set of typed {@link Slot}s and emits {@link Trace}
 * events so the UI can draw each variable with its category, type, size and value.
 * Where the teaching point is a concrete numeric fact (overflow, {@code 0.1 + 0.2},
 * a narrowing cast, the {@code Integer} cache) the model computes it with real Java
 * arithmetic so the displayed result is exactly what the JVM would produce.
 */
public class VisualDataTypes {

    /** Declared variables, in declaration order. */
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    /** Optional one-line computation shown alongside the slots ({@code expr -> result}). */
    private Note note;

    public VisualDataTypes() {
        Trace.event("TYPE_SCENE",
                "Empty scene: no variables yet. Java has two families of types — primitives and references.",
                "Пустая сцена: переменных пока нет. В Java два семейства типов — примитивы и ссылки.",
                List.of(), state());
    }

    /**
     * Declares a primitive variable, e.g. {@code int i = 5;}. The model knows each
     * primitive's size in bits and value range, so it shows what fits where.
     */
    public void primitive(String name, String type, String value) {
        Meta m = META.get(type);
        slots.put(name, new Slot(name, "primitive", type, m.bits, value, m.range));
        note = null;
        Trace.event("TYPE_PRIMITIVE",
                type + " " + name + " = " + value + " — a primitive: " + m.bits
                        + " bits, value stored directly, never null (range " + m.range + ")",
                type + " " + name + " = " + value + " — примитив: " + m.bits
                        + " бит, значение хранится напрямую, никогда не null (диапазон " + m.range + ")",
                List.of("slot:" + name), state());
    }

    /**
     * Declares a reference variable, e.g. {@code String s = "hi";}. A reference type
     * holds a handle to an object on the heap and may be {@code null}.
     */
    public void reference(String name, String type, String value) {
        slots.put(name, new Slot(name, "reference", type, null, value, null));
        note = null;
        Trace.event("TYPE_REFERENCE",
                type + " " + name + " = " + value + " — a reference type: the variable holds a handle to a heap object (can be null)",
                type + " " + name + " = " + value + " — ссылочный тип: переменная хранит указатель на объект в куче (может быть null)",
                List.of("slot:" + name), state());
    }

    /**
     * Integer overflow: a fixed-width integer at its maximum wraps around to its
     * minimum instead of growing. Computed with real {@code int} arithmetic.
     */
    public void overflowInt(String name) {
        Slot s = slots.get(name);
        int v = Integer.parseInt(s.value);
        int wrapped = v + 1;            // real wrap-around at Integer.MAX_VALUE
        s.value = Integer.toString(wrapped);
        note = new Note(v + " + 1", Integer.toString(wrapped));
        Trace.event("TYPE_OVERFLOW",
                name + " + 1 overflows: " + v + " + 1 wraps to " + wrapped
                        + " — a fixed-width int has no room above MAX_VALUE",
                name + " + 1 переполняется: " + v + " + 1 заворачивается в " + wrapped
                        + " — у int фиксированной ширины нет места выше MAX_VALUE",
                List.of("slot:" + name), state());
    }

    /**
     * {@code char} is an unsigned 16-bit number: {@code 'A'} is the code 65, and
     * {@code 'A' + 1} promotes to {@code int} 66.
     */
    public void charIsANumber(String name) {
        Slot s = slots.get(name);
        char c = s.value.length() == 1 ? s.value.charAt(0) : s.value.replace("'", "").charAt(0);
        int code = c;
        note = new Note("(int) '" + c + "'", code + "  ('" + c + "' + 1 = " + (code + 1) + ", an int)");
        Trace.event("TYPE_CHAR",
                "'" + c + "' is really the number " + code + " — char is an unsigned 16-bit code; arithmetic on it promotes to int",
                "'" + c + "' — это на самом деле число " + code + " — char это беззнаковый 16-битный код; арифметика над ним повышается до int",
                List.of("slot:" + name), state());
    }

    /**
     * Binary floating point is inexact: {@code 0.1 + 0.2} is not exactly
     * {@code 0.3}. Computed with real {@code double} arithmetic.
     */
    public void floatingImprecision(String name) {
        double sum = 0.1 + 0.2;         // the famous 0.30000000000000004
        slots.put(name, new Slot(name, "primitive", "double", 64, Double.toString(sum), META.get("double").range));
        note = new Note("0.1 + 0.2", Double.toString(sum));
        Trace.event("TYPE_FLOAT",
                "double " + name + " = 0.1 + 0.2 = " + sum + " — binary floating point cannot represent 0.1 exactly, so it is not 0.3",
                "double " + name + " = 0.1 + 0.2 = " + sum + " — двоичная плавающая точка не может точно представить 0.1, поэтому это не 0.3",
                List.of("slot:" + name), state());
    }

    /**
     * Widening conversion (e.g. {@code int} to {@code long}/{@code double}) is
     * implicit and never loses the value.
     */
    public void widen(String target, String targetType, String source) {
        Slot src = slots.get(source);
        String result;
        if (targetType.equals("double") || targetType.equals("float")) {
            result = Double.toString(Double.parseDouble(src.value));
        } else {
            result = Long.toString(Long.parseLong(src.value));
        }
        Meta m = META.get(targetType);
        slots.put(target, new Slot(target, "primitive", targetType, m.bits, result, m.range));
        note = new Note(src.type + " " + source + " -> " + targetType, result + " (no loss)");
        Trace.event("TYPE_CONVERT",
                targetType + " " + target + " = " + source + " — widening " + src.type + " to " + targetType
                        + " is implicit and lossless",
                targetType + " " + target + " = " + source + " — расширение " + src.type + " до " + targetType
                        + " неявное и без потерь",
                List.of("slot:" + target, "slot:" + source), state());
    }

    /**
     * Narrowing conversion (e.g. {@code long} to {@code byte}) needs an explicit
     * cast and can silently lose data. Computed with the real cast.
     */
    public void narrow(String target, String targetType, String source) {
        Slot src = slots.get(source);
        long v = Long.parseLong(src.value);
        long casted;
        switch (targetType) {
            case "byte" -> casted = (byte) v;
            case "short" -> casted = (short) v;
            case "int" -> casted = (int) v;
            case "char" -> casted = (char) v;
            default -> casted = v;
        }
        boolean lossy = casted != v;
        Meta m = META.get(targetType);
        slots.put(target, new Slot(target, "primitive", targetType, m.bits, Long.toString(casted), m.range));
        note = new Note("(" + targetType + ") " + source + " /* " + v + " */",
                casted + (lossy ? "  (data LOST)" : "  (fits)"));
        Trace.event("TYPE_CONVERT",
                targetType + " " + target + " = (" + targetType + ") " + source + " — narrowing needs a cast; "
                        + v + " becomes " + casted + (lossy ? " (bits truncated, data lost)" : " (value fits)"),
                targetType + " " + target + " = (" + targetType + ") " + source + " — сужение требует приведения; "
                        + v + " превращается в " + casted + (lossy ? " (биты отброшены, данные потеряны)" : " (значение помещается)"),
                List.of("slot:" + target, "slot:" + source), state());
    }

    /**
     * Autoboxing: a primitive {@code int} is wrapped into an {@code Integer} object
     * — a reference type that lives on the heap and can be {@code null}.
     */
    public void box(String target, String wrapperType, String source) {
        Slot src = slots.get(source);
        slots.put(target, new Slot(target, "wrapper", wrapperType, null, src.value, null));
        note = new Note(wrapperType + ".valueOf(" + source + ")", "boxed object on the heap");
        Trace.event("TYPE_BOX",
                wrapperType + " " + target + " = " + source + " — autoboxing wraps the primitive into a "
                        + wrapperType + " object (a reference type on the heap)",
                wrapperType + " " + target + " = " + source + " — автоупаковка оборачивает примитив в объект "
                        + wrapperType + " (ссылочный тип в куче)",
                List.of("slot:" + target, "slot:" + source), state());
    }

    /**
     * The {@code Integer} cache: {@code Integer.valueOf} caches values in
     * -128..127, so {@code ==} (identity) is {@code true} for cached values but
     * {@code false} for larger ones. Computed with the real {@code Integer.valueOf}.
     */
    public void integerCacheCompare(int value) {
        boolean same = Integer.valueOf(value) == Integer.valueOf(value);   // real cache behaviour
        boolean cached = value >= -128 && value <= 127;
        note = new Note("Integer.valueOf(" + value + ") == Integer.valueOf(" + value + ")",
                same + (cached ? "  (cached: same object)" : "  (not cached: two objects)"));
        Trace.event("TYPE_CACHE",
                "Integer.valueOf(" + value + ") == Integer.valueOf(" + value + ") is " + same
                        + (cached ? " — " + value + " is in the -128..127 cache, so == sees one shared object"
                                  : " — " + value + " is outside the cache, so == compares two different objects"),
                "Integer.valueOf(" + value + ") == Integer.valueOf(" + value + ") равно " + same
                        + (cached ? " — " + value + " попадает в кэш -128..127, поэтому == видит один общий объект"
                                  : " — " + value + " вне кэша, поэтому == сравнивает два разных объекта"),
                List.of(), state());
    }

    /**
     * Default values: an uninitialised <em>field</em> of a primitive type is zero
     * ({@code 0}/{@code 0.0}/{@code ' '}/{@code false}); a reference field is
     * {@code null}. (Local variables get no default — they must be assigned.)
     */
    public void defaultValue(String name, String type) {
        String def;
        String category;
        Integer bits;
        String range;
        if (PRIMITIVE_DEFAULT.containsKey(type)) {
            def = PRIMITIVE_DEFAULT.get(type);
            category = "primitive";
            Meta m = META.get(type);
            bits = m.bits;
            range = m.range;
        } else {
            def = "null";
            category = "reference";
            bits = null;
            range = null;
        }
        slots.put(name, new Slot(name, category, type, bits, def, range));
        note = new Note(type + " " + name + " /* field, not assigned */", def);
        Trace.event("TYPE_DEFAULT",
                type + " " + name + " defaults to " + def + " — an unassigned field of a "
                        + ("reference".equals(category) ? "reference type is null" : "primitive type is zero/false"),
                type + " " + name + " по умолчанию " + def + " — неинициализированное поле "
                        + ("reference".equals(category) ? "ссылочного типа равно null" : "примитивного типа равно нулю/false"),
                List.of("slot:" + name), state());
    }

    // --- internals -------------------------------------------------------

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>();
        for (Slot slot : slots.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", slot.name);
            m.put("category", slot.category);
            m.put("type", slot.type);
            m.put("bits", slot.bits);
            m.put("value", slot.value);
            m.put("range", slot.range);
            list.add(m);
        }
        s.put("slots", list);
        if (note == null) {
            s.put("note", null);
        } else {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("expr", note.expr);
            n.put("result", note.result);
            s.put("note", n);
        }
        return s;
    }

    private static final class Slot {
        final String name;
        final String category;   // "primitive" | "wrapper" | "reference"
        final String type;
        final Integer bits;      // size in bits for a primitive; null for a reference
        String value;
        final String range;      // value range for a primitive; null otherwise

        Slot(String name, String category, String type, Integer bits, String value, String range) {
            this.name = name;
            this.category = category;
            this.type = type;
            this.bits = bits;
            this.value = value;
            this.range = range;
        }
    }

    private record Note(String expr, String result) {
    }

    private record Meta(int bits, String range) {
    }

    /** Size and range for each of the eight primitive types. */
    private static final Map<String, Meta> META = new LinkedHashMap<>();
    /** Default value of each primitive type for an unassigned field. */
    private static final Map<String, String> PRIMITIVE_DEFAULT = new LinkedHashMap<>();

    static {
        META.put("byte", new Meta(8, "-128..127"));
        META.put("short", new Meta(16, "-32768..32767"));
        META.put("int", new Meta(32, "-2^31..2^31-1"));
        META.put("long", new Meta(64, "-2^63..2^63-1"));
        META.put("float", new Meta(32, "~7 significant digits"));
        META.put("double", new Meta(64, "~16 significant digits"));
        META.put("char", new Meta(16, "0..65535 (\\u0000..\\uffff)"));
        META.put("boolean", new Meta(1, "true / false"));

        PRIMITIVE_DEFAULT.put("byte", "0");
        PRIMITIVE_DEFAULT.put("short", "0");
        PRIMITIVE_DEFAULT.put("int", "0");
        PRIMITIVE_DEFAULT.put("long", "0");
        PRIMITIVE_DEFAULT.put("float", "0.0");
        PRIMITIVE_DEFAULT.put("double", "0.0");
        PRIMITIVE_DEFAULT.put("char", "'\\u0000'");
        PRIMITIVE_DEFAULT.put("boolean", "false");
    }
}
