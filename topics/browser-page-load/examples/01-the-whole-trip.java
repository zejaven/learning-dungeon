import visual.VisualPageLoad;

public class Playground {
    public static void main(String[] args) {
        // The complete answer, in the order a browser actually performs it:
        // address bar -> DNS -> TCP -> TLS -> HTTP -> subresources -> paint.
        VisualPageLoad browser = VisualPageLoad.browser();

        // open() is just the steps below run back to back; the other examples
        // slow each of them down and look inside.
        browser.open("www.google.com");
        browser.report();

        System.out.println("Four layers had to succeed before one byte of HTML existed.");
    }
}
