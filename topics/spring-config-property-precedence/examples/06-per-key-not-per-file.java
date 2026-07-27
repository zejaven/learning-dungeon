import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        VisualPropertySources env = VisualPropertySources.springBoot();
        env.packagedProperties(
                "server.port=8080",
                "spring.application.name=shop-api",
                "app.checkout.timeout=2s");

        // One variable aimed at one key -- and a second with a typo in its name
        // (APP_CHEKOUT_TIMEOUT, one C short of APP_CHECKOUT_TIMEOUT).
        env.environmentVariables("SERVER_PORT=9000", "APP_CHEKOUT_TIMEOUT=10s");

        // Overridden by the variable.
        System.out.println("server.port             = " + env.getProperty("server.port"));
        // Untouched: the rest of the file is still in force.
        System.out.println("spring.application.name = " + env.getProperty("spring.application.name"));
        // The typo overrode nothing and nobody complained.
        System.out.println("app.checkout.timeout    = " + env.getProperty("app.checkout.timeout"));

        // What "nobody defines this" looks like from the inside.
        System.out.println("app.feature.beta        = " + env.getProperty("app.feature.beta"));
    }
}
