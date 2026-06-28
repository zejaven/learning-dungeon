import visual.VisualNaiveArrayList;

public class Playground {
    public static void main(String[] args) {
        VisualNaiveArrayList<String> tickets = new VisualNaiveArrayList<>("tickets");

        tickets.add("T-1");
        tickets.add("T-2");
        tickets.reportTotalWork();

        System.out.println("copies = " + tickets.totalCopies());
    }
}
