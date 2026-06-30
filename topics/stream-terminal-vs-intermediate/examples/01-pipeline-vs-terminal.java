import visual.VisualStream;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        // VisualStream is a teaching model of java.util.stream.Stream.
        // filter and map are intermediate (pipeline) operations: each only
        // appends a stage and returns a new stream. collect is the terminal
        // operation that finally runs the whole pipeline and produces a result.
        List<Integer> result = VisualStream.of("orders", 1, 2, 3, 4, 5, 6)
                .filter("n % 2 == 0", n -> n % 2 == 0)
                .map("n * 10", n -> n * 10)
                .collectToList("collect(toList())");

        System.out.println("result = " + result);
    }
}
