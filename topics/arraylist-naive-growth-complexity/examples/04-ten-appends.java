import visual.VisualNaiveArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualNaiveArrayList<Integer> queue = new VisualNaiveArrayList<>("queue");

        for (int i = 1; i <= 10; i++) {
            queue.add(i);
        }
        queue.reportTotalWork();

        System.out.println("copies = " + queue.totalCopies());
    }
}
