import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        // Compare the main Throwable branches without throwing real failures.
        VisualException vm = new VisualException();

        // Checked: callers must handle or declare it.
        vm.describe("IOException");

        // Unchecked: usually a programming or validation problem.
        vm.describe("NullPointerException");

        // Error: a serious JVM/process problem, not normal business flow.
        vm.describe("OutOfMemoryError");
    }
}
