import visual.VisualRedBlackTree;

public class Playground {
    public static void main(String[] args) {
        VisualRedBlackTree tree = new VisualRedBlackTree("deliveries");

        tree.insert(40);
        tree.insert(10);
        tree.insert(70);
        tree.insert(50);
        tree.insert(60);

        System.out.println("Has 50? " + tree.contains(50));
        System.out.println("Has 99? " + tree.contains(99));
    }
}
