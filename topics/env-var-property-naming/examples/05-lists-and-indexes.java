import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        // A list of two targets in application.properties.
        app.fileProperty("job.targets[0].url", "https://staging.example/a");
        app.fileProperty("job.targets[0].name", "primary");
        app.fileProperty("job.targets[1].url", "https://staging.example/b");

        // The index is spelled with an underscore on each side.
        String variable = app.envNameFor("job.targets[0].url");
        System.out.println("variable = " + variable);

        // Overriding one element hands the whole list to the environment.
        app.export(variable, "https://prod.example/a");
        System.out.println("targets[0].url = " + app.resolve("job.targets[0].url"));

        app.report();
    }
}
