import visual.VisualMemory;

public class Playground {
    static class Ticket {
        final String code;

        Ticket(String code) {
            this.code = code;
        }
    }

    public static void main(String[] args) {
        VisualMemory memory = new VisualMemory();

        Ticket ticket = new Ticket("T-7");
        memory.newObject("ticket", "Ticket", "code=T-7");

        ticket = null;
        memory.setNull("ticket");

        System.out.println(ticket == null);
    }
}
