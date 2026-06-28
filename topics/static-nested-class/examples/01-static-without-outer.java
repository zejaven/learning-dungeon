import visual.VisualStaticNestedClass;

public class Playground {
    static class Invoice {
        static class NumberFormatter {
            private final String prefix;

            NumberFormatter(String prefix) {
                this.prefix = prefix;
            }

            String format(int number) {
                return prefix + number;
            }
        }
    }

    public static void main(String[] args) {
        VisualStaticNestedClass scene = new VisualStaticNestedClass(
                "Invoice", "Invoice.NumberFormatter", "Invoice.Item");

        scene.createStaticNested("formatter", "pattern=INV-0000");

        Invoice.NumberFormatter formatter = new Invoice.NumberFormatter("INV-");
        formatter.format(42);
    }
}
