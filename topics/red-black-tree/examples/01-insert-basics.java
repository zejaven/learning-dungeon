import visual.VisualRedBlackTree;

public class Playground {
    public static void main(String[] args) {
        VisualRedBlackTree tree = new VisualRedBlackTree("orders");

        tree.insert(10);
        tree.insert(5);
        tree.insert(15);

        System.out.println("Sorted keys: " + tree.values());
    }
}
