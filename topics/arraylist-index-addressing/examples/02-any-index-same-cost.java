import visual.VisualArrayIndexing;

public class Playground {
    public static void main(String[] args) {
        VisualArrayIndexing list = new VisualArrayIndexing("data", 8);
        for (int i = 0; i < 6; i++) {
            list.store("v" + i);
        }

        // The first and the last element both take a single multiply-add.
        // The address math does not depend on the index or on the list size.
        System.out.println("first -> " + list.get(0));
        System.out.println("last  -> " + list.get(5));
    }
}
