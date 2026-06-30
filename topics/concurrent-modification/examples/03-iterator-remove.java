import visual.VisualConcurrentList;

public class Playground {
    public static void main(String[] args) {
        // The safe single-threaded way to remove while iterating: Iterator.remove().
        VisualConcurrentList tasks = new VisualConcurrentList("tasks", VisualConcurrentList.FAIL_FAST);

        tasks.add("wash");
        tasks.add("skip");
        tasks.add("serve");

        tasks.iterator();
        tasks.next();           // reads "wash"
        tasks.next();           // reads "skip"
        tasks.iteratorRemove(); // removes "skip" through the iterator, stays valid
        tasks.next();           // reads "serve" without a CME
    }
}
