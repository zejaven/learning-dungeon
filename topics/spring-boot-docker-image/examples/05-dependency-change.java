import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        VisualDockerBuild docker = VisualDockerBuild.daemon();

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

        // Layering does not make every change cheap -- it makes the FREQUENT
        // change cheap. Touching pom.xml moves the dependency layer, and
        // everything copied after it has to be written again.
        docker.addDependency("spring-boot-starter-actuator");

        docker.build("shop-api:1.1.0", dockerfile);
        docker.push("shop-api:1.1.0");

        docker.report();
        System.out.println("Order layers by how often they change, not by what they are.");
    }
}
