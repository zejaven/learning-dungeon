package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A teaching model for passing lambda expressions into Java methods. It records
 * the functional interface target type, the lambda expression, the receiving
 * method parameter, captured locals, invocation, arguments, and return values.
 */
public class VisualLambda {

    private final String targetType;
    private final String samSignature;
    private String lambdaName = "";
    private String lambdaExpression = "";
    private String receivingMethod = "";
    private String parameterName = "";
    private String lastCall = "";
    private String argument = "";
    private String result = "";
    private String phase = "target";
    private final List<Map<String, Object>> captured = new ArrayList<>();

    public VisualLambda(String targetType, String samSignature) {
        this.targetType = targetType;
        this.samSignature = samSignature;
        Trace.event("LAMBDA_TARGET_DECLARED",
                "Target type " + targetType + " is a functional interface; the lambda must match "
                        + samSignature + ".",
                "Целевой тип " + targetType + " — functional interface; lambda должна соответствовать "
                        + samSignature + ".",
                List.of("card:target"), state());
    }

    public void created(String lambdaName, String lambdaExpression) {
        this.lambdaName = lambdaName;
        this.lambdaExpression = lambdaExpression;
        this.phase = "created";
        Trace.event("LAMBDA_CREATED",
                "Lambda " + lambdaName + " is prepared from " + lambdaExpression
                        + "; it is behavior stored as a value, not a call yet.",
                "Lambda " + lambdaName + " подготовлена из " + lambdaExpression
                        + "; это поведение как значение, а не вызов прямо сейчас.",
                List.of("card:lambda"), state());
    }

    public void captured(String name, Object value) {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("name", name);
        local.put("value", show(value));
        captured.add(local);
        this.phase = "captured";
        Trace.event("LAMBDA_CAPTURED_VALUE",
                "Lambda reads effectively final local variable " + name + " = " + show(value) + ".",
                "Lambda читает effectively final локальную переменную " + name + " = " + show(value) + ".",
                List.of("card:capture", "card:lambda"), state());
    }

    public void passed(String receivingMethod, String parameterName) {
        this.receivingMethod = receivingMethod;
        this.parameterName = parameterName;
        this.phase = "passed";
        Trace.event("LAMBDA_PASSED",
                "Passed lambda " + displayLambda() + " into parameter " + parameterName
                        + " of " + receivingMethod + ".",
                "Lambda " + displayLambda() + " передана в параметр " + parameterName
                        + " метода " + receivingMethod + ".",
                List.of("card:method", "card:lambda"), state());
    }

    public void invokeRunnable(String call, Runnable body) {
        beginInvoke(call);
        body.run();
    }

    public <T> T invokeSupplier(String call, Supplier<T> supplier) {
        beginInvoke(call);
        T value = supplier.get();
        returned(value);
        return value;
    }

    public <T> void invokeConsumer(String call, T value, Consumer<T> consumer) {
        argument(value);
        beginInvoke(call);
        consumer.accept(value);
    }

    public <T, R> R invokeFunction(String call, T value, Function<T, R> function) {
        argument(value);
        beginInvoke(call);
        R mapped = function.apply(value);
        returned(mapped);
        return mapped;
    }

    private void argument(Object value) {
        this.argument = show(value);
        this.phase = "argument";
        Trace.event("LAMBDA_ARGUMENT_SENT",
                "Method " + displayMethod() + " sends argument " + argument + " into the lambda call.",
                "Метод " + displayMethod() + " передает аргумент " + argument + " в вызов lambda.",
                List.of("card:data", "card:method"), state());
    }

    private void beginInvoke(String call) {
        this.lastCall = call;
        this.phase = "invoked";
        Trace.event("LAMBDA_INVOKED",
                displayMethod() + " calls " + call + "; now the lambda body executes.",
                displayMethod() + " вызывает " + call + "; теперь выполняется тело lambda.",
                List.of("card:invoke", "card:lambda"), state());
    }

    private void returned(Object value) {
        this.result = show(value);
        this.phase = "returned";
        Trace.event("LAMBDA_RETURNED_VALUE",
                "Lambda call returned " + result + ".",
                "Вызов lambda вернул " + result + ".",
                List.of("card:data", "card:invoke"), state());
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("targetType", targetType);
        s.put("samSignature", samSignature);
        s.put("lambdaName", lambdaName);
        s.put("lambdaExpression", lambdaExpression);
        s.put("receivingMethod", receivingMethod);
        s.put("parameterName", parameterName);
        s.put("lastCall", lastCall);
        s.put("argument", argument);
        s.put("result", result);
        s.put("phase", phase);
        s.put("captured", new ArrayList<>(captured));
        return s;
    }

    private String displayLambda() {
        return lambdaName.isBlank() ? "<lambda>" : lambdaName;
    }

    private String displayMethod() {
        return receivingMethod.isBlank() ? "The method" : receivingMethod;
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
