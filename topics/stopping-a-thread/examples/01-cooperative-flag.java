import visual.VisualThreadStop;

public class Playground {
    public static void main(String[] args) {
        VisualThreadStop demo = new VisualThreadStop("cooperative-flag");

        demo.createWorker("report-writer");
        demo.start("report-writer");
        demo.work("report-writer", 2);

        demo.requestStop("report-writer");
        demo.work("report-writer", 1); // A request is not observed until the worker checks it.
        demo.observeStopRequest("report-writer");
        demo.exit("report-writer");
        demo.join("report-writer");
    }
}
