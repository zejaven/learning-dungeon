import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        VisualDockerBuild docker = VisualDockerBuild.daemon();

        // Two FROM blocks in one Dockerfile. The first has Maven and a JDK and
        // builds the jar; the second has only a JRE and receives that jar.
        // Copying pom.xml BEFORE src/ is what keeps the dependency download
        // cached when only the sources change.
        Dockerfile dockerfile = Dockerfile.from("maven:3.9-eclipse-temurin-21", "builder")
                .workdir("/build")
                .copy("pom.xml", "/build/pom.xml", 0, Content.BUILD_FILE)
                .run("mvn -B dependency:go-offline", 300)
                .copy("src/", "/build/src/", 1, Content.SOURCE_TREE)
                .run("mvn -B package -DskipTests", 45)
                .stage("eclipse-temurin:21-jre")
                .workdir("/app")
                .copyFrom("builder", "/build/target/app.jar", "/app/app.jar", 45)
                .expose(8080)
                .entrypoint("java -jar /app/app.jar");

        docker.build("shop-api:1.0.0", dockerfile);

        // Rebuild after a code change: the pom and the downloaded dependencies
        // are still cache hits, so Maven does not go to the network again.
        docker.editCode("OrderController.java");
        docker.build("shop-api:1.0.1", dockerfile);

        docker.report();
        System.out.println("The machine that builds the jar is not the machine that ships.");
    }
}
