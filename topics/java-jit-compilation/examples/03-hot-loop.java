import visual.VisualJit;

public class Playground {
    public static void main(String[] args) {
        VisualJit jit = new VisualJit("loop-jvm", 3);

        for (int i = 0; i < 4; i++) {
            jit.call("sumLoop");
        }
    }
}
