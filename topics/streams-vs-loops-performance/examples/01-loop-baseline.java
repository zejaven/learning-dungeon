import visual.VisualStreamProbe;

import java.util.List;

public class Playground {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4);
        VisualStreamProbe probe = new VisualStreamProbe("loop baseline", numbers);

        int sum = 0;
        for (int value : numbers) {
            int current = probe.loopVisit(value);
            if (current % 2 == 0) {
                sum += current * current;
            }
        }

        probe.finishLoop(sum);
        System.out.println("sum = " + sum);
    }
}
