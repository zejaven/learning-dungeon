package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of an SSL/TLS certificate and of the handshake that turns it
 * into an encrypted connection.
 *
 * <p>A certificate is not a key and not a cipher. It is a signed statement — <em>this
 * public key belongs to this name</em> — and the whole value of HTTPS rests on who signed
 * it. The model makes both halves visible:
 *
 * <ul>
 *   <li><b>identity</b> — {@link #inspect(Certificate)} prints the fields anybody can read,
 *       and {@link #connect(String, Certificate)} runs the five checks a browser runs:
 *       <em>chain</em> (does it lead to a root in my trust store), <em>hostname</em> (does
 *       it speak for the name I typed), <em>validity</em> (is it inside its dates),
 *       <em>revocation</em> (has it been withdrawn) and <em>possession</em> (does the other
 *       side actually hold the matching private key);</li>
 *   <li><b>encryption</b> — once the checks pass, an ephemeral key agreement produces a
 *       symmetric session key and every {@link #send(String)} becomes one AEAD record.
 *       Asymmetric cryptography authenticates and agrees on a key; symmetric cryptography
 *       carries the data.</li>
 * </ul>
 *
 * <p>The failure modes are the interesting part: a copied certificate fails
 * <em>possession</em> because the certificate is public and the private key is not; a
 * rogue CA whose root somebody installed passes every check, which is exactly how
 * corporate TLS inspection works; and a recording of the traffic stays unreadable after
 * the server's private key leaks — but only because the key agreement was ephemeral.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free, and every value it prints is deterministic so a run always traces the
 * same way.
 */
public class VisualTls {

    /** The root CA a fresh client already trusts, exactly like a browser ships with one. */
    public static final String ROOT_CA = "GlobalRoot CA";

    /** The CA that actually signs site certificates; it is itself signed by the root. */
    public static final String INTERMEDIATE_CA = "Example Intermediate CA";

    /** Model "today", in days. Certificates are dated around it. */
    public static final int TODAY = 100;

    /** The checks a client runs against a presented certificate, in order. */
    private static final List<String> CHECKS =
            List.of("chain", "hostname", "validity", "revocation", "possession");

    /** The phases of the handshake, in order. */
    private static final List<String> PHASES =
            List.of("hello", "certificate", "validate", "keys", "finished");

    // ----------------------------------------------------------------- certificate

    /** One certificate in a chain: a name, a public key, and a signature by its issuer. */
    private static final class Node {

        private final String role;
        private final String subject;
        private final List<String> names;
        private final String issuer;
        private final int notBefore;
        private final int notAfter;
        private final String keyType;
        private final boolean selfSigned;
        private final boolean sent;
        private final boolean revoked;

        private Node(String role, String subject, List<String> names, String issuer,
                     int notBefore, int notAfter, String keyType, boolean selfSigned,
                     boolean sent, boolean revoked) {
            this.role = role;
            this.subject = subject;
            this.names = names;
            this.issuer = issuer;
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            this.keyType = keyType;
            this.selfSigned = selfSigned;
            this.sent = sent;
            this.revoked = revoked;
        }
    }

    /**
     * What a server presents: its own certificate plus the intermediates needed to link it
     * to a root. The root itself is not sent — it is already on the client.
     */
    public static final class Certificate {

        private final List<Node> chain;
        private final boolean missingIntermediate;
        private final boolean interceptor;
        private final boolean privateKeyHeld;

        private Certificate(List<Node> chain, boolean missingIntermediate, boolean interceptor,
                            boolean privateKeyHeld) {
            this.chain = chain;
            this.missingIntermediate = missingIntermediate;
            this.interceptor = interceptor;
            this.privateKeyHeld = privateKeyHeld;
        }

        private Node leaf() {
            return chain.get(0);
        }

        /** The name the certificate is issued to. */
        public String subject() {
            return leaf().subject;
        }

        /** Who signed it — the only reason anybody believes the rest. */
        public String issuer() {
            return leaf().issuer;
        }

        /** Every hostname this certificate speaks for (the SAN list). */
        public List<String> names() {
            return List.copyOf(leaf().names);
        }

        /** First day the certificate is valid, in model days. */
        public int validFrom() {
            return leaf().notBefore;
        }

        /** Last day the certificate is valid, in model days. */
        public int validUntil() {
            return leaf().notAfter;
        }

        /** The key pair type — what the certificate binds a name to. */
        public String keyType() {
            return leaf().keyType;
        }

        /** The public half, which the certificate publishes to the whole world. */
        public String publicKey() {
            return "04" + hex("pub|" + leaf().subject + "|" + leaf().keyType);
        }

        /** Whether the presenter also holds the private half. A copy does not. */
        public boolean hasPrivateKey() {
            return privateKeyHeld;
        }

        /** How many certificates travel with it. */
        public int chainLength() {
            return chain.size();
        }
    }

    // -------------------------------------------------------- certificate factories

    /**
     * A normal, correctly issued certificate: signed by {@link #INTERMEDIATE_CA}, which is
     * signed by {@link #ROOT_CA}. The first name is the subject; all of them go in the SAN
     * list, so {@code certificateFor("*.shop.example")} produces a wildcard certificate.
     */
    public static Certificate certificateFor(String... names) {
        List<String> sanList = List.of(names);
        return new Certificate(standardChain(sanList, TODAY - 60, TODAY + 30, true),
                false, false, true);
    }

    /** The same certificate, but its validity window ended before today. */
    public static Certificate expiredCertificateFor(String host) {
        return new Certificate(standardChain(List.of(host), TODAY - 90, TODAY - 35, true),
                false, false, true);
    }

    /** A valid certificate the CA has withdrawn — typically because its key leaked. */
    public static Certificate revokedCertificateFor(String host) {
        List<Node> chain = new ArrayList<>(standardChain(List.of(host), TODAY - 60, TODAY + 30, true));
        Node leaf = chain.get(0);
        chain.set(0, new Node(leaf.role, leaf.subject, leaf.names, leaf.issuer, leaf.notBefore,
                leaf.notAfter, leaf.keyType, false, true, true));
        return new Certificate(List.copyOf(chain), false, false, true);
    }

    /** A certificate that signed itself: real keys, real encryption, nobody vouching for it. */
    public static Certificate selfSignedCertificateFor(String host) {
        Node leaf = new Node("leaf", host, List.of(host), host, TODAY - 10, TODAY + 355,
                "ECDSA-P256", true, true, false);
        return new Certificate(List.of(leaf), false, false, true);
    }

    /**
     * A certificate for {@code host} signed by some other CA entirely — an interception
     * proxy, a corporate middlebox, or an attacker with their own root.
     */
    public static Certificate certificateFromRoot(String host, String rootCa) {
        Node leaf = new Node("leaf", host, List.of(host), rootCa, TODAY - 1, TODAY + 89,
                "ECDSA-P256", false, true, false);
        Node root = new Node("root", rootCa, List.of(), rootCa, 0, TODAY + 500,
                "RSA-4096", true, false, false);
        return new Certificate(List.of(leaf, root), false, true, true);
    }

    /** The classic deployment slip: the leaf is sent alone, without the intermediate. */
    public static Certificate chainWithoutIntermediate(String host) {
        return new Certificate(standardChain(List.of(host), TODAY - 60, TODAY + 30, false),
                true, false, true);
    }

    /**
     * A byte-perfect copy of somebody else's certificate. Everything in a certificate is
     * public, so copying it is trivial — and useless, because the private key stays behind.
     */
    public static Certificate copyOf(Certificate genuine) {
        return new Certificate(genuine.chain, genuine.missingIntermediate, genuine.interceptor,
                false);
    }

    private static List<Node> standardChain(List<String> names, int notBefore, int notAfter,
                                            boolean withIntermediate) {
        List<Node> chain = new ArrayList<>();
        chain.add(new Node("leaf", names.get(0), names, INTERMEDIATE_CA, notBefore, notAfter,
                "ECDSA-P256", false, true, false));
        if (withIntermediate) {
            chain.add(new Node("intermediate", INTERMEDIATE_CA, List.of(), ROOT_CA,
                    0, TODAY + 900, "RSA-4096", false, true, false));
            chain.add(new Node("root", ROOT_CA, List.of(), ROOT_CA, 0, TODAY + 3000,
                    "RSA-4096", true, false, false));
        }
        return List.copyOf(chain);
    }

    // ----------------------------------------------------------------------- state

    private final Map<String, String> trustStore = new LinkedHashMap<>();
    private final Map<String, String[]> checks = new LinkedHashMap<>();
    private final Map<String, String[]> phases = new LinkedHashMap<>();
    private final List<Map<String, Object>> records = new ArrayList<>();

    private int clock = TODAY;
    private String protocol = "TLS 1.3";
    private String keyExchange = "ECDHE (X25519)";
    private String cipher = "AES-256-GCM";
    private boolean forwardSecrecy = true;

    private String host = "";
    private Certificate presented;
    private String sessionKey;
    private boolean established;
    private boolean intercepted;

    private int recorded;
    private boolean privateKeyStolen;
    private boolean attackerCanRead;
    private boolean rootAdded;

    private String decision = "idle";
    private String reason;
    private String detail = "";

    private int handshakes;
    private int refused;
    private int breaches;
    private int recordsSent;
    private int bytesEncrypted;

    private VisualTls() {
        trustStore.put(ROOT_CA, "preinstalled");
        resetConnection();
    }

    /**
     * A fresh client — a browser, a JVM, curl — with the root CAs it was shipped with and
     * nothing else. Everything it will ever believe about a server's identity comes from
     * this list.
     */
    public static VisualTls browser() {
        VisualTls client = new VisualTls();
        Trace.event("TLS_CLIENT",
                "A client starts with a TRUST STORE: a list of root CA certificates somebody "
                        + "else decided it should trust, shipped with the browser or the OS. It knows "
                        + "nothing about '" + ROOT_CA + "' beyond that entry — no key exchange, no "
                        + "certificate and no padlock means anything until a chain lands on one of "
                        + "these names",
                "Клиент начинает с ХРАНИЛИЩА ДОВЕРИЯ: списка корневых сертификатов CA, доверять "
                        + "которым решил кто-то другой, — он поставляется вместе с браузером или ОС. "
                        + "Про «" + ROOT_CA + "» клиент не знает ничего, кроме этой записи, — и ни "
                        + "обмен ключами, ни сертификат, ни замочек не значат ничего, пока цепочка не "
                        + "упрётся в одно из этих имён",
                List.of("truststore"), client.state());
        return client;
    }

    // --------------------------------------------------------------- configuration

    /**
     * Installs another root CA. This is one line of code and one click in a dialog — and it
     * hands whoever holds that root the power to mint a certificate for any site alive.
     */
    public VisualTls installRoot(String name) {
        trustStore.put(name, "added");
        rootAdded = true;
        beginStep();
        this.decision = "pending";
        Trace.event("ROOT_INSTALLED",
                "'" + name + "' is now a trusted root on this machine. Read what that means: any "
                        + "certificate this CA signs, for ANY hostname, will now validate perfectly "
                        + "here — no warning, no padlock difference, nothing for the user to notice. "
                        + "This is how corporate TLS inspection works, and it is also the goal of every "
                        + "attack that ends with 'just install our certificate'",
                "«" + name + "» теперь доверенный корень на этой машине. Прочитайте, что это "
                        + "значит: любой сертификат, подписанный этим CA, для ЛЮБОГО имени хоста "
                        + "теперь пройдёт здесь проверку идеально — без предупреждения, без разницы в "
                        + "замочке, без единого признака для пользователя. Так работает корпоративная "
                        + "инспекция TLS, и такова же цель любой атаки, заканчивающейся словами "
                        + "«просто установите наш сертификат»",
                List.of("truststore"), state());
        return this;
    }

    /**
     * Switches the key exchange from an ephemeral one to RSA key transport, the way TLS 1.2
     * could do it: the client encrypts the secret to the certificate's public key.
     */
    public VisualTls useRsaKeyTransport() {
        this.protocol = "TLS 1.2";
        this.keyExchange = "RSA key transport";
        this.forwardSecrecy = false;
        beginStep();
        this.decision = "pending";
        Trace.event("CIPHER_CONFIGURED",
                "The connection will use RSA KEY TRANSPORT: the client picks the secret, encrypts "
                        + "it with the public key out of the certificate, and sends it over. It works, "
                        + "and it ties every session that ever used this certificate to one long-lived "
                        + "private key — whoever obtains that key later can decrypt every conversation "
                        + "they recorded earlier. TLS 1.3 removed this mode entirely",
                "Соединение будет использовать ПЕРЕДАЧУ КЛЮЧА ПО RSA: клиент выбирает секрет, "
                        + "шифрует его публичным ключом из сертификата и отправляет. Это работает — и "
                        + "привязывает все сессии, когда-либо использовавшие этот сертификат, к одному "
                        + "долгоживущему приватному ключу: тот, кто получит этот ключ потом, "
                        + "расшифрует все разговоры, записанные раньше. В TLS 1.3 этот режим убрали "
                        + "целиком",
                List.of("crypto"), state());
        return this;
    }

    /** Moves the calendar forward, so a certificate can reach the end of its validity. */
    public void advanceDays(int days) {
        clock += days;
        beginStep();
        this.decision = "pending";
        Trace.event("TIME_PASSED",
                days + " day(s) pass; the model calendar is now on day " + clock
                        + ". Nothing was deployed and no code changed — and a certificate is a "
                        + "statement with an END DATE, so time alone is enough to take a working site "
                        + "off the air",
                "Проходит дней: " + days + "; в модели наступил день " + clock
                        + ". Ничего не выкатывали и код не менялся — а сертификат это утверждение с "
                        + "ДАТОЙ ОКОНЧАНИЯ, поэтому одного лишь времени достаточно, чтобы работающий "
                        + "сайт перестал открываться",
                List.of("clock"), state());
    }

    // ------------------------------------------------------------------- inspection

    /** Reads a certificate the way anyone can: it is a public document, signed but not secret. */
    public void inspect(Certificate certificate) {
        beginStep();
        this.presented = certificate;
        this.decision = "pending";
        Node leaf = certificate.leaf();
        Trace.event("CERT_INSPECTED",
                "A certificate is a PUBLIC document with four things inside: the name it speaks "
                        + "for (" + String.join(", ", leaf.names) + "), the PUBLIC key it binds to "
                        + "that name (" + leaf.keyType + "), a validity window (day " + leaf.notBefore
                        + " to day " + leaf.notAfter + "), and a signature by '" + leaf.issuer
                        + "'. Nothing here is secret and nothing here encrypts anything — it is a "
                        + "notarised claim, and its whole worth is the signature at the bottom",
                "Сертификат — это ПУБЛИЧНЫЙ документ, внутри которого четыре вещи: имя, за "
                        + "которое он отвечает (" + String.join(", ", leaf.names) + "), ПУБЛИЧНЫЙ "
                        + "ключ, привязанный к этому имени (" + leaf.keyType + "), окно "
                        + "действительности (с дня " + leaf.notBefore + " по день " + leaf.notAfter
                        + ") и подпись «" + leaf.issuer + "». Здесь нет ничего секретного, и сам он "
                        + "ничего не шифрует — это заверенное утверждение, и вся его ценность в "
                        + "подписи внизу",
                List.of("chain", "cert:" + leaf.subject), state());
    }

    // -------------------------------------------------------------------- handshake

    /**
     * The whole handshake: hello, certificate, the five checks, the key agreement, and the
     * session keys the rest of the connection actually uses.
     */
    public void connect(String requestedHost, Certificate certificate) {
        beginConnection(requestedHost, certificate);
        handshakes++;

        // --- 1. ClientHello: everything here is in the clear, there is no tunnel yet.
        phase("hello", "passed", protocol + ", SNI " + requestedHost);
        Trace.event("CLIENT_HELLO",
                "The client opens with the versions and cipher suites it supports, a fresh key "
                        + "share, and SNI = '" + requestedHost + "' so a server hosting many sites "
                        + "knows which certificate to send. All of this is PLAINTEXT — the tunnel does "
                        + "not exist yet, which is why an observer learns which site you are opening "
                        + "even though they will not see what you do there",
                "Клиент начинает с версий и наборов шифров, которые поддерживает, свежей доли "
                        + "ключа и SNI = «" + requestedHost + "», чтобы сервер, где живёт много "
                        + "сайтов, понял, какой сертификат отдавать. Всё это ОТКРЫТЫМ ТЕКСТОМ — "
                        + "туннеля ещё нет, и поэтому наблюдатель узнаёт, какой сайт вы открываете, "
                        + "хотя и не увидит, что вы там делаете",
                List.of("phase:hello"), state());

        // --- 2. Certificate: the server answers with its chain.
        phase("certificate", "passed", certificate.chain.size() + " certificate(s)");
        Trace.event("CERT_PRESENTED",
                "The server sends its certificate and the intermediates that link it upward — "
                        + certificate.chain.size() + " certificate(s) in this chain, of which "
                        + sentCount(certificate) + " actually travel over the wire. It is sent to "
                        + "EVERY client that connects, so treat it as published: the secret is the "
                        + "private key, which never leaves the server",
                "Сервер отправляет свой сертификат и промежуточные, связывающие его вверх, — в "
                        + "этой цепочке сертификатов: " + certificate.chain.size() + ", из них по "
                        + "проводу реально идут " + sentCount(certificate) + ". Он отправляется "
                        + "КАЖДОМУ подключившемуся клиенту, так что считайте его опубликованным: "
                        + "секрет — это приватный ключ, и он никогда не покидает сервер",
                List.of("phase:certificate", "chain"), state());

        // --- 3. The five checks.
        if (!checkChain(certificate)) {
            return;
        }
        if (!checkHostname(requestedHost, certificate)) {
            return;
        }
        if (!checkValidity(certificate)) {
            return;
        }
        if (!checkRevocation(certificate)) {
            return;
        }
        if (!checkPossession(certificate)) {
            return;
        }
        phase("validate", "passed", "5 checks");

        // --- 4. Key agreement and session keys.
        sessionKey = hex("keys|" + requestedHost + "|" + handshakes + "|" + keyExchange);
        phase("keys", "passed", keyExchange);
        if (forwardSecrecy) {
            Trace.event("KEY_EXCHANGE",
                    "Both sides run an EPHEMERAL Diffie-Hellman exchange (" + keyExchange
                            + "): each sends a public share, each combines it with its own private "
                            + "value, and both arrive at the same secret without that secret ever "
                            + "crossing the wire. Notice what the certificate's key did here — it "
                            + "SIGNED the exchange, it did not encrypt anything. Authentication and "
                            + "encryption are two different jobs done by two different keys",
                    "Обе стороны выполняют ЭФЕМЕРНЫЙ обмен Диффи-Хеллмана (" + keyExchange
                            + "): каждая присылает публичную долю, каждая складывает её со своим "
                            + "приватным значением, и обе приходят к одному секрету, который по "
                            + "проводу не передавался вовсе. Обратите внимание, что сделал ключ из "
                            + "сертификата: он ПОДПИСАЛ обмен, а не зашифровал что-либо. "
                            + "Аутентификация и шифрование — две разные работы для двух разных ключей",
                    List.of("phase:keys", "crypto"), state());
        } else {
            Trace.event("KEY_EXCHANGE",
                    "The client generates the secret itself and encrypts it with the PUBLIC KEY "
                            + "taken from the certificate; only the matching private key can open it. "
                            + "This is the one place where a certificate's key does encrypt something "
                            + "— and it is also the reason this mode is gone: the secret of every "
                            + "session is locked to a key that lives on the server for years",
                    "Клиент сам генерирует секрет и шифрует его ПУБЛИЧНЫМ КЛЮЧОМ, взятым из "
                            + "сертификата; открыть его может только парный приватный ключ. Это "
                            + "единственное место, где ключ из сертификата что-то шифрует, — и это же "
                            + "причина, по которой режим убрали: секрет каждой сессии заперт ключом, "
                            + "который живёт на сервере годами",
                    List.of("phase:keys", "crypto"), state());
        }

        established = true;
        phase("finished", "passed", cipher);
        Trace.event("SESSION_KEYS_DERIVED",
                "The shared secret is stretched into SYMMETRIC session keys and the connection "
                        + "switches to " + cipher + " — key " + sessionKey + ". From here the "
                        + "asymmetric maths is finished: it authenticated the server and agreed on a "
                        + "key, and everything after this point is symmetric, which is why HTTPS costs "
                        + "a handshake rather than a throughput penalty",
                "Общий секрет растягивается в СИММЕТРИЧНЫЕ ключи сессии, и соединение переходит "
                        + "на " + cipher + " — ключ " + sessionKey + ". С этого момента асимметричная "
                        + "математика закончилась: она аутентифицировала сервер и договорилась о "
                        + "ключе, а дальше всё симметрично — поэтому HTTPS стоит рукопожатия, а не "
                        + "потери пропускной способности",
                List.of("phase:keys", "crypto"), state());

        if (certificate.interceptor && trustStore.containsKey(anchorOf(certificate))) {
            breaches++;
            intercepted = true;
            this.decision = "breach";
            this.reason = "rogue-root";
            this.detail = anchorOf(certificate);
            Trace.event("MITM_ACCEPTED",
                    "The connection is encrypted, the padlock is shown, every check passed — and "
                            + "the other end is '" + anchorOf(certificate) + "', not the real "
                            + requestedHost + ". Nothing is broken: TLS proved the certificate chains "
                            + "to a root THIS MACHINE TRUSTS, and somebody put that root there. "
                            + "Encryption without the right identity is just a private conversation "
                            + "with the wrong party",
                    "Соединение зашифровано, замочек показан, все проверки пройдены — а на другом "
                            + "конце «" + anchorOf(certificate) + "», а не настоящий " + requestedHost
                            + ". Ничего не сломалось: TLS доказал, что цепочка ведёт к корню, "
                            + "которому ДОВЕРЯЕТ ЭТА МАШИНА, а корень туда кто-то положил. Шифрование "
                            + "без правильной личности — это просто приватный разговор не с тем "
                            + "собеседником",
                    List.of("truststore", "chain", "outcome"), state());
            return;
        }

        this.decision = "secure";
        this.detail = requestedHost;
        Trace.event("HANDSHAKE_DONE",
                "The connection to " + requestedHost + " is up: authenticated by a certificate "
                        + "that chains to " + anchorOf(certificate) + ", encrypted with " + cipher
                        + ". Only now does the browser send a single byte of the request — and the "
                        + "padlock means exactly this much: private, unmodified, and really "
                        + requestedHost + ". It says nothing about whether " + requestedHost
                        + " deserves your data",
                "Соединение с " + requestedHost + " установлено: аутентифицировано сертификатом, "
                        + "цепочка которого ведёт к " + anchorOf(certificate) + ", зашифровано "
                        + cipher + ". Только теперь браузер отправит первый байт запроса — и замочек "
                        + "означает ровно это: приватно, не изменено и это действительно "
                        + requestedHost + ". Про то, заслуживает ли " + requestedHost
                        + " ваших данных, он не говорит ничего",
                List.of("phase:finished", "outcome"), state());
    }

    // ----------------------------------------------------------------- the checks

    private boolean checkChain(Certificate certificate) {
        if (certificate.missingIntermediate) {
            mark("chain", "failed", "no intermediate");
            fail("incomplete-chain", certificate.leaf().issuer);
            Trace.event("CHAIN_INCOMPLETE",
                    "The server sent only its own certificate. Its issuer '"
                            + certificate.leaf().issuer + "' is not a root and is not on this client, "
                            + "so there is no path to build and the connection is refused. The classic "
                            + "symptom: it works in your browser, which cached the intermediate from "
                            + "another site, and fails in curl and on the phone. Serve the full chain",
                    "Сервер отправил только собственный сертификат. Его издатель «"
                            + certificate.leaf().issuer + "» не корень и на этом клиенте "
                            + "отсутствует, поэтому путь построить не из чего и соединение "
                            + "отклоняется. Классический симптом: в вашем браузере работает — он "
                            + "закешировал промежуточный с другого сайта, — а в curl и на телефоне "
                            + "нет. Отдавайте цепочку целиком",
                    List.of("check:chain", "chain"), state());
            return false;
        }
        String anchor = anchorOf(certificate);
        if (!trustStore.containsKey(anchor)) {
            mark("chain", "failed", anchor + " not trusted");
            fail("untrusted-issuer", anchor);
            Trace.event("CHAIN_UNTRUSTED",
                    "Every signature in the chain is mathematically fine and the chain still ends "
                            + "at '" + anchor + "', which is not in the trust store. That is the whole "
                            + "job of a CA: a self-signed certificate encrypts exactly as well as any "
                            + "other and proves NOTHING, because anybody can sign a document claiming "
                            + "to be " + certificate.subject() + ". This is the warning page users are "
                            + "trained to click through",
                    "Все подписи в цепочке математически в порядке, и цепочка всё равно "
                            + "заканчивается на «" + anchor + "», которого нет в хранилище доверия. В "
                            + "этом и состоит работа CA: самоподписанный сертификат шифрует ровно так "
                            + "же хорошо, как любой другой, и не доказывает НИЧЕГО — подписать "
                            + "документ с надписью " + certificate.subject() + " может кто угодно. "
                            + "Это та самая страница предупреждения, которую пользователей приучили "
                            + "прокликивать",
                    List.of("check:chain", "chain", "truststore"), state());
            return false;
        }
        mark("chain", "passed", certificate.chain.size() + " certs to " + anchor);
        Trace.event("CHAIN_VERIFIED",
                "The client walks the chain: '" + certificate.subject() + "' was signed by '"
                        + certificate.leaf().issuer + "', which was signed by '" + anchor
                        + "' — and '" + anchor + "' is in the trust store, so the walk stops there. "
                        + "Each step is checked with the ISSUER'S PUBLIC KEY, and the root was never "
                        + "sent: it was already on this machine. Trust is not in the certificate, it "
                        + "is in that last name",
                "Клиент идёт по цепочке: «" + certificate.subject() + "» подписан «"
                        + certificate.leaf().issuer + "», тот подписан «" + anchor + "», а «" + anchor
                        + "» есть в хранилище доверия — на нём обход и останавливается. Каждый шаг "
                        + "проверяется ПУБЛИЧНЫМ КЛЮЧОМ ИЗДАТЕЛЯ, а корень никто не присылал: он уже "
                        + "лежал на этой машине. Доверие живёт не в сертификате, а в этом последнем "
                        + "имени",
                List.of("check:chain", "chain", "truststore"), state());
        return true;
    }

    private boolean checkHostname(String requestedHost, Certificate certificate) {
        List<String> names = certificate.leaf().names;
        if (!matchesAny(requestedHost, names)) {
            mark("hostname", "failed", requestedHost + " not in SAN");
            fail("hostname-mismatch", String.join(", ", names));
            Trace.event("HOSTNAME_MISMATCH",
                    "The certificate is genuine, current and signed by a trusted CA — and it says "
                            + String.join(", ", names) + ", while the address bar says "
                            + requestedHost + ". Refused. Without this check a valid certificate for "
                            + "any domain at all would let its holder impersonate every site, so "
                            + "'signed by a real CA' is only half the question; the other half is "
                            + "'signed for THIS NAME'",
                    "Сертификат настоящий, действующий и подписан доверенным CA — и в нём "
                            + "написано " + String.join(", ", names) + ", а в адресной строке "
                            + requestedHost + ". Отказ. Без этой проверки корректный сертификат на "
                            + "любой домен позволил бы его владельцу выдавать себя за любой сайт, "
                            + "поэтому «подписан настоящим CA» — это только половина вопроса; вторая "
                            + "половина — «подписан на ЭТО ИМЯ»",
                    List.of("check:hostname", "chain"), state());
            return false;
        }
        mark("hostname", "passed", requestedHost);
        return true;
    }

    private boolean checkValidity(Certificate certificate) {
        Node leaf = certificate.leaf();
        if (clock > leaf.notAfter || clock < leaf.notBefore) {
            mark("validity", "failed", "day " + clock + " outside " + leaf.notBefore + ".."
                    + leaf.notAfter);
            fail("expired", "day " + leaf.notAfter);
            Trace.event("CERT_EXPIRED",
                    "Day " + clock + " is outside the window day " + leaf.notBefore + " to day "
                            + leaf.notAfter + ", so the client refuses — hard, with an interstitial, "
                            + "for a site whose code and config are perfectly fine. Short lifetimes "
                            + "are deliberate (they bound the damage of a leaked key), which is why "
                            + "renewal has to be automated and alerted on, not remembered",
                    "День " + clock + " лежит вне окна с дня " + leaf.notBefore + " по день "
                            + leaf.notAfter + ", поэтому клиент отказывает — жёстко, с заглушкой, для "
                            + "сайта, у которого код и конфигурация в полном порядке. Короткий срок "
                            + "жизни сделан намеренно (он ограничивает ущерб от утёкшего ключа), "
                            + "поэтому обновление должно быть автоматическим и с алертами, а не "
                            + "«не забыть»",
                    List.of("check:validity", "chain", "clock"), state());
            return false;
        }
        mark("validity", "passed", "day " + clock + " of " + leaf.notBefore + ".." + leaf.notAfter);
        return true;
    }

    private boolean checkRevocation(Certificate certificate) {
        if (certificate.leaf().revoked) {
            mark("revocation", "failed", "revoked by " + certificate.leaf().issuer);
            fail("revoked", certificate.leaf().issuer);
            Trace.event("CERT_REVOKED",
                    "The certificate is inside its dates and the CA has withdrawn it anyway — "
                            + "normally because the private key leaked. The check exists because "
                            + "expiry is too slow an answer to a compromise, and it is the weakest of "
                            + "the five in practice: CRLs are large, OCSP is a privacy leak and a "
                            + "latency risk, and clients often fail open. OCSP stapling is the "
                            + "workable version",
                    "Сертификат внутри своих дат, и CA всё равно его отозвал — обычно потому, что "
                            + "утёк приватный ключ. Проверка существует потому, что истечение срока "
                            + "слишком медленный ответ на компрометацию, и на практике она самая "
                            + "слабая из пяти: CRL огромны, OCSP — утечка приватности и риск задержки, "
                            + "а клиенты часто пропускают ошибку. Рабочий вариант — OCSP stapling",
                    List.of("check:revocation", "chain"), state());
            return false;
        }
        mark("revocation", "passed", "not revoked");
        return true;
    }

    private boolean checkPossession(Certificate certificate) {
        if (!certificate.hasPrivateKey()) {
            mark("possession", "failed", "no matching private key");
            fail("no-private-key", certificate.subject());
            Trace.event("POSSESSION_FAILED",
                    "The presented certificate is a perfect copy of the real one — same bytes, "
                            + "same CA signature, and it passes every check about its CONTENT. Then "
                            + "the client asks the other side to sign the handshake transcript with "
                            + "the matching private key, and there is nothing to sign with. This is "
                            + "why publishing a certificate to the world is safe: it is a lock, not "
                            + "a key",
                    "Предъявленный сертификат — идеальная копия настоящего: те же байты, та же "
                            + "подпись CA, и все проверки его СОДЕРЖИМОГО он проходит. А затем клиент "
                            + "просит другую сторону подписать стенограмму рукопожатия парным "
                            + "приватным ключом — и подписывать нечем. Поэтому публиковать сертификат "
                            + "всему миру безопасно: это замок, а не ключ",
                    List.of("check:possession", "crypto"), state());
            return false;
        }
        mark("possession", "passed", "signed the transcript");
        Trace.event("POSSESSION_PROVEN",
                "The server signs the handshake transcript with the private key belonging to the "
                        + "public key in the certificate, and the client verifies that signature with "
                        + "the certificate. This is the step that binds the document to the party "
                        + "holding the connection — without it, anybody could download "
                        + certificate.subject() + "'s certificate and replay it",
                "Сервер подписывает стенограмму рукопожатия приватным ключом, парным к публичному "
                        + "ключу из сертификата, а клиент проверяет эту подпись самим сертификатом. "
                        + "Именно этот шаг связывает документ с той стороной, которая держит "
                        + "соединение: без него кто угодно скачал бы сертификат "
                        + certificate.subject() + " и предъявил его снова",
                List.of("check:possession", "crypto"), state());
        return true;
    }

    // --------------------------------------------------------------- application data

    /** Sends one HTTP request through the established tunnel, as a single AEAD record. */
    public void send(String request) {
        beginStep();
        if (!established) {
            this.decision = "refused";
            this.reason = "no-session";
            this.detail = request;
            Trace.event("PLAINTEXT_ON_THE_WIRE",
                    "There is no session — the handshake never completed — so this request would "
                            + "go out as readable text: \"" + request + "\". That is what HTTP looks "
                            + "like to every router, proxy and Wi-Fi access point on the path, and it "
                            + "is the state a failed TLS connection must never silently fall back to",
                    "Сессии нет — рукопожатие не завершилось, — поэтому запрос ушёл бы читаемым "
                            + "текстом: «" + request + "». Именно так HTTP выглядит для каждого "
                            + "роутера, прокси и точки доступа Wi-Fi на пути, и именно в это "
                            + "состояние неудавшееся TLS-соединение никогда не должно тихо "
                            + "откатываться",
                    List.of("records"), state());
            return;
        }
        int seq = records.size() + 1;
        String ciphertext = hex("ct|" + sessionKey + "|" + seq + "|" + request);
        String tag = hex("tag|" + sessionKey + "|" + seq + "|" + request);
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("seq", seq);
        rec.put("plaintext", request);
        rec.put("ciphertext", ciphertext);
        rec.put("tag", tag);
        rec.put("intact", true);
        rec.put("exposed", attackerCanRead || intercepted);
        records.add(rec);
        recordsSent++;
        bytesEncrypted += request.length();
        this.decision = "secure";
        this.detail = request;
        Trace.event("RECORD_ENCRYPTED",
                "\"" + request + "\" becomes record #" + seq + ": ciphertext " + ciphertext
                        + " plus authentication tag " + tag + ". " + cipher + " is an AEAD cipher, so "
                        + "one operation gives both confidentiality and integrity — and the whole HTTP "
                        + "message is inside: method, path, query string, headers, cookies and body. "
                        + "What still leaks is the destination IP, the size and the timing",
                "«" + request + "» превращается в запись №" + seq + ": шифротекст " + ciphertext
                        + " плюс тег аутентичности " + tag + ". " + cipher + " — это AEAD-шифр, "
                        + "поэтому одна операция даёт и конфиденциальность, и целостность, — а внутри "
                        + "лежит HTTP-сообщение целиком: метод, путь, query-строка, заголовки, куки и "
                        + "тело. Утекают по-прежнему IP назначения, размер и тайминг",
                List.of("records", "crypto"), state());
    }

    /** Sends the same request with no TLS at all — the contrast that makes the point. */
    public void sendWithoutTls(String request) {
        beginStep();
        int seq = records.size() + 1;
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("seq", seq);
        rec.put("plaintext", request);
        rec.put("ciphertext", request);
        rec.put("tag", "");
        rec.put("intact", true);
        rec.put("exposed", true);
        records.add(rec);
        this.decision = "exposed";
        this.reason = "plaintext";
        this.detail = request;
        Trace.event("PLAINTEXT_ON_THE_WIRE",
                "Over plain HTTP the bytes on the wire ARE the message: \"" + request + "\". "
                        + "Anyone on the path reads it, and — worse — can rewrite it, because there is "
                        + "no tag to break. Compare the previous record: the difference is not "
                        + "'slightly safer', it is 'unreadable and unmodifiable' versus 'a postcard'",
                "По обычному HTTP байты на проводе И ЕСТЬ сообщение: «" + request + "». Любой на "
                        + "пути его читает и — что хуже — может переписать, потому что ломать нечего: "
                        + "тега нет. Сравните с предыдущей записью: разница не «немного безопаснее», "
                        + "а «нечитаемо и неизменяемо» против «открытки»",
                List.of("records"), state());
    }

    /** Flips a byte of the last record in flight — what integrity protection is for. */
    public void tamperInFlight() {
        beginStep();
        if (records.isEmpty()) {
            return;
        }
        Map<String, Object> rec = records.get(records.size() - 1);
        rec.put("intact", false);
        established = false;
        this.decision = "refused";
        this.reason = "tampered";
        this.detail = "record #" + rec.get("seq");
        Trace.event("RECORD_TAMPERED",
                "Somebody on the path flips a byte of record #" + rec.get("seq")
                        + ". The receiver recomputes the authentication tag over what it actually got, "
                        + "the tags disagree, and the connection is torn down immediately — no "
                        + "partial message, no 'best effort' decryption. This is INTEGRITY, and it is "
                        + "why nobody can inject an advert or rewrite a script inside HTTPS",
                "Кто-то на пути переворачивает байт в записи №" + rec.get("seq")
                        + ". Получатель пересчитывает тег аутентичности по фактически полученному, "
                        + "теги не сходятся, и соединение немедленно рвётся — ни частичного "
                        + "сообщения, ни расшифровки «как получится». Это ЦЕЛОСТНОСТЬ, и благодаря ей "
                        + "никто не вставит рекламу и не перепишет скрипт внутри HTTPS",
                List.of("records", "outcome"), state());
    }

    // ------------------------------------------------------------- forward secrecy

    /** A passive eavesdropper stores the ciphertext, hoping to read it one day. */
    public void eavesdropperRecords() {
        beginStep();
        recorded += records.size();
        this.decision = "pending";
        this.detail = recorded + " record(s)";
        Trace.event("TRAFFIC_RECORDED",
                "An eavesdropper cannot read the traffic, so they keep it: " + recorded
                        + " record(s) written to disk. Storage is cheap and ciphertext keeps well — "
                        + "'harvest now, decrypt later' is a real strategy, and the only question is "
                        + "whether anything they might obtain in the future would unlock this "
                        + "recording",
                "Прослушивающий не может прочитать трафик, поэтому он его сохраняет: записей на "
                        + "диске " + recorded + ". Хранилище дёшево, а шифротекст хорошо лежит — "
                        + "«собрать сейчас, расшифровать потом» это реальная стратегия, и весь вопрос "
                        + "в том, откроет ли эту запись что-нибудь, что они смогут добыть в будущем",
                List.of("attacker", "records"), state());
    }

    /** The server's private key leaks a year later. Whether that matters depends on step 4. */
    public void stealPrivateKeyLater() {
        beginStep();
        privateKeyStolen = true;
        Trace.event("PRIVATE_KEY_STOLEN",
                "A year later the server's PRIVATE KEY leaks — a stolen backup, a vulnerable "
                        + "library, a departing admin. From this moment the attacker can impersonate "
                        + "the site until the certificate is revoked. The separate question is what it "
                        + "does to the traffic they recorded back then",
                "Через год ПРИВАТНЫЙ КЛЮЧ сервера утекает — украденный бэкап, уязвимая "
                        + "библиотека, ушедший администратор. С этого момента злоумышленник может "
                        + "выдавать себя за сайт, пока сертификат не отзовут. Отдельный вопрос — что "
                        + "это делает с трафиком, который он записал тогда",
                List.of("attacker", "crypto"), state());

        if (forwardSecrecy) {
            this.decision = "secure";
            this.detail = recorded + " record(s) stay unreadable";
            Trace.event("FORWARD_SECRECY_HELD",
                    "The recording stays unreadable. The session key came from an EPHEMERAL "
                            + "exchange: both private values existed only in memory, for one "
                            + "connection, and were discarded — the certificate's long-lived key never "
                            + "encrypted anything, it only signed. That property is FORWARD SECRECY, "
                            + "and it is the reason TLS 1.3 allows nothing else",
                    "Запись остаётся нечитаемой. Ключ сессии получился из ЭФЕМЕРНОГО обмена: оба "
                            + "приватных значения существовали только в памяти, ради одного "
                            + "соединения, и были выброшены, — долгоживущий ключ из сертификата "
                            + "ничего не шифровал, он только подписывал. Это свойство называется "
                            + "ПРЯМОЙ СЕКРЕТНОСТЬЮ, и ради него TLS 1.3 не разрешает ничего другого",
                    List.of("attacker", "crypto", "records"), state());
            return;
        }

        attackerCanRead = true;
        for (Map<String, Object> rec : records) {
            rec.put("exposed", true);
        }
        breaches++;
        this.decision = "breach";
        this.reason = "no-forward-secrecy";
        this.detail = recorded + " record(s) decrypted";
        Trace.event("RECORDING_DECRYPTED",
                "Every recorded record opens up at once. With RSA key transport the session "
                        + "secret was encrypted TO this private key, so the key that leaked today "
                        + "decrypts conversations from a year ago — retroactively, in bulk, with "
                        + "nothing anyone can do about it now. One key compromise, every past session",
                "Все записанные записи открываются разом. При передаче ключа по RSA секрет сессии "
                        + "был зашифрован ИМЕННО ЭТИМ приватным ключом, поэтому ключ, утёкший "
                        + "сегодня, расшифровывает разговоры годичной давности — задним числом, "
                        + "оптом, и сделать с этим уже ничего нельзя. Одна утечка ключа — все прошлые "
                        + "сессии",
                List.of("attacker", "crypto", "records"), state());
    }

    /** Prints what the whole run added up to. */
    public void report() {
        beginStep();
        this.decision = "pending";
        Trace.event("TLS_AUDIT",
                "After the run: handshakes " + handshakes + ", refused " + refused
                        + ", records encrypted " + recordsSent + ", plaintext bytes protected "
                        + bytesEncrypted + ", connections that looked secure and were not " + breaches
                        + ". That last number is the one worth staring at — every breach here passed "
                        + "the cryptography and failed on identity",
                "Итоги прогона: рукопожатий " + handshakes + ", отклонено " + refused
                        + ", зашифровано записей " + recordsSent + ", защищено байт открытого текста "
                        + bytesEncrypted + ", соединений, которые выглядели безопасными и не были ими: "
                        + breaches + ". Именно на последнее число стоит смотреть: каждая брешь здесь "
                        + "прошла криптографию и провалилась на личности",
                List.of(), state());
    }

    // ------------------------------------------------------------------ internals

    private void beginStep() {
        this.decision = "pending";
        this.reason = null;
        this.detail = "";
    }

    private void beginConnection(String requestedHost, Certificate certificate) {
        this.host = requestedHost;
        this.presented = certificate;
        this.sessionKey = null;
        this.established = false;
        this.intercepted = false;
        this.decision = "pending";
        this.reason = null;
        this.detail = "";
        resetConnection();
    }

    private void resetConnection() {
        checks.clear();
        for (String id : CHECKS) {
            checks.put(id, new String[]{"pending", ""});
        }
        phases.clear();
        for (String id : PHASES) {
            phases.put(id, new String[]{"pending", ""});
        }
    }

    private void mark(String id, String status, String detail) {
        checks.put(id, new String[]{status, detail});
    }

    private void phase(String id, String status, String detail) {
        phases.put(id, new String[]{status, detail});
    }

    private void fail(String why, String what) {
        phase("validate", "failed", why);
        this.decision = "refused";
        this.reason = why;
        this.detail = what;
        refused++;
    }

    /** The name the chain ends at — the one that has to be in the trust store. */
    private static String anchorOf(Certificate certificate) {
        Node top = certificate.chain.get(certificate.chain.size() - 1);
        return top.selfSigned ? top.subject : top.issuer;
    }

    private static int sentCount(Certificate certificate) {
        int sent = 0;
        for (Node node : certificate.chain) {
            if (node.sent) {
                sent++;
            }
        }
        return sent;
    }

    /** SAN matching: exact, or a wildcard that covers exactly one label. */
    private static boolean matchesAny(String requestedHost, List<String> names) {
        for (String name : names) {
            if (name.equals(requestedHost)) {
                return true;
            }
            if (name.startsWith("*.")) {
                String suffix = name.substring(1);
                if (requestedHost.endsWith(suffix)) {
                    String prefix = requestedHost.substring(0, requestedHost.length() - suffix.length());
                    if (!prefix.isEmpty() && prefix.indexOf('.') < 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Deterministic stand-in for real cryptographic output, so every run traces the same. */
    private static String hex(String material) {
        return Integer.toHexString(material.hashCode() & 0x7fffffff);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("host", host);
        s.put("clock", clock);

        List<Object> roots = new ArrayList<>();
        for (Map.Entry<String, String> root : trustStore.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", root.getKey());
            v.put("source", root.getValue());
            roots.add(v);
        }
        s.put("trustStore", roots);

        List<Object> chain = new ArrayList<>();
        if (presented != null) {
            for (Node node : presented.chain) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("role", node.role);
                v.put("subject", node.subject);
                v.put("names", List.copyOf(node.names));
                v.put("issuer", node.issuer);
                v.put("notBefore", node.notBefore);
                v.put("notAfter", node.notAfter);
                v.put("keyType", node.keyType);
                v.put("selfSigned", node.selfSigned);
                v.put("revoked", node.revoked);
                v.put("sent", node.sent);
                v.put("trusted", trustStore.containsKey(node.subject));
                chain.add(v);
            }
        }
        s.put("chain", chain);
        s.put("privateKeyHeld", presented != null && presented.privateKeyHeld);

        List<Object> phaseList = new ArrayList<>();
        for (Map.Entry<String, String[]> ph : phases.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", ph.getKey());
            v.put("status", ph.getValue()[0]);
            v.put("detail", ph.getValue()[1]);
            phaseList.add(v);
        }
        s.put("handshake", phaseList);

        List<Object> checkList = new ArrayList<>();
        for (Map.Entry<String, String[]> check : checks.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", check.getKey());
            v.put("status", check.getValue()[0]);
            v.put("detail", check.getValue()[1]);
            checkList.add(v);
        }
        s.put("checks", checkList);

        Map<String, Object> crypto = new LinkedHashMap<>();
        crypto.put("protocol", protocol);
        crypto.put("keyExchange", keyExchange);
        crypto.put("authentication", presented == null ? "" : presented.keyType() + " signature");
        crypto.put("cipher", cipher);
        crypto.put("sessionKey", sessionKey);
        crypto.put("forwardSecrecy", forwardSecrecy);
        crypto.put("established", established);
        s.put("crypto", crypto);

        s.put("records", new ArrayList<Object>(records));

        Map<String, Object> attacker = new LinkedHashMap<>();
        attacker.put("recorded", recorded);
        attacker.put("hasPrivateKey", privateKeyStolen);
        attacker.put("canRead", attackerCanRead);
        attacker.put("rootInstalled", rootAdded);
        s.put("attacker", attacker);

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("decision", decision);
        outcome.put("reason", reason);
        outcome.put("detail", detail);
        s.put("outcome", outcome);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("handshakes", handshakes);
        stats.put("refused", refused);
        stats.put("records", recordsSent);
        stats.put("bytesEncrypted", bytesEncrypted);
        stats.put("breaches", breaches);
        s.put("stats", stats);
        return s;
    }
}
