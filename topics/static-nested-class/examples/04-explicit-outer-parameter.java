import visual.VisualStaticNestedClass;

public class Playground {
    static class Config {
        private final String tenant;
        private static final String DEFAULT_ZONE = "UTC";

        Config(String tenant) {
            this.tenant = tenant;
        }

        static class Snapshot {
            String zone() {
                return DEFAULT_ZONE;
            }

            String tenantOf(Config config) {
                return config.tenant;
            }
        }
    }

    public static void main(String[] args) {
        VisualStaticNestedClass scene = new VisualStaticNestedClass(
                "Config", "Config.Snapshot", "Config.Entry");

        Config config = new Config("shop-a");
        scene.createOuter("shopConfig", "tenant=shop-a");
        scene.createStaticNested("snapshot", "zone=UTC");

        Config.Snapshot snapshot = new Config.Snapshot();
        snapshot.zone();
        snapshot.tenantOf(config);
    }
}
