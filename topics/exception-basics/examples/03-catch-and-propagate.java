import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        // main wraps the call in try/catch (RuntimeException). The exception is
        // thrown deeper, propagates up one frame, and is caught in main.
        VisualException vm = new VisualException();

        vm.call("main", "RuntimeException", false); // try { ... } catch (RuntimeException e)
        vm.call("parsePort");                        // no handler here

        vm.throwException("NumberFormatException", "for input \"abc\"");
    }
}
