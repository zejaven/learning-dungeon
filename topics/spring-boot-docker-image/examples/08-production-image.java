import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        VisualDockerBuild docker = VisualDockerBuild.daemon();

        // The naive image from the first example: no USER, so PID 1 is root.
        Dockerfile naive = Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .copy("target/app.jar", "/app/app.jar", 45, Content.FAT_JAR)
                .entrypoint("java -jar /app/app.jar");
        docker.build("shop-api:naive", naive);
        docker.run("shop-api:naive", 512);

        // The same application, ready for a cluster: layered so deploys are
        // small, a dedicated account instead of root, and a JVM told how much
        // of the container's limit it may actually use.
        Dockerfile hardened = Dockerfile.from("eclipse-temurin:21-jre")
                .workdir("/app")
                .run("useradd --system --no-create-home app", 0)
                .copy("dependencies/", "/app/", 43, Content.DEPENDENCIES)
                .copy("spring-boot-loader/", "/app/", 1, Content.STATIC)
                .copy("snapshot-dependencies/", "/app/", 0, Content.DEPENDENCIES)
                .copy("application/", "/app/", 1, Content.APPLICATION)
                .env("JAVA_TOOL_OPTIONS", "-XX:MaxRAMPercentage=75")
                .user("app")
                .expose(8080)
                .entrypoint("java -jar /app/application.jar");

        docker.build("shop-api:1.4.0", hardened);
        docker.run("shop-api:1.4.0", 512);

        docker.report();
        System.out.println("Same jar, same base image -- a different container to be woken up by at 3am.");
    }
}
