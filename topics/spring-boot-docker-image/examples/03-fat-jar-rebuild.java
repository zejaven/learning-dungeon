import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        VisualDockerBuild docker = VisualDockerBuild.daemon();

        // One COPY brings in the whole fat jar: your classes and every
        // dependency in a single layer that can only change as a unit.
        Dockerfile dockerfile = Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .copy("target/app.jar", "/app/app.jar", 45, Content.FAT_JAR)
                .expose(8080)
                .entrypoint("java -jar /app/app.jar");

        docker.build("shop-api:1.0.0", dockerfile);
        docker.push("shop-api:1.0.0");

        // A typical commit: one line of one class.
        docker.editCode("OrderController.java");

        docker.build("shop-api:1.0.1", dockerfile);
        docker.push("shop-api:1.0.1");

        docker.report();
        System.out.println("One changed line, 45 MB re-uploaded. The layer is the unit of change.");
    }
}
