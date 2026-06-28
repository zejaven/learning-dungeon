import visual.VisualAnonymousClass;

public class Playground {
    interface ClickListener {
        void onClick(String button);
    }

    static void register(String button, ClickListener listener, VisualAnonymousClass visual) {
        visual.passed("register(String, ClickListener)", "listener");
        listener.onClick(button);
    }

    public static void main(String[] args) {
        VisualAnonymousClass visual = new VisualAnonymousClass("ClickListener");
        visual.target("interface", "onClick(String)");

        ClickListener listener = new ClickListener() {
            @Override
            public void onClick(String button) {
                System.out.println("Saved " + button);
            }
        };

        visual.created("listener", listener);
        register("Save", listener, visual);
        visual.called("onClick(String)", "Saved Save");
    }
}
