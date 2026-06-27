import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        // A tour of the Throwable hierarchy: classify common types without
        // throwing them. Each line shows where the type sits.
        VisualException vm = new VisualException();

        // Unchecked: subclasses of RuntimeException (compiler does NOT force handling).
        vm.describe("NullPointerException");
        vm.describe("IllegalArgumentException");

        // Checked: subclasses of Exception but NOT RuntimeException (must handle/declare).
        vm.describe("IOException");

        // Error: serious JVM problems you are not meant to catch.
        vm.describe("OutOfMemoryError");
    }
}
