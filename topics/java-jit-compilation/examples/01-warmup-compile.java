import visual.VisualJit;

public class Playground {
    public static void main(String[] args) {
        VisualJit jit = new VisualJit("checkout-jvm", 4);

        for (int i = 0; i < 5; i++) {
            jit.call("calculateTotal");
        }
    }
}
