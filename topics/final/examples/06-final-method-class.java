import visual.VisualFinal;

public class Playground {
    public static void main(String[] args) {
        VisualFinal f = new VisualFinal();

        // A final method: subclasses inherit it but cannot override it. This
        // locks down behaviour the class relies on.
        f.method("compute()");

        // A final class: it cannot be subclassed at all. `String` is the classic
        // example, which is part of why String is immutable and safe to share.
        f.clazz("Money");
    }
}
