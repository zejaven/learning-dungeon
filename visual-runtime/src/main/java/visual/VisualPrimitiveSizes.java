package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small teaching model for the byte widths of Java primitive values.
 *
 * <p>The Java language fixes the value width of byte, short, int, long, float,
 * double, and char. boolean is deliberately shown as the exception: it has two
 * values, but the language does not define a portable byte count for storage.
 * The trace state keeps this table deterministic so the frontend can render it
 * as a byte-size reference.
 */
public class VisualPrimitiveSizes {

    private String focus = "table";

    public VisualPrimitiveSizes() {
        emit("PRIMITIVE_SIZE_TABLE",
                "Primitive size table: byte=1, short=2, int/float=4, long/double=8, char=2 bytes. boolean has no fixed Java byte count.",
                "Таблица размеров примитивов: byte=1, short=2, int/float=4, long/double=8, char=2 байта. У boolean нет фиксированного числа байт в Java.",
                List.of());
    }

    public void showTable() {
        focus = "table";
        emit("PRIMITIVE_SIZE_TABLE",
                "The full table is the interview baseline: memorize bytes, but keep the boolean caveat.",
                "Полная таблица - база для собеседования: запомните байты, но не забудьте оговорку про boolean.",
                List.of());
    }

    public void showIntegerFamily() {
        focus = "integer";
        emit("PRIMITIVE_INTEGRAL_SIZES",
                "Integer primitives grow by width: byte is 1 byte, short is 2, int is 4, and long is 8.",
                "Целочисленные примитивы растут по ширине: byte - 1 байт, short - 2, int - 4, long - 8.",
                List.of("type:byte", "type:short", "type:int", "type:long"));
    }

    public void showFloatingFamily() {
        focus = "floating";
        emit("PRIMITIVE_FLOATING_SIZES",
                "Floating primitives are fixed too: float is 4 bytes and double is 8 bytes.",
                "Примитивы с плавающей точкой тоже фиксированы: float - 4 байта, double - 8 байт.",
                List.of("type:float", "type:double"));
    }

    public void showChar() {
        focus = "char";
        emit("PRIMITIVE_CHAR_SIZE",
                "char is 2 bytes because it is a 16-bit UTF-16 code unit, not necessarily a whole Unicode character.",
                "char занимает 2 байта, потому что это 16-битная кодовая единица UTF-16, а не обязательно целый символ Unicode.",
                List.of("type:char"));
    }

    public void showBooleanCaveat() {
        focus = "boolean";
        emit("PRIMITIVE_BOOLEAN_CAVEAT",
                "boolean is the trap: Java defines true and false, but not a portable storage size in bytes.",
                "boolean - это ловушка: Java задает значения true и false, но не переносимый размер хранения в байтах.",
                List.of("type:boolean"));
    }

    public void showStorageContext() {
        focus = "storage";
        emit("PRIMITIVE_STORAGE_CONTEXT",
                "A primitive value width is not the full object or array memory footprint: fields may be padded, arrays have headers, and locals use JVM slots.",
                "Ширина значения примитива - это не полный след объекта или массива в памяти: поля могут выравниваться, у массивов есть заголовки, а локальные переменные используют JVM slots.",
                List.of("type:int", "type:long", "type:boolean"));
    }

    private void emit(String event, String descEn, String descRu, List<String> highlight) {
        Trace.event(event, descEn, descRu, highlight, state());
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("focus", focus);
        List<Object> rows = new ArrayList<>();
        for (Primitive p : TABLE) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", p.type);
            row.put("family", p.family);
            row.put("bits", p.bits);
            row.put("bytes", p.bytes);
            row.put("storage", p.storage);
            row.put("range", p.range);
            rows.add(row);
        }
        s.put("rows", rows);
        return s;
    }

    private record Primitive(String type, String family, Integer bits, Integer bytes,
                             String storage, String range) {
    }

    private static final List<Primitive> TABLE = List.of(
            new Primitive("byte", "integer", 8, 1, "fixed", "-128..127"),
            new Primitive("short", "integer", 16, 2, "fixed", "-32768..32767"),
            new Primitive("int", "integer", 32, 4, "fixed", "-2^31..2^31-1"),
            new Primitive("long", "integer", 64, 8, "fixed", "-2^63..2^63-1"),
            new Primitive("float", "floating", 32, 4, "fixed", "~7 significant digits"),
            new Primitive("double", "floating", 64, 8, "fixed", "~16 significant digits"),
            new Primitive("char", "character", 16, 2, "fixed", "0..65535 (\\u0000..\\uffff)"),
            new Primitive("boolean", "logical", null, null, "not-specified", "true / false")
    );
}
