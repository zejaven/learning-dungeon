import visual.VisualProxyFactory;

public class Playground {
    public static void main(String[] args) {
        // CGLIB must subclass the target. A final class cannot be subclassed,
        // so proxy creation fails outright. Give the bean an interface (for a
        // JDK proxy) or drop the final modifier to fix this.
        new VisualProxyFactory("ReportService")
                .finalClass()
                .method("generate")
                .createProxy();
    }
}
