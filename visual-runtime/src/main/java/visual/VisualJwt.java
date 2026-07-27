package visual;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of a JWT and of the choice it forces: hand the client a
 * signed document it carries everywhere, or hand it a meaningless id and keep the
 * truth on the server.
 *
 * <p>A JWT is three base64url segments joined by dots — {@code header.payload.signature}.
 * The first two are readable by anyone holding the token; only the third is evidence.
 * The model makes that concrete: {@link #decode(Token)} prints the claims without any
 * key at all, {@link #tamper(Token, String, String)} edits one and watches the
 * signature stop matching, and {@link #stripSignature(Token)} shows the classic
 * {@code alg: none} forgery that a verifier trusting the header will accept.
 *
 * <p>Every verification walks the same five checks, which is where the two schemes
 * visibly diverge:
 * <ul>
 *   <li><b>parse</b> — is this a well-formed credential at all;</li>
 *   <li><b>source</b> — where the facts come from: decoded locally out of the token,
 *       or read from the session store (one lookup, counted);</li>
 *   <li><b>trust</b> — why we believe them: a signature we recomputed, or the fact
 *       that the id was in our own store;</li>
 *   <li><b>claims</b> — {@code exp} and {@code aud}: still valid, and addressed to us;</li>
 *   <li><b>revoked</b> — is it still <em>supposed</em> to work.</li>
 * </ul>
 *
 * <p>The last check is where a stateless token loses. A logged-out JWT still verifies,
 * and a token minted before a role changed still claims the old role — both show up as
 * {@code decision: "breach"}: the request was served and should not have been. Turning
 * on {@link #denyList()} fixes it by quietly putting the state back.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free.
 */
public class VisualJwt {

    /** How long an issued access credential stays valid, in model minutes. */
    public static final int ACCESS_TTL_MINUTES = 15;

    /** The signing key. Real systems use a long random secret or an RSA/EC private key. */
    public static final String SIGNING_KEY = "auth-service-signing-key";

    /** The checks every verification walks, in order. */
    private static final List<String> CHECKS = List.of("parse", "source", "trust", "claims", "revoked");

    // ----------------------------------------------------------------- records

    /** What the database says about a user <em>right now</em> — the only current truth. */
    private static final class Account {

        private final String name;
        private String role;
        private boolean active = true;

        private Account(String name, String role) {
            this.name = name;
            this.role = role;
        }
    }

    /**
     * The credential the client walks away with: either a compact JWT the client can
     * read, or an opaque id that means nothing outside the server's store.
     */
    public static final class Token {

        private final String kind;
        private final String jti;
        private final String user;
        private final String role;
        private final String audience;
        private final int issuedAt;
        private final int expiresAt;

        private final Map<String, String> header = new LinkedHashMap<>();
        private final Map<String, String> payload = new LinkedHashMap<>();
        private String signature = "";
        private boolean tampered;
        private String raw;

        private Token(String kind, String jti, String user, String role, String audience,
                      int issuedAt, int expiresAt) {
            this.kind = kind;
            this.jti = jti;
            this.user = user;
            this.role = role;
            this.audience = audience;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }

        /** The exact bytes the client puts on the wire. */
        public String value() {
            return raw;
        }

        /** Which user this credential speaks for. */
        public String user() {
            return user;
        }

        /** What the credential itself claims the role is — not necessarily what the database says. */
        public String role() {
            return role;
        }

        /** How many bytes this credential adds to every single request. */
        public int size() {
            return raw == null ? 0 : raw.length();
        }
    }

    // ------------------------------------------------------------------- state

    private final String scheme;
    private boolean checkAudience;
    private boolean denyListEnabled;
    private boolean trustAlgHeader;

    private final Map<String, Account> directory = new LinkedHashMap<>();
    /** What the server remembers. Stays empty in JWT mode — that is the whole point. */
    private final Map<String, Token> sessionStore = new LinkedHashMap<>();
    private final Set<String> denied = new LinkedHashSet<>();
    private final Set<String> loggedOut = new LinkedHashSet<>();
    private final Map<String, Integer> services = new LinkedHashMap<>();
    private final Map<String, String[]> checks = new LinkedHashMap<>();

    private int clock;
    private int counter;

    private Token focus;
    private boolean focusRevealed;

    private String actionKind = "idle";
    private String actionActor = "";
    private String actionDetail = "";
    private Integer status;
    private String decision = "idle";
    private String reason;
    private String detail = "";

    private int issued;
    private int verifications;
    private int accepted;
    private int refused;
    private int breaches;
    private int storeLookups;
    private int bytesOnTheWire;

    private VisualJwt(String scheme) {
        this.scheme = scheme;
        resetChecks();
    }

    /**
     * An issuer that hands out stateless JWTs: the claims travel with the client,
     * signed, and the server keeps nothing.
     */
    public static VisualJwt jwt() {
        VisualJwt model = new VisualJwt("jwt");
        Trace.event("JWT_SETUP",
                "An issuer that hands out JSON Web Tokens. A JWT is three base64url segments joined "
                        + "by dots — header.payload.signature — and it is a BEARER credential the client "
                        + "carries on every request. Nothing about it is stored on the server, so any "
                        + "instance that has the key can check it without asking anybody. Remember the "
                        + "shape: the first two segments are readable by anyone, and only the third is "
                        + "evidence",
                "Издатель, который выдаёт JSON Web Token. JWT — это три base64url-сегмента, "
                        + "соединённые точками: header.payload.signature, — и это предъявительские "
                        + "учётные данные, которые клиент везёт с каждым запросом. На сервере не "
                        + "хранится ничего, поэтому любой инстанс с ключом проверит токен, никого не "
                        + "спрашивая. Запомните форму: первые два сегмента может прочитать кто угодно, и "
                        + "доказательством является только третий",
                List.of("scheme", "client"), model.state());
        return model;
    }

    /**
     * An issuer that hands out opaque session ids: the client holds a meaningless
     * string and the server holds everything behind it.
     */
    public static VisualJwt sessions() {
        VisualJwt model = new VisualJwt("session");
        Trace.event("JWT_SETUP",
                "An issuer that hands out OPAQUE SESSION IDS. The client gets a random string that "
                        + "means nothing anywhere except inside this server's store, and every request "
                        + "costs one lookup to turn it back into a user. That lookup is not overhead you "
                        + "failed to remove — it is the moment the server gets to change its mind, and "
                        + "it is what you are buying",
                "Издатель, который выдаёт НЕПРОЗРАЧНЫЕ ИДЕНТИФИКАТОРЫ СЕССИЙ. Клиент получает "
                        + "случайную строку, которая ничего не значит нигде, кроме хранилища этого "
                        + "сервера, и каждый запрос стоит одного обращения, чтобы превратить её обратно "
                        + "в пользователя. Это обращение — не накладные расходы, которые не смогли "
                        + "убрать: это момент, когда сервер может передумать, и именно за него вы "
                        + "платите",
                List.of("scheme", "server"), model.state());
        return model;
    }

    // ---------------------------------------------------------- configuration

    /** Registers a service that will verify credentials — an API, or another microservice. */
    public VisualJwt service(String name) {
        services.putIfAbsent(name, 0);
        Trace.event("SERVICE_ADDED",
                "'" + name + "' can now verify credentials. "
                        + ("jwt".equals(scheme)
                            ? "It needs only the key (or the issuer's public key) — no connection to "
                              + "the auth service, no shared database, nothing to be down. That is the "
                              + "single strongest argument for JWTs across a trust boundary"
                            : "It needs access to the session store, which means every service here "
                              + "shares one database or one Redis — a dependency and a bottleneck that "
                              + "grows with the number of services"),
                "«" + name + "» теперь может проверять учётные данные. "
                        + ("jwt".equals(scheme)
                            ? "Ему нужен только ключ (или публичный ключ издателя) — ни соединения с "
                              + "сервисом аутентификации, ни общей базы, ничего, что могло бы лежать. "
                              + "Это самый сильный аргумент за JWT через границу доверия"
                            : "Ему нужен доступ к хранилищу сессий, а значит, все сервисы делят одну "
                              + "базу или один Redis — зависимость и узкое место, растущие вместе с "
                              + "числом сервисов"),
                List.of("service:" + name), state());
        return this;
    }

    /** Makes services reject a token that was not addressed to them — the {@code aud} claim. */
    public VisualJwt checkAudience() {
        this.checkAudience = true;
        Trace.event("JWT_SETUP",
                "Services will now check the 'aud' claim and refuse a token addressed to someone "
                        + "else. Without it, a token any service accepts is a token every service "
                        + "accepts: the low-trust reporting API you hand your token to can replay it "
                        + "against payments. 'aud' is what makes a token a message to ONE recipient",
                "Сервисы теперь проверяют claim «aud» и отклоняют токен, адресованный кому-то "
                        + "другому. Без этого токен, который принимает любой сервис, — это токен, "
                        + "который принимают все: низкодоверенный сервис отчётов, которому вы отдали "
                        + "токен, переиграет его против платежей. «aud» — то, что делает токен "
                        + "сообщением ОДНОМУ получателю",
                List.of("scheme"), state());
        return this;
    }

    /** Gives the issuer a revocation list, so a JWT can be cut off before it expires. */
    public VisualJwt denyList() {
        this.denyListEnabled = true;
        Trace.event("JWT_SETUP",
                "The issuer now keeps a deny-list of revoked token ids, and every verification "
                        + "consults it. Be honest about what just happened: statelessness was the reason "
                        + "to pick a JWT, and this puts the state back. It is still a good trade — the "
                        + "list holds only revoked ids until they expire, not every live session — but "
                        + "'stateless JWT with instant revocation' does not exist",
                "Издатель теперь ведёт список отозванных идентификаторов токенов, и каждая проверка "
                        + "с ним сверяется. Скажем честно, что произошло: отсутствие состояния было "
                        + "причиной выбрать JWT — и состояние вернулось. Обмен всё равно выгодный: в "
                        + "списке лежат только отозванные идентификаторы до истечения их срока, а не все "
                        + "живые сессии, — но «JWT без состояния с мгновенным отзывом» не существует",
                List.of("denylist"), state());
        return this;
    }

    /**
     * The classic verification bug: believe whatever the token's own {@code alg} header
     * says instead of the algorithm you chose.
     */
    public VisualJwt trustAlgorithmHeader() {
        this.trustAlgHeader = true;
        Trace.event("JWT_SETUP",
                "This verifier is configured to use whatever algorithm the token's own header names. "
                        + "Read that again: the value being checked is telling the checker how to check "
                        + "it. This is the bug behind the 'alg: none' forgery and behind algorithm "
                        + "confusion, where an RS256 public key — which is public — is fed to an HS256 "
                        + "verifier as if it were a shared secret",
                "Этот проверяющий настроен использовать тот алгоритм, который называет заголовок "
                        + "самого токена. Перечитайте: проверяемое значение указывает проверяющему, как "
                        + "его проверять. Это ошибка, стоящая за подделкой «alg: none» и за algorithm "
                        + "confusion, когда публичный ключ RS256 — а он публичный — скармливают "
                        + "проверяющему HS256 как общий секрет",
                List.of("scheme"), state());
        return this;
    }

    // ------------------------------------------------------------------ issue

    /** Issues a credential for a user with a role, addressed to a single audience. */
    public Token issue(String user, String role, String audience) {
        directory.putIfAbsent(user, new Account(user, role));
        Account account = directory.get(user);
        account.role = role;
        account.active = true;
        beginAction("issue", "auth", user);

        counter++;
        String jti = "id" + counter;
        Token token = new Token(scheme.equals("jwt") ? "jwt" : "opaque", jti, user, role, audience,
                clock, clock + ACCESS_TTL_MINUTES);

        if ("jwt".equals(scheme)) {
            token.header.put("alg", "HS256");
            token.header.put("typ", "JWT");
            token.payload.put("iss", "auth");
            token.payload.put("sub", user);
            token.payload.put("aud", audience);
            token.payload.put("role", role);
            token.payload.put("iat", String.valueOf(token.issuedAt));
            token.payload.put("exp", String.valueOf(token.expiresAt));
            token.payload.put("jti", jti);
            sign(token);
            issued++;
            focus(token);
            this.status = 200;
            this.decision = "allowed";
            markAll("skipped", "");
            Trace.event("TOKEN_ISSUED",
                    "A JWT is signed for '" + user + "' as " + role + ", addressed to " + audience
                            + ", valid until minute " + token.expiresAt + ". The server writes down "
                            + "nothing: it hands over " + token.size() + " bytes and forgets. Note what "
                            + "the token is — a snapshot of facts that were true at minute "
                            + token.issuedAt + ", not a live view of them",
                    "Для «" + user + "» подписывается JWT с ролью " + role + ", адресованный "
                            + audience + " и действительный до минуты " + token.expiresAt
                            + ". Сервер не записывает ничего: он отдаёт " + token.size()
                            + " байт и забывает. Обратите внимание, что такое токен, — это снимок "
                            + "фактов, верных на минуту " + token.issuedAt
                            + ", а не их актуальное состояние",
                    List.of("credential", "client"), state());
            return token;
        }

        token.raw = "sid_" + Integer.toHexString(("session|" + jti).hashCode() & 0xffffff);
        sessionStore.put(token.raw, token);
        issued++;
        focus(token);
        this.status = 200;
        this.decision = "allowed";
        markAll("skipped", "");
        Trace.event("SESSION_ISSUED",
                "A session record is created for '" + user + "' and the client is handed the id "
                        + token.raw + " — " + token.size() + " bytes that say nothing at all. Everything "
                        + "the id stands for lives on the server, so the client can neither read nor "
                        + "edit any of it, and the server can drop the whole record whenever it likes",
                "Для «" + user + "» создаётся запись о сессии, а клиенту отдаётся идентификатор "
                        + token.raw + " — " + token.size() + " байт, не говорящих ровно ничего. Всё, что "
                        + "за ним стоит, живёт на сервере, поэтому клиент не может это ни прочитать, ни "
                        + "изменить, а сервер может выбросить всю запись когда угодно",
                List.of("credential", "server"), state());
        return token;
    }

    /** Issues a credential addressed to the single service registered so far, or to {@code api}. */
    public Token issue(String user, String role) {
        return issue(user, role, services.size() == 1 ? services.keySet().iterator().next() : "api");
    }

    // ----------------------------------------------------------------- decode

    /** Reads the credential without any key — what a client, a proxy or a thief can see. */
    public void decode(Token token) {
        if (token == null) {
            return;
        }
        beginAction("decode", "anyone", token.jti);
        focus(token);
        if (!"jwt".equals(token.kind)) {
            this.status = null;
            this.decision = "allowed";
            Trace.event("NOTHING_TO_READ",
                    "Decoding '" + token.raw + "' produces nothing. An opaque id is not encoded, it is "
                            + "MEANINGLESS: it carries no user, no role and no expiry, because all of "
                            + "that stayed on the server. Whoever steals it learns only that they hold a "
                            + "session id — and a token they steal instead tells them who the user is, "
                            + "what they may do and where it is accepted",
                    "Расшифровка «" + token.raw + "» не даёт ничего. Непрозрачный идентификатор не "
                            + "закодирован, он БЕССМЫСЛЕН: в нём нет ни пользователя, ни роли, ни срока "
                            + "действия, потому что всё это осталось на сервере. Тот, кто его украдёт, "
                            + "узнает только, что держит идентификатор сессии, — а украв вместо этого "
                            + "токен, он узнает, кто пользователь, что ему можно и где это принимают",
                    List.of("credential"), state());
            return;
        }
        focusRevealed = true;
        this.status = null;
        this.decision = "allowed";
        Trace.event("TOKEN_DECODED",
                "No key, no permission, no server: base64url is an ENCODING, not encryption, so the "
                        + "header " + json(token.header) + " and the payload " + json(token.payload)
                        + " read straight off the wire. Anything you would not print in a log has no "
                        + "business in a JWT — and 'the client cannot see this claim' is never true",
                "Ни ключа, ни разрешения, ни сервера: base64url — это КОДИРОВАНИЕ, а не шифрование, "
                        + "поэтому заголовок " + json(token.header) + " и полезная нагрузка "
                        + json(token.payload) + " читаются прямо с провода. Всему, что вы не стали бы "
                        + "печатать в лог, в JWT не место, а «клиент не увидит этот claim» не бывает "
                        + "правдой никогда",
                List.of("credential", "payload"), state());
    }

    // -------------------------------------------------------------- forgeries

    /** The client edits a claim and keeps the signature it was given. */
    public Token tamper(Token token, String claim, String value) {
        if (token == null || !"jwt".equals(token.kind)) {
            return token;
        }
        beginAction("tamper", "client", claim + "=" + value);
        Token forged = copyOf(token);
        forged.payload.put(claim, value);
        forged.tampered = true;
        forged.raw = encode(json(forged.header)) + "." + encode(json(forged.payload)) + "."
                + forged.signature;
        focus(forged);
        focusRevealed = true;
        this.status = null;
        this.decision = "pending";
        Trace.event("TOKEN_TAMPERED",
                "The client rewrites '" + claim + "' to \"" + value + "\" and sends the token back "
                        + "with the SAME signature — editing the payload is trivial, it is just text. "
                        + "The signature was computed over the old bytes, so it no longer describes "
                        + "these ones. This is exactly why a claim is evidence only after verification",
                "Клиент переписывает «" + claim + "» в «" + value + "» и отправляет токен обратно с "
                        + "ТОЙ ЖЕ подписью: править payload элементарно, это просто текст. Подпись "
                        + "считалась по старым байтам и эти байты больше не описывает. Именно поэтому "
                        + "claim становится доказательством только после проверки",
                List.of("credential", "payload"), state());
        return forged;
    }

    /** Rewrites the header to {@code alg: none} and throws the signature away. */
    public Token stripSignature(Token token) {
        if (token == null || !"jwt".equals(token.kind)) {
            return token;
        }
        beginAction("tamper", "client", "alg=none");
        Token forged = copyOf(token);
        forged.header.put("alg", "none");
        forged.payload.put("role", "admin");
        forged.signature = "";
        forged.tampered = true;
        forged.raw = encode(json(forged.header)) + "." + encode(json(forged.payload)) + ".";
        focus(forged);
        focusRevealed = true;
        this.status = null;
        this.decision = "pending";
        Trace.event("TOKEN_TAMPERED",
                "The attacker sets 'alg' to \"none\", claims role=admin and deletes the signature "
                        + "entirely. 'none' is a real registered JWT algorithm, meant for tokens already "
                        + "protected by something else — and a verifier that obeys the header will "
                        + "happily conclude there is nothing to check",
                "Злоумышленник ставит «alg» в «none», объявляет role=admin и полностью удаляет "
                        + "подпись. «none» — настоящий зарегистрированный алгоритм JWT, предназначенный "
                        + "для токенов, защищённых чем-то ещё, — и проверяющий, который слушается "
                        + "заголовка, с радостью заключит, что проверять нечего",
                List.of("credential", "header"), state());
        return forged;
    }

    // ----------------------------------------------------------- verification

    /** The whole check, run by one service against one presented credential. */
    public void verifyAt(String serviceName, Token token) {
        services.putIfAbsent(serviceName, 0);
        services.put(serviceName, services.get(serviceName) + 1);
        beginAction("verify", serviceName, token == null ? "" : token.jti);
        verifications++;
        if (token != null) {
            focus(token);
            bytesOnTheWire += token.size();
        }

        if (token == null) {
            mark("parse", "failed", "nothing presented");
            refuse(401, "no-credential", "");
            Trace.event("ACCESS_DENIED",
                    "Nothing was presented, so '" + serviceName + "' does not know who this is and "
                            + "answers 401. Every request re-authenticates itself from scratch: there "
                            + "is no logged-in connection, only a value attached to each call",
                    "Ничего не предъявлено, поэтому «" + serviceName + "» не знает, кто это, и "
                            + "отвечает 401. Каждый запрос аутентифицируется заново: залогиненного "
                            + "соединения не бывает, есть только значение, приложенное к каждому вызову",
                    List.of("service:" + serviceName), state());
            return;
        }

        mark("parse", "passed", "jwt".equals(token.kind) ? "3 segments" : "opaque id");

        // --- source: where do the facts come from?
        if ("jwt".equals(token.kind)) {
            mark("source", "passed", "decoded locally");
        } else {
            storeLookups++;
            Trace.event("STORE_LOOKUP",
                    "'" + serviceName + "' cannot tell anything from " + token.raw
                            + " on its own, so it asks the session store — lookup number "
                            + storeLookups + " of this run. That is a network hop on the path of every "
                            + "single request, and a shared component every service depends on. It is "
                            + "also the reason the next few checks can be answered with today's truth",
                    "«" + serviceName + "» сам по себе ничего не может сказать про " + token.raw
                            + ", поэтому обращается к хранилищу сессий — обращение номер " + storeLookups
                            + " за этот прогон. Это сетевой прыжок на пути каждого запроса и общий "
                            + "компонент, от которого зависят все сервисы. Он же — причина, по которой "
                            + "следующие проверки отвечают сегодняшней правдой",
                    List.of("service:" + serviceName, "store"), state());
            if (!sessionStore.containsKey(token.raw)) {
                mark("source", "failed", "no such session");
                refuse(401, "unknown-session", token.raw);
                Trace.event("ACCESS_DENIED",
                        "The store has no record for " + token.raw + ", so this is nobody and the "
                                + "request is refused. Whatever ended that session — a logout, an admin "
                                + "cutting off the account, a password change — took effect on THIS "
                                + "request, with no window at all",
                        "В хранилище нет записи для " + token.raw
                                + ", значит, это никто, и запрос отклоняется. Что бы ни завершило эту "
                                + "сессию — выход, отключение аккаунта администратором, смена пароля, — "
                                + "это подействовало на ЭТОТ запрос, вообще без окна",
                        List.of("service:" + serviceName, "store"), state());
                return;
            }
            mark("source", "passed", "found in store");
        }

        // --- trust: why should we believe the facts?
        if ("jwt".equals(token.kind)) {
            String declared = token.header.get("alg");
            if ("none".equals(declared)) {
                if (trustAlgHeader) {
                    mark("trust", "breach", "alg: none");
                    grantWrongly("alg-none", token, serviceName);
                    Trace.event("ALG_NONE_ACCEPTED",
                            "The verifier read 'alg' from the token, found \"none\", and concluded "
                                    + "there was nothing to verify — so an unsigned document the "
                                    + "attacker typed is now an identity, with role=admin. The fix is "
                                    + "one line: the SERVER decides the algorithm and the key, and a "
                                    + "token whose header disagrees is rejected before anything else",
                            "Проверяющий прочитал «alg» из токена, увидел «none» и заключил, что "
                                    + "проверять нечего, — и неподписанный документ, набранный "
                                    + "злоумышленником, стал личностью с role=admin. Починка в одну "
                                    + "строку: алгоритм и ключ выбирает СЕРВЕР, а токен, чей заголовок с "
                                    + "этим не согласен, отклоняется раньше всего остального",
                            List.of("service:" + serviceName, "check:trust"), state());
                    return;
                }
                mark("trust", "failed", "alg: none");
                refuse(401, "bad-signature", "alg=none");
                Trace.event("ALG_NONE_REJECTED",
                        "The token asks to be checked with \"none\" and the verifier does not care "
                                + "what the token asks: it was told to expect HS256 and only HS256, so "
                                + "an unsigned token is not a token. Pin the algorithm on the server "
                                + "side — never read it out of the value you are validating",
                        "Токен просит проверить себя алгоритмом «none», а проверяющему всё равно, о "
                                + "чём просит токен: ему сказано ждать HS256 и только HS256, поэтому "
                                + "неподписанный токен — не токен. Алгоритм закрепляют на стороне "
                                + "сервера и никогда не читают из проверяемого значения",
                        List.of("service:" + serviceName, "check:trust"), state());
                return;
            }
            String expected = signatureOf(token);
            if (!expected.equals(token.signature)) {
                mark("trust", "failed", "signature mismatch");
                refuse(401, "bad-signature", token.signature + " != " + expected);
                Trace.event("SIGNATURE_INVALID",
                        "'" + serviceName + "' recomputed the signature over the bytes it actually "
                                + "received and got " + expected + ", not " + token.signature
                                + " — so the payload changed after signing and the whole token is "
                                + "discarded. The client could edit the claims freely; what it could not "
                                + "do is produce a signature without the key. That, and nothing else, is "
                                + "what makes a claim trustworthy",
                        "«" + serviceName + "» пересчитал подпись по фактически полученным байтам и "
                                + "получил " + expected + ", а не " + token.signature
                                + ", — значит, payload изменили после подписания, и токен целиком "
                                + "отбрасывается. Править claims клиент мог сколько угодно; чего он не "
                                + "мог — это выпустить подпись без ключа. Именно это, и ничто другое, "
                                + "делает claim заслуживающим доверия",
                        List.of("service:" + serviceName, "check:trust", "credential"), state());
                return;
            }
            mark("trust", "passed", "signature ok");
            Trace.event("SIGNATURE_VERIFIED",
                    "'" + serviceName + "' rebuilt the signature from header.payload with its copy of "
                            + "the key and got " + token.signature
                            + " — a match, so these claims really were issued by 'auth' and have not "
                            + "been touched since. No database was involved, no network call was made; "
                            + "this is the property you buy a JWT for",
                    "«" + serviceName + "» пересобрал подпись из header.payload своей копией ключа и "
                            + "получил " + token.signature
                            + " — совпадение, значит, эти claims действительно выпустил «auth» и с тех "
                            + "пор их не трогали. Ни базы, ни сетевого вызова: ради этого свойства JWT и "
                            + "берут",
                    List.of("service:" + serviceName, "check:trust"), state());
        } else {
            mark("trust", "passed", "our own record");
        }

        // --- claims: expiry and audience
        if (clock >= token.expiresAt) {
            mark("claims", "failed", "exp=" + token.expiresAt);
            refuse(401, "expired", "minute " + clock);
            Trace.event("TOKEN_EXPIRED",
                    "The credential is good until minute " + token.expiresAt + " and it is minute "
                            + clock + ", so it is refused. Nobody called anything and nothing changed on "
                            + "the server — time alone did it. For a stateless token this is the ONLY "
                            + "thing that reliably ends it, which is why 'exp' is short and why the "
                            + "answer to 'how long can a revoked JWT keep working' is 'until exp'",
                    "Учётные данные действительны до минуты " + token.expiresAt + ", а сейчас минута "
                            + clock + " — отказ. Никто ничего не вызывал и на сервере ничего не "
                            + "менялось, всё сделало время. Для токена без состояния это ЕДИНСТВЕННОЕ, "
                            + "что надёжно его завершает: поэтому «exp» делают коротким, и поэтому ответ "
                            + "на вопрос «сколько ещё будет работать отозванный JWT» — «до exp»",
                    List.of("service:" + serviceName, "check:claims"), state());
            return;
        }
        if (checkAudience && token.audience != null && !token.audience.equals(serviceName)) {
            mark("claims", "failed", "aud=" + token.audience);
            refuse(403, "wrong-audience", serviceName);
            Trace.event("AUDIENCE_MISMATCH",
                    "The signature is perfectly valid and the token is still refused: 'aud' says "
                            + token.audience + " and this is '" + serviceName
                            + "'. A valid signature only means 'auth issued this' — it never means "
                            + "'issued FOR YOU'. Without this check, handing your token to one service "
                            + "lets that service act as you against every other one",
                    "Подпись совершенно корректна, и токен всё равно отклонён: в «aud» написано "
                            + token.audience + ", а это «" + serviceName
                            + "». Корректная подпись означает только «это выпустил auth» и никогда — "
                            + "«выпустил ДЛЯ ВАС». Без этой проверки, отдав токен одному сервису, вы "
                            + "позволяете ему действовать от вашего имени против всех остальных",
                    List.of("service:" + serviceName, "check:claims"), state());
            return;
        }
        mark("claims", "passed", "exp=" + token.expiresAt
                + (token.audience == null ? "" : ", aud=" + token.audience));

        // --- revoked: is it still supposed to work?
        if ("jwt".equals(token.kind)) {
            if (denyListEnabled && denied.contains(token.jti)) {
                mark("revoked", "failed", token.jti);
                refuse(401, "revoked", token.jti);
                Trace.event("DENYLIST_HIT",
                        "The signature checks out, the token has not expired, and it is refused anyway "
                                + "because " + token.jti + " is on the deny-list. This is how a JWT "
                                + "system gets real revocation — and notice the cost: a lookup on every "
                                + "request, which is the thing statelessness was supposed to save. The "
                                + "list is at least small, holding only revoked ids until their exp",
                        "Подпись сходится, срок не истёк — и токен всё равно отклонён, потому что "
                                + token.jti + " в списке отозванных. Так система с JWT получает "
                                + "настоящий отзыв, — и обратите внимание на цену: обращение к "
                                + "хранилищу на каждом запросе, ровно то, что должно было сэкономить "
                                + "отсутствие состояния. Список хотя бы маленький: в нём только "
                                + "отозванные идентификаторы до их exp",
                        List.of("service:" + serviceName, "denylist"), state());
                return;
            }
            if (loggedOut.contains(token.jti)) {
                mark("revoked", "breach", "nothing checks this");
                grantWrongly("revoked-token", token, serviceName);
                Trace.event("REVOKED_TOKEN_ACCEPTED",
                        "'" + token.user + "' pressed log out and this token still works. Every check "
                                + "a stateless verifier performs — signature, exp, aud — passes, and "
                                + "none of them can express 'the user asked us to stop'. There is "
                                + "nothing on the server to delete. Short 'exp' plus a revoked refresh "
                                + "token, or a deny-list, are the only real answers",
                        "«" + token.user + "» нажал «выйти», а токен по-прежнему работает. Все "
                                + "проверки, которые делает проверяющий без состояния, — подпись, exp, "
                                + "aud — проходят, и ни одна не умеет выразить «пользователь попросил "
                                + "прекратить». Удалять на сервере нечего. Единственные настоящие "
                                + "ответы — короткий «exp» вместе с отзывом refresh-токена или "
                                + "список отозванных",
                        List.of("service:" + serviceName, "check:revoked", "credential"), state());
                return;
            }
            mark("revoked", denyListEnabled ? "passed" : "skipped",
                    denyListEnabled ? "not on the list" : "no list kept");
        } else {
            mark("revoked", "passed", "the record still exists");
        }

        // --- identity: and is what it says still true?
        Account account = directory.get(token.user);
        if ("jwt".equals(token.kind)) {
            if (account != null && (!account.active || !account.role.equals(token.role))) {
                mark("claims", "breach", "role=" + token.role);
                grantWrongly("stale-claim", token, serviceName);
                Trace.event("STALE_CLAIM_ACCEPTED",
                        "The token says role=" + token.role + " and the database says "
                                + (account.active ? "role=" + account.role : "this account is disabled")
                                + ". The token wins, because it is the only thing the verifier looked "
                                + "at: a JWT is a snapshot taken at minute " + token.issuedAt
                                + ", and every claim in it is stale by design until 'exp'. This is the "
                                + "question to ask about any JWT design — how wrong may a claim be, and "
                                + "for how long?",
                        "В токене написано role=" + token.role + ", а в базе — "
                                + (account.active ? "role=" + account.role : "аккаунт отключён")
                                + ". Побеждает токен, потому что больше проверяющий ни на что и не "
                                + "смотрел: JWT — это снимок, сделанный на минуте " + token.issuedAt
                                + ", и каждый claim в нём устаревает by design до самого «exp». Это и "
                                + "есть вопрос к любой схеме с JWT: насколько неверным может быть claim "
                                + "и как долго?",
                        List.of("service:" + serviceName, "truth", "credential"), state());
                return;
            }
        } else if (account != null && !account.active) {
            mark("revoked", "failed", "account disabled");
            refuse(403, "account-disabled", token.user);
            Trace.event("ACCESS_DENIED",
                    "The session id is valid and the request is refused anyway: the lookup returned "
                            + "the user's CURRENT row, and that row says the account is disabled. The "
                            + "server did not have to remember to revoke anything — it reads the truth "
                            + "on every request, so there is no window in which the old answer survives",
                    "Идентификатор сессии корректен, и запрос всё равно отклонён: обращение вернуло "
                            + "ТЕКУЩУЮ строку пользователя, а в ней сказано, что аккаунт отключён. "
                            + "Серверу не пришлось помнить, что нужно что-то отозвать: он читает правду "
                            + "на каждом запросе, поэтому окна, в котором доживает старый ответ, просто "
                            + "нет",
                    List.of("service:" + serviceName, "truth"), state());
            return;
        }

        String effectiveRole = account == null ? token.role
                : ("jwt".equals(token.kind) ? token.role : account.role);
        this.status = 200;
        this.decision = "allowed";
        this.detail = token.user + " / " + effectiveRole;
        accepted++;
        Trace.event("ACCESS_GRANTED",
                "'" + serviceName + "' serves the request as " + token.user + " with role "
                        + effectiveRole + ". "
                        + ("jwt".equals(token.kind)
                            ? "It reached that answer with zero lookups — the identity came out of the "
                              + "token and the key it already had, which is why this scales to any "
                              + "number of instances and services"
                            : "It reached that answer through the store, which is why the role is "
                              + "whatever the database says right now, not what it said at login"),
                "«" + serviceName + "» обслуживает запрос как " + token.user + " с ролью "
                        + effectiveRole + ". "
                        + ("jwt".equals(token.kind)
                            ? "Он пришёл к этому ответу без единого обращения к хранилищу: личность "
                              + "получена из токена и уже имевшегося ключа — поэтому такое "
                              + "масштабируется на любое число инстансов и сервисов"
                            : "Он пришёл к этому ответу через хранилище — поэтому роль здесь такая, "
                              + "какая записана в базе прямо сейчас, а не какая была на момент входа"),
                List.of("service:" + serviceName, "check:revoked"), state());
    }

    // ---------------------------------------------------- the world moves on

    /** The database changes. Whether anyone notices depends entirely on the scheme. */
    public void changeRole(String user, String role) {
        Account account = directory.computeIfAbsent(user, name -> new Account(name, role));
        String was = account.role;
        account.role = role;
        beginAction("truth", "admin", user + ": " + was + " → " + role);
        this.decision = "allowed";
        Trace.event("ROLE_CHANGED",
                "'" + user + "' goes from " + was + " to " + role + " in the database. "
                        + ("jwt".equals(scheme)
                            ? "Not one byte of any token already in the wild has changed, and nothing "
                              + "will tell them: the tokens keep saying " + was + " until they expire"
                            : "Nothing needs to be pushed anywhere — the next request looks the user up "
                              + "and gets the new row"),
                "«" + user + "» в базе меняется с " + was + " на " + role + ". "
                        + ("jwt".equals(scheme)
                            ? "Ни один байт уже выданных токенов не изменился, и сообщить им нечем: они "
                              + "продолжают утверждать " + was + " до самого истечения срока"
                            : "Ничего никуда рассылать не нужно: следующий запрос найдёт пользователя и "
                              + "получит новую строку"),
                List.of("truth"), state());
    }

    /** The account is switched off — the "cut this person off right now" case. */
    public void deactivate(String user) {
        Account account = directory.get(user);
        if (account == null) {
            return;
        }
        account.active = false;
        beginAction("truth", "admin", user + ": disabled");
        this.decision = "allowed";
        Trace.event("ROLE_CHANGED",
                "'" + user + "' is disabled in the database — the request every security team makes "
                        + "at some point is 'cut this person off, now'. "
                        + ("jwt".equals(scheme)
                            ? "Ask what 'now' means in this system before you answer: with plain "
                              + "stateless tokens it means 'when the last issued token expires'"
                            : "Here 'now' means the next request, because there is a lookup to fail"),
                "«" + user + "» отключён в базе — запрос, который рано или поздно делает любая "
                        + "команда безопасности: «отрежьте этого человека, немедленно». "
                        + ("jwt".equals(scheme)
                            ? "Прежде чем отвечать, выясните, что в этой системе значит «немедленно»: с "
                              + "обычными токенами без состояния это значит «когда истечёт последний "
                              + "выданный токен»"
                            : "Здесь «немедленно» значит «на следующем запросе», потому что есть "
                              + "обращение к хранилищу, которое может не найтись"),
                List.of("truth"), state());
    }

    /** Ends the login. What that costs is the whole interview question. */
    public void logout(Token token) {
        if (token == null) {
            return;
        }
        beginAction("logout", token.user, token.jti);
        loggedOut.add(token.jti);
        this.status = 200;
        this.decision = "allowed";
        if (!"jwt".equals(token.kind)) {
            sessionStore.remove(token.raw);
            Trace.event("LOGGED_OUT",
                    "The session record is deleted, so the very next request presenting " + token.raw
                            + " finds nothing behind it. Logging out is one DELETE and it is immediate. "
                            + "That is what the per-request lookup was for",
                    "Запись о сессии удалена, поэтому следующий же запрос с " + token.raw
                            + " не найдёт за ним ничего. Выход — это один DELETE, и он мгновенный. "
                            + "Именно ради этого и было обращение к хранилищу на каждом запросе",
                    List.of("store", "credential"), state());
            return;
        }
        if (denyListEnabled) {
            denied.add(token.jti);
            Trace.event("LOGGED_OUT",
                    token.jti + " goes on the deny-list, and it can come off again once its 'exp' has "
                            + "passed — after that the signature check refuses it for free. So the list "
                            + "stays proportional to recent logouts rather than to live users, which is "
                            + "the honest version of 'stateless': much less state, not none",
                    token.jti + " попадает в список отозванных, и его можно будет оттуда убрать, как "
                            + "только пройдёт его «exp»: дальше проверка подписи отклонит его бесплатно. "
                            + "Поэтому список пропорционален недавним выходам, а не числу живых "
                            + "пользователей, — это и есть честная версия «без состояния»: состояния "
                            + "сильно меньше, но не ноль",
                    List.of("denylist", "credential"), state());
            return;
        }
        Trace.event("LOGGED_OUT",
                "The client drops its copy of the token and the server does nothing, because there "
                        + "is nothing to do: it never recorded that the token existed. 'Log out' here "
                        + "is a button that clears local storage — any copy taken from a log, a proxy "
                        + "or a shared machine keeps working until 'exp'",
                "Клиент выбрасывает свою копию токена, а сервер не делает ничего, потому что делать "
                        + "нечего: он никогда и не записывал, что этот токен существует. «Выйти» здесь "
                        + "— это кнопка, очищающая local storage; любая копия, снятая из лога, с прокси "
                        + "или с общей машины, продолжит работать до «exp»",
                List.of("credential"), state());
    }

    /** Moves the clock forward, so credentials can reach their expiry. */
    public void advanceMinutes(int minutes) {
        clock += minutes;
        beginAction("clock", "", minutes + " min");
        this.decision = "allowed";
        Trace.event("TIME_PASSED",
                minutes + " minute(s) pass; the clock is now at minute " + clock
                        + ". Nothing was called and nothing changed on the server — and for a stateless "
                        + "token, time is the only force that can end it",
                "Проходит минут: " + minutes + "; на часах теперь минута " + clock
                        + ". Никто ничего не вызывал и на сервере ничего не менялось — а для токена без "
                        + "состояния время является единственной силой, способной его завершить",
                List.of("clock"), state());
    }

    /** Prints what the whole run added up to. */
    public void report() {
        beginAction("audit", "", "");
        this.decision = "allowed";
        Trace.event("JWT_AUDIT",
                "After the run: credentials issued " + issued + ", verifications " + verifications
                        + ", served " + accepted + ", refused " + refused + ", store lookups "
                        + storeLookups + ", credential bytes on the wire " + bytesOnTheWire
                        + ", requests served that should NOT have been " + breaches
                        + ". Read those last three together — they are the trade in numbers: a JWT buys "
                        + "you zero lookups and pays for it in bytes and in requests served on stale or "
                        + "revoked facts",
                "Итоги прогона: выдано учётных данных " + issued + ", проверок " + verifications
                        + ", обслужено " + accepted + ", отклонено " + refused
                        + ", обращений к хранилищу " + storeLookups + ", байт учётных данных на проводе "
                        + bytesOnTheWire + ", обслужено запросов, которые обслуживать НЕ следовало: "
                        + breaches + ". Читайте последние три вместе — это и есть обмен в цифрах: JWT "
                        + "даёт ноль обращений к хранилищу и платит за это байтами и запросами, "
                        + "обслуженными по устаревшим или отозванным фактам",
                List.of(), state());
    }

    // --------------------------------------------------------------- internals

    private Token copyOf(Token token) {
        Token copy = new Token(token.kind, token.jti, token.user, token.role, token.audience,
                token.issuedAt, token.expiresAt);
        copy.header.putAll(token.header);
        copy.payload.putAll(token.payload);
        copy.signature = token.signature;
        copy.raw = token.raw;
        return copy;
    }

    private void sign(Token token) {
        String signingInput = encode(json(token.header)) + "." + encode(json(token.payload));
        token.signature = mac(signingInput);
        token.raw = signingInput + "." + token.signature;
    }

    private String signatureOf(Token token) {
        return mac(encode(json(token.header)) + "." + encode(json(token.payload)));
    }

    /**
     * A stand-in for HMAC-SHA256: deterministic, so every example traces the same way.
     * Real code uses a library and a constant-time comparison.
     */
    private static String mac(String signingInput) {
        return Integer.toHexString((SIGNING_KEY + "." + signingInput).hashCode() & 0x7fffffff);
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(field.getKey()).append("\":");
            String value = field.getValue();
            if (value != null && value.matches("-?\\d+")) {
                sb.append(value);
            } else {
                sb.append('"').append(value).append('"');
            }
        }
        return sb.append('}').toString();
    }

    /** A JWT's segments are always readable; an opaque id has nothing to show. */
    private void focus(Token token) {
        this.focus = token;
        this.focusRevealed = "jwt".equals(token.kind);
    }

    private void beginAction(String kind, String actor, String detail) {
        actionKind = kind;
        actionActor = actor;
        actionDetail = detail;
        status = null;
        decision = "pending";
        reason = null;
        this.detail = "";
        resetChecks();
    }

    private void refuse(int httpStatus, String why, String what) {
        this.status = httpStatus;
        this.decision = "denied";
        this.reason = why;
        this.detail = what;
        refused++;
    }

    private void grantWrongly(String why, Token token, String serviceName) {
        this.status = 200;
        this.decision = "breach";
        this.reason = why;
        this.detail = serviceName + " served " + token.user;
        accepted++;
        breaches++;
    }

    private void resetChecks() {
        checks.clear();
        for (String id : CHECKS) {
            checks.put(id, new String[]{"pending", ""});
        }
    }

    private void markAll(String status, String detail) {
        for (String id : CHECKS) {
            checks.put(id, new String[]{status, detail});
        }
    }

    private void mark(String id, String status, String detail) {
        checks.put(id, new String[]{status, detail});
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("scheme", scheme);
        s.put("clock", clock);
        s.put("accessTtl", ACCESS_TTL_MINUTES);
        s.put("checkAudience", checkAudience);
        s.put("denyListEnabled", denyListEnabled);
        s.put("trustAlgHeader", trustAlgHeader);

        List<Object> serviceList = new ArrayList<>();
        for (Map.Entry<String, Integer> service : services.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", service.getKey());
            v.put("verifications", service.getValue());
            serviceList.add(v);
        }
        s.put("services", serviceList);

        s.put("credential", focus == null ? null : credentialSnapshot(focus));

        List<Object> store = new ArrayList<>();
        for (Token session : sessionStore.values()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", session.raw);
            v.put("user", session.user);
            v.put("expiresAt", session.expiresAt);
            store.add(v);
        }
        s.put("sessionStore", store);
        s.put("denied", new ArrayList<>(denied));

        List<Object> truth = new ArrayList<>();
        for (Account account : directory.values()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("user", account.name);
            v.put("role", account.role);
            v.put("active", account.active);
            truth.add(v);
        }
        s.put("truth", truth);

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("kind", actionKind);
        action.put("actor", actionActor);
        action.put("detail", actionDetail);
        s.put("action", action);

        List<Object> checkList = new ArrayList<>();
        for (Map.Entry<String, String[]> check : checks.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", check.getKey());
            v.put("status", check.getValue()[0]);
            v.put("detail", check.getValue()[1]);
            checkList.add(v);
        }
        s.put("checks", checkList);

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("status", status);
        outcome.put("decision", decision);
        outcome.put("reason", reason);
        outcome.put("detail", detail);
        s.put("outcome", outcome);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("issued", issued);
        stats.put("verifications", verifications);
        stats.put("accepted", accepted);
        stats.put("refused", refused);
        stats.put("storeLookups", storeLookups);
        stats.put("bytesOnTheWire", bytesOnTheWire);
        stats.put("breaches", breaches);
        s.put("stats", stats);
        return s;
    }

    private Map<String, Object> credentialSnapshot(Token token) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("kind", token.kind);
        c.put("raw", token.raw);
        c.put("size", token.size());
        c.put("revealed", focusRevealed);
        c.put("tampered", token.tampered);
        c.put("signature", token.signature);
        c.put("header", claimList(token.header));
        c.put("payload", claimList(token.payload));
        return c;
    }

    private static List<Object> claimList(Map<String, String> fields) {
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("k", field.getKey());
            v.put("v", field.getValue());
            list.add(v);
        }
        return list;
    }
}
