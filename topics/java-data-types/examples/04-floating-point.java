import visual.VisualDataTypes;

public class Playground {
    public static void main(String[] args) {
        VisualDataTypes t = new VisualDataTypes();

        // float and double store numbers in binary, and 0.1 has no exact binary
        // representation. So 0.1 + 0.2 is NOT exactly 0.3.
        t.floatingImprecision("sum");

        // Lesson: never use double for money — use BigDecimal or integer cents.
    }
}
