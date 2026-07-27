package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of everything that happens between typing a host
 * name into the address bar and looking at a rendered page.
 *
 * <p>The interview question ("you type google.com — what happens?") is really a
 * question about layers, and this model walks them in order, one method per
 * layer:
 *
 * <ol>
 *   <li>{@link #type(String)} — what you typed is not a URL yet. The browser
 *       decides search-vs-navigation, picks a scheme, fills in the default port
 *       and the {@code /} path, and consults the HSTS list.</li>
 *   <li>{@link #resolve()} — a name is not an address. Three local caches, then
 *       a recursive resolver walking root → TLD → authoritative, and only then
 *       an A record with a TTL.</li>
 *   <li>{@link #connect()} — an address is not a connection. SYN, SYN-ACK, ACK:
 *       one round trip before a single byte of your request may be sent.</li>
 *   <li>{@link #secure()} — a connection is not a secure connection. ClientHello
 *       with SNI and ALPN, the certificate chain, the key exchange: one more
 *       round trip on TLS 1.3, two on TLS 1.2.</li>
 *   <li>{@link #request()} / {@link #respond()} — only now does the request line
 *       exist on the wire, and the first response byte is one more round trip
 *       plus whatever the server spends thinking.</li>
 *   <li>{@link #parseHtml(String...)}, {@link #fetch(String)}, {@link #render()}
 *       — the document is not the page: the HTML references subresources, and
 *       "loaded" means those finished too.</li>
 * </ol>
 *
 * <p>Nothing is timed or random. Every hop costs a fixed number of milliseconds
 * and a fixed number of round trips, and addresses are derived from the host
 * name, so every run produces exactly the same trace. The same browser object
 * keeps its DNS cache, its open connection and its HTTP cache between
 * navigations, which is what makes the second visit look so different from the
 * first. Every step emits a bilingual {@link Trace} event and the class stays
 * dependency-free.
 */
public class VisualPageLoad {

    /** The pipeline, in the order a navigation goes through it. */
    private static final List<String> STAGES =
            List.of("URL", "DNS", "TCP", "TLS", "HTTP", "CONTENT", "RENDER");

    /** Browser → OS → configured recursive resolver: local hops, but not free. */
    private static final int DNS_STUB_MS = 5;

    /** One resolver hop: to a root, a TLD or an authoritative name server. */
    private static final int DNS_HOP_MS = 25;

    /** One browser ↔ server round trip on the open connection. */
    private static final int RTT_MS = 30;

    /** What the server spends producing the HTML after the request arrives. */
    private static final int SERVER_THINK_MS = 40;

    /** Parsing the received HTML far enough to find what else it needs. */
    private static final int PARSE_MS = 20;

    /** Layout plus paint, once nothing render-blocking is outstanding. */
    private static final int PAINT_MS = 15;

    private static final int DOCUMENT_BYTES = 18_000;

    /** One A record, exactly as a cache would hold it. */
    private static final class Answer {

        private final String ip;
        private final int ttl;

        private Answer(String ip, int ttl) {
            this.ip = ip;
            this.ttl = ttl;
        }
    }

    /** One place the question was asked, and what came back. */
    private static final class DnsStep {

        private final String from;
        private final String question;
        private final String result;
        private final String detail;
        private final int ms;

        private DnsStep(String from, String question, String result, String detail, int ms) {
            this.from = from;
            this.question = question;
            this.result = result;
            this.detail = detail;
            this.ms = ms;
        }
    }

    /** The one TCP connection everything after DNS happens on. */
    private static final class Conn {

        private final String host;
        private final int port;
        private final String ip;
        private boolean secure;
        private String tls;
        private String alpn = "http/1.1";
        private int requests;
        private boolean open = true;

        private Conn(String host, int port, String ip) {
            this.host = host;
            this.port = port;
            this.ip = ip;
        }
    }

    /** One message on that connection, in either direction. */
    private static final class Wire {

        private final int seq;
        private final String dir;
        private final String layer;
        private final String label;
        private final String detail;
        private final int bytes;

        private Wire(int seq, String dir, String layer, String label, String detail, int bytes) {
            this.seq = seq;
            this.dir = dir;
            this.layer = layer;
            this.label = label;
            this.detail = detail;
            this.bytes = bytes;
        }
    }

    /** One thing the page is made of, and where it came from. */
    private static final class Res {

        private final String name;
        private final String kind;
        private final String source;
        private final int ms;
        private final int bytes;

        private Res(String name, String kind, String source, int ms, int bytes) {
            this.name = name;
            this.kind = kind;
            this.source = source;
            this.ms = ms;
            this.bytes = bytes;
        }
    }

    /** One stage of the pipeline and what it cost this navigation. */
    private static final class Stage {

        private String status = "PENDING";
        private int ms;
        private String detail = "";
    }

    // ------------------------------------------------------------------ state

    private final Set<String> hstsHosts = new LinkedHashSet<>();
    private final Map<String, Answer> dnsCache = new LinkedHashMap<>();
    private final Set<String> httpCache = new LinkedHashSet<>();
    private final Map<String, Stage> stages = new LinkedHashMap<>();

    private String tlsVersion = "TLS 1.3";

    private int visit;
    private String typed = "";
    private String scheme = "https";
    private String host = "";
    private int port = 443;
    private String path = "/";
    private boolean urlParsed;

    private Answer answer;
    private boolean answerFromCache;
    private Conn conn;

    private final List<DnsStep> dnsSteps = new ArrayList<>();
    private final List<Wire> wire = new ArrayList<>();
    private final List<Res> resources = new ArrayList<>();
    private final List<String> pending = new ArrayList<>();
    private final Map<String, Integer> milestones = new LinkedHashMap<>();

    private int ms;
    private int roundTrips;
    private int dnsQueries;
    private int bytes;
    private int connectionsOpened;
    private int requests;
    private int cacheHits;
    private int redirects;

    private VisualPageLoad() {
        resetStages();
    }

    // -------------------------------------------------------------- factories

    /** A browser that has just been started: every cache empty, nothing open. */
    public static VisualPageLoad browser() {
        return new VisualPageLoad();
    }

    /**
     * Marks a host as known-HTTPS-only, either because it is on the browser's
     * built-in preload list or because it sent {@code Strict-Transport-Security}
     * on an earlier visit.
     */
    public VisualPageLoad hstsPreloaded(String host) {
        hstsHosts.add(host);
        return this;
    }

    /** Pins the TLS version the handshake will use — the round-trip count differs. */
    public VisualPageLoad usingTls(String version) {
        tlsVersion = version;
        return this;
    }

    // -------------------------------------------------------------- the steps

    /** What you typed, turned into a URL — the step everybody skips. */
    public VisualPageLoad type(String input) {
        visit++;
        typed = input;
        startNavigation();

        String typedScheme = parseUrl(input);
        boolean typedPlain = "http".equals(typedScheme);
        boolean known = hstsHosts.contains(host);

        finish("URL", 0, "DONE", scheme + "://" + host + ":" + port + path);
        Trace.event("URL_TYPED",
                "\"" + input + "\" is not a URL yet, and the browser's first job is to decide what it even "
                        + "is: a string with no dots and a space is a search, this one is a host name. Then it "
                        + "fills in everything you did not type — the scheme, the port ("
                        + port + " is the default for " + scheme + ", which is why you never see it) and the "
                        + "path, which is \"" + path + "\" because a URL always has one. "
                        + (typedScheme == null
                        ? "You typed no scheme at all, so the browser picks one: modern browsers try https "
                        + "first and fall back only if that fails"
                        : "You typed the scheme yourself, so it is used as written unless HSTS overrules it")
                        + ". Nothing has left the machine yet — no packet, no lookup, nothing",
                "«" + input + "» — это ещё не URL, и первая задача браузера — понять, что это вообще такое: "
                        + "строка без точек и с пробелом — поисковый запрос, а эта — имя хоста. Затем он "
                        + "дописывает всё, что вы не набрали: схему, порт (" + port + " — порт по умолчанию "
                        + "для " + scheme + ", поэтому вы его никогда не видите) и путь, который равен «"
                        + path + "», потому что путь у URL есть всегда. "
                        + (typedScheme == null
                        ? "Схему вы не набрали вовсе, поэтому её выбирает браузер: современные браузеры "
                        + "сначала пробуют https и откатываются, только если не вышло"
                        : "Схему вы указали сами, поэтому она используется как есть, если её не переопределит "
                        + "HSTS")
                        + ". Из машины пока не ушло ничего — ни пакета, ни запроса, ничего",
                List.of("url"), state());

        if (known) {
            scheme = "https";
            port = typedPlain ? 443 : port;
            finish("URL", 0, "DONE", scheme + "://" + host + ":" + port + path);
            Trace.event("HSTS_UPGRADED",
                    host + " is on the browser's HSTS list — either preloaded into the binary or remembered "
                            + "from a Strict-Transport-Security header on an earlier visit — so https is not a "
                            + "guess here, it is a rule with no fallback"
                            + (typedPlain
                            ? ", and the http:// you typed was rewritten before any socket was opened"
                            : ", and a plain http request to this host will never be attempted")
                            + ". This is a local rewrite: zero round trips, zero packets, and no chance for "
                            + "anyone on the path to see or touch a plaintext request. Compare it with the "
                            + "alternative — send the http request and let the server answer 301 — which costs "
                            + "a full DNS + TCP + request cycle AND puts one unencrypted request on a network "
                            + "where somebody can rewrite it. That first plain request is the hole HSTS closes, "
                            + "and the preload list closes it even for the very first visit",
                    host + " есть в HSTS-списке браузера — либо в предзагруженном списке внутри самого "
                            + "браузера, либо запомненный из заголовка Strict-Transport-Security в прошлый "
                            + "визит, — поэтому https здесь не догадка, а правило без права на откат"
                            + (typedPlain
                            ? ", и набранный вами http:// был переписан ещё до открытия сокета"
                            : ", и открытый http-запрос к этому хосту не будет предпринят никогда")
                            + ". Это локальная замена: ноль round trip, ноль пакетов и ни одного шанса для "
                            + "того, кто стоит по пути, увидеть или подменить незашифрованный запрос. "
                            + "Сравните с альтернативой — отправить http-запрос и получить в ответ 301: она "
                            + "стоит полного цикла DNS + TCP + запрос И кладёт один незашифрованный запрос в "
                            + "сеть, где его могут переписать. Именно эту дыру закрывает HSTS, а "
                            + "preload-список закрывает её даже для самого первого визита",
                    List.of("url"), state());
        }
        return this;
    }

    /** A name is not an address: DNS, from the three local caches outwards. */
    public VisualPageLoad resolve() {
        requireUrl();
        Answer cached = dnsCache.get(host);
        if (cached != null) {
            answer = cached;
            answerFromCache = true;
            dnsSteps.add(new DnsStep("BROWSER_CACHE", host + " A", "HIT",
                    cached.ip + " (TTL " + cached.ttl + ")", 0));
            finish("DNS", 0, "CACHED", cached.ip);
            Trace.event("DNS_CACHE_HIT",
                    "The browser's own DNS cache still holds " + host + " → " + cached.ip + ", so this whole "
                            + "layer costs zero round trips and zero milliseconds. Every level of the system "
                            + "caches the answer for its TTL (" + cached.ttl + " seconds here): the browser, "
                            + "the OS stub resolver and the recursive resolver upstream. That is also the "
                            + "reason a DNS change does not take effect everywhere at once — until the TTL "
                            + "expires, caches keep handing out the old address, which is why you lower the "
                            + "TTL BEFORE a migration rather than during it",
                    "Собственный DNS-кэш браузера всё ещё держит " + host + " → " + cached.ip + ", поэтому "
                            + "весь этот слой стоит ноль round trip и ноль миллисекунд. Ответ кэширует каждый "
                            + "уровень системы на время TTL (здесь " + cached.ttl + " секунд): браузер, "
                            + "системный stub-резолвер и вышестоящий рекурсивный резолвер. Поэтому же смена "
                            + "DNS-записи не вступает в силу везде одновременно: пока TTL не истёк, кэши "
                            + "продолжают отдавать старый адрес — и поэтому TTL понижают ДО переезда, а не "
                            + "во время него",
                    List.of("dns"), state());
            return this;
        }

        answerFromCache = false;
        dnsSteps.add(new DnsStep("BROWSER_CACHE", host + " A", "MISS", "not cached", 0));
        dnsSteps.add(new DnsStep("OS_CACHE", host + " A", "MISS", "not cached", 0));
        dnsSteps.add(new DnsStep("HOSTS_FILE", host, "MISS", "no entry", 0));
        dnsSteps.add(new DnsStep("RESOLVER", host + " A", "MISS", "recursion requested", DNS_STUB_MS));
        spend(DNS_STUB_MS);
        roundTrips++;
        bytes += 68;
        Trace.event("DNS_CACHE_MISS",
                "Before a single DNS packet leaves the machine, three local places are checked and all three "
                        + "miss: the browser's own cache, the OS stub resolver's cache and the hosts file (the "
                        + "one that still wins over DNS, which is why an old line in it can make one host "
                        + "unreachable for years). Only then does the stub send one UDP datagram to the "
                        + "configured recursive resolver — your router, your ISP's, 8.8.8.8, 1.1.1.1 — asking "
                        + "it to do the walking. Your machine does not query the root servers itself; it asks "
                        + "one resolver a recursive question and waits for a final answer",
                "Прежде чем из машины уйдёт хоть один DNS-пакет, проверяются три локальных места, и все три "
                        + "промахиваются: собственный кэш браузера, кэш системного stub-резолвера и файл hosts "
                        + "(тот самый, который до сих пор побеждает DNS, — поэтому старая строчка в нём может "
                        + "годами делать один хост недоступным). И только тогда stub отправляет одну "
                        + "UDP-датаграмму настроенному рекурсивному резолверу — роутеру, провайдерскому, "
                        + "8.8.8.8, 1.1.1.1 — с просьбой сходить за ответом. Ваша машина не опрашивает "
                        + "корневые серверы сама: она задаёт одному резолверу рекурсивный вопрос и ждёт "
                        + "готового ответа",
                List.of("dns"), state());

        String tld = tld(host);
        String registrable = registrable(host);
        askDns("ROOT", "a.root-servers.net", "REFERRAL",
                "ask " + tldServer(tld) + " about ." + tld,
                "The resolver starts at the top, because the root is the only address it knows by heart (13 "
                        + "named root servers, shipped with every resolver as a hints file). The root does not "
                        + "know " + host + " and never will — it knows who runs ." + tld + " and answers with "
                        + "a referral. Notice what this proves about DNS: it is not one database anybody owns, "
                        + "it is a tree of delegations, and every lookup that is not cached walks down it",
                "Резолвер начинает сверху, потому что корень — единственный адрес, который он знает наизусть "
                        + "(13 именованных корневых серверов, поставляемых с каждым резолвером в виде "
                        + "hints-файла). Корень не знает " + host + " и никогда не узнает: он знает, кто "
                        + "заведует зоной ." + tld + ", и отвечает отсылкой. Обратите внимание, что это "
                        + "доказывает про DNS: это не одна база, которой кто-то владеет, а дерево делегаций, "
                        + "и каждый некэшированный запрос спускается по нему");
        askDns("TLD", tldServer(tld), "REFERRAL",
                "ask ns1." + registrable + " about " + registrable,
                "The ." + tld + " servers do not know the address either — they know which name servers the "
                        + "owner of " + registrable + " declared, and hand back another referral. This is the "
                        + "step that makes DNS delegation real: whoever controls those NS records controls "
                        + "every name under that domain, which is why registrar access is treated as "
                        + "production access",
                "Серверы зоны ." + tld + " адреса тоже не знают — они знают, какие серверы имён объявил "
                        + "владелец " + registrable + ", и отдают ещё одну отсылку. Именно этот шаг делает "
                        + "делегирование в DNS настоящим: кто управляет этими NS-записями, тот управляет "
                        + "всеми именами внутри домена, — поэтому доступ к регистратору считают доступом к "
                        + "продакшену");
        askDns("AUTHORITATIVE", "ns1." + registrable, "ANSWER",
                host + " A " + ip(host) + " (TTL 300)",
                "The authoritative server for " + registrable + " is the one that actually holds the zone "
                        + "file, and it answers with the A record. Everyone on the way back caches it for its "
                        + "TTL, which is why the next visitor from the same network usually skips all of this. "
                        + "The whole exchange was UDP on port 53 (with fallback to TCP for large answers), and "
                        + "unless DoH or DoT is in use it was plaintext — the one part of an https:// page "
                        + "load that an observer can read in full",
                "Авторитативный сервер зоны " + registrable + " — тот, у кого действительно лежит файл "
                        + "зоны, — отвечает A-записью. Все по обратному пути кэшируют её на время TTL, "
                        + "поэтому следующий посетитель из той же сети обычно пропускает всё это целиком. "
                        + "Весь обмен шёл по UDP на порту 53 (с откатом на TCP для больших ответов), и, если "
                        + "не включены DoH или DoT, шёл открытым текстом — это единственная часть загрузки "
                        + "https-страницы, которую наблюдатель читает целиком");

        answer = new Answer(ip(host), 300);
        dnsCache.put(host, answer);
        finish("DNS", DNS_STUB_MS + 3 * DNS_HOP_MS, "DONE", answer.ip);
        Trace.event("DNS_RESOLVED",
                host + " is " + answer.ip + ", cached for " + answer.ttl + " seconds, and it took "
                        + (DNS_STUB_MS + 3 * DNS_HOP_MS) + " ms and 4 round trips to learn one 4-byte "
                        + "number. (The addresses in this model come from the documentation range "
                        + "198.51.100.0/24 so every run is identical.) Three things people get wrong here. A "
                        + "real answer is usually several A records plus AAAA records, and the browser races "
                        + "IPv6 against IPv4 — that is Happy Eyeballs. DNS gives you an address, not a "
                        + "connection: nothing is open yet. And DNS is where the biggest single lever lives, "
                        + "because a CDN answers the same name with a different address per region",
                host + " — это " + answer.ip + ", закэшировано на " + answer.ttl + " секунд, и чтобы узнать "
                        + "одно четырёхбайтное число, ушло " + (DNS_STUB_MS + 3 * DNS_HOP_MS) + " мс и четыре "
                        + "round trip. (Адреса в этой модели берутся из документационного диапазона "
                        + "198.51.100.0/24, чтобы каждый прогон был одинаковым.) Здесь ошибаются в трёх "
                        + "вещах. Настоящий ответ — это обычно несколько A-записей плюс AAAA-записи, и "
                        + "браузер устраивает гонку IPv6 против IPv4 — это Happy Eyeballs. DNS даёт адрес, а "
                        + "не соединение: пока не открыто ничего. И именно в DNS находится самый сильный "
                        + "рычаг, потому что CDN отвечает на одно и то же имя разными адресами в разных "
                        + "регионах",
                List.of("dns"), state());
        return this;
    }

    /** An address is not a connection: SYN, SYN-ACK, ACK. */
    public VisualPageLoad connect() {
        requireUrl();
        if (answer == null) {
            throw new IllegalStateException("resolve() first — you cannot connect to a name");
        }
        if (conn != null && conn.open && conn.host.equals(host) && conn.port == port
                && conn.secure == "https".equals(scheme)) {
            finish("TCP", 0, "REUSED", conn.ip + ":" + conn.port);
            if (conn.secure) {
                finish("TLS", 0, "REUSED", conn.tls + " · " + conn.alpn);
            }
            Trace.event("TCP_REUSED",
                    "There is already an open connection to " + conn.ip + ":" + conn.port + ", so there is "
                            + "no handshake at all: no SYN, no ClientHello, no certificate check — zero round "
                            + "trips and zero milliseconds before the request can be written. This is what "
                            + "Connection: keep-alive bought in HTTP/1.1 and what HTTP/2 made the norm, and it "
                            + "is why the first request to an origin is dramatically more expensive than the "
                            + "next hundred. It is also why a load balancer that closes idle connections, or a "
                            + "client that opens a fresh connection per call, quietly pays for a full "
                            + "TCP + TLS setup over and over",
                    "К " + conn.ip + ":" + conn.port + " уже есть открытое соединение, поэтому рукопожатия "
                            + "нет вовсе: ни SYN, ни ClientHello, ни проверки сертификата — ноль round trip и "
                            + "ноль миллисекунд до момента, когда можно писать запрос. Именно это давал "
                            + "Connection: keep-alive в HTTP/1.1 и что HTTP/2 сделал нормой, и именно поэтому "
                            + "первый запрос к origin намного дороже следующей сотни. И поэтому же "
                            + "балансировщик, который рвёт простаивающие соединения, или клиент, который "
                            + "открывает по соединению на вызов, незаметно платят за полную настройку "
                            + "TCP + TLS снова и снова",
                    List.of("tcp", "connection"), state());
            return this;
        }

        conn = new Conn(host, port, answer.ip);
        connectionsOpened++;
        record("OUT", "TCP", "SYN", "seq=0, win=64240, MSS=1460", 60);
        record("IN", "TCP", "SYN-ACK", "seq=0, ack=1", 60);
        record("OUT", "TCP", "ACK", "ack=1 — the connection is established", 52);
        spend(RTT_MS);
        roundTrips++;
        finish("TCP", RTT_MS, "DONE", conn.ip + ":" + conn.port);
        Trace.event("TCP_HANDSHAKE",
                "SYN out, SYN-ACK back, ACK out: the three-way handshake, one full round trip ("
                        + RTT_MS + " ms here) spent before a single byte of your request may be sent. Both "
                        + "sides pick a starting sequence number and agree on window sizes, and from this "
                        + "moment the kernel on each end has a socket with buffers — that is what \"a "
                        + "connection\" physically is. Two consequences worth saying out loud: the handshake "
                        + "costs one RTT no matter how small the request is, so distance alone sets a floor on "
                        + "your latency, and the connection is to " + conn.ip + ":" + conn.port + ", an "
                        + "address and a port — the host name is not part of TCP at all",
                "SYN туда, SYN-ACK обратно, ACK туда: трёхстороннее рукопожатие, целый round trip ("
                        + RTT_MS + " мс здесь), потраченный до того, как можно отправить хоть один байт "
                        + "запроса. Обе стороны выбирают начальные номера последовательности и "
                        + "договариваются о размерах окна, и с этого момента у ядра на каждом конце есть "
                        + "сокет с буферами — вот что такое «соединение» физически. Два следствия, которые "
                        + "стоит проговорить: рукопожатие стоит один RTT независимо от размера запроса, "
                        + "поэтому одно только расстояние задаёт нижнюю границу задержки, — и соединение "
                        + "устанавливается к " + conn.ip + ":" + conn.port + ", то есть к адресу и порту: "
                        + "имени хоста в TCP нет вообще",
                List.of("tcp", "connection", "wire"), state());
        return this;
    }

    /** A connection is not a secure connection: the TLS handshake. */
    public VisualPageLoad secure() {
        requireConn();
        if (!"https".equals(scheme)) {
            throw new IllegalStateException("secure() makes no sense on " + scheme + "://");
        }
        if (conn.secure) {
            // This connection was negotiated once and stays encrypted; connect()
            // already reported that it was reused.
            return this;
        }
        boolean tls13 = tlsVersion.contains("1.3");
        int cost = tls13 ? RTT_MS : 2 * RTT_MS;
        record("OUT", "TLS", "ClientHello",
                "SNI=" + host + ", ALPN=[h2, http/1.1], versions=[TLS 1.3, TLS 1.2], key_share", 512);
        record("IN", "TLS", "ServerHello + Certificate",
                "chosen " + tlsVersion + ", ALPN=h2, cert chain for " + host, 3400);
        if (!tls13) {
            record("OUT", "TLS", "ClientKeyExchange", "one extra round trip — TLS 1.2 needs two", 300);
        }
        record("OUT", "TLS", "Finished", "encrypted from here on", 80);
        spend(cost);
        roundTrips += tls13 ? 1 : 2;
        conn.secure = true;
        conn.tls = tlsVersion;
        conn.alpn = "h2";
        Trace.event("TLS_HANDSHAKE",
                "ClientHello goes out carrying three things worth naming: SNI, the host name in plaintext, "
                        + "which is how one IP can serve thousands of certificates (and the one field an "
                        + "observer can still read); ALPN, the list of protocols the browser speaks, which is "
                        + "how HTTP/2 gets negotiated without an extra round trip; and the versions and key "
                        + "material it supports. The server picks " + tlsVersion + ", sends its certificate "
                        + "chain and its own key share. " + (tls13
                        ? "TLS 1.3 finishes in one round trip because the client guesses the key exchange in "
                        + "the very first message"
                        : "TLS 1.2 needs two round trips, because the key exchange only starts after the "
                        + "server has spoken") + " — " + cost + " ms here",
                "Наружу уходит ClientHello, и в нём три вещи, которые стоит назвать: SNI — имя хоста "
                        + "открытым текстом, благодаря которому один IP обслуживает тысячи сертификатов (и "
                        + "единственное поле, которое наблюдатель по-прежнему читает); ALPN — список "
                        + "протоколов, на которых говорит браузер, благодаря которому HTTP/2 согласуется без "
                        + "лишнего round trip; и поддерживаемые версии с ключевым материалом. Сервер "
                        + "выбирает " + tlsVersion + ", присылает цепочку сертификатов и свою часть ключа. "
                        + (tls13
                        ? "TLS 1.3 укладывается в один round trip, потому что клиент угадывает обмен ключами "
                        + "уже в первом сообщении"
                        : "TLS 1.2 требует двух round trip, потому что обмен ключами начинается только после "
                        + "того, как заговорит сервер") + " — здесь это " + cost + " мс",
                List.of("tls", "connection", "wire"), state());

        finish("TLS", cost, "DONE", tlsVersion + " · h2");
        Trace.event("TLS_ESTABLISHED",
                "The browser verified the chain up to a root it already trusts, checked the dates and checked "
                        + "that the certificate actually covers " + host + " — a valid certificate for the "
                        + "wrong name fails exactly here, and that check is the whole reason the padlock means "
                        + "anything. Both sides now hold the same symmetric session keys, so everything from "
                        + "here — request line, headers, cookies, body — is encrypted. An observer still sees "
                        + "the IP, the SNI host name and the size and timing of what you transfer; the "
                        + "certificate proves who you are talking to, not that the site is honest. ALPN chose "
                        + "h2, so the requests that follow are multiplexed on this one connection",
                "Браузер проверил цепочку до корня, которому уже доверяет, проверил даты и проверил, что "
                        + "сертификат действительно покрывает " + host + ": действительный сертификат на "
                        + "чужое имя падает ровно здесь, и именно эта проверка придаёт смысл замочку. Теперь "
                        + "у обеих сторон одинаковые симметричные сеансовые ключи, поэтому всё дальнейшее — "
                        + "строка запроса, заголовки, куки, тело — шифруется. Наблюдатель по-прежнему видит "
                        + "IP, имя хоста в SNI, а также размеры и тайминги передачи; сертификат доказывает, с "
                        + "кем вы говорите, а не то, что сайт честный. ALPN выбрал h2, поэтому следующие "
                        + "запросы мультиплексируются в этом одном соединении",
                List.of("tls", "connection"), state());
        return this;
    }

    /** Only now does an HTTP request exist on the wire. */
    public VisualPageLoad request() {
        requireConn();
        if ("https".equals(scheme) && !conn.secure) {
            throw new IllegalStateException("secure() first — nothing may be sent in the clear on https://");
        }
        conn.requests++;
        requests++;
        String line = "h2".equals(conn.alpn)
                ? ":method GET · :authority " + host + " · :path " + path + " · :scheme " + scheme
                : "GET " + path + " HTTP/1.1 · Host: " + host;
        record("OUT", "HTTP", "GET " + path, line + " · accept-encoding: gzip, br · cookie: SID=…", 480);
        Trace.event("HTTP_REQUEST",
                "Now — and only now, after a name lookup, a TCP handshake and a TLS handshake — does the "
                        + "request you actually wanted exist on the wire: " + line + ". It is a few hundred "
                        + "bytes of text, it is one of "
                        + (requests == 1 ? "many that this page will need" : "several on this connection")
                        + ", and it is invisible to anyone watching because it travels inside the TLS "
                        + "session. Everything the server will use to answer is in these headers: the path, "
                        + "the Host (which is how one server picks between virtual hosts), the cookies, the "
                        + "accepted encodings and the cache validators",
                "Теперь — и только теперь, после разрешения имени, рукопожатия TCP и рукопожатия TLS — на "
                        + "проводе появляется тот самый запрос, который вы хотели: " + line + ". Это "
                        + "несколько сотен байт текста, это один из "
                        + (requests == 1 ? "многих, которые понадобятся странице" : "нескольких в этом "
                        + "соединении") + ", и он невидим для наблюдателя, потому что едет внутри "
                        + "TLS-сессии. Всё, чем сервер будет пользоваться при ответе, лежит в этих "
                        + "заголовках: путь, Host (по которому один сервер выбирает виртуальный хост), куки, "
                        + "принимаемые кодировки и валидаторы кэша",
                List.of("http", "wire"), state());
        return this;
    }

    /** The response: status line, headers, and the first byte at last. */
    public VisualPageLoad respond() {
        requireConn();
        int cost = RTT_MS + SERVER_THINK_MS;
        spend(cost);
        roundTrips++;
        milestones.put("TTFB", ms);
        record("IN", "HTTP", "200 OK",
                "content-type: text/html · content-encoding: gzip · cache-control: max-age=300", 400);
        record("IN", "HTTP", "HTML body", DOCUMENT_BYTES + " bytes of markup", DOCUMENT_BYTES);
        resources.add(new Res(path, "DOCUMENT", "NETWORK", cost, DOCUMENT_BYTES));
        httpCache.add(path);
        finish("HTTP", cost, "DONE", "200 OK · TTFB " + ms + " ms");
        Trace.event("HTTP_RESPONSE",
                "200 OK. The first byte arrived " + ms + " ms after you pressed Enter, and that number — "
                        + "TTFB — is one round trip plus however long the server needed to build the page ("
                        + SERVER_THINK_MS + " ms here) on top of everything the earlier layers already spent. "
                        + "The headers matter as much as the body: content-type decides how the bytes are "
                        + "interpreted, content-encoding says they arrived compressed, and cache-control is "
                        + "the server telling the browser whether the next visit may skip this request "
                        + "entirely. What you have now is an HTML document — not a page. Nothing is on screen",
                "200 OK. Первый байт пришёл через " + ms + " мс после нажатия Enter, и это число — TTFB — "
                        + "складывается из одного round trip и того времени, которое сервер потратил на "
                        + "сборку страницы (" + SERVER_THINK_MS + " мс здесь), поверх всего, что уже "
                        + "потратили предыдущие слои. Заголовки здесь важны не меньше тела: content-type "
                        + "решает, как трактовать байты, content-encoding говорит, что они пришли сжатыми, а "
                        + "cache-control — это сервер, сообщающий браузеру, можно ли в следующий раз "
                        + "пропустить этот запрос целиком. То, что у вас сейчас есть, — HTML-документ, а не "
                        + "страница. На экране пока нет ничего",
                List.of("http", "wire"), state());
        return this;
    }

    /**
     * The server answers 3xx instead: the browser starts the whole pipeline
     * again at the new URL, and most of what was just paid for is thrown away.
     */
    public VisualPageLoad redirect(String location) {
        requireConn();
        int cost = RTT_MS + SERVER_THINK_MS;
        spend(cost);
        roundTrips++;
        redirects++;
        record("IN", "HTTP", "301 Moved Permanently", "location: " + location, 300);
        finish("HTTP", cost, "DONE", "301 → " + location);

        String from = scheme + "://" + host + ":" + port + path;
        String oldHost = host;
        boolean wasSecure = conn.secure;
        int spentSoFar = ms;
        int tripsSoFar = roundTrips;

        // Everything below the URL starts over; the elapsed cost does not.
        parseUrl(location);
        typed = location;
        answer = null;
        answerFromCache = false;
        dnsSteps.clear();
        conn.open = false;
        conn = null;
        for (String stage : List.of("DNS", "TCP", "TLS", "HTTP", "CONTENT", "RENDER")) {
            stages.put(stage, new Stage());
        }
        finish("URL", 0, "DONE", scheme + "://" + host + ":" + port + path);
        boolean sameHost = oldHost.equals(host);

        Trace.event("HTTP_REDIRECT",
                "301 Moved Permanently: the page does not live at " + from + ", it lives at " + location
                        + ". Look at what that just cost — the DNS lookup, the TCP handshake"
                        + (wasSecure ? ", the TLS handshake" : "") + " and the request, " + spentSoFar
                        + " ms and " + tripsSoFar + " round trip(s), all spent to receive a one-line header "
                        + "saying \"start over\". The connection is closed because the new URL needs a "
                        + "different port, and the pipeline restarts from DNS. "
                        + (sameHost
                        ? "The DNS answer is the one thing that survives, because the host name did not change"
                        : "Even the DNS answer is useless, because the host name changed too")
                        + ". This is why an http:// → https:// redirect is worth deleting with HSTS, and why "
                        + "a chain of two or three redirects is a genuinely expensive mistake on a mobile "
                        + "network where one round trip is not 30 ms but 300",
                "301 Moved Permanently: страница живёт не по адресу " + from + ", а по " + location + ". "
                        + "Посмотрите, во что это только что обошлось: разрешение имени, рукопожатие TCP"
                        + (wasSecure ? ", рукопожатие TLS" : "") + " и запрос — " + spentSoFar + " мс и "
                        + tripsSoFar + " round trip, — и всё ради однострочного заголовка «начни сначала». "
                        + "Соединение закрывается, потому что новому URL нужен другой порт, и конвейер "
                        + "стартует заново с DNS. "
                        + (sameHost
                        ? "Единственное, что уцелело, — DNS-ответ, потому что имя хоста не изменилось"
                        : "Бесполезен даже DNS-ответ, потому что имя хоста тоже изменилось")
                        + ". Поэтому редирект http:// → https:// стоит убирать через HSTS, а цепочка из "
                        + "двух-трёх редиректов — по-настоящему дорогая ошибка в мобильной сети, где один "
                        + "round trip не 30 мс, а 300",
                List.of("http", "url"), state());
        return this;
    }

    /** The document is not the page: the parser finds what else is needed. */
    public VisualPageLoad parseHtml(String... refs) {
        requireUrl();
        spend(PARSE_MS);
        pending.clear();
        for (String ref : refs) {
            pending.add(ref);
        }
        milestones.put("DOM_INTERACTIVE", ms);
        finish("CONTENT", PARSE_MS, "PENDING", pending.size() + " subresource(s) queued");
        Trace.event("HTML_PARSED",
                "The parser walks the markup building the DOM and hits " + pending.size() + " references it "
                        + "cannot render without: " + String.join(", ", pending) + ". Each one is a separate "
                        + "HTTP request, and a stylesheet or a synchronous script blocks rendering, so the "
                        + "page will not appear until they arrive. This is the half of the answer people "
                        + "forget: \"until the page loads\" is not one request/response, it is one document "
                        + "plus everything the document asks for — on a real site that is dozens of requests, "
                        + "often to other origins, each of which may need its own DNS lookup and its own "
                        + "TCP + TLS handshake",
                "Парсер идёт по разметке, строит DOM и натыкается на ссылки, без которых отрисовать нечего "
                        + "(" + pending.size() + " шт.): " + String.join(", ", pending) + ". Каждая — "
                        + "отдельный HTTP-запрос, а таблица стилей или синхронный скрипт блокируют "
                        + "отрисовку, поэтому страница не появится, пока они не придут. Это та половина "
                        + "ответа, о которой забывают: «пока страница не загрузится» — это не один запрос и "
                        + "ответ, а документ плюс всё, что документ просит; на реальном сайте это десятки "
                        + "запросов, часто к другим origin, каждому из которых может понадобиться свой "
                        + "DNS-запрос и своё рукопожатие TCP + TLS",
                List.of("content"), state());
        return this;
    }

    /** One subresource — from the network, or from the cache if it is still there. */
    public VisualPageLoad fetch(String ref) {
        requireUrl();
        pending.remove(ref);
        if (httpCache.contains(ref)) {
            cacheHits++;
            resources.add(new Res(ref, kind(ref), "DISK_CACHE", 0, size(ref)));
            finish("CONTENT", 0, "CACHED", cacheHits + " from cache");
            Trace.event("RESOURCE_FROM_CACHE",
                    ref + " is still in the HTTP cache and its max-age has not expired, so the browser does "
                            + "not ask at all: no request, no round trip, no server involved — the fastest "
                            + "request is the one that never happens. Note the difference from a conditional "
                            + "request: if the entry had only an ETag or a Last-Modified date, the browser "
                            + "would still pay one round trip to hear 304 Not Modified. Getting cache-control "
                            + "right on static assets is the single cheapest performance change most sites "
                            + "never make",
                    ref + " всё ещё лежит в HTTP-кэше, и его max-age не истёк, поэтому браузер вообще ничего "
                            + "не спрашивает: ни запроса, ни round trip, ни участия сервера — самый быстрый "
                            + "запрос тот, которого не было. Обратите внимание на разницу с условным "
                            + "запросом: если бы в записи были только ETag или Last-Modified, браузер всё "
                            + "равно потратил бы один round trip, чтобы услышать 304 Not Modified. "
                            + "Правильный cache-control на статике — самое дешёвое улучшение "
                            + "производительности, которое большинство сайтов так и не делает",
                    List.of("content"), state());
            return this;
        }

        requireConn();
        conn.requests++;
        requests++;
        spend(RTT_MS);
        roundTrips++;
        record("OUT", "HTTP", "GET " + ref, "on the same connection, stream " + (conn.requests * 2 - 1), 300);
        record("IN", "HTTP", "200 OK", size(ref) + " bytes of " + kind(ref).toLowerCase(), size(ref));
        resources.add(new Res(ref, kind(ref), "NETWORK", RTT_MS, size(ref)));
        httpCache.add(ref);
        finish("CONTENT", RTT_MS, "PENDING", resources.size() + " loaded, " + pending.size() + " to go");
        Trace.event("RESOURCE_FETCHED",
                ref + " fetched in " + RTT_MS + " ms — one round trip and nothing else, because the "
                        + "connection to " + conn.ip + ":" + conn.port + " is already open and already "
                        + "encrypted. That is the whole argument for keep-alive and for HTTP/2: the "
                        + "expensive part was setting the connection up, and every request after the first "
                        + "amortises it. Over HTTP/2 this request is one stream among many on that single "
                        + "connection, so it does not queue behind the others the way six parallel HTTP/1.1 "
                        + "connections used to",
                ref + " получен за " + RTT_MS + " мс — один round trip и ничего больше, потому что "
                        + "соединение с " + conn.ip + ":" + conn.port + " уже открыто и уже зашифровано. В "
                        + "этом весь аргумент за keep-alive и за HTTP/2: дорогой была настройка соединения, "
                        + "и каждый запрос после первого её амортизирует. По HTTP/2 этот запрос — один из "
                        + "многих потоков в том же соединении, поэтому он не стоит в очереди за остальными, "
                        + "как это было с шестью параллельными соединениями HTTP/1.1",
                List.of("content", "wire"), state());
        return this;
    }

    /** Layout and paint — the first moment there is anything to look at. */
    public VisualPageLoad render() {
        requireUrl();
        spend(PAINT_MS);
        milestones.put("FIRST_PAINT", ms);
        finish("CONTENT", 0, "DONE", resources.size() + " resource(s)");
        finish("RENDER", PAINT_MS, "DONE", ms + " ms total");
        Trace.event("PAGE_RENDERED",
                "The browser turns the DOM and the CSSOM into a render tree, lays it out and paints: "
                        + ms + " ms after Enter, with " + roundTrips + " round trip(s) and " + resources.size()
                        + " resource(s) behind it. Notice how the time splits — the network layers "
                        + "(DNS, TCP, TLS) were spent before the server even heard about you, and no amount "
                        + "of backend optimisation touches them; they are fixed by distance, by round-trip "
                        + "count and by how much you can cache or reuse. That is why the practical answers to "
                        + "\"make it faster\" are a CDN (fewer milliseconds per round trip), keep-alive and "
                        + "HTTP/2 (fewer connections), HSTS (no redirect) and caching (no request at all)",
                "Браузер превращает DOM и CSSOM в дерево отрисовки, раскладывает его и рисует: " + ms + " мс "
                        + "после Enter, при " + roundTrips + " round trip и " + resources.size()
                        + " ресурсе(ах) позади. Обратите внимание, как делится время: сетевые слои "
                        + "(DNS, TCP, TLS) были потрачены ещё до того, как сервер вообще о вас узнал, и "
                        + "никакая оптимизация бэкенда их не трогает — их задают расстояние, количество "
                        + "round trip и то, сколько удаётся закэшировать или переиспользовать. Поэтому "
                        + "практические ответы на «сделай быстрее» — это CDN (меньше миллисекунд на round "
                        + "trip), keep-alive и HTTP/2 (меньше соединений), HSTS (нет редиректа) и "
                        + "кэширование (нет запроса вовсе)",
                List.of("render", "stats"), state());
        return this;
    }

    /** The whole pipeline, in order, the way a browser actually runs it. */
    public VisualPageLoad open(String input, String... refs) {
        String[] page = refs.length == 0
                ? new String[]{"/styles.css", "/app.js", "/logo.png"}
                : refs;
        type(input);
        resolve();
        connect();
        if ("https".equals(scheme)) {
            secure();
        }
        request();
        respond();
        parseHtml(page);
        for (String ref : page) {
            fetch(ref);
        }
        render();
        return this;
    }

    /** Prints where the time of this navigation actually went. */
    public void report() {
        Trace.event("PAGE_LOAD_AUDIT",
                "Visit #" + visit + " to " + scheme + "://" + host + path + ": " + ms + " ms total, "
                        + roundTrips + " round trip(s), " + dnsQueries + " DNS query/queries, "
                        + connectionsOpened + " TCP connection(s) opened, " + requests + " HTTP request(s), "
                        + cacheHits + " served from cache, " + redirects + " redirect(s), " + bytes
                        + " bytes transferred",
                "Визит №" + visit + " на " + scheme + "://" + host + path + ": " + ms + " мс всего, "
                        + roundTrips + " round trip, DNS-запросов " + dnsQueries + ", открыто "
                        + "TCP-соединений " + connectionsOpened + ", HTTP-запросов " + requests + ", из кэша "
                        + cacheHits + ", редиректов " + redirects + ", передано байт " + bytes,
                List.of("stats"), state());
    }

    /**
     * Prices the same page load under the conditions that actually differ in
     * production — the table the "why was it slow?" half of the question wants.
     */
    public static void compareRoundTrips() {
        int dnsMs = DNS_STUB_MS + 3 * DNS_HOP_MS;
        int firstByte = RTT_MS + SERVER_THINK_MS;
        List<Object> rows = new ArrayList<>();
        rows.add(row("COLD_TLS12", 4, 1, 2, 1, dnsMs + RTT_MS + 2 * RTT_MS + firstByte));
        rows.add(row("COLD_TLS13", 4, 1, 1, 1, dnsMs + RTT_MS + RTT_MS + firstByte));
        rows.add(row("WARM_DNS", 0, 1, 1, 1, RTT_MS + RTT_MS + firstByte));
        rows.add(row("REUSED_CONNECTION", 0, 0, 0, 1, firstByte));
        rows.add(row("FROM_CACHE", 0, 0, 0, 0, 0));

        Trace.event("ROUND_TRIPS_COMPARED",
                "The same document, five different starting conditions. A cold load on TLS 1.2 pays 8 round "
                        + "trips before the first byte; TLS 1.3 removes one; a warm DNS cache removes four "
                        + "more; an already-open connection removes everything except the request itself; and "
                        + "a fresh-enough cache entry removes even that. Two lessons live in this table. "
                        + "First, the fixed cost is measured in round trips, so it scales with distance, not "
                        + "with bandwidth — a faster connection does not fix it, a nearer server does. "
                        + "Second, every real optimisation is just a way of deleting a row: CDN, HSTS, "
                        + "keep-alive, HTTP/2, cache-control, and on HTTP/3 a 0-RTT resumption that folds the "
                        + "handshake into the request",
                "Один и тот же документ и пять разных стартовых условий. Холодная загрузка на TLS 1.2 "
                        + "платит 8 round trip до первого байта; TLS 1.3 убирает один; тёплый DNS-кэш "
                        + "убирает ещё четыре; уже открытое соединение убирает всё, кроме самого запроса; а "
                        + "достаточно свежая запись в кэше убирает и его. В этой таблице два урока. "
                        + "Во-первых, фиксированная цена измеряется в round trip, поэтому она зависит от "
                        + "расстояния, а не от ширины канала: быстрый интернет её не лечит, а близкий сервер "
                        + "— да. Во-вторых, любая настоящая оптимизация — это способ вычеркнуть строку: CDN, "
                        + "HSTS, keep-alive, HTTP/2, cache-control, а в HTTP/3 — возобновление 0-RTT, "
                        + "складывающее рукопожатие в сам запрос",
                List.of("stats"), comparisonState(rows));
    }

    // ------------------------------------------------------------- internals

    private void startNavigation() {
        resetStages();
        dnsSteps.clear();
        wire.clear();
        resources.clear();
        pending.clear();
        milestones.clear();
        answer = null;
        answerFromCache = false;
        ms = 0;
        roundTrips = 0;
        dnsQueries = 0;
        bytes = 0;
        connectionsOpened = 0;
        requests = 0;
        cacheHits = 0;
        redirects = 0;
    }

    private void resetStages() {
        stages.clear();
        for (String name : STAGES) {
            stages.put(name, new Stage());
        }
    }

    /**
     * Splits a typed string into scheme / host / port / path, filling in every
     * default the user did not type. Returns the scheme as typed, or null.
     */
    private String parseUrl(String input) {
        String rest = input;
        String typedScheme = null;
        int sep = rest.indexOf("://");
        if (sep > 0) {
            typedScheme = rest.substring(0, sep);
            rest = rest.substring(sep + 3);
        }
        int slash = rest.indexOf('/');
        String authority = slash < 0 ? rest : rest.substring(0, slash);
        path = slash < 0 ? "/" : rest.substring(slash);
        int colon = authority.indexOf(':');
        host = colon < 0 ? authority : authority.substring(0, colon);
        scheme = typedScheme == null ? "https" : typedScheme;
        port = colon < 0
                ? ("https".equals(scheme) ? 443 : 80)
                : Integer.parseInt(authority.substring(colon + 1));
        urlParsed = true;
        return typedScheme;
    }

    private void requireUrl() {
        if (!urlParsed) {
            throw new IllegalStateException("type(...) first — there is no URL yet");
        }
    }

    private void requireConn() {
        requireUrl();
        if (conn == null || !conn.open) {
            throw new IllegalStateException("connect() first — there is no open connection");
        }
    }

    private void spend(int millis) {
        ms += millis;
    }

    private void finish(String stage, int millis, String status, String detail) {
        Stage s = stages.get(stage);
        s.ms += millis;
        s.status = status;
        s.detail = detail;
    }

    /** One resolver hop: appends the step, charges the time, emits the event. */
    private void askDns(String from, String server, String result, String detail,
                        String descEn, String descRu) {
        dnsQueries++;
        spend(DNS_HOP_MS);
        roundTrips++;
        bytes += 152;
        dnsSteps.add(new DnsStep(from, host + " A → " + server, result, detail, DNS_HOP_MS));
        Trace.event("DNS_QUERY", descEn, descRu, List.of("dns"), state());
    }

    private void record(String dir, String layer, String label, String detail, int size) {
        wire.add(new Wire(wire.size() + 1, dir, layer, label, detail, size));
        bytes += size;
    }

    private static String ip(String host) {
        return "198.51.100." + (Math.floorMod(host.hashCode(), 250) + 1);
    }

    private static String tld(String host) {
        int dot = host.lastIndexOf('.');
        return dot < 0 ? host : host.substring(dot + 1);
    }

    private static String registrable(String host) {
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return host;
        }
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static String tldServer(String tld) {
        return switch (tld) {
            case "com", "net", "org" -> "a.gtld-servers.net";
            default -> "a.nic." + tld;
        };
    }

    private static String kind(String ref) {
        String lower = ref.toLowerCase();
        if (lower.endsWith(".css")) {
            return "STYLESHEET";
        }
        if (lower.endsWith(".js")) {
            return "SCRIPT";
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".svg")) {
            return "IMAGE";
        }
        return "OTHER";
    }

    private static int size(String ref) {
        return switch (kind(ref)) {
            case "STYLESHEET" -> 24_000;
            case "SCRIPT" -> 96_000;
            case "IMAGE" -> 12_000;
            default -> 8_000;
        };
    }

    // ------------------------------------------------------------------ state

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("typed", typed);
        s.put("visit", visit);

        Map<String, Object> url = new LinkedHashMap<>();
        url.put("scheme", scheme);
        url.put("host", host);
        url.put("port", port);
        url.put("path", path);
        url.put("hsts", hstsHosts.contains(host));
        s.put("url", url);

        List<Object> pipeline = new ArrayList<>();
        for (Map.Entry<String, Stage> entry : stages.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", entry.getKey());
            row.put("status", entry.getValue().status);
            row.put("ms", entry.getValue().ms);
            row.put("detail", entry.getValue().detail);
            pipeline.add(row);
        }
        s.put("stages", pipeline);

        Map<String, Object> dns = new LinkedHashMap<>();
        dns.put("ip", answer == null ? null : answer.ip);
        dns.put("ttl", answer == null ? null : Integer.valueOf(answer.ttl));
        dns.put("cached", answerFromCache);
        List<Object> steps = new ArrayList<>();
        for (DnsStep step : dnsSteps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", step.from);
            row.put("question", step.question);
            row.put("result", step.result);
            row.put("detail", step.detail);
            row.put("ms", step.ms);
            steps.add(row);
        }
        dns.put("steps", steps);
        s.put("dns", dns);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("state", conn == null ? "NONE" : (!conn.open ? "CLOSED" : conn.secure ? "SECURE" : "ESTABLISHED"));
        connection.put("peer", conn == null ? null : conn.ip + ":" + conn.port);
        connection.put("tls", conn == null ? null : conn.tls);
        connection.put("alpn", conn == null ? null : conn.alpn);
        connection.put("requests", conn == null ? 0 : conn.requests);
        s.put("connection", connection);

        List<Object> messages = new ArrayList<>();
        for (Wire w : wire) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", w.seq);
            row.put("dir", w.dir);
            row.put("layer", w.layer);
            row.put("label", w.label);
            row.put("detail", w.detail);
            row.put("bytes", w.bytes);
            messages.add(row);
        }
        s.put("wire", messages);

        s.put("page", pageState());
        s.put("stats", stats());
        s.put("comparison", new ArrayList<>());
        return s;
    }

    private Map<String, Object> pageState() {
        Map<String, Object> page = new LinkedHashMap<>();
        List<Object> loaded = new ArrayList<>();
        for (Res res : resources) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", res.name);
            row.put("kind", res.kind);
            row.put("source", res.source);
            row.put("ms", res.ms);
            row.put("bytes", res.bytes);
            loaded.add(row);
        }
        page.put("resources", loaded);
        page.put("pending", new ArrayList<>(pending));
        List<Object> marks = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : milestones.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", entry.getKey());
            row.put("ms", entry.getValue());
            marks.add(row);
        }
        page.put("milestones", marks);
        return page;
    }

    private Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ms", ms);
        stats.put("roundTrips", roundTrips);
        stats.put("dnsQueries", dnsQueries);
        stats.put("connections", connectionsOpened);
        stats.put("requests", requests);
        stats.put("cacheHits", cacheHits);
        stats.put("redirects", redirects);
        stats.put("bytes", bytes);
        return stats;
    }

    // ------------------------------------------------------------ comparison

    private static Map<String, Object> row(String scenario, int dns, int tcp, int tls, int http, int millis) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", scenario);
        row.put("dnsRtt", dns);
        row.put("tcpRtt", tcp);
        row.put("tlsRtt", tls);
        row.put("httpRtt", http);
        row.put("totalRtt", dns + tcp + tls + http);
        row.put("ms", millis);
        return row;
    }

    /** A well-formed state whose only interesting part is the comparison table. */
    private static Map<String, Object> comparisonState(List<Object> rows) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("typed", "");
        s.put("visit", 0);

        Map<String, Object> url = new LinkedHashMap<>();
        url.put("scheme", "https");
        url.put("host", "");
        url.put("port", 443);
        url.put("path", "/");
        url.put("hsts", false);
        s.put("url", url);

        s.put("stages", new ArrayList<>());

        Map<String, Object> dns = new LinkedHashMap<>();
        dns.put("ip", null);
        dns.put("ttl", null);
        dns.put("cached", false);
        dns.put("steps", new ArrayList<>());
        s.put("dns", dns);

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("state", "NONE");
        connection.put("peer", null);
        connection.put("tls", null);
        connection.put("alpn", null);
        connection.put("requests", 0);
        s.put("connection", connection);

        s.put("wire", new ArrayList<>());

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("resources", new ArrayList<>());
        page.put("pending", new ArrayList<>());
        page.put("milestones", new ArrayList<>());
        s.put("page", page);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ms", 0);
        stats.put("roundTrips", 0);
        stats.put("dnsQueries", 0);
        stats.put("connections", 0);
        stats.put("requests", 0);
        stats.put("cacheHits", 0);
        stats.put("redirects", 0);
        stats.put("bytes", 0);
        s.put("stats", stats);

        s.put("comparison", rows);
        return s;
    }
}
