import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        app.fileProperty("job.timeout", "60s");

        // The dot was dropped instead of being converted to an underscore.
        app.export("JOBTIMEOUT", "30s");
        System.out.println("job.timeout = " + app.resolve("job.timeout"));

        // The same mistake in the other direction: the prefix was forgotten.
        app.export("TIMEOUT", "30s");
        System.out.println("job.timeout = " + app.resolve("job.timeout"));

        // The name the rule actually produces.
        app.export("JOB_TIMEOUT", "30s");
        System.out.println("job.timeout = " + app.resolve("job.timeout"));
    }
}
