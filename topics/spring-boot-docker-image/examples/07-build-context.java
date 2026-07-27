import visual.VisualDockerBuild;
import visual.VisualDockerBuild.Content;
import visual.VisualDockerBuild.Context;
import visual.VisualDockerBuild.Dockerfile;

public class Playground {
    public static void main(String[] args) {
        // The dot in `docker build .` is not decoration: it is the folder the
        // CLI packs up and ships to the daemon before reading the Dockerfile.
        VisualDockerBuild docker = VisualDockerBuild.daemon(Context.of()
                .file("pom.xml", 0)
                .file("src/", 1)
                .file("target/", 240)
                .file(".git/", 90)
                .file("node_modules/", 180));

        Dockerfile dockerfile = Dockerfile.from("maven:3.9-eclipse-temurin-21", "builder")
                .workdir("/build")
                .copy("pom.xml", "/build/pom.xml", 0, Content.BUILD_FILE)
                .run("mvn -B dependency:go-offline", 300)
                .copy("src/", "/build/src/", 1, Content.SOURCE_TREE)
                .run("mvn -B package -DskipTests", 45)
                .stage("eclipse-temurin:21-jre")
                .workdir("/app")
                .copyFrom("builder", "/build/target/app.jar", "/app/app.jar", 45)
                .entrypoint("java -jar /app/app.jar");

        // Without a .dockerignore, half a gigabyte of local build output and
        // git history is transferred on every single build.
        docker.build("shop-api:1.0.0", dockerfile);

        // One file fixes it. The image is byte-for-byte the same.
        docker.dockerignore("target/", ".git/", "node_modules/");
        docker.build("shop-api:1.0.0", dockerfile);

        docker.report();
        System.out.println("Ignore what the image never needs -- but keep whatever a COPY still reads.");
    }
}
