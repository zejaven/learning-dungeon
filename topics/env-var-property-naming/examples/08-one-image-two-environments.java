import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        // The defaults baked into the jar, once, for every environment.
        app.fileProperty("job.timeout", "60s");
        app.fileProperty("job.batch-size", "100");
        app.fileProperty("job.enabled", "true");

        // What production sets. Note the dash: JOB_BATCHSIZE, not JOB_BATCH_SIZE.
        app.export("JOB_TIMEOUT", "30s");
        app.export("JOB_BATCHSIZE", "500");

        System.out.println("timeout    = " + app.resolve("job.timeout"));
        System.out.println("batch-size = " + app.resolve("job.batch-size"));
        System.out.println("enabled    = " + app.resolve("job.enabled"));

        app.report();
    }
}
