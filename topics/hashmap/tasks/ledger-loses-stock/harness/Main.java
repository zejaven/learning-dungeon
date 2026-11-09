import visual.TestKit;

import java.util.List;

/** Hidden tests for the StockLedger task. */
public class Main {

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("one vendor in any case is one SKU", "8 1", () -> {
                    StockLedger ledger = new StockLedger();
                    ledger.receive(new Sku("ACME", "A1"), 5);
                    ledger.receive(new Sku("acme", "A1"), 3);
                    return ledger.unitsOf(new Sku("Acme", "A1")) + " " + ledger.distinctSkus();
                }),
                TestKit.test("different codes stay apart", "5 2 2", () -> {
                    StockLedger ledger = new StockLedger();
                    ledger.receive(new Sku("Acme", "A1"), 5);
                    ledger.receive(new Sku("Acme", "B2"), 2);
                    return ledger.unitsOf(new Sku("Acme", "A1")) + " " + ledger.unitsOf(new Sku("Acme", "B2"))
                            + " " + ledger.distinctSkus();
                }),
                TestKit.test("rebranded stock is found under the new name only", "5 0", () -> {
                    StockLedger ledger = new StockLedger();
                    ledger.receive(new Sku("Acme", "A1"), 5);
                    ledger.rebrand("acme", "NovaCo");
                    return ledger.unitsOf(new Sku("NovaCo", "A1")) + " " + ledger.unitsOf(new Sku("Acme", "A1"));
                }),
                TestKit.test("rebranding into a vendor that stocks the same code adds the units up", "7 1", () -> {
                    StockLedger ledger = new StockLedger();
                    ledger.receive(new Sku("Acme", "A1"), 5);
                    ledger.receive(new Sku("NovaCo", "A1"), 2);
                    ledger.rebrand("Acme", "NovaCo");
                    return ledger.unitsOf(new Sku("novaco", "A1")) + " " + ledger.distinctSkus();
                }),
                TestKit.test("rebranding leaves other vendors alone", "4 5 2", () -> {
                    StockLedger ledger = new StockLedger();
                    ledger.receive(new Sku("Acme", "A1"), 5);
                    ledger.receive(new Sku("Bolt", "A1"), 4);
                    ledger.rebrand("ACME", "NovaCo");
                    return ledger.unitsOf(new Sku("Bolt", "A1")) + " " + ledger.unitsOf(new Sku("NovaCo", "A1"))
                            + " " + ledger.distinctSkus();
                }),
                TestKit.test("the report is sorted by code", List.of("A1=1", "B7=2", "M5=3", "Q2=4", "Z9=5"), () -> {
                    StockLedger ledger = new StockLedger();
                    ledger.receive(new Sku("Acme", "Z9"), 5);
                    ledger.receive(new Sku("Acme", "Q2"), 4);
                    ledger.receive(new Sku("Acme", "M5"), 3);
                    ledger.receive(new Sku("Acme", "A1"), 1);
                    ledger.receive(new Sku("Acme", "B7"), 2);
                    return ledger.report();
                })
        );
    }
}
