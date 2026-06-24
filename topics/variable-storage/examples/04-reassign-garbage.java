import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // p references the first object.
        mem.newObject("p", "Point", "x=1", "y=2");

        // p = new Point(7, 8); repoints p at a brand-new object.
        // The first object is now unreachable from any variable: it is garbage.
        mem.reassignNew("p", "Point", "x=7", "y=8");
    }
}
