import visual.VisualStaticNestedClass;

public class Playground {
    static class Kitchen {
        private final String name;

        Kitchen(String name) {
            this.name = name;
        }

        class Shelf {
            private final String code;

            Shelf(String code) {
                this.code = code;
            }

            String label() {
                return name + ":" + code;
            }
        }

        static class Timer {
            int minutes;
        }
    }

    public static void main(String[] args) {
        VisualStaticNestedClass scene = new VisualStaticNestedClass(
                "Kitchen", "Kitchen.Timer", "Kitchen.Shelf");

        Kitchen kitchen = new Kitchen("Main");
        String outerId = scene.createOuter("mainKitchen", "name=Main");

        Kitchen.Shelf shelf = kitchen.new Shelf("spices");
        scene.createInner(outerId, "spiceShelf", "code=spices");
        shelf.label();
    }
}
