import visual.VisualStackQueue;

public class Playground {
    public static void main(String[] args) {
        VisualStackQueue<String> undo = VisualStackQueue.stack("undo");

        undo.push("type-email");
        undo.push("delete-line");
        undo.push("paste-signature");

        String action = undo.pop();
        String next = undo.peek();

        System.out.println("undo action = " + action);
        System.out.println("next undo = " + next);
    }
}
