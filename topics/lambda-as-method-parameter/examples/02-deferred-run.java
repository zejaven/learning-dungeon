import visual.VisualLambda;

public class Playground {
    private static final VisualLambda trace = new VisualLambda("Runnable", "void run()");

    public static void main(String[] args) {
        trace.created("cleanup", "() -> System.out.println(\"Clean the table\")");
        runAfterMessage(() -> System.out.println("Clean the table"));
    }

    static void runAfterMessage(Runnable cleanup) {
        trace.passed("runAfterMessage", "cleanup");
        System.out.println("The lambda body has not run yet.");
        trace.invokeRunnable("cleanup.run()", cleanup);
    }
}
