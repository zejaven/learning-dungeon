// Observer contract. Every concrete subscriber reacts to a published update.
public interface Subscriber {
    void update(String headline);
}
