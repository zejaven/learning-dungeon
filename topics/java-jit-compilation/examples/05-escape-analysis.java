import visual.VisualJit;

public class Playground {
    public static void main(String[] args) {
        VisualJit jit = new VisualJit("geometry-jvm", 3);

        for (int i = 0; i < 3; i++) {
            jit.call("distance");
        }
        jit.eliminateAllocation("distance", "Point");
    }
}
