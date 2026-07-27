import visual.VisualLongPoll;

public class Playground {
    public static void main(String[] args) {
        // A response ends its request, so between two long polls nothing is
        // listening. With a cursor, that gap is survivable.
        VisualLongPoll careful = VisualLongPoll.held("GET /api/messages", 4, 10);

        careful.poll("tab-1");
        careful.tick(2);
        careful.serverEvent("msg-1 from ada");
        // This one lands in the gap and simply waits in the log.
        careful.serverEvent("msg-2 from omar");
        // ?since=1 makes the next request return immediately instead of parking.
        careful.poll("tab-1");
        careful.report();

        // The same code with no cursor: the request is only attached to events
        // that happen after it is parked.
        VisualLongPoll naive = VisualLongPoll.held("GET /api/messages", 4, 10).withoutCursor();

        naive.poll("tab-2");
        naive.tick(2);
        naive.serverEvent("msg-1 from ada");
        naive.serverEvent("msg-2 from omar");
        naive.poll("tab-2");
        naive.report();

        System.out.println("The hold is the easy half; the cursor is what makes it correct.");
    }
}
