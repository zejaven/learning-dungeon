import visual.VisualLambda;

public class Playground {
    private static final VisualLambda trace = new VisualLambda("Runnable", "void run()");

    public static void main(String[] args) {
        trace.created("logic", "() -> System.out.println(\"Hello\")");
        doSomeLogic(() -> System.out.println("Hello"));
    }

    static void doSomeLogic(Runnable logic) {
        trace.passed("doSomeLogic", "logic");
        trace.invokeRunnable("logic.run()", logic);
    }
}
