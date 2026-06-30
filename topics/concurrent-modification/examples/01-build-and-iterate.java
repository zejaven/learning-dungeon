import visual.VisualConcurrentList;

public class Playground {
    public static void main(String[] args) {
        // A normal fail-fast ArrayList. Iterating without changing it is fine.
        VisualConcurrentList tasks = new VisualConcurrentList("tasks", VisualConcurrentList.FAIL_FAST);

        tasks.add("wash");
        tasks.add("cook");
        tasks.add("serve");

        tasks.iterator();
        tasks.next();
        tasks.next();
        tasks.next();
        tasks.next(); // exhausts the iterator -> ITERATION_DONE
    }
}
