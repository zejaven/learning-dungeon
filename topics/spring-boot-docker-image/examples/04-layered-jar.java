import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        VisualDockerBuild docker = VisualDockerBuild.daemon();

        // The same jar, extracted into the four layers Spring Boot defines and
        // copied in order of how often they change: dependencies first,
        // your own classes last.
        Dockerfile dockerfile = Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .copy("dependencies/", "/app/", 43, Content.DEPENDENCIES)
                .copy("spring-boot-loader/", "/app/", 1, Content.STATIC)
                .copy("snapshot-dependencies/", "/app/", 0, Content.DEPENDENCIES)
                .copy("application/", "/app/", 1, Content.APPLICATION)
                .expose(8080)
                .entrypoint("java -jar /app/application.jar");

        docker.build("shop-api:1.0.0", dockerfile);
        docker.push("shop-api:1.0.0");

        // Exactly the change from the previous example.
        docker.editCode("OrderController.java");

        docker.build("shop-api:1.0.1", dockerfile);
        docker.push("shop-api:1.0.1");

        docker.report();
        System.out.println("Same 235 MB image, but a deploy now costs 1 MB instead of 45 MB.");
    }
}
