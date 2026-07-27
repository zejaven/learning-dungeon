package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of one HTTP endpoint, answered in two different ways.
 *
 * <p>A regular request is a closed loop: the client asks, the handler runs, the
 * response is written, the request is over. If there is nothing to report the
 * server still answers — with {@code 200 []} or {@code 204} — because a request
 * that is not answered is a request that is still costing both sides something.
 *
 * <p>Long polling changes exactly one thing about that loop: <em>when</em> the
 * response is written. Same method, same URL, same headers, same status codes,
 * same proxies — but the handler does not return a body while there is nothing
 * to say. It parks the request and answers the moment news exists, or answers
 * empty on purpose when its hold window runs out. Nothing about the protocol is
 * new; the waiting simply moved from the client's timer to the server.
 *
 * <p>That move is not free, and this model is built to make the bill visible:
 *
 * <ul>
 *   <li>{@link Style#IMMEDIATE} — the ordinary endpoint. Every request is
 *       answered inside the same tick, empty or not.</li>
 *   <li>{@link Style#HELD_BLOCKING} — long polling written the naive way: the
 *       parked request keeps the worker thread that picked it up, so an idle
 *       client costs a thread and the pool runs out.</li>
 *   <li>{@link Style#HELD_ASYNC} — long polling done properly (a
 *       {@code DeferredResult}, a {@code CompletableFuture}, Servlet async): the
 *       request stays open while the worker goes back to the pool.</li>
 * </ul>
 *
 * <p>Time is discrete — the model counts <em>ticks</em> — so every run is
 * deterministic and the two styles can be priced honestly. A client's
 * {@code since} cursor is modelled too, because the interesting bug in long
 * polling is not the hold, it is the gap between one response and the next
 * request: {@link #withoutCursor()} drops the cursor and shows what falls into
 * that gap.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualLongPoll {

    /** How this endpoint decides when to write the response. */
    public enum Style { IMMEDIATE, HELD_BLOCKING, HELD_ASYNC }

    /** Something that happened on the server, appended to its event log. */
    private static final class Entry {

        private final int seq;
        private final String label;
        private final int createdAt;
        private int deliveries;
        private boolean missed;

        private Entry(int seq, String label, int createdAt) {
            this.seq = seq;
            this.label = label;
            this.createdAt = createdAt;
        }
    }

    /** One browser tab, with the cursor it sends as {@code ?since=}. */
    private static final class Client {

        private final String name;
        private int cursor;
        private int received;
        private Integer lastLatency;

        private Client(String name) {
            this.name = name;
        }
    }

    /** A request the server has accepted and not answered yet. */
    private static final class Inflight {

        private final String client;
        private final int openedAt;
        private final int since;
        private boolean holdsWorker;

        private Inflight(String client, int openedAt, int since, boolean holdsWorker) {
            this.client = client;
            this.openedAt = openedAt;
            this.since = since;
            this.holdsWorker = holdsWorker;
        }
    }

    /** One unit of simulated time, rendered as a cell of the timeline strip. */
    private static final class Slot {

        private final int tick;
        private boolean request;
        private boolean empty;
        private boolean delivery;
        private boolean serverEvent;
        private boolean parked;
        private int busy;

        private Slot(int tick) {
            this.tick = tick;
        }
    }

    // ------------------------------------------------------------------ state

    private final String endpoint;
    private final Style style;
    /** How many worker threads the container has for this endpoint. */
    private final int workers;
    /** Held styles: how many ticks a request may stay parked before answering empty. */
    private final int holdTicks;

    private boolean useCursor = true;
    private int now;
    private int nextSeq;

    private final List<Entry> log = new ArrayList<>();
    private final Map<String, Client> clients = new LinkedHashMap<>();
    private final List<Inflight> inflight = new ArrayList<>();
    private final List<Slot> timeline = new ArrayList<>();

    private int requests;
    private int emptyResponses;
    private int deliveredCount;
    private int missed;
    private int refused;
    private int latencySum;
    private int maxLatency;
    private int peakParked;
    private int workerTicks;

    private VisualLongPoll(String endpoint, Style style, int workers, int holdTicks) {
        this.endpoint = endpoint;
        this.style = style;
        this.workers = Math.max(1, workers);
        this.holdTicks = Math.max(1, holdTicks);
        this.timeline.add(new Slot(0));
    }

    // -------------------------------------------------------------- factories

    /**
     * The ordinary endpoint every REST API is made of: the handler runs and the
     * response is written before the request leaves the worker.
     */
    public static VisualLongPoll immediate(String endpoint, int workers) {
        VisualLongPoll server = new VisualLongPoll(endpoint, Style.IMMEDIATE, workers, 1);
        server.announce("a regular request/response endpoint",
                "The handler is expected to produce a response NOW. It looks at whatever the server "
                        + "already knows, writes it out and the request is finished — so \"nothing new\" is "
                        + "an answer too, and it costs exactly as much as a real one. Latency here is not "
                        + "the server's fault: news that appears between two requests simply waits until the "
                        + "client next asks",
                "Обработчик обязан выдать ответ СЕЙЧАС. Он смотрит на то, что сервер уже знает, "
                        + "записывает это, и запрос завершён — поэтому «ничего нового» тоже ответ, и стоит он "
                        + "ровно столько же, сколько настоящий. Задержка тут не вина сервера: новость, "
                        + "появившаяся между двумя запросами, просто ждёт, пока клиент спросит в следующий раз");
        return server;
    }

    /**
     * Long polling written the obvious way: the handler blocks until it has
     * something, so the parked request keeps its worker thread.
     */
    public static VisualLongPoll held(String endpoint, int workers, int holdTicks) {
        VisualLongPoll server = new VisualLongPoll(endpoint, Style.HELD_BLOCKING, workers, holdTicks);
        server.announce("long polling, blocking handler, held up to " + server.holdTicks + " tick(s)",
                "The same URL and the same method, with one rule changed: while there is nothing to "
                        + "report the handler does not return. The request is parked and answered the "
                        + "instant news exists. Written this way the handler is still sitting on its worker "
                        + "thread the whole time — " + server.workers + " worker(s) means " + server.workers
                        + " waiting client(s), and the next request finds no thread to run on",
                "Тот же URL и тот же метод, изменено одно правило: пока сообщать нечего, обработчик не "
                        + "возвращает управление. Запрос припаркован и получает ответ в момент появления "
                        + "новости. Написанный так обработчик всё это время продолжает занимать рабочий "
                        + "поток: " + server.workers + " воркер(ов) — значит " + server.workers
                        + " ожидающих клиент(ов), и следующему запросу уже не на чем выполняться");
        return server;
    }

    /**
     * Long polling done properly: the handler registers the waiting request and
     * returns, so the worker thread goes back to the pool while the response
     * stays unfinished.
     */
    public static VisualLongPoll heldAsync(String endpoint, int workers, int holdTicks) {
        VisualLongPoll server = new VisualLongPoll(endpoint, Style.HELD_ASYNC, workers, holdTicks);
        server.announce("long polling, async handler, held up to " + server.holdTicks + " tick(s)",
                "Same held request, no held thread. The handler returns something that will be "
                        + "completed later — a DeferredResult, a CompletableFuture, a Servlet async context "
                        + "— and the worker goes straight back to the pool. What stays open is a socket and "
                        + "a small piece of registered state, which is why this shape survives thousands of "
                        + "waiting clients while the blocking one dies at " + server.workers,
                "Тот же придержанный запрос, но без придержанного потока. Обработчик возвращает то, что "
                        + "будет завершено позже, — DeferredResult, CompletableFuture, асинхронный контекст "
                        + "сервлета, — и воркер сразу возвращается в пул. Открытыми остаются сокет и "
                        + "небольшое зарегистрированное состояние; именно поэтому такая форма выдерживает "
                        + "тысячи ждущих клиентов, а блокирующая умирает на " + server.workers);
        return server;
    }

    /**
     * Drops the {@code ?since=} cursor: the client asks "anything new?" with no
     * idea of where it stopped, so the server can only hand it what happens
     * <em>after</em> the request is parked.
     */
    public VisualLongPoll withoutCursor() {
        useCursor = false;
        Trace.event("CURSOR_DROPPED",
                "This client polls " + endpoint + " with no cursor: no ?since=, no Last-Event-ID, "
                        + "nothing that says where it stopped reading. The server therefore cannot answer "
                        + "\"here is what you missed\" — it can only attach the parked request to future "
                        + "events. Everything that happens while no request is parked is invisible to this "
                        + "client forever",
                "Этот клиент опрашивает " + endpoint + " без курсора: ни ?since=, ни Last-Event-ID — "
                        + "ничего, что говорило бы, где он остановился. Поэтому сервер не может ответить "
                        + "«вот что вы пропустили»: он умеет только привязать припаркованный запрос к "
                        + "будущим событиям. Всё, что произошло, пока запрос не был припаркован, для этого "
                        + "клиента исчезло навсегда",
                List.of("cursor", "client"), state());
        return this;
    }

    // ----------------------------------------------------------------- clock

    /** Advances the clock by one tick. */
    public void tick() {
        tick(1);
    }

    /** Advances the clock by {@code ticks} ticks, expiring holds on the way. */
    public void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            now++;
            timeline.add(new Slot(now));
            for (Inflight parked : new ArrayList<>(inflight)) {
                if (now - parked.openedAt >= holdTicks) {
                    expire(parked);
                }
            }
            // A blocked handler burns its worker for every tick it waits.
            workerTicks += busyWorkers();
            stamp();
        }
    }

    // --------------------------------------------------------------- actions

    /**
     * A GET arrives at the endpoint. On the wire this is the same request in
     * both styles — only what the server does with it differs.
     */
    public void poll(String name) {
        Client client = client(name);
        requests++;
        current().request = true;
        Trace.event("REQUEST_ARRIVED",
                "Tick " + now + ": " + endpoint + (useCursor ? "?since=" + client.cursor : "")
                        + " from " + name + ". Nothing here is special to long polling: one TCP/TLS "
                        + "connection, one request line, the usual headers, cookies and auth. Whether this "
                        + "is a \"normal\" call or a long poll is decided entirely by what the handler does "
                        + "next — the client cannot tell from the request it just sent",
                "Тик " + now + ": " + endpoint + (useCursor ? "?since=" + client.cursor : "")
                        + " от " + name + ". Ничего специфичного для long polling здесь нет: одно "
                        + "TCP/TLS-соединение, одна строка запроса, обычные заголовки, куки и авторизация. "
                        + "«Обычный» это вызов или длинный опрос, решает исключительно то, что дальше "
                        + "сделает обработчик, — по самому запросу клиент этого не различает",
                List.of("client", "request"), state());

        if (holdsThread() && busyWorkers() >= workers) {
            refused++;
            Trace.event("POOL_EXHAUSTED",
                    "All " + workers + " worker(s) are sitting inside a parked handler, so there is no "
                            + "thread left to even start this one. The connection was accepted and then "
                            + "nothing happens: the request waits in the container's queue until somebody "
                            + "else's hold expires. Note what this hits — not just the long-poll endpoint, "
                            + "but every other endpoint sharing the pool, which is how a notification "
                            + "feature takes down a checkout page",
                    "Все " + workers + " воркер(ов) сидят внутри припаркованного обработчика, поэтому "
                            + "запустить этот запрос попросту не на чем. Соединение принято, и дальше не "
                            + "происходит ничего: запрос ждёт в очереди контейнера, пока у кого-то не "
                            + "истечёт удержание. Обратите внимание, по кому это бьёт: не только по "
                            + "long-poll эндпоинту, а по всем остальным, делящим тот же пул, — именно так "
                            + "фича уведомлений роняет страницу оплаты",
                    List.of("pool", "request"), state());
            stamp();
            return;
        }

        // A worker picked the request up and ran the handler.
        workerTicks++;

        List<Entry> pending;
        if (useCursor) {
            pending = pendingFor(client);
        } else {
            // No cursor: the client can only be told about what happens next.
            client.cursor = nextSeq;
            pending = List.of();
        }

        if (style == Style.IMMEDIATE) {
            if (pending.isEmpty()) {
                emptyResponses++;
                current().empty = true;
                Trace.event("ANSWERED_EMPTY",
                        "The handler had nothing, so it answered anyway: 200 [] (or 204). That is the "
                                + "defining property of a regular request — it is over when the handler "
                                + "returns, whether or not the answer is useful. Request number " + requests
                                + " for this endpoint, " + emptyResponses + " of them empty, each one a full "
                                + "round trip plus a handler plus a database look-up to learn nothing",
                        "У обработчика ничего не было, и он всё равно ответил: 200 [] (или 204). Это и есть "
                                + "определяющее свойство обычного запроса — он завершается, когда обработчик "
                                + "вернул управление, полезен ответ или нет. Запрос номер " + requests
                                + " к этому эндпоинту, из них пустых: " + emptyResponses + ", и каждый — "
                                + "полный обмен плюс обработчик плюс обращение к базе ради того, чтобы не "
                                + "узнать ничего",
                        List.of("client", "cost"), state());
            } else {
                deliver(client, pending, "ANSWERED_NOW",
                        "The handler found something waiting and returned it at once. Look at the delay "
                                + "though: the news did not arrive when it happened, it arrived when the "
                                + "client next asked. With a regular endpoint that gap is the client's "
                                + "polling interval and there is nothing the server can do about it",
                        "Обработчик нашёл накопившееся и сразу это вернул. Но посмотрите на задержку: "
                                + "новость пришла не когда произошла, а когда клиент в следующий раз "
                                + "спросил. У обычного эндпоинта этот разрыв равен интервалу опроса, и "
                                + "сервер с этим ничего сделать не может");
            }
            stamp();
            return;
        }

        if (!pending.isEmpty()) {
            deliver(client, pending, "POLL_CATCHES_UP",
                    "The request is not parked at all: since=" + client.cursor + " told the server this "
                            + "client is behind, and the server had the events in its log, so it answered "
                            + "immediately. This is the half of long polling people forget — the cursor "
                            + "makes the endpoint correct for a client that was away, and the hold is only "
                            + "an optimisation for a client that is caught up",
                    "Запрос вообще не паркуется: since=" + client.cursor + " сообщил серверу, что клиент "
                            + "отстал, а события лежали в журнале, поэтому сервер ответил сразу. Это та "
                            + "половина long polling, о которой забывают: курсор делает эндпоинт "
                            + "корректным для клиента, который отсутствовал, а удержание — лишь "
                            + "оптимизация для клиента, который уже всё видел");
            stamp();
            return;
        }

        Inflight parked = new Inflight(name, now, client.cursor, holdsThread());
        inflight.add(parked);
        peakParked = Math.max(peakParked, inflight.size());
        Trace.event("REQUEST_PARKED",
                "The handler has nothing to say, and this time it does NOT answer. No status line, no "
                        + "body, no bytes at all — the response is simply unfinished, and the client is "
                        + "left waiting on a read that has not completed. Nothing is being polled and "
                        + "nothing is being pushed: one ordinary request is being held open on purpose, up "
                        + "to " + holdTicks + " tick(s)",
                "Обработчику нечего сказать, и на этот раз он НЕ отвечает. Ни строки статуса, ни тела, "
                        + "ни единого байта — ответ просто не завершён, а клиент ждёт незакончившегося "
                        + "чтения. Никто ничего не опрашивает и никто ничего не пушит: один обычный запрос "
                        + "намеренно держат открытым, до " + holdTicks + " тик(ов)",
                List.of("client", "parked", "pool"), state());

        if (style == Style.HELD_ASYNC) {
            parked.holdsWorker = false;
            Trace.event("WORKER_RELEASED",
                    "And the worker is already free. The handler returned a DeferredResult instead of a "
                            + "body: the framework remembers the unfinished response, the thread goes back "
                            + "to the pool, and " + workers + " worker(s) can now hold far more than "
                            + workers + " waiting client(s). The request is still open — what is no longer "
                            + "occupied is the thread",
                    "И воркер уже свободен. Обработчик вернул DeferredResult вместо тела: фреймворк "
                            + "запомнил незавершённый ответ, поток вернулся в пул, и " + workers
                            + " воркер(ов) теперь могут держать гораздо больше, чем " + workers
                            + " ждущих клиент(ов). Запрос по-прежнему открыт — не занят больше именно поток",
                    List.of("pool", "parked"), state());
        }
        stamp();
    }

    /** Something happens on the server: a message, a payment, a finished job. */
    public void serverEvent(String label) {
        Entry entry = new Entry(++nextSeq, label, now);
        log.add(entry);
        current().serverEvent = true;
        Trace.event("SERVER_EVENT",
                "Tick " + now + ": \"" + label + "\" happened and was appended to the server's log as "
                        + "#" + entry.seq + ". The server cannot dial a browser tab, so this event reaching "
                        + "a screen depends on exactly one thing: whether there is an unanswered request it "
                        + "can be written into right now",
                "Тик " + now + ": произошло «" + label + "» и добавлено в журнал сервера под номером #"
                        + entry.seq + ". Сервер не может «позвонить» во вкладку браузера, поэтому дойдёт ли "
                        + "событие до экрана, зависит ровно от одного: есть ли прямо сейчас неотвеченный "
                        + "запрос, в который его можно записать",
                List.of("server", "log"), state());
        flush(entry);
        stamp();
    }

    /** Prints what this endpoint actually cost over the whole run. */
    public void report() {
        Trace.event("ENDPOINT_AUDIT",
                "After " + now + " tick(s) on " + style + ": requests " + requests + " (of which "
                        + emptyResponses + " came back with nothing), events delivered "
                        + deliveredCount + ", never delivered " + missed + ", average delay "
                        + avgLatency() + " tick(s), worst delay " + maxLatency + " tick(s), peak parked "
                        + "requests " + peakParked + ", worker-ticks spent " + workerTicks
                        + ", requests refused by a full pool " + refused,
                "После тиков (" + now + ") на " + style + ": запросов " + requests + " (из них "
                        + "пустых: " + emptyResponses + "), доставлено событий: " + deliveredCount
                        + ", не доставлено: " + missed + ", средняя задержка: " + avgLatency()
                        + " тик(ов), худшая задержка: " + maxLatency + " тик(ов), пик припаркованных "
                        + "запросов: " + peakParked + ", потрачено воркер-тиков: " + workerTicks
                        + ", запросов отвергнуто из-за занятого пула: " + refused,
                List.of("cost"), state());
    }

    /**
     * Runs one fixed timeline through all three styles and prices it — the
     * comparison an interviewer is actually asking for.
     */
    public static void compare() {
        int ticks = 24;
        int[] events = {3, 4, 17};
        List<Object> rows = new ArrayList<>();
        rows.add(row("IMMEDIATE", false, false, 6, 10, ticks, events));
        rows.add(row("HELD_BLOCKING", true, false, 6, 10, ticks, events));
        rows.add(row("HELD_ASYNC", true, true, 6, 10, ticks, events));

        Trace.event("STYLES_COMPARED",
                "The same 24 ticks and the same 3 events, answered three ways. The regular endpoint "
                        + "polled every 6 ticks pays 4 requests, 2 of which return nothing, and is late by "
                        + "up to a whole interval. Both long polls deliver with zero delay for 5 requests "
                        + "— and they differ only in the last column: the blocking one burns a worker for "
                        + "every tick it waits, the async one burns a worker only while a handler actually "
                        + "runs. Long polling buys latency with server-side occupancy; writing it async is "
                        + "what makes that price affordable",
                "Одни и те же 24 тика и одни и те же 3 события, отвеченные тремя способами. Обычный "
                        + "эндпоинт при опросе раз в 6 тиков платит 4 запроса, 2 из которых не возвращают "
                        + "ничего, и опаздывает вплоть до целого интервала. Оба длинных опроса доставляют "
                        + "с нулевой задержкой за 5 запросов — и различаются только последней колонкой: "
                        + "блокирующий сжигает воркер на каждом тике ожидания, асинхронный — только пока "
                        + "реально выполняется обработчик. Long polling покупает задержку ценой занятости "
                        + "сервера; асинхронная реализация — то, что делает эту цену приемлемой",
                List.of("cost"), comparisonState(ticks, rows));
    }

    // -------------------------------------------------------------- delivery

    /** Hands every parked request whatever the new event makes available. */
    private void flush(Entry entry) {
        boolean answered = false;
        for (Inflight parked : new ArrayList<>(inflight)) {
            Client client = clients.get(parked.client);
            List<Entry> pending = pendingFor(client);
            if (pending.isEmpty()) {
                continue;
            }
            inflight.remove(parked);
            answered = true;
            deliver(client, pending, "PARKED_COMPLETED",
                    "The parked request is completed right now: 200 with the event(s), "
                            + (now - parked.openedAt) + " tick(s) after it was opened and 0 tick(s) after "
                            + "the news existed. This is the whole point — an ordinary HTTP response, "
                            + "written at the moment the server had something to put in it. And note that "
                            + "the response ENDS the request: from here the client has to open another one",
                    "Припаркованный запрос завершается прямо сейчас: 200 с событием(ями), через "
                            + (now - parked.openedAt) + " тик(ов) после открытия и через 0 тик(ов) после "
                            + "появления новости. В этом весь смысл: обычный HTTP-ответ, записанный в тот "
                            + "момент, когда серверу было что в него положить. И заметьте, что ответ "
                            + "ЗАВЕРШАЕТ запрос: дальше клиенту нужно открывать следующий");
        }
        if (answered) {
            return;
        }
        if (useCursor) {
            Trace.event("NO_ONE_LISTENING",
                    "There is no unanswered request to write #" + entry.seq + " into, so it just stays in "
                            + "the log. Nothing is lost: the client's cursor is behind now, and its next "
                            + "request — with since=" + lastCursor() + " — will be answered immediately "
                            + "instead of being parked. The log plus the cursor is what makes the gap "
                            + "between two long polls survivable",
                    "Неотвеченного запроса, в который можно записать #" + entry.seq + ", нет, поэтому "
                            + "событие просто остаётся в журнале. Ничего не потеряно: курсор клиента теперь "
                            + "отстал, и его следующий запрос — с since=" + lastCursor() + " — получит "
                            + "ответ сразу, а не будет припаркован. Журнал плюс курсор — это то, что "
                            + "позволяет пережить промежуток между двумя длинными опросами",
                    List.of("log", "server"), state());
            return;
        }
        entry.missed = true;
        missed++;
        Trace.event("EVENT_MISSED",
                "\"" + entry.label + "\" is gone. No request was parked to write it into, and this "
                        + "client polls without a cursor, so its next request cannot ask for it either — it "
                        + "will only be attached to events that happen after it is parked. A long poll that "
                        + "subscribes on arrival and remembers nothing loses everything that lands between "
                        + "one response and the next request",
                "«" + entry.label + "» потеряно. Припаркованного запроса, в который это можно было "
                        + "записать, не было, а этот клиент опрашивает без курсора, поэтому и его следующий "
                        + "запрос об этом не попросит — его привяжут только к событиям, случившимся после "
                        + "парковки. Длинный опрос, который подписывается при поступлении и ничего не "
                        + "запоминает, теряет всё, что попадает между ответом и следующим запросом",
                List.of("log", "cursor"), state());
    }

    /** Writes the response and records what the delay cost. */
    private void deliver(Client client, List<Entry> pending, String event, String descEn, String descRu) {
        int worst = 0;
        StringBuilder labels = new StringBuilder();
        for (Entry entry : pending) {
            entry.deliveries++;
            client.cursor = Math.max(client.cursor, entry.seq);
            client.received++;
            deliveredCount++;
            int latency = now - entry.createdAt;
            latencySum += latency;
            worst = Math.max(worst, latency);
            maxLatency = Math.max(maxLatency, latency);
            if (labels.length() > 0) {
                labels.append(", ");
            }
            labels.append(entry.label);
        }
        client.lastLatency = worst;
        current().delivery = true;
        Trace.event(event,
                descEn + ". Delivered to " + client.name + ": " + labels + " (" + pending.size()
                        + " event(s), waited " + worst + " tick(s)); its cursor is now " + client.cursor,
                descRu + ". Доставлено клиенту " + client.name + ": " + labels + " (событий: "
                        + pending.size() + ", ожидание: " + worst + " тик(ов)); его курсор теперь "
                        + client.cursor,
                List.of("client", "delivered"), state());
    }

    /** The hold window ran out with nothing to say, so the server answers empty. */
    private void expire(Inflight parked) {
        inflight.remove(parked);
        emptyResponses++;
        current().empty = true;
        Trace.event("HOLD_EXPIRED",
                "The request has been parked for " + holdTicks + " tick(s) with no news, so the server "
                        + "ends it on purpose: 200 [] or 204. It has to — a browser, a reverse proxy, a "
                        + "load balancer and a NAT box all kill connections that stay silent too long, so "
                        + "the hold must expire before the shortest of THEIR timeouts. Answering first is "
                        + "the difference between a clean empty response and a client that sees a network "
                        + "error",
                "Запрос простоял припаркованным " + holdTicks + " тик(ов) без новостей, поэтому сервер "
                        + "намеренно его завершает: 200 [] или 204. Иначе нельзя — браузер, обратный "
                        + "прокси, балансировщик и NAT рвут соединения, которые слишком долго молчат, "
                        + "поэтому удержание должно истечь раньше самого короткого ИЗ ИХ таймаутов. "
                        + "Ответить первым — это разница между аккуратным пустым ответом и сетевой ошибкой "
                        + "у клиента",
                List.of("client", "cost", "parked"), state());
    }

    // ------------------------------------------------------------- internals

    private void announce(String name, String descEn, String descRu) {
        Trace.event("ENDPOINT_OPENED",
                endpoint + " is served as " + name + ", on a pool of " + workers + " worker(s). " + descEn,
                endpoint + " обслуживается как " + name + ", на пуле из " + workers + " воркер(ов). "
                        + descRu,
                List.of("server", "pool"), state());
    }

    private Client client(String name) {
        return clients.computeIfAbsent(name, Client::new);
    }

    /** Everything in the log this client has not seen yet. */
    private List<Entry> pendingFor(Client client) {
        List<Entry> pending = new ArrayList<>();
        for (Entry entry : log) {
            if (entry.seq > client.cursor && !entry.missed) {
                pending.add(entry);
            }
        }
        return pending;
    }

    private boolean holdsThread() {
        return style == Style.HELD_BLOCKING;
    }

    private int busyWorkers() {
        int busy = 0;
        for (Inflight parked : inflight) {
            if (parked.holdsWorker) {
                busy++;
            }
        }
        return busy;
    }

    private Slot current() {
        return timeline.get(timeline.size() - 1);
    }

    /** Records the occupancy of the current tick after something changed. */
    private void stamp() {
        Slot slot = current();
        slot.parked = !inflight.isEmpty();
        slot.busy = Math.max(slot.busy, busyWorkers());
    }

    /** The cursor of the client that is furthest behind, for the log message. */
    private int lastCursor() {
        int cursor = 0;
        for (Client client : clients.values()) {
            cursor = Math.max(cursor, client.cursor);
        }
        return cursor;
    }

    private double avgLatency() {
        if (deliveredCount == 0) {
            return 0.0;
        }
        return Math.round(latencySum * 10.0 / deliveredCount) / 10.0;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("endpoint", endpoint);
        s.put("style", style.name());
        s.put("now", now);
        s.put("holdTicks", style == Style.IMMEDIATE ? null : holdTicks);
        s.put("cursor", useCursor);

        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("size", workers);
        pool.put("busy", busyWorkers());
        pool.put("parked", inflight.size());
        s.put("pool", pool);

        List<Object> open = new ArrayList<>();
        for (Inflight parked : inflight) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("client", parked.client);
            row.put("openedAt", parked.openedAt);
            row.put("waited", now - parked.openedAt);
            row.put("since", parked.since);
            row.put("holdsWorker", parked.holdsWorker);
            open.add(row);
        }
        s.put("inflight", open);

        List<Object> entries = new ArrayList<>();
        for (Entry entry : log) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", entry.seq);
            row.put("label", entry.label);
            row.put("createdAt", entry.createdAt);
            row.put("deliveries", entry.deliveries);
            row.put("missed", entry.missed);
            entries.add(row);
        }
        s.put("log", entries);

        List<Object> tabs = new ArrayList<>();
        for (Client client : clients.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", client.name);
            row.put("cursor", client.cursor);
            row.put("received", client.received);
            row.put("lastLatency", client.lastLatency);
            tabs.add(row);
        }
        s.put("clients", tabs);

        List<Object> slots = new ArrayList<>();
        for (Slot slot : timeline) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", slot.tick);
            row.put("request", slot.request);
            row.put("empty", slot.empty);
            row.put("delivery", slot.delivery);
            row.put("serverEvent", slot.serverEvent);
            row.put("parked", slot.parked);
            row.put("busy", slot.busy);
            slots.add(row);
        }
        s.put("timeline", slots);

        s.put("stats", stats(requests, emptyResponses, deliveredCount, missed, refused, avgLatency(),
                maxLatency, peakParked, workerTicks));
        s.put("comparison", new ArrayList<>());
        return s;
    }

    private static Map<String, Object> stats(int requests, int empty, int delivered, int missed,
                                             int refused, double avgLatency, int maxLatency,
                                             int peakParked, int workerTicks) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("requests", requests);
        stats.put("empty", empty);
        stats.put("delivered", delivered);
        stats.put("missed", missed);
        stats.put("refused", refused);
        stats.put("avgLatency", avgLatency);
        stats.put("maxLatency", maxLatency);
        stats.put("peakParked", peakParked);
        stats.put("workerTicks", workerTicks);
        return stats;
    }

    // ------------------------------------------------------------ comparison

    /**
     * Replays one fixed timeline through one style without emitting anything, so
     * the three can be priced side by side. A regular endpoint is polled every
     * {@code interval} ticks; a held one re-opens as soon as it is answered.
     */
    private static Map<String, Object> row(String style, boolean held, boolean async, int interval,
                                           int hold, int ticks, int[] eventTicks) {
        int requests = 0;
        int empty = 0;
        int delivered = 0;
        int latencySum = 0;
        int maxLatency = 0;
        int workerTicks = 0;
        List<Integer> waiting = new ArrayList<>();
        int openedAt = -1;

        if (held) {
            // The client opens its first long poll before the clock starts.
            requests++;
            workerTicks++;
            openedAt = 0;
        }

        int next = 0;
        for (int tick = 1; tick <= ticks; tick++) {
            while (next < eventTicks.length && eventTicks[next] == tick) {
                waiting.add(tick);
                next++;
            }
            if (held) {
                if (!waiting.isEmpty()) {
                    for (int createdAt : waiting) {
                        delivered++;
                        latencySum += tick - createdAt;
                        maxLatency = Math.max(maxLatency, tick - createdAt);
                    }
                    waiting.clear();
                    // The response ended the request, so the client re-opens.
                    requests++;
                    workerTicks++;
                    openedAt = tick;
                } else if (tick - openedAt >= hold) {
                    empty++;
                    requests++;
                    workerTicks++;
                    openedAt = tick;
                }
                if (!async) {
                    // The blocking handler is still on its worker for this tick.
                    workerTicks++;
                }
            } else if (tick % interval == 0) {
                requests++;
                workerTicks++;
                if (waiting.isEmpty()) {
                    empty++;
                } else {
                    for (int createdAt : waiting) {
                        delivered++;
                        latencySum += tick - createdAt;
                        maxLatency = Math.max(maxLatency, tick - createdAt);
                    }
                    waiting.clear();
                }
            }
        }

        double avg = delivered == 0 ? 0.0 : Math.round(latencySum * 10.0 / delivered) / 10.0;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("style", style);
        row.put("requests", requests);
        row.put("empty", empty);
        row.put("delivered", delivered);
        row.put("avgLatency", avg);
        row.put("maxLatency", maxLatency);
        row.put("workerTicks", workerTicks);
        row.put("parkedPeak", held ? 1 : 0);
        return row;
    }

    /** A well-formed state whose only interesting part is the comparison table. */
    private static Map<String, Object> comparisonState(int ticks, List<Object> rows) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("endpoint", "GET /api/messages");
        s.put("style", null);
        s.put("now", ticks);
        s.put("holdTicks", null);
        s.put("cursor", true);

        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("size", 1);
        pool.put("busy", 0);
        pool.put("parked", 0);
        s.put("pool", pool);

        s.put("inflight", new ArrayList<>());
        s.put("log", new ArrayList<>());
        s.put("clients", new ArrayList<>());
        s.put("timeline", new ArrayList<>());
        s.put("stats", stats(0, 0, 0, 0, 0, 0.0, 0, 0, 0));
        s.put("comparison", rows);
        return s;
    }
}
