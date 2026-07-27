import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        VisualPropertySources env = VisualPropertySources.springBoot();

        // The same key, defined five times, deliberately bottom-up: if the
        // order of these calls mattered, the file would win.
        env.packagedProperties("server.port=8080");    // in the jar
        env.externalProperties("server.port=8081");    // ./config/, next to the jar
        env.environmentVariables("SERVER_PORT=9000");  // docker run -e / a k8s env block
        env.systemProperties("server.port=9100");      // java -Dserver.port=9100 -jar app.jar
        env.commandLine("--server.port=9200");         // java -jar app.jar --server.port=9200

        System.out.println("server.port = " + env.getProperty("server.port"));

        env.precedence();
        System.out.println("Five definitions, one winner -- decided by rank, not by who wrote last.");
    }
}
