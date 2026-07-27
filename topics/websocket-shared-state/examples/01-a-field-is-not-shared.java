import visual.VisualSharedState;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

public class Playground {
    public static void main(String[] args) {
        // A @ServerEndpoint POJO is instantiated once per connection, so a
        // Map<String, Session> field on it belongs to that one connection.
        VisualSharedState server = VisualSharedState.server(
                Store.INSTANCE_FIELD, Source.ATOMIC_COUNTER);

        server.connect("alice");
        server.connect("bob");
        server.connect("carol");

        // Each @OnOpen puts its own session into its own map.
        server.join("alice");
        server.join("bob");
        server.join("carol");

        // "Send to everyone" runs on the object that received the message.
        server.broadcast("market open");

        server.report();
        System.out.println("Three connections, three registries, one recipient.");
    }
}
