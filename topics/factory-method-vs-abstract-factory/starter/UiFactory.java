// Abstract Factory: one interface creates a compatible family of UI products.
// TODO (missions): add LightUiFactory and DarkUiFactory implementations.
public interface UiFactory {
    Button createButton();

    Dialog createDialog();
}
