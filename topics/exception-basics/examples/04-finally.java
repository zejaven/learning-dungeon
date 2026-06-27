import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        // withResource has a finally but no catch: its finally still runs as the
        // exception unwinds, then main's catch handles it.
        VisualException vm = new VisualException();

        vm.call("main", "Exception", false);    // catch (Exception e) — catches everything below Exception
        vm.call("withResource", null, true);     // try { ... } finally { close() }  (no catch)

        vm.throwException("IllegalStateException", "stream already closed");
    }
}
