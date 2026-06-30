import visual.VisualStreamProbe;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6);
        VisualStreamProbe probe = new VisualStreamProbe("short circuit", numbers);

        probe.pipelineDeclared("filter even -> findFirst");
        int firstEven = numbers.stream()
                .filter(probe::filterEven)
                .findFirst()
                .orElseThrow();

        probe.shortCircuitFound(firstEven);
        System.out.println("firstEven = " + firstEven);
    }
}
