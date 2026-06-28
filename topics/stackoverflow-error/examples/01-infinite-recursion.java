import visual.VisualCallStack;

public class Playground {
    public static void main(String[] args) {
        // The simplest cause of StackOverflowError: a method that calls itself
        // with no base case. In real Java this is just:
        //
        //     static void overflow() { overflow(); }
        //
        // Each call pushes a frame and never returns, so the stack fills up.
        VisualCallStack stack = new VisualCallStack(6);
        stack.recurseUntilOverflow("overflow");
    }
}
