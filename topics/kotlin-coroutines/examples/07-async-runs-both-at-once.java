import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        // One Default thread, so the concurrency cannot come from parallelism.
        VisualCoroutines rt = VisualCoroutines.runtime(1, 4);

        // suspend fun quote() = coroutineScope {
        //     val rate = async { fetchRate() }
        //     val fees = async { fetchFees() }
        //     Quote(rate.await(), fees.await())
        // }
        rt.scope("priceQuote");
        String quote = rt.launchIn("priceQuote", "Default", "quote");

        // Both are started BEFORE either is awaited. That is where concurrency lives.
        String rate = rt.async("priceQuote", "IO", "fetchRate");
        String fees = rt.async("priceQuote", "IO", "fetchFees");

        rt.suspendAt(rate, "httpGet(/rate)");
        rt.suspendAt(fees, "httpGet(/fees)");

        rt.await(quote, rate);
        rt.resume(rate);
        rt.complete(rate);

        rt.await(quote, fees);
        rt.resume(fees);
        rt.complete(fees);

        rt.complete(quote);
        rt.joinScope("priceQuote");

        rt.report();
        System.out.println("Move an await() up one line and this becomes a sequential program.");
    }
}
