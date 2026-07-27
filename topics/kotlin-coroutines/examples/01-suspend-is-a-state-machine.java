import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        // The Kotlin this models:
        //
        //   suspend fun loadProfile(userId: Int): Profile {
        //       val user   = fetchUser(userId)      // suspension point
        //       val orders = fetchOrders(user.id)   // suspension point
        //       return Profile(user, orders)
        //   }
        //
        // Three sequential lines in the source; three entry points in the bytecode.
        VisualCoroutines.suspendFun("loadProfile")
                .local("userId", "42")
                .await("fetchUser(userId)", "user", "ada")
                .await("fetchOrders(user.id)", "orders", "[o-17, o-18]")
                .returns("Profile(ada, 2 orders)")
                .run();

        System.out.println("The whole runtime is: call invokeSuspend, get a value or COROUTINE_SUSPENDED.");
    }
}
