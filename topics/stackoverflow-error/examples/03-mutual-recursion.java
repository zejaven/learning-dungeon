import visual.VisualCallStack;

public class Playground {
    public static void main(String[] args) {
        // Recursion does not have to be a single self-call. Two methods that
        // call each other with no base case overflow just the same:
        //
        //     static void ping() { pong(); }
        //     static void pong() { ping(); }
        //
        // The frames alternate ping(), pong(), ping(), pong() ... and still pile
        // up until the stack is full.
        VisualCallStack stack = new VisualCallStack(6);
        boolean ping = true;
        while (!stack.call(ping ? "ping" : "pong")) {
            ping = !ping;
        }
    }
}
