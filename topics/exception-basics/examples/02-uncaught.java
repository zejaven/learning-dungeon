import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        // Nobody catches the exception, so it unwinds the whole stack and the
        // thread terminates with a stack trace.
        VisualException vm = new VisualException();

        vm.call("main");        // no try/catch
        vm.call("readConfig");  // no try/catch

        // readConfig throws; it propagates through readConfig, then main, then out.
        vm.throwException("NullPointerException", "config is null");
    }
}
