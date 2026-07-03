package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A dependency-free teaching model of Spring transaction propagation. It does not
 * open real database transactions; it simulates the distinction interviewers care
 * about: a <em>logical</em> transaction is a {@code @Transactional} method
 * boundary, while a <em>physical</em> transaction is the actual database
 * transaction (connection) that one or more logical boundaries share.
 *
 * <p>Each method boundary is entered with a {@link Propagation} type. The model
 * decides whether to start a new physical transaction, join the current one,
 * suspend it, open a savepoint, run without a transaction, or raise a propagation
 * error, and emits a bilingual trace event for the frontend.
 */
public class VisualTransactionPropagation {

    public enum Propagation {
        REQUIRED, REQUIRES_NEW, NESTED, SUPPORTS, NOT_SUPPORTED, MANDATORY, NEVER
    }

    private final List<Frame> frames = new ArrayList<>();   // append-only history for display
    private final List<Frame> stack = new ArrayList<>();    // active call stack (LIFO)
    private final List<PhysicalTx> physicals = new ArrayList<>();

    private int nextTx = 1;
    private int nextSavepoint = 1;
    private int nextFrame = 1;
    private PhysicalTx active;   // currently active physical transaction, or null

    /**
     * Enters a {@code @Transactional} method with the given propagation. Emits the
     * event(s) that describe how Spring would react to the current context.
     */
    public Frame enter(String method, Propagation propagation) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(propagation, "propagation");
        Frame f = new Frame("F" + nextFrame++, method, propagation, stack.size());
        frames.add(f);

        switch (propagation) {
            case REQUIRED -> {
                if (active != null) {
                    join(f);
                } else {
                    startNew(f);
                }
                push(f);
            }
            case REQUIRES_NEW -> {
                if (active != null) {
                    suspend(f);
                }
                startNew(f);
                push(f);
            }
            case NESTED -> {
                if (active != null) {
                    savepoint(f);
                } else {
                    startNew(f);
                }
                push(f);
            }
            case SUPPORTS -> {
                if (active != null) {
                    join(f);
                } else {
                    nonTransactional(f);
                }
                push(f);
            }
            case NOT_SUPPORTED -> {
                if (active != null) {
                    suspend(f);
                }
                nonTransactional(f);
                push(f);
            }
            case MANDATORY -> {
                if (active != null) {
                    join(f);
                    push(f);
                } else {
                    error(f, "MANDATORY needs an existing transaction, but none is active",
                            "MANDATORY требует уже открытую транзакцию, но активной нет");
                }
            }
            case NEVER -> {
                if (active != null) {
                    error(f, "NEVER forbids running inside a transaction, but " + active.id + " is active",
                            "NEVER запрещает работу внутри транзакции, но активна " + active.id);
                } else {
                    nonTransactional(f);
                    push(f);
                }
            }
        }
        return f;
    }

    /** Normal return of the innermost method: commit / join-return / release savepoint. */
    public void commit() {
        Frame f = pop();
        if (f.savepoint != null) {
            PhysicalTx tx = txById(f.physicalTx);
            tx.savepoints.remove(f.savepoint);
            f.status = "RELEASED";
            event("TX_RELEASE_SAVEPOINT",
                    f.method + " returned normally; savepoint " + f.savepoint + " is released inside " + tx.id,
                    f.method + " завершился штатно; savepoint " + f.savepoint + " освобождён внутри " + tx.id,
                    List.of("frame:" + f.handle, "tx:" + tx.id));
        } else if (f.owns) {
            PhysicalTx tx = txById(f.physicalTx);
            if (tx.rollbackOnly) {
                tx.status = "ROLLED_BACK";
                f.status = "ROLLED_BACK";
                event("TX_UNEXPECTED_ROLLBACK",
                        f.method + " tried to commit " + tx.id + ", but it was marked rollback-only: UnexpectedRollbackException",
                        f.method + " попытался закоммитить " + tx.id + ", но он помечен rollback-only: UnexpectedRollbackException",
                        List.of("frame:" + f.handle, "tx:" + tx.id));
            } else {
                tx.status = "COMMITTED";
                f.status = "COMMITTED";
                event("TX_COMMIT",
                        f.method + " committed physical transaction " + tx.id,
                        f.method + " закоммитил физическую транзакцию " + tx.id,
                        List.of("frame:" + f.handle, "tx:" + tx.id));
            }
            resumeAfter(f);
        } else {
            f.status = "RETURNED";
            event("TX_RETURN",
                    f.method + " returned; it shared a physical transaction, so nothing is committed yet",
                    f.method + " вернулся; он разделял физическую транзакцию, поэтому коммита пока нет",
                    List.of("frame:" + f.handle));
            resumeAfter(f);
        }
    }

    /** The innermost method failed (threw): roll it back according to its propagation. */
    public void rollback() {
        Frame f = pop();
        if (f.savepoint != null) {
            PhysicalTx tx = txById(f.physicalTx);
            tx.savepoints.remove(f.savepoint);
            f.status = "ROLLED_BACK";
            event("TX_ROLLBACK_SAVEPOINT",
                    f.method + " failed; only savepoint " + f.savepoint + " is rolled back, " + tx.id + " stays active",
                    f.method + " упал; откатывается только savepoint " + f.savepoint + ", " + tx.id + " остаётся активной",
                    List.of("frame:" + f.handle, "tx:" + tx.id));
        } else if (f.owns) {
            PhysicalTx tx = txById(f.physicalTx);
            tx.status = "ROLLED_BACK";
            f.status = "ROLLED_BACK";
            event("TX_ROLLBACK",
                    f.method + " failed; its own physical transaction " + tx.id + " is rolled back",
                    f.method + " упал; его собственная физическая транзакция " + tx.id + " откачена",
                    List.of("frame:" + f.handle, "tx:" + tx.id));
            resumeAfter(f);
        } else if (f.physicalTx != null) {
            PhysicalTx tx = txById(f.physicalTx);
            tx.rollbackOnly = true;
            f.status = "ROLLED_BACK";
            event("TX_MARK_ROLLBACK",
                    f.method + " failed inside shared " + tx.id + "; it is marked rollback-only, so the whole transaction must roll back",
                    f.method + " упал внутри общей " + tx.id + "; она помечена rollback-only, поэтому вся транзакция обязана откатиться",
                    List.of("frame:" + f.handle, "tx:" + tx.id));
        } else {
            f.status = "ROLLED_BACK";
            event("TX_RETURN",
                    f.method + " failed, but it ran without a transaction, so there is nothing to roll back",
                    f.method + " упал, но он работал без транзакции, откатывать нечего",
                    List.of("frame:" + f.handle));
            resumeAfter(f);
        }
    }

    private void join(Frame f) {
        f.physicalTx = active.id;
        f.owns = false;
        f.roleKind = "JOIN";
        event("TX_JOIN",
                f.method + " (" + f.propagation + ") joined the existing physical transaction " + active.id,
                f.method + " (" + f.propagation + ") присоединился к существующей физической транзакции " + active.id,
                List.of("frame:" + f.handle, "tx:" + active.id));
    }

    private void startNew(Frame f) {
        PhysicalTx tx = new PhysicalTx("T" + nextTx++);
        physicals.add(tx);
        active = tx;
        f.physicalTx = tx.id;
        f.owns = true;
        f.roleKind = "START";
        event("TX_START_PHYSICAL",
                f.method + " (" + f.propagation + ") started a new physical transaction " + tx.id,
                f.method + " (" + f.propagation + ") открыл новую физическую транзакцию " + tx.id,
                List.of("frame:" + f.handle, "tx:" + tx.id));
    }

    private void suspend(Frame f) {
        PhysicalTx suspended = active;
        suspended.status = "SUSPENDED";
        f.suspended = suspended;
        active = null;
        event("TX_SUSPEND",
                f.method + " (" + f.propagation + ") suspended the current physical transaction " + suspended.id,
                f.method + " (" + f.propagation + ") приостановил текущую физическую транзакцию " + suspended.id,
                List.of("frame:" + f.handle, "tx:" + suspended.id));
    }

    private void savepoint(Frame f) {
        String sp = "SP" + nextSavepoint++;
        active.savepoints.add(sp);
        f.physicalTx = active.id;
        f.owns = false;
        f.savepoint = sp;
        f.roleKind = "SAVEPOINT";
        event("TX_SAVEPOINT",
                f.method + " (NESTED) created savepoint " + sp + " inside the same physical transaction " + active.id,
                f.method + " (NESTED) создал savepoint " + sp + " внутри той же физической транзакции " + active.id,
                List.of("frame:" + f.handle, "tx:" + active.id));
    }

    private void nonTransactional(Frame f) {
        f.physicalTx = null;
        f.owns = false;
        f.roleKind = "NONE";
        event("TX_NON_TRANSACTIONAL",
                f.method + " (" + f.propagation + ") runs with no transaction on the connection",
                f.method + " (" + f.propagation + ") выполняется без транзакции на соединении",
                List.of("frame:" + f.handle));
    }

    private void error(Frame f, String descEn, String descRu) {
        f.status = "ERROR";
        f.active = false;
        f.roleKind = "ERROR";
        event("TX_ERROR",
                f.method + " (" + f.propagation + "): " + descEn,
                f.method + " (" + f.propagation + "): " + descRu,
                List.of("frame:" + f.handle));
    }

    private void resumeAfter(Frame f) {
        if (f.suspended != null) {
            f.suspended.status = "ACTIVE";
            active = f.suspended;
            event("TX_RESUME",
                    "Resumed the suspended physical transaction " + active.id,
                    "Возобновлена приостановленная физическая транзакция " + active.id,
                    List.of("tx:" + active.id));
        } else if (f.owns) {
            active = null;
        }
    }

    private void push(Frame f) {
        stack.add(f);
    }

    private Frame pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("No active @Transactional method to return from");
        }
        Frame f = stack.remove(stack.size() - 1);
        f.active = false;
        return f;
    }

    private PhysicalTx txById(String id) {
        for (PhysicalTx tx : physicals) {
            if (tx.id.equals(id)) {
                return tx;
            }
        }
        throw new IllegalStateException("Unknown physical transaction " + id);
    }

    private void event(String type, String descEn, String descRu, List<String> highlight) {
        Trace.event(type, descEn, descRu, highlight, state(type));
    }

    private Object state(String operation) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("operation", operation);
        s.put("activeTx", active == null ? "none" : active.id);
        s.put("depth", stack.size());

        List<Object> frameList = new ArrayList<>();
        for (Frame f : frames) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("handle", f.handle);
            item.put("method", f.method);
            item.put("propagation", f.propagation.name());
            item.put("physicalTx", f.physicalTx == null ? "none" : f.physicalTx);
            item.put("roleKind", f.roleKind);
            item.put("savepoint", f.savepoint == null ? "" : f.savepoint);
            item.put("depth", f.depth);
            item.put("active", f.active);
            item.put("status", f.status);
            frameList.add(item);
        }
        s.put("frames", frameList);

        List<Object> txList = new ArrayList<>();
        for (PhysicalTx tx : physicals) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", tx.id);
            item.put("status", tx.status);
            item.put("rollbackOnly", tx.rollbackOnly);
            item.put("savepoints", new ArrayList<>(tx.savepoints));
            txList.add(item);
        }
        s.put("physicalTransactions", txList);
        return s;
    }

    public static final class Frame {
        private final String handle;
        private final String method;
        private final Propagation propagation;
        private final int depth;
        private String physicalTx;         // id of the physical tx, or null (non-transactional)
        private boolean owns;              // true if this frame started the physical tx
        private String savepoint;          // set for NESTED frames
        private PhysicalTx suspended;      // the tx this frame suspended on enter, if any
        private String roleKind = "";      // START | JOIN | SAVEPOINT | NONE | ERROR
        private boolean active = true;
        private String status = "ACTIVE";  // ACTIVE | COMMITTED | ROLLED_BACK | RELEASED | RETURNED | ERROR

        private Frame(String handle, String method, Propagation propagation, int depth) {
            this.handle = handle;
            this.method = method;
            this.propagation = propagation;
            this.depth = depth;
        }

        public String method() {
            return method;
        }

        public Propagation propagation() {
            return propagation;
        }
    }

    private static final class PhysicalTx {
        final String id;
        String status = "ACTIVE";
        boolean rollbackOnly;
        final List<String> savepoints = new ArrayList<>();

        PhysicalTx(String id) {
            this.id = id;
        }
    }
}
