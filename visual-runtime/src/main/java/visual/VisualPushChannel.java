package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the one thing plain HTTP does not give you: a
 * server that is allowed to speak first.
 *
 * <p>A browser tab is not a server. It has no address anyone can call, and an
 * HTTP connection is opened by the client and closed once the response is
 * finished. So when something happens on the backend — a payment is confirmed,
 * a message arrives, a report finishes — there is no wire to send it down. Every
 * "real-time" technology is a different answer to that one problem:
 *
 * <ul>
 *   <li>{@link Transport#SHORT_POLL} — ask again on a timer. Nothing is held
 *       open; the cost is a round trip per "nothing yet" and a delay of up to
 *       one interval.</li>
 *   <li>{@link Transport#LONG_POLL} — ask, and let the server hold the request
 *       until it has news or the hold expires. Delivery is immediate; the cost
 *       is a parked request per client and a re-open after every message.</li>
 *   <li>{@link Transport#SSE} — one GET whose response never ends. The server
 *       writes messages into it as they happen; the browser reconnects on its
 *       own and replays with {@code Last-Event-ID}. One direction only.</li>
 *   <li>{@link Transport#WEBSOCKET} — one HTTP handshake upgraded to a raw
 *       two-way tunnel. Either side may send a frame; nothing is replayed for
 *       you after a reconnect.</li>
 * </ul>
 *
 * <p>Time is discrete: the model counts <em>ticks</em>, so a run is completely
 * deterministic and the price of each transport can be compared honestly.
 * {@link #serverEvent(String)} makes news appear on the server, {@link #tick()}
 * advances the clock, and the transport decides what happens next.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualPushChannel {

    /** How the browser and the server are wired together for this run. */
    public enum Transport { SHORT_POLL, LONG_POLL, SSE, WEBSOCKET }

    /** News that exists on the server and has not reached the browser yet. */
    private static final class Pending {

        private final String id;
        private final String label;
        private final int createdAt;

        private Pending(String id, String label, int createdAt) {
            this.id = id;
            this.label = label;
            this.createdAt = createdAt;
        }
    }

    /** News that made it to the screen, with the delay it took. */
    private static final class Delivery {

        private final String id;
        private final String label;
        private final int createdAt;
        private final int deliveredAt;

        private Delivery(Pending pending, int deliveredAt) {
            this.id = pending.id;
            this.label = pending.label;
            this.createdAt = pending.createdAt;
            this.deliveredAt = deliveredAt;
        }

        private int latency() {
            return deliveredAt - createdAt;
        }
    }

    /** One unit of simulated time, rendered as a cell of the timeline strip. */
    private static final class Slot {

        private final int tick;
        private final boolean held;
        private boolean serverEvent;
        private boolean request;
        private boolean empty;
        private boolean delivery;

        private Slot(int tick, boolean held) {
            this.tick = tick;
            this.held = held;
        }
    }

    // ------------------------------------------------------------------ state

    private final String app;
    private final Transport transport;
    /** Short polling: how many ticks between two requests. */
    private final int interval;
    /** Long polling: how many ticks the server may hold a request before answering empty. */
    private final int hold;

    private int now;
    private boolean online = true;
    private boolean sharedBus;
    private int instances = 1;

    private final List<Pending> queue = new ArrayList<>();
    private final List<Delivery> delivered = new ArrayList<>();
    private final List<Slot> timeline = new ArrayList<>();

    /** Long polling: the tick the currently parked request was opened at, or -1. */
    private int heldSince = -1;
    private String lastEventId;
    private int nextEventNo;

    private int requests;
    private int emptyResponses;
    private int frames;
    private int latencySum;
    private int maxLatency;
    private int lost;

    private VisualPushChannel(String app, Transport transport, int interval, int hold) {
        this.app = app;
        this.transport = transport;
        this.interval = Math.max(1, interval);
        this.hold = Math.max(1, hold);
        this.timeline.add(new Slot(0, false));
    }

    // -------------------------------------------------------------- factories

    /**
     * The browser asks "anything new?" on a timer. Nothing is held open between
     * requests, so the server still cannot start a conversation.
     */
    public static VisualPushChannel shortPolling(String app, int intervalTicks) {
        VisualPushChannel channel = new VisualPushChannel(app, Transport.SHORT_POLL, intervalTicks, 1);
        channel.announce("short polling every " + channel.interval + " tick(s)",
                "The client asks on a TIMER and the server answers on the spot. Nothing is open "
                        + "between requests, so news created just after an answer has to wait for the next "
                        + "one: the worst-case delay is a whole interval, and every request that finds "
                        + "nothing is still a full round trip",
                "Клиент спрашивает ПО ТАЙМЕРУ, а сервер отвечает сразу. Между запросами ничего не "
                        + "открыто, поэтому новость, появившаяся сразу после ответа, ждёт следующего: "
                        + "худшая задержка — целый интервал, а каждый запрос, не нашедший ничего, — это "
                        + "всё равно полный обмен");
        return channel;
    }

    /**
     * The browser asks once and the server keeps the request parked until it has
     * something to say (or the hold expires). Same HTTP, different waiting side.
     */
    public static VisualPushChannel longPolling(String app, int holdTicks) {
        VisualPushChannel channel = new VisualPushChannel(app, Transport.LONG_POLL, 1, holdTicks);
        channel.announce("long polling, held up to " + channel.hold + " tick(s)",
                "The client still asks first, but the server does NOT answer straight away — it parks "
                        + "the request and replies the moment news appears. The waiting moved from the "
                        + "client's timer to the server's thread, which buys near-zero delay at the price "
                        + "of one parked request per connected client",
                "Клиент по-прежнему спрашивает первым, но сервер НЕ отвечает сразу — он придерживает "
                        + "запрос и отвечает в тот момент, когда появляется новость. Ожидание переехало с "
                        + "таймера клиента на поток сервера: это даёт почти нулевую задержку ценой одного "
                        + "припаркованного запроса на каждого подключённого клиента");
        channel.openLongPoll("LONGPOLL_HELD",
                "The browser sent GET /api/events and the server did not answer. The request is parked: "
                        + "one client, one open request, no traffic at all while nothing happens",
                "Браузер отправил GET /api/events, и сервер не ответил. Запрос припаркован: один клиент, "
                        + "один открытый запрос и вообще никакого трафика, пока ничего не происходит");
        return channel;
    }

    /**
     * One GET whose response simply never ends: the server writes
     * {@code data:} lines into it as events happen.
     */
    public static VisualPushChannel sse(String app) {
        VisualPushChannel channel = new VisualPushChannel(app, Transport.SSE, 1, 1);
        channel.announce("Server-Sent Events (SSE)",
                "One ordinary GET, answered with Content-Type: text/event-stream and a response body "
                        + "that is never finished. The server writes an event into it whenever it has one. "
                        + "It is plain HTTP — proxies, gzip, auth headers and HTTP/2 all still work — but "
                        + "it only carries data in ONE direction",
                "Один обычный GET, на который отвечают Content-Type: text/event-stream и телом ответа, "
                        + "которое никогда не завершается. Сервер пишет в него событие всякий раз, как оно "
                        + "появляется. Это обычный HTTP — прокси, gzip, заголовки авторизации и HTTP/2 "
                        + "продолжают работать, — но данные он несёт только в ОДНУ сторону");
        channel.requests++;
        channel.mark(true, false, false);
        Trace.event("SSE_STREAM_OPENED",
                "GET /api/events -> 200, Content-Type: text/event-stream, Connection: keep-alive. The "
                        + "response has started and will not end; from here the browser just reads lines "
                        + "out of it. EventSource also reconnects by itself and remembers the id of the "
                        + "last event it saw, which is the part you would have had to write yourself",
                "GET /api/events -> 200, Content-Type: text/event-stream, Connection: keep-alive. Ответ "
                        + "начался и не закончится; дальше браузер просто читает из него строки. EventSource "
                        + "ещё и переподключается сам и помнит id последнего увиденного события — ровно ту "
                        + "часть, которую иначе пришлось бы писать самому",
                List.of("connection", "client"), channel.state());
        return channel;
    }

    /**
     * One HTTP handshake upgraded to a raw two-way tunnel: after 101 the
     * protocol is no longer request/response at all.
     */
    public static VisualPushChannel webSocket(String app) {
        VisualPushChannel channel = new VisualPushChannel(app, Transport.WEBSOCKET, 1, 1);
        channel.announce("WebSocket",
                "The only truly two-way option: after one handshake the same TCP connection carries "
                        + "frames in both directions, with no request and no response. That is what you "
                        + "want for chat, collaborative editing or a game — and it is more than you need "
                        + "when the data only ever flows from the server to the screen",
                "Единственный по-настоящему двусторонний вариант: после одного рукопожатия то же "
                        + "TCP-соединение несёт кадры в обе стороны, без запросов и ответов. Это то, что "
                        + "нужно чату, совместному редактированию или игре, — и это больше, чем нужно, "
                        + "когда данные всё равно идут только от сервера на экран");
        channel.requests++;
        channel.mark(true, false, false);
        Trace.event("WS_HANDSHAKE",
                "GET /ws/events with Upgrade: websocket -> 101 Switching Protocols. This is the last "
                        + "HTTP message of the conversation: the socket is now a bare frame pipe, so "
                        + "cookies, status codes, caching and REST semantics stop applying and anything "
                        + "you need on top of it — message types, acknowledgements, retries — you design "
                        + "yourself",
                "GET /ws/events с Upgrade: websocket -> 101 Switching Protocols. Это последнее "
                        + "HTTP-сообщение в разговоре: дальше сокет — просто труба для кадров, поэтому "
                        + "куки, коды состояния, кэширование и семантика REST перестают действовать, а всё, "
                        + "что нужно поверх — типы сообщений, подтверждения, повторы, — проектируется "
                        + "самостоятельно",
                List.of("connection", "client"), channel.state());
        return channel;
    }

    // ----------------------------------------------------------------- clock

    /** Advances the clock by one tick and lets the transport react. */
    public void tick() {
        tick(1);
    }

    /** Advances the clock by {@code ticks} ticks. */
    public void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            now++;
            timeline.add(new Slot(now, connectionOpen()));
            if (!online) {
                continue;
            }
            if (transport == Transport.SHORT_POLL && now % interval == 0) {
                poll();
            } else if (transport == Transport.LONG_POLL && heldSince >= 0 && now - heldSince >= hold) {
                longPollTimeout();
            }
        }
    }

    // --------------------------------------------------------------- actions

    /** Something happens on the server: a payment, a message, a finished job. */
    public void serverEvent(String label) {
        String id = "e" + (++nextEventNo);
        queue.add(new Pending(id, label, now));
        mark(false, false, true);
        Trace.event("SERVER_EVENT",
                "Tick " + now + ": \"" + label + "\" happened on the server. Nobody asked for it, and "
                        + "the server cannot dial the browser — a tab has no address to be called back on. "
                        + "Whether this reaches the screen in a millisecond or in half a minute is decided "
                        + "entirely by what is (or is not) open between them",
                "Тик " + now + ": на сервере произошло «" + label + "». Никто об этом не спрашивал, и "
                        + "сервер не может «позвонить» в браузер — у вкладки нет адреса для обратного "
                        + "вызова. Дойдёт ли это до экрана за миллисекунду или за полминуты, решает "
                        + "исключительно то, что между ними открыто (или не открыто)",
                List.of("server", "queue"), state());
        push();
    }

    /** The client says something back — a read receipt, a typed line, a cursor move. */
    public void sendFromClient(String label) {
        if (transport == Transport.WEBSOCKET) {
            frames++;
            mark(false, false, false);
            Trace.event("WS_FRAME_UP",
                    "The browser sent \"" + label + "\" as a frame on the SAME connection — no request, "
                            + "no headers, no handshake. This is the one thing only a WebSocket does: the "
                            + "channel is symmetric, so a chat message or a cursor position costs a few "
                            + "bytes instead of a round trip",
                    "Браузер отправил «" + label + "» кадром по ТОМУ ЖЕ соединению — без запроса, без "
                            + "заголовков, без рукопожатия. Это единственное, что умеет только WebSocket: "
                            + "канал симметричен, поэтому сообщение чата или позиция курсора стоят "
                            + "нескольких байт, а не полного обмена",
                    List.of("connection", "client"), state());
            return;
        }
        requests++;
        mark(true, false, false);
        Trace.event("CLIENT_MESSAGE_POSTED",
                "To send \"" + label + "\" the client had to open a SEPARATE call: POST /api/actions. "
                        + transport + " carries data from the server to the browser only, so the way back "
                        + "is an ordinary request. That is usually fine — most apps read far more often "
                        + "than they write — and it is exactly the asymmetry a WebSocket removes",
                "Чтобы отправить «" + label + "», клиенту пришлось открыть ОТДЕЛЬНЫЙ вызов: POST "
                        + "/api/actions. " + transport + " несёт данные только от сервера к браузеру, "
                        + "поэтому обратный путь — обычный запрос. Обычно это нормально: большинство "
                        + "приложений читают куда чаще, чем пишут, — и именно эту асимметрию убирает "
                        + "WebSocket",
                List.of("client"), state());
    }

    /** The laptop lid closes, the train enters a tunnel, the proxy times out. */
    public void goOffline() {
        online = false;
        heldSince = -1;
        Trace.event("CONNECTION_LOST",
                connectionLostEn(), connectionLostRu(),
                List.of("connection"), state());
    }

    /** The network is back; what the client can recover now depends on the transport. */
    public void goOnline() {
        online = true;
        Trace.event("CONNECTION_RESTORED",
                "The network is back at tick " + now + ". Reconnecting is not the interesting part — "
                        + "what matters is whether anything that happened in the gap can still be "
                        + "recovered, and each transport answers that differently",
                "Сеть вернулась на тике " + now + ". Интересно не само переподключение, а то, можно ли "
                        + "ещё получить то, что произошло в промежутке, — и каждый транспорт отвечает на "
                        + "это по-своему",
                List.of("connection"), state());
        switch (transport) {
            case SSE -> resumeStream();
            case WEBSOCKET -> resumeSocket();
            case LONG_POLL -> openLongPoll("LONGPOLL_REOPENED",
                    "The client opens a new long-poll request. Anything that piled up while it was away "
                            + "is still in the server's queue, so the answer can come back immediately",
                    "Клиент открывает новый long-poll запрос. Всё, что накопилось, пока его не было, "
                            + "по-прежнему лежит в очереди сервера, поэтому ответ может прийти сразу");
            default -> { }
        }
    }

    /**
     * The event happened on a different instance from the one holding this
     * client's connection — the problem every push technology inherits the
     * moment you run more than one server.
     */
    public void eventOnAnotherInstance(String label) {
        instances = Math.max(instances, 3);
        boolean connectionBound = transport == Transport.SSE || transport == Transport.WEBSOCKET;
        if (connectionBound && !sharedBus) {
            lost++;
            Trace.event("FANOUT_MISS",
                    "\"" + label + "\" happened on instance B, and this user's connection is held by "
                            + "instance A. B has nothing to write to: an open connection is STATE, and it "
                            + "lives in exactly one process. The notification is simply never sent — no "
                            + "error, no log line, just a screen that stays wrong",
                    "«" + label + "» произошло на экземпляре B, а соединение этого пользователя держит "
                            + "экземпляр A. Писать B некуда: открытое соединение — это СОСТОЯНИЕ, и живёт "
                            + "оно ровно в одном процессе. Уведомление просто никогда не отправляется — ни "
                            + "ошибки, ни записи в логе, только экран, который остался неверным",
                    List.of("server", "connection"), state());
            return;
        }

        String id = "e" + (++nextEventNo);
        queue.add(new Pending(id, label, now));
        mark(false, false, true);
        Trace.event("FANOUT_ROUTED",
                connectionBound
                        ? "\"" + label + "\" happened on instance B again, but now every instance "
                        + "publishes to a shared bus (Redis pub/sub, Kafka, a broker) and subscribes to "
                        + "it. B publishes, A — the one actually holding the connection — receives it and "
                        + "writes it out. Push does not scale horizontally on its own; this is the piece "
                        + "you have to add"
                        : "\"" + label + "\" happened on instance B, and it does not matter in the "
                        + "slightest: the client's next request can land on ANY instance, because the "
                        + "news is in shared storage rather than in somebody's open connection. Being "
                        + "stateless is exactly why polling survives a load balancer for free",
                connectionBound
                        ? "«" + label + "» снова произошло на экземпляре B, но теперь каждый экземпляр "
                        + "публикует события в общую шину (Redis pub/sub, Kafka, брокер) и подписан на "
                        + "неё. B публикует, A — тот, кто на самом деле держит соединение, — получает и "
                        + "записывает в поток. Push сам по себе горизонтально не масштабируется; это та "
                        + "часть, которую нужно добавить"
                        : "«" + label + "» произошло на экземпляре B, и это совершенно неважно: "
                        + "следующий запрос клиента может попасть на ЛЮБОЙ экземпляр, потому что новость "
                        + "лежит в общем хранилище, а не в чьём-то открытом соединении. Именно "
                        + "отсутствие состояния позволяет опросу бесплатно переживать балансировщик",
                List.of("server", "queue"), state());
        push();
    }

    /** Puts a shared bus between the instances so any of them can reach the connection. */
    public void useSharedBus() {
        sharedBus = true;
        instances = Math.max(instances, 3);
        Trace.event("BUS_ATTACHED",
                "A shared bus is attached: every instance publishes domain events to it and every "
                        + "instance subscribes. The one that happens to hold a given client's connection "
                        + "writes the message out. Note what this really is — a second messaging system "
                        + "added purely to make the first one work behind a load balancer",
                "Подключена общая шина: каждый экземпляр публикует в неё доменные события и каждый на "
                        + "неё подписан. Тот, кто держит соединение конкретного клиента, и записывает "
                        + "сообщение. Заметьте, что это такое на самом деле: вторая система обмена "
                        + "сообщениями, добавленная только чтобы первая работала за балансировщиком",
                List.of("server"), state());
    }

    /** Prints what this transport actually cost over the whole run. */
    public void report() {
        Trace.event("CHANNEL_AUDIT",
                "After " + now + " tick(s) on " + transport + ": HTTP requests " + requests
                        + " (of which " + emptyResponses + " came back with nothing), frames pushed "
                        + frames + ", events delivered " + delivered.size() + ", never delivered " + lost
                        + ", average delay " + avgLatency() + " tick(s), worst delay " + maxLatency
                        + " tick(s), still waiting on the server " + queue.size(),
                "После тиков (" + now + ") на " + transport + ": HTTP-запросов " + requests
                        + " (из них пустых: " + emptyResponses + "), отправлено кадров: " + frames
                        + ", доставлено событий: " + delivered.size() + ", не доставлено: " + lost
                        + ", средняя задержка: " + avgLatency() + " тик(ов), худшая задержка: "
                        + maxLatency + " тик(ов), ещё ждёт на сервере: " + queue.size(),
                List.of("cost"), state());
    }

    /**
     * Runs the same timeline through all four transports and reports what each
     * one charged for it — the answer an interviewer is actually after.
     */
    public static void compare() {
        int ticks = 24;
        int[] events = {3, 4, 17};
        List<Object> rows = new ArrayList<>();
        rows.add(row("SHORT_POLL", Transport.SHORT_POLL, 6, 10, ticks, events, "client-pull", false));
        rows.add(row("LONG_POLL", Transport.LONG_POLL, 6, 10, ticks, events, "client-pull", true));
        rows.add(row("SSE", Transport.SSE, 6, 10, ticks, events, "server-push", true));
        rows.add(row("WEBSOCKET", Transport.WEBSOCKET, 6, 10, ticks, events, "full-duplex", true));

        Map<String, Object> s = comparisonState(ticks, rows);
        Trace.event("TRANSPORT_COMPARED",
                "The same 24 ticks and the same 3 events, priced by each transport. Short polling every "
                        + "6 ticks pays 4 requests to deliver 3 events, 2 of them buying nothing, and is "
                        + "late by up to a whole interval; long polling delivers instantly for 5 requests "
                        + "but keeps one parked per client; SSE and WebSocket both deliver instantly over "
                        + "a single connection, and the only thing that separates them is whether the "
                        + "client also needs to send. There is no winner in this table — there is a column "
                        + "you care about",
                "Одни и те же 24 тика и одни и те же 3 события, оценённые каждым транспортом. Короткий "
                        + "опрос раз в 6 тиков платит 4 запроса за доставку 3 событий, 2 из них не "
                        + "покупают ничего, и опаздывает вплоть до целого интервала; long polling "
                        + "доставляет мгновенно за 5 запросов, но держит по одному припаркованному "
                        + "запросу на клиента; SSE и WebSocket доставляют мгновенно по одному соединению, "
                        + "и различает их только то, нужно ли клиенту ещё и отправлять. Победителя в этой "
                        + "таблице нет — есть колонка, которая важна именно вам",
                List.of("cost"), s);
    }

    // -------------------------------------------------------------- delivery

    /** Hands the queue to the browser if — and only if — something is open. */
    private void push() {
        if (!online) {
            waiting("The client is not reachable right now, so the news stays on the server. Nothing is "
                            + "lost yet: it is sitting in a queue, and whether it survives depends on where "
                            + "that queue lives",
                    "Клиент сейчас недоступен, поэтому новость остаётся на сервере. Пока ничего не "
                            + "потеряно: она лежит в очереди, а уцелеет ли — зависит от того, где эта "
                            + "очередь находится");
            return;
        }
        switch (transport) {
            case SHORT_POLL -> waiting(
                    "Nothing is open, so nothing can be sent. The news waits in the queue for the next "
                            + "poll — up to " + interval + " tick(s) away. That waiting IS the latency of "
                            + "polling, and it is bounded only by how often you are willing to pay for a "
                            + "request",
                    "Ничего не открыто, значит и отправить нельзя. Новость ждёт в очереди следующего "
                            + "опроса — до " + interval + " тик(ов). Это ожидание И ЕСТЬ задержка опроса, и "
                            + "ограничена она только тем, как часто вы готовы платить за запрос");
            case LONG_POLL -> {
                if (heldSince < 0) {
                    waiting("The client's request is not parked at the moment — it is between two "
                                    + "long polls. This gap is small but real: an event that lands in it "
                                    + "waits for the next request instead of being answered instantly",
                            "Запрос клиента сейчас не припаркован — он между двумя long-poll вызовами. "
                                    + "Промежуток мал, но реален: событие, попавшее в него, ждёт следующего "
                                    + "запроса вместо мгновенного ответа");
                    return;
                }
                deliver("LONGPOLL_DELIVERED",
                        "The parked request finally answers: 200 with the event(s), " + heldFor()
                                + " tick(s) after it was opened. The client got the news the moment it "
                                + "existed, over completely ordinary HTTP — no new protocol, no upgrade",
                        "Припаркованный запрос наконец отвечает: 200 с событием(ями), через " + heldFor()
                                + " тик(ов) после открытия. Клиент получил новость в момент её появления и "
                                + "по совершенно обычному HTTP — без нового протокола и без апгрейда");
                openLongPoll("LONGPOLL_REOPENED",
                        "The response ended the request, so the client immediately opens the next one. "
                                + "Between those two moments nothing is listening — a busy stream turns "
                                + "long polling back into a request per message, which is why it stops "
                                + "paying off under chatty traffic",
                        "Ответ завершил запрос, поэтому клиент немедленно открывает следующий. Между "
                                + "этими двумя моментами никто не слушает — при плотном потоке long polling "
                                + "снова превращается в запрос на сообщение, поэтому на «болтливом» трафике "
                                + "он перестаёт окупаться");
            }
            case SSE -> {
                frames++;
                deliver("SSE_MESSAGE",
                        "The server writes \"id: " + queueTailId() + "\\ndata: ...\" into the response "
                                + "that never ended. No request, no headers, no handshake — a few bytes "
                                + "down a stream that was already open, and onmessage fires in the tab",
                        "Сервер пишет «id: " + queueTailId() + "\\ndata: ...» в ответ, который так и не "
                                + "закончился. Ни запроса, ни заголовков, ни рукопожатия — несколько байт по "
                                + "уже открытому потоку, и во вкладке срабатывает onmessage");
            }
            case WEBSOCKET -> {
                frames++;
                deliver("WS_FRAME_DOWN",
                        "The server writes a frame into the socket. It did not wait to be asked and it "
                                + "did not answer anything — after the 101 this connection has no notion of "
                                + "request or response left at all",
                        "Сервер пишет кадр в сокет. Он не ждал вопроса и ни на что не отвечал — после 101 "
                                + "у этого соединения вообще не осталось понятий запроса и ответа");
            }
            default -> { }
        }
    }

    /** Moves everything queued to the browser and records what it cost in delay. */
    private void deliver(String event, String descEn, String descRu) {
        int count = queue.size();
        StringBuilder labels = new StringBuilder();
        int worst = 0;
        for (Pending pending : queue) {
            Delivery done = new Delivery(pending, now);
            delivered.add(done);
            latencySum += done.latency();
            worst = Math.max(worst, done.latency());
            maxLatency = Math.max(maxLatency, done.latency());
            lastEventId = pending.id;
            if (labels.length() > 0) {
                labels.append(", ");
            }
            labels.append(pending.label);
        }
        queue.clear();
        mark(false, true, false);
        Trace.event(event,
                descEn + ". Delivered: " + labels + " (" + count + " event(s), waited " + worst
                        + " tick(s))",
                descRu + ". Доставлено: " + labels + " (событий: " + count + ", ожидание: " + worst
                        + " тик(ов))",
                List.of("client", "delivered"), state());
    }

    /** Announces that news exists and has nowhere to go yet. */
    private void waiting(String descEn, String descRu) {
        Trace.event("EVENT_WAITING", descEn, descRu, List.of("queue", "server"), state());
    }

    // ------------------------------------------------------- transport steps

    /** Short polling: the timer fired, so the client asks. */
    private void poll() {
        requests++;
        if (queue.isEmpty()) {
            emptyResponses++;
            markEmptyRequest();
            Trace.event("POLL_EMPTY",
                    "Tick " + now + ": GET /api/events -> 200 []. Request number " + requests + ", and "
                            + emptyResponses + " of them have now come back with nothing. Each one still "
                            + "cost a TCP/TLS round trip, headers, a handler, a database look-up and a log "
                            + "line — multiply that by every open tab you have",
                    "Тик " + now + ": GET /api/events -> 200 []. Запрос номер " + requests + ", и "
                            + emptyResponses + " из них вернулись пустыми. Каждый всё равно стоил обмена "
                            + "TCP/TLS, заголовков, обработчика, обращения к базе и строки в логе — "
                            + "умножьте это на каждую открытую вкладку",
                    List.of("client", "cost"), state());
            return;
        }
        mark(true, false, false);
        deliver("POLL_DELIVERED",
                "Tick " + now + ": GET /api/events -> 200 with the queued event(s). The news did not "
                        + "arrive when it happened — it arrived when the timer next went off",
                "Тик " + now + ": GET /api/events -> 200 с накопившимися событиями. Новость пришла не "
                        + "тогда, когда произошла, а когда в следующий раз сработал таймер");
    }

    /** Long polling: the hold expired with nothing to say. */
    private void longPollTimeout() {
        emptyResponses++;
        heldSince = -1;
        markEmptyRequest();
        Trace.event("LONGPOLL_TIMEOUT",
                "The request has been parked for " + hold + " tick(s) with no news, so the server "
                        + "answers empty on purpose. It has to: browsers, proxies and load balancers all "
                        + "kill connections that go quiet for too long, so the hold must expire before "
                        + "they do",
                "Запрос простоял припаркованным " + hold + " тик(ов) без новостей, поэтому сервер "
                        + "намеренно отвечает пустым ответом. Иначе нельзя: браузеры, прокси и "
                        + "балансировщики рвут соединения, которые слишком долго молчат, поэтому "
                        + "удержание должно истечь раньше, чем это сделают они",
                List.of("client", "cost"), state());
        openLongPoll("LONGPOLL_REOPENED",
                "And the client opens the next one straight away. An idle long poll therefore costs one "
                        + "request per hold window per client — cheap next to polling every few seconds, "
                        + "and not free",
                "И клиент тут же открывает следующий. Простаивающий long polling поэтому стоит одного "
                        + "запроса на окно удержания на клиента — дёшево по сравнению с опросом раз в "
                        + "несколько секунд и всё же не бесплатно");
    }

    /** Parks a fresh long-poll request on the server. */
    private void openLongPoll(String event, String descEn, String descRu) {
        requests++;
        heldSince = now;
        mark(true, false, false);
        Trace.event(event, descEn, descRu, List.of("connection", "client"), state());
        if (!queue.isEmpty()) {
            deliver("LONGPOLL_DELIVERED",
                    "The new request does not have to wait at all — the server already had news queued, "
                            + "so it answers immediately",
                    "Новому запросу вообще не пришлось ждать — у сервера уже была накопленная новость, и "
                            + "он отвечает сразу");
            openLongPoll("LONGPOLL_REOPENED",
                    "And the client parks the next request again",
                    "И клиент снова паркует следующий запрос");
        }
    }

    /** SSE reconnects itself and asks the server to replay from the last id it saw. */
    private void resumeStream() {
        requests++;
        mark(true, false, false);
        if (queue.isEmpty()) {
            Trace.event("SSE_STREAM_OPENED",
                    "EventSource reconnected on its own after its retry interval and sent "
                            + "Last-Event-ID: " + lastSeenId() + ". Nothing happened while "
                            + "it was away, so there is nothing to replay",
                    "EventSource переподключился сам по истечении своего интервала retry и отправил "
                            + "Last-Event-ID: " + lastSeenId() + ". Пока его не было, "
                            + "ничего не произошло, так что повторять нечего",
                    List.of("connection"), state());
            return;
        }
        deliver("SSE_REPLAYED",
                "EventSource reconnected by itself and sent Last-Event-ID: "
                        + lastSeenId() + ", so the server knows exactly where this client "
                        + "stopped and replays what it missed. The gap healed without a single line of "
                        + "application code — that is the protocol doing it, provided your handler "
                        + "actually honours the header",
                "EventSource переподключился сам и отправил Last-Event-ID: "
                        + lastSeenId() + ", поэтому сервер точно знает, где этот клиент "
                        + "остановился, и досылает пропущенное. Дыра затянулась без единой строки "
                        + "прикладного кода — это делает протокол, при условии что ваш обработчик "
                        + "действительно учитывает этот заголовок");
    }

    /** A WebSocket reconnect is your own code, and nothing is replayed for you. */
    private void resumeSocket() {
        requests++;
        mark(true, false, false);
        if (queue.isEmpty()) {
            Trace.event("WS_HANDSHAKE",
                    "Your reconnect code ran and did the handshake again: GET /ws/events with Upgrade -> "
                            + "101. Nothing happened while the socket was down, so nothing was missed this "
                            + "time",
                    "Ваш код переподключения отработал и снова провёл рукопожатие: GET /ws/events с "
                            + "Upgrade -> 101. Пока сокет был мёртв, ничего не произошло, так что на этот "
                            + "раз ничего не пропущено",
                    List.of("connection"), state());
            return;
        }
        int missed = queue.size();
        StringBuilder labels = new StringBuilder();
        for (Pending pending : queue) {
            if (labels.length() > 0) {
                labels.append(", ");
            }
            labels.append(pending.label);
        }
        lost += missed;
        queue.clear();
        Trace.event("MESSAGES_MISSED",
                "The socket is back, and " + missed + " message(s) are gone: " + labels
                        + ". A raw WebSocket has no Last-Event-ID and no replay — frames written to a dead "
                        + "socket are simply dropped. This is why a real client re-fetches its state over "
                        + "plain HTTP after every reconnect and treats the socket as an optimisation, not "
                        + "as the source of truth",
                "Сокет вернулся, и сообщений потеряно: " + missed + " — " + labels
                        + ". У «голого» WebSocket нет ни Last-Event-ID, ни повтора: кадры, записанные в "
                        + "мёртвый сокет, просто отбрасываются. Поэтому настоящий клиент после каждого "
                        + "переподключения перезапрашивает состояние обычным HTTP и относится к сокету как "
                        + "к оптимизации, а не как к источнику истины",
                List.of("connection", "cost"), state());
    }

    // -------------------------------------------------------------- internals

    private void announce(String name, String descEn, String descRu) {
        Trace.event("CHANNEL_OPENED",
                app + " needs to hear about server-side events. Transport chosen: " + name + ". "
                        + descEn,
                app + " должен узнавать о событиях на сервере. Выбранный транспорт: " + name + ". "
                        + descRu,
                List.of("client", "server"), state());
    }

    private void mark(boolean request, boolean delivery, boolean serverEvent) {
        Slot slot = timeline.get(timeline.size() - 1);
        slot.request |= request;
        slot.delivery |= delivery;
        slot.serverEvent |= serverEvent;
    }

    private void markEmptyRequest() {
        Slot slot = timeline.get(timeline.size() - 1);
        slot.request = true;
        slot.empty = true;
    }

    private boolean connectionOpen() {
        if (!online) {
            return false;
        }
        return switch (transport) {
            case LONG_POLL -> heldSince >= 0;
            case SSE, WEBSOCKET -> true;
            default -> false;
        };
    }

    private String connectionKind() {
        if (!connectionOpen()) {
            return "none";
        }
        return switch (transport) {
            case LONG_POLL -> "held-request";
            case SSE -> "stream";
            case WEBSOCKET -> "socket";
            default -> "none";
        };
    }

    private String duplex() {
        return switch (transport) {
            case SHORT_POLL, LONG_POLL -> "client-pull";
            case SSE -> "server-push";
            case WEBSOCKET -> "full-duplex";
        };
    }

    private int heldFor() {
        return heldSince < 0 ? 0 : now - heldSince;
    }

    private String queueTailId() {
        return queue.isEmpty() ? "-" : queue.get(queue.size() - 1).id;
    }

    /** The id SSE would send back in Last-Event-ID after a drop. */
    private String lastSeenId() {
        return lastEventId == null ? "none" : lastEventId;
    }

    private double avgLatency() {
        if (delivered.isEmpty()) {
            return 0.0;
        }
        return Math.round(latencySum * 10.0 / delivered.size()) / 10.0;
    }

    private String connectionLostEn() {
        return switch (transport) {
            case SSE -> "The stream died at tick " + now + " (a closed lid, a proxy timeout, a lost "
                    + "signal). The server does not necessarily notice at once; from the browser's side "
                    + "the response it was reading simply ended.";
            case WEBSOCKET -> "The socket died at tick " + now + ". Everything that made this connection "
                    + "valuable — that it was open, and that the server could write into it — is exactly "
                    + "what is now gone.";
            default -> "The connection died at tick " + now + ", so the client's requests cannot leave. "
                    + "Nothing is at risk on the server side: the news is queued in storage, not held in "
                    + "an open response.";
        };
    }

    private String connectionLostRu() {
        return switch (transport) {
            case SSE -> "Поток оборвался на тике " + now + " (закрытая крышка, таймаут прокси, "
                    + "пропавший сигнал). Сервер не обязательно замечает это сразу; со стороны браузера "
                    + "ответ, который он читал, просто закончился.";
            case WEBSOCKET -> "Сокет оборвался на тике " + now + ". Всё, что делало это соединение "
                    + "ценным, — то, что оно открыто и что сервер может в него писать, — именно этого "
                    + "теперь и нет.";
            default -> "Соединение оборвалось на тике " + now + ", поэтому запросы клиента не могут "
                    + "уйти. На стороне сервера ничем не рискуют: новость лежит в хранилище, а не "
                    + "удерживается в открытом ответе.";
        };
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("transport", transport.name());
        s.put("app", app);
        s.put("now", now);
        s.put("online", online);
        s.put("duplex", duplex());
        s.put("interval", transport == Transport.SHORT_POLL ? interval : null);
        s.put("hold", transport == Transport.LONG_POLL ? hold : null);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("open", connectionOpen());
        connection.put("kind", connectionKind());
        connection.put("openedAt", heldSince < 0 ? null : heldSince);
        connection.put("lastEventId", lastEventId);
        s.put("connection", connection);

        s.put("sharedBus", sharedBus);
        s.put("instances", instances);

        List<Object> waitingList = new ArrayList<>();
        for (Pending pending : queue) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", pending.id);
            row.put("label", pending.label);
            row.put("createdAt", pending.createdAt);
            row.put("waited", now - pending.createdAt);
            waitingList.add(row);
        }
        s.put("queue", waitingList);

        List<Object> deliveredList = new ArrayList<>();
        for (Delivery done : delivered) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", done.id);
            row.put("label", done.label);
            row.put("createdAt", done.createdAt);
            row.put("deliveredAt", done.deliveredAt);
            row.put("latency", done.latency());
            deliveredList.add(row);
        }
        s.put("delivered", deliveredList);

        List<Object> slots = new ArrayList<>();
        for (Slot slot : timeline) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", slot.tick);
            row.put("serverEvent", slot.serverEvent);
            row.put("request", slot.request);
            row.put("empty", slot.empty);
            row.put("delivery", slot.delivery);
            row.put("held", slot.held);
            slots.add(row);
        }
        s.put("timeline", slots);

        s.put("stats", stats(requests, emptyResponses, frames, delivered.size(), lost,
                avgLatency(), maxLatency));
        s.put("comparison", new ArrayList<>());
        return s;
    }

    private static Map<String, Object> stats(int requests, int empty, int frames, int delivered,
                                             int lost, double avgLatency, int maxLatency) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("requests", requests);
        stats.put("empty", empty);
        stats.put("frames", frames);
        stats.put("delivered", delivered);
        stats.put("lost", lost);
        stats.put("avgLatency", avgLatency);
        stats.put("maxLatency", maxLatency);
        return stats;
    }

    // ------------------------------------------------------------ comparison

    /**
     * Replays one fixed timeline through one transport without emitting
     * anything, so the four can be priced side by side.
     */
    private static Map<String, Object> row(String name, Transport transport, int interval, int hold,
                                           int ticks, int[] eventTicks, String duplex, boolean held) {
        int requests = 0;
        int empty = 0;
        int frames = 0;
        int deliveredCount = 0;
        int latencySum = 0;
        int maxLatency = 0;
        List<Integer> waiting = new ArrayList<>();
        int heldSince = -1;

        if (transport == Transport.LONG_POLL) {
            requests = 1;
            heldSince = 0;
        } else if (transport == Transport.SSE || transport == Transport.WEBSOCKET) {
            requests = 1;
        }

        int next = 0;
        for (int tick = 1; tick <= ticks; tick++) {
            while (next < eventTicks.length && eventTicks[next] == tick) {
                waiting.add(tick);
                next++;
            }
            switch (transport) {
                case SSE, WEBSOCKET -> {
                    // An open connection delivers the instant the event exists.
                    frames += waiting.size();
                    deliveredCount += waiting.size();
                    waiting.clear();
                }
                case LONG_POLL -> {
                    if (heldSince >= 0 && !waiting.isEmpty()) {
                        for (int createdAt : waiting) {
                            deliveredCount++;
                            latencySum += tick - createdAt;
                            maxLatency = Math.max(maxLatency, tick - createdAt);
                        }
                        waiting.clear();
                        requests++;
                        heldSince = tick;
                    }
                    if (heldSince >= 0 && tick - heldSince >= hold) {
                        empty++;
                        requests++;
                        heldSince = tick;
                    }
                }
                default -> {
                    if (tick % interval == 0) {
                        requests++;
                        if (waiting.isEmpty()) {
                            empty++;
                        } else {
                            for (int createdAt : waiting) {
                                deliveredCount++;
                                latencySum += tick - createdAt;
                                maxLatency = Math.max(maxLatency, tick - createdAt);
                            }
                            waiting.clear();
                        }
                    }
                }
            }
        }

        double avg = deliveredCount == 0 ? 0.0 : Math.round(latencySum * 10.0 / deliveredCount) / 10.0;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("transport", name);
        row.put("requests", requests);
        row.put("empty", empty);
        row.put("frames", frames);
        row.put("delivered", deliveredCount);
        row.put("avgLatency", avg);
        row.put("maxLatency", maxLatency);
        row.put("duplex", duplex);
        row.put("held", held);
        return row;
    }

    /** A well-formed state whose only interesting part is the comparison table. */
    private static Map<String, Object> comparisonState(int ticks, List<Object> rows) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("transport", null);
        s.put("app", "same events, four transports");
        s.put("now", ticks);
        s.put("online", true);
        s.put("duplex", null);
        s.put("interval", null);
        s.put("hold", null);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("open", false);
        connection.put("kind", "none");
        connection.put("openedAt", null);
        connection.put("lastEventId", null);
        s.put("connection", connection);

        s.put("sharedBus", false);
        s.put("instances", 1);
        s.put("queue", new ArrayList<>());
        s.put("delivered", new ArrayList<>());
        s.put("timeline", new ArrayList<>());
        s.put("stats", stats(0, 0, 0, 0, 0, 0.0, 0));
        s.put("comparison", rows);
        return s;
    }
}
