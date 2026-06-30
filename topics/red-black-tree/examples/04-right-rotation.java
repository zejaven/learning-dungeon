import visual.VisualRedBlackTree;

public class Playground {
    public static void main(String[] args) {
        VisualRedBlackTree tree = new VisualRedBlackTree("priorities");

        tree.insert(30);
        tree.insert(20);
        tree.insert(10);

        System.out.println("Sorted keys: " + tree.values());
    }
}
