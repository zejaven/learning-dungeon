import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        // What the artefact ships with. The code asks for this exact spelling:
        // @Value("${job.timeout}") or a @ConfigurationProperties field.
        app.fileProperty("job.timeout", "60s");

        // The interview question, asked out loud.
        String variable = app.envNameFor("job.timeout");
        System.out.println("variable = " + variable);

        // The deployment sets it. Nothing inside the jar changes.
        app.export(variable, "30s");
        System.out.println("job.timeout = " + app.resolve("job.timeout"));
    }
}
