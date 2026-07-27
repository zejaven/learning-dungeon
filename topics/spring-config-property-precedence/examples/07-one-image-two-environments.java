import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        // ONE jar, built once and promoted through the pipeline unchanged.
        // Its application.properties holds the values a developer needs locally.
        VisualPropertySources staging = VisualPropertySources.springBoot();
        staging.packagedProperties(
                "spring.datasource.url=jdbc:h2:mem:dev",
                "spring.datasource.password=dev",
                "logging.level.root=INFO");
        staging.environmentVariables(
                "SPRING_DATASOURCE_URL=jdbc:postgresql://staging-db:5432/shop",
                "SPRING_DATASOURCE_PASSWORD=staging-secret");
        System.out.println("staging url = " + staging.getProperty("spring.datasource.url"));

        // The same artefact, a different deployment, a different set of variables.
        VisualPropertySources prod = VisualPropertySources.springBoot();
        prod.packagedProperties(
                "spring.datasource.url=jdbc:h2:mem:dev",
                "spring.datasource.password=dev",
                "logging.level.root=INFO");
        prod.environmentVariables(
                "SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/shop",
                "SPRING_DATASOURCE_PASSWORD=prod-secret");
        System.out.println("prod url    = " + prod.getProperty("spring.datasource.url"));

        // Everything nobody overrode is identical in both -- that is what makes
        // staging a rehearsal for production rather than a different program.
        System.out.println("log level   = " + prod.getProperty("logging.level.root"));
        prod.precedence();
    }
}
