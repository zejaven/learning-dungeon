import visual.VisualJit;

public class Playground {
    public static void main(String[] args) {
        VisualJit jit = new VisualJit("service-jvm", 4);

        jit.call("rareAdminReport");
        jit.call("healthCheck");
        jit.call("dailyCleanup");
    }
}
