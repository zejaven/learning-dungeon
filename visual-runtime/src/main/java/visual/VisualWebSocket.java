package visual;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of one WebSocket endpoint, from the HTTP handshake
 * down to the object the container hands your code.
 *
 * <p>Three questions this model is built to answer, because they are the three
 * halves of the interview question:
 *
 * <ol>
 *   <li><b>How does the connection start?</b> With an ordinary HTTP GET carrying
 *       {@code Upgrade: websocket} and a {@code Sec-WebSocket-Key}. The server
 *       answers {@code 101 Switching Protocols} with a
 *       {@code Sec-WebSocket-Accept} derived from that key — and this model
 *       computes it for real (SHA-1 of the key plus the RFC 6455 GUID, base64),
 *       so the header lines you see are the header lines that would be on the
 *       wire.</li>
 *   <li><b>What is underneath?</b> One TCP connection — the very same one the
 *       GET arrived on. Never UDP. After 101 nothing on it is HTTP any more:
 *       both sides write frames whenever they like, which is what
 *       "bidirectional" actually means.</li>
 *   <li><b>Who serves it?</b> {@link Model#PER_CONNECTION} — the Jakarta
 *       WebSocket default, where the container creates a new endpoint object
 *       for every connection — or {@link Model#SHARED}, the Spring
 *       {@code WebSocketHandler} shape, where one singleton bean serves every
 *       connection. The difference is invisible until somebody stores a user in
 *       a field, which is exactly what {@link #rememberInField} does here.</li>
 * </ol>
 *
 * <p>Nothing is timed or random: frames are numbered, byte counts are computed
 * from the real framing rules, and handshake nonces are derived from the client
 * name — so every run produces the same trace. Every step emits a bilingual
 * {@link Trace} event and the class stays dependency-free.
 */
public class VisualWebSocket {

    /** How the container decides which object handles a connection. */
    public enum Model { PER_CONNECTION, SHARED }

    /** The magic string RFC 6455 appends to the key before hashing. */
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** The nonce used in the RFC 6455 example, so the arithmetic is checkable. */
    private static final String RFC_NONCE = "the sample nonce";

    /** One endpoint object, whether it serves one connection or all of them. */
    private static final class Instance {

        private final String name;
        private final List<String> serves = new ArrayList<>();
        private final Map<String, String> fieldValues = new LinkedHashMap<>();
        private final Map<String, String> fieldOwners = new LinkedHashMap<>();
        private boolean alive = true;

        private Instance(String name) {
            this.name = name;
        }
    }

    /** One browser tab: its socket, its phase and its session-scoped state. */
    private static final class Conn {

        private final String id;
        private final int ordinal;
        private final String origin;
        private final String key;
        private final String accept;
        private Instance instance;
        private String phase = "HTTP";
        private int sent;
        private int received;
        private Integer closeCode;
        private final Map<String, String> session = new LinkedHashMap<>();

        private Conn(String id, int ordinal, String origin, String key, String accept) {
            this.id = id;
            this.ordinal = ordinal;
            this.origin = origin;
            this.key = key;
            this.accept = accept;
        }
    }

    /** One frame on the wire, in either direction. */
    private static final class Frame {

        private final int seq;
        private final String dir;
        private final String opcode;
        private final String client;
        private final String payload;
        private final boolean masked;
        private final int bytes;

        private Frame(int seq, String dir, String opcode, String client, String payload,
                      boolean masked, int bytes) {
            this.seq = seq;
            this.dir = dir;
            this.opcode = opcode;
            this.client = client;
            this.payload = payload;
            this.masked = masked;
            this.bytes = bytes;
        }
    }

    // ------------------------------------------------------------------ state

    private final String host;
    private final String path;
    private final Model model;
    private final String endpointClass;

    private boolean tls;
    private String allowedOrigin;

    private final Map<String, Conn> connections = new LinkedHashMap<>();
    private final List<Instance> instances = new ArrayList<>();
    private final List<Frame> frames = new ArrayList<>();
    private Instance singleton;

    private List<String> requestLines = new ArrayList<>();
    private List<String> responseLines = new ArrayList<>();
    private String handshakeClient;
    private Boolean handshakeAccepted;

    private int handshakes;
    private int rejected;
    private int instancesCreated;
    private int framesIn;
    private int framesOut;
    private int bytes;
    private int clashes;
    private int leaks;
    private int lost;
    private int closed;
    private int dropped;

    private VisualWebSocket(String host, String path, Model model, String endpointClass) {
        this.host = host;
        this.path = path;
        this.model = model;
        this.endpointClass = endpointClass;
    }

    // -------------------------------------------------------------- factories

    /**
     * The Jakarta WebSocket default: a {@code @ServerEndpoint} POJO of which the
     * container creates <em>a new instance per connection</em>.
     */
    public static VisualWebSocket perConnection(String host, String path, String endpointClass) {
        VisualWebSocket server = new VisualWebSocket(host, path, Model.PER_CONNECTION, endpointClass);
        server.announce(
                "Jakarta WebSocket (JSR-356): @ServerEndpoint(\"" + path + "\") on class " + endpointClass
                        + ". The container instantiates " + endpointClass + " ONCE PER CONNECTION and keeps "
                        + "that object for the whole life of the socket, so an instance field is private to "
                        + "one client. Two things people get wrong about this shape: the object is created by "
                        + "the container, not by Spring, so @Autowired fields are null unless you install a "
                        + "custom Configurator; and \"per connection\" only covers instance fields — a static "
                        + "field or a shared registry is still touched by every connection's thread",
                "Jakarta WebSocket (JSR-356): @ServerEndpoint(\"" + path + "\") на классе " + endpointClass
                        + ". Контейнер создаёт " + endpointClass + " ПО ЭКЗЕМПЛЯРУ НА СОЕДИНЕНИЕ и держит "
                        + "этот объект всё время жизни сокета, поэтому поле экземпляра принадлежит одному "
                        + "клиенту. Две вещи, в которых тут ошибаются: объект создаёт контейнер, а не Spring, "
                        + "поэтому поля с @Autowired будут null, пока не поставишь свой Configurator; и «на "
                        + "соединение» распространяется только на поля экземпляра — статическое поле или общий "
                        + "реестр по-прежнему трогают потоки всех соединений");
        return server;
    }

    /**
     * The Spring shape: one {@code WebSocketHandler} bean — a singleton — serves
     * every connection, and per-connection state has to live somewhere else.
     */
    public static VisualWebSocket shared(String host, String path, String handlerClass) {
        VisualWebSocket server = new VisualWebSocket(host, path, Model.SHARED, handlerClass);
        server.announce(
                "Spring WebSocket: registerHandler(new " + handlerClass + "(), \"" + path + "\"). "
                        + handlerClass + " is an ordinary singleton bean, so ONE object handles every "
                        + "connection and every callback — handleMessage is called with a WebSocketSession "
                        + "argument precisely because the handler itself cannot know who it is talking to. "
                        + "That makes dependency injection trivial and makes an instance field a bug: it is "
                        + "shared by all clients and written from all their threads",
                "Spring WebSocket: registerHandler(new " + handlerClass + "(), \"" + path + "\"). "
                        + handlerClass + " — обычный singleton-бин, поэтому ОДИН объект обслуживает все "
                        + "соединения и все колбэки: handleMessage получает аргументом WebSocketSession как "
                        + "раз потому, что сам обработчик не может знать, с кем он говорит. Это делает "
                        + "внедрение зависимостей тривиальным и делает поле экземпляра ошибкой: оно общее для "
                        + "всех клиентов и пишется из всех их потоков");
        return server;
    }

    /** Switches the endpoint to {@code wss://} — TLS over the same TCP connection. */
    public VisualWebSocket secure() {
        tls = true;
        Trace.event("TLS_ENABLED",
                "The endpoint is now " + url() + " on port 443. Nothing about the WebSocket protocol "
                        + "changed: TLS is a layer between TCP and the frames, exactly as it is between TCP "
                        + "and HTTP. The practical reason to insist on wss:// is not only privacy — plain "
                        + "ws:// is the shape middleboxes feel free to inspect, buffer and mangle, and a "
                        + "browser refuses it outright from an https:// page",
                "Эндпоинт теперь " + url() + " на порту 443. В самом протоколе WebSocket ничего не "
                        + "изменилось: TLS — это слой между TCP и фреймами, ровно как между TCP и HTTP. "
                        + "Настаивать на wss:// стоит не только ради приватности: именно простой ws:// "
                        + "промежуточные узлы считают себя вправе разбирать, буферизовать и портить, а "
                        + "браузер со страницы https:// его просто запретит",
                List.of("transport"), state());
        return this;
    }

    /**
     * Turns on the server-side {@code Origin} check. A WebSocket handshake is not
     * covered by CORS, so this check is the server's own job or it does not happen.
     */
    public VisualWebSocket withOriginCheck(String origin) {
        allowedOrigin = origin;
        Trace.event("ORIGIN_CHECK_ON",
                "The handshake will now be accepted only from Origin: " + origin + ". This has to be "
                        + "written by hand because the browser's same-origin protections do NOT apply here: "
                        + "there is no preflight and no Access-Control-Allow-Origin negotiation on a "
                        + "WebSocket. Any page on the internet may open a socket to this URL, and the browser "
                        + "will happily attach your cookies to the handshake — which is cross-site WebSocket "
                        + "hijacking, and checking Origin (plus a token that is not a cookie) is the fix",
                "Рукопожатие теперь принимается только с Origin: " + origin + ". Это приходится писать "
                        + "руками, потому что браузерная защита одного источника здесь НЕ действует: у "
                        + "WebSocket нет ни preflight, ни согласования Access-Control-Allow-Origin. Любая "
                        + "страница в интернете может открыть сокет на этот URL, и браузер с радостью "
                        + "приложит к рукопожатию твои куки — это и есть cross-site WebSocket hijacking, а "
                        + "лечится оно проверкой Origin (плюс токеном, который не кука)",
                List.of("handshake"), state());
        return this;
    }

    // -------------------------------------------------------------- handshake

    /** Opens a connection from a trusted page. */
    public void connect(String client) {
        connect(client, allowedOrigin != null ? allowedOrigin : "https://" + host);
    }

    /**
     * Opens a connection from {@code origin}: one ordinary HTTP GET that either
     * becomes a socket or stays an ordinary HTTP error.
     */
    public void connect(String client, String origin) {
        int ordinal = connections.size() + 1;
        String nonce = ordinal == 1 ? RFC_NONCE : pad16("nonce-" + client);
        String key = Base64.getEncoder().encodeToString(nonce.getBytes(StandardCharsets.UTF_8));
        String accept = accept(key);
        Conn conn = new Conn(client, ordinal, origin, key, accept);
        connections.put(client, conn);

        handshakeClient = client;
        handshakeAccepted = null;
        requestLines = List.of(
                "GET " + path + " HTTP/1.1",
                "Host: " + host,
                "Upgrade: websocket",
                "Connection: Upgrade",
                "Sec-WebSocket-Key: " + key,
                "Sec-WebSocket-Version: 13",
                "Origin: " + origin);
        responseLines = List.of();
        Trace.event("HANDSHAKE_REQUEST",
                client + " opens a TCP connection to " + host + ":" + port() + " and sends a completely "
                        + "ordinary HTTP GET on it — same request line, same headers, same cookies. Three "
                        + "headers make it an upgrade request: Upgrade: websocket and Connection: Upgrade say "
                        + "what it wants, Sec-WebSocket-Key carries 16 random bytes in base64 ("
                        + key + "), and Sec-WebSocket-Version: 13 pins RFC 6455. The key is not a secret and "
                        + "not authentication — it exists so a cache or a proxy that does not understand "
                        + "upgrades cannot fake its way into being treated as a WebSocket server",
                client + " открывает TCP-соединение к " + host + ":" + port() + " и отправляет по нему "
                        + "совершенно обычный HTTP-запрос GET — та же строка запроса, те же заголовки, те же "
                        + "куки. Запросом на апгрейд его делают три заголовка: Upgrade: websocket и "
                        + "Connection: Upgrade говорят, чего он хочет, Sec-WebSocket-Key несёт 16 случайных "
                        + "байт в base64 (" + key + "), а Sec-WebSocket-Version: 13 фиксирует RFC 6455. Ключ "
                        + "— не секрет и не аутентификация: он нужен, чтобы кэш или прокси, не понимающий "
                        + "апгрейдов, не смог случайно сойти за WebSocket-сервер",
                List.of("handshake", "transport"), state());

        if (allowedOrigin != null && !allowedOrigin.equals(origin)) {
            reject(conn);
            return;
        }
        handshakes++;
        handshakeAccepted = true;
        conn.phase = "OPEN";
        responseLines = List.of(
                "HTTP/1.1 101 Switching Protocols",
                "Upgrade: websocket",
                "Connection: Upgrade",
                "Sec-WebSocket-Accept: " + accept);
        Trace.event("HANDSHAKE_ACCEPTED",
                "101 Switching Protocols" + (ordinal == 1
                        ? " — and this is the RFC 6455 example, so you can check the arithmetic: "
                        : " — the accept value is derived, not invented: ")
                        + "Sec-WebSocket-Accept = base64(SHA-1(key + \"" + GUID + "\")) = " + accept
                        + ". 101 is the whole point of the status code range: it does not mean \"here is "
                        + "your response\", it means \"this connection stops being HTTP now\". The same TCP "
                        + "connection stays open and carries frames from here on — no second connection, no "
                        + "second port, no HTTP request/response pairing ever again",
                "101 Switching Protocols" + (ordinal == 1
                        ? " — и это пример из RFC 6455, так что арифметику можно проверить: "
                        : " — значение accept выводится, а не выдумывается: ")
                        + "Sec-WebSocket-Accept = base64(SHA-1(key + \"" + GUID + "\")) = " + accept
                        + ". В этом весь смысл кода 101: он означает не «вот твой ответ», а «это соединение "
                        + "с этого момента перестаёт быть HTTP». То же самое TCP-соединение остаётся "
                        + "открытым и дальше несёт фреймы — ни второго соединения, ни второго порта, ни "
                        + "пар «запрос-ответ» больше никогда",
                List.of("handshake", "transport"), state());
        attach(conn);
    }

    /** The handshake failed a check, so it stays an ordinary HTTP exchange. */
    private void reject(Conn conn) {
        rejected++;
        handshakeAccepted = false;
        conn.phase = "CLOSED";
        conn.closeCode = null;
        responseLines = List.of(
                "HTTP/1.1 403 Forbidden",
                "Connection: close");
        Trace.event("HANDSHAKE_REJECTED",
                "Origin " + conn.origin + " is not " + allowedOrigin + ", so the server answers 403 and "
                        + "closes. Notice what this proves: until the 101 arrives this is still HTTP, so it "
                        + "can be refused with a status code the way any request can — and this is the only "
                        + "moment where that is true. There is no per-message authorization afterwards, no "
                        + "status code inside a frame and no way to send a 401 to an open socket, so the "
                        + "handshake is where authentication, authorization and origin checking all have to "
                        + "happen",
                "Origin " + conn.origin + " не равен " + allowedOrigin + ", поэтому сервер отвечает 403 и "
                        + "закрывает соединение. Обратите внимание, что это доказывает: пока не пришёл 101, "
                        + "перед нами всё ещё HTTP, и его можно отклонить кодом состояния, как любой запрос, "
                        + "— и это единственный момент, когда так можно. Дальше не будет ни авторизации на "
                        + "каждое сообщение, ни кода состояния внутри фрейма, ни способа отправить 401 в "
                        + "открытый сокет, поэтому аутентификация, авторизация и проверка Origin должны "
                        + "происходить именно на рукопожатии",
                List.of("handshake"), state());
    }

    /** Gives the freshly opened connection the object that will serve it. */
    private void attach(Conn conn) {
        if (model == Model.PER_CONNECTION) {
            instancesCreated++;
            Instance instance = new Instance(endpointClass + "#" + instancesCreated);
            instance.serves.add(conn.id);
            instances.add(instance);
            conn.instance = instance;
            Trace.event("ENDPOINT_INSTANTIATED",
                    "The container called the no-arg constructor and made a brand new " + instance.name
                            + " for this connection alone. Its @OnOpen ran, it will get every @OnMessage from "
                            + "this client and nobody else's, and when the socket closes the object is "
                            + "garbage. So an instance field here IS per-connection state — this is the shape "
                            + "where \"just store the user in a field\" is correct. Two costs come with it: "
                            + "the container built the object, so Spring never injected anything into it, and "
                            + "one object plus one Session per connection is your memory-per-client figure",
                    "Контейнер вызвал конструктор без аргументов и создал совершенно новый " + instance.name
                            + " только для этого соединения. Отработал его @OnOpen, он будет получать все "
                            + "@OnMessage от этого клиента и ни от кого другого, а когда сокет закроется, "
                            + "объект станет мусором. Значит, поле экземпляра здесь И ЕСТЬ состояние "
                            + "соединения: это та форма, где «просто положи пользователя в поле» — верно. "
                            + "Цена двойная: объект создал контейнер, поэтому Spring в него ничего не "
                            + "внедрил, и один объект плюс одна Session на соединение — это и есть твоя "
                            + "память на клиента",
                    List.of("instances", "connections"), state());
            return;
        }
        if (singleton == null) {
            instancesCreated++;
            singleton = new Instance(endpointClass + "@singleton");
            instances.add(singleton);
        }
        singleton.serves.add(conn.id);
        conn.instance = singleton;
        Trace.event("ENDPOINT_SHARED",
                "No object was created. " + singleton.name + " already exists and now serves "
                        + singleton.serves.size() + " connection(s): " + String.join(", ", singleton.serves)
                        + ". This is why every callback takes the session as a parameter — the handler is "
                        + "stateless by construction and the session is the only thing that tells it who is "
                        + "speaking. It also means the object is entered by as many threads as there are "
                        + "active connections, so anything mutable inside it is shared, concurrent state "
                        + "whether you meant it that way or not",
                "Никакой объект не создавался. " + singleton.name + " уже существует и теперь обслуживает "
                        + "соединений: " + singleton.serves.size() + " (" + String.join(", ", singleton.serves)
                        + "). Именно поэтому каждый колбэк принимает сессию параметром: обработчик "
                        + "по построению не имеет состояния, и сессия — единственное, что говорит ему, кто "
                        + "сейчас говорит. Это же означает, что в объект входит столько потоков, сколько "
                        + "активных соединений, поэтому всё изменяемое внутри него — общее конкурентное "
                        + "состояние, хотел ты того или нет",
                List.of("instances", "connections"), state());
    }

    // ----------------------------------------------------------------- frames

    /** A TEXT frame from the browser to the server. */
    public void send(String client, String text) {
        Conn conn = require(client);
        if (!open(conn)) {
            frameLost(conn, "IN", text);
            return;
        }
        int size = record(conn, "IN", "TEXT", text, true);
        conn.sent++;
        framesIn++;
        Trace.event("FRAME_FROM_CLIENT",
                client + " writes a TEXT frame: \"" + text + "\" — " + size + " bytes on the wire (a "
                        + "2-byte header, a 4-byte masking key and the payload). No request line, no method, "
                        + "no URL, no cookies, no Content-Type: all of that was spent once on the handshake "
                        + "and never repeats. Client-to-server frames are always masked, which is not "
                        + "security — it is there so a broken cache in the middle cannot be tricked into "
                        + "reading attacker-chosen bytes as if they were a request",
                client + " записывает TEXT-фрейм: «" + text + "» — " + size + " байт на проводе "
                        + "(2-байтный заголовок, 4-байтный ключ маски и полезная нагрузка). Ни строки "
                        + "запроса, ни метода, ни URL, ни кук, ни Content-Type: всё это было потрачено один "
                        + "раз на рукопожатии и больше не повторяется. Фреймы от клиента к серверу всегда "
                        + "маскируются, и это не безопасность — это защита от того, чтобы сломанный кэш "
                        + "посередине не прочитал выбранные атакующим байты как запрос",
                List.of("frames", "connections"), state());
    }

    /** A BINARY frame from the browser: same framing, opcode 0x2. */
    public void sendBinary(String client, String label, int payloadBytes) {
        Conn conn = require(client);
        if (!open(conn)) {
            frameLost(conn, "IN", label);
            return;
        }
        int size = frameSize(payloadBytes, true);
        frames.add(new Frame(frames.size() + 1, "IN", "BINARY", client, label, true, size));
        bytes += size;
        conn.sent++;
        framesIn++;
        Trace.event("FRAME_FROM_CLIENT",
                client + " writes a BINARY frame (opcode 0x2): " + label + ", " + payloadBytes
                        + " payload bytes, " + size + " on the wire. This is a capability the HTTP-shaped "
                        + "alternatives do not have — SSE is a text protocol, so bytes have to be base64'd "
                        + "into it at a 33% tax. Here the payload length field grew to 16 bits because the "
                        + "body no longer fits in the 7-bit form, which is the entire difference in framing "
                        + "cost between a small and a large message",
                client + " записывает BINARY-фрейм (opcode 0x2): " + label + ", полезной нагрузки "
                        + payloadBytes + " байт, на проводе " + size + ". Это возможность, которой нет у "
                        + "альтернатив в форме HTTP: SSE — текстовый протокол, поэтому байты приходится "
                        + "кодировать в base64 с налогом в 33%. Здесь поле длины выросло до 16 бит, потому "
                        + "что тело больше не помещается в 7-битную форму, — и в этом вся разница в цене "
                        + "кадрирования между маленьким и большим сообщением",
                List.of("frames", "connections"), state());
    }

    /** A TEXT frame the server writes without anyone asking for it. */
    public void push(String client, String text) {
        Conn conn = require(client);
        if (!open(conn)) {
            frameLost(conn, "OUT", text);
            return;
        }
        int size = record(conn, "OUT", "TEXT", text, false);
        conn.received++;
        framesOut++;
        Trace.event("FRAME_FROM_SERVER",
                "The server writes \"" + text + "\" to " + client + " — " + size + " bytes, unmasked — "
                        + "and no request asked for it. THIS is what bidirectional means: the socket is not "
                        + "a queue of answers, it is a pipe both ends may write into at any time, including "
                        + "at the same time. There is no status code, no correlation id and no response "
                        + "pairing here: if this frame is a reply to something, that is a convention your "
                        + "application invented, not a property of the protocol",
                "Сервер записывает «" + text + "» клиенту " + client + " — " + size + " байт, без маски — "
                        + "и никакой запрос об этом не просил. ВОТ что означает «двусторонний»: сокет — не "
                        + "очередь ответов, а труба, в которую оба конца могут писать когда угодно, в том "
                        + "числе одновременно. Здесь нет ни кода состояния, ни идентификатора корреляции, "
                        + "ни привязки ответа к запросу: если этот фрейм — ответ на что-то, то это "
                        + "соглашение, придуманное твоим приложением, а не свойство протокола",
                List.of("frames", "connections"), state());
    }

    /** One message to every open socket — the fan-out the protocol does not provide. */
    public void broadcast(String text) {
        List<String> targets = new ArrayList<>();
        int total = 0;
        for (Conn conn : connections.values()) {
            if (!open(conn)) {
                continue;
            }
            total += record(conn, "OUT", "TEXT", text, false);
            conn.received++;
            framesOut++;
            targets.add(conn.id);
        }
        Trace.event("BROADCAST",
                "\"" + text + "\" written to " + targets.size() + " socket(s): " + String.join(", ", targets)
                        + " — " + total + " bytes in total. The loop is the point: a WebSocket has no "
                        + "concept of a topic, a room or a subscriber list, so \"broadcast\" is you iterating "
                        + "a collection of open sessions that you keep yourself. That collection is written "
                        + "and read by every connection's thread, so it has to be concurrent (a "
                        + "CopyOnWriteArraySet is the usual answer) — and it lives inside ONE process, which "
                        + "is why a second instance behind the load balancer needs a shared bus to reach "
                        + "sockets it does not hold",
                "«" + text + "» записано в сокеты: " + targets.size() + " (" + String.join(", ", targets)
                        + ") — всего " + total + " байт. Смысл именно в цикле: у WebSocket нет понятия темы, "
                        + "комнаты или списка подписчиков, поэтому «широковещание» — это ты сам обходишь "
                        + "коллекцию открытых сессий, которую сам же и ведёшь. В эту коллекцию пишут и "
                        + "читают потоки всех соединений, поэтому она обязана быть конкурентной (обычный "
                        + "ответ — CopyOnWriteArraySet), и живёт она внутри ОДНОГО процесса — поэтому "
                        + "второму экземпляру за балансировщиком нужна общая шина, чтобы дотянуться до "
                        + "чужих сокетов",
                List.of("frames", "connections"), state());
    }

    /** A PING control frame and the PONG the peer must send back. */
    public void ping(String client) {
        Conn conn = require(client);
        if (!open(conn)) {
            frameLost(conn, "OUT", "ping");
            return;
        }
        int out = record(conn, "OUT", "PING", "", false);
        int in = record(conn, "IN", "PONG", "", true);
        framesOut++;
        framesIn++;
        Trace.event("PING_PONG",
                "PING (opcode 0x9) out, PONG (0xA) back — " + (out + in) + " bytes to prove the "
                        + "connection is still there. Two different problems make this mandatory rather than "
                        + "decorative. A silent TCP connection can be dead for minutes without either side "
                        + "noticing, because nothing tells you a peer vanished until you write to it. And "
                        + "everything in the path — a load balancer, an nginx, a corporate proxy, a NAT "
                        + "table — closes idle connections on its own schedule, typically after 30-60 "
                        + "seconds, so a heartbeat is what keeps a quiet socket alive",
                "PING (opcode 0x9) наружу, PONG (0xA) обратно — " + (out + in) + " байт, чтобы доказать, "
                        + "что соединение ещё живо. Обязательным, а не декоративным это делают две разные "
                        + "проблемы. Молчащее TCP-соединение может быть мёртвым минутами, и ни одна сторона "
                        + "этого не заметит, потому что об исчезновении собеседника узнаёшь только когда "
                        + "попробуешь ему написать. И всё, что стоит по пути, — балансировщик, nginx, "
                        + "корпоративный прокси, таблица NAT — закрывает простаивающие соединения по своему "
                        + "расписанию, обычно через 30-60 секунд, поэтому именно heartbeat держит тихий "
                        + "сокет живым",
                List.of("frames", "transport"), state());
    }

    /** Something was written to a socket that is not open any more. */
    private void frameLost(Conn conn, String dir, String payload) {
        lost++;
        Trace.event("FRAME_LOST",
                "\"" + payload + "\" was written to " + conn.id + ", whose socket is " + conn.phase
                        + ". It is gone — no exception you can retry, no queue it waits in, no redelivery "
                        + "when the client comes back. This is the honest difference between a WebSocket and "
                        + "a message broker: the socket is a pipe, and a pipe that is not there does not "
                        + "store anything. Anything that must survive a reconnect has to be in a database or "
                        + "a broker, with the client re-fetching state after it reconnects",
                "«" + payload + "» записано клиенту " + conn.id + ", чей сокет в состоянии " + conn.phase
                        + ". Оно потеряно: ни исключения, которое можно повторить, ни очереди, где оно "
                        + "подождёт, ни передоставки, когда клиент вернётся. В этом честная разница между "
                        + "WebSocket и брокером сообщений: сокет — это труба, а труба, которой нет, ничего "
                        + "не хранит. Всё, что должно пережить переподключение, обязано лежать в базе или в "
                        + "брокере, а клиент после переподключения должен перезапросить состояние",
                List.of("frames", "connections"), state());
    }

    // ------------------------------------------------------------------ state

    /** Assigns a field on the endpoint object serving this connection. */
    public void rememberInField(String client, String field, String value) {
        Conn conn = require(client);
        Instance instance = conn.instance;
        if (instance == null) {
            frameLost(conn, "OUT", field);
            return;
        }
        String previousOwner = instance.fieldOwners.get(field);
        String previousValue = instance.fieldValues.get(field);
        instance.fieldValues.put(field, value);
        instance.fieldOwners.put(field, client);
        Trace.event("FIELD_WRITTEN",
                "this." + field + " = \"" + value + "\" — executed on " + instance.name + " while handling "
                        + client + ". The line is identical in both models; what differs is which object it "
                        + "lands on, and that is decided by the container, not by this code. "
                        + (model == Model.PER_CONNECTION
                        ? "Here the object belongs to this connection, so the assignment is safe and the "
                        + "value will still be there on the next message from this client"
                        : "Here the object is shared by every connection, so this assignment is a write to "
                        + "global mutable state performed from one client's thread"),
                "this." + field + " = «" + value + "» — выполнено на " + instance.name + " при обработке "
                        + client + ". Строка в обеих моделях одинаковая; различается то, на какой объект "
                        + "она попадает, и решает это контейнер, а не этот код. "
                        + (model == Model.PER_CONNECTION
                        ? "Здесь объект принадлежит этому соединению, поэтому присваивание безопасно и "
                        + "значение останется на месте к следующему сообщению от этого клиента"
                        : "Здесь объект общий для всех соединений, поэтому это присваивание — запись в "
                        + "глобальное изменяемое состояние из потока одного клиента"),
                List.of("instances"), state());

        if (previousOwner != null && !previousOwner.equals(client)) {
            clashes++;
            Trace.event("STATE_CLASHED",
                    "And it just overwrote " + previousOwner + "'s value: " + field + " was \""
                            + previousValue + "\", it is now \"" + value + "\", on the one object both "
                            + "connections are being served by. Nothing failed, nothing was logged, and the "
                            + "bug does not exist with one user on your laptop — it appears the moment a "
                            + "second person connects, and it shows up as one user seeing another user's "
                            + "data. Worse, two threads racing on that field can also leave it torn or stale "
                            + "for a third one",
                    "И оно только что перезаписало значение клиента " + previousOwner + ": поле " + field
                            + " было «" + previousValue + "», стало «" + value + "» — на одном объекте, "
                            + "которым обслуживаются оба соединения. Ничего не упало, ничего не "
                            + "залогировано, и на твоём ноутбуке с одним пользователем этой ошибки не "
                            + "существует: она появляется в момент, когда подключается второй, и выглядит "
                            + "как «один пользователь видит данные другого». Хуже того, два потока, "
                            + "гоняющиеся за это поле, могут оставить его несогласованным или устаревшим "
                            + "для третьего",
                    List.of("instances", "clash"), state());
        }
    }

    /** Reads that field back while handling a message from {@code client}. */
    public String readField(String client, String field) {
        Conn conn = require(client);
        Instance instance = conn.instance;
        String value = instance == null ? null : instance.fieldValues.get(field);
        String owner = instance == null ? null : instance.fieldOwners.get(field);
        if (owner != null && !owner.equals(client)) {
            leaks++;
            Trace.event("STATE_LEAKED",
                    "A message arrives from " + client + ", the handler reads this." + field + " to find "
                            + "out who is speaking — and gets \"" + value + "\", which belongs to " + owner
                            + ". The next line will fetch " + owner + "'s data, or write " + client
                            + "'s message under " + owner + "'s name. This is the classic WebSocket "
                            + "production incident: a chat where names swap, a dashboard showing someone "
                            + "else's account, an audit log attributing an action to the wrong person — from "
                            + "one field on a singleton",
                    "Приходит сообщение от " + client + ", обработчик читает this." + field + ", чтобы "
                            + "понять, кто говорит, — и получает «" + value + "», которое принадлежит "
                            + owner + ". Следующая строка достанет данные клиента " + owner + " или запишет "
                            + "сообщение клиента " + client + " под именем " + owner + ". Это классический "
                            + "продакшен-инцидент с WebSocket: чат, где путаются имена, дашборд с чужим "
                            + "аккаунтом, журнал аудита, приписывающий действие не тому человеку, — и всё "
                            + "из-за одного поля на синглтоне",
                    List.of("instances", "clash"), state());
            return value;
        }
        Trace.event("FIELD_READ",
                "Handling a message from " + client + ", the handler reads this." + field + " and gets \""
                        + value + "\" — its own. In this model the object was created for this connection "
                        + "and no other thread enters it on behalf of anybody else, so the field is as "
                        + "private as a local variable would be. That is exactly the guarantee you lose the "
                        + "day someone makes the endpoint a singleton to inject a service into it",
                "Обрабатывая сообщение от " + client + ", обработчик читает this." + field + " и получает "
                        + "«" + value + "» — своё собственное. В этой модели объект был создан для этого "
                        + "соединения, и никакой другой поток не заходит в него от чужого имени, поэтому "
                        + "поле настолько же приватно, как локальная переменная. Ровно эту гарантию теряют "
                        + "в тот день, когда эндпоинт делают синглтоном, чтобы внедрить в него сервис",
                List.of("instances"), state());
        return value;
    }

    /**
     * Stores the value on the connection's own {@code Session} instead — the
     * answer that is correct in both models.
     */
    public void rememberInSession(String client, String key, String value) {
        Conn conn = require(client);
        conn.session.put(key, value);
        Trace.event("SESSION_STATE_STORED",
                "session.getUserProperties().put(\"" + key + "\", \"" + value + "\") for " + client
                        + " — in Spring, session.getAttributes(). The Session object is created by the "
                        + "container per connection in BOTH models, which is what makes this the portable "
                        + "answer: it does not matter whether the endpoint object is yours alone or shared, "
                        + "because the state is keyed by the connection rather than stored on the handler. "
                        + "The handler stays stateless and injectable, and every callback already receives "
                        + "the session it needs",
                "session.getUserProperties().put(«" + key + "», «" + value + "») для " + client
                        + " — в Spring это session.getAttributes(). Объект Session создаётся контейнером на "
                        + "каждое соединение в ОБЕИХ моделях, и именно это делает такой ответ переносимым: "
                        + "неважно, принадлежит объект эндпоинта только тебе или он общий, потому что "
                        + "состояние привязано к соединению, а не лежит на обработчике. Обработчик остаётся "
                        + "без состояния и пригодным для внедрения зависимостей, а сессию каждый колбэк и "
                        + "так получает",
                List.of("session", "connections"), state());
    }

    // ---------------------------------------------------------------- closing

    /** A clean close: a CLOSE frame each way, then the TCP connection goes down. */
    public void close(String client, int code, String reason) {
        Conn conn = require(client);
        if (!open(conn)) {
            return;
        }
        record(conn, "OUT", "CLOSE", code + " " + reason, false);
        record(conn, "IN", "CLOSE", String.valueOf(code), true);
        framesOut++;
        framesIn++;
        conn.phase = "CLOSED";
        conn.closeCode = code;
        closed++;
        detach(conn);
        Trace.event("CLOSE_HANDSHAKE",
                "A CLOSE frame (opcode 0x8) carrying code " + code + " (" + reason + "), the peer echoes "
                        + "one back, and only then does the TCP connection get its FIN. Closing is a "
                        + "handshake too, so both sides know the socket ended on purpose: 1000 is a normal "
                        + "close, 1001 is \"going away\" (the tab was closed, the server is shutting down), "
                        + "1009 is a message that was too big. "
                        + (model == Model.PER_CONNECTION
                        ? "@OnClose runs, and then the endpoint object for this connection is garbage — "
                        + "along with every field on it"
                        : "afterConnectionClosed runs on the shared handler; the object lives on, so "
                        + "whatever you registered for this connection you must now unregister by hand"),
                "CLOSE-фрейм (opcode 0x8) с кодом " + code + " (" + reason + "), собеседник отвечает таким "
                        + "же, и только после этого TCP-соединение получает свой FIN. Закрытие — тоже "
                        + "рукопожатие, чтобы обе стороны знали, что сокет завершился намеренно: 1000 — "
                        + "нормальное закрытие, 1001 — «ухожу» (вкладку закрыли, сервер выключается), 1009 "
                        + "— сообщение оказалось слишком большим. "
                        + (model == Model.PER_CONNECTION
                        ? "Отрабатывает @OnClose, и после этого объект эндпоинта для этого соединения — "
                        + "мусор вместе со всеми своими полями"
                        : "Отрабатывает afterConnectionClosed на общем обработчике; сам объект живёт "
                        + "дальше, поэтому всё, что ты зарегистрировал для этого соединения, теперь "
                        + "обязан снять руками"),
                List.of("connections", "instances"), state());
    }

    /** The network died: no CLOSE frame, and code 1006 was never on the wire. */
    public void drop(String client) {
        Conn conn = require(client);
        if (!open(conn)) {
            return;
        }
        conn.phase = "CLOSED";
        conn.closeCode = 1006;
        dropped++;
        detach(conn);
        Trace.event("CONNECTION_DROPPED",
                client + " went through a tunnel: the TCP connection is gone with no CLOSE frame, and both "
                        + "sides report 1006. That code is special — it is the one status code that is never "
                        + "sent on the wire, because it means precisely \"the connection ended and nobody "
                        + "said why\". Your server may not notice for a while, which is what heartbeats are "
                        + "for. And when the client comes back it is a NEW connection: a new handshake, a "
                        + "new Session, "
                        + (model == Model.PER_CONNECTION ? "a new endpoint object, " : "")
                        + "empty state, and no Last-Event-ID to resume from — the reconnect-and-resubscribe "
                        + "loop is code you write",
                client + " заехал в тоннель: TCP-соединение исчезло без CLOSE-фрейма, и обе стороны "
                        + "сообщают 1006. Этот код особенный — единственный, который никогда не бывает на "
                        + "проводе, потому что означает ровно «соединение кончилось, и никто не сказал "
                        + "почему». Сервер может не заметить этого какое-то время — как раз для этого нужен "
                        + "heartbeat. А когда клиент вернётся, это будет НОВОЕ соединение: новое "
                        + "рукопожатие, новая Session, "
                        + (model == Model.PER_CONNECTION ? "новый объект эндпоинта, " : "")
                        + "пустое состояние и никакого Last-Event-ID, с которого можно продолжить, — цикл "
                        + "переподключения и переподписки пишешь ты сам",
                List.of("connections", "instances"), state());
    }

    /** Releases whatever the closed connection was holding. */
    private void detach(Conn conn) {
        Instance instance = conn.instance;
        if (instance == null) {
            return;
        }
        instance.serves.remove(conn.id);
        if (model == Model.PER_CONNECTION) {
            instance.alive = false;
        }
    }

    // ---------------------------------------------------------------- reports

    /** Spells out what the frames are actually travelling on. */
    public void transportFacts() {
        Trace.event("TRANSPORT_EXPLAINED",
                "The transport is TCP, and only TCP — " + url() + " means port " + port() + " on one "
                        + "ordinary TCP connection, the same one the GET was sent on. It is never UDP, and "
                        + "the API does not expose a choice: the browser gives you WebSocket over TCP, and "
                        + "the thing you reach for when you genuinely want UDP semantics (a game, live "
                        + "audio, where a late packet is worse than a lost one) is WebRTC. So you inherit "
                        + "TCP's guarantees whole: frames arrive in order, exactly once, none of them "
                        + "corrupt — and you also inherit head-of-line blocking, because a lost segment "
                        + "stalls everything queued behind it. Over HTTP/2 and HTTP/3 the handshake is "
                        + "tunnelled through a stream instead (RFC 8441/9220), and although HTTP/3 rides "
                        + "QUIC over UDP, QUIC re-adds reliable ordered delivery — the guarantees your code "
                        + "is written against never change",
                "Транспорт — TCP, и только TCP: " + url() + " означает порт " + port() + " на одном "
                        + "обычном TCP-соединении, том самом, по которому был отправлен GET. Это никогда не "
                        + "UDP, и API даже не предлагает выбора: браузер даёт тебе WebSocket поверх TCP, а "
                        + "то, за чем идут, когда действительно нужна семантика UDP (игра, живой звук, где "
                        + "опоздавший пакет хуже потерянного), называется WebRTC. Поэтому ты наследуешь "
                        + "гарантии TCP целиком: фреймы приходят по порядку, ровно один раз и неиспорченными "
                        + "— и вместе с ними наследуешь head-of-line blocking, потому что потерянный сегмент "
                        + "задерживает всё, что стоит за ним. В HTTP/2 и HTTP/3 рукопожатие вместо этого "
                        + "туннелируется через поток (RFC 8441/9220), и хотя HTTP/3 едет на QUIC поверх UDP, "
                        + "QUIC возвращает надёжную упорядоченную доставку — гарантии, под которые написан "
                        + "твой код, не меняются",
                List.of("transport"), state());
    }

    /** Prints what this endpoint did and what it is holding. */
    public void report() {
        Trace.event("WEBSOCKET_AUDIT",
                "Endpoint " + url() + " served as " + model + ": handshakes accepted " + handshakes
                        + ", rejected " + rejected + ", sockets still open " + openCount()
                        + ", endpoint objects created " + instancesCreated + ", frames in " + framesIn
                        + ", frames out " + framesOut + ", bytes framed " + bytes
                        + ", state collisions " + clashes + ", wrong-client reads " + leaks
                        + ", frames written to a dead socket " + lost + ", clean closes " + closed
                        + ", abnormal drops " + dropped,
                "Эндпоинт " + url() + " в модели " + model + ": рукопожатий принято " + handshakes
                        + ", отклонено " + rejected + ", сокетов ещё открыто " + openCount()
                        + ", объектов эндпоинта создано " + instancesCreated + ", фреймов внутрь "
                        + framesIn + ", наружу " + framesOut + ", байт в кадрах " + bytes
                        + ", коллизий состояния " + clashes + ", чтений чужого состояния " + leaks
                        + ", фреймов в мёртвый сокет " + lost + ", штатных закрытий " + closed
                        + ", аварийных обрывов " + dropped,
                List.of("stats"), state());
    }

    /**
     * Prices the three real instancing choices against each other — the table
     * the third part of the interview question is asking for.
     */
    public static void compareInstancing() {
        List<Object> rows = new ArrayList<>();
        rows.add(comparisonRow("JSR356_ENDPOINT", 3, true, false, true, "FIELD"));
        rows.add(comparisonRow("JSR356_SINGLETON", 1, false, true, false, "SESSION"));
        rows.add(comparisonRow("SPRING_HANDLER", 1, false, true, false, "SESSION"));

        Trace.event("INSTANCING_COMPARED",
                "Three connections, three ways of being served. A plain @ServerEndpoint POJO gives you "
                        + "three objects — one per connection — so a field is per-client state, but the "
                        + "container built those objects and Spring injected nothing into them. Override "
                        + "ServerEndpointConfig.Configurator.getEndpointInstance() to return a bean and you "
                        + "are down to one object for all three, with injection working and every field now "
                        + "shared. A Spring WebSocketHandler is that same singleton shape by default (and "
                        + "PerConnectionWebSocketHandler is the opt-out that gives you an instance per "
                        + "connection again). The rule that survives all three: never assume — check how "
                        + "your endpoint is created, and keep per-connection state on the Session, where the "
                        + "answer is the same either way",
                "Три соединения и три способа их обслужить. Обычный POJO с @ServerEndpoint даёт три "
                        + "объекта — по одному на соединение, — поэтому поле является состоянием клиента, но "
                        + "эти объекты создал контейнер, и Spring в них ничего не внедрил. Переопредели "
                        + "ServerEndpointConfig.Configurator.getEndpointInstance(), чтобы он возвращал бин, "
                        + "— и на все три соединения останется один объект: внедрение заработает, а каждое "
                        + "поле станет общим. Spring WebSocketHandler по умолчанию имеет ровно эту "
                        + "синглтонную форму (а PerConnectionWebSocketHandler — способ отказаться и снова "
                        + "получить экземпляр на соединение). Правило, которое переживает все три варианта: "
                        + "никогда не предполагай — проверь, как создаётся твой эндпоинт, и держи состояние "
                        + "соединения в Session, где ответ одинаков в любом случае",
                List.of("instances"), comparisonState(rows));
    }

    // ------------------------------------------------------------- internals

    private void announce(String descEn, String descRu) {
        Trace.event("SERVER_READY",
                "Endpoint declared at " + url() + ". " + descEn,
                "Эндпоинт объявлен на " + url() + ". " + descRu,
                List.of("instances", "transport"), state());
    }

    private Conn require(String client) {
        Conn conn = connections.get(client);
        if (conn == null) {
            throw new IllegalStateException(client + " has not connected to " + url() + " yet");
        }
        return conn;
    }

    private static boolean open(Conn conn) {
        return "OPEN".equals(conn.phase);
    }

    private int openCount() {
        int count = 0;
        for (Conn conn : connections.values()) {
            if (open(conn)) {
                count++;
            }
        }
        return count;
    }

    /** Appends a frame and returns how many bytes it occupies on the wire. */
    private int record(Conn conn, String dir, String opcode, String payload, boolean masked) {
        int payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        int size = frameSize(payloadBytes, masked);
        frames.add(new Frame(frames.size() + 1, dir, opcode, conn.id, payload, masked, size));
        bytes += size;
        return size;
    }

    /**
     * RFC 6455 framing: two header bytes, a longer length field once the payload
     * outgrows 7 bits, and a four-byte masking key on client-to-server frames.
     */
    private static int frameSize(int payloadBytes, boolean masked) {
        int header = 2;
        if (payloadBytes > 125 && payloadBytes < 65536) {
            header += 2;
        } else if (payloadBytes >= 65536) {
            header += 8;
        }
        if (masked) {
            header += 4;
        }
        return header + payloadBytes;
    }

    /** base64(SHA-1(key + GUID)) — the real Sec-WebSocket-Accept computation. */
    private static String accept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by RFC 6455", e);
        }
    }

    private static String pad16(String seed) {
        String padded = seed + "................";
        return padded.substring(0, 16);
    }

    private String url() {
        return (tls ? "wss://" : "ws://") + host + path;
    }

    private int port() {
        return tls ? 443 : 80;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("url", url());
        s.put("model", model.name());
        s.put("endpointClass", endpointClass);
        s.put("allowedOrigin", allowedOrigin);

        Map<String, Object> transport = new LinkedHashMap<>();
        transport.put("protocol", "TCP");
        transport.put("port", port());
        transport.put("tls", tls);
        transport.put("sockets", openCount());
        s.put("transport", transport);

        Map<String, Object> handshake = new LinkedHashMap<>();
        handshake.put("client", handshakeClient);
        handshake.put("request", new ArrayList<>(requestLines));
        handshake.put("response", new ArrayList<>(responseLines));
        handshake.put("accepted", handshakeAccepted);
        s.put("handshake", handshake);

        List<Object> conns = new ArrayList<>();
        for (Conn conn : connections.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", conn.id);
            row.put("phase", conn.phase);
            row.put("instance", conn.instance == null ? null : conn.instance.name);
            row.put("sent", conn.sent);
            row.put("received", conn.received);
            row.put("closeCode", conn.closeCode);
            List<Object> session = new ArrayList<>();
            for (Map.Entry<String, String> entry : conn.session.entrySet()) {
                Map<String, Object> attr = new LinkedHashMap<>();
                attr.put("key", entry.getKey());
                attr.put("value", entry.getValue());
                session.add(attr);
            }
            row.put("session", session);
            conns.add(row);
        }
        s.put("connections", conns);

        List<Object> objects = new ArrayList<>();
        for (Instance instance : instances) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", instance.name);
            row.put("alive", instance.alive);
            row.put("serves", new ArrayList<>(instance.serves));
            List<Object> fields = new ArrayList<>();
            for (Map.Entry<String, String> entry : instance.fieldValues.entrySet()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", entry.getKey());
                field.put("value", entry.getValue());
                field.put("owner", instance.fieldOwners.get(entry.getKey()));
                fields.add(field);
            }
            row.put("fields", fields);
            objects.add(row);
        }
        s.put("instances", objects);

        List<Object> wire = new ArrayList<>();
        for (Frame frame : frames) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", frame.seq);
            row.put("dir", frame.dir);
            row.put("opcode", frame.opcode);
            row.put("client", frame.client);
            row.put("payload", frame.payload);
            row.put("masked", frame.masked);
            row.put("bytes", frame.bytes);
            wire.add(row);
        }
        s.put("frames", wire);

        s.put("stats", stats());
        s.put("comparison", new ArrayList<>());
        return s;
    }

    private Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("handshakes", handshakes);
        stats.put("rejected", rejected);
        stats.put("open", openCount());
        stats.put("instances", instancesCreated);
        stats.put("framesIn", framesIn);
        stats.put("framesOut", framesOut);
        stats.put("bytes", bytes);
        stats.put("clashes", clashes);
        stats.put("leaks", leaks);
        stats.put("lost", lost);
        stats.put("closed", closed);
        stats.put("dropped", dropped);
        return stats;
    }

    // ------------------------------------------------------------ comparison

    private static Map<String, Object> comparisonRow(String model, int instancesForThree,
                                                     boolean fieldPerConnection, boolean needsSync,
                                                     boolean containerCreated, String stateLives) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("model", model);
        row.put("instances", instancesForThree);
        row.put("fieldPerConnection", fieldPerConnection);
        row.put("needsSync", needsSync);
        row.put("injection", !containerCreated);
        row.put("stateLives", stateLives);
        return row;
    }

    /** A well-formed state whose only interesting part is the comparison table. */
    private static Map<String, Object> comparisonState(List<Object> rows) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("url", "ws://chat.example.com/ws/chat");
        s.put("model", null);
        s.put("endpointClass", "ChatEndpoint");
        s.put("allowedOrigin", null);

        Map<String, Object> transport = new LinkedHashMap<>();
        transport.put("protocol", "TCP");
        transport.put("port", 80);
        transport.put("tls", false);
        transport.put("sockets", 3);
        s.put("transport", transport);

        Map<String, Object> handshake = new LinkedHashMap<>();
        handshake.put("client", null);
        handshake.put("request", new ArrayList<>());
        handshake.put("response", new ArrayList<>());
        handshake.put("accepted", null);
        s.put("handshake", handshake);

        s.put("connections", new ArrayList<>());
        s.put("instances", new ArrayList<>());
        s.put("frames", new ArrayList<>());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("handshakes", 0);
        stats.put("rejected", 0);
        stats.put("open", 0);
        stats.put("instances", 0);
        stats.put("framesIn", 0);
        stats.put("framesOut", 0);
        stats.put("bytes", 0);
        stats.put("clashes", 0);
        stats.put("leaks", 0);
        stats.put("lost", 0);
        stats.put("closed", 0);
        stats.put("dropped", 0);
        s.put("stats", stats);

        s.put("comparison", rows);
        return s;
    }
}
