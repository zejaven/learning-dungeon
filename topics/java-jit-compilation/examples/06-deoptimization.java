import visual.VisualJit;

public class Playground {
    public static void main(String[] args) {
        VisualJit jit = new VisualJit("format-jvm", 3);

        for (int i = 0; i < 4; i++) {
            jit.call("formatPrice");
        }

        jit.deoptimize("formatPrice", "new receiver type");
        jit.call("formatPrice");
    }
}
