import visual.VisualLambda;

public class Playground {
    private static final VisualLambda trace = new VisualLambda("Runnable", "void run()");

    public static void main(String[] args) {
        String name = "Nina";
        trace.created("greeting", "() -> System.out.println(\"Hello, \" + name)");
        trace.captured("name", name);
        doSomeLogic(() -> System.out.println("Hello, " + name));
    }

    static void doSomeLogic(Runnable greeting) {
        trace.passed("doSomeLogic", "greeting");
        trace.invokeRunnable("greeting.run()", greeting);
    }
}
