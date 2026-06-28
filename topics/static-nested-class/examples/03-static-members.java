import visual.VisualStaticNestedClass;

public class Playground {
    static class Ticket {
        static class Counter {
            static int next = 100;

            int number() {
                return next++;
            }
        }

        class Line {
            String text;
        }
    }

    public static void main(String[] args) {
        VisualStaticNestedClass scene = new VisualStaticNestedClass(
                "Ticket", "Ticket.Counter", "Ticket.Line");

        scene.setStaticField("Ticket.Counter", "next", String.valueOf(Ticket.Counter.next));

        Ticket.Counter first = new Ticket.Counter();
        Ticket.Counter second = new Ticket.Counter();
        scene.createStaticNested("firstCounter");
        scene.createStaticNested("secondCounter");

        first.number();
        second.number();
        scene.setStaticField("Ticket.Counter", "next", String.valueOf(Ticket.Counter.next));
        scene.accessStaticField("Ticket.Counter", "next");
    }
}
