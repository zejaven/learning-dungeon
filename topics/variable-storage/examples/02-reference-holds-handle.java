import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // Point p = new Point(1, 2);
        // The Point object lives on the heap. The variable p stores only a
        // reference (a handle) to it, sitting in p's stack slot.
        mem.newObject("p", "Point", "x=1", "y=2");
    }
}
