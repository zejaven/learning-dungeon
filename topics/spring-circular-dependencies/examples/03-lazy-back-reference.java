import visual.VisualSpringBeanFactory;

public class Playground {
    public static void main(String[] args) {
        VisualSpringBeanFactory context = new VisualSpringBeanFactory("app");

        context.bean("A").dependsOn("B");
        context.bean("B").dependsOn("C");
        context.bean("C").lazyDependsOn("A");

        context.refresh();
        context.useLazy("C", "A");
    }
}
