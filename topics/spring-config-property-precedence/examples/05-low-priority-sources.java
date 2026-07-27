import visual.VisualPropertySources;

public class Playground {
    public static void main(String[] args) {
        VisualPropertySources env = VisualPropertySources.springBoot();

        // Registered in code as a last-resort value:
        // new SpringApplicationBuilder(App.class).properties("app.retries=1")
        env.defaultProperties("app.retries=1");

        // @PropertySource("classpath:legacy.properties") on a @Configuration class.
        // It looks deliberate. It reads like an override. It is neither.
        env.propertySourceAnnotation("classpath:legacy.properties", "app.retries=5");

        // The ordinary file everybody edits.
        env.packagedProperties("app.retries=3");

        System.out.println("app.retries = " + env.getProperty("app.retries"));
        System.out.println("@PropertySource loses to application.properties, however deliberate it looks.");
    }
}
