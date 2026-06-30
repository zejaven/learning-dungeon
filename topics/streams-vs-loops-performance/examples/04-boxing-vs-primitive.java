import visual.VisualStreamProbe;

import java.util.List;
import java.util.stream.IntStream;

public class Playground {
    public static void main(String[] args) {
        List<Integer> boxedNumbers = List.of(10, 20, 30);
        VisualStreamProbe boxed = new VisualStreamProbe("Stream<Integer>", boxedNumbers);
        int boxedSum = boxedNumbers.stream()
                .mapToInt(boxed::unbox)
                .sum();
        boxed.finishStream(boxedSum);

        VisualStreamProbe primitive = new VisualStreamProbe("IntStream", boxedNumbers);
        int primitiveSum = IntStream.of(10, 20, 30)
                .peek(primitive::primitiveVisit)
                .sum();
        primitive.finishStream(primitiveSum);

        System.out.println("boxedSum = " + boxedSum);
        System.out.println("primitiveSum = " + primitiveSum);
    }
}
