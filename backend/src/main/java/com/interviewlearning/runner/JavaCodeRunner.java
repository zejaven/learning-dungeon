package com.interviewlearning.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Compiles a single-file Java snippet (in process, using the JDK compiler API)
 * and runs the resulting class in a <em>separate</em> JVM with a heap cap and a
 * wall-clock timeout. The child JVM has the visual-runtime jar on its classpath
 * so user code can drive the learning models that emit {@code @@TRACE@@} events.
 */
@Service
public class JavaCodeRunner {

    private static final Logger log = LoggerFactory.getLogger(JavaCodeRunner.class);

    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+(?:final\\s+)?class\\s+(\\w+)");
    private static final Pattern ANY_CLASS = Pattern.compile("\\bclass\\s+(\\w+)");

    private final RepoPaths repoPaths;
    private final ObjectMapper mapper;
    private final int timeoutSeconds;
    private final String maxHeap;

    public JavaCodeRunner(RepoPaths repoPaths,
                          ObjectMapper mapper,
                          @Value("${app.runner.timeout-seconds:5}") int timeoutSeconds,
                          @Value("${app.runner.max-heap:128m}") String maxHeap) {
        this.repoPaths = repoPaths;
        this.mapper = mapper;
        this.timeoutSeconds = timeoutSeconds;
        this.maxHeap = maxHeap;
    }

    public RunResult run(String code) {
        if (code == null || code.isBlank()) {
            return RunResult.failure("No code provided.");
        }
        String className = detectClassName(code);
        Path workDir;
        try {
            workDir = Files.createTempDirectory("ilrun-");
        } catch (IOException e) {
            return RunResult.failure("Could not create temp dir: " + e.getMessage());
        }

        try {
            Path src = workDir.resolve(className + ".java");
            Files.writeString(src, code, StandardCharsets.UTF_8);

            String classpath = buildClasspath(workDir);

            String compileError = compile(src, workDir, classpath);
            if (compileError != null) {
                return new RunResult(false, "", List.of(), compileError);
            }
            return execute(className, workDir, classpath);
        } catch (IOException e) {
            return RunResult.failure("Runner I/O error: " + e.getMessage());
        } finally {
            deleteRecursively(workDir);
        }
    }

    private String detectClassName(String code) {
        Matcher m = PUBLIC_CLASS.matcher(code);
        if (m.find()) {
            return m.group(1);
        }
        Matcher any = ANY_CLASS.matcher(code);
        if (any.find()) {
            return any.group(1);
        }
        return "Playground";
    }

    private String buildClasspath(Path workDir) {
        String sep = File.pathSeparator;
        String vr = repoPaths.visualRuntimeJar().map(Path::toString).orElse("");
        return vr.isEmpty() ? workDir.toString() : workDir + sep + vr;
    }

    /** @return null on success, or the collected compiler diagnostics. */
    private String compile(Path src, Path outDir, String classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return "No system Java compiler available — backend must run on a JDK.";
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fm.getJavaFileObjectsFromFiles(List.of(src.toFile()));
            List<String> options = List.of(
                    "-classpath", classpath,
                    "-d", outDir.toString()
            );
            boolean ok = compiler.getTask(null, fm, diagnostics, options, null, units).call();
            if (ok) {
                return null;
            }
            StringBuilder sb = new StringBuilder("Compilation failed:\n");
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                sb.append(d.getKind()).append(": ");
                if (d.getLineNumber() >= 0) {
                    sb.append("line ").append(d.getLineNumber()).append(": ");
                }
                sb.append(d.getMessage(null)).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "Compiler error: " + e.getMessage();
        }
    }

    private RunResult execute(String className, Path workDir, String classpath) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = List.of(
                javaBin,
                "-Xmx" + maxHeap,
                "-XX:+UseSerialGC",
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-classpath", classpath,
                className
        );

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true);

        TraceCollector collector = new TraceCollector(mapper);
        try {
            Process process = pb.start();

            // Drain output on a separate thread so the child never blocks on a
            // full pipe while we wait for the timeout.
            List<String> lines = new ArrayList<>();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (IOException ignored) {
                    // process ended / stream closed
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                lines.forEach(collector::accept);
                return new RunResult(false, collector.output(), collector.events(),
                        "Execution timed out after " + timeoutSeconds + "s (possible infinite loop).");
            }

            reader.join(2000);
            lines.forEach(collector::accept);

            int exit = process.exitValue();
            if (exit != 0) {
                return new RunResult(false, collector.output(), collector.events(),
                        "Program exited with code " + exit + ".");
            }
            return new RunResult(true, collector.output(), collector.events(), null);
        } catch (IOException e) {
            return RunResult.failure("Failed to start JVM: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RunResult.failure("Run interrupted.");
        }
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("Could not delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.debug("Cleanup failed for {}: {}", dir, e.getMessage());
        }
    }
}
