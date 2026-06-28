import visual.VisualStaticNestedClass;

public class Playground {
    static class Garage {
        static class Car {
            private final String model;

            Car(String model) {
                this.model = model;
            }

            String model() {
                return model;
            }
        }

        class Bay {
            int number;
        }
    }

    public static void main(String[] args) {
        VisualStaticNestedClass scene = new VisualStaticNestedClass(
                "Garage", "Garage.Car", "Garage.Bay");

        Garage.Car car = new Garage.Car("EV");
        scene.createStaticNested("cityCar", "model=EV");

        String qualifiedName = Garage.Car.class.getName();
        Class<?> superType = Garage.Car.class.getSuperclass();
        boolean extendsOuter = superType == Garage.class;
        car.model();
        qualifiedName.length();
        Boolean.toString(extendsOuter);
    }
}
