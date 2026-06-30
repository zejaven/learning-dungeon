import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        VisualException vm = new VisualException();

        vm.heapBudget(32);
        vm.allocateMemory("baseline objects", 20);
        vm.allocateMemory("image cache", 8, "cache");

        // Optional memory can be released before retrying bounded work.
        vm.releaseCaches();
        vm.allocateMemory("next request", 8);
    }
}
