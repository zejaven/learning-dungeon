import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        app.fileProperty("job.timeout", "60s");
        app.export("JOB_TIMEOUT", "30s");

        // Spring converts the key it is asked for, then looks.
        System.out.println("Environment    = " + app.resolve("job.timeout"));

        // The JVM converts nothing at all.
        System.out.println("getenv(dotted) = " + app.getenv("job.timeout"));
        System.out.println("getenv(shouty) = " + app.getenv("JOB_TIMEOUT"));
    }
}
