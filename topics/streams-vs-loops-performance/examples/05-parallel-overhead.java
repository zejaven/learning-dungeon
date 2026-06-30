import visual.VisualStreamProbe;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6);
        VisualStreamProbe probe = new VisualStreamProbe("parallel-style sum", numbers);

        int sum = probe.simulateParallelSum(3);

        System.out.println("sum = " + sum);
    }
}
