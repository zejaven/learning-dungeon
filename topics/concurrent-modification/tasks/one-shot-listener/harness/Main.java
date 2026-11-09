import visual.TestKit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Hidden tests for the EventBus task. */
public class Main {

    /** A listener that records "name:event" into the shared log. */
    private static Consumer<String> recorder(List<String> log, String name) {
        return event -> log.add(name + ":" + event);
    }

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("plain listeners get each event in subscription order",
                        List.of("A:1", "B:1", "A:2", "B:2"), () -> {
                            List<String> log = new ArrayList<>();
                            EventBus bus = new EventBus();
                            bus.subscribe(recorder(log, "A"));
                            bus.subscribe(recorder(log, "B"));
                            bus.publish("1");
                            bus.publish("2");
                            return log;
                        }),
                TestKit.test("a one-shot listener in front does not cost the others the event",
                        List.of("once:1", "A:1", "B:1", "A:2", "B:2"), () -> {
                            List<String> log = new ArrayList<>();
                            EventBus bus = new EventBus();
                            bus.subscribeOnce(recorder(log, "once"));
                            bus.subscribe(recorder(log, "A"));
                            bus.subscribe(recorder(log, "B"));
                            bus.publish("1");
                            bus.publish("2");
                            return log;
                        }),
                TestKit.test("a one-shot listener second to last",
                        List.of("A:1", "once:1", "B:1", "A:2", "B:2"), () -> {
                            List<String> log = new ArrayList<>();
                            EventBus bus = new EventBus();
                            bus.subscribe(recorder(log, "A"));
                            bus.subscribeOnce(recorder(log, "once"));
                            bus.subscribe(recorder(log, "B"));
                            bus.publish("1");
                            bus.publish("2");
                            return log;
                        }),
                TestKit.test("a one-shot listener leaves the bus after its event", 1, () -> {
                    EventBus bus = new EventBus();
                    bus.subscribeOnce(event -> { });
                    bus.subscribe(event -> { });
                    bus.publish("1");
                    return bus.listenerCount();
                }),
                TestKit.test("a listener added during delivery waits for the next event",
                        List.of("A:1", "A:2", "late:2"), () -> {
                            List<String> log = new ArrayList<>();
                            EventBus bus = new EventBus();
                            Consumer<String> late = recorder(log, "late");
                            bus.subscribeOnce(event -> bus.subscribe(late));
                            bus.subscribe(recorder(log, "A"));
                            bus.publish("1");
                            bus.publish("2");
                            return log;
                        }),
                TestKit.test("a listener removed during delivery still gets the event in flight",
                        List.of("B:1", "C:1", "C:2"), () -> {
                            List<String> log = new ArrayList<>();
                            EventBus bus = new EventBus();
                            Consumer<String> b = recorder(log, "B");
                            bus.subscribe(event -> {
                                if (event.equals("1")) {
                                    bus.unsubscribe(b);
                                }
                            });
                            bus.subscribe(b);
                            bus.subscribe(recorder(log, "C"));
                            bus.publish("1");
                            bus.publish("2");
                            return log;
                        })
        );
    }
}
