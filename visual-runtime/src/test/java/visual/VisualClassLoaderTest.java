package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualClassLoaderTest {

    private String captureTrace(Runnable body) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void coreClassDelegatesUpAndIsDefinedByBootstrap() {
        String out = captureTrace(() -> {
            VisualClassLoader app = VisualClassLoader.standardHierarchy();
            app.loadClass("java.lang.String");
        });
        assertTrue(out.contains("DELEGATE_UP"),
                "expected delegation up the chain, got:\n" + out);
        assertTrue(out.contains("CLASS_DEFINED"),
                "expected the class to be defined, got:\n" + out);
        assertTrue(out.contains("Bootstrap"),
                "expected Bootstrap to define a core class, got:\n" + out);
    }

    @Test
    void applicationClassIsDefinedByApplicationLoader() {
        String out = captureTrace(() -> {
            VisualClassLoader app = VisualClassLoader.standardHierarchy();
            app.loadClass("com.app.Service");
        });
        assertTrue(out.contains("CLASS_DEFINED"),
                "expected the application class to be defined, got:\n" + out);
    }

    @Test
    void secondLoadHitsTheCache() {
        String out = captureTrace(() -> {
            VisualClassLoader app = VisualClassLoader.standardHierarchy();
            app.loadClass("java.lang.String");
            app.loadClass("java.lang.String");
        });
        assertTrue(out.contains("ALREADY_LOADED"),
                "expected the second load to hit the cache, got:\n" + out);
    }

    @Test
    void unknownClassThrowsClassNotFound() {
        String out = captureTrace(() -> {
            VisualClassLoader app = VisualClassLoader.standardHierarchy();
            app.loadClass("com.unknown.Missing");
        });
        assertTrue(out.contains("CLASS_NOT_FOUND"),
                "expected a not-found event, got:\n" + out);
    }

    @Test
    void customLoaderDefinesItsOwnClass() {
        String out = captureTrace(() -> {
            VisualClassLoader app = VisualClassLoader.standardHierarchy();
            VisualClassLoader plugin = app.withChild("PluginLoader", "com.plugin.Greeter");
            plugin.loadClass("com.plugin.Greeter");
        });
        assertTrue(out.contains("CLASSLOADER_CREATED"),
                "expected the custom loader to be created, got:\n" + out);
        assertTrue(out.contains("CLASS_DEFINED"),
                "expected the custom loader to define its class, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualClassLoader app = VisualClassLoader.standardHierarchy();
            app.loadClass("com.app.Service");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
