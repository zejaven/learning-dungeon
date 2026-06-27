import visual.VisualAopProxy;

public class Playground {
    public static void main(String[] args) {
        VisualAopProxy app = new VisualAopProxy("NotificationService");

        app.afterThrowing("AlertAdvice", "send*")
                .call("sendEmail")
                .targetLine("connectToSmtp()")
                .throwException("MailServerDownException");
    }
}
