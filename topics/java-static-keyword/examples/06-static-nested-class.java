import visual.VisualStatic;

public class Playground {
    public static void main(String[] args) {
        VisualStatic order = new VisualStatic("Order");

        // A static nested class is namespaced under Order, but it needs no Order object.
        order.staticNestedClass("Order.RowMapper", "helper type grouped under Order");

        // The outer class can still have normal instances.
        order.newInstance("order42", "id=42", "status=new");
    }
}
