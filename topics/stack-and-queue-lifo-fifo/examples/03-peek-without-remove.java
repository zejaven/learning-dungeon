import visual.VisualStackQueue;

public class Playground {
    public static void main(String[] args) {
        VisualStackQueue<String> stack = VisualStackQueue.stack("drafts");
        stack.push("draft-v1");
        stack.push("draft-v2");
        System.out.println("top = " + stack.peek());
        System.out.println("pop = " + stack.pop());

        VisualStackQueue<String> queue = VisualStackQueue.queue("jobs");
        queue.offer("job-A");
        queue.offer("job-B");
        System.out.println("front = " + queue.peek());
        System.out.println("poll = " + queue.poll());
    }
}
