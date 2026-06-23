import visual.VisualThreadPool;

public class Playground {
    public static void main(String[] args) {
        VisualThreadPool demo = new VisualThreadPool("new-thread-demo");

        demo.runWithNewThread("parse-request", () -> {
        });
        demo.runWithNewThread("send-email", () -> {
        });
        demo.runWithNewThread("write-audit", () -> {
        });
    }
}
