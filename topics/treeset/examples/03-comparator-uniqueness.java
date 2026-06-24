import visual.VisualTreeSet;

import java.util.Comparator;

public class Playground {
    public static void main(String[] args) {
        Comparator<String> byLength = Comparator.comparingInt(String::length);
        VisualTreeSet<String> words = new VisualTreeSet<>("words", byLength, "length Comparator");

        words.add("go");
        words.add("up");
        words.add("java");

        System.out.println("Words kept by length: " + words.values());
    }
}
