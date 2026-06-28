import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory memory = new VisualMemory();

        int quantity = 7;
        memory.primitive("quantity", "int", String.valueOf(quantity));

        Integer boxed = quantity;
        memory.newObject("boxed", "Integer", "value=7");

        int again = boxed;
        memory.primitive("again", "int", String.valueOf(again));

        System.out.println(boxed + again);
    }
}
