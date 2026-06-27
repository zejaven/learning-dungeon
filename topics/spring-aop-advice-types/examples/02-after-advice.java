import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("ShippingService");

        app.after("CleanupAdvice", "ship*")
                .call("shipOrder")
                .targetLine("reserveCourier()")
                .throwException("CourierUnavailableException");
    }
}
