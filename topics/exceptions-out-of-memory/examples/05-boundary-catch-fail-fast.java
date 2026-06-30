import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        VisualException vm = new VisualException();

        vm.heapBudget(16);
        vm.call("main", "Throwable", true);
        vm.call("worker");
        vm.allocateMemory("batch import", 32);

        // A boundary may record diagnostics, then stop or restart the process.
        vm.failFast("write diagnostics, alert, and restart the process");
    }
}
