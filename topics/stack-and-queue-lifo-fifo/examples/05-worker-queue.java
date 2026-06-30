import visual.VisualStackQueue;

public class Playground {
    public static void main(String[] args) {
        VisualStackQueue<String> printer = VisualStackQueue.queue("printer");

        printer.offer("invoice.pdf");
        printer.offer("labels.pdf");
        printer.offer("report.pdf");

        String first = printer.poll();
        printer.offer("contract.pdf");
        String second = printer.poll();

        System.out.println("printing = " + first);
        System.out.println("printing = " + second);
    }
}
