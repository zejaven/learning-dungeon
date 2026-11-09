import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** An in-process event bus. Listeners run on the publishing thread, in subscription order. */
final class EventBus {
    private final List<Consumer<String>> listeners = new ArrayList<>();

    void subscribe(Consumer<String> listener) {
        listeners.add(listener);
    }

    void unsubscribe(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /** Registers a listener that receives exactly one event and then leaves the bus. */
    void subscribeOnce(Consumer<String> listener) {
        subscribe(new Consumer<String>() {
            @Override
            public void accept(String event) {
                unsubscribe(this);
                listener.accept(event);
            }
        });
    }

    int listenerCount() {
        return listeners.size();
    }

    void publish(String event) {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).accept(event);
        }
    }
}
