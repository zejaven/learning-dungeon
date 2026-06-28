import java.util.function.Supplier;
import visual.VisualAnonymousClass;

public class Playground {
    public static void main(String[] args) {
        VisualAnonymousClass visual = new VisualAnonymousClass("Supplier<String>");
        visual.target("interface", "get()");

        String city = "Berlin";
        Supplier<String> label = new Supplier<String>() {
            @Override
            public String get() {
                return city + " depot";
            }
        };

        visual.created("label", label);
        visual.captured("city", city);
        String value = label.get();
        visual.called("get()", value);
        System.out.println(value);
    }
}
