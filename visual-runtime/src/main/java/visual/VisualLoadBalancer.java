package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of one HTTP service under more load than it can
 * serve, and of the two ways out: a bigger box, or more boxes behind a load
 * balancer.
 *
 * <p>The point the model is built to make is that "overwhelmed" is not a vague
 * feeling — it is arithmetic. A node has a fixed number of concurrent slots and
 * every request occupies one for a fixed number of ticks, so its throughput is
 * {@code concurrency / serviceTicks} requests per tick and nothing the code does
 * changes that number. While arrivals stay under it, the queue is empty and
 * latency equals service time. The moment arrivals exceed it, the surplus has to
 * go somewhere, and there are only three somewheres: a queue (latency), a
 * rejection (a 503), or a dropped connection. That is the whole failure mode —
 * the server does not get slower, it gets <em>later</em>, and then it starts
 * saying no.
 *
 * <p>Everything else in this class is a consequence worth seeing:
 *
 * <ul>
 *   <li>a queue turns overload into latency, and a queue longer than the
 *       client's timeout turns it into <em>wasted</em> work — requests finished
 *       for clients who already gave up;</li>
 *   <li>{@link #scaleUp(int)} (vertical) multiplies the same single number and
 *       leaves the failure domain at one;</li>
 *   <li>{@link #addNode()} (horizontal) adds capacity <em>and</em> a survivor,
 *       at the price of a load balancer, health checks and statelessness;</li>
 *   <li>{@link Strategy} decides which node a request lands on, which only
 *       matters — and then matters enormously — once the nodes stop being
 *       identical;</li>
 *   <li>{@link #failNode(String)} shows what a balancer without health checks
 *       does with a dead node, and what the survivors inherit once it is
 *       evicted;</li>
 *   <li>{@link #sessionsInMemory()} shows the reason a service cannot be scaled
 *       horizontally at all: state that lives in one node's heap;</li>
 *   <li>{@link #sharedDependency(String, int)} shows the bottleneck moving —
 *       replicas multiply until they all queue on the one database behind
 *       them.</li>
 * </ul>
 *
 * <p>Time is discrete — the model counts <em>ticks</em> — so every run is
 * deterministic and two topologies can be priced against the same traffic.
 * Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free.
 */
public class VisualLoadBalancer {

    /** How the balancer picks the node a request is sent to. */
    public enum Strategy {
        /** No balancer at all: one server, every request goes to it. */
        NONE,
        /** Next node in the rotation, regardless of what it is already doing. */
        ROUND_ROBIN,
        /** The node with the fewest requests in flight — serving plus queued. */
        LEAST_BUSY,
        /** The node this client was pinned to, so its in-memory session is there. */
        STICKY
    }

    /** One request, from arrival to completion. */
    private static final class Req {

        private final String client;
        private final int arrivedAt;
        private int startedAt = -1;
        private boolean timedOut;

        private Req(String client, int arrivedAt) {
            this.client = client;
            this.arrivedAt = arrivedAt;
        }
    }

    /** One application instance: slots, a queue in front of them, and state. */
    private static final class Node {

        private final String name;
        private int concurrency;
        private int serviceTicks;
        private final int queueLimit;
        private boolean alive = true;
        private boolean inRotation = true;
        private boolean degraded;
        private int diedAt = -1;
        private final List<Req> serving = new ArrayList<>();
        private final List<Req> queue = new ArrayList<>();
        private final Set<String> sessions = new LinkedHashSet<>();
        private int served;
        private int rejected;

        private Node(String name, int concurrency, int serviceTicks, int queueLimit) {
            this.name = name;
            this.concurrency = Math.max(1, concurrency);
            this.serviceTicks = Math.max(1, serviceTicks);
            this.queueLimit = Math.max(0, queueLimit);
        }

        /** Requests this node is responsible for right now. */
        private int load() {
            return serving.size() + queue.size();
        }

        /** Throughput ceiling: slots divided by how long a slot is held. */
        private double capacity() {
            return concurrency / (double) serviceTicks;
        }
    }

    /** One tick of the run, rendered as a column of the traffic strip. */
    private static final class Slot {

        private final int tick;
        private int arrived;
        private int served;
        private int rejected;
        private int failed;
        private int queued;
        private int nodes;

        private Slot(int tick) {
            this.tick = tick;
        }
    }

    // ------------------------------------------------------------------ state

    private final String service;
    private final int nodeConcurrency;
    private final int nodeServiceTicks;
    private final int nodeQueueLimit;

    private Strategy strategy;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Slot> timeline = new ArrayList<>();
    /** Sticky routing: which node each client was pinned to. */
    private final Map<String, String> pinned = new LinkedHashMap<>();

    private String bottleneckName;
    private int bottleneckCapacity;
    private int bottleneckUsed;
    private boolean bottleneckBlocked;

    private int clientTimeout = 8;
    private int healthCheckTicks;
    private boolean healthChecks;
    private boolean localSessions;
    /** Suppresses tracing so a run can be priced silently for the comparison. */
    private boolean quiet;

    private int now;
    private int rotationCursor;

    private int arrived;
    private int served;
    private int rejected;
    private int failed;
    private int timedOut;
    private int wasted;
    private int sessionMisses;
    private int blackholed;
    private int latencySum;
    private int maxLatency;
    private int peakQueue;

    /** True while a real accumulation exists, so growth and drain can alternate. */
    private boolean backlog;
    private boolean saidLatency;
    private boolean saidRejected;
    private boolean saidTimeout;
    private boolean saidWasted;
    private boolean saidBottleneck;
    private boolean saidHotspot;
    private boolean saidBlackhole;
    private boolean saidSessionMiss;
    private boolean saidSticky;

    private VisualLoadBalancer(String service, int replicas, int concurrency, int serviceTicks,
                               int queueLimit, Strategy strategy) {
        this.service = service;
        this.nodeConcurrency = Math.max(1, concurrency);
        this.nodeServiceTicks = Math.max(1, serviceTicks);
        this.nodeQueueLimit = Math.max(0, queueLimit);
        this.strategy = strategy;
        for (int i = 0; i < Math.max(1, replicas); i++) {
            nodes.add(new Node("app-" + (i + 1), nodeConcurrency, nodeServiceTicks, nodeQueueLimit));
        }
        timeline.add(new Slot(0));
        stamp();
    }

    // -------------------------------------------------------------- factories

    /**
     * One server, no balancer — the shape almost every system starts in.
     *
     * @param service      what this endpoint is called, e.g. "POST /checkout"
     * @param concurrency  how many requests it can work on at once
     * @param serviceTicks how many ticks one request occupies a slot
     * @param queueLimit   how many waiting requests it accepts before refusing
     */
    public static VisualLoadBalancer singleServer(String service, int concurrency, int serviceTicks,
                                                  int queueLimit) {
        VisualLoadBalancer server =
                new VisualLoadBalancer(service, 1, concurrency, serviceTicks, queueLimit, Strategy.NONE);
        server.emit("SERVER_STARTED",
                service + " is served by one instance with " + server.nodeConcurrency
                        + " concurrent slot(s), " + server.nodeServiceTicks + " tick(s) of work per "
                        + "request and room for " + server.nodeQueueLimit + " waiting request(s). That "
                        + "makes its throughput exactly " + round(server.capacityPerTick())
                        + " request(s) per tick, and no amount of clever code changes that number — it is "
                        + "slots divided by how long a slot is held. Below that rate the queue stays empty "
                        + "and latency is just the work. Above it, the surplus has to go somewhere, and "
                        + "there are only three somewheres: the queue, a 503, or a dropped connection",
                service + " обслуживается одним экземпляром: " + server.nodeConcurrency
                        + " одновременных слот(ов), " + server.nodeServiceTicks + " тик(ов) работы на "
                        + "запрос и место для " + server.nodeQueueLimit + " ожидающих запрос(ов). Значит "
                        + "его пропускная способность ровно " + round(server.capacityPerTick())
                        + " запрос(ов) в тик, и никакой умный код это число не поменяет — это слоты, "
                        + "делённые на время удержания слота. Ниже этой скорости очередь пуста, а задержка "
                        + "равна самой работе. Выше — излишку надо куда-то деться, а деться можно только "
                        + "в три места: в очередь, в 503 или в разорванное соединение",
                List.of("nodes", "capacity"));
        return server;
    }

    /**
     * Several identical replicas behind a load balancer, routed round-robin
     * until {@link #strategy(Strategy)} says otherwise.
     */
    public static VisualLoadBalancer behindBalancer(String service, int replicas, int concurrency,
                                                    int serviceTicks, int queueLimit) {
        VisualLoadBalancer pool = new VisualLoadBalancer(service, replicas, concurrency, serviceTicks,
                queueLimit, Strategy.ROUND_ROBIN);
        pool.emit("BALANCER_STARTED",
                service + " is served by " + pool.nodes.size() + " identical replica(s) behind a load "
                        + "balancer, " + round(pool.nodeCapacity()) + " request(s) per tick each, "
                        + round(pool.capacityPerTick()) + " in total. The balancer owns the address "
                        + "clients call and picks a replica per request, so capacity is now a number you "
                        + "can change by deployment rather than by rewriting code. Two things came with "
                        + "it: the balancer has to know which replicas are alive, and a request may land "
                        + "on any of them — so anything a replica remembers between requests is now a bug "
                        + "waiting to be found",
                service + " обслуживается " + pool.nodes.size() + " одинаковыми репликами за "
                        + "балансировщиком, по " + round(pool.nodeCapacity()) + " запрос(ов) в тик каждая, "
                        + round(pool.capacityPerTick()) + " суммарно. Балансировщик владеет адресом, "
                        + "который зовут клиенты, и выбирает реплику на каждый запрос, поэтому "
                        + "производительность теперь меняется деплоем, а не переписыванием кода. Вместе с "
                        + "этим пришло двое: балансировщик обязан знать, какие реплики живы, и запрос "
                        + "может попасть на любую из них — так что всё, что реплика помнит между "
                        + "запросами, теперь баг, который ждёт своего часа",
                List.of("balancer", "nodes", "capacity"));
        return pool;
    }

    // ----------------------------------------------------------- configuration

    /** How long a client waits before giving up on a request. */
    public VisualLoadBalancer clientTimeout(int ticks) {
        this.clientTimeout = Math.max(1, ticks);
        return this;
    }

    /** Turns on health checks, noticing a dead replica after {@code ticks}. */
    public VisualLoadBalancer healthChecks(int ticks) {
        this.healthChecks = true;
        this.healthCheckTicks = Math.max(1, ticks);
        return this;
    }

    /** Switches the balancing algorithm. */
    public VisualLoadBalancer strategy(Strategy chosen) {
        this.strategy = chosen;
        resetAnnouncements();
        emit("STRATEGY_CHANGED",
                "The balancer now routes by " + chosen + ". With identical replicas and identical "
                        + "requests every algorithm looks the same, which is why the choice is usually "
                        + "made carelessly. It stops looking the same the moment the nodes stop being "
                        + "identical — a node mid-GC, a node with a cold cache, a node on noisy "
                        + "hardware — because ROUND_ROBIN keeps feeding it its full share while "
                        + "LEAST_BUSY notices that its share is not coming back",
                "Балансировщик теперь маршрутизирует по " + chosen + ". При одинаковых репликах и "
                        + "одинаковых запросах все алгоритмы выглядят одинаково — поэтому выбор обычно и "
                        + "делают небрежно. Одинаковыми они быть перестают ровно тогда, когда перестают "
                        + "быть одинаковыми узлы: узел в GC, узел с холодным кэшем, узел на шумном "
                        + "железе, — потому что ROUND_ROBIN продолжает отдавать ему полную долю, а "
                        + "LEAST_BUSY замечает, что эта доля не возвращается",
                List.of("balancer", "strategy"));
        return this;
    }

    /**
     * Declares that a replica keeps per-client state (an HTTP session, a cart,
     * an upload) in its own heap.
     */
    public VisualLoadBalancer sessionsInMemory() {
        this.localSessions = true;
        emit("STATE_IS_LOCAL",
                "Each replica of " + service + " keeps per-client state in its own heap: an HttpSession, "
                        + "a shopping cart, a half-finished upload, a cached permission set. Nothing is "
                        + "wrong with that on one server — it is the fastest storage there is. It becomes "
                        + "the thing that stops horizontal scaling, because a second replica is not a "
                        + "copy of the first: it has the same code and none of the memory. The next "
                        + "request from a client is now only correct if it happens to land where its "
                        + "state is",
                "Каждая реплика " + service + " держит состояние клиента в собственной куче: HttpSession, "
                        + "корзину, недозагруженный файл, закэшированные права. На одном сервере в этом "
                        + "нет ничего плохого — это самое быстрое хранилище, какое бывает. Но именно оно "
                        + "и не даёт масштабироваться горизонтально, потому что вторая реплика — не копия "
                        + "первой: у неё тот же код и ни байта той памяти. Следующий запрос клиента "
                        + "теперь корректен, только если случайно попал туда, где лежит его состояние",
                List.of("sessions", "nodes"));
        return this;
    }

    /**
     * Puts one shared resource behind every replica — a primary database, a
     * licence server, a payment gateway — that can only start
     * {@code capacityPerTick} requests per tick no matter how many replicas ask.
     */
    public VisualLoadBalancer sharedDependency(String name, int capacityPerTick) {
        this.bottleneckName = name;
        this.bottleneckCapacity = Math.max(1, capacityPerTick);
        return this;
    }

    // ----------------------------------------------------------------- traffic

    /** Sends {@code perTick} requests per tick for {@code ticks} ticks. */
    public void traffic(int perTick, int ticks) {
        for (int i = 0; i < ticks; i++) {
            List<String> batch = new ArrayList<>();
            for (int j = 0; j < perTick; j++) {
                batch.add("web");
            }
            send(batch);
            tick();
        }
    }

    /** One request from each named client, then one tick. */
    public void round(String... clients) {
        send(List.of(clients));
        tick();
    }

    /** A spike: {@code count} requests land in this single tick. */
    public void burst(int count) {
        List<String> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            batch.add("web");
        }
        send(batch);
    }

    /** Advances the clock by one tick. */
    public void tick() {
        tick(1);
    }

    /** Advances the clock, completing work and admitting queued requests. */
    public void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            now++;
            timeline.add(new Slot(now));
            bottleneckUsed = 0;
            bottleneckBlocked = false;

            complete();
            evictDeadNodes();
            expireClientPatience();
            admitFromQueues();

            stamp();
            announceConditions();
        }
    }

    // ----------------------------------------------------------------- scaling

    /**
     * Vertical scaling: the same one box, with more of it. Everything about the
     * topology stays as it was — only the single number changes.
     */
    public void scaleUp(int concurrency) {
        double before = capacityPerTick();
        for (Node node : nodes) {
            node.concurrency = Math.max(1, concurrency);
        }
        resetAnnouncements();
        emit("SCALED_UP",
                "Bigger box: " + concurrency + " concurrent slot(s) per instance, so capacity goes from "
                        + round(before) + " to " + round(capacityPerTick()) + " request(s) per tick. This "
                        + "is the cheapest fix that exists — no new code, no new failure modes, no "
                        + "distributed anything — and it is the right first answer surprisingly often. "
                        + "What you did not buy: the ceiling is still there, just further away; the "
                        + "resize is still a restart of the thing serving your traffic; and the failure "
                        + "domain is still exactly one machine, so this box dying is still the whole "
                        + "service dying",
                "Коробка побольше: " + concurrency + " одновременных слот(ов) на экземпляр, значит "
                        + "производительность выросла с " + round(before) + " до " + round(capacityPerTick())
                        + " запрос(ов) в тик. Это самое дешёвое из существующих решений — ни нового кода, "
                        + "ни новых режимов отказа, ни распределённости — и удивительно часто именно оно "
                        + "и есть правильный первый ответ. Чего вы не купили: потолок никуда не делся, он "
                        + "просто отодвинулся; смена размера по-прежнему рестарт того, что обслуживает "
                        + "трафик; и домен отказа по-прежнему ровно одна машина, так что смерть этой "
                        + "коробки — это по-прежнему смерть всего сервиса",
                List.of("nodes", "capacity"));
    }

    /**
     * Horizontal scaling: one more replica in the rotation. The first call on a
     * single server also puts a load balancer in front of it.
     */
    public void addNode() {
        if (strategy == Strategy.NONE) {
            strategy = Strategy.ROUND_ROBIN;
            emit("BALANCER_STARTED",
                    "A load balancer moves in front of " + service + ". From now on clients resolve the "
                            + "balancer's address, not a server's, and each request is handed to one "
                            + "replica — which is the whole reason capacity can grow at all. The price is "
                            + "paid immediately: the balancer needs health checks to know who is alive, "
                            + "and the replicas must be interchangeable, because the client no longer "
                            + "chooses which one answers",
                    "Перед " + service + " встаёт балансировщик. С этого момента клиенты резолвят адрес "
                            + "балансировщика, а не сервера, и каждый запрос отдаётся одной из реплик — "
                            + "именно поэтому мощность вообще может расти. Цена платится сразу: "
                            + "балансировщику нужны health-check'и, чтобы знать, кто жив, а реплики "
                            + "обязаны быть взаимозаменяемыми, потому что клиент больше не выбирает, кто "
                            + "ему ответит",
                    List.of("balancer"));
        }
        double before = capacityPerTick();
        Node node = new Node("app-" + (nodes.size() + 1), nodeConcurrency, nodeServiceTicks, nodeQueueLimit);
        nodes.add(node);
        resetAnnouncements();
        stamp();
        emit("NODE_ADDED",
                node.name + " joins the rotation: capacity " + round(before) + " → "
                        + round(capacityPerTick()) + " request(s) per tick across " + liveNodes()
                        + " replica(s). Note what horizontal scaling buys that a bigger box does not — the "
                        + "service now survives losing a machine, capacity can be changed while it is "
                        + "running, and the ceiling is a budget rather than a hardware catalogue. Note "
                        + "also what it demands in return: replicas that hold no state of their own, and "
                        + "a balancer that finds out quickly when one of them stops answering",
                node.name + " входит в ротацию: мощность " + round(before) + " → "
                        + round(capacityPerTick()) + " запрос(ов) в тик на " + liveNodes()
                        + " реплик(ах). Обратите внимание, что даёт горизонтальное масштабирование сверх "
                        + "большой коробки: сервис теперь переживает потерю машины, мощность меняется на "
                        + "ходу, а потолок определяется бюджетом, а не каталогом железа. И чего оно "
                        + "требует взамен: реплик, не хранящих собственного состояния, и балансировщика, "
                        + "который быстро узнаёт, что одна из них перестала отвечать",
                List.of("nodes", "capacity", "node:" + node.name));
    }

    /** A node gets slow — a long GC pause, a cold cache, noisy neighbours. */
    public void degrade(String name, int serviceTicks) {
        Node node = node(name);
        if (node == null) {
            return;
        }
        node.serviceTicks = Math.max(1, serviceTicks);
        node.degraded = true;
        resetAnnouncements();
        emit("NODE_DEGRADED",
                node.name + " now takes " + node.serviceTicks + " tick(s) per request instead of "
                        + nodeServiceTicks + ", so its throughput drops to " + round(node.capacity())
                        + " request(s) per tick while its neighbours still do " + round(nodeCapacity())
                        + ". It is not down — it answers everything, just slowly, which is precisely why "
                        + "it is dangerous: a health check that only asks \"are you there?\" gets a "
                        + "cheerful yes. This is the ordinary state of a real fleet: a node in a long GC "
                        + "pause, a node that just restarted with an empty cache, a node sharing a host "
                        + "with something greedy",
                node.name + " теперь тратит " + node.serviceTicks + " тик(ов) на запрос вместо "
                        + nodeServiceTicks + ", поэтому его пропускная способность падает до "
                        + round(node.capacity()) + " запрос(ов) в тик, тогда как соседи держат "
                        + round(nodeCapacity()) + ". Он не лежит — он отвечает на всё, просто медленно, и "
                        + "именно поэтому опасен: health-check, спрашивающий «ты живой?», получает "
                        + "бодрое «да». Это обычное состояние реального парка машин: узел в длинной паузе "
                        + "GC, узел, только что перезапущенный с пустым кэшем, узел, делящий хост с "
                        + "кем-то прожорливым",
                List.of("nodes", "node:" + node.name));
    }

    /** An instance dies: the process is gone and everything in flight with it. */
    public void failNode(String name) {
        Node node = node(name);
        if (node == null || !node.alive) {
            return;
        }
        int lost = node.load();
        int sessions = node.sessions.size();
        node.alive = false;
        node.diedAt = now;
        node.serving.clear();
        node.queue.clear();
        failed += lost;
        current().failed += lost;
        if (!healthChecks) {
            // Nobody is asking, so the balancer keeps routing to a corpse.
            node.inRotation = true;
        }
        resetAnnouncements();
        stamp();
        emit("NODE_FAILED",
                node.name + " is gone — process killed, host lost, kernel panicked, it does not matter "
                        + "which. " + lost + " request(s) it was holding died with it, and they are not "
                        + "in anyone's error log yet: the clients are still waiting on sockets that will "
                        + "never answer. Capacity is now " + round(capacityPerTick()) + " request(s) per "
                        + "tick across " + liveNodes() + " live replica(s)"
                        + (healthChecks
                                ? ", and the balancer will find out in " + healthCheckTicks + " tick(s)"
                                : ", and the balancer has not been told and has no way to notice"),
                node.name + " больше нет — процесс убит, хост потерян, паника ядра; неважно что именно. "
                        + lost + " запрос(ов), которые он держал, умерли вместе с ним, и пока их нет ни в "
                        + "одном журнале ошибок: клиенты всё ещё ждут на сокетах, которые никогда не "
                        + "ответят. Мощность теперь " + round(capacityPerTick()) + " запрос(ов) в тик на "
                        + liveNodes() + " живых реплик(ах)"
                        + (healthChecks
                                ? ", и балансировщик узнает об этом через " + healthCheckTicks + " тик(ов)"
                                : ", а балансировщику не сказали, и заметить он это не может"),
                List.of("nodes", "node:" + node.name));
        if (sessions > 0) {
            emit("SESSIONS_LOST",
                    sessions + " client session(s) lived only in " + node.name + "'s heap and are now "
                            + "unrecoverable. Those users are logged out, their carts are empty and their "
                            + "half-finished forms are gone — one machine dying became a visible product "
                            + "failure for a subset of users, which is the exact thing horizontal scaling "
                            + "was supposed to prevent. Sticky sessions did not make the state safe; they "
                            + "only made it reliably reachable while the machine was alive",
                    sessions + " клиентских сесси(й) жили только в куче " + node.name + " и теперь "
                            + "невосстановимы. Эти пользователи разлогинены, их корзины пусты, их "
                            + "недозаполненные формы исчезли — смерть одной машины превратилась в видимый "
                            + "продуктовый сбой для части пользователей, то есть ровно в то, что "
                            + "горизонтальное масштабирование должно было предотвратить. Sticky-сессии не "
                            + "сделали состояние надёжным — они лишь сделали его достижимым, пока машина "
                            + "была жива",
                    List.of("sessions", "node:" + node.name));
        }
        node.sessions.clear();
        pinned.entrySet().removeIf(e -> e.getValue().equals(node.name));
        if (liveNodes() == 0) {
            emit("OUTAGE",
                    "There is nothing left to route to. Every request to " + service + " now fails, and "
                            + "no retry, no timeout tuning and no circuit breaker helps, because the "
                            + "capacity behind the address is zero. This is what \"one server\" really "
                            + "costs: not slow — absent. A second replica would have turned this same "
                            + "event into a capacity problem instead of an outage, which is why "
                            + "availability, not throughput, is usually the argument that actually wins "
                            + "the budget",
                    "Маршрутизировать больше некуда. Любой запрос к " + service + " теперь падает, и "
                            + "никакой ретрай, никакая настройка таймаутов и никакой circuit breaker не "
                            + "помогут, потому что мощность за этим адресом равна нулю. Вот сколько на "
                            + "самом деле стоит «один сервер»: не медленно — а никак. Вторая реплика "
                            + "превратила бы то же самое событие в проблему мощности, а не в простой; "
                            + "поэтому бюджет обычно выигрывает аргумент про доступность, а не про "
                            + "пропускную способность",
                    List.of("nodes", "outage"));
        }
    }

    /** Prints what this topology actually did with the traffic it was given. */
    public void report() {
        emit("RUN_AUDIT",
                "After " + now + " tick(s) on " + topology() + " with " + liveNodes() + " live replica(s) "
                        + "at " + round(capacityPerTick()) + " request(s) per tick: arrived " + arrived
                        + ", served " + served + ", rejected " + rejected + ", failed " + failed
                        + ", clients who gave up " + timedOut + ", of which " + wasted + " were finished "
                        + "anyway for nobody. Average latency " + round(avgLatency()) + " tick(s), worst "
                        + maxLatency + ", peak queue depth " + peakQueue
                        + ". Read those together: served versus arrived is whether you have enough "
                        + "capacity, latency versus service time is how much of it is queueing, and "
                        + "wasted is how much of your capacity was spent on answers nobody read",
                "После " + now + " тик(ов) на " + topology() + " с " + liveNodes() + " живыми репликами "
                        + "по " + round(capacityPerTick()) + " запрос(ов) в тик: пришло " + arrived
                        + ", обслужено " + served + ", отвергнуто " + rejected + ", провалено " + failed
                        + ", клиентов сдалось " + timedOut + ", из них " + wasted + " всё равно доделали "
                        + "в никуда. Средняя задержка " + round(avgLatency()) + " тик(ов), худшая "
                        + maxLatency + ", пик очереди " + peakQueue
                        + ". Читайте это вместе: обслужено против пришло — хватает ли мощности, задержка "
                        + "против времени работы — сколько в ней стояния в очереди, а «в никуда» — "
                        + "сколько мощности ушло на ответы, которых никто не прочитал",
                List.of("stats"));
    }

    /**
     * Runs one identical traffic pattern through four topologies and prices
     * them — the table the interview question is really asking for.
     */
    public static void compare() {
        int perTick = 5;
        int ticks = 20;
        List<Object> rows = new ArrayList<>();
        rows.add(price("SINGLE", 1, 4, 2, 6, 0, perTick, ticks));
        rows.add(price("BIGGER_BOX", 1, 12, 2, 6, 0, perTick, ticks));
        rows.add(price("THREE_NODES", 3, 4, 2, 6, 0, perTick, ticks));
        rows.add(price("THREE_NODES_ONE_DB", 3, 4, 2, 6, 3, perTick, ticks));

        Trace.event("TOPOLOGIES_COMPARED",
                "The same " + (perTick * ticks) + " requests, four topologies. SINGLE is the starting "
                        + "point and it drops most of them. BIGGER_BOX and THREE_NODES serve the same "
                        + "traffic at the same latency — on throughput alone, vertical scaling wins on "
                        + "simplicity, and the honest reason to pick horizontal is the column that is not "
                        + "about throughput at all: one of them survives losing a machine and the other "
                        + "does not. THREE_NODES_ONE_DB is the row people forget: the same three "
                        + "replicas, the same three times the application capacity, and one shared "
                        + "database behind them — and it serves 74 of the 100 instead of all of them, at "
                        + "more than twice the average latency and ten times the worst. Three times the "
                        + "app tier did not buy three times the throughput, because the queue moved to "
                        + "the thing that was never replicated",
                "Одни и те же " + (perTick * ticks) + " запросов, четыре топологии. SINGLE — точка "
                        + "отсчёта, и она теряет большую их часть. BIGGER_BOX и THREE_NODES обслуживают "
                        + "один и тот же трафик с одинаковой задержкой: по чистой пропускной способности "
                        + "вертикальное масштабирование выигрывает простотой, а честная причина выбрать "
                        + "горизонтальное — колонка, которая вообще не про пропускную способность: одна "
                        + "топология переживает потерю машины, а другая нет. THREE_NODES_ONE_DB — строка, "
                        + "о которой забывают: те же три реплики, та же втрое большая мощность "
                        + "приложения и одна общая база за ними — и обслужено 74 из 100 вместо всех, при "
                        + "вдвое большей средней задержке и вдесятеро большей худшей. Втрое больший слой "
                        + "приложения не купил втрое большую пропускную способность, потому что очередь "
                        + "переехала в то, что никто не реплицировал",
                List.of("stats", "capacity"), comparisonState(rows));
    }

    // ---------------------------------------------------------------- routing

    /** Routes and admits a batch of requests arriving in the current tick. */
    private void send(List<String> clients) {
        int accepted = 0;
        int queued = 0;
        int refused = 0;
        int dropped = 0;
        for (String client : clients) {
            arrived++;
            current().arrived++;
            Req request = new Req(client, now);
            Node target = route(client);
            if (target == null || !target.alive) {
                failed++;
                current().failed++;
                dropped++;
                if (target != null) {
                    blackholed++;
                }
                continue;
            }
            if (localSessions && !claimSession(target, client)) {
                failed++;
                current().failed++;
                sessionMisses++;
                dropped++;
                continue;
            }
            if (target.serving.size() < target.concurrency && bottleneckAllows()) {
                start(target, request);
                accepted++;
            } else if (target.queue.size() < target.queueLimit) {
                target.queue.add(request);
                queued++;
            } else {
                target.rejected++;
                rejected++;
                current().rejected++;
                refused++;
            }
        }
        stamp();
        emit("TRAFFIC_ARRIVED",
                "Tick " + now + ": " + clients.size() + " request(s) reach " + service + " against "
                        + round(capacityPerTick()) + " request(s) per tick of capacity — " + accepted
                        + " started immediately, " + queued + " went into a queue, " + refused
                        + " were refused outright and " + dropped + " never reached a working replica. "
                        + "Queue depth is now " + totalQueued() + ". That first comparison is the entire "
                        + "diagnosis: while arrivals are below capacity none of the other numbers can be "
                        + "anything but zero",
                "Тик " + now + ": " + clients.size() + " запрос(ов) приходят на " + service + " против "
                        + round(capacityPerTick()) + " запрос(ов) в тик мощности — " + accepted
                        + " начали выполняться сразу, " + queued + " ушли в очередь, " + refused
                        + " отвергнуты сразу, " + dropped + " вообще не добрались до работающей реплики. "
                        + "Глубина очереди теперь " + totalQueued() + ". Это первое сравнение и есть весь "
                        + "диагноз: пока приход ниже мощности, все остальные числа не могут быть ничем, "
                        + "кроме нуля",
                List.of("traffic", "queue"));
        announceRouting();
    }

    /** Picks the replica this request is sent to, per the current strategy. */
    private Node route(String client) {
        List<Node> rotation = rotation();
        if (rotation.isEmpty()) {
            return null;
        }
        if (strategy == Strategy.NONE) {
            return nodes.get(0);
        }
        if (strategy == Strategy.LEAST_BUSY) {
            Node best = rotation.get(0);
            for (Node candidate : rotation) {
                if (candidate.load() < best.load()) {
                    best = candidate;
                }
            }
            return best;
        }
        if (strategy == Strategy.STICKY) {
            String pin = pinned.get(client);
            Node pinnedNode = pin == null ? null : node(pin);
            if (pinnedNode != null && pinnedNode.inRotation) {
                return pinnedNode;
            }
            Node chosen = rotation.get(Math.floorMod(rotationCursor++, rotation.size()));
            pinned.put(client, chosen.name);
            return chosen;
        }
        return rotation.get(Math.floorMod(rotationCursor++, rotation.size()));
    }

    /** The nodes the balancer believes it can send traffic to. */
    private List<Node> rotation() {
        List<Node> live = new ArrayList<>();
        for (Node node : nodes) {
            if (node.inRotation) {
                live.add(node);
            }
        }
        return live;
    }

    /**
     * True when this replica may serve the client: it either already owns the
     * client's session, or nobody does and it takes ownership.
     */
    private boolean claimSession(Node target, String client) {
        if (target.sessions.contains(client)) {
            return true;
        }
        for (Node node : nodes) {
            if (node != target && node.sessions.contains(client)) {
                return false;
            }
        }
        target.sessions.add(client);
        return true;
    }

    // -------------------------------------------------------------- mechanics

    private void start(Node node, Req request) {
        request.startedAt = now;
        node.serving.add(request);
        if (bottleneckCapacity > 0) {
            bottleneckUsed++;
        }
    }

    private boolean bottleneckAllows() {
        if (bottleneckCapacity <= 0) {
            return true;
        }
        if (bottleneckUsed < bottleneckCapacity) {
            return true;
        }
        bottleneckBlocked = true;
        return false;
    }

    /** Finishes everything whose service time has elapsed. */
    private void complete() {
        for (Node node : nodes) {
            if (!node.alive) {
                continue;
            }
            for (Req request : new ArrayList<>(node.serving)) {
                if (now - request.startedAt < node.serviceTicks) {
                    continue;
                }
                node.serving.remove(request);
                node.served++;
                served++;
                current().served++;
                int latency = now - request.arrivedAt;
                latencySum += latency;
                maxLatency = Math.max(maxLatency, latency);
                if (request.timedOut) {
                    wasted++;
                }
            }
        }
    }

    /** Health checks notice a dead replica and take it out of the rotation. */
    private void evictDeadNodes() {
        if (!healthChecks) {
            return;
        }
        for (Node node : nodes) {
            if (node.alive || !node.inRotation || now - node.diedAt < healthCheckTicks) {
                continue;
            }
            node.inRotation = false;
            emit("HEALTH_CHECK_FAILED",
                    "The balancer's probe to " + node.name + " did not come back. This is the only way a "
                            + "balancer ever learns anything: it does not get told about a crash, it "
                            + "notices the absence of an answer, which is why the interval and the "
                            + "threshold are a real design decision — too slow and you keep sending real "
                            + "users into a hole, too twitchy and a node that paused for a second gets "
                            + "thrown out of a fleet that needed it",
                    "Проба балансировщика к " + node.name + " не вернулась. Это единственный способ, "
                            + "которым балансировщик вообще что-то узнаёт: ему не сообщают о падении, он "
                            + "замечает отсутствие ответа — поэтому интервал и порог здесь настоящее "
                            + "проектное решение: слишком медленно — и вы продолжаете отправлять живых "
                            + "пользователей в яму, слишком нервно — и узел, задумавшийся на секунду, "
                            + "выкидывается из парка, которому он был нужен",
                    List.of("balancer", "node:" + node.name));
            resetAnnouncements();
            emit("NODE_EVICTED",
                    node.name + " is out of the rotation after " + (now - node.diedAt) + " tick(s) of "
                            + "being dead, and requests stop being sent into the void. Everything that "
                            + "arrived during those tick(s) failed for real users — health checks bound "
                            + "the damage of a crash, they do not prevent it, and that window is exactly "
                            + "how long your error rate is elevated",
                    node.name + " выведен из ротации через " + (now - node.diedAt) + " тик(ов) после "
                            + "смерти, и запросы перестают уходить в пустоту. Всё, что пришло за эти "
                            + "тики, провалилось у настоящих пользователей: health-check'и ограничивают "
                            + "ущерб от падения, но не предотвращают его, и это окно — ровно то время, "
                            + "пока у вас повышен процент ошибок",
                    List.of("balancer", "node:" + node.name));
            double perSurvivor = liveNodes() == 0 ? 0 : capacityPerTick() / liveNodes();
            emit("LOAD_REDISTRIBUTED",
                    "The share " + node.name + " used to take is now split across " + liveNodes()
                            + " survivor(s), so each one is carrying about " + round(perSurvivor)
                            + " request(s) per tick of the load with " + round(nodeCapacity())
                            + " of capacity. This is the calculation that decides whether losing a node "
                            + "is a non-event or the start of a cascade: replicas have to be sized so "
                            + "that N-1 of them still fit the traffic, because the load does not shrink "
                            + "when the fleet does",
                    "Доля, которую забирал " + node.name + ", теперь делится между " + liveNodes()
                            + " выжившими, поэтому каждый несёт около " + round(perSurvivor)
                            + " запрос(ов) в тик при мощности " + round(nodeCapacity())
                            + ". Именно этот расчёт решает, потеря узла — это ничто или начало каскада: "
                            + "реплики надо считать так, чтобы N-1 из них ещё вмещали трафик, потому что "
                            + "нагрузка не уменьшается вместе с парком машин",
                    List.of("nodes", "capacity"));
        }
    }

    /** Marks requests whose clients have waited longer than they agreed to. */
    private void expireClientPatience() {
        int before = timedOut;
        for (Node node : nodes) {
            if (!node.alive) {
                continue;
            }
            for (Req request : node.queue) {
                if (!request.timedOut && now - request.arrivedAt >= clientTimeout) {
                    request.timedOut = true;
                    timedOut++;
                }
            }
            for (Req request : node.serving) {
                if (!request.timedOut && now - request.arrivedAt >= clientTimeout) {
                    request.timedOut = true;
                    timedOut++;
                }
            }
        }
        if (timedOut > before && !saidTimeout) {
            saidTimeout = true;
            emit("CLIENT_TIMEOUT",
                    (timedOut - before) + " client(s) have now waited " + clientTimeout + " tick(s) and "
                            + "given up. Nothing happened on the server: the request is still in a queue "
                            + "or still being worked on, and it will be finished properly and written to "
                            + "a socket nobody is reading. Worse, a client that gave up usually retries, "
                            + "so the offered load goes UP at the exact moment the server is least able "
                            + "to take it — the queue you added to be resilient is now amplifying the "
                            + "overload",
                    (timedOut - before) + " клиент(ов) прождали " + clientTimeout + " тик(ов) и сдались. "
                            + "На сервере при этом не произошло ничего: запрос всё ещё в очереди или всё "
                            + "ещё выполняется, и его аккуратно доделают и запишут в сокет, который никто "
                            + "не читает. Хуже того, сдавшийся клиент обычно повторяет запрос, поэтому "
                            + "предлагаемая нагрузка РАСТЁТ ровно в тот момент, когда сервер меньше "
                            + "всего способен её принять: очередь, добавленная ради устойчивости, теперь "
                            + "усиливает перегрузку",
                    List.of("queue", "timeout"));
        }
    }

    /** Starts queued work in every free slot the shared dependency allows. */
    private void admitFromQueues() {
        for (Node node : nodes) {
            if (!node.alive) {
                continue;
            }
            while (node.serving.size() < node.concurrency && !node.queue.isEmpty() && bottleneckAllows()) {
                start(node, node.queue.remove(0));
            }
        }
    }

    // ---------------------------------------------------------- announcements

    /** Fires the routing-level observations, each at most once per phase. */
    private void announceRouting() {
        if (blackholed > 0 && !saidBlackhole) {
            saidBlackhole = true;
            emit("BLACKHOLED",
                    "Requests are being routed to a replica that is not there. Without health checks the "
                            + "balancer's list of backends is a static configuration file, not a fact "
                            + "about the world, so it keeps handing out its share to a dead address — "
                            + "roughly one request in " + rotation().size() + " simply fails while the "
                            + "surviving replicas sit half-idle. The tell in production is an error rate "
                            + "that is a clean fraction and refuses to move: a third of requests failing, "
                            + "with latency and CPU perfectly normal",
                    "Запросы маршрутизируются на реплику, которой нет. Без health-check'ов список бэкендов "
                            + "у балансировщика — это статический конфиг, а не факт о мире, поэтому он "
                            + "продолжает отдавать свою долю мёртвому адресу: примерно один запрос из "
                            + rotation().size() + " просто падает, пока выжившие реплики стоят "
                            + "полупустые. Симптом в проде узнаваем: доля ошибок — ровная дробь, которая "
                            + "не двигается, треть запросов падает, а задержка и CPU идеально нормальные",
                    List.of("balancer", "nodes"));
        }
        if (sessionMisses > 0 && !saidSessionMiss) {
            saidSessionMiss = true;
            emit("SESSION_MISS",
                    "A request landed on a replica that has never heard of this client. The session "
                            + "object is in another JVM's heap, so this replica sees an unauthenticated "
                            + "user with an empty cart — and the user sees a random logout on a random "
                            + "click. Nothing crashed and nothing is in the error log; the service is "
                            + "just wrong for a fraction of requests equal to the fraction of replicas "
                            + "that do not hold the state. This is the real answer to \"why can't we just "
                            + "add servers?\"",
                    "Запрос попал на реплику, которая ничего не знает об этом клиенте. Объект сессии "
                            + "лежит в куче другой JVM, поэтому эта реплика видит неаутентифицированного "
                            + "пользователя с пустой корзиной, а пользователь видит случайный разлогин на "
                            + "случайном клике. Ничего не упало и в журнале ошибок пусто; сервис просто "
                            + "неправильно работает для доли запросов, равной доле реплик, где состояния "
                            + "нет. Это и есть настоящий ответ на вопрос «почему нельзя просто добавить "
                            + "серверов?»",
                    List.of("sessions", "nodes"));
        }
        if (strategy == Strategy.STICKY && !saidSticky && !pinned.isEmpty()) {
            saidSticky = true;
            emit("STICKY_ROUTED",
                    "Sticky sessions: the balancer pins each client to a replica (a cookie, a source-IP "
                            + "hash) so its state is always where it landed the first time. The misses "
                            + "stop, and it is a legitimate stopgap — but read what you agreed to. Load "
                            + "is now balanced by client rather than by request, so one heavy client can "
                            + "saturate one replica while the others idle; a replica cannot be drained "
                            + "for deployment without dropping its clients' state; and when it dies, "
                            + "everything pinned to it dies with it",
                    "Sticky-сессии: балансировщик прикрепляет каждого клиента к реплике (кука, хэш "
                            + "IP-адреса), чтобы состояние всегда было там, куда клиент попал в первый "
                            + "раз. Промахи прекращаются, и как временная мера это законно — но прочтите, "
                            + "на что вы согласились. Нагрузка теперь балансируется по клиентам, а не по "
                            + "запросам, поэтому один тяжёлый клиент может насытить одну реплику, пока "
                            + "остальные простаивают; реплику нельзя вывести из-под трафика для деплоя, "
                            + "не потеряв состояние её клиентов; а когда она умрёт, вместе с ней умрёт "
                            + "всё, что к ней прикреплено",
                    List.of("sessions", "balancer"));
        }
        if (current().rejected > 0 && !saidRejected) {
            saidRejected = true;
            emit("REQUESTS_REJECTED",
                    "The queue is full, so the server is refusing requests outright — 503, connection "
                            + "refused, whatever the stack calls it. This looks like the failure and it "
                            + "is actually the healthy behaviour: a bounded queue converts overload into "
                            + "a fast, honest \"no\" for the surplus while everything inside the bound "
                            + "still gets a normal answer. The alternative is not \"nobody is refused\" — "
                            + "it is an unbounded queue where every single client waits past its timeout "
                            + "and the server spends 100% of its capacity on answers nobody reads",
                    "Очередь заполнена, поэтому сервер отказывает сразу — 503, connection refused, как "
                            + "бы это ни называлось в вашем стеке. Это выглядит как сбой, а на самом деле "
                            + "это здоровое поведение: ограниченная очередь превращает перегрузку в "
                            + "быстрое честное «нет» для излишка, а всё, что помещается в границу, "
                            + "получает нормальный ответ. Альтернатива — не «никому не отказали», а "
                            + "неограниченная очередь, где каждый клиент ждёт дольше своего таймаута, а "
                            + "сервер тратит 100% мощности на ответы, которых никто не читает",
                    List.of("queue", "nodes"));
        }
    }

    /** Fires the per-tick observations, each at most once per phase. */
    private void announceConditions() {
        peakQueue = Math.max(peakQueue, totalQueued());

        if (!backlog && queueBuildingUp()) {
            backlog = true;
            emit("QUEUE_GROWING",
                    "The queue is at " + totalQueued() + " of " + queueCapacity() + ". This is the first "
                            + "symptom and the most misread one: the server is not slower than it was — "
                            + "every request still takes " + nodeServiceTicks + " tick(s) of actual work "
                            + "— it is simply receiving more per tick than " + round(capacityPerTick())
                            + ", and the difference accumulates. A queue does not add capacity, it only "
                            + "converts a shortage of capacity into latency, and it does so at a rate "
                            + "that gets worse the closer utilisation is to 100%",
                    "Очередь заполнена на " + totalQueued() + " из " + queueCapacity() + ". Это первый "
                            + "симптом и самый неверно читаемый: сервер не стал медленнее — каждый запрос "
                            + "по-прежнему требует " + nodeServiceTicks + " тик(ов) настоящей работы, — "
                            + "он просто получает в тик больше, чем " + round(capacityPerTick())
                            + ", и разница накапливается. Очередь не добавляет мощности, она лишь "
                            + "превращает нехватку мощности в задержку, причём тем быстрее, чем ближе "
                            + "загрузка к 100%",
                    List.of("queue", "nodes"));
        }

        if (!saidLatency && served >= 3 && avgLatency() > 2.0 * nodeServiceTicks) {
            saidLatency = true;
            emit("LATENCY_CLIMBING",
                    "Average latency is " + round(avgLatency()) + " tick(s) against " + nodeServiceTicks
                            + " tick(s) of real work, so most of what a client now experiences is waiting "
                            + "in line. Two consequences an interviewer will look for: profiling the "
                            + "application will find nothing, because no method got slower; and the curve "
                            + "is not linear — at 50% utilisation a little extra traffic costs a little "
                            + "latency, at 90% the same extra traffic costs several times more, which is "
                            + "why systems seem fine right up until they very suddenly are not",
                    "Средняя задержка " + round(avgLatency()) + " тик(ов) против " + nodeServiceTicks
                            + " тик(ов) настоящей работы, то есть почти всё, что чувствует клиент, — это "
                            + "стояние в очереди. Два следствия, которых ждёт интервьюер: профилирование "
                            + "приложения не найдёт ничего, потому что ни один метод не стал медленнее; и "
                            + "кривая нелинейна — при загрузке 50% немного лишнего трафика стоит немного "
                            + "задержки, при 90% тот же излишек стоит в разы больше. Поэтому системы "
                            + "выглядят нормально ровно до момента, когда очень внезапно перестают",
                    List.of("queue", "latency"));
        }

        if (!saidWasted && wasted > 0) {
            saidWasted = true;
            emit("WASTED_WORK",
                    wasted + " request(s) have been completed for clients that stopped waiting. The "
                            + "server did the database call, built the response and wrote it to a "
                            + "connection that is already closed — that capacity is gone, and it was "
                            + "spent on nothing. This is why a queue deeper than the client's timeout is "
                            + "strictly harmful: past that depth the server is guaranteed to work only on "
                            + "requests whose clients have already left, so it can be 100% busy and 0% "
                            + "useful. Bound the queue, and shed load instead of storing it",
                    wasted + " запрос(ов) доведены до конца для клиентов, которые перестали ждать. Сервер "
                            + "сходил в базу, собрал ответ и записал его в уже закрытое соединение — эта "
                            + "мощность потрачена, и потрачена впустую. Поэтому очередь глубже "
                            + "клиентского таймаута строго вредна: за этой глубиной сервер гарантированно "
                            + "работает только над запросами, чьи клиенты уже ушли, и может быть на 100% "
                            + "занят и на 0% полезен. Ограничивайте очередь и сбрасывайте нагрузку вместо "
                            + "того, чтобы её копить",
                    List.of("queue", "timeout"));
        }

        if (bottleneckBlocked && !saidBottleneck) {
            saidBottleneck = true;
            emit("BOTTLENECK_SATURATED",
                    "Every replica has free slots and none of them can use one: " + bottleneckName
                            + " is at its ceiling of " + bottleneckCapacity + " request(s) per tick, and "
                            + "all " + liveNodes() + " replica(s) are queueing on the same thing. The "
                            + "bottleneck did not disappear when you scaled out — it moved, and it moved "
                            + "to the component you did not replicate. Adding another replica now adds "
                            + "zero throughput and slightly more contention, which is why the first "
                            + "question about scaling is always \"what is the constraint?\" and never "
                            + "\"how many instances?\"",
                    "У каждой реплики есть свободные слоты, и ни одна не может ими воспользоваться: "
                            + bottleneckName + " упёрся в свой потолок " + bottleneckCapacity
                            + " запрос(ов) в тик, и все " + liveNodes() + " реплик(и) стоят в очереди к "
                            + "одному и тому же. Узкое место не исчезло, когда вы масштабировались вширь, "
                            + "— оно переехало, и переехало в тот компонент, который вы не реплицировали. "
                            + "Ещё одна реплика теперь добавит ноль пропускной способности и немного "
                            + "конкуренции; поэтому первый вопрос про масштабирование всегда «что "
                            + "является ограничением?» и никогда «сколько инстансов?»",
                    List.of("bottleneck", "nodes"));
        }

        if (!saidHotspot && hotspot()) {
            saidHotspot = true;
            emit("HOTSPOT",
                    "One replica has a queue while another has a free slot. The traffic is balanced — "
                            + "that is the problem. " + strategy + " hands out equal shares to unequal "
                            + "nodes, so the slow one accumulates everything it cannot finish while its "
                            + "neighbours idle, and the service's p99 becomes the property of its worst "
                            + "member. Routing by observed load instead of by turn (LEAST_BUSY, or "
                            + "least-outstanding-requests in a real balancer) fixes exactly this, because "
                            + "a node that is not finishing work stops looking cheap to send to",
                    "У одной реплики очередь, а у другой свободный слот. Трафик сбалансирован — в этом и "
                            + "проблема. " + strategy + " раздаёт равные доли неравным узлам, поэтому "
                            + "медленный копит всё, что не успевает доделать, пока соседи простаивают, и "
                            + "p99 всего сервиса становится свойством худшего его участника. "
                            + "Маршрутизация по наблюдаемой загрузке вместо очерёдности (LEAST_BUSY, или "
                            + "least-outstanding-requests в настоящем балансировщике) чинит ровно это: "
                            + "узел, который не доделывает работу, перестаёт выглядеть дешёвым для "
                            + "отправки",
                    List.of("balancer", "nodes"));
        }

        if (backlog && totalQueued() == 0) {
            backlog = false;
            emit("QUEUE_DRAINED",
                    "The backlog is gone and latency is back down to " + nodeServiceTicks + " tick(s) of "
                            + "work plus nothing. That is what \"enough capacity\" looks like from the "
                            + "outside: an empty queue, not a fast one. Note that the fix was arithmetic "
                            + "rather than optimisation — arrivals are now below "
                            + round(capacityPerTick()) + " request(s) per tick, so there is no surplus to "
                            + "accumulate, and every symptom above it disappeared at once",
                    "Накопившееся разобрано, и задержка вернулась к " + nodeServiceTicks + " тик(ов) "
                            + "работы плюс ничего. Вот как «достаточно мощности» выглядит снаружи: пустая "
                            + "очередь, а не быстрая. Заметьте, что починка была арифметикой, а не "
                            + "оптимизацией: приход теперь ниже " + round(capacityPerTick())
                            + " запрос(ов) в тик, поэтому излишку нечего накапливать, и все симптомы выше "
                            + "исчезли разом",
                    List.of("queue", "capacity"));
        }
    }

    /**
     * True when some replica has filled at least half of its own queue — a
     * backlog is local to a node, so a fleet-wide average would hide it.
     */
    private boolean queueBuildingUp() {
        for (Node node : nodes) {
            if (node.alive && node.queue.size() >= Math.max(1, node.queueLimit / 2)) {
                return true;
            }
        }
        return false;
    }

    /** True when one replica is queueing while another has an idle slot. */
    private boolean hotspot() {
        if (rotation().size() < 2) {
            return false;
        }
        boolean queueing = false;
        boolean idle = false;
        for (Node node : rotation()) {
            if (!node.alive) {
                continue;
            }
            if (!node.queue.isEmpty()) {
                queueing = true;
            }
            if (node.serving.size() < node.concurrency) {
                idle = true;
            }
        }
        return queueing && idle;
    }

    /** A topology change makes every earlier observation worth stating again. */
    private void resetAnnouncements() {
        saidLatency = false;
        saidRejected = false;
        saidTimeout = false;
        saidWasted = false;
        saidBottleneck = false;
        saidHotspot = false;
    }

    // ------------------------------------------------------------- internals

    /**
     * Emits a trace event for the current state, unless this run is only being
     * priced for the comparison table.
     */
    private void emit(String event, String descEn, String descRu, List<String> highlight) {
        if (quiet) {
            return;
        }
        Trace.event(event, descEn, descRu, highlight, state());
    }

    private Node node(String name) {
        for (Node node : nodes) {
            if (node.name.equals(name)) {
                return node;
            }
        }
        return null;
    }

    private Slot current() {
        return timeline.get(timeline.size() - 1);
    }

    /** Records queue depth and fleet size on the current tick. */
    private void stamp() {
        Slot slot = current();
        slot.queued = totalQueued();
        slot.nodes = liveNodes();
    }

    private int liveNodes() {
        int live = 0;
        for (Node node : nodes) {
            if (node.alive) {
                live++;
            }
        }
        return live;
    }

    private int totalQueued() {
        int queued = 0;
        for (Node node : nodes) {
            queued += node.queue.size();
        }
        return queued;
    }

    private int queueCapacity() {
        int capacity = 0;
        for (Node node : nodes) {
            if (node.alive) {
                capacity += node.queueLimit;
            }
        }
        return Math.max(1, capacity);
    }

    /** One healthy replica's throughput, in requests per tick. */
    private double nodeCapacity() {
        return nodeConcurrency / (double) nodeServiceTicks;
    }

    /** What the whole topology can actually start per tick, shared limit included. */
    private double capacityPerTick() {
        double total = 0;
        for (Node node : nodes) {
            if (node.alive) {
                total += node.capacity();
            }
        }
        if (bottleneckCapacity > 0) {
            total = Math.min(total, bottleneckCapacity);
        }
        return total;
    }

    private double avgLatency() {
        return served == 0 ? 0.0 : latencySum / (double) served;
    }

    private String topology() {
        return nodes.size() > 1 || strategy != Strategy.NONE ? "BALANCED" : "SINGLE";
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // ------------------------------------------------------------------ state

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("service", service);
        s.put("topology", topology());
        s.put("strategy", strategy.name());
        s.put("now", now);
        s.put("clientTimeout", clientTimeout);
        s.put("healthChecks", healthChecks ? healthCheckTicks : null);
        s.put("localSessions", localSessions);
        s.put("capacityPerTick", round(capacityPerTick()));

        if (bottleneckCapacity > 0) {
            Map<String, Object> shared = new LinkedHashMap<>();
            shared.put("name", bottleneckName);
            shared.put("capacity", bottleneckCapacity);
            shared.put("used", bottleneckUsed);
            s.put("bottleneck", shared);
        } else {
            s.put("bottleneck", null);
        }

        List<Object> replicas = new ArrayList<>();
        for (Node node : nodes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", node.name);
            row.put("alive", node.alive);
            row.put("inRotation", node.inRotation);
            row.put("degraded", node.degraded);
            row.put("concurrency", node.concurrency);
            row.put("serviceTicks", node.serviceTicks);
            row.put("serving", node.serving.size());
            row.put("queue", node.queue.size());
            row.put("queueLimit", node.queueLimit);
            row.put("capacity", round(node.capacity()));
            row.put("served", node.served);
            row.put("rejected", node.rejected);
            row.put("sessions", node.sessions.size());
            replicas.add(row);
        }
        s.put("nodes", replicas);

        List<Object> slots = new ArrayList<>();
        for (Slot slot : timeline) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", slot.tick);
            row.put("arrived", slot.arrived);
            row.put("served", slot.served);
            row.put("rejected", slot.rejected);
            row.put("failed", slot.failed);
            row.put("queued", slot.queued);
            row.put("nodes", slot.nodes);
            slots.add(row);
        }
        s.put("timeline", slots);

        s.put("stats", stats());
        s.put("comparison", new ArrayList<>());
        return s;
    }

    private Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("arrived", arrived);
        stats.put("served", served);
        stats.put("rejected", rejected);
        stats.put("failed", failed);
        stats.put("timedOut", timedOut);
        stats.put("wasted", wasted);
        stats.put("sessionMisses", sessionMisses);
        stats.put("blackholed", blackholed);
        stats.put("avgLatency", round(avgLatency()));
        stats.put("maxLatency", maxLatency);
        stats.put("peakQueue", peakQueue);
        return stats;
    }

    // ------------------------------------------------------------- comparison

    /**
     * Replays one traffic pattern through one topology without tracing, so the
     * topologies can be priced against exactly the same requests.
     */
    private static Map<String, Object> price(String label, int replicas, int concurrency,
                                             int serviceTicks, int queueLimit, int sharedCapacity,
                                             int perTick, int ticks) {
        VisualLoadBalancer run = new VisualLoadBalancer("compare", replicas, concurrency, serviceTicks,
                queueLimit, replicas > 1 ? Strategy.ROUND_ROBIN : Strategy.NONE);
        run.quiet = true;
        if (sharedCapacity > 0) {
            run.sharedDependency("shared database", sharedCapacity);
        }
        run.traffic(perTick, ticks);
        // Let whatever is still in flight finish, so nothing is counted twice.
        run.tick(ticks);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("topology", label);
        row.put("nodes", replicas);
        row.put("capacityPerTick", round(run.capacityPerTick()));
        row.put("arrived", run.arrived);
        row.put("served", run.served);
        row.put("rejected", run.rejected);
        row.put("avgLatency", round(run.avgLatency()));
        row.put("maxLatency", run.maxLatency);
        row.put("survivesLoss", replicas > 1);
        return row;
    }

    /** A well-formed state whose only interesting part is the comparison table. */
    private static Object comparisonState(List<Object> rows) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("service", "one endpoint, four topologies");
        s.put("topology", "BALANCED");
        s.put("strategy", Strategy.ROUND_ROBIN.name());
        s.put("now", 0);
        s.put("clientTimeout", 0);
        s.put("healthChecks", null);
        s.put("localSessions", false);
        s.put("capacityPerTick", 0.0);
        s.put("bottleneck", null);
        s.put("nodes", new ArrayList<>());
        s.put("timeline", new ArrayList<>());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("arrived", 0);
        stats.put("served", 0);
        stats.put("rejected", 0);
        stats.put("failed", 0);
        stats.put("timedOut", 0);
        stats.put("wasted", 0);
        stats.put("sessionMisses", 0);
        stats.put("blackholed", 0);
        stats.put("avgLatency", 0.0);
        stats.put("maxLatency", 0);
        stats.put("peakQueue", 0);
        s.put("stats", stats);

        s.put("comparison", rows);
        return s;
    }
}
