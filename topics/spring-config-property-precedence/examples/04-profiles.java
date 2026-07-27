import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        VisualPropertySources env = VisualPropertySources.springBoot();
        env.packagedProperties("spring.datasource.url=jdbc:h2:mem:dev");

        // application-prod.properties ships inside the SAME jar...
        env.profileProperties("prod", "spring.datasource.url=jdbc:postgresql://db:5432/shop");

        // ...and is not consulted at all while no profile is active.
        System.out.println("no profile -> " + env.getProperty("spring.datasource.url"));

        // SPRING_PROFILES_ACTIVE=prod, and the same file jumps above the plain one.
        env.activateProfiles("prod");
        System.out.println("prod       -> " + env.getProperty("spring.datasource.url"));

        // A profile reorders the FILES. It does not lift a file above the environment.
        env.environmentVariables("SPRING_DATASOURCE_URL=jdbc:postgresql://replica:5432/shop");
        System.out.println("prod + env -> " + env.getProperty("spring.datasource.url"));
    }
}
