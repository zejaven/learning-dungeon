import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        // An Environment is an ordered list of property sources. Nothing is
        // configured yet -- the ladder itself already exists.
        VisualPropertySources env = VisualPropertySources.springBoot();

        // application.properties travelled inside the jar and says 8080.
        env.packagedProperties("server.port=8080", "spring.application.name=shop-api");

        // Ops start the very same jar with one variable set in the deployment.
        env.environmentVariables("SERVER_PORT=9000");

        // Which one does the application actually see?
        String port = env.getProperty("server.port");

        System.out.println("server.port = " + port);
        System.out.println("The variable wins: it sits above both copies of application.properties.");
    }
}
