import visual.VisualStreamProbe;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4);
        VisualStreamProbe probe = new VisualStreamProbe("sequential stream", numbers);

        probe.pipelineDeclared("filter even -> map square -> reduce sum");
        int sum = numbers.stream()
                .filter(probe::filterEven)
                .map(probe::mapSquare)
                .reduce(0, probe::reduceSum);

        probe.finishStream(sum);
        System.out.println("sum = " + sum);
    }
}
