import visual.VisualStream;

import java.util.Optional;

public class Playground {
    public static void main(String[] args) {
        // findFirst is a SHORT-CIRCUITING terminal operation: as soon as one
        // element makes it through, the stream stops pulling the rest. Elements
        // 4 and 5 are never read, even though the source has them.
        Optional<Integer> firstBig = VisualStream.of("orders", 1, 2, 3, 4, 5)
                .filter("n > 2", n -> n > 2)
                .findFirst("findFirst()");

        System.out.println("firstBig = " + firstBig.orElse(-1));
    }
}
