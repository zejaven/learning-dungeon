import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        // A Kubernetes manifest accepts dots in a variable name; bash does not.
        app.export("job.timeout", "10s");
        System.out.println("job.timeout      = " + app.resolve("job.timeout"));

        // Lowercase: the lookup folds case, the convention does not.
        app.export("job_readtimeout", "20s");
        System.out.println("job.read-timeout = " + app.resolve("job.read-timeout"));

        // The older spelling, where a dash became an underscore.
        app.export("JOB_WRITE_TIMEOUT", "30s");
        System.out.println("job.write-timeout = " + app.resolve("job.write-timeout"));

        // Add the canonical name next to the dotted one: it is checked first.
        app.export("JOB_TIMEOUT", "40s");
        System.out.println("job.timeout      = " + app.resolve("job.timeout"));
    }
}
