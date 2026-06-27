import visual.VisualProxyFactory;

public class Playground {
    public static void main(String[] args) {
        // No interface to implement, so a JDK dynamic proxy is impossible.
        // Spring falls back to CGLIB, which generates a subclass of the bean.
        new VisualProxyFactory("ReportService")
                .method("generate")
                .createProxy()
                .invoke("generate");
    }
}
