import java.util.Comparator;
import visual.VisualAnonymousClass;

public class Playground {
    public static void main(String[] args) {
        VisualAnonymousClass visual = new VisualAnonymousClass("Comparator<String>");
        visual.target("interface", "compare(String, String)");

        Comparator<String> byLength = new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Integer.compare(left.length(), right.length());
            }
        };

        visual.created("byLength", byLength);
        int result = byLength.compare("tea", "coffee");
        visual.called("compare(String, String)", result);
        System.out.println(result);
    }
}
