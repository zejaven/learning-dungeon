// Subject / Publisher. It should store subscribers through the Subscriber
// interface and notify them without knowing their concrete classes.
//
// TODO (missions):
//   1. Create EmailSubscriber and SmsSubscriber classes that implement Subscriber.
//   2. Give NewsPublisher a field such as List<Subscriber> subscribers.
//   3. Notify every subscriber from publish(...).
public class NewsPublisher {

    public void publish(String headline) {
        // TODO: notify subscribers by calling update(headline)
    }
}
