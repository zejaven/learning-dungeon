import visual.VisualStream;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        // limit is a short-circuiting INTERMEDIATE operation: it is still lazy
        // (it only runs when the terminal collect pulls), but once it has passed
        // enough elements it tells the pipeline to stop. Only the first two
        // elements are mapped; the source is never read to the end.
        List<Integer> firstTwo = VisualStream.of("orders", 10, 20, 30, 40, 50)
                .map("n + 1", n -> n + 1)
                .limit("limit(2)", 2)
                .collectToList("collect(toList())");

        System.out.println("firstTwo = " + firstTwo);
    }
}
