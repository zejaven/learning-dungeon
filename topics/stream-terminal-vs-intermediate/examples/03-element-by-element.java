import visual.VisualStream;

public class Playground {
    public static void main(String[] args) {
        // A stream is processed vertically: each element travels through the
        // WHOLE pipeline before the next element starts. The terminal forEach
        // pulls one element at a time, so filter and map run interleaved, not
        // stage-by-stage over the whole collection.
        VisualStream.of("orders", 1, 2, 3, 4)
                .filter("n % 2 == 0", n -> n % 2 == 0)
                .map("n * 10", n -> n * 10)
                .forEach("forEach(println)", n -> System.out.println("handled " + n));
    }
}
