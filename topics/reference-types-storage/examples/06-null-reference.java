import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.newObject("session", "Session", "id=42");

        // Setting the reference to null: the variable now points at no object.
        // The object is not erased immediately — it becomes garbage and waits
        // for the garbage collector.
        mem.setNull("session");

        System.out.println("null means 'no object'; the heap object becomes garbage.");
    }
}
