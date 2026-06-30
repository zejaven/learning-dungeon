import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        VisualException vm = new VisualException();

        vm.heapBudget(16);
        vm.call("main", "Exception", true);
        vm.call("loadReport");

        // This is deterministic simulation: no real large array is allocated.
        vm.allocateMemory("large report", 32);
    }
}
