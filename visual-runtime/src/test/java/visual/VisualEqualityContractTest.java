package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualEqualityContractTest {

    private String captureTrace(Runnable body) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void emitsContractOkForEqualObjectsWithSameHashCode() {
        String out = captureTrace(() -> {
            VisualEqualityContract<OrderKey> lab = new VisualEqualityContract<>("orders");
            lab.compare(new OrderKey("A-1", 7), new OrderKey("A-1", 7));
        });

        assertTrue(out.contains("EQUALITY_CONTRACT_OK"),
                "expected contract OK event, got:\n" + out);
    }

    @Test
    void emitsContractBrokenForEqualObjectsWithDifferentHashCodes() {
        String out = captureTrace(() -> {
            VisualEqualityContract<BadOrderKey> lab = new VisualEqualityContract<>("badOrders");
            lab.compare(new BadOrderKey("A-1", 1), new BadOrderKey("A-1", 2));
        });

        assertTrue(out.contains("EQUALITY_CONTRACT_BROKEN"),
                "expected broken contract event, got:\n" + out);
    }

    @Test
    void rejectsDuplicateWhenEqualObjectUsesSameHashCode() {
        VisualEqualityContract<OrderKey> lab = new VisualEqualityContract<>("orders");
        String out = captureTrace(() -> {
            lab.add(new OrderKey("A-1", 7));
            lab.add(new OrderKey("A-1", 7));
        });

        assertTrue(out.contains("EQUALITY_DUPLICATE_REJECTED"),
                "expected duplicate rejection event, got:\n" + out);
        assertEquals(1, lab.size());
    }

    @Test
    void storesDuplicateWhenEqualObjectsUseDifferentHashCodes() {
        VisualEqualityContract<BadOrderKey> lab = new VisualEqualityContract<>("badOrders");
        String out = captureTrace(() -> {
            lab.add(new BadOrderKey("A-1", 1));
            lab.add(new BadOrderKey("A-1", 2));
        });

        assertTrue(out.contains("EQUALITY_DUPLICATE_STORED"),
                "expected duplicate stored event, got:\n" + out);
        assertEquals(2, lab.size());
    }

    @Test
    void emitsLookupMissWhenMutableHashChangesAfterAdd() {
        VisualEqualityContract<MutableKey> lab = new VisualEqualityContract<>("sessions");
        String out = captureTrace(() -> {
            MutableKey key = new MutableKey("draft");
            lab.add(key);
            key.status = "sent";
            lab.contains(key);
        });

        assertTrue(out.contains("EQUALITY_LOOKUP_MISSED"),
                "expected lookup miss event, got:\n" + out);
    }

    @Test
    void emitsSymmetryBrokenForInheritanceTrap() {
        String out = captureTrace(() -> {
            VisualEqualityContract<Price> lab = new VisualEqualityContract<>("prices");
            lab.checkSymmetry(new Price(100), new DiscountedPrice(100, "SALE"));
        });

        assertTrue(out.contains("EQUALITY_SYMMETRY_BROKEN"),
                "expected symmetry failure event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualEqualityContract<OrderKey> lab = new VisualEqualityContract<>("orders");
            lab.add(new OrderKey("A-1", 7));
            lab.contains(new OrderKey("A-1", 7));
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }

    static final class OrderKey {
        final String number;
        final int tenantId;

        OrderKey(String number, int tenantId) {
            this.number = number;
            this.tenantId = tenantId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof OrderKey other
                    && tenantId == other.tenantId
                    && number.equals(other.number);
        }

        @Override
        public int hashCode() {
            return 31 * number.hashCode() + tenantId;
        }

        @Override
        public String toString() {
            return "OrderKey(" + number + "," + tenantId + ")";
        }
    }

    static final class BadOrderKey {
        final String number;
        final int forcedHash;

        BadOrderKey(String number, int forcedHash) {
            this.number = number;
            this.forcedHash = forcedHash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BadOrderKey other && number.equals(other.number);
        }

        @Override
        public int hashCode() {
            return forcedHash;
        }

        @Override
        public String toString() {
            return "BadOrderKey(" + number + "," + forcedHash + ")";
        }
    }

    static final class MutableKey {
        String status;

        MutableKey(String status) {
            this.status = status;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MutableKey other && status.equals(other.status);
        }

        @Override
        public int hashCode() {
            return status.hashCode();
        }

        @Override
        public String toString() {
            return "MutableKey(" + status + ")";
        }
    }

    static class Price {
        final int cents;

        Price(int cents) {
            this.cents = cents;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Price other && cents == other.cents;
        }

        @Override
        public int hashCode() {
            return cents;
        }

        @Override
        public String toString() {
            return "Price(" + cents + ")";
        }
    }

    static final class DiscountedPrice extends Price {
        final String coupon;

        DiscountedPrice(int cents, String coupon) {
            super(cents);
            this.coupon = coupon;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DiscountedPrice other
                    && cents == other.cents
                    && coupon.equals(other.coupon);
        }

        @Override
        public int hashCode() {
            return 31 * cents + coupon.hashCode();
        }

        @Override
        public String toString() {
            return "DiscountedPrice(" + cents + "," + coupon + ")";
        }
    }
}
