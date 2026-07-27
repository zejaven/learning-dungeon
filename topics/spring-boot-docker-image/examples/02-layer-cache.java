import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        VisualDockerBuild docker = VisualDockerBuild.daemon();

        Dockerfile dockerfile = Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .copy("target/app.jar", "/app/app.jar", 45, Content.FAT_JAR)
                .expose(8080)
                .entrypoint("java -jar /app/app.jar");

        // First build: the base image is pulled and every layer is written.
        docker.build("shop-api:1.0.0", dockerfile);

        // Second build with nothing touched in between. Every instruction has
        // the same cache key as before, so the daemon reuses the layers it
        // already has -- this is why a no-op rebuild takes a second.
        docker.build("shop-api:1.0.0", dockerfile);

        docker.report();
        System.out.println("A layer is reused only while its instruction AND everything below it are unchanged.");
    }
}
