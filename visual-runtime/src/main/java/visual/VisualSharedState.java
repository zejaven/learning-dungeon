package visual;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A <em>teaching model</em> of the state a WebSocket server keeps <em>outside</em>
 * any single connection: the registry of open sessions that every connection's
 * thread reads and writes, and the value handed to a new connection that must be
 * unique across all of them.
 *
 * <p>The interview question has two halves and they fail in different ways:
 *
 * <ol>
 *   <li><b>Where does shared state live and what must it be?</b> A field on a
 *       per-connection endpoint object ({@link Store#INSTANCE_FIELD}) is not
 *       shared at all — the fan-out reaches one client. A {@code static HashMap}
 *       ({@link Store#PLAIN_MAP}) is shared but not safe, and two concurrent
 *       publishes lose an entry. {@link Store#SYNCHRONIZED_MAP} makes each call
 *       atomic but not a sequence of calls, and its iterator still throws.
 *       {@link Store#CONCURRENT_MAP} is the answer for one JVM.</li>
 *   <li><b>Where does a globally unique value come from?</b>
 *       {@link Source#PLAIN_COUNTER} and {@link Source#REGISTRY_SIZE} are
 *       read-then-write, so two threads hand out the same number.
 *       {@link Source#SYNCHRONIZED_COUNTER} and {@link Source#ATOMIC_COUNTER}
 *       fix that — <em>inside one JVM</em>, which is exactly as far as they go:
 *       a second node behind the load balancer starts counting at 1 again.
 *       Only {@link Source#RANDOM_UUID} (no coordination) and
 *       {@link Source#DB_SEQUENCE} (one authority everybody asks) are unique for
 *       the whole cluster.</li>
 * </ol>
 *
 * <p>Interleavings are scripted rather than timed: {@code beginJoin} performs the
 * read half and {@code finishJoin} the publish half, so an example can put two
 * connections inside the same window and the failure happens on every run. That
 * is the one lie this model tells — a real lost update is intermittent, which is
 * precisely why it reaches production. Everything else (values, counters, byte
 * counts, UUIDs) is derived deterministically, and the class stays
 * dependency-free.
 */
public class VisualSharedState {

    /** Where the map of open connections actually lives. */
    public enum Store { INSTANCE_FIELD, PLAIN_MAP, SYNCHRONIZED_MAP, CONCURRENT_MAP }

    /** Where the value that has to be unique comes from. */
    public enum Source {
        PLAIN_COUNTER, REGISTRY_SIZE, SYNCHRONIZED_COUNTER, ATOMIC_COUNTER, RANDOM_UUID, DB_SEQUENCE
    }

    /** The handler class every example pretends to be running. */
    private static final String HANDLER = "ChatHandler";

    /** Where the shared database sequence starts, so its values are recognisable. */
    private static final long SEQUENCE_START = 1000L;

    /** One JVM behind the load balancer: its own heap, its own counter. */
    private static final class Node {

        private final String id;
        private long counter;

        private Node(String id) {
            this.id = id;
        }
    }

    /** One row of the session registry: a client mapped to the value it was given. */
    private static final class Entry {

        private final String node;
        private final String holder;
        private final String client;
        private final String value;
        private boolean live = true;
        private boolean stale;
        private boolean duplicate;

        private Entry(String node, String holder, String client, String value) {
            this.node = node;
            this.holder = holder;
            this.client = client;
            this.value = value;
        }
    }

    /** One open socket, the thread serving it and the value it ended up with. */
    private static final class Conn {

        private final String id;
        private final Node node;
        private final String thread;
        private final String holder;
        private String phase = "OPEN";
        private String candidate;
        private String value;
        private boolean raced;
        private boolean duplicate;
        private Entry entry;

        private Conn(String id, Node node, String thread, String holder) {
            this.id = id;
            this.node = node;
            this.thread = thread;
            this.holder = holder;
        }
    }

    /** One value that was handed out, in the order it was handed out. */
    private static final class Issued {

        private final int seq;
        private final String client;
        private final String node;
        private final String value;
        private boolean unique = true;

        private Issued(int seq, String client, String node, String value) {
            this.seq = seq;
            this.client = client;
            this.node = node;
            this.value = value;
        }
    }

    // ------------------------------------------------------------------ state

    private final Store store;
    private final Source source;

    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, Conn> connections = new LinkedHashMap<>();
    private final List<Entry> registry = new ArrayList<>();
    private final List<Issued> issued = new ArrayList<>();

    private long sequence = SEQUENCE_START;
    private boolean serializedWrites;
    private int endpointObjects;

    private int opened;
    private int registered;
    private int duplicates;
    private int lost;
    private int leaked;
    private int delivered;
    private int missed;
    private int stale;
    private int writeErrors;

    private VisualSharedState(Store store, Source source, int nodeCount) {
        this.store = store;
        this.source = source;
        for (int i = 1; i <= nodeCount; i++) {
            nodes.add(new Node("app-" + i));
        }
    }

    // -------------------------------------------------------------- factories

    /** One process holding every socket — the shape you develop against. */
    public static VisualSharedState server(Store store, Source source) {
        return cluster(store, source, 1);
    }

    /** Several processes behind a load balancer, each holding some of the sockets. */
    public static VisualSharedState cluster(Store store, Source source, int nodeCount) {
        if (nodeCount < 1) {
            throw new IllegalArgumentException("a cluster needs at least one node");
        }
        VisualSharedState server = new VisualSharedState(store, source, nodeCount);
        server.announce();
        return server;
    }

    /**
     * Wraps every session in something that serialises writes to it — Spring's
     * {@code ConcurrentWebSocketSessionDecorator}, or your own per-session lock.
     */
    public VisualSharedState serializeWrites() {
        serializedWrites = true;
        Trace.event("WRITES_SERIALIZED",
                "Every session is now wrapped so that writes to it are serialised — in Spring that is "
                        + "ConcurrentWebSocketSessionDecorator, by hand it is a lock per session. Note what "
                        + "this is NOT: it is not the registry's job. A ConcurrentHashMap of sessions makes "
                        + "the map safe to share; it says nothing about two threads writing into one of the "
                        + "sessions it holds, and a WebSocket session is not required to tolerate that",
                "Каждая сессия теперь обёрнута так, что записи в неё сериализуются — в Spring это "
                        + "ConcurrentWebSocketSessionDecorator, руками — по блокировке на сессию. Обратите "
                        + "внимание, чего это НЕ делает: это не задача реестра. ConcurrentHashMap с сессиями "
                        + "делает безопасной саму карту и ничего не говорит про два потока, которые пишут в "
                        + "одну из лежащих в ней сессий, а WebSocket-сессия не обязана это выдерживать",
                List.of("writes"), state());
        return this;
    }

    // ------------------------------------------------------------ connections

    /** A socket opens and the container gives it a thread of its own. */
    public void connect(String client) {
        if (connections.containsKey(client)) {
            throw new IllegalStateException(client + " is already connected");
        }
        Node node = nodes.get(connections.size() % nodes.size());
        String thread = "ws-" + (connections.size() + 1);
        String holder;
        if (store == Store.INSTANCE_FIELD) {
            endpointObjects++;
            holder = HANDLER + "#" + endpointObjects;
        } else {
            holder = "sessions@" + node.id;
        }
        connections.put(client, new Conn(client, node, thread, holder));
        opened++;
        Trace.event("CONNECTION_OPENED",
                client + " is connected to " + node.id + " and its callbacks run on " + thread + ". This "
                        + "is the fact the whole question rests on: there is a thread per connection, not a "
                        + "thread for the application, so every line of your handler is executed by "
                        + connections.size() + " different thread(s) at once. Anything the handler touches "
                        + "that is not a local variable and not that connection's own session is shared "
                        + "mutable state, whether you designed it that way or not",
                client + " подключился к " + node.id + ", и его колбэки выполняются на потоке " + thread
                        + ". Именно на этом факте держится весь вопрос: поток приходится на соединение, а не "
                        + "на приложение, поэтому каждую строку вашего обработчика одновременно выполняют "
                        + connections.size() + " разных потока(ов). Всё, к чему обработчик прикасается и что "
                        + "не является локальной переменной и не принадлежит сессии этого соединения, — общее "
                        + "изменяемое состояние, спроектировали вы его так или нет",
                List.of("connections", "node:" + node.id), state());
    }

    /**
     * The first half of {@code @OnOpen}: work out the value this connection will
     * be given. For a read-then-write source this only <em>reads</em> — nothing is
     * reserved until {@link #finishJoin(String)} publishes it.
     */
    public void beginJoin(String client) {
        Conn conn = require(client);
        if (!"OPEN".equals(conn.phase)) {
            throw new IllegalStateException(client + " is not waiting to join (phase " + conn.phase + ")");
        }
        for (Conn other : connections.values()) {
            if (other != conn && "JOINING".equals(other.phase)) {
                other.raced = true;
                conn.raced = true;
            }
        }
        conn.phase = "JOINING";

        switch (source) {
            case PLAIN_COUNTER -> {
                long seen = conn.node.counter;
                conn.candidate = String.valueOf(seen + 1);
                Trace.event("VALUE_READ",
                        thread(conn) + " reads the counter field: nextId is " + seen + ", so it intends to "
                                + "use " + conn.candidate + ". `nextId++` looks like one thing and is three — "
                                + "read, add one, write back — with a window in the middle where another "
                                + "thread reads the same " + seen + ". Nothing is reserved by reading, and on "
                                + "a long field there is not even a guarantee the value read is current",
                        thread(conn) + " читает поле-счётчик: nextId равен " + seen + ", поэтому он "
                                + "собирается использовать " + conn.candidate + ". `nextId++` выглядит одним "
                                + "действием, а является тремя — прочитать, прибавить единицу, записать "
                                + "обратно, — и посередине есть окно, в котором другой поток прочитает тот же "
                                + seen + ". Чтение ничего не резервирует, а для поля типа long нет даже "
                                + "гарантии, что прочитанное значение актуально",
                        List.of("values", "node:" + conn.node.id), state());
            }
            case REGISTRY_SIZE -> {
                int size = liveOn(conn.node);
                conn.candidate = String.valueOf(size + 1);
                Trace.event("VALUE_READ",
                        thread(conn) + " calls sessions.size(), gets " + size + " and intends to use "
                                + conn.candidate + ". size() answers \"how many are in the map right now\", "
                                + "which is not the same question as \"which numbers have been handed out\". "
                                + "It is stale the moment it returns, and it goes DOWN when somebody "
                                + "disconnects — so this scheme reissues old numbers even with a single "
                                + "thread and no race at all",
                        thread(conn) + " вызывает sessions.size(), получает " + size + " и собирается "
                                + "использовать " + conn.candidate + ". size() отвечает на вопрос «сколько "
                                + "их в карте прямо сейчас», а это не тот же вопрос, что «какие номера уже "
                                + "выданы». Ответ устаревает сразу же, а при отключении кого-нибудь он ещё и "
                                + "УМЕНЬШАЕТСЯ — поэтому такая схема переиспользует старые номера даже в один "
                                + "поток и вообще без гонки",
                        List.of("values", "registry"), state());
            }
            case SYNCHRONIZED_COUNTER -> {
                conn.node.counter++;
                conn.candidate = String.valueOf(conn.node.counter);
                Trace.event("VALUE_ISSUED",
                        thread(conn) + " enters a synchronized block, increments the counter to "
                                + conn.candidate + " and leaves. Read, add and write are now one indivisible "
                                + "step: any other thread that wants the counter waits at the monitor, and "
                                + "releasing it also publishes the new value to whoever enters next. Correct "
                                + "— and the scope of that correctness is one JVM, because the lock only "
                                + "exists inside this process",
                        thread(conn) + " входит в synchronized-блок, увеличивает счётчик до "
                                + conn.candidate + " и выходит. Чтение, прибавление и запись стали одним "
                                + "неделимым шагом: любой другой поток, которому нужен счётчик, ждёт на "
                                + "мониторе, а освобождение монитора ещё и публикует новое значение для того, "
                                + "кто войдёт следующим. Это верно — и область этой верности равна одной JVM, "
                                + "потому что блокировка существует только внутри этого процесса",
                        List.of("values", "node:" + conn.node.id), state());
            }
            case ATOMIC_COUNTER -> {
                conn.node.counter++;
                conn.candidate = String.valueOf(conn.node.counter);
                Trace.event("VALUE_ISSUED",
                        thread(conn) + " calls nextId.incrementAndGet() and gets " + conn.candidate
                                + ". One atomic read-modify-write on the hardware: no thread can observe the "
                                + "value between the read and the write, and no thread waits for a lock — a "
                                + "failed compare-and-set simply retries. This is the right answer for a "
                                + "counter shared by every connection in one process, and it is still only "
                                + "one process",
                        thread(conn) + " вызывает nextId.incrementAndGet() и получает " + conn.candidate
                                + ". Одна атомарная операция «прочитать-изменить-записать» на уровне "
                                + "процессора: ни один поток не может увидеть значение между чтением и "
                                + "записью, и ни один поток не ждёт блокировку — неудачный compare-and-set "
                                + "просто повторяется. Это правильный ответ для счётчика, общего для всех "
                                + "соединений одного процесса, и это по-прежнему всего один процесс",
                        List.of("values", "node:" + conn.node.id), state());
            }
            case RANDOM_UUID -> {
                conn.candidate = uuidFor(client);
                Trace.event("VALUE_ISSUED",
                        thread(conn) + " calls UUID.randomUUID() and gets " + conn.candidate
                                + ". Nothing was read, nothing was locked and nobody was asked: 122 random "
                                + "bits are wide enough that a collision is not a risk you manage. That "
                                + "buys uniqueness with zero coordination — across threads, across nodes, "
                                + "across data centres — and the price is 16 bytes with no order in them, so "
                                + "it makes a poor sort key and a poor clustered primary key",
                        thread(conn) + " вызывает UUID.randomUUID() и получает " + conn.candidate
                                + ". Ничего не прочитано, ничего не заблокировано и никто не спрошен: 122 "
                                + "случайных бита достаточно широки, чтобы коллизия перестала быть риском, "
                                + "которым управляют. Так уникальность покупается вообще без координации — "
                                + "между потоками, между узлами, между дата-центрами, — а цена в том, что это "
                                + "16 байт без порядка внутри, поэтому из него выходит плохой ключ сортировки "
                                + "и плохой кластерный первичный ключ",
                        List.of("values"), state());
            }
            case DB_SEQUENCE -> {
                sequence++;
                conn.candidate = String.valueOf(sequence);
                Trace.event("VALUE_ISSUED",
                        thread(conn) + " asks the database for nextval() and gets " + conn.candidate
                                + ". The atomicity is the same idea as an AtomicLong, moved to the one thing "
                                + "every node shares: a sequence hands each caller a value it will never hand "
                                + "out again, transaction or no transaction. That makes the number unique for "
                                + "the whole cluster and not just this JVM — at the cost of a network round "
                                + "trip per connection, and of the database being a thing that can be down",
                        thread(conn) + " просит у базы nextval() и получает " + conn.candidate
                                + ". Атомарность здесь та же, что у AtomicLong, только перенесена в "
                                + "единственное, что общее у всех узлов: последовательность выдаёт каждому "
                                + "обратившемуся значение, которое больше никогда не выдаст, независимо от "
                                + "транзакций. Это делает номер уникальным для всего кластера, а не только "
                                + "для этой JVM, — ценой сетевого похода на каждое соединение и того, что "
                                + "база может быть недоступна",
                        List.of("values"), state());
            }
        }
    }

    /** The second half of {@code @OnOpen}: publish this connection into the registry. */
    public void finishJoin(String client) {
        Conn conn = require(client);
        if (!"JOINING".equals(conn.phase)) {
            throw new IllegalStateException(client + " has not started joining (phase " + conn.phase + ")");
        }
        if (source == Source.PLAIN_COUNTER) {
            // The write-back half of nextId++ — and whatever another thread did in
            // the meantime is now gone.
            conn.node.counter = Long.parseLong(conn.candidate);
        }
        conn.value = conn.candidate;
        conn.phase = "REGISTERED";
        registered++;

        Entry victim = store == Store.PLAIN_MAP && conn.raced ? lastLiveEntryOn(conn.node) : null;

        Entry entry = new Entry(conn.node.id, conn.holder, client, conn.value);
        registry.add(entry);
        conn.entry = entry;

        Issued clash = firstIssuedWith(conn.value);
        Issued row = new Issued(issued.size() + 1, client, conn.node.id, conn.value);
        if (clash != null) {
            duplicates++;
            row.unique = false;
            clash.unique = false;
            conn.duplicate = true;
            entry.duplicate = true;
            Conn owner = connections.get(clash.client);
            if (owner != null) {
                owner.duplicate = true;
                if (owner.entry != null) {
                    owner.entry.duplicate = true;
                }
            }
        }
        issued.add(row);

        Trace.event("REGISTERED",
                thread(conn) + " publishes " + client + " into " + conn.holder + " with value "
                        + conn.value + " — " + putCallEn() + ". " + storeNoteEn(),
                thread(conn) + " публикует " + client + " в " + conn.holder + " со значением "
                        + conn.value + " — " + putCallRu() + ". " + storeNoteRu(),
                List.of("registry", "connections", "node:" + conn.node.id), state());

        if (victim != null) {
            victim.live = false;
            lost++;
            Trace.event("ENTRY_LOST",
                    "And " + victim.client + " has just fallen out of the map. Two threads were inside an "
                            + "unsynchronized HashMap.put() at the same time; they landed in the same bucket "
                            + "and one of them wrote a table reference the other had already moved on from, "
                            + "so one entry is simply not there any more. Nothing threw, size() is wrong, and "
                            + victim.client + " will silently miss every broadcast from now on. The version "
                            + "of this bug that actually ends careers is worse: a concurrent resize can leave "
                            + "the table circular, and a later get() spins at 100% CPU forever",
                    "И " + victim.client + " только что выпал из карты. Два потока одновременно оказались "
                            + "внутри HashMap.put() без синхронизации; они попали в один бакет, и один из них "
                            + "записал ссылку на таблицу, от которой другой уже ушёл, — в результате одной "
                            + "записи просто нет. Ничего не упало, size() врёт, и " + victim.client
                            + " с этого момента молча пропустит все рассылки. Версия этой ошибки, которая "
                            + "по-настоящему стоит карьеры, ещё хуже: конкурентный resize способен замкнуть "
                            + "таблицу в кольцо, и последующий get() будет вечно крутиться на 100% CPU",
                    List.of("registry", "loss"), state());
        }

        if (clash != null) {
            if (clash.node.equals(conn.node.id)) {
                duplicateOnOneNode(conn, clash);
            } else {
                duplicateAcrossNodes(conn, clash);
            }
        }

        if (racyValue() && threadSafeStore() && (clash != null || conn.raced)) {
            Trace.event("CHECK_THEN_ACT",
                    "Look at where this went wrong: the map is thread-safe and the value still collided. "
                            + "A thread-safe collection promises that ONE call is atomic, never that a "
                            + "sequence of calls is — and \"read something, decide, then write\" is a "
                            + "sequence. `if (!sessions.containsKey(id)) sessions.put(id, s)` has the same "
                            + "hole. The fix is not a bigger map, it is to make the decision and the write "
                            + "one operation: putIfAbsent, computeIfAbsent, merge — or to take the value from "
                            + "something that is atomic by construction",
                    "Посмотрите, где именно это сломалось: карта потокобезопасна, а значение всё равно "
                            + "совпало. Потокобезопасная коллекция обещает атомарность ОДНОГО вызова, а не "
                            + "последовательности вызовов, — а «прочитать, решить, записать» и есть "
                            + "последовательность. У `if (!sessions.containsKey(id)) sessions.put(id, s)` "
                            + "ровно та же дыра. Лечится это не более крупной картой, а тем, чтобы решение и "
                            + "запись стали одной операцией: putIfAbsent, computeIfAbsent, merge — либо тем, "
                            + "чтобы брать значение у того, что атомарно по построению",
                    List.of("values", "registry"), state());
        }

        if (store == Store.INSTANCE_FIELD) {
            Trace.event("REGISTRY_PRIVATE",
                    conn.holder + " keeps its own map, so this registry has exactly one entry in it: "
                            + client + ". Per-connection instancing is a genuine answer for per-connection "
                            + "state, and it is the wrong tool the moment the state has to be seen by "
                            + "everybody — a \"registry\" that each connection has its own copy of is not a "
                            + "registry. Shared state has to live somewhere that outlives and outranks the "
                            + "connection: a singleton bean, a static field, an injected service",
                    conn.holder + " держит собственную карту, поэтому в этом реестре ровно одна запись: "
                            + client + ". Создание объекта на соединение — честный ответ для состояния "
                            + "соединения и неверный инструмент в тот момент, когда состояние должно быть "
                            + "видно всем: «реестр», своя копия которого есть у каждого соединения, реестром "
                            + "не является. Общее состояние обязано жить там, где переживает соединение и "
                            + "стоит над ним: в singleton-бине, в статическом поле, во внедрённом сервисе",
                    List.of("registry", "connections"), state());
        }
    }

    /** {@code beginJoin} and {@code finishJoin} back to back — nothing interleaves. */
    public void join(String client) {
        beginJoin(client);
        finishJoin(client);
    }

    // ------------------------------------------------------------- fan-out

    /** One message to everybody the registry can reach from the node that got it. */
    public void broadcast(String text) {
        Conn origin = firstRegistered();
        String fromNode = origin == null ? nodes.get(0).id : origin.node.id;
        String fromHolder = origin == null ? null : origin.holder;

        List<String> reached = new ArrayList<>();
        List<String> staleHits = new ArrayList<>();
        for (Entry entry : registry) {
            if (!entry.live || !entry.node.equals(fromNode)) {
                continue;
            }
            if (store == Store.INSTANCE_FIELD && !entry.holder.equals(fromHolder)) {
                continue;
            }
            if (entry.stale) {
                staleHits.add(entry.client);
                stale++;
                continue;
            }
            reached.add(entry.client);
        }
        List<String> notReached = new ArrayList<>();
        for (Conn conn : connections.values()) {
            if (!"CLOSED".equals(conn.phase) && !reached.contains(conn.id)) {
                notReached.add(conn.id);
            }
        }
        delivered += reached.size();
        missed += notReached.size();

        Trace.event("BROADCAST_FANOUT",
                "\"" + text + "\" is written to " + reached.size() + " socket(s) on " + fromNode + ": "
                        + join(reached) + ". This loop is the only fan-out there is — the protocol has no "
                        + "rooms, no topics and no subscriber list, so \"send to everyone\" means iterating "
                        + "a collection you maintain yourself, from one connection's thread, while other "
                        + "connections' threads are adding to it and removing from it"
                        + (staleHits.isEmpty() ? ""
                        : ". " + join(staleHits) + " is still in the map with a socket that is already "
                        + "closed, so that write goes nowhere"),
                "«" + text + "» записано в сокеты: " + reached.size() + " на " + fromNode + " ("
                        + join(reached) + "). Этот цикл — единственный существующий фан-аут: у протокола нет "
                        + "ни комнат, ни тем, ни списка подписчиков, поэтому «отправить всем» означает обход "
                        + "коллекции, которую вы ведёте сами, из потока одного соединения, пока потоки других "
                        + "соединений в неё добавляют и из неё удаляют"
                        + (staleHits.isEmpty() ? ""
                        : ". " + join(staleHits) + " всё ещё лежит в карте с уже закрытым сокетом, поэтому "
                        + "эта запись уходит в никуда"),
                List.of("registry", "broadcast"), state());

        if (store == Store.SYNCHRONIZED_MAP && anyJoining()) {
            Trace.event("ITERATION_RACE",
                    "That loop just walked a synchronizedMap while another thread was inside put() on it. "
                            + "Collections.synchronizedMap(...) locks each individual method, and iterating "
                            + "is not a method — the javadoc says in so many words that you must "
                            + "synchronize on the map yourself while traversing it, or get a "
                            + "ConcurrentModificationException. So the collection everybody calls "
                            + "\"thread-safe\" is exactly the wrong tool for the one operation a WebSocket "
                            + "server does constantly, and a ConcurrentHashMap — whose iterator is weakly "
                            + "consistent and never throws — is the right one",
                    "Этот цикл только что обошёл synchronizedMap, пока другой поток был внутри put() на "
                            + "ней. Collections.synchronizedMap(...) блокирует каждый отдельный метод, а "
                            + "итерирование методом не является — в javadoc прямым текстом сказано, что во "
                            + "время обхода синхронизироваться по карте нужно самому, иначе получите "
                            + "ConcurrentModificationException. Получается, что коллекция, которую все "
                            + "называют «потокобезопасной», — ровно неподходящий инструмент для той "
                            + "операции, которую WebSocket-сервер делает постоянно, а подходящий — "
                            + "ConcurrentHashMap, чей итератор слабо согласован и не бросает исключений",
                    List.of("registry", "broadcast"), state());
        }

        if (!notReached.isEmpty()) {
            Trace.event("BROADCAST_MISSED",
                    "Nobody got an error, and " + join(notReached) + " did not get the message: "
                            + missReasonEn() + ". This is what makes shared-state bugs on a WebSocket server "
                            + "so expensive — the failure is a message that never arrives, with no exception, "
                            + "no log line and no failed request to find in a dashboard",
                    "Никто не получил ошибку, а сообщение не получил " + join(notReached) + ": "
                            + missReasonRu() + ". Именно это делает ошибки с общим состоянием на "
                            + "WebSocket-сервере такими дорогими: отказ выглядит как сообщение, которое не "
                            + "пришло, — без исключения, без строчки в логе и без упавшего запроса, который "
                            + "можно найти на дашборде",
                    List.of("registry", "broadcast", "loss"), state());
        }
    }

    /**
     * Two threads write into the SAME session at the same time — a scheduled push
     * and a reply to an incoming message, which is the usual pairing.
     */
    public void sendFromTwoThreads(String client, String first, String second) {
        Conn conn = require(client);
        if (serializedWrites) {
            Trace.event("WRITE_SERIALIZED",
                    "\"" + first + "\" and \"" + second + "\" are written to " + client + " from two "
                            + "threads, and the decorator lets the second one in only after the first has "
                            + "finished its message. Both arrive whole and in one order. The lock is per "
                            + "session, so connections do not block each other — and that is the point: "
                            + "the shared registry needs concurrency, an individual session needs mutual "
                            + "exclusion, and they are two different problems with two different answers",
                    "«" + first + "» и «" + second + "» пишутся клиенту " + client + " из двух потоков, и "
                            + "декоратор пускает второй только после того, как первый закончил своё "
                            + "сообщение. Оба доходят целиком и в одном порядке. Блокировка живёт на "
                            + "сессию, поэтому соединения не блокируют друг друга, — и в этом суть: общему "
                            + "реестру нужна конкурентность, отдельной сессии нужно взаимное исключение, и "
                            + "это две разные задачи с двумя разными ответами",
                    List.of("writes", "connections"), state());
            return;
        }
        writeErrors++;
        Trace.event("CONCURRENT_WRITE_FAILED",
                thread(conn) + " is halfway through writing \"" + first + "\" to " + client
                        + " when another thread starts writing \"" + second + "\" to the same session. A "
                        + "WebSocket message is a sequence of frames, so the second write either interleaves "
                        + "its frames with the first — producing a message neither side sent — or the "
                        + "container refuses it outright with IllegalStateException: TEXT_PARTIAL_WRITING. "
                        + "Note that the registry did nothing wrong here: the map was perfectly concurrent",
                thread(conn) + " находится в середине записи «" + first + "» клиенту " + client
                        + ", когда другой поток начинает писать «" + second + "» в ту же сессию. Сообщение "
                        + "WebSocket — это последовательность фреймов, поэтому вторая запись либо перемешает "
                        + "свои фреймы с первой, дав сообщение, которого не отправлял никто, либо контейнер "
                        + "прямо откажет с IllegalStateException: TEXT_PARTIAL_WRITING. Заметьте, что реестр "
                        + "здесь ни в чём не виноват: карта была абсолютно конкурентной",
                List.of("writes", "connections", "loss"), state());
    }

    // -------------------------------------------------------------- closing

    /** {@code @OnClose} removes the connection from the registry, as it must. */
    public void disconnect(String client) {
        Conn conn = require(client);
        conn.phase = "CLOSED";
        if (conn.entry != null) {
            conn.entry.live = false;
        }
        Trace.event("UNREGISTERED",
                client + "'s socket closed and the close callback removed it from " + conn.holder
                        + ". Registering on open and removing on close have to be the same kind of code — "
                        + "the removal must also run when the connection dies abnormally (1006), because "
                        + "that is how most of them end. Note what the shrinking map does to any scheme that "
                        + "derives a value from size(): the numbers it hands out start going backwards",
                "Сокет клиента " + client + " закрылся, и колбэк закрытия убрал его из " + conn.holder
                        + ". Регистрация при открытии и удаление при закрытии обязаны быть кодом одного "
                        + "уровня надёжности — удаление должно срабатывать и при аварийном обрыве (1006), "
                        + "потому что именно так заканчивается большинство соединений. И обратите внимание, "
                        + "что уменьшение карты делает с любой схемой, выводящей значение из size(): "
                        + "выдаваемые номера начинают идти назад",
                List.of("registry", "connections"), state());
    }

    /** The socket died and nothing removed it — the registry keeps growing. */
    public void disconnectWithoutCleanup(String client) {
        Conn conn = require(client);
        conn.phase = "CLOSED";
        if (conn.entry != null) {
            conn.entry.stale = true;
        }
        leaked++;
        Trace.event("REGISTRY_LEAK",
                client + " is gone — the TCP connection dropped with no close frame — and its entry is "
                        + "still in " + conn.holder + ", holding the session object and whatever that "
                        + "references. A registry of sessions is a GC root that only shrinks when your code "
                        + "shrinks it, so a missed removal is a textbook memory leak: the map grows for "
                        + "days, every broadcast iterates more dead sockets, and the heap dump eventually "
                        + "shows one enormous map of sessions nobody is connected to",
                client + " исчез — TCP-соединение оборвалось без закрывающего фрейма, — а его запись "
                        + "по-прежнему лежит в " + conn.holder + ", удерживая объект сессии и всё, на что "
                        + "тот ссылается. Реестр сессий — это GC root, который уменьшается только тогда, "
                        + "когда его уменьшает ваш код, поэтому пропущенное удаление — хрестоматийная утечка "
                        + "памяти: карта растёт сутками, каждая рассылка обходит всё больше мёртвых сокетов, "
                        + "а в heap dump в итоге видна одна огромная карта сессий, к которым никто не "
                        + "подключён",
                List.of("registry", "loss"), state());
    }

    // -------------------------------------------------------------- reports

    /** Prints what the run actually produced: entries, values and what was lost. */
    public void report() {
        Trace.event("UNIQUENESS_AUDIT",
                "Store " + store + " with values from " + source + " on " + nodes.size() + " node(s): "
                        + "connections opened " + opened + ", registrations " + registered
                        + ", live entries " + liveEntries() + ", values handed out " + issued.size()
                        + ", distinct values " + distinct() + ", duplicate values " + duplicates
                        + ", entries lost to a data race " + lost + ", entries leaked after close " + leaked
                        + ", broadcast deliveries " + delivered + ", connections missed " + missed
                        + ", writes into a dead entry " + stale + ", concurrent-write failures "
                        + writeErrors,
                "Хранилище " + store + " со значениями из " + source + " на " + nodes.size()
                        + " узле(ах): соединений открыто " + opened + ", регистраций " + registered
                        + ", живых записей " + liveEntries() + ", значений выдано " + issued.size()
                        + ", различных значений " + distinct() + ", дубликатов " + duplicates
                        + ", записей потеряно из-за гонки " + lost + ", записей утекло после закрытия "
                        + leaked + ", доставок при рассылке " + delivered + ", соединений пропущено "
                        + missed + ", записей в мёртвую запись реестра " + stale
                        + ", отказов при конкурентной записи " + writeErrors,
                List.of("stats"), state());
    }

    /**
     * Prices the value sources against the two questions that matter: is it unique
     * inside one JVM, and is it still unique once there are two of them.
     */
    public static void compareValueSources() {
        List<Object> rows = new ArrayList<>();
        rows.add(comparisonRow("PLAIN_COUNTER", false, false, true, "NONE"));
        rows.add(comparisonRow("REGISTRY_SIZE", false, false, false, "NONE"));
        rows.add(comparisonRow("SYNCHRONIZED_COUNTER", true, false, true, "JVM_LOCK"));
        rows.add(comparisonRow("ATOMIC_COUNTER", true, false, true, "JVM_CAS"));
        rows.add(comparisonRow("RANDOM_UUID", true, true, false, "NONE"));
        rows.add(comparisonRow("DB_SEQUENCE", true, true, true, "DATABASE"));
        rows.add(comparisonRow("SNOWFLAKE_ID", true, true, true, "UNIQUE_NODE_ID"));

        Trace.event("VALUE_SOURCES_COMPARED",
                "Read the second column before the first. A counter field and sessions.size() are not "
                        + "unique even inside one process, because both read a value that nothing reserved. "
                        + "synchronized and AtomicLong fix that and stop exactly at the edge of the JVM — "
                        + "with two nodes both hand out 1, and no amount of locking inside a process can "
                        + "coordinate with a process that cannot see the lock. Past that edge there are only "
                        + "two shapes: ask one authority that everybody shares (a database sequence, Redis "
                        + "INCR), or generate something so wide that nobody has to be asked (UUID) — with "
                        + "Snowflake as the middle road, where a unique node id per process buys back "
                        + "ordering and 8 bytes",
                "Читайте вторую колонку раньше первой. Поле-счётчик и sessions.size() не уникальны даже "
                        + "внутри одного процесса, потому что оба читают значение, которое никто не "
                        + "зарезервировал. synchronized и AtomicLong это чинят и заканчиваются ровно на "
                        + "границе JVM: на двух узлах оба выдадут 1, и никакие блокировки внутри процесса не "
                        + "договорятся с процессом, который этой блокировки не видит. За этой границей форм "
                        + "всего две: спросить у единственного общего авторитета (последовательность в базе, "
                        + "Redis INCR) или сгенерировать нечто настолько широкое, что спрашивать не у кого "
                        + "(UUID), — а Snowflake посередине, где уникальный номер узла у каждого процесса "
                        + "выкупает обратно порядок и 8 байт",
                List.of("values"), comparisonState(rows));
    }

    // ------------------------------------------------------------- internals

    private void announce() {
        Trace.event("REGISTRY_READY",
                "The endpoint is up on " + nodes.size() + " node(s). The registry of open connections "
                        + storeEn() + ", and the value each new connection is given comes from "
                        + sourceEn() + ". Those are the two decisions the question is about, and they are "
                        + "independent: a perfectly concurrent map full of duplicated values is just as "
                        + "broken as a correct value in a map that loses entries",
                "Эндпоинт поднят на " + nodes.size() + " узле(ах). Реестр открытых соединений "
                        + storeRu() + ", а значение, которое получает новое соединение, берётся из "
                        + sourceRu() + ". Это два решения, о которых спрашивает вопрос, и они независимы: "
                        + "идеально конкурентная карта, полная одинаковых значений, сломана ровно так же, "
                        + "как верное значение в карте, теряющей записи",
                List.of("registry", "values"), state());
    }

    private void duplicateOnOneNode(Conn conn, Issued clash) {
        Trace.event("DUPLICATE_VALUE",
                "Value " + conn.value + " has now been given to both " + clash.client + " and " + conn.id
                        + " on " + conn.node.id + ". Both threads read the same starting point and neither "
                        + "of them was wrong on its own — the sequence of two operations was. Everything "
                        + "downstream now silently misattributes: the message routed by that id reaches the "
                        + "wrong person, the audit row names the wrong session, and a unique constraint "
                        + "somewhere turns this into a 500 for whoever was second",
                "Значение " + conn.value + " теперь выдано и клиенту " + clash.client + ", и клиенту "
                        + conn.id + " на узле " + conn.node.id + ". Оба потока прочитали одну и ту же "
                        + "отправную точку, и по отдельности ни один из них не ошибся — ошиблась "
                        + "последовательность из двух операций. Дальше по цепочке всё начинает молча "
                        + "путать адресатов: сообщение, маршрутизируемое по этому идентификатору, приходит не "
                        + "тому, строка аудита называет не ту сессию, а уникальный индекс где-нибудь "
                        + "превращает это в 500 для того, кто оказался вторым",
                List.of("values", "duplicate"), state());
    }

    private void duplicateAcrossNodes(Conn conn, Issued clash) {
        Trace.event("CROSS_NODE_DUPLICATE",
                "Value " + conn.value + " was already given to " + clash.client + " on " + clash.node
                        + ", and " + conn.id + " has just been given it again on " + conn.node.id
                        + ". Nothing raced and nothing is broken inside either process — each counter is "
                        + "correct, and each is counting its own connections from 1. This is the half of "
                        + "the question that the word GLOBALLY is doing: thread-safe means \"correct among "
                        + "the threads that share this memory\", and a second JVM shares none of it",
                "Значение " + conn.value + " уже было выдано клиенту " + clash.client + " на узле "
                        + clash.node + ", а теперь его же получил " + conn.id + " на узле " + conn.node.id
                        + ". Никакой гонки не было, и внутри каждого процесса ничего не сломано: каждый "
                        + "счётчик верен и каждый считает свои соединения с единицы. Это та половина "
                        + "вопроса, за которую отвечает слово ГЛОБАЛЬНО: потокобезопасность означает "
                        + "«корректность среди потоков, разделяющих эту память», а вторая JVM не разделяет "
                        + "из неё ничего",
                List.of("values", "duplicate", "nodes"), state());
    }

    private Conn require(String client) {
        Conn conn = connections.get(client);
        if (conn == null) {
            throw new IllegalStateException(client + " has not connected yet");
        }
        return conn;
    }

    private Conn firstRegistered() {
        for (Conn conn : connections.values()) {
            if ("REGISTERED".equals(conn.phase)) {
                return conn;
            }
        }
        return null;
    }

    private boolean anyJoining() {
        for (Conn conn : connections.values()) {
            if ("JOINING".equals(conn.phase)) {
                return true;
            }
        }
        return false;
    }

    private Entry lastLiveEntryOn(Node node) {
        Entry found = null;
        for (Entry entry : registry) {
            if (entry.live && entry.node.equals(node.id)) {
                found = entry;
            }
        }
        return found;
    }

    private Issued firstIssuedWith(String value) {
        for (Issued row : issued) {
            if (row.value.equals(value)) {
                return row;
            }
        }
        return null;
    }

    private int liveOn(Node node) {
        int count = 0;
        for (Entry entry : registry) {
            if (entry.live && entry.node.equals(node.id)) {
                count++;
            }
        }
        return count;
    }

    private int liveEntries() {
        int count = 0;
        for (Entry entry : registry) {
            if (entry.live) {
                count++;
            }
        }
        return count;
    }

    private int distinct() {
        List<String> seen = new ArrayList<>();
        for (Issued row : issued) {
            if (!seen.contains(row.value)) {
                seen.add(row.value);
            }
        }
        return seen.size();
    }

    private boolean racyValue() {
        return source == Source.PLAIN_COUNTER || source == Source.REGISTRY_SIZE;
    }

    private boolean threadSafeStore() {
        return store == Store.SYNCHRONIZED_MAP || store == Store.CONCURRENT_MAP;
    }

    private static String thread(Conn conn) {
        return conn.thread + " (" + conn.id + ")";
    }

    private static String join(List<String> names) {
        return names.isEmpty() ? "nobody" : String.join(", ", names);
    }

    /** Deterministic stand-in for UUID.randomUUID() so every run traces the same. */
    private static String uuidFor(String client) {
        return UUID.nameUUIDFromBytes(("ws-session:" + client).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ------------------------------------------------------ wording helpers

    private String putCallEn() {
        return switch (store) {
            case INSTANCE_FIELD -> "this.sessions.put(...) on its own object";
            case PLAIN_MAP -> "a bare HashMap.put(...) with no lock held";
            case SYNCHRONIZED_MAP -> "put(...) on a Collections.synchronizedMap, which takes the map's lock";
            case CONCURRENT_MAP -> "sessions.putIfAbsent(...) on a ConcurrentHashMap";
        };
    }

    private String putCallRu() {
        return switch (store) {
            case INSTANCE_FIELD -> "this.sessions.put(...) на своём собственном объекте";
            case PLAIN_MAP -> "голый HashMap.put(...) без удерживаемой блокировки";
            case SYNCHRONIZED_MAP -> "put(...) на Collections.synchronizedMap, который берёт блокировку карты";
            case CONCURRENT_MAP -> "sessions.putIfAbsent(...) на ConcurrentHashMap";
        };
    }

    private String storeNoteEn() {
        return switch (store) {
            case INSTANCE_FIELD -> "The map is a field of an object that exists for this connection only";
            case PLAIN_MAP -> "The map is shared by every connection and protected by nothing at all";
            case SYNCHRONIZED_MAP -> "Every method wraps itself in synchronized(this), so one call cannot "
                    + "overlap another — at the cost of one lock for the whole map";
            case CONCURRENT_MAP -> "The write locks one bin, not the map, and putIfAbsent decides and writes "
                    + "in a single atomic step, so readers never block and no entry can be lost";
        };
    }

    private String storeNoteRu() {
        return switch (store) {
            case INSTANCE_FIELD -> "Карта — поле объекта, который существует только для этого соединения";
            case PLAIN_MAP -> "Карта общая для всех соединений и не защищена вообще ничем";
            case SYNCHRONIZED_MAP -> "Каждый метод оборачивает себя в synchronized(this), поэтому один вызов "
                    + "не может наложиться на другой — ценой одной блокировки на всю карту";
            case CONCURRENT_MAP -> "Запись блокирует одну корзину, а не карту, и putIfAbsent принимает "
                    + "решение и пишет одним атомарным шагом, поэтому читатели не ждут и ни одна запись не "
                    + "может потеряться";
        };
    }

    private String storeEn() {
        return switch (store) {
            case INSTANCE_FIELD -> "is an instance field of the endpoint object, one per connection";
            case PLAIN_MAP -> "is a static HashMap shared by every connection";
            case SYNCHRONIZED_MAP -> "is a Collections.synchronizedMap shared by every connection";
            case CONCURRENT_MAP -> "is a ConcurrentHashMap on a singleton bean shared by every connection";
        };
    }

    private String storeRu() {
        return switch (store) {
            case INSTANCE_FIELD -> "— это поле экземпляра объекта эндпоинта, по одному на соединение";
            case PLAIN_MAP -> "— это статический HashMap, общий для всех соединений";
            case SYNCHRONIZED_MAP -> "— это Collections.synchronizedMap, общий для всех соединений";
            case CONCURRENT_MAP -> "— это ConcurrentHashMap в singleton-бине, общий для всех соединений";
        };
    }

    private String sourceEn() {
        return switch (source) {
            case PLAIN_COUNTER -> "a plain `long nextId` field incremented with nextId++";
            case REGISTRY_SIZE -> "sessions.size() + 1";
            case SYNCHRONIZED_COUNTER -> "a counter incremented inside a synchronized block";
            case ATOMIC_COUNTER -> "AtomicLong.incrementAndGet()";
            case RANDOM_UUID -> "UUID.randomUUID()";
            case DB_SEQUENCE -> "a database sequence shared by every node";
        };
    }

    private String sourceRu() {
        return switch (source) {
            case PLAIN_COUNTER -> "обычного поля `long nextId`, увеличиваемого через nextId++";
            case REGISTRY_SIZE -> "sessions.size() + 1";
            case SYNCHRONIZED_COUNTER -> "счётчика, увеличиваемого внутри synchronized-блока";
            case ATOMIC_COUNTER -> "AtomicLong.incrementAndGet()";
            case RANDOM_UUID -> "UUID.randomUUID()";
            case DB_SEQUENCE -> "последовательности в базе данных, общей для всех узлов";
        };
    }

    private String missReasonEn() {
        if (store == Store.INSTANCE_FIELD) {
            return "the registry doing the fan-out belongs to one connection, so it has never heard of the "
                    + "others";
        }
        if (nodes.size() > 1) {
            return "their sockets are held by another node's process, and an in-memory registry cannot "
                    + "reach a socket it does not hold — this is where a Redis pub/sub or a broker relay "
                    + "stops being optional";
        }
        if (lost > 0) {
            return "their entry was lost by an unsafe write, so as far as the registry is concerned they "
                    + "are not connected";
        }
        return "they are not in the registry yet — registering is a separate step from connecting, and a "
                + "message sent in that window is simply not delivered";
    }

    private String missReasonRu() {
        if (store == Store.INSTANCE_FIELD) {
            return "реестр, выполняющий рассылку, принадлежит одному соединению, поэтому про остальных он "
                    + "никогда и не слышал";
        }
        if (nodes.size() > 1) {
            return "их сокеты держит процесс другого узла, а реестр в памяти не может дотянуться до "
                    + "сокета, которого у него нет, — вот здесь Redis pub/sub или ретрансляция через брокер "
                    + "перестают быть необязательными";
        }
        if (lost > 0) {
            return "их запись потерялась при небезопасной записи, поэтому с точки зрения реестра они не "
                    + "подключены";
        }
        return "их ещё нет в реестре — регистрация является отдельным шагом от подключения, и сообщение, "
                + "отправленное в это окно, просто не доставляется";
    }

    // ------------------------------------------------------------- snapshots

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("store", store.name());
        s.put("valueSource", source.name());
        s.put("serializedWrites", serializedWrites);

        List<Object> nodeRows = new ArrayList<>();
        for (Node node : nodes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", node.id);
            row.put("counter", node.counter);
            row.put("entries", liveOn(node));
            nodeRows.add(row);
        }
        s.put("nodes", nodeRows);

        List<Object> connRows = new ArrayList<>();
        for (Conn conn : connections.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", conn.id);
            row.put("node", conn.node.id);
            row.put("thread", conn.thread);
            row.put("holder", conn.holder);
            row.put("phase", conn.phase);
            row.put("candidate", conn.candidate);
            row.put("value", conn.value);
            row.put("duplicate", conn.duplicate);
            connRows.add(row);
        }
        s.put("connections", connRows);

        List<Object> entryRows = new ArrayList<>();
        for (Entry entry : registry) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("node", entry.node);
            row.put("holder", entry.holder);
            row.put("client", entry.client);
            row.put("value", entry.value);
            row.put("live", entry.live);
            row.put("stale", entry.stale);
            row.put("duplicate", entry.duplicate);
            entryRows.add(row);
        }
        s.put("registry", entryRows);

        List<Object> valueRows = new ArrayList<>();
        for (Issued row : issued) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", row.seq);
            item.put("client", row.client);
            item.put("node", row.node);
            item.put("value", row.value);
            item.put("unique", row.unique);
            valueRows.add(item);
        }
        s.put("issued", valueRows);

        s.put("stats", stats());
        s.put("comparison", new ArrayList<>());
        return s;
    }

    private Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("opened", opened);
        stats.put("registered", registered);
        stats.put("issued", issued.size());
        stats.put("distinct", distinct());
        stats.put("duplicates", duplicates);
        stats.put("lost", lost);
        stats.put("leaked", leaked);
        stats.put("delivered", delivered);
        stats.put("missed", missed);
        stats.put("stale", stale);
        stats.put("writeErrors", writeErrors);
        return stats;
    }

    private static Map<String, Object> comparisonRow(String source, boolean uniqueInJvm,
                                                     boolean uniqueInCluster, boolean ordered,
                                                     String coordination) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);
        row.put("uniqueInJvm", uniqueInJvm);
        row.put("uniqueInCluster", uniqueInCluster);
        row.put("ordered", ordered);
        row.put("coordination", coordination);
        return row;
    }

    /** A well-formed state whose only interesting part is the comparison table. */
    private static Map<String, Object> comparisonState(List<Object> rows) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("store", "CONCURRENT_MAP");
        s.put("valueSource", "ATOMIC_COUNTER");
        s.put("serializedWrites", false);
        s.put("nodes", new ArrayList<>());
        s.put("connections", new ArrayList<>());
        s.put("registry", new ArrayList<>());
        s.put("issued", new ArrayList<>());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("opened", 0);
        stats.put("registered", 0);
        stats.put("issued", 0);
        stats.put("distinct", 0);
        stats.put("duplicates", 0);
        stats.put("lost", 0);
        stats.put("leaked", 0);
        stats.put("delivered", 0);
        stats.put("missed", 0);
        stats.put("stale", 0);
        stats.put("writeErrors", 0);
        s.put("stats", stats);

        s.put("comparison", rows);
        return s;
    }
}
