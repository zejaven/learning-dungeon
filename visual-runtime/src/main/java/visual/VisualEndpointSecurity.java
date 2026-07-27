package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A <em>teaching model</em> of the security scheme that sits in front of a set of
 * HTTP endpoints: the ordered rule list that decides which endpoint needs what,
 * and the chain of gates every request walks before a handler is allowed to run.
 *
 * <p>The gates run in the order a real filter chain runs them, and each one has a
 * single question to answer:
 * <ul>
 *   <li><b>transport</b> — is this connection encrypted? A credential sent over
 *       plain HTTP is already compromised;</li>
 *   <li><b>rateLimit</b> — has this client asked too often? Cheap to check, and it
 *       protects the expensive gates behind it;</li>
 *   <li><b>routing</b> — which rule covers this method and path? The answer for an
 *       endpoint nobody wrote a rule for is what the <em>default stance</em> decides;</li>
 *   <li><b>authentication</b> — <em>who</em> is calling? Only a verified token
 *       answers that; a header the client set answers nothing;</li>
 *   <li><b>authorization</b> — may <em>this</em> caller use this endpoint at all
 *       (role), and may <em>this token</em> be used for it (scope)?</li>
 *   <li><b>ownership</b> — may this caller touch <em>this particular record</em>?
 *       The check every role-based scheme forgets;</li>
 *   <li><b>handler</b> — the business code, which runs only once every gate agreed.</li>
 * </ul>
 *
 * <p>The model exists to make the failure modes visible as well as the happy path:
 * an endpoint exposed because no rule matched it, a rule made unreachable by a
 * broader rule in front of it, a token that was decoded but never verified, an
 * identity header trusted from the internet, and a caller who passes the role
 * check and then reads somebody else's record. Those show up as
 * {@code decision: "breach"} — the handler ran, and it should not have.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free.
 */
public class VisualEndpointSecurity {

    /** The issuer the API trusts; a token from anywhere else is not evidence of anything. */
    public static final String TRUSTED_ISSUER = "https://auth.example.com";
    /** The audience this API answers to; a token minted for another API must not work here. */
    public static final String EXPECTED_AUDIENCE = "orders-api";

    /** The gates, in the order the chain runs them. */
    private static final List<String> GATES = List.of(
            "transport", "rateLimit", "routing", "authentication", "authorization",
            "ownership", "handler");

    // ------------------------------------------------------------------ access

    /** What a rule demands of a caller before the handler may run. */
    public static final class Access {

        private final String kind;
        private final String role;
        private final List<String> scopes = new ArrayList<>();
        private boolean ownerOnly;

        private Access(String kind, String role) {
            this.kind = kind;
            this.role = role;
        }

        /** Anyone, signed in or not — a deliberately public endpoint. */
        public static Access permitAll() {
            return new Access("permitAll", null);
        }

        /** Any caller the API could identify from a verified token. */
        public static Access authenticated() {
            return new Access("authenticated", null);
        }

        /** A caller whose verified token says they hold this role. */
        public static Access hasRole(String role) {
            return new Access("hasRole", role);
        }

        /** Also require these scopes — what the TOKEN may do, not what the user may do. */
        public Access scope(String... scopes) {
            for (String scope : scopes) {
                this.scopes.add(scope);
            }
            return this;
        }

        /** Also require that the caller owns the record being addressed (object-level check). */
        public Access ownerOnly() {
            this.ownerOnly = true;
            return this;
        }

        private boolean isPublic() {
            return "permitAll".equals(kind);
        }

        private String describeEn() {
            StringBuilder sb = new StringBuilder(switch (kind) {
                case "permitAll" -> "anyone";
                case "hasRole" -> "role " + role;
                default -> "any authenticated caller";
            });
            if (!scopes.isEmpty()) {
                sb.append(" + scope ").append(String.join(", ", scopes));
            }
            if (ownerOnly) {
                sb.append(" + owner of the record");
            }
            return sb.toString();
        }

        private String describeRu() {
            StringBuilder sb = new StringBuilder(switch (kind) {
                case "permitAll" -> "кто угодно";
                case "hasRole" -> "роль " + role;
                default -> "любой аутентифицированный вызывающий";
            });
            if (!scopes.isEmpty()) {
                sb.append(" + scope ").append(String.join(", ", scopes));
            }
            if (ownerOnly) {
                sb.append(" + владелец записи");
            }
            return sb.toString();
        }
    }

    /** One entry of the ordered rule list: a method + path pattern and its requirement. */
    private static final class Rule {

        private final String method;
        private final String pattern;
        private final Access access;
        private boolean shadowed;

        private Rule(String method, String pattern, Access access) {
            this.method = method;
            this.pattern = pattern;
            this.access = access;
        }

        private boolean matches(String requestMethod, String path) {
            if (method != null && !method.equals(requestMethod)) {
                return false;
            }
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                return path.equals(prefix) || path.startsWith(prefix + "/");
            }
            return pattern.equals(path);
        }

        private String label() {
            return (method == null ? "ANY" : method) + " " + pattern;
        }
    }

    // ------------------------------------------------------------------- token

    /**
     * A bearer token as the API receives it. A fresh one is exactly what the
     * trusted issuer would have minted; the mutators below produce the tokens an
     * attacker (or a clock, or a copy-paste from another environment) produces.
     */
    public static final class Token {

        private final String subject;
        private final List<String> roles = new ArrayList<>();
        private final List<String> scopes = new ArrayList<>();
        private boolean signatureValid = true;
        private boolean expired;
        private String issuer = TRUSTED_ISSUER;
        private String audience = EXPECTED_AUDIENCE;

        private Token(String subject) {
            this.subject = subject;
        }

        /** A valid token for this user: signed by the trusted issuer, for this API, not expired. */
        public static Token forUser(String subject) {
            return new Token(subject);
        }

        /** The roles the issuer put in the token — who the user is. */
        public Token roles(String... roles) {
            for (String role : roles) {
                this.roles.add(role);
            }
            return this;
        }

        /** The scopes the user consented to — what this token may do on their behalf. */
        public Token scopes(String... scopes) {
            for (String scope : scopes) {
                this.scopes.add(scope);
            }
            return this;
        }

        /** Past its exp claim: still perfectly readable, and worth nothing. */
        public Token expired() {
            this.expired = true;
            return this;
        }

        /** Hand-written claims signed with a key the API does not trust. */
        public Token forged() {
            this.signatureValid = false;
            return this;
        }

        /** Minted by someone else — a valid signature is not the same as a trusted one. */
        public Token fromIssuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /** Minted for another API; replaying it here must not work. */
        public Token forAudience(String audience) {
            this.audience = audience;
            return this;
        }
    }

    // ----------------------------------------------------------------- request

    /** One call arriving at the API, exactly as the example wrote it. */
    public static final class Request {

        private final String method;
        private final String path;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Token token;
        private boolean https = true;

        private Request(String method, String path) {
            this.method = method.toUpperCase(Locale.ROOT);
            this.path = path;
        }

        public static Request get(String path) {
            return new Request("GET", path);
        }

        public static Request post(String path) {
            return new Request("POST", path);
        }

        public static Request put(String path) {
            return new Request("PUT", path);
        }

        public static Request patch(String path) {
            return new Request("PATCH", path);
        }

        public static Request delete(String path) {
            return new Request("DELETE", path);
        }

        /** Any other method. */
        public static Request method(String method, String path) {
            return new Request(method, path);
        }

        /** Sends {@code Authorization: Bearer <token>}. */
        public Request bearer(Token token) {
            this.token = token;
            return this;
        }

        /** Adds a request header — including one the client made up, such as X-User-Role. */
        public Request header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Sends this over http:// instead of https://. */
        public Request overPlainHttp() {
            this.https = false;
            return this;
        }

        private List<String> identityHeaders() {
            List<String> names = new ArrayList<>();
            for (String name : headers.keySet()) {
                if (name.toLowerCase(Locale.ROOT).startsWith("x-user")) {
                    names.add(name);
                }
            }
            return names;
        }

        private String credential() {
            if (token != null) {
                return "Bearer " + token.subject;
            }
            return "none";
        }
    }

    /** Who the API decided the caller is, once a gate produced an identity. */
    private static final class Principal {

        private final String subject;
        private final List<String> roles;
        private final List<String> scopes;
        private final String source;

        private Principal(String subject, List<String> roles, List<String> scopes, String source) {
            this.subject = subject;
            this.roles = roles;
            this.scopes = scopes;
            this.source = source;
        }
    }

    // ------------------------------------------------------------------- state

    private final boolean permitByDefault;
    private final List<Rule> rules = new ArrayList<>();
    private final Map<String, String> owners = new LinkedHashMap<>();
    private final Map<String, Integer> requestCounts = new LinkedHashMap<>();
    private final Map<String, String[]> gates = new LinkedHashMap<>();

    private int rateLimit;
    private boolean trustsIdentityHeaders;
    private boolean trustsUnverifiedTokens;

    private Request request;
    private Principal principal;
    private int matchedRule = -1;
    private Integer status;
    private String decision = "idle";
    private String reason;
    private String detail = "";
    /** Set when this request got further than it should have. */
    private boolean breached;

    private int requests;
    private int allowed;
    private int unauthenticated;
    private int forbidden;
    private int rateLimited;
    private int insecure;
    private int breaches;
    private int leakedCredentials;

    private VisualEndpointSecurity(boolean permitByDefault) {
        this.permitByDefault = permitByDefault;
        resetGates();
    }

    /**
     * An API that denies anything no rule allows. This is the stance you want: a
     * new endpoint added tomorrow is closed until somebody decides otherwise.
     */
    public static VisualEndpointSecurity denyByDefault() {
        VisualEndpointSecurity api = new VisualEndpointSecurity(false);
        Trace.event("SECURITY_SETUP",
                "An API whose default stance is DENY: a request that matches no rule is refused. "
                        + "Every endpoint is closed until a rule opens it, so forgetting a rule fails "
                        + "safely — the endpoint stops working instead of standing open",
                "API, у которого позиция по умолчанию — ЗАПРЕТ: запрос, не подошедший ни под одно "
                        + "правило, отклоняется. Каждый эндпоинт закрыт, пока правило его не откроет, "
                        + "поэтому забытое правило ломается безопасно — эндпоинт перестаёт работать, "
                        + "а не остаётся открытым",
                List.of("policy"), api.state());
        return api;
    }

    /**
     * An API that serves anything no rule forbids. Every "we protected the admin
     * pages" incident report describes this stance meeting a new endpoint.
     */
    public static VisualEndpointSecurity permitByDefault() {
        VisualEndpointSecurity api = new VisualEndpointSecurity(true);
        Trace.event("SECURITY_SETUP",
                "An API whose default stance is PERMIT: a request that matches no rule is served. "
                        + "The rule list now has to enumerate everything that must be protected — and "
                        + "the endpoint somebody adds next sprint is not on that list yet",
                "API, у которого позиция по умолчанию — РАЗРЕШИТЬ: запрос, не подошедший ни под одно "
                        + "правило, обслуживается. Теперь список правил обязан перечислить всё, что "
                        + "нужно защитить, — а эндпоинта, который добавят в следующем спринте, в этом "
                        + "списке ещё нет",
                List.of("policy"), api.state());
        return api;
    }

    /** Adds a rule for any method on this path pattern ({@code /api/orders/**} or an exact path). */
    public VisualEndpointSecurity rule(String pathPattern, Access access) {
        return addRule(null, pathPattern, access);
    }

    /** Adds a rule for one method only — reads and writes rarely deserve the same requirement. */
    public VisualEndpointSecurity rule(String method, String pathPattern, Access access) {
        return addRule(method.toUpperCase(Locale.ROOT), pathPattern, access);
    }

    /** Records that the record served at this path belongs to this user. */
    public VisualEndpointSecurity owner(String path, String subject) {
        owners.put(path, subject);
        return this;
    }

    /** Caps how many requests one client (token subject, or "anonymous") may make. */
    public VisualEndpointSecurity rateLimit(int maxRequestsPerClient) {
        this.rateLimit = maxRequestsPerClient;
        Trace.event("RATE_LIMIT_SET",
                "A rate limit of " + maxRequestsPerClient + " request(s) per client is installed in "
                        + "front of the expensive gates. It bounds password guessing, token stuffing "
                        + "and scraping, none of which authentication or authorization can see: every "
                        + "single one of those attempts is a perfectly well-formed request",
                "Перед дорогими проверками ставится ограничение: " + maxRequestsPerClient
                        + " запрос(ов) на клиента. Оно ограничивает подбор паролей, перебор токенов и "
                        + "выкачивание данных — ничего из этого аутентификация и авторизация не видят: "
                        + "каждая такая попытка сама по себе совершенно корректный запрос",
                List.of("policy"), state());
        return this;
    }

    /**
     * Makes the API believe {@code X-User-*} headers. Correct behind a gateway that
     * strips and re-adds them, catastrophic the moment the service is reachable
     * directly — which is the point of demonstrating it.
     */
    public VisualEndpointSecurity trustIdentityHeaders() {
        this.trustsIdentityHeaders = true;
        Trace.event("SECURITY_SETUP",
                "This API is configured to take the caller's identity from X-User-* headers instead "
                        + "of from a verified token. That is only ever safe if nothing but a trusted "
                        + "gateway can reach it AND the gateway strips those headers from client "
                        + "requests — two conditions that are one network change apart from being false",
                "Этот API настроен брать личность вызывающего из заголовков X-User-* вместо "
                        + "проверенного токена. Это безопасно, только если до него не дотянуться никому, "
                        + "кроме доверенного шлюза, И шлюз вырезает эти заголовки из клиентских "
                        + "запросов — два условия, до нарушения которых одно изменение в сети",
                List.of("policy"), state());
        return this;
    }

    /**
     * Makes the API decode tokens without checking the signature — the shortcut
     * behind "we just read the user id out of the JWT".
     */
    public VisualEndpointSecurity trustUnverifiedTokens() {
        this.trustsUnverifiedTokens = true;
        Trace.event("SECURITY_SETUP",
                "This API decodes the token but does not verify its signature. A JWT is base64, not "
                        + "encryption: anybody can read the claims, edit them and send them back. The "
                        + "signature is the only part that makes the claims evidence rather than input",
                "Этот API декодирует токен, но не проверяет подпись. JWT — это base64, а не "
                        + "шифрование: любой может прочитать поля, отредактировать их и отправить "
                        + "обратно. Именно подпись превращает эти поля из пользовательского ввода в "
                        + "доказательство",
                List.of("policy"), state());
        return this;
    }

    // ----------------------------------------------------------------- the chain

    /** Runs one request through the whole chain. */
    public void send(Request call) {
        begin(call);
        Trace.event("REQUEST_RECEIVED",
                call.method + " " + call.path + " arrives over " + (call.https ? "https" : "http")
                        + " with credential: " + call.credential()
                        + ". Nothing about it is trusted yet — the chain decides, endpoint by endpoint",
                call.method + " " + call.path + " приходит по " + (call.https ? "https" : "http")
                        + ", учётные данные: " + call.credential()
                        + ". Пока ничему в нём доверия нет — решает цепочка проверок, эндпоинт за "
                        + "эндпоинтом",
                List.of("request"), state());

        if (!checkTransport(call)) {
            return;
        }
        if (!checkRateLimit(call)) {
            return;
        }
        Rule matched = matchRule(call);
        if (matched == null) {
            return;
        }
        if (matched.access.isPublic()) {
            gate("authentication", "skipped", "permitAll");
            gate("authorization", "skipped", "permitAll");
            gate("ownership", "skipped", "permitAll");
            runHandler(false, "public endpoint", "публичный эндпоинт");
            return;
        }
        if (!authenticate(call)) {
            return;
        }
        if (!authorize(matched)) {
            return;
        }
        if (!checkOwnership(matched, call)) {
            return;
        }
        runHandler(breached,
                "the caller passed every gate the rule configured",
                "вызывающий прошёл все проверки, которые задало правило");
    }

    /** Prints what the whole run added up to. */
    public void report() {
        Trace.event("SECURITY_AUDIT",
                "After " + requests + " request(s): served " + allowed + ", refused with 401 "
                        + unauthenticated + ", refused with 403/404 " + forbidden + ", rate limited "
                        + rateLimited + ", refused as plaintext " + insecure
                        + ", credentials exposed on the wire " + leakedCredentials
                        + ", requests served that should NOT have been " + breaches
                        + ". The last number is the only one an attacker cares about, and the audit "
                        + "log is how you find it — every decision here is one log line",
                "После запросов (" + requests + "): обслужено " + allowed + ", отклонено с 401 "
                        + unauthenticated + ", отклонено с 403/404 " + forbidden + ", ограничено по "
                        + "частоте " + rateLimited + ", отклонено как открытый текст " + insecure
                        + ", учётных данных засвечено в сети " + leakedCredentials
                        + ", обслужено запросов, которые обслуживать НЕ следовало: "
                        + breaches + ". Последнее число — единственное, которое интересует "
                        + "злоумышленника, и находят его по журналу аудита: каждое решение здесь — это "
                        + "одна строка лога",
                List.of(), state());
    }

    // ------------------------------------------------------------------- gates

    private boolean checkTransport(Request call) {
        if (call.https) {
            gate("transport", "passed", "https");
            return true;
        }
        boolean carried = call.token != null || !call.identityHeaders().isEmpty();
        if (carried) {
            leakedCredentials++;
        }
        gate("transport", "denied", "http");
        deny(400, "insecure-transport", "");
        insecure++;
        Trace.event("TRANSPORT_INSECURE",
                "Refused before anything else: the request came over plain http, so every byte of it "
                        + "was readable by every hop in between"
                        + (carried
                            ? ". The credential it carried is now compromised — rejecting the request "
                              + "does not un-send it, and the only real fix is to rotate that credential"
                            : ". There was no credential to lose this time, but the response would have "
                              + "been just as readable and just as modifiable")
                        + ". This is why HTTPS plus HSTS is gate zero, not a nice-to-have",
                "Отклонено раньше всех остальных проверок: запрос пришёл по обычному http, поэтому "
                        + "каждый его байт был доступен любому узлу по пути"
                        + (carried
                            ? ". Учётные данные, которые он нёс, теперь скомпрометированы — отклонение "
                              + "запроса не отменяет его отправки, и по-настоящему лечится это только "
                              + "заменой этих учётных данных"
                            : ". В этот раз терять было нечего, но и ответ был бы так же читаем и так "
                              + "же изменяем")
                        + ". Поэтому HTTPS вместе с HSTS — нулевая проверка, а не приятное дополнение",
                List.of("gate:transport"), state());
        return false;
    }

    private boolean checkRateLimit(Request call) {
        if (rateLimit <= 0) {
            gate("rateLimit", "skipped", "no limit");
            return true;
        }
        String client = call.token != null ? call.token.subject : "anonymous";
        int used = requestCounts.merge(client, 1, Integer::sum);
        if (used > rateLimit) {
            gate("rateLimit", "denied", used + "/" + rateLimit);
            deny(429, "rate-limited", client);
            rateLimited++;
            Trace.event("RATE_LIMITED",
                    "Request " + used + " from client '" + client + "' exceeds the limit of "
                            + rateLimit + ", so it is refused with 429 before the chain spends anything "
                            + "on it. Note what this catches that no other gate can: each of these "
                            + "attempts is individually valid, and it is only their RATE that is an "
                            + "attack",
                    "Запрос " + used + " от клиента «" + client + "» превышает лимит " + rateLimit
                            + ", поэтому он отклоняется с кодом 429 ещё до того, как цепочка что-то на "
                            + "него потратит. Обратите внимание, что здесь ловится то, чего не поймает "
                            + "ни одна другая проверка: каждая такая попытка по отдельности корректна, "
                            + "и атака — именно их ЧАСТОТА",
                    List.of("gate:rateLimit"), state());
            return false;
        }
        gate("rateLimit", "passed", used + "/" + rateLimit);
        return true;
    }

    private Rule matchRule(Request call) {
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.matches(call.method, call.path)) {
                matchedRule = i;
                gate("routing", "passed", rule.label());
                return rule;
            }
        }
        if (permitByDefault) {
            gate("routing", "breach", "no rule");
            breaches++;
            breached = true;
            Trace.event("EXPOSED_BY_DEFAULT",
                    "No rule matches " + call.method + " " + call.path + ", and this API permits by "
                            + "default — so the request goes straight through to the handler with no "
                            + "identity at all. Nobody decided this endpoint was public; it is public "
                            + "because the rule list never mentioned it, which is exactly how internal "
                            + "endpoints end up on the internet",
                    "Ни одно правило не подходит под " + call.method + " " + call.path + ", а этот API "
                            + "по умолчанию разрешает — поэтому запрос идёт прямо в обработчик вообще "
                            + "без установленной личности. Никто не решал делать этот эндпоинт "
                            + "публичным; он публичен потому, что список правил про него не вспомнил, — "
                            + "именно так внутренние эндпоинты и оказываются в интернете",
                    List.of("gate:routing"), state());
            gate("authentication", "skipped", "no rule");
            gate("authorization", "skipped", "no rule");
            gate("ownership", "skipped", "no rule");
            runHandler(true, "no rule matched and the default is permit",
                    "ни одно правило не подошло, а по умолчанию разрешено");
            return null;
        }
        gate("routing", "denied", "no rule");
        deny(403, "no-rule", "");
        forbidden++;
        Trace.event("DENIED_BY_DEFAULT",
                "No rule matches " + call.method + " " + call.path + ", and this API denies by "
                        + "default — so it is refused without anyone having to have predicted this "
                        + "endpoint. The failure mode of forgetting a rule here is a broken feature, "
                        + "which someone reports in a day; with the opposite stance it is a silent hole "
                        + "nobody reports at all",
                "Ни одно правило не подходит под " + call.method + " " + call.path + ", а этот API по "
                        + "умолчанию запрещает — поэтому запрос отклонён, и никому не нужно было "
                        + "заранее предвидеть этот эндпоинт. Забытое правило здесь ломает функцию, о "
                        + "чём сообщат за день; при обратной позиции это молчаливая дыра, о которой не "
                        + "сообщит никто",
                List.of("gate:routing"), state());
        return null;
    }

    private boolean authenticate(Request call) {
        List<String> spoofable = call.identityHeaders();
        if (!spoofable.isEmpty()) {
            if (trustsIdentityHeaders) {
                principal = principalFromHeaders(call);
                gate("authentication", "breach", "header");
                breaches++;
                breached = true;
                Trace.event("IDENTITY_HEADER_TRUSTED",
                        "The caller set " + String.join(", ", spoofable) + " themselves, and the API "
                                + "believes it: they are now '" + principal.subject + "' with roles "
                                + join(principal.roles) + ". Nothing was verified, because a header is "
                                + "not evidence — it is user input with a colon in it. Any client that "
                                + "can reach this service can be any user, including one with the "
                                + "admin role",
                        "Вызывающий сам выставил " + String.join(", ", spoofable)
                                + ", и API этому верит: теперь он «" + principal.subject + "» с ролями "
                                + join(principal.roles) + ". Ничего не проверено, потому что заголовок "
                                + "— не доказательство, а пользовательский ввод с двоеточием. Любой "
                                + "клиент, который дотянется до этого сервиса, может стать любым "
                                + "пользователем, в том числе с ролью администратора",
                        List.of("gate:authentication", "principal"), state());
                return true;
            }
            Trace.event("SPOOFED_IDENTITY_HEADER",
                    "The request carries " + String.join(", ", spoofable) + ", which the client typed "
                            + "in themselves. The API ignores those headers completely and keeps "
                            + "looking for a verified token — identity comes from something the caller "
                            + "cannot forge, and a header is not that. Behind a gateway the same rule "
                            + "holds: the gateway must STRIP incoming X-User-* headers before adding "
                            + "its own",
                    "Запрос несёт " + String.join(", ", spoofable) + ", и это клиент вписал сам. API "
                            + "полностью игнорирует такие заголовки и продолжает искать проверенный "
                            + "токен: личность берётся из того, что вызывающий не может подделать, а "
                            + "заголовок — не из этой категории. За шлюзом правило то же: шлюз обязан "
                            + "ВЫРЕЗАТЬ входящие заголовки X-User-*, прежде чем добавлять свои",
                    List.of("gate:authentication", "request"), state());
        }

        if (call.token == null) {
            gate("authentication", "denied", "no credential");
            deny(401, "no-credential", "");
            unauthenticated++;
            Trace.event("AUTHENTICATION_MISSING",
                    "This endpoint needs to know who is calling and the request says nothing, so the "
                            + "answer is 401 Unauthorized with WWW-Authenticate: Bearer — 'I do not "
                            + "know who you are, try again with a credential'. That is a different "
                            + "sentence from 403, and clients act on the difference: a 401 means "
                            + "re-authenticate, a 403 means stop",
                    "Этому эндпоинту нужно знать, кто вызывает, а запрос об этом молчит — поэтому "
                            + "ответ 401 Unauthorized с заголовком WWW-Authenticate: Bearer: «я не "
                            + "знаю, кто ты, попробуй ещё раз с учётными данными». Это другое "
                            + "утверждение, чем 403, и клиенты действуют по-разному: 401 — "
                            + "аутентифицируйся заново, 403 — прекрати",
                    List.of("gate:authentication"), state());
            return false;
        }

        String failure = verify(call.token);
        if (failure != null) {
            if (trustsUnverifiedTokens && "bad-signature".equals(failure)) {
                principal = principalFromToken(call.token);
                gate("authentication", "breach", "unverified");
                breaches++;
                breached = true;
                Trace.event("TOKEN_NOT_VERIFIED",
                        "The signature does not check out, but this API only decodes tokens — so the "
                                + "forged claims are accepted and the caller is now '"
                                + principal.subject + "' with roles " + join(principal.roles)
                                + ". Writing your own user id and role into a JWT takes about ten "
                                + "seconds; verifying the signature (and the algorithm, so 'alg: none' "
                                + "is refused) is what makes that pointless",
                        "Подпись не сходится, но этот API только декодирует токены — поэтому "
                                + "поддельные поля приняты, и вызывающий теперь «" + principal.subject
                                + "» с ролями " + join(principal.roles) + ". Вписать в JWT свой "
                                + "идентификатор и свою роль — дело десяти секунд; проверка подписи (и "
                                + "алгоритма, чтобы «alg: none» отклонялся) делает это бессмысленным",
                        List.of("gate:authentication", "principal"), state());
                return true;
            }
            gate("authentication", "denied", failure);
            deny(401, failure, failureDetail(call.token, failure));
            unauthenticated++;
            Trace.event("TOKEN_REJECTED",
                    "The token is refused: " + tokenFailureEn(call.token, failure)
                            + ". The answer is 401, not 403 — a token that does not verify identifies "
                            + "nobody, so there is no caller yet whose permissions could be discussed",
                    "Токен отклонён: " + tokenFailureRu(call.token, failure)
                            + ". Ответ — 401, а не 403: непроверенный токен никого не идентифицирует, "
                            + "поэтому вызывающего, о чьих правах можно было бы говорить, ещё нет",
                    List.of("gate:authentication"), state());
            return false;
        }

        principal = principalFromToken(call.token);
        gate("authentication", "passed", principal.subject);
        Trace.event("AUTHENTICATED",
                "Signature, issuer (" + TRUSTED_ISSUER + "), audience (" + EXPECTED_AUDIENCE
                        + ") and expiry all check out, so the caller is '" + principal.subject
                        + "' with roles " + join(principal.roles) + " and scopes "
                        + join(principal.scopes) + ". Authentication has answered WHO; it has not "
                        + "answered whether they may do this",
                "Подпись, издатель (" + TRUSTED_ISSUER + "), аудитория (" + EXPECTED_AUDIENCE
                        + ") и срок действия — всё сходится, поэтому вызывающий — это «"
                        + principal.subject + "» с ролями " + join(principal.roles) + " и scope "
                        + join(principal.scopes) + ". Аутентификация ответила на вопрос КТО; на вопрос, "
                        + "можно ли ему это делать, она не отвечала",
                List.of("gate:authentication", "principal"), state());
        return true;
    }

    private boolean authorize(Rule rule) {
        Access access = rule.access;
        if (access.role != null && !principal.roles.contains(access.role)) {
            gate("authorization", "denied", access.role);
            deny(403, "role", access.role);
            forbidden++;
            Trace.event("ROLE_DENIED",
                    "'" + principal.subject + "' is authenticated but holds " + join(principal.roles)
                            + ", and " + rule.label() + " requires the role " + access.role + ". This "
                            + "is 403 Forbidden: the API knows exactly who is calling and the answer is "
                            + "still no, so repeating the request with the same credential will never "
                            + "help",
                    "«" + principal.subject + "» аутентифицирован, но у него роли "
                            + join(principal.roles) + ", а " + rule.label() + " требует роль "
                            + access.role + ". Это 403 Forbidden: API прекрасно знает, кто вызывает, и "
                            + "всё равно отвечает «нет», поэтому повтор с теми же учётными данными "
                            + "никогда не поможет",
                    List.of("gate:authorization", "principal"), state());
            return false;
        }
        for (String required : access.scopes) {
            if (!principal.scopes.contains(required)) {
                gate("authorization", "denied", required);
                deny(403, "scope", required);
                forbidden++;
                Trace.event("SCOPE_DENIED",
                        "'" + principal.subject + "' may well be allowed to do this in person, but "
                                + "THIS token was issued with scopes " + join(principal.scopes)
                                + " and " + rule.label() + " requires " + required
                                + ". Roles say what the user may do; scopes say what the bearer of this "
                                + "particular token may do on their behalf — which is how you hand an "
                                + "integration read access without handing it your account",
                        "Возможно, «" + principal.subject + "» и вправе сделать это лично, но ЭТОТ "
                                + "токен выдан со scope " + join(principal.scopes) + ", а "
                                + rule.label() + " требует " + required + ". Роли говорят, что может "
                                + "пользователь; scope говорят, что может предъявитель конкретно этого "
                                + "токена от его имени, — именно так интеграции выдают доступ на "
                                + "чтение, не отдавая ей весь аккаунт",
                        List.of("gate:authorization", "principal"), state());
                return false;
            }
        }
        gate("authorization", "passed", access.describeEn());
        Trace.event("AUTHORIZED",
                "The rule " + rule.label() + " asks for " + access.describeEn() + ", and '"
                        + principal.subject + "' satisfies it. Note how coarse this answer is: it is "
                        + "about the ENDPOINT, not about the record behind it",
                "Правило " + rule.label() + " требует: " + access.describeRu() + ", и «"
                        + principal.subject + "» этому соответствует. Обратите внимание, насколько это "
                        + "грубый ответ: он про ЭНДПОИНТ, а не про запись, которая за ним стоит",
                List.of("gate:authorization"), state());
        return true;
    }

    private boolean checkOwnership(Rule rule, Request call) {
        String owner = owners.get(call.path);
        if (rule.access.ownerOnly) {
            if (owner == null) {
                gate("ownership", "passed", "no record");
                return true;
            }
            if (!owner.equals(principal.subject)) {
                gate("ownership", "denied", owner);
                deny(404, "not-owner", owner);
                forbidden++;
                Trace.event("OWNERSHIP_DENIED",
                        "'" + principal.subject + "' passed the endpoint check, so the object check "
                                + "runs: " + call.path + " belongs to '" + owner + "'. Refused — and "
                                + "with 404, not 403, because 403 would confirm that this id exists and "
                                + "let an attacker enumerate the records of every other user",
                        "«" + principal.subject + "» прошёл проверку эндпоинта, поэтому выполняется "
                                + "проверка объекта: " + call.path + " принадлежит «" + owner
                                + "». Отказ — причём с кодом 404, а не 403, потому что 403 подтвердил "
                                + "бы существование этого идентификатора и позволил бы перебрать записи "
                                + "всех остальных пользователей",
                        List.of("gate:ownership", "principal"), state());
                return false;
            }
            gate("ownership", "passed", owner);
            return true;
        }
        if (owner != null && !owner.equals(principal.subject)) {
            gate("ownership", "breach", owner);
            breaches++;
            breached = true;
            Trace.event("OWNERSHIP_CHECK_MISSING",
                    "'" + principal.subject + "' is a legitimate user with a legitimate token asking "
                            + "for " + call.path + ", which belongs to '" + owner + "' — and the rule "
                            + "only ever checked whether the caller is somebody. Nothing here is "
                            + "broken; the check simply does not exist. Changing one digit in the URL "
                            + "is the whole exploit, and it is the single most common API "
                            + "vulnerability there is",
                    "«" + principal.subject + "» — настоящий пользователь с настоящим токеном, он "
                            + "запрашивает " + call.path + ", а эта запись принадлежит «" + owner
                            + "»; правило же проверяло только то, что вызывающий вообще кто-то. Здесь "
                            + "ничего не сломано — проверки просто нет. Весь эксплойт — поменять одну "
                            + "цифру в URL, и это самая частая уязвимость API из существующих",
                    List.of("gate:ownership", "principal"), state());
            return true;
        }
        gate("ownership", "skipped", "not required");
        return true;
    }

    private void runHandler(boolean breach, String noteEn, String noteRu) {
        gate("handler", breach ? "breach" : "passed", "200");
        status = 200;
        decision = breach ? "breach" : "allowed";
        allowed++;
        Trace.event("HANDLER_RAN",
                "200 OK — the business code runs (" + noteEn + ")"
                        + (breach
                            ? ". This one should not have: the request was served because a gate was "
                              + "missing or misconfigured, not because anybody decided it was allowed. "
                              + "There is no error anywhere in the logs; the response is a perfectly "
                              + "successful one"
                            : ". Every gate in front of it agreed, and the handler itself is free to "
                              + "assume an identity it never had to establish"),
                "200 OK — выполняется бизнес-код (" + noteRu + ")"
                        + (breach
                            ? ". Хотя не следовало: запрос обслужен из-за отсутствующей или неверно "
                              + "настроенной проверки, а не потому, что кто-то решил его разрешить. "
                              + "Ошибки в логах не будет нигде — ответ совершенно успешный"
                            : ". Все проверки перед ним согласились, и сам обработчик спокойно "
                              + "полагается на личность, которую ему не пришлось устанавливать самому"),
                List.of("gate:handler"), state());
    }

    // --------------------------------------------------------------- internals

    private VisualEndpointSecurity addRule(String method, String pattern, Access access) {
        Rule rule = new Rule(method, pattern, access);
        int shadowedBy = firstMatchOf(rule);
        rules.add(rule);
        if (shadowedBy >= 0) {
            rule.shadowed = true;
            Rule earlier = rules.get(shadowedBy);
            Trace.event("RULE_SHADOWED",
                    "Rule " + rules.size() + " (" + rule.label() + " -> " + access.describeEn()
                            + ") can never fire: rule " + (shadowedBy + 1) + " (" + earlier.label()
                            + " -> " + earlier.access.describeEn() + ") already matches those paths, "
                            + "and the first match wins. The stricter rule is in the file, it is in "
                            + "code review, and it is dead — put the specific patterns BEFORE the broad "
                            + "ones",
                    "Правило " + rules.size() + " (" + rule.label() + " -> " + access.describeRu()
                            + ") никогда не сработает: правило " + (shadowedBy + 1) + " ("
                            + earlier.label() + " -> " + earlier.access.describeRu()
                            + ") уже подходит под эти пути, а выигрывает первое совпадение. Более "
                            + "строгое правило есть в файле, оно прошло ревью — и оно мертво. "
                            + "Конкретные шаблоны ставьте ПЕРЕД широкими",
                    List.of("rule:" + (rules.size() - 1), "rule:" + shadowedBy), state());
            return this;
        }
        Trace.event("RULE_ADDED",
                "Rule " + rules.size() + ": " + rule.label() + " -> " + access.describeEn()
                        + ". The rule list is the security scheme — it is read top to bottom and the "
                        + "first matching entry decides, so both its contents and its order are the "
                        + "policy",
                "Правило " + rules.size() + ": " + rule.label() + " -> " + access.describeRu()
                        + ". Список правил и есть схема безопасности — он читается сверху вниз, и "
                        + "решает первая подошедшая запись, поэтому политика — это и его содержимое, и "
                        + "его порядок",
                List.of("rule:" + (rules.size() - 1)), state());
        return this;
    }

    /** Index of an existing rule that already covers everything the new one would, or -1. */
    private int firstMatchOf(Rule candidate) {
        String sample = candidate.pattern.endsWith("/**")
                ? candidate.pattern.substring(0, candidate.pattern.length() - 3) + "/probe"
                : candidate.pattern;
        String method = candidate.method == null ? "GET" : candidate.method;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).matches(method, sample)) {
                return i;
            }
        }
        return -1;
    }

    /** First failing check, in the order a library performs them, or null when the token is good. */
    private String verify(Token token) {
        if (!token.signatureValid) {
            return "bad-signature";
        }
        if (!TRUSTED_ISSUER.equals(token.issuer)) {
            return "wrong-issuer";
        }
        if (!EXPECTED_AUDIENCE.equals(token.audience)) {
            return "wrong-audience";
        }
        if (token.expired) {
            return "expired";
        }
        return null;
    }

    private String failureDetail(Token token, String failure) {
        return switch (failure) {
            case "wrong-issuer" -> token.issuer;
            case "wrong-audience" -> token.audience;
            default -> "";
        };
    }

    private String tokenFailureEn(Token token, String failure) {
        return switch (failure) {
            case "bad-signature" -> "the signature does not match, so the claims inside are just text "
                    + "somebody sent, not a statement by " + TRUSTED_ISSUER;
            case "wrong-issuer" -> "it was signed by " + token.issuer + ", and this API only trusts "
                    + TRUSTED_ISSUER + " — a real signature from the wrong authority is still not "
                    + "authorization here";
            case "wrong-audience" -> "its audience is " + token.audience + ", not " + EXPECTED_AUDIENCE
                    + " — it was minted for a different API, and checking aud is what stops that API "
                    + "from replaying its tokens against this one";
            default -> "it is past its expiry, which is the whole point of short-lived tokens: a "
                    + "stolen one stops working on its own instead of waiting for someone to notice";
        };
    }

    private String tokenFailureRu(Token token, String failure) {
        return switch (failure) {
            case "bad-signature" -> "подпись не сходится, поэтому поля внутри — просто текст, который "
                    + "кто-то прислал, а не утверждение от " + TRUSTED_ISSUER;
            case "wrong-issuer" -> "он подписан " + token.issuer + ", а этот API доверяет только "
                    + TRUSTED_ISSUER + ": настоящая подпись не того центра здесь всё равно не "
                    + "авторизация";
            case "wrong-audience" -> "его аудитория — " + token.audience + ", а не " + EXPECTED_AUDIENCE
                    + ": он выпущен для другого API, и проверка aud как раз не даёт тому API "
                    + "переиспользовать свои токены против этого";
            default -> "он просрочен, а в этом и весь смысл короткоживущих токенов: украденный "
                    + "перестаёт работать сам, не дожидаясь, пока кто-нибудь заметит";
        };
    }

    private Principal principalFromToken(Token token) {
        return new Principal(token.subject, new ArrayList<>(token.roles),
                new ArrayList<>(token.scopes), "token");
    }

    private Principal principalFromHeaders(Request call) {
        String subject = "unknown";
        List<String> roles = new ArrayList<>();
        for (Map.Entry<String, String> header : call.headers.entrySet()) {
            String name = header.getKey().toLowerCase(Locale.ROOT);
            if ("x-user-id".equals(name)) {
                subject = header.getValue();
            } else if ("x-user-role".equals(name) || "x-user-roles".equals(name)) {
                for (String role : header.getValue().split(",")) {
                    roles.add(role.trim());
                }
            }
        }
        return new Principal(subject, roles, new ArrayList<>(), "header");
    }

    private void begin(Request call) {
        requests++;
        request = call;
        principal = null;
        matchedRule = -1;
        status = null;
        decision = "pending";
        reason = null;
        detail = "";
        breached = false;
        resetGates();
    }

    private void deny(int status, String reason, String detail) {
        this.status = status;
        this.decision = "denied";
        this.reason = reason;
        this.detail = detail;
    }

    private void resetGates() {
        gates.clear();
        for (String id : GATES) {
            gates.put(id, new String[]{"pending", ""});
        }
    }

    private void gate(String id, String status, String detail) {
        gates.put(id, new String[]{status, detail});
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "(none)" : String.join(", ", values);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("defaultStance", permitByDefault ? "permit" : "deny");
        s.put("trustsIdentityHeaders", trustsIdentityHeaders);
        s.put("trustsUnverifiedTokens", trustsUnverifiedTokens);
        s.put("rateLimit", rateLimit);

        List<Object> ruleList = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("method", rule.method == null ? "ANY" : rule.method);
            r.put("pattern", rule.pattern);
            r.put("requirement", rule.access.kind);
            r.put("role", rule.access.role);
            r.put("scopes", new ArrayList<>(rule.access.scopes));
            r.put("ownerOnly", rule.access.ownerOnly);
            r.put("shadowed", rule.shadowed);
            r.put("matched", i == matchedRule);
            ruleList.add(r);
        }
        s.put("rules", ruleList);

        if (request != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("method", request.method);
            r.put("path", request.path);
            r.put("scheme", request.https ? "https" : "http");
            r.put("credential", request.credential());
            List<Object> headers = new ArrayList<>();
            List<String> identity = request.identityHeaders();
            for (Map.Entry<String, String> header : request.headers.entrySet()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", header.getKey());
                entry.put("value", header.getValue());
                entry.put("clientControlled", identity.contains(header.getKey()));
                headers.add(entry);
            }
            r.put("headers", headers);
            s.put("request", r);

            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("status", status);
            outcome.put("decision", decision);
            outcome.put("reason", reason);
            outcome.put("detail", detail);
            s.put("outcome", outcome);
        }

        if (principal != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("subject", principal.subject);
            p.put("roles", new ArrayList<>(principal.roles));
            p.put("scopes", new ArrayList<>(principal.scopes));
            p.put("source", principal.source);
            s.put("principal", p);
        } else {
            s.put("principal", null);
        }

        List<Object> gateList = new ArrayList<>();
        for (Map.Entry<String, String[]> gate : gates.entrySet()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("id", gate.getKey());
            g.put("status", gate.getValue()[0]);
            g.put("detail", gate.getValue()[1]);
            gateList.add(g);
        }
        s.put("gates", gateList);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("requests", requests);
        stats.put("allowed", allowed);
        stats.put("unauthenticated", unauthenticated);
        stats.put("forbidden", forbidden);
        stats.put("rateLimited", rateLimited);
        stats.put("insecure", insecure);
        stats.put("leakedCredentials", leakedCredentials);
        stats.put("breaches", breaches);
        s.put("stats", stats);
        return s;
    }
}
