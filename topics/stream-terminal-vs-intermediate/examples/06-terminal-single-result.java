import visual.VisualStream;

public class Playground {
    public static void main(String[] args) {
        // A terminal operation returns a single, non-stream value (here a sum)
        // and consumes the stream. After STREAM_CONSUMED the same stream object
        // cannot be reused: a second terminal call would throw
        // IllegalStateException("stream has already been operated upon or closed").
        int total = VisualStream.of("orders", 1, 2, 3, 4)
                .filter("n % 2 == 0", n -> n % 2 == 0)
                .reduce("reduce(0, Integer::sum)", 0, Integer::sum);

        System.out.println("sum of evens = " + total);
    }
}
