import visual.VisualThreadStop;

public class Playground {
    public static void main(String[] args) {
        VisualThreadStop demo = new VisualThreadStop("unsafe-stop");

        demo.createWorker("file-writer");
        demo.start("file-writer");
        demo.work("file-writer", 1);

        demo.unsafeStopAttempt("file-writer");
        demo.requestStop("file-writer");
        demo.observeStopRequest("file-writer");
        demo.exit("file-writer");
        demo.join("file-writer");
    }
}
