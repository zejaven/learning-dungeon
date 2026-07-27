import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        String variable = app.envNameFor("job.timeout");

        // One name, five places it gets written.
        app.deploymentForms(variable, "30s");
    }
}
