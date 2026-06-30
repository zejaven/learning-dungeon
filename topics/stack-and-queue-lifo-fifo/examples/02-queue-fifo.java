import visual.VisualStackQueue;

public class Playground {
    public static void main(String[] args) {
        VisualStackQueue<String> queue = VisualStackQueue.queue("tickets");

        queue.offer("ticket-1");
        queue.offer("ticket-2");
        queue.offer("ticket-3");

        String served = queue.poll();
        System.out.println("served = " + served);
    }
}
