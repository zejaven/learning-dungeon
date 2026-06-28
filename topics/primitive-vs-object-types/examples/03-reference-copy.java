import visual.VisualMemory;

public class Playground {
    static class Basket {
        int items;

        Basket(int items) {
            this.items = items;
        }
    }

    public static void main(String[] args) {
        VisualMemory memory = new VisualMemory();

        Basket first = new Basket(2);
        memory.newObject("first", "Basket", "items=2");

        Basket second = first;
        memory.copyReference("second", "Basket", "first");

        second.items = 3;
        memory.mutateField("second", "items", String.valueOf(second.items));

        System.out.println("first.items=" + first.items);
    }
}
