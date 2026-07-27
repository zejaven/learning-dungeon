import visual.VisualAuthentication;

public class Playground {
    public static void main(String[] args) {
        // Two users who happen to choose the same password. Because each row is
        // hashed with its own salt, the stored values are different, so cracking
        // one of them says nothing about the other.
        VisualAuthentication careful = VisualAuthentication.withSessions();
        careful.register("alice", "correct-horse-battery");
        careful.register("bob", "correct-horse-battery");
        careful.leakTheUserStore();

        // The same application, with one thing changed: it keeps the password as
        // typed. Logging in behaves identically, which is what makes this
        // survive code review - the difference only shows up on the bad day.
        VisualAuthentication careless = VisualAuthentication.withSessions()
                .storePasswordsInPlaintext();
        careless.register("alice", "correct-horse-battery");
        careless.leakTheUserStore();

        System.out.println("A password store answers 'could they produce it', not 'what is it'.");
    }
}
