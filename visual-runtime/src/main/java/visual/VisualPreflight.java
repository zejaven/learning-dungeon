package visual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of the CORS <strong>preflight</strong> — the extra
 * {@code OPTIONS} request a browser sends before a cross-origin call it is not
 * already allowed to make.
 *
 * <p>Where {@link VisualCors} models the whole same-origin story, this model
 * zooms in on the handshake itself and makes four things visible:
 * <ul>
 *   <li><b>the classification</b> — method and headers are checked against the
 *       CORS safelist; only a request no HTML form could have produced is
 *       preflighted;</li>
 *   <li><b>the exchange</b> — what the browser puts in {@code OPTIONS}
 *       ({@code Origin}, {@code Access-Control-Request-Method},
 *       {@code Access-Control-Request-Headers}) and what it demands back;</li>
 *   <li><b>the verdict</b> — a denied preflight means the real request is never
 *       sent, so unlike a blocked simple request nothing reaches the handler;</li>
 *   <li><b>the cache</b> — {@code Access-Control-Max-Age} lets one answer serve
 *       many calls, keyed by path, method, header set and credentials mode.</li>
 * </ul>
 *
 * <p>The server side is modelled too, because the classic production failure is
 * not a CORS bug: a security filter that authenticates <em>every</em> request
 * answers the preflight with {@code 401} — and the preflight carries no cookies
 * and no {@code Authorization} header, so it can never satisfy it.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualPreflight {

    /** Methods a browser will send cross-origin without asking permission first. */
    private static final Set<String> SAFELISTED_METHODS = Set.of("GET", "HEAD", "POST");
    /** Request headers that never trigger a preflight. */
    private static final Set<String> SAFELISTED_REQUEST_HEADERS =
            Set.of("accept", "accept-language", "content-language");
    /** The only Content-Type values an HTML form could produce, so they are safelisted too. */
    private static final Set<String> SAFELISTED_CONTENT_TYPES =
            Set.of("application/x-www-form-urlencoded", "multipart/form-data", "text/plain");
    /** Browsers refuse to remember a preflight forever; Chromium's ceiling is two hours. */
    private static final int BROWSER_MAX_AGE_CAP = 7200;

    /**
     * The API being called: how it answers {@code OPTIONS}. {@link #withoutCors()}
     * is a server that never learned about CORS — it still answers, it just says
     * nothing the browser can act on.
     */
    public static final class Api {

        private final boolean corsEnabled;
        private boolean authFilterFirst;
        private String allowOrigin;
        private final List<String> allowMethods = new ArrayList<>();
        private final List<String> allowHeaders = new ArrayList<>();
        private boolean allowCredentials;
        private int maxAge;

        private Api(boolean corsEnabled) {
            this.corsEnabled = corsEnabled;
        }

        /** An API with a CORS handler that answers preflights. */
        public static Api cors() {
            return new Api(true);
        }

        /** An API with no CORS configuration at all — OPTIONS gets a bare answer. */
        public static Api withoutCors() {
            return new Api(false);
        }

        /** Answers {@code Access-Control-Allow-Origin: <origin>}. */
        public Api allowOrigin(String origin) {
            this.allowOrigin = origin;
            return this;
        }

        /** Answers {@code Access-Control-Allow-Origin: *}. */
        public Api allowAnyOrigin() {
            return allowOrigin("*");
        }

        /** Answers {@code Access-Control-Allow-Methods}. */
        public Api allowMethods(String... methods) {
            for (String method : methods) {
                allowMethods.add(method.toUpperCase(Locale.ROOT));
            }
            return this;
        }

        /** Answers {@code Access-Control-Allow-Headers}. */
        public Api allowHeaders(String... headers) {
            Collections.addAll(allowHeaders, headers);
            return this;
        }

        /** Answers {@code Access-Control-Allow-Headers: *}. */
        public Api allowAnyHeader() {
            return allowHeaders("*");
        }

        /** Answers {@code Access-Control-Allow-Credentials: true}. */
        public Api allowCredentials() {
            this.allowCredentials = true;
            return this;
        }

        /** Answers {@code Access-Control-Max-Age} — how long the answer may be reused. */
        public Api maxAge(int seconds) {
            this.maxAge = seconds;
            return this;
        }

        /**
         * A security filter that authenticates every request runs before the CORS
         * handler, so the unauthenticated {@code OPTIONS} is rejected with 401.
         */
        public Api authFilterBeforeCors() {
            this.authFilterFirst = true;
            return this;
        }
    }

    /** One call the page's JavaScript makes. */
    public static final class Call {

        private final String method;
        private final String path;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private boolean credentials;

        private Call(String method, String path) {
            this.method = method.toUpperCase(Locale.ROOT);
            this.path = path;
        }

        public static Call get(String path) {
            return new Call("GET", path);
        }

        public static Call post(String path) {
            return new Call("POST", path);
        }

        public static Call put(String path) {
            return new Call("PUT", path);
        }

        public static Call patch(String path) {
            return new Call("PATCH", path);
        }

        public static Call delete(String path) {
            return new Call("DELETE", path);
        }

        /** Any other method, e.g. HEAD. */
        public static Call method(String method, String path) {
            return new Call(method, path);
        }

        /** Adds a request header exactly as the fetch call would. */
        public Call header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** {@code Content-Type: application/json} — NOT safelisted, so it preflights. */
        public Call json() {
            return header("Content-Type", "application/json");
        }

        /** {@code Content-Type: application/x-www-form-urlencoded} — safelisted. */
        public Call formEncoded() {
            return header("Content-Type", "application/x-www-form-urlencoded");
        }

        /** {@code Content-Type: text/plain} — safelisted, which is why beacons use it. */
        public Call textPlain() {
            return header("Content-Type", "text/plain");
        }

        /** {@code Authorization: Bearer <token>} — never safelisted. */
        public Call bearer(String token) {
            return header("Authorization", "Bearer " + token);
        }

        /** {@code credentials: 'include'} — cookies on the REAL request, never on OPTIONS. */
        public Call withCredentials() {
            this.credentials = true;
            return this;
        }
    }

    /** One remembered preflight answer. */
    private static final class CacheEntry {

        private final String path;
        private final String method;
        private final List<String> headers;
        private final boolean credentials;
        private final int expiresAt;

        private CacheEntry(String path, String method, List<String> headers,
                           boolean credentials, int expiresAt) {
            this.path = path;
            this.method = method;
            this.headers = headers;
            this.credentials = credentials;
            this.expiresAt = expiresAt;
        }
    }

    private final String pageOrigin;
    private final String apiOrigin;

    private Api api;
    /** Preflight answers the browser may reuse, keyed by path + method + headers + credentials. */
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();
    /** Seconds since the page loaded; only {@link #advanceSeconds(int)} moves it. */
    private int clock;

    private int calls;
    private int preflightsSent;
    private int cacheHits;
    private int denied;
    private int realRequests;
    private int roundTrips;

    private Call call;
    private boolean needsPreflight;
    private String trigger = "none";
    private String triggerDetail = "";
    private List<String> askedHeaders = new ArrayList<>();
    private String preflightStatus = "none";
    private int responseStatus;
    private int effectiveMaxAge;
    private String reason;
    private String reasonDetail = "";
    private String stage = "idle";
    private boolean realRequestSent;

    private VisualPreflight(String pageOrigin, String apiOrigin, Api api) {
        this.pageOrigin = pageOrigin;
        this.apiOrigin = apiOrigin;
        this.api = api;
    }

    /**
     * A page loaded from {@code pageOrigin} whose JavaScript calls an API at a
     * different {@code apiOrigin} answering as {@code api} describes.
     */
    public static VisualPreflight browser(String pageOrigin, String apiOrigin, Api api) {
        VisualPreflight browser = new VisualPreflight(pageOrigin, apiOrigin, api);
        Trace.event("PREFLIGHT_SETUP",
                "A page from " + pageOrigin + " calls the API at " + apiOrigin
                        + " — a different origin, so the browser decides per call whether it may send "
                        + "it straight away or must ask first. The API answers OPTIONS with: "
                        + browser.answerEn(),
                "Страница с " + pageOrigin + " обращается к API на " + apiOrigin
                        + " — это другой origin, поэтому браузер для каждого вызова решает, можно ли "
                        + "отправить его сразу или сначала надо спросить. API отвечает на OPTIONS так: "
                        + browser.answerRu(),
                List.of("api"), browser.state());
        return browser;
    }

    /** The API is redeployed with a different configuration; the cache starts empty. */
    public void redeploy(Api api) {
        this.api = api;
        cache.clear();
        Trace.event("PREFLIGHT_SETUP",
                "The API is redeployed and now answers OPTIONS with: " + answerEn()
                        + ". The model empties the preflight cache here so the next call shows the new "
                        + "configuration — a real browser would keep the OLD permission until its "
                        + "Access-Control-Max-Age ran out, which is why a corrected CORS config can "
                        + "look like it did not take effect",
                "API переразвёрнут и теперь отвечает на OPTIONS так: " + answerRu()
                        + ". Модель здесь очищает кеш предварительных запросов, чтобы следующий вызов "
                        + "показал новую конфигурацию, — настоящий браузер держал бы СТАРОЕ разрешение "
                        + "до конца его Access-Control-Max-Age, из-за чего исправленная настройка CORS "
                        + "кажется не применившейся",
                List.of("api"), state());
    }

    /** Moves the clock forward so cached preflights can expire. */
    public void advanceSeconds(int seconds) {
        clock += seconds;
        Trace.event("CLOCK_ADVANCED",
                "The user keeps the page open: " + seconds + " s pass, the clock is now at " + clock
                        + " s. Nothing is sent — but a remembered preflight is only good until its "
                        + "Access-Control-Max-Age runs out",
                "Пользователь не закрывает страницу: проходит " + seconds + " с, на часах теперь "
                        + clock + " с. Ничего не отправляется — но запомненное разрешение действует "
                        + "лишь до конца Access-Control-Max-Age",
                List.of("cache"), state());
    }

    /** Runs one fetch() the way a browser would. */
    public void send(Call outgoing) {
        begin(outgoing);
        Trace.event("CALL_ISSUED",
                "JavaScript calls fetch(\"" + apiOrigin + outgoing.path + "\") with " + outgoing.method
                        + describeHeadersEn() + credentialsEn()
                        + ". Before anything leaves the machine, the browser classifies it",
                "JavaScript вызывает fetch(\"" + apiOrigin + outgoing.path + "\") методом "
                        + outgoing.method + describeHeadersRu() + credentialsRu()
                        + ". Прежде чем что-либо покинет машину, браузер классифицирует вызов",
                List.of("call"), state());

        if (!needsPreflight) {
            preflightStatus = "none";
            Trace.event("SIMPLE_CALL",
                    "No preflight needed: " + outgoing.method + " is one of GET, HEAD, POST and every "
                            + "header is on the CORS safelist, so an ordinary HTML form could already "
                            + "have sent this. Asking permission for it would break the web, so the "
                            + "browser sends it immediately",
                    "Предварительный запрос не нужен: " + outgoing.method + " — это GET, HEAD или POST, "
                            + "и все заголовки из безопасного списка CORS, то есть такой запрос могла "
                            + "бы отправить и обычная HTML-форма. Спрашивать про неё разрешение — "
                            + "сломать веб, поэтому браузер отправляет запрос сразу",
                    List.of("call"), state());
            sendRealRequest(false);
            return;
        }

        Trace.event("PREFLIGHT_REQUIRED",
                "This is not a request a form could have made" + triggerEn()
                        + ", so it is a preflighted request: the browser will ask permission first and "
                        + "send nothing until it gets an answer",
                "Такой запрос форма отправить не могла" + triggerRu()
                        + ", поэтому это запрос с предварительной проверкой: браузер сначала спросит "
                        + "разрешение и до ответа не отправит ничего",
                List.of("call", "preflight"), state());

        String key = cacheKey();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt > clock) {
            preflightStatus = "cached";
            cacheHits++;
            Trace.event("CACHE_HIT",
                    "The browser already asked about " + call.method + " " + call.path + " with "
                            + join(askedHeaders) + " and was told Access-Control-Max-Age: "
                            + api.maxAge + ". That permission is valid for another "
                            + (cached.expiresAt - clock) + " s, so no OPTIONS is sent this time",
                    "Браузер уже спрашивал про " + call.method + " " + call.path + " с заголовками "
                            + join(askedHeaders) + " и получил Access-Control-Max-Age: " + api.maxAge
                            + ". Это разрешение действует ещё " + (cached.expiresAt - clock)
                            + " с, поэтому OPTIONS сейчас не отправляется",
                    List.of("cache"), state());
            sendRealRequest(true);
            return;
        }
        if (cached != null) {
            cache.remove(key);
            Trace.event("CACHE_EXPIRED",
                    "There was a remembered answer for this combination, but it expired at "
                            + cached.expiresAt + " s and the clock is at " + clock
                            + " s. An expired entry is worth nothing — the browser must ask again",
                    "Запомненный ответ для этой комбинации был, но он истёк на " + cached.expiresAt
                            + " с, а на часах " + clock
                            + " с. Истёкшая запись ничего не стоит — браузер обязан спросить заново",
                    List.of("cache"), state());
        }

        preflightsSent++;
        roundTrips++;
        preflightStatus = "sent";
        Trace.event("OPTIONS_SENT",
                "The browser sends the preflight itself: OPTIONS " + call.path
                        + " with Origin: " + pageOrigin
                        + ", Access-Control-Request-Method: " + call.method
                        + (askedHeaders.isEmpty() ? ""
                            : ", Access-Control-Request-Headers: " + join(askedHeaders))
                        + ". It carries no body, no cookies and no Authorization header — only the "
                        + "NAMES of the headers the real request wants to set, never their values",
                "Браузер отправляет сам предварительный запрос: OPTIONS " + call.path
                        + " с Origin: " + pageOrigin
                        + ", Access-Control-Request-Method: " + call.method
                        + (askedHeaders.isEmpty() ? ""
                            : ", Access-Control-Request-Headers: " + join(askedHeaders))
                        + ". В нём нет тела, нет кук и нет заголовка Authorization — только ИМЕНА "
                        + "заголовков, которые хочет выставить настоящий запрос, но не их значения",
                List.of("preflight"), state());

        if (api.authFilterFirst) {
            responseStatus = 401;
            Trace.event("OPTIONS_UNAUTHORIZED",
                    "A security filter runs before the CORS handler and demands authentication on every "
                            + "request, so it answers 401 — and it cannot be satisfied, because a "
                            + "preflight is unauthenticated by design. The console will say the CORS "
                            + "policy blocked the call, which sends people looking at CORS configuration "
                            + "instead of at the filter chain",
                    "Фильтр безопасности стоит перед обработчиком CORS и требует аутентификации от "
                            + "каждого запроса, поэтому отвечает 401 — и удовлетворить его нельзя, ведь "
                            + "предварительный запрос по своей природе неаутентифицирован. В консоли "
                            + "будет написано, что вызов заблокирован политикой CORS, и человек пойдёт "
                            + "разбираться с настройками CORS, а не с цепочкой фильтров",
                    List.of("preflight", "api"), state());
            deny("bad-status", String.valueOf(responseStatus));
            return;
        }

        responseStatus = api.corsEnabled ? 204 : 200;
        Trace.event("OPTIONS_ANSWERED",
                "The API answers " + responseStatus + " with " + answerEn()
                        + ". Only the status and the Access-Control-* headers matter here — the body of "
                        + "a preflight response is ignored, and a redirect is not followed",
                "API отвечает " + responseStatus + ", " + answerRu()
                        + ". Здесь важны только статус и заголовки Access-Control-* — тело ответа на "
                        + "предварительный запрос игнорируется, а редирект не выполняется",
                List.of("preflight", "api"), state());

        String failure = evaluateAnswer();
        if (failure != null) {
            deny(failure, reasonDetail);
            return;
        }

        preflightStatus = "approved";
        effectiveMaxAge = Math.min(api.maxAge, BROWSER_MAX_AGE_CAP);
        if (effectiveMaxAge > 0) {
            cache.put(key, new CacheEntry(call.path, call.method,
                    new ArrayList<>(askedHeaders), call.credentials, clock + effectiveMaxAge));
        }
        Trace.event("PREFLIGHT_APPROVED",
                "Every question is answered yes: the origin matches, " + call.method
                        + " is in Access-Control-Allow-Methods"
                        + (askedHeaders.isEmpty() ? ""
                            : " and " + join(askedHeaders) + " in Access-Control-Allow-Headers")
                        + ". " + cacheNoteEn(),
                "На все вопросы ответ «да»: origin совпадает, " + call.method
                        + " есть в Access-Control-Allow-Methods"
                        + (askedHeaders.isEmpty() ? ""
                            : ", а " + join(askedHeaders) + " — в Access-Control-Allow-Headers")
                        + ". " + cacheNoteRu(),
                List.of("preflight", "cache"), state());

        sendRealRequest(false);
    }

    /** Prints what the whole run cost. */
    public void report() {
        Trace.event("PREFLIGHT_AUDIT",
                "After " + calls + " call(s): preflights sent: " + preflightsSent
                        + ", answered from the cache: " + cacheHits + ", denied: " + denied
                        + ", real requests that reached the API: " + realRequests
                        + ", network round trips in total: " + roundTrips
                        + " — every preflight that is not cached is one extra round trip before any "
                        + "useful work happens",
                "После вызовов (" + calls + "): отправлено предварительных запросов: " + preflightsSent
                        + ", отвечено из кеша: " + cacheHits + ", отклонено: " + denied
                        + ", настоящих запросов дошло до API: " + realRequests
                        + ", всего обращений по сети: " + roundTrips
                        + " — каждый некешированный предварительный запрос это лишний круг по сети до "
                        + "того, как начнётся полезная работа",
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    private void begin(Call outgoing) {
        calls++;
        call = outgoing;
        askedHeaders = nonSafelistedHeaders();
        trigger = "none";
        triggerDetail = "";
        if (!SAFELISTED_METHODS.contains(outgoing.method)) {
            trigger = "method";
            triggerDetail = outgoing.method;
        } else if (!askedHeaders.isEmpty()) {
            String first = askedHeaders.get(0);
            trigger = "content-type".equals(first) ? "content-type" : "header";
            triggerDetail = first;
        }
        needsPreflight = !"none".equals(trigger);
        preflightStatus = "none";
        responseStatus = 0;
        effectiveMaxAge = 0;
        reason = null;
        reasonDetail = "";
        stage = "classified";
        realRequestSent = false;
    }

    private void sendRealRequest(boolean fromCache) {
        realRequests++;
        roundTrips++;
        realRequestSent = true;
        stage = "settled";
        Trace.event("REAL_REQUEST_SENT",
                "Now the real " + call.method + " " + call.path + " goes out"
                        + (needsPreflight
                            ? (fromCache ? " on the strength of the remembered permission"
                                         : " — the second round trip of this call")
                            : "")
                        + ", this time with its body" + (call.credentials ? ", its cookies" : "")
                        + " and all of its headers. Its own response still needs "
                        + "Access-Control-Allow-Origin: the preflight granted permission to SEND, not "
                        + "permission to READ",
                "Теперь уходит настоящий " + call.method + " " + call.path
                        + (needsPreflight
                            ? (fromCache ? " — на основании запомненного разрешения"
                                         : " — это второе обращение по сети в рамках одного вызова")
                            : "")
                        + ", уже с телом" + (call.credentials ? ", с куками" : "")
                        + " и всеми заголовками. Его собственному ответу всё равно нужен "
                        + "Access-Control-Allow-Origin: предварительный запрос дал право ОТПРАВИТЬ, а "
                        + "не право ПРОЧИТАТЬ",
                List.of("call"), state());
    }

    private void deny(String failure, String detail) {
        preflightStatus = "denied";
        reason = failure;
        reasonDetail = detail;
        denied++;
        stage = "settled";
        Trace.event("PREFLIGHT_DENIED",
                "The browser refuses the call: " + failureEn(failure)
                        + ". fetch() rejects with a TypeError and the console prints a CORS error",
                "Браузер отклоняет вызов: " + failureRu(failure)
                        + ". fetch() падает с TypeError, а в консоли появляется ошибка CORS",
                List.of("preflight", "api"), state());
        Trace.event("REQUEST_NEVER_SENT",
                "Note what did NOT happen: the real " + call.method + " " + call.path
                        + " was never sent. No handler ran, nothing was created, changed or deleted, "
                        + "and the API's access log has only the OPTIONS line. That is the asymmetry "
                        + "with a simple request, which is delivered and executed before the browser "
                        + "decides anything",
                "Обратите внимание, чего НЕ произошло: настоящий " + call.method + " " + call.path
                        + " так и не был отправлен. Ни один обработчик не выполнился, ничего не создано, "
                        + "не изменено и не удалено, а в логе доступа API есть только строка про OPTIONS. "
                        + "Это и есть асимметрия с простым запросом, который доставляется и выполняется "
                        + "до того, как браузер что-либо решит",
                List.of("call"), state());
    }

    /** Request headers that are not on the CORS safelist, lowercased and sorted as a browser sends them. */
    private List<String> nonSafelistedHeaders() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> header : call.headers.entrySet()) {
            String name = header.getKey().toLowerCase(Locale.ROOT);
            if (SAFELISTED_REQUEST_HEADERS.contains(name)) {
                continue;
            }
            if ("content-type".equals(name)
                    && SAFELISTED_CONTENT_TYPES.contains(header.getValue().toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(name);
        }
        Collections.sort(out);
        return out;
    }

    /** What a cached answer covers: this path, method, header set and credentials mode. */
    private String cacheKey() {
        return call.path + "|" + call.method + "|" + String.join(",", askedHeaders)
                + "|" + call.credentials;
    }

    /** Why the browser refuses the preflight answer, or null when it accepts it. */
    private String evaluateAnswer() {
        if (responseStatus < 200 || responseStatus > 299) {
            reasonDetail = String.valueOf(responseStatus);
            return "bad-status";
        }
        if (api.allowOrigin == null) {
            return "no-allow-origin";
        }
        boolean wildcardOrigin = "*".equals(api.allowOrigin);
        if (call.credentials && wildcardOrigin) {
            return "credentials-wildcard";
        }
        if (!wildcardOrigin && !api.allowOrigin.equals(pageOrigin)) {
            reasonDetail = api.allowOrigin;
            return "origin-mismatch";
        }
        if (call.credentials && !api.allowCredentials) {
            return "credentials-not-allowed";
        }

        if (!listed(api.allowMethods, call.method) && !SAFELISTED_METHODS.contains(call.method)) {
            reasonDetail = call.method;
            return hasWildcard(api.allowMethods) && call.credentials
                    ? "wildcard-literal-with-credentials"
                    : "method-not-allowed";
        }
        for (String header : askedHeaders) {
            if (listed(api.allowHeaders, header)) {
                continue;
            }
            reasonDetail = header;
            if (hasWildcard(api.allowHeaders)) {
                if (call.credentials) {
                    return "wildcard-literal-with-credentials";
                }
                // The one header the wildcard deliberately does not cover.
                if ("authorization".equals(header)) {
                    return "authorization-not-listed";
                }
            }
            return "header-not-allowed";
        }
        return null;
    }

    /**
     * Whether a value is granted by an Access-Control-Allow-* list. {@code *} matches
     * anything only for a request without credentials, and never {@code Authorization}.
     */
    private boolean listed(List<String> allowed, String value) {
        for (String entry : allowed) {
            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
            if ("*".equals(entry) && !call.credentials
                    && !"authorization".equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWildcard(List<String> allowed) {
        return allowed.contains("*");
    }

    // ------------------------------------------------------------- descriptions

    private String failureEn(String failure) {
        return switch (failure) {
            case "bad-status" -> "the preflight was answered with " + reasonDetail
                    + ", and only a 2xx answer counts as permission";
            case "no-allow-origin" -> "the answer carries no Access-Control-Allow-Origin header, so "
                    + "there is nothing granting " + pageOrigin + " anything";
            case "origin-mismatch" -> "Access-Control-Allow-Origin says " + reasonDetail
                    + ", which is not " + pageOrigin;
            case "credentials-wildcard" -> "the call carries cookies while Access-Control-Allow-Origin "
                    + "is the wildcard *, a combination the browser never accepts";
            case "credentials-not-allowed" -> "the call carries cookies and "
                    + "Access-Control-Allow-Credentials: true is missing";
            case "method-not-allowed" -> "Access-Control-Allow-Methods lists "
                    + join(api.allowMethods) + ", and " + reasonDetail + " is not among them";
            case "authorization-not-listed" -> "Access-Control-Allow-Headers is *, and the wildcard "
                    + "deliberately does not cover Authorization — that one header always has to be "
                    + "named explicitly";
            case "wildcard-literal-with-credentials" -> "the call carries cookies, and for a "
                    + "credentialed request * is matched literally rather than as a wildcard, so "
                    + reasonDetail + " counts as not listed";
            default -> "the browser asked about " + reasonDetail
                    + ", and Access-Control-Allow-Headers lists only " + join(api.allowHeaders);
        };
    }

    private String failureRu(String failure) {
        return switch (failure) {
            case "bad-status" -> "на предварительный запрос ответили " + reasonDetail
                    + ", а разрешением считается только ответ 2xx";
            case "no-allow-origin" -> "в ответе нет заголовка Access-Control-Allow-Origin, то есть "
                    + pageOrigin + " ничего не разрешено";
            case "origin-mismatch" -> "в Access-Control-Allow-Origin указан " + reasonDetail
                    + ", а это не " + pageOrigin;
            case "credentials-wildcard" -> "вызов идёт с куками, тогда как в "
                    + "Access-Control-Allow-Origin стоит подстановочный знак *, — такое сочетание "
                    + "браузер не принимает никогда";
            case "credentials-not-allowed" -> "вызов идёт с куками, а заголовка "
                    + "Access-Control-Allow-Credentials: true нет";
            case "method-not-allowed" -> "в Access-Control-Allow-Methods перечислено "
                    + join(api.allowMethods) + ", и " + reasonDetail + " там нет";
            case "authorization-not-listed" -> "в Access-Control-Allow-Headers стоит *, а "
                    + "подстановочный знак намеренно не покрывает Authorization — этот заголовок всегда "
                    + "приходится называть явно";
            case "wildcard-literal-with-credentials" -> "вызов идёт с куками, а для запроса с куками * "
                    + "сравнивается буквально, а не как подстановочный знак, поэтому " + reasonDetail
                    + " считается неперечисленным";
            default -> "браузер спросил про " + reasonDetail
                    + ", а в Access-Control-Allow-Headers указано только " + join(api.allowHeaders);
        };
    }

    private String cacheNoteEn() {
        if (api.maxAge <= 0) {
            return "There is no Access-Control-Max-Age, so the browser remembers nothing and the very "
                    + "next identical call will preflight again";
        }
        if (api.maxAge > BROWSER_MAX_AGE_CAP) {
            return "Access-Control-Max-Age asks for " + api.maxAge + " s, but browsers cap what they "
                    + "will remember — this one keeps it for " + effectiveMaxAge + " s";
        }
        return "Access-Control-Max-Age: " + api.maxAge + " lets the browser remember this answer for "
                + effectiveMaxAge + " s, for this path, method, header set and credentials mode";
    }

    private String cacheNoteRu() {
        if (api.maxAge <= 0) {
            return "Access-Control-Max-Age нет, поэтому браузер ничего не запоминает, и следующий же "
                    + "такой же вызов снова пойдёт с предварительным запросом";
        }
        if (api.maxAge > BROWSER_MAX_AGE_CAP) {
            return "В Access-Control-Max-Age просят " + api.maxAge + " с, но браузеры ограничивают срок "
                    + "хранения — этот запомнит на " + effectiveMaxAge + " с";
        }
        return "Access-Control-Max-Age: " + api.maxAge + " разрешает браузеру помнить этот ответ "
                + effectiveMaxAge + " с — для этого пути, метода, набора заголовков и режима кук";
    }

    private String triggerEn() {
        return switch (trigger) {
            case "method" -> " (" + triggerDetail + " is not one of GET, HEAD, POST)";
            case "content-type" -> " (Content-Type: " + call.headers.get(contentTypeKey())
                    + " is not one of the three values a form can produce)";
            default -> " (" + triggerDetail + " is not on the header safelist)";
        };
    }

    private String triggerRu() {
        return switch (trigger) {
            case "method" -> " (" + triggerDetail + " — не GET, не HEAD и не POST)";
            case "content-type" -> " (Content-Type: " + call.headers.get(contentTypeKey())
                    + " — не одно из трёх значений, которые может выдать форма)";
            default -> " (заголовка " + triggerDetail + " нет в безопасном списке)";
        };
    }

    /** The Content-Type key as the call spelled it, so the value can be quoted back. */
    private String contentTypeKey() {
        for (String name : call.headers.keySet()) {
            if ("content-type".equalsIgnoreCase(name)) {
                return name;
            }
        }
        return "Content-Type";
    }

    private String describeHeadersEn() {
        return call.headers.isEmpty() ? " and no extra headers" : " and " + describe(call.headers);
    }

    private String describeHeadersRu() {
        return call.headers.isEmpty() ? " без дополнительных заголовков"
                : " и с заголовками " + describe(call.headers);
    }

    private String credentialsEn() {
        return call.credentials ? ", credentials: 'include'" : "";
    }

    private String credentialsRu() {
        return call.credentials ? ", credentials: 'include'" : "";
    }

    private String answerEn() {
        if (api.authFilterFirst) {
            return "401 from the security filter, before the CORS handler is ever reached";
        }
        if (!api.corsEnabled || api.allowOrigin == null) {
            return "no Access-Control-* headers at all";
        }
        StringBuilder sb = new StringBuilder("Access-Control-Allow-Origin: ").append(api.allowOrigin);
        if (!api.allowMethods.isEmpty()) {
            sb.append(", Access-Control-Allow-Methods: ").append(join(api.allowMethods));
        }
        if (!api.allowHeaders.isEmpty()) {
            sb.append(", Access-Control-Allow-Headers: ").append(join(api.allowHeaders));
        }
        if (api.allowCredentials) {
            sb.append(", Access-Control-Allow-Credentials: true");
        }
        if (api.maxAge > 0) {
            sb.append(", Access-Control-Max-Age: ").append(api.maxAge);
        }
        return sb.toString();
    }

    private String answerRu() {
        if (api.authFilterFirst) {
            return "401 от фильтра безопасности, до обработчика CORS дело вообще не доходит";
        }
        if (!api.corsEnabled || api.allowOrigin == null) {
            return "без единого заголовка Access-Control-*";
        }
        return answerEn();
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
        s.put("clock", clock);

        Map<String, Object> a = new LinkedHashMap<>();
        a.put("corsEnabled", api.corsEnabled);
        a.put("authFilterFirst", api.authFilterFirst);
        a.put("allowOrigin", api.allowOrigin);
        a.put("allowMethods", new ArrayList<>(api.allowMethods));
        a.put("allowHeaders", new ArrayList<>(api.allowHeaders));
        a.put("allowCredentials", api.allowCredentials);
        a.put("maxAge", api.maxAge);
        s.put("api", a);

        if (call != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("method", call.method);
            c.put("path", call.path);
            List<Object> headers = new ArrayList<>();
            for (Map.Entry<String, String> header : call.headers.entrySet()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", header.getKey());
                entry.put("value", header.getValue());
                entry.put("safelisted", !askedHeaders.contains(header.getKey().toLowerCase(Locale.ROOT)));
                headers.add(entry);
            }
            c.put("headers", headers);
            c.put("credentials", call.credentials);
            c.put("needsPreflight", needsPreflight);
            c.put("trigger", trigger);
            c.put("triggerDetail", triggerDetail);
            s.put("call", c);

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("status", preflightStatus);
            p.put("requestMethod", call.method);
            p.put("requestHeaders", new ArrayList<>(askedHeaders));
            p.put("responseStatus", responseStatus);
            p.put("effectiveMaxAge", effectiveMaxAge);
            p.put("reason", reason);
            p.put("detail", reasonDetail);
            s.put("preflight", p);

            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("stage", stage);
            outcome.put("realRequestSent", realRequestSent);
            s.put("outcome", outcome);
        }

        List<Object> entries = new ArrayList<>();
        for (CacheEntry entry : cache.values()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("path", entry.path);
            e.put("method", entry.method);
            e.put("headers", new ArrayList<>(entry.headers));
            e.put("credentials", entry.credentials);
            e.put("expiresAt", entry.expiresAt);
            e.put("remaining", Math.max(0, entry.expiresAt - clock));
            entries.add(e);
        }
        s.put("cache", entries);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("calls", calls);
        stats.put("preflightsSent", preflightsSent);
        stats.put("cacheHits", cacheHits);
        stats.put("denied", denied);
        stats.put("realRequests", realRequests);
        stats.put("roundTrips", roundTrips);
        s.put("stats", stats);
        return s;
    }
}
