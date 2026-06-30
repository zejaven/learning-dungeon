import visual.VisualConcurrentList;

public class Playground {
    public static void main(String[] args) {
        // CopyOnWriteArrayList: an iterator reads a frozen snapshot and never throws.
        VisualConcurrentList tasks = new VisualConcurrentList("tasks", VisualConcurrentList.COPY_ON_WRITE);

        tasks.add("wash");
        tasks.add("cook");

        tasks.iterator();   // freezes a snapshot of [wash, cook]
        tasks.next();       // reads "wash" from the snapshot
        tasks.add("serve"); // write copies the backing array; snapshot is unchanged
        tasks.next();       // still reads "cook" from the old snapshot, no CME
        tasks.next();       // snapshot exhausted -> ITERATION_DONE
    }
}
