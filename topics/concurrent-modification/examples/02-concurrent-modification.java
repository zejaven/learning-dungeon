import visual.VisualConcurrentList;

public class Playground {
    public static void main(String[] args) {
        // Structurally changing the list while iterating breaks the modCount check.
        VisualConcurrentList tasks = new VisualConcurrentList("tasks", VisualConcurrentList.FAIL_FAST);

        tasks.add("wash");
        tasks.add("cook");
        tasks.add("serve");

        tasks.iterator();   // expectedModCount is recorded here
        tasks.next();       // reads "wash"
        tasks.add("clean"); // structural change -> modCount no longer matches
        tasks.next();       // throws ConcurrentModificationException
    }
}
