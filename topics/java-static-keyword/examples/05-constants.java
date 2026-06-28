import visual.VisualStatic;

public class Playground {
    public static void main(String[] args) {
        VisualStatic defaults = new VisualStatic("Defaults");

        // Compile-time constants are class-level values and may be inlined.
        defaults.constant("MAX_RETRIES", "3");
        defaults.constant("API_VERSION", "v1");

        // Reading or writing a non-constant static member is active use.
        defaults.staticField("loadedFromFile", "true");
    }
}
