import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory memory = new VisualMemory();

        int leftCount = 5;
        int rightCount = 5;
        memory.primitive("leftCount", "int", String.valueOf(leftCount));
        memory.primitive("rightCount", "int", String.valueOf(rightCount));

        String leftLabel = new String("desk");
        memory.newObject("leftLabel", "String", "value=desk");

        String rightLabel = new String("desk");
        memory.newObject("rightLabel", "String", "value=desk");

        System.out.println(leftCount == rightCount);
        System.out.println(leftLabel == rightLabel);
        System.out.println(leftLabel.equals(rightLabel));
    }
}
