import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        VisualPropertySources env = VisualPropertySources.springBoot();

        // The keys as the code and the file spell them: dotted, with dashes.
        env.packagedProperties(
                "spring.datasource.url=jdbc:h2:mem:dev",
                "spring.jpa.hibernate.ddl-auto=update");

        // No shell lets you export a variable called `spring.datasource.url`,
        // so the deployment sets the only spelling it can.
        env.environmentVariables(
                "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/shop",
                "SPRING_JPA_HIBERNATE_DDLAUTO=validate");

        // The code still asks for the key it was written with, and still gets
        // the variable: Spring converts the key, not the variable.
        System.out.println("url      = " + env.getProperty("spring.datasource.url"));
        System.out.println("ddl-auto = " + env.getProperty("spring.jpa.hibernate.ddl-auto"));

        // The JVM's own view of the environment is not relaxed at all.
        System.out.println("getenv   = " + env.getenv("spring.datasource.url"));
    }
}
