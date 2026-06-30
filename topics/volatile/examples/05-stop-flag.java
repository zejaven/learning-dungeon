import visual.VisualVolatile;

public class Playground {
    public static void main(String[] args) {
        VisualVolatile stopSignal = new VisualVolatile("stop-signal", true);

        boolean firstCheck = stopSignal.readReady("Worker");
        stopSignal.writeReady("Main", true);
        boolean secondCheck = stopSignal.readReady("Worker");

        System.out.println("before stop = " + firstCheck + ", after stop = " + secondCheck);
    }
}
