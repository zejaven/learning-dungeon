import visual.VisualEnvBinding;

public class Playground {
    public static void main(String[] args) {
        VisualEnvBinding app = VisualEnvBinding.application();

        // No dash: the conversion is only dots and case.
        System.out.println(app.envNameFor("job.timeout"));

        // A dash inside one element. It is deleted, not replaced.
        System.out.println(app.envNameFor("job.read-timeout"));

        // The same rule on a key everybody has actually typed.
        System.out.println(app.envNameFor("spring.jpa.hibernate.ddl-auto"));

        // Long keys are no different: still one underscore per dot.
        System.out.println(app.envNameFor("management.endpoints.web.exposure.include"));
    }
}
