package visual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of CORS — Cross-Origin Resource Sharing — as it is
 * actually enforced: by the browser, on behalf of the page, using headers the
 * <em>server</em> sends.
 *
 * <p>The model owns three things: the origin of the page running the JavaScript,
 * the origin of the API it calls, and the CORS {@link Policy} the API is
 * configured with. Every {@link #send(Request)} walks the same decision the
 * browser walks:
 * <ul>
 *   <li>same origin -> the same-origin policy has nothing to restrict and CORS
 *       never enters the picture;</li>
 *   <li>cross origin, and the method plus headers are on the CORS safelist ->
 *       a "simple" request: the browser sends it immediately (the server
 *       <em>runs</em> it) and only then decides whether JavaScript may read the
 *       response;</li>
 *   <li>cross origin with anything else -> a preflight {@code OPTIONS} first,
 *       so a rejected request never reaches the handler at all;</li>
 *   <li>cookies ({@link Request#withCredentials()}) tighten every rule: a
 *       wildcard {@code Access-Control-Allow-Origin} stops being acceptable.</li>
 * </ul>
 *
 * <p>The distinction the model exists to make visible is that "blocked by CORS"
 * usually means <em>the response was hidden from JavaScript</em>, not that the
 * request was stopped — which is why CORS is not server-side authorization.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualCors {

    /** Methods a browser will send cross-origin without asking permission first. */
    private static final Set<String> SIMPLE_METHODS = Set.of("GET", "HEAD", "POST");
    /** Methods that only read, so a blocked response costs work but changes nothing. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD");
    /** Request headers that never trigger a preflight. */
    private static final Set<String> SAFELISTED_REQUEST_HEADERS =
            Set.of("accept", "accept-language", "content-language");
    /** The only Content-Type values a form could produce, so they are safelisted too. */
    private static final Set<String> SAFELISTED_CONTENT_TYPES =
            Set.of("application/x-www-form-urlencoded", "multipart/form-data", "text/plain");
    /** Response headers JavaScript may read without Access-Control-Expose-Headers. */
    private static final Set<String> SAFELISTED_RESPONSE_HEADERS =
            Set.of("cache-control", "content-language", "content-length", "content-type",
                    "expires", "last-modified", "pragma");

    /**
     * The CORS headers the API answers with. {@link #none()} is a server that has
     * never heard of CORS: it replies normally, it just says nothing about who may
     * read the reply.
     */
    public static final class Policy {

        private String allowOrigin;
        private final List<String> allowMethods = new ArrayList<>();
        private final List<String> allowHeaders = new ArrayList<>();
        private final List<String> exposeHeaders = new ArrayList<>();
        private boolean allowCredentials;
        private int maxAge;

        private Policy() {
        }

        /** No CORS headers at all — the default of every framework until configured. */
        public static Policy none() {
            return new Policy();
        }

        /** Sends {@code Access-Control-Allow-Origin: <origin>}. */
        public static Policy allowOrigin(String origin) {
            Policy policy = new Policy();
            policy.allowOrigin = origin;
            return policy;
        }

        /** Sends {@code Access-Control-Allow-Origin: *} — open to any page, no cookies. */
        public static Policy allowAnyOrigin() {
            return allowOrigin("*");
        }

        /** Sends {@code Access-Control-Allow-Methods}. */
        public Policy allowMethods(String... methods) {
            for (String method : methods) {
                allowMethods.add(method);
            }
            return this;
        }

        /** Sends {@code Access-Control-Allow-Headers}. */
        public Policy allowHeaders(String... headers) {
            for (String header : headers) {
                allowHeaders.add(header);
            }
            return this;
        }

        /** Sends {@code Access-Control-Expose-Headers} — makes a response header readable. */
        public Policy exposeHeaders(String... headers) {
            for (String header : headers) {
                exposeHeaders.add(header);
            }
            return this;
        }

        /** Sends {@code Access-Control-Allow-Credentials: true}. */
        public Policy allowCredentials() {
            this.allowCredentials = true;
            return this;
        }

        /** Sends {@code Access-Control-Max-Age} — how long a preflight result is reusable. */
        public Policy maxAge(int seconds) {
            this.maxAge = seconds;
            return this;
        }
    }

    /** One call the page's JavaScript makes, in the order the example wrote it. */
    public static final class Request {

        private final String method;
        private final String path;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final List<String> wantsHeaders = new ArrayList<>();
        private boolean credentials;

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

        public static Request delete(String path) {
            return new Request("DELETE", path);
        }

        /** Any other method, e.g. PATCH. */
        public static Request method(String method, String path) {
            return new Request(method, path);
        }

        /** Adds a request header exactly as the fetch call would. */
        public Request header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** {@code Content-Type: application/json} — NOT on the safelist, so it preflights. */
        public Request json() {
            return header("Content-Type", "application/json");
        }

        /** {@code Content-Type: application/x-www-form-urlencoded} — safelisted, no preflight. */
        public Request formEncoded() {
            return header("Content-Type", "application/x-www-form-urlencoded");
        }

        /** {@code credentials: 'include'} — send the API's cookies with the request. */
        public Request withCredentials() {
            this.credentials = true;
            return this;
        }

        /** The JavaScript wants to read this response header afterwards. */
        public Request readsHeader(String name) {
            wantsHeaders.add(name);
            return this;
        }
    }

    private final String pageOrigin;
    private final String apiOrigin;
    private final boolean crossOrigin;

    private Policy policy;
    /** Preflight results the browser may reuse while Access-Control-Max-Age holds. */
    private final Set<String> preflightCache = new LinkedHashSet<>();

    private int requests;
    private int reachedServer;
    private int blockedByBrowser;
    private int preflights;
    private int preflightsSaved;

    private Request request;
    private boolean needsPreflight;
    private String preflightStatus;
    private String stage = "idle";
    private boolean delivered;
    private boolean readable;
    private String reason;
    private String reasonDetail = "";
    private List<String> hiddenHeaders = new ArrayList<>();

    private VisualCors(String pageOrigin, String apiOrigin, Policy policy) {
        this.pageOrigin = pageOrigin;
        this.apiOrigin = apiOrigin;
        this.policy = policy;
        this.crossOrigin = !pageOrigin.equals(apiOrigin);
    }

    /**
     * A page loaded from {@code pageOrigin} whose JavaScript calls an API at
     * {@code apiOrigin}, which answers with {@code policy}. An origin is
     * scheme + host + port, so passing the same string twice is a same-origin setup.
     */
    public static VisualCors browser(String pageOrigin, String apiOrigin, Policy policy) {
        VisualCors cors = new VisualCors(pageOrigin, apiOrigin, policy);
        Trace.event("CORS_SETUP",
                "A page from " + pageOrigin + " calls " + apiOrigin + " — "
                        + (cors.crossOrigin
                            ? "a DIFFERENT origin (origin = scheme + host + port), so the browser "
                              + "applies CORS. The API answers with " + cors.policyEn()
                            : "the SAME origin, so nothing here is cross-origin"),
                "Страница с " + pageOrigin + " обращается к " + apiOrigin + " — "
                        + (cors.crossOrigin
                            ? "это ДРУГОЙ origin (origin = схема + хост + порт), поэтому браузер "
                              + "применяет CORS. API отвечает: " + cors.policyRu()
                            : "тот же самый origin, поэтому здесь нет ничего кросс-доменного"),
                List.of("policy"), cors.state());
        return cors;
    }

    /** The team ships a new CORS configuration; the browser starts from a clean cache. */
    public void reconfigure(Policy policy) {
        this.policy = policy;
        preflightCache.clear();
        Trace.event("CORS_SETUP",
                "The API is redeployed with a new CORS configuration: " + policyEn()
                        + " (the browser's preflight cache starts empty again)",
                "API переразвёрнут с новой конфигурацией CORS: " + policyRu()
                        + " (кеш предварительных запросов браузера снова пуст)",
                List.of("policy"), state());
    }

    /** Runs one fetch() the way a browser would. */
    public void send(Request call) {
        begin(call);
        Trace.event("REQUEST_ISSUED",
                "JavaScript on " + pageOrigin + " calls fetch(\"" + apiOrigin + call.path + "\") with "
                        + call.method + describeHeadersEn() + credentialsEn(),
                "JavaScript на " + pageOrigin + " вызывает fetch(\"" + apiOrigin + call.path
                        + "\") методом " + call.method + describeHeadersRu() + credentialsRu(),
                List.of("request"), state());

        if (!crossOrigin) {
            delivered = true;
            readable = true;
            stage = "settled";
            reachedServer++;
            Trace.event("SAME_ORIGIN_OK",
                    "Same origin: the same-origin policy has nothing to restrict, so the browser sends "
                            + "no Origin header, asks for no permission, and hands the whole response to "
                            + "JavaScript. CORS only exists to RELAX this rule, never to impose it",
                    "Тот же origin: правилу ограничивать нечего, поэтому браузер не шлёт заголовок "
                            + "Origin, ни у кого не спрашивает разрешения и отдаёт JavaScript весь ответ "
                            + "целиком. CORS существует только чтобы ОСЛАБИТЬ это правило, а не ввести его",
                    List.of("request", "response"), state());
            return;
        }

        if (needsPreflight) {
            if (!runPreflight()) {
                return;
            }
        } else {
            Trace.event("SIMPLE_REQUEST",
                    "No preflight: " + call.method + " is a safelisted method and every header is "
                            + "safelisted too, so this request is one a plain HTML form could already "
                            + "have made. The browser sends it straight away with Origin: " + pageOrigin,
                    "Без предварительного запроса: " + call.method + " — метод из безопасного списка, и "
                            + "все заголовки тоже из него, поэтому такой запрос могла бы отправить и "
                            + "обычная HTML-форма. Браузер сразу шлёт его с заголовком Origin: "
                            + pageOrigin,
                    List.of("request"), state());
        }

        delivered = true;
        reachedServer++;
        stage = "delivered";
        Trace.event("REQUEST_DELIVERED",
                "The request reaches " + apiOrigin + call.path + " and the handler runs it — CORS has "
                        + "not been checked yet, because the check is done on the RESPONSE",
                "Запрос доходит до " + apiOrigin + call.path + ", и обработчик его выполняет — CORS ещё "
                        + "не проверен, потому что проверка делается по ОТВЕТУ",
                List.of("request", "server"), state());

        String failure = originFailure();
        if (failure != null) {
            reason = failure;
            readable = false;
            stage = "settled";
            blockedByBrowser++;
            if (failure.startsWith("credentials")) {
                Trace.event("CREDENTIALS_BLOCKED",
                        credentialsFailureEn(failure),
                        credentialsFailureRu(failure),
                        List.of("response", "policy"), state());
            } else {
                Trace.event("RESPONSE_BLOCKED",
                        "The response came back, but " + originFailureEn(failure) + " — so the browser "
                                + "refuses to hand it to JavaScript: fetch() rejects with a TypeError and "
                                + "the console prints the CORS error. The network tab still shows the "
                                + "response, because it really did arrive",
                        "Ответ пришёл, но " + originFailureRu(failure) + " — поэтому браузер отказывается "
                                + "отдавать его JavaScript: fetch() падает с TypeError, а в консоли "
                                + "появляется ошибка CORS. Во вкладке Network ответ по-прежнему виден, "
                                + "потому что он действительно пришёл",
                        List.of("response", "policy"), state());
            }
            Trace.event("SERVER_ALREADY_RAN",
                    "Note what was NOT prevented: the " + call.method + " was delivered and executed"
                            + (SAFE_METHODS.contains(call.method)
                                ? ", so the server did the work and only the answer was thrown away"
                                : ", so the write really happened — the browser hid the receipt, not the "
                                  + "effect")
                            + ". CORS protects the visitor's data from a hostile page; it is not "
                            + "server-side authorization",
                    "Обратите внимание, что НЕ было предотвращено: " + call.method + " был доставлен и "
                            + "выполнен"
                            + (SAFE_METHODS.contains(call.method)
                                ? ", то есть сервер сделал работу, и выброшен был только ответ"
                                : ", то есть изменение действительно произошло — браузер скрыл квитанцию, "
                                  + "а не эффект")
                            + ". CORS защищает данные посетителя от враждебной страницы; это не "
                            + "серверная авторизация",
                    List.of("server"), state());
            return;
        }

        readable = true;
        stage = "settled";
        Trace.event("RESPONSE_READABLE",
                "The response carries Access-Control-Allow-Origin: " + policy.allowOrigin
                        + (request.credentials ? " and Access-Control-Allow-Credentials: true" : "")
                        + ", which matches " + pageOrigin + " — the browser hands the body to "
                        + "JavaScript and the fetch() promise resolves",
                "Ответ несёт Access-Control-Allow-Origin: " + policy.allowOrigin
                        + (request.credentials ? " и Access-Control-Allow-Credentials: true" : "")
                        + ", и это подходит для " + pageOrigin + " — браузер отдаёт тело JavaScript, "
                        + "и промис fetch() успешно разрешается",
                List.of("response"), state());

        checkExposedHeaders();
    }

    /** Prints what the page managed to read after everything the example did. */
    public void report() {
        Trace.event("CORS_AUDIT",
                "After " + requests + " request(s) from " + pageOrigin + " to " + apiOrigin
                        + ": reached the server: " + reachedServer + ", blocked by the browser: "
                        + blockedByBrowser + ", preflights sent: " + preflights
                        + ", preflights saved by Access-Control-Max-Age: " + preflightsSaved,
                "После запросов (" + requests + ") с " + pageOrigin + " на " + apiOrigin
                        + ": дошло до сервера: " + reachedServer + ", заблокировано браузером: "
                        + blockedByBrowser + ", отправлено предварительных запросов: " + preflights
                        + ", сэкономлено благодаря Access-Control-Max-Age: " + preflightsSaved,
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    /** Runs the OPTIONS handshake; returns false when the real request must not be sent. */
    private boolean runPreflight() {
        String key = preflightKey();
        if (policy.maxAge > 0 && preflightCache.contains(key)) {
            preflightStatus = "cached";
            preflightsSaved++;
            Trace.event("PREFLIGHT_CACHED",
                    "The browser already asked about " + request.method + " with "
                            + join(nonSafelistedHeaders()) + " and the answer said "
                            + "Access-Control-Max-Age: " + policy.maxAge + " — so it reuses that "
                            + "permission instead of paying for a second round trip",
                    "Браузер уже спрашивал про " + request.method + " с заголовками "
                            + join(nonSafelistedHeaders()) + ", а в ответе было "
                            + "Access-Control-Max-Age: " + policy.maxAge + " — поэтому он переиспользует "
                            + "это разрешение и не платит за второй круг по сети",
                    List.of("preflight"), state());
            return true;
        }

        preflights++;
        preflightStatus = "sent";
        Trace.event("PREFLIGHT_SENT",
                "This is not a request a form could have made" + preflightTriggerEn()
                        + ", so before sending anything the browser asks permission: OPTIONS "
                        + request.path + " with Origin: " + pageOrigin
                        + ", Access-Control-Request-Method: " + request.method
                        + (nonSafelistedHeaders().isEmpty() ? ""
                            : ", Access-Control-Request-Headers: " + join(nonSafelistedHeaders())),
                "Такой запрос форма отправить не могла" + preflightTriggerRu()
                        + ", поэтому, прежде чем что-либо отправлять, браузер спрашивает разрешение: "
                        + "OPTIONS " + request.path + " с Origin: " + pageOrigin
                        + ", Access-Control-Request-Method: " + request.method
                        + (nonSafelistedHeaders().isEmpty() ? ""
                            : ", Access-Control-Request-Headers: " + join(nonSafelistedHeaders())),
                List.of("preflight", "request"), state());

        String failure = preflightFailure();
        if (failure != null) {
            preflightStatus = "blocked";
            reason = failure;
            readable = false;
            delivered = false;
            stage = "settled";
            blockedByBrowser++;
            Trace.event("PREFLIGHT_BLOCKED",
                    "The OPTIONS answer does not grant it: " + failureEn(failure)
                            + ". The browser therefore never sends the real " + request.method
                            + " — nothing reached the handler, so nothing was created, changed or "
                            + "deleted",
                    "Ответ на OPTIONS этого не разрешает: " + failureRu(failure)
                            + ". Поэтому браузер вообще не отправляет настоящий " + request.method
                            + " — до обработчика ничего не дошло, значит ничего не создано, не изменено "
                            + "и не удалено",
                    List.of("preflight", "policy"), state());
            return false;
        }

        preflightStatus = "allowed";
        Trace.event("PREFLIGHT_ALLOWED",
                "The OPTIONS answer grants it: Access-Control-Allow-Origin: " + policy.allowOrigin
                        + ", Access-Control-Allow-Methods: " + join(policy.allowMethods)
                        + (policy.allowHeaders.isEmpty() ? ""
                            : ", Access-Control-Allow-Headers: " + join(policy.allowHeaders))
                        + (policy.maxAge > 0 ? ", Access-Control-Max-Age: " + policy.maxAge : "")
                        + ". Only now does the browser send the real request",
                "Ответ на OPTIONS это разрешает: Access-Control-Allow-Origin: " + policy.allowOrigin
                        + ", Access-Control-Allow-Methods: " + join(policy.allowMethods)
                        + (policy.allowHeaders.isEmpty() ? ""
                            : ", Access-Control-Allow-Headers: " + join(policy.allowHeaders))
                        + (policy.maxAge > 0 ? ", Access-Control-Max-Age: " + policy.maxAge : "")
                        + ". Только теперь браузер отправляет настоящий запрос",
                List.of("preflight", "policy"), state());

        if (policy.maxAge > 0) {
            preflightCache.add(key);
        }
        return true;
    }

    private void checkExposedHeaders() {
        List<String> hidden = new ArrayList<>();
        for (String wanted : request.wantsHeaders) {
            String name = wanted.toLowerCase(Locale.ROOT);
            if (SAFELISTED_RESPONSE_HEADERS.contains(name)) {
                continue;
            }
            if (!contains(policy.exposeHeaders, wanted)) {
                hidden.add(wanted);
            }
        }
        if (hidden.isEmpty()) {
            return;
        }
        hiddenHeaders = hidden;
        Trace.event("HEADER_HIDDEN",
                "Reading the BODY was allowed, reading " + join(hidden) + " is not: cross-origin "
                        + "JavaScript only sees a safelist of response headers (Content-Type, "
                        + "Content-Length, Cache-Control, Expires, Last-Modified, Content-Language, "
                        + "Pragma). response.headers.get(\"" + hidden.get(0) + "\") returns null until "
                        + "the server adds Access-Control-Expose-Headers: " + hidden.get(0),
                "Читать ТЕЛО разрешено, а " + join(hidden) + " — нет: кросс-доменному JavaScript виден "
                        + "лишь безопасный список заголовков ответа (Content-Type, Content-Length, "
                        + "Cache-Control, Expires, Last-Modified, Content-Language, Pragma). "
                        + "response.headers.get(\"" + hidden.get(0) + "\") вернёт null, пока сервер не "
                        + "добавит Access-Control-Expose-Headers: " + hidden.get(0),
                List.of("response"), state());
    }

    private void begin(Request call) {
        requests++;
        request = call;
        needsPreflight = crossOrigin
                && (!SIMPLE_METHODS.contains(call.method) || !nonSafelistedHeaders().isEmpty());
        preflightStatus = null;
        stage = "issued";
        delivered = false;
        readable = false;
        reason = null;
        reasonDetail = "";
        hiddenHeaders = new ArrayList<>();
    }

    /** Request headers that are not on the CORS safelist, in the order they were set. */
    private List<String> nonSafelistedHeaders() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            String name = header.getKey().toLowerCase(Locale.ROOT);
            if (SAFELISTED_REQUEST_HEADERS.contains(name)) {
                continue;
            }
            if ("content-type".equals(name)
                    && SAFELISTED_CONTENT_TYPES.contains(header.getValue().toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(header.getKey());
        }
        return out;
    }

    /** What the browser is allowed to reuse: the method plus the headers it asked about. */
    private String preflightKey() {
        List<String> names = new ArrayList<>();
        for (String header : nonSafelistedHeaders()) {
            names.add(header.toLowerCase(Locale.ROOT));
        }
        Collections.sort(names);
        return request.method + "|" + String.join(",", names);
    }

    /** Why the response may not be read, or null when it may. */
    private String originFailure() {
        if (policy.allowOrigin == null) {
            return "no-allow-origin";
        }
        if (!"*".equals(policy.allowOrigin) && !policy.allowOrigin.equals(pageOrigin)) {
            reasonDetail = policy.allowOrigin;
            return "origin-mismatch";
        }
        if (request.credentials) {
            if ("*".equals(policy.allowOrigin)) {
                return "credentials-wildcard";
            }
            if (!policy.allowCredentials) {
                return "credentials-not-allowed";
            }
        }
        return null;
    }

    /** Why the preflight refuses permission, or null when it grants it. */
    private String preflightFailure() {
        String failure = originFailure();
        if (failure != null) {
            return failure;
        }
        if (!contains(policy.allowMethods, request.method)) {
            reasonDetail = request.method;
            return "method-not-allowed";
        }
        for (String header : nonSafelistedHeaders()) {
            if (!contains(policy.allowHeaders, header)) {
                reasonDetail = header;
                return "header-not-allowed";
            }
        }
        return null;
    }

    private static boolean contains(List<String> allowed, String value) {
        for (String entry : allowed) {
            if ("*".equals(entry) || entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- descriptions

    private String failureEn(String failure) {
        return switch (failure) {
            case "method-not-allowed" -> "Access-Control-Allow-Methods lists "
                    + join(policy.allowMethods) + ", and " + reasonDetail + " is not among them";
            case "header-not-allowed" -> "the browser asked about " + reasonDetail
                    + ", and Access-Control-Allow-Headers lists only " + join(policy.allowHeaders);
            default -> originFailureEn(failure);
        };
    }

    private String failureRu(String failure) {
        return switch (failure) {
            case "method-not-allowed" -> "в Access-Control-Allow-Methods перечислено "
                    + join(policy.allowMethods) + ", и " + reasonDetail + " там нет";
            case "header-not-allowed" -> "браузер спросил про " + reasonDetail
                    + ", а в Access-Control-Allow-Headers указано только " + join(policy.allowHeaders);
            default -> originFailureRu(failure);
        };
    }

    private String originFailureEn(String failure) {
        return switch (failure) {
            case "no-allow-origin" -> "it carries no Access-Control-Allow-Origin header at all";
            case "origin-mismatch" -> "Access-Control-Allow-Origin says " + reasonDetail
                    + ", which is not " + pageOrigin;
            case "credentials-wildcard" -> "the request carried cookies while "
                    + "Access-Control-Allow-Origin is the wildcard *";
            default -> "the request carried cookies and Access-Control-Allow-Credentials: true "
                    + "is missing";
        };
    }

    private String originFailureRu(String failure) {
        return switch (failure) {
            case "no-allow-origin" -> "в нём вообще нет заголовка Access-Control-Allow-Origin";
            case "origin-mismatch" -> "в Access-Control-Allow-Origin указан " + reasonDetail
                    + ", а это не " + pageOrigin;
            case "credentials-wildcard" -> "запрос нёс куки, тогда как в "
                    + "Access-Control-Allow-Origin стоит подстановочный знак *";
            default -> "запрос нёс куки, а заголовка Access-Control-Allow-Credentials: true нет";
        };
    }

    private String credentialsFailureEn(String failure) {
        if ("credentials-wildcard".equals(failure)) {
            return "The request was sent with cookies, and the answer says "
                    + "Access-Control-Allow-Origin: * — which a credentialed request is never allowed "
                    + "to accept. The wildcard means 'any page may read this', and no server can mean "
                    + "that about a logged-in user's data, so the browser blocks it. The fix is to echo "
                    + "the exact origin " + pageOrigin + " and add Access-Control-Allow-Credentials: true";
        }
        return "The request was sent with cookies, but the answer never said "
                + "Access-Control-Allow-Credentials: true — so the browser blocks the response. "
                + "Allowing the origin is not the same as allowing the origin to act as the logged-in "
                + "user";
    }

    private String credentialsFailureRu(String failure) {
        if ("credentials-wildcard".equals(failure)) {
            return "Запрос ушёл вместе с куками, а в ответе стоит Access-Control-Allow-Origin: * — "
                    + "запрос с куками никогда не имеет права это принять. Звёздочка означает «читать "
                    + "может любая страница», и ни один сервер не может утверждать такое про данные "
                    + "залогиненного пользователя, поэтому браузер блокирует ответ. Лечится тем, что "
                    + "сервер возвращает точный origin " + pageOrigin + " и добавляет "
                    + "Access-Control-Allow-Credentials: true";
        }
        return "Запрос ушёл вместе с куками, но в ответе не было "
                + "Access-Control-Allow-Credentials: true — поэтому браузер блокирует ответ. Разрешить "
                + "origin и разрешить ему действовать от имени залогиненного пользователя — это разные "
                + "вещи";
    }

    private String preflightTriggerEn() {
        if (!SIMPLE_METHODS.contains(request.method)) {
            return " (" + request.method + " is not one of GET, HEAD, POST)";
        }
        return " (" + join(nonSafelistedHeaders()) + " is not on the header safelist)";
    }

    private String preflightTriggerRu() {
        if (!SIMPLE_METHODS.contains(request.method)) {
            return " (" + request.method + " — не GET, не HEAD и не POST)";
        }
        return " (заголовка " + join(nonSafelistedHeaders()) + " нет в безопасном списке)";
    }

    private String describeHeadersEn() {
        if (request.headers.isEmpty()) {
            return " and no extra headers";
        }
        return " and " + describe(request.headers);
    }

    private String describeHeadersRu() {
        if (request.headers.isEmpty()) {
            return " без дополнительных заголовков";
        }
        return " и с заголовками " + describe(request.headers);
    }

    private String credentialsEn() {
        return request.credentials ? ", credentials: 'include' (cookies attached)" : "";
    }

    private String credentialsRu() {
        return request.credentials ? ", credentials: 'include' (с куками)" : "";
    }

    private String policyEn() {
        if (policy.allowOrigin == null) {
            return "no CORS headers at all";
        }
        StringBuilder sb = new StringBuilder("Access-Control-Allow-Origin: ").append(policy.allowOrigin);
        if (!policy.allowMethods.isEmpty()) {
            sb.append(", Access-Control-Allow-Methods: ").append(join(policy.allowMethods));
        }
        if (!policy.allowHeaders.isEmpty()) {
            sb.append(", Access-Control-Allow-Headers: ").append(join(policy.allowHeaders));
        }
        if (!policy.exposeHeaders.isEmpty()) {
            sb.append(", Access-Control-Expose-Headers: ").append(join(policy.exposeHeaders));
        }
        if (policy.allowCredentials) {
            sb.append(", Access-Control-Allow-Credentials: true");
        }
        if (policy.maxAge > 0) {
            sb.append(", Access-Control-Max-Age: ").append(policy.maxAge);
        }
        return sb.toString();
    }

    private String policyRu() {
        return policy.allowOrigin == null ? "никаких заголовков CORS" : policyEn();
    }

    private static String describe(Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(header.getKey()).append(": ").append(header.getValue());
        }
        return sb.toString();
    }

    private static String join(List<String> parts) {
        return parts.isEmpty() ? "(none)" : String.join(", ", parts);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("pageOrigin", pageOrigin);
        s.put("apiOrigin", apiOrigin);
        s.put("crossOrigin", crossOrigin);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("allowOrigin", policy.allowOrigin);
        p.put("allowMethods", new ArrayList<>(policy.allowMethods));
        p.put("allowHeaders", new ArrayList<>(policy.allowHeaders));
        p.put("exposeHeaders", new ArrayList<>(policy.exposeHeaders));
        p.put("allowCredentials", policy.allowCredentials);
        p.put("maxAge", policy.maxAge);
        s.put("policy", p);

        if (request != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("method", request.method);
            r.put("path", request.path);
            List<Object> headers = new ArrayList<>();
            List<String> unsafe = nonSafelistedHeaders();
            for (Map.Entry<String, String> header : request.headers.entrySet()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", header.getKey());
                entry.put("value", header.getValue());
                entry.put("safelisted", !unsafe.contains(header.getKey()));
                headers.add(entry);
            }
            r.put("headers", headers);
            r.put("credentials", request.credentials);
            r.put("wantsHeaders", new ArrayList<>(request.wantsHeaders));
            r.put("needsPreflight", needsPreflight);
            s.put("request", r);

            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("stage", stage);
            outcome.put("preflight", preflightStatus);
            outcome.put("delivered", delivered);
            outcome.put("readable", readable);
            outcome.put("reason", reason);
            outcome.put("detail", reasonDetail);
            s.put("outcome", outcome);
        }

        s.put("hiddenHeaders", new ArrayList<>(hiddenHeaders));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("requests", requests);
        stats.put("reachedServer", reachedServer);
        stats.put("blockedByBrowser", blockedByBrowser);
        stats.put("preflights", preflights);
        stats.put("preflightsSaved", preflightsSaved);
        s.put("stats", stats);
        return s;
    }
}
