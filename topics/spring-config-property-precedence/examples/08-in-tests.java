import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        // A laptop where SERVER_PORT and SPRING_DATASOURCE_URL are exported in
        // .bashrc, running an integration test.
        VisualPropertySources env = VisualPropertySources.springBoot();
        env.packagedProperties(
                "server.port=8080",
                "spring.datasource.url=jdbc:h2:mem:dev");
        env.environmentVariables(
                "SERVER_PORT=9000",
                "SPRING_DATASOURCE_URL=jdbc:postgresql://staging-db:5432/shop");

        // @SpringBootTest(properties = {
        //     "server.port=0", "spring.datasource.url=jdbc:h2:mem:test" })
        env.testProperties("server.port=0", "spring.datasource.url=jdbc:h2:mem:test");

        System.out.println("server.port = " + env.getProperty("server.port"));
        System.out.println("url         = " + env.getProperty("spring.datasource.url"));
        System.out.println("Inlined test properties outrank even the command line -- that is what keeps a test hermetic.");
    }
}
