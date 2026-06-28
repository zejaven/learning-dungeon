import visual.VisualAnonymousClass;

public class Playground {
    public static void main(String[] args) {
        VisualAnonymousClass visual = new VisualAnonymousClass("DiscountPolicy");
        visual.target("abstract class", "apply(int)");

        abstract class DiscountPolicy {
            private final String name;

            DiscountPolicy(String name) {
                this.name = name;
            }

            String name() {
                return name;
            }

            abstract int apply(int cents);
        }

        DiscountPolicy policy = new DiscountPolicy("weekend") {
            @Override
            int apply(int cents) {
                return cents - 500;
            }
        };

        visual.created("policy", policy);
        int finalPrice = policy.apply(2500);
        visual.called("apply(int)", finalPrice);
        System.out.println(policy.name() + ": " + finalPrice);
    }
}
