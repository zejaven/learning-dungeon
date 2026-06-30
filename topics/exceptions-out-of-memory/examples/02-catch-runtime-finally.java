import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        VisualException vm = new VisualException();

        vm.call("main", "RuntimeException", true);
        vm.call("parseOrder");
        vm.throwException("NumberFormatException", "order id is not a number");
    }
}
