import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        // A machine with Docker installed and a project Maven has already
        // packaged into target/app.jar. Building the image is the step after
        // `mvn package`, not instead of it.
        VisualDockerBuild docker = VisualDockerBuild.daemon();

        // The Dockerfile every Spring Boot project starts with: a Java runtime
        // to run on, the jar, and the command that launches it. Note the JRE
        // base image -- the JDK compiler is a build-time tool, not a run-time one.
        Dockerfile dockerfile = Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .copy("target/app.jar", "/app/app.jar", 45, Content.FAT_JAR)
                .expose(8080)
                .entrypoint("java -jar /app/app.jar");

        docker.build("shop-api:1.0.0", dockerfile);
        docker.run("shop-api:1.0.0", 512);
        docker.report();

        System.out.println("Five instructions is a working image. The rest of this topic is making it good.");
    }
}
