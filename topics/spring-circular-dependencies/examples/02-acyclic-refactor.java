import visual.VisualSpringBeanFactory;

public class Playground {
    public static void main(String[] args) {
        VisualSpringBeanFactory context = new VisualSpringBeanFactory("app");

        context.bean("OrderService").dependsOn("PaymentService");
        context.bean("PaymentService").dependsOn("PricingRules");
        context.bean("NotificationService").dependsOn("PricingRules");
        context.bean("PricingRules");

        context.refresh();
    }
}
