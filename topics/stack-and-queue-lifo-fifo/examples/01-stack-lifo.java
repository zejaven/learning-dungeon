import visual.VisualStackQueue;

public class Playground {
    public static void main(String[] args) {
        VisualStackQueue<String> stack = VisualStackQueue.stack("plates");

        stack.push("plate-A");
        stack.push("plate-B");
        stack.push("plate-C");

        String removed = stack.pop();
        System.out.println("removed = " + removed);
    }
}
