package visual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A <em>teaching model</em> of cross-site request forgery: a bank the user is signed
 * in to, a page on somebody else's domain, and a browser that helpfully attaches the
 * bank's session cookie to any request aimed at the bank — including the ones that
 * other page caused.
 *
 * <p>The whole vulnerability is in one word: <em>ambient</em>. A cookie is authority
 * the browser applies by destination, not by intent. The server therefore receives a
 * perfectly authenticated request the user never asked for, and every authentication
 * check it runs will pass, because it really is the user's session.
 *
 * <p>The model makes visible the three decisions that settle the outcome:
 * <ul>
 *   <li><b>the browser's</b> — will it send the request at all (preflight), and will it
 *       attach the cookie ({@code SameSite})?</li>
 *   <li><b>the server's</b> — can it tell a request built by its own page from one built
 *       by a stranger's page (a CSRF token, the {@code Origin} header)?</li>
 *   <li><b>the design's</b> — is the credential ambient in the first place? A token the
 *       application's own JavaScript must attach cannot be forged this way.</li>
 * </ul>
 *
 * <p>The rules here are deliberate simplifications of what a real browser does — enough
 * to be faithful about <em>which defence stops which delivery</em>, and small enough to
 * read. Every step emits a bilingual {@link Trace} event; the class is dependency-free.
 */
public class VisualCsrf {

    /** The site under attack — the one holding the session and the money. */
    public static final String SITE = "https://bank.example";
    /** The page the victim is actually looking at. */
    public static final String ATTACKER_SITE = "https://evil.example";
    /** The session cookie, which is the whole reason this attack exists. */
    public static final String COOKIE_NAME = "session";
    /** Its value — never guessed, never read, and never needed by the attacker. */
    public static final String COOKIE_VALUE = "s3ss10n-7b41";
    /** The synchronizer token the bank puts into its own pages. */
    public static final String CSRF_TOKEN = "csrf-8d31f0";

    private static final String ATTACKER_PAYEE = "attacker-42";
    private static final String TRUSTED_PAYEE = "payee-7";
    private static final int OPENING_BALANCE = 5000;

    /**
     * How the attacker's page makes the victim's browser send the request. The delivery
     * decides two things the attacker does not otherwise control: whether the browser
     * asks permission first, and whether {@code SameSite=Lax} lets the cookie through.
     */
    public enum Delivery {

        /** A one-pixel image. No click, no JavaScript; works from an HTML email too. */
        IMAGE_TAG("GET", false, false,
                "an <img> tag pointed at the bank, which the browser loads on its own",
                "тег <img>, направленный на банк, — браузер загрузит его сам"),
        /** A link the victim clicks: a top-level navigation, which {@code SameSite=Lax} permits. */
        LINK_CLICK("GET", true, false,
                "a link the victim clicks, which counts as a top-level navigation",
                "ссылка, по которой кликает жертва, — это переход верхнего уровня"),
        /** The classic: a hidden form that submits itself. A cross-origin POST needs no permission. */
        AUTO_FORM("POST", true, false,
                "a hidden form that submits itself, which HTML has always been allowed to do across "
                        + "origins",
                "скрытая форма, которая отправляет себя сама, — HTML всегда мог делать это между "
                        + "источниками"),
        /** A scripted POST with a JSON body — the one delivery the browser asks permission for. */
        FETCH_JSON("POST", false, true,
                "a fetch() with Content-Type: application/json, which is not a shape a plain HTML "
                        + "form can produce",
                "вызов fetch() с Content-Type: application/json — такую форму запроса обычная "
                        + "HTML-форма выдать не может");

        private final String method;
        private final boolean topLevel;
        private final boolean preflighted;
        private final String en;
        private final String ru;

        Delivery(String method, boolean topLevel, boolean preflighted, String en, String ru) {
            this.method = method;
            this.topLevel = topLevel;
            this.preflighted = preflighted;
            this.en = en;
            this.ru = ru;
        }

        /** Whether {@code SameSite=Lax} still attaches the cookie: top-level and a safe method. */
        boolean allowedByLax() {
            return topLevel && "GET".equals(method);
        }
    }

    // ------------------------------------------------------------------- state

    /** None | Lax | Strict. */
    private String sameSite = "None";
    private boolean csrfToken;
    private boolean originCheck;
    private boolean postOnly;
    /** cookie | bearer. */
    private String auth = "cookie";
    private boolean corsReflectsOrigin;

    private boolean started;
    private String origin;
    private String deliveryKind;
    private String method;
    private String target;
    private String body;
    private String markup;
    private String payee;
    private int amount;
    private boolean crossSite;
    private boolean userInitiated;
    private boolean preflightRequired;
    private boolean cookieAllowed;
    private boolean credentials;
    private String sentToken;
    private boolean reached;

    private String stage = "idle";
    private boolean authenticated;
    private boolean performed;
    private String blockedBy;
    private boolean responseReadable;

    private int balance = OPENING_BALANCE;
    private int stolen;

    private int attempts;
    private int reachedServer;
    private int changed;
    private int forged;
    private int blocked;

    private VisualCsrf() {
    }

    /**
     * A bank the user is already signed in to: session in a cookie, no {@code SameSite},
     * no token, no origin check. Every one of those omissions was normal in the code that
     * first shipped this endpoint.
     */
    public static VisualCsrf bank() {
        VisualCsrf bank = new VisualCsrf();
        Trace.event("CSRF_SETUP",
                "The user is signed in to " + SITE + " and the session lives in a cookie. That is the "
                        + "detail the entire attack rests on: the browser attaches this cookie to "
                        + "every request addressed to " + SITE + ", automatically, no matter which "
                        + "page caused the request. Nothing here is misconfigured — cookies are "
                        + "specified to work by destination, not by intent",
                "Пользователь вошёл в " + SITE + ", и сессия лежит в cookie. Именно на этой детали "
                        + "держится вся атака: браузер прикрепляет эту cookie к каждому запросу по "
                        + "адресу " + SITE + " автоматически, независимо от того, какая страница этот "
                        + "запрос вызвала. Здесь ничего не настроено криво — cookie по замыслу "
                        + "работают по адресу назначения, а не по намерению",
                List.of("session", "account"), bank.state());
        return bank;
    }

    // ---------------------------------------------------------------- defences

    /**
     * Sets the {@code SameSite} attribute of the session cookie: {@code None}, {@code Lax}
     * (what browsers now default to) or {@code Strict}. This is the only defence here the
     * browser applies, and it is applied before the request is even sent.
     */
    public VisualCsrf sameSite(String policy) {
        sameSite = normalizePolicy(policy);
        String en;
        String ru;
        if ("Strict".equals(sameSite)) {
            en = "the cookie is left out of every request that did not originate on " + SITE
                    + " itself — including an ordinary link from an email or a search result, which "
                    + "is why a Strict session cookie makes users look logged out when they arrive "
                    + "from outside";
            ru = "cookie не попадает ни в один запрос, начатый не на самом " + SITE
                    + ", — включая обычную ссылку из письма или из поисковой выдачи, поэтому с "
                    + "cookie Strict пользователи, пришедшие снаружи, выглядят разлогиненными";
        } else if ("Lax".equals(sameSite)) {
            en = "the cookie is withheld from cross-site subresource loads and from cross-site POSTs, "
                    + "but it is still attached to a top-level GET navigation. Read that twice: a "
                    + "link the victim clicks still arrives fully authenticated";
            ru = "cookie не отправляется при кросс-сайтовой загрузке подресурсов и при кросс-сайтовом "
                    + "POST, но по-прежнему прикрепляется к переходу верхнего уровня методом GET. "
                    + "Прочитайте это дважды: ссылка, по которой кликнет жертва, всё равно придёт "
                    + "полностью аутентифицированной";
        } else {
            en = "the cookie travels with every cross-site request. This is the legacy behaviour, it "
                    + "is what the specification meant by default for twenty years, and it is exactly "
                    + "what makes forgery possible";
            ru = "cookie едет с каждым кросс-сайтовым запросом. Это унаследованное поведение, именно "
                    + "оно двадцать лет было умолчанием, и именно оно делает подделку возможной";
        }
        Trace.event("DEFENCE_ENABLED",
                "The session cookie is now SameSite=" + sameSite + ": " + en
                        + ". Note who enforces this — the browser, not your code. It costs one "
                        + "attribute and it protects endpoints you have forgotten you have",
                "Cookie сессии теперь SameSite=" + sameSite + ": " + ru
                        + ". Обратите внимание, кто это применяет — браузер, а не ваш код. Стоит это "
                        + "один атрибут, а защищает даже те эндпоинты, о которых вы забыли",
                List.of("defences", "session"), state());
        return this;
    }

    /**
     * The synchronizer token pattern: the server puts an unpredictable value into every
     * page it renders and requires it back with every state-changing request.
     */
    public VisualCsrf csrfToken() {
        csrfToken = true;
        Trace.event("DEFENCE_ENABLED",
                "A synchronizer token is switched on: the server embeds an unpredictable value ("
                        + CSRF_TOKEN + ") in the pages it renders and rejects any state-changing "
                        + "request that does not send it back. It works for exactly one reason — the "
                        + "same-origin policy stops " + ATTACKER_SITE + " from reading the bank's "
                        + "HTML, so it can never learn the value. The token proves nothing about the "
                        + "user; it proves the request was built by a page that could read your HTML",
                "Включён синхронизирующий токен: сервер встраивает непредсказуемое значение ("
                        + CSRF_TOKEN + ") в отдаваемые страницы и отклоняет любой изменяющий состояние "
                        + "запрос, в котором его не вернули. Работает это ровно по одной причине — "
                        + "правило одного источника не даёт " + ATTACKER_SITE + " прочитать HTML "
                        + "банка, поэтому значение ему не узнать. Токен ничего не доказывает про "
                        + "пользователя; он доказывает, что запрос собрала страница, сумевшая "
                        + "прочитать ваш HTML",
                List.of("defences", "session"), state());
        return this;
    }

    /**
     * Checks the {@code Origin} header (falling back to {@code Referer}) against the
     * server's own origin. Cheap, stateless, and it leans on a header the browser reserves
     * for itself.
     */
    public VisualCsrf checkOrigin() {
        originCheck = true;
        Trace.event("DEFENCE_ENABLED",
                "The server now compares the Origin header with its own, falling back to Referer when "
                        + "Origin is absent. This works because Origin is a forbidden header name: "
                        + "page JavaScript cannot set it, the browser fills it in, and on a "
                        + "cross-origin request it names the page that caused the request rather than "
                        + "the one being asked. The check keeps no state, which makes it the cheap "
                        + "defence to add to an API that has no HTML pages to put a token in",
                "Сервер теперь сравнивает заголовок Origin со своим собственным, а при его отсутствии "
                        + "смотрит на Referer. Работает это потому, что Origin — запрещённое для "
                        + "скрипта имя заголовка: JavaScript страницы его не выставит, его заполняет "
                        + "браузер, и в кросс-оригинальном запросе там будет страница, вызвавшая "
                        + "запрос, а не та, к которой обращаются. Проверка не хранит состояния, "
                        + "поэтому её дёшево добавить в API, у которого нет HTML-страниц, чтобы "
                        + "положить в них токен",
                List.of("defences"), state());
        return this;
    }

    /**
     * Refuses to change state on a safe method. Worth doing on its own merits — and
     * routinely mistaken for a CSRF defence, which it is not.
     */
    public VisualCsrf postOnly() {
        postOnly = true;
        Trace.event("DEFENCE_ENABLED",
                "GET and HEAD are now read-only: a state change requires POST. This is correct HTTP "
                        + "and it closes the deliveries that need no JavaScript at all — an <img> tag, "
                        + "a link, a prefetch. Be precise about what it is not: a cross-site form can "
                        + "POST perfectly well, so 'we only accept POST' on its own is not a CSRF "
                        + "defence",
                "GET и HEAD теперь только читают: изменение состояния требует POST. Это правильный "
                        + "HTTP, и это закрывает те способы доставки, которым вообще не нужен "
                        + "JavaScript, — тег <img>, ссылку, предзагрузку. Важно не переоценить: "
                        + "кросс-сайтовая форма прекрасно умеет POST, поэтому «мы принимаем только "
                        + "POST» само по себе защитой от CSRF не является",
                List.of("defences"), state());
        return this;
    }

    /**
     * Moves the session out of the cookie and into an {@code Authorization} header the
     * application's own JavaScript attaches. This removes ambient authority — the root
     * cause rather than one of its symptoms.
     */
    public VisualCsrf bearerToken() {
        auth = "bearer";
        Trace.event("DEFENCE_ENABLED",
                "The session moves out of the cookie and into an Authorization header that the "
                        + "application's own JavaScript attaches to each call. That single change "
                        + "removes ambient authority: there is nothing for the browser to add "
                        + "automatically, so a request built by another site arrives with no identity "
                        + "at all, and CSRF stops existing rather than being blocked. It is not free "
                        + "— a token JavaScript can attach is a token JavaScript can read, so you "
                        + "have traded CSRF exposure for XSS exposure",
                "Сессия переезжает из cookie в заголовок Authorization, который к каждому вызову "
                        + "добавляет собственный JavaScript приложения. Одно это изменение убирает "
                        + "ambient-полномочия: браузеру больше нечего подставлять автоматически, "
                        + "поэтому запрос, собранный чужим сайтом, приходит вообще без личности, и "
                        + "CSRF не блокируется, а перестаёт существовать. Бесплатно это не даётся — "
                        + "токен, который может прикрепить JavaScript, это токен, который JavaScript "
                        + "может прочитать, так что вы обменяли уязвимость к CSRF на уязвимость к XSS",
                List.of("defences", "session"), state());
        return this;
    }

    /**
     * The misconfiguration that hands the browser's verdict back to the attacker: echo
     * whatever {@code Origin} asked for, with credentials allowed.
     */
    public VisualCsrf corsReflectsAnyOrigin() {
        corsReflectsOrigin = true;
        Trace.event("CORS_MISCONFIGURED",
                "The API now echoes back whatever Origin asked for, together with "
                        + "Access-Control-Allow-Credentials: true. This configuration exists in the "
                        + "wild because it makes preflight failures go away during development — and "
                        + "what it actually does is let every origin, " + ATTACKER_SITE + " included, "
                        + "send this API a scripted request with the user's credentials and read the "
                        + "answer",
                "API теперь возвращает обратно тот Origin, который спросили, вместе с "
                        + "Access-Control-Allow-Credentials: true. Такая конфигурация встречается в "
                        + "жизни, потому что она убирает ошибки preflight во время разработки, — а на "
                        + "деле она позволяет любому источнику, включая " + ATTACKER_SITE + ", "
                        + "отправить в этот API скриптовый запрос с учётными данными пользователя и "
                        + "прочитать ответ",
                List.of("defences"), state());
        return this;
    }

    // ---------------------------------------------------------------- requests

    /**
     * The legitimate flow: the user is on the bank's own page and submits its transfer
     * form. Every defence must let this through, which is the half of the exercise people
     * skip.
     */
    public void userTransfers(int transferAmount) {
        begin(SITE, "OWN_PAGE_FORM", "POST", "/transfer",
                "to=" + TRUSTED_PAYEE + "&amount=" + transferAmount,
                "<form action=\"/transfer\" method=\"post\"> … on the bank's own page",
                TRUSTED_PAYEE, transferAmount, false, true, false, false);
        Trace.event("REQUEST_BUILT",
                "The user is on " + SITE + ", fills in the bank's own transfer form and presses Send. "
                        + "This request is same-site, it was actually asked for, and it is the one "
                        + "every defence below has to keep working — a CSRF defence that blocks this "
                        + "too is not a defence, it is an outage",
                "Пользователь находится на " + SITE + ", заполняет собственную форму перевода банка и "
                        + "нажимает «Отправить». Этот запрос — same-site, его действительно просили, и "
                        + "именно он должен продолжать работать при любой защите ниже: защита от CSRF, "
                        + "которая ломает и это, — не защита, а простой",
                List.of("request"), state());
        deliver(true);
    }

    /**
     * The attack: the victim is looking at the attacker's page, which quietly makes their
     * browser send a request to the bank.
     */
    public void crossSiteAttempt(Delivery delivery, int transferAmount) {
        boolean get = "GET".equals(delivery.method);
        String path = get
                ? "/transfer?to=" + ATTACKER_PAYEE + "&amount=" + transferAmount
                : "/transfer";
        String payload = get ? null
                : (delivery == Delivery.FETCH_JSON
                        ? "{\"to\":\"" + ATTACKER_PAYEE + "\",\"amount\":" + transferAmount + "}"
                        : "to=" + ATTACKER_PAYEE + "&amount=" + transferAmount);
        begin(ATTACKER_SITE, delivery.name(), delivery.method, path, payload,
                markupFor(delivery, transferAmount), ATTACKER_PAYEE, transferAmount,
                true, false, delivery.preflighted, delivery.allowedByLax());
        Trace.event("REQUEST_BUILT",
                "The victim opens " + ATTACKER_SITE + " — a competition page, a forum post, an image "
                        + "in an email. It contains " + delivery.en + ", addressed to " + SITE
                        + ". The victim typed nothing into the bank and, in most of these deliveries, "
                        + "clicked nothing either",
                "Жертва открывает " + ATTACKER_SITE + " — страницу с розыгрышем, сообщение на форуме, "
                        + "картинку в письме. На ней есть " + delivery.ru + ", направленный на " + SITE
                        + ". В банк жертва ничего не вводила и в большинстве таких способов доставки "
                        + "ничего не нажимала",
                List.of("request"), state());
        deliver(false);
    }

    /**
     * Not CSRF — and the reason CSRF defences are no substitute for fixing XSS: a script
     * running on the bank's own origin can simply read the token off the page.
     */
    public void injectedScriptTransfer(int transferAmount) {
        begin(SITE, "INJECTED_SCRIPT", "POST", "/transfer",
                "to=" + ATTACKER_PAYEE + "&amount=" + transferAmount,
                "fetch('/transfer', { method: 'POST', body: … }) — running on " + SITE,
                ATTACKER_PAYEE, transferAmount, false, false, false, false);
        Trace.event("REQUEST_BUILT",
                "A different bug — a cross-site scripting hole on the bank itself — has put the "
                        + "attacker's JavaScript on " + SITE + ". It now builds the transfer request "
                        + "from inside your own origin, and that changes everything below: every "
                        + "same-origin check is about to agree with it",
                "Другая ошибка — межсайтовый скриптинг на самом банке — поместила JavaScript "
                        + "злоумышленника на " + SITE + ". Теперь он собирает запрос на перевод "
                        + "изнутри вашего же origin, и это меняет всё, что ниже: любая проверка на "
                        + "«тот же источник» с ним согласится",
                List.of("request"), state());
        if (csrfToken) {
            sentToken = CSRF_TOKEN;
            Trace.event("TOKEN_STOLEN",
                    "The script reads the CSRF token straight out of the page — it sits in a hidden "
                            + "input, or in a meta tag, or in a cookie the script can read, and all "
                            + "three are readable by same-origin JavaScript by design. It attaches "
                            + "the token to its own request. This is why a CSRF token is worth nothing "
                            + "once a site has XSS, and why the two have to be fixed independently",
                    "Скрипт читает CSRF-токен прямо со страницы — тот лежит в скрытом поле, или в "
                            + "meta-теге, или в cookie, доступной скрипту, и все три варианта по "
                            + "замыслу читаются JavaScript того же источника. Токен он прикрепляет к "
                            + "своему запросу. Поэтому CSRF-токен ничего не стоит, если на сайте есть "
                            + "XSS, и поэтому чинить эти две вещи нужно независимо",
                    List.of("request", "session"), state());
        }
        deliver(false);
    }

    /** Prints what the whole run added up to. */
    public void report() {
        Trace.event("CSRF_AUDIT",
                "After " + attempts + " request(s): " + reachedServer + " reached the server, "
                        + changed + " changed state, " + forged + " of those were never asked for by "
                        + "the user, and " + blocked + " were stopped. The balance went from "
                        + OPENING_BALANCE + " to " + balance + ", with " + stolen + " now in the "
                        + "attacker's account. The number that matters is the third one: those "
                        + "requests were authenticated, logged, and indistinguishable from real ones "
                        + "in every record you keep",
                "После запросов (" + attempts + "): до сервера дошло " + reachedServer + ", состояние "
                        + "изменили " + changed + ", из них пользователь не просил ни об одном из "
                        + forged + ", остановлено " + blocked + ". Баланс изменился с "
                        + OPENING_BALANCE + " на " + balance + ", у злоумышленника теперь " + stolen
                        + ". Важно третье число: эти запросы были аутентифицированы, залогированы и "
                        + "неотличимы от настоящих в любой записи, которую вы храните",
                List.of("account"), state());
    }

    // -------------------------------------------------------------- the engine

    /**
     * Walks one request from the page that built it to the server's verdict.
     *
     * @param tokenReadable whether the page that built the request could read the CSRF token
     */
    private void deliver(boolean tokenReadable) {
        attempts++;

        if (preflightRequired) {
            if (!corsReflectsOrigin) {
                blockedBy = "preflight";
                blocked++;
                stage = "settled";
                Trace.event("PREFLIGHT_BLOCKED",
                        "The browser will not send this one at all: a POST with Content-Type: "
                                + "application/json is not a shape an HTML form can produce, so it is "
                                + "not a 'simple' request and the browser asks permission first with "
                                + "an OPTIONS preflight. The bank does not list " + ATTACKER_SITE
                                + " as an allowed origin, so the real request never leaves. Notice "
                                + "the shape of this protection: it is CORS refusing to let script "
                                + "make an unusual request, not the bank refusing to act",
                        "Этот запрос браузер не отправит вовсе: POST с Content-Type: "
                                + "application/json — не та форма запроса, которую может выдать "
                                + "HTML-форма, значит он не «простой», и браузер сначала спрашивает "
                                + "разрешение предварительным запросом OPTIONS. Банк не перечисляет "
                                + ATTACKER_SITE + " среди разрешённых источников, поэтому настоящий "
                                + "запрос так и не уходит. Обратите внимание на природу этой защиты: "
                                + "это CORS не даёт скрипту сделать необычный запрос, а не банк "
                                + "отказывается действовать",
                        List.of("request", "outcome"), state());
                return;
            }
            Trace.event("PREFLIGHT_ALLOWED",
                    "The preflight comes back with Access-Control-Allow-Origin: " + ATTACKER_SITE
                            + " and Access-Control-Allow-Credentials: true, because the API echoes "
                            + "whatever it is asked. The browser was doing its job and the server told "
                            + "it to stand down, so the real request goes out — with cookies",
                    "Предварительный запрос возвращается с Access-Control-Allow-Origin: "
                            + ATTACKER_SITE + " и Access-Control-Allow-Credentials: true, потому что "
                            + "API отражает всё, о чём его просят. Браузер делал свою работу, а сервер "
                            + "велел ему отойти в сторону, поэтому настоящий запрос уходит — вместе с "
                            + "cookie",
                    List.of("request", "defences"), state());
        }

        attachCredentials();

        if (csrfToken && tokenReadable) {
            sentToken = CSRF_TOKEN;
        }

        reached = true;
        reachedServer++;
        stage = "sent";
        Trace.event("REQUEST_SENT",
                "The request arrives at " + SITE + ": " + method + " " + target
                        + (body == null ? "" : " with body " + body)
                        + ", Origin: " + origin + ", session "
                        + (credentials ? "attached" : "absent")
                        + ", CSRF token " + (sentToken == null ? "absent" : sentToken)
                        + ". Everything the server will ever know about who wanted this is in that "
                        + "one line",
                "Запрос приходит на " + SITE + ": " + method + " " + target
                        + (body == null ? "" : " с телом " + body)
                        + ", Origin: " + origin + ", сессия "
                        + (credentials ? "прикреплена" : "отсутствует")
                        + ", CSRF-токен " + (sentToken == null ? "отсутствует" : sentToken)
                        + ". Всё, что сервер когда-либо узнает о том, кто этого хотел, — в этой "
                        + "единственной строке",
                List.of("request"), state());

        if (postOnly && "GET".equals(method)) {
            reject("method",
                    "The bank refuses: this endpoint no longer changes anything on a GET. The cookie "
                            + "was attached and the session was valid, and it did not matter, because "
                            + "there was no state-changing route to reach. Keeping GET safe costs "
                            + "nothing and removes every delivery that needs neither script nor a "
                            + "click",
                    "Банк отказывает: этот эндпоинт больше ничего не меняет по GET. Cookie была "
                            + "прикреплена, сессия была настоящей, и это не помогло, потому что "
                            + "изменяющего состояние маршрута просто нет. Держать GET безопасным "
                            + "ничего не стоит и убирает все способы доставки, которым не нужны ни "
                            + "скрипт, ни клик");
            finish();
            return;
        }

        if (!credentials) {
            reject("no-credentials",
                    "The bank sees an anonymous request and answers 401. Nothing clever happened here "
                            + "— the attack was not detected, it was disarmed one step earlier, when "
                            + "the credential was not attached",
                    "Банк видит анонимный запрос и отвечает 401. Ничего хитрого здесь не произошло: "
                            + "атаку не распознали — её обезоружили шагом раньше, когда учётные данные "
                            + "не были прикреплены");
            finish();
            return;
        }

        authenticated = true;
        Trace.event("SESSION_ACCEPTED",
                "The server looks the session up and it is perfectly valid: the right user, not "
                        + "expired, every permission in place. Sit with that for a second — this is "
                        + "why CSRF is not an authentication bug, and why no amount of stronger "
                        + "login, MFA or password rotation touches it. The request really is in the "
                        + "user's session; what it is missing is the user's intent",
                "Сервер поднимает сессию, и она совершенно настоящая: тот пользователь, не истекла, "
                        + "все права на месте. Задержитесь здесь на секунду — именно поэтому CSRF не "
                        + "является ошибкой аутентификации и поэтому его не лечат ни более строгий "
                        + "вход, ни MFA, ни смена паролей. Запрос действительно идёт в сессии "
                        + "пользователя — в нём нет только намерения пользователя",
                List.of("request", "outcome"), state());

        if (originCheck && !SITE.equals(origin)) {
            Trace.event("ORIGIN_REJECTED",
                    "The Origin header says " + origin + " and this server is " + SITE
                            + ", so the request is refused. The header was set by the browser and the "
                            + "attacker's page had no way to change it — that is what makes the check "
                            + "worth anything. Two things to settle before relying on it alone: some "
                            + "requests arrive with neither Origin nor Referer, so you must decide "
                            + "what to do with those, and the check is only as good as the string "
                            + "comparison behind it",
                    "В заголовке Origin стоит " + origin + ", а этот сервер — " + SITE
                            + ", поэтому запрос отклонён. Заголовок выставил браузер, и страница "
                            + "злоумышленника никак не могла его изменить — именно поэтому проверка "
                            + "чего-то стоит. Прежде чем полагаться только на неё, надо решить две "
                            + "вещи: часть запросов приходит вообще без Origin и без Referer, и с "
                            + "ними что-то надо делать, — а сама проверка хороша ровно настолько, "
                            + "насколько аккуратно написано сравнение строк",
                    List.of("request", "defences", "outcome"), state());
            reject("origin",
                    "403: the request was authenticated and it was still refused, because who sent it "
                            + "and where it was assembled are two different questions",
                    "403: запрос был аутентифицирован и всё равно отклонён, потому что «кто отправил» "
                            + "и «где его собрали» — два разных вопроса");
            finish();
            return;
        }

        if (csrfToken && !CSRF_TOKEN.equals(sentToken)) {
            Trace.event("TOKEN_MISSING",
                    "The request carries no CSRF token. The attacker's page could not add one: "
                            + "learning " + CSRF_TOKEN + " would have meant reading the bank's HTML, "
                            + "and the same-origin policy does not let one site read another site's "
                            + "response. This is the difference between authentication and intent made "
                            + "mechanical — the session says who, the token says the request was "
                            + "assembled by a page belonging to this site",
                    "В запросе нет CSRF-токена. Страница злоумышленника не могла его добавить: чтобы "
                            + "узнать " + CSRF_TOKEN + ", нужно было бы прочитать HTML банка, а "
                            + "правило одного источника не даёт одному сайту читать ответ другого. "
                            + "Здесь механически проведена граница между аутентификацией и "
                            + "намерением: сессия говорит кто, а токен — что запрос собрала страница "
                            + "этого сайта",
                    List.of("request", "defences", "outcome"), state());
            reject("token",
                    "403: no token, no transfer. Note what the user experienced — nothing. The "
                            + "defence only ever inconveniences the page that could not read your "
                            + "HTML",
                    "403: нет токена — нет перевода. Заметьте, что почувствовал пользователь, — "
                            + "ничего. Эта защита мешает только той странице, которая не смогла "
                            + "прочитать ваш HTML");
            finish();
            return;
        }

        if (csrfToken) {
            Trace.event("TOKEN_VALIDATED",
                    "The token that came back matches the one bound to the session, so the request is "
                            + "accepted. Worth noticing that the legitimate flow was never "
                            + "inconvenienced: the bank's own page had the token because the bank put "
                            + "it there. A good CSRF defence is invisible to everyone except the "
                            + "forger",
                    "Пришедший обратно токен совпадает с тем, что привязан к сессии, поэтому запрос "
                            + "принят. Стоит заметить, что легальный сценарий ничего не потерял: у "
                            + "собственной страницы банка токен был, потому что банк его туда и "
                            + "положил. Хорошая защита от CSRF незаметна для всех, кроме "
                            + "подделывающего",
                    List.of("request", "defences"), state());
        }

        if ("GET".equals(method)) {
            Trace.event("UNSAFE_GET",
                    "Before the money moves, look at the method: this is a GET and it is about to "
                            + "change state. That is what let an <img> tag be the entire attack — no "
                            + "form, no JavaScript, no click, and it works from an email, a forum post "
                            + "or a Markdown comment. HTTP calls GET a safe method for exactly this "
                            + "reason",
                    "Прежде чем деньги уйдут, посмотрите на метод: это GET, и он сейчас изменит "
                            + "состояние. Именно поэтому целой атакой оказался тег <img> — ни формы, "
                            + "ни JavaScript, ни клика, и это работает из письма, из сообщения на "
                            + "форуме или из комментария в Markdown. HTTP называет GET безопасным "
                            + "методом ровно по этой причине",
                    List.of("request", "outcome"), state());
        }

        performed = true;
        changed++;
        balance -= amount;
        if (ATTACKER_PAYEE.equals(payee)) {
            stolen += amount;
        }
        stage = "settled";
        if (userInitiated) {
            Trace.event("ACTION_PERFORMED",
                    "The transfer goes through: " + amount + " to " + payee + ", balance now "
                            + balance + ". This is the request the user actually made, and it has to "
                            + "keep working — measure every defence by whether this line still appears",
                    "Перевод проходит: " + amount + " на " + payee + ", баланс теперь " + balance
                            + ". Это тот запрос, который пользователь действительно сделал, и он "
                            + "обязан продолжать работать — любую защиту проверяйте тем, остаётся ли "
                            + "эта строка",
                    List.of("account", "outcome"), state());
        } else {
            forged++;
            Trace.event("FORGED_ACTION",
                    "The transfer goes through: " + amount + " to " + payee + ", balance now "
                            + balance + " — and the user never asked for it. There is no anomaly to "
                            + "find afterwards: the session was real, the IP is the user's, the device "
                            + "is the user's, and the audit row says the user did it. The only party "
                            + "who knows otherwise is the person whose money left",
                    "Перевод проходит: " + amount + " на " + payee + ", баланс теперь " + balance
                            + " — и пользователь об этом не просил. Никакой аномалии потом не найти: "
                            + "сессия настоящая, IP пользовательский, устройство пользовательское, в "
                            + "журнале аудита написано, что это сделал пользователь. Единственный, кто "
                            + "знает иначе, — тот, чьи деньги ушли",
                    List.of("account", "outcome"), state());
        }
        finish();
    }

    /** Decides what identity, if any, rides along with this request. */
    private void attachCredentials() {
        if ("bearer".equals(auth)) {
            if (crossSite) {
                credentials = false;
                blockedBy = "no-credentials";
                Trace.event("NO_AMBIENT_CREDENTIALS",
                        "There is nothing for the browser to attach. The session is not a cookie any "
                                + "more, it is a value the bank's own JavaScript adds to its own "
                                + "calls, and " + ATTACKER_SITE + " cannot run the bank's JavaScript. "
                                + "The request will reach the server anonymous — the forgery did not "
                                + "fail a check, it never had an identity to forge",
                        "Браузеру нечего прикреплять. Сессия больше не cookie, а значение, которое "
                                + "собственный JavaScript банка добавляет к собственным вызовам, а "
                                + ATTACKER_SITE + " не может выполнить JavaScript банка. Запрос "
                                + "дойдёт до сервера анонимным — подделка не провалила проверку, у "
                                + "неё просто не было личности, которую можно подделать",
                        List.of("request", "session", "outcome"), state());
                return;
            }
            credentials = true;
            Trace.event("AUTH_HEADER_ATTACHED",
                    "The bank's own JavaScript attaches Authorization: Bearer " + COOKIE_VALUE
                            + " to its own call. The credential is deliberate here: it is added by "
                            + "the code that decided to make the request — which is precisely the "
                            + "property a cookie does not have",
                    "Собственный JavaScript банка добавляет к своему вызову Authorization: Bearer "
                            + COOKIE_VALUE + ". Здесь учётные данные подставлены осознанно: их "
                            + "добавляет тот код, который и решил сделать запрос, — а именно этого "
                            + "свойства у cookie нет",
                    List.of("request", "session"), state());
            return;
        }

        if (crossSite && !cookieAllowed) {
            credentials = false;
            blockedBy = "samesite";
            Trace.event("COOKIE_WITHHELD",
                    "The browser leaves the session cookie out: SameSite=" + sameSite + " does not "
                            + "cover a request of this shape, started on " + ATTACKER_SITE + ". The "
                            + "request is still sent — the attacker's page was never blocked from "
                            + "making it — but it arrives as an anonymous stranger, and the bank will "
                            + "treat it as one",
                    "Браузер не кладёт cookie сессии: SameSite=" + sameSite + " не покрывает запрос "
                            + "такого вида, начатый на " + ATTACKER_SITE + ". Запрос всё равно "
                            + "отправляется — странице злоумышленника никто не запрещал его делать, — "
                            + "но приходит он анонимным незнакомцем, и банк так к нему и отнесётся",
                    List.of("request", "session", "outcome"), state());
            return;
        }

        credentials = true;
        Trace.event("COOKIE_ATTACHED",
                "The browser attaches " + COOKIE_NAME + "=" + COOKIE_VALUE + " because the request is "
                        + "addressed to " + SITE + ". It does not ask who wanted this request or "
                        + "which page built it — the cookie belongs to the destination, and this "
                        + "request is going to the destination. The whole vulnerability lives in that "
                        + "one automatic step nobody wrote",
                "Браузер прикрепляет " + COOKIE_NAME + "=" + COOKIE_VALUE + ", потому что запрос "
                        + "адресован " + SITE + ". Он не спрашивает, кто этого запроса хотел и какая "
                        + "страница его собрала: cookie принадлежит адресу назначения, а запрос идёт "
                        + "по этому адресу. Вся уязвимость и живёт в этом единственном автоматическом "
                        + "шаге, которого никто не писал",
                List.of("request", "session"), state());

        if (crossSite && "Lax".equals(sameSite)) {
            Trace.event("SAMESITE_GAP",
                    "And SameSite=Lax permitted it. Lax blocks cross-site POSTs and subresource "
                            + "loads, but it deliberately allows top-level GET navigations, because "
                            + "blocking those would log people out of every link arriving from email "
                            + "or search. So the hole Lax leaves is precisely: any GET that changes "
                            + "something. Which is the other reason GET has to stay safe",
                    "И SameSite=Lax это разрешил. Lax блокирует кросс-сайтовые POST и загрузку "
                            + "подресурсов, но намеренно пропускает переходы верхнего уровня методом "
                            + "GET: иначе пользователи оказывались бы разлогинены при переходе по "
                            + "любой ссылке из почты или поиска. Значит, дыра, которую оставляет Lax, "
                            + "— это ровно любой GET, который что-то меняет. И это вторая причина, по "
                            + "которой GET обязан оставаться безопасным",
                    List.of("request", "defences"), state());
        }
    }

    /** What the page that built the request can learn afterwards. */
    private void finish() {
        if (!crossSite) {
            responseReadable = true;
            return;
        }
        responseReadable = false;
        Trace.event("RESPONSE_UNREADABLE",
                "The same-origin policy now does its job perfectly: " + ATTACKER_SITE + " cannot read "
                        + "a single byte of the bank's response, so it never learns the balance and "
                        + "never even finds out whether the request worked. That is worth saying "
                        + "plainly, because it is the part people miss — CSRF is a write attack. The "
                        + "attacker does not need to read anything; the side effect has already "
                        + "happened",
                "Правило одного источника здесь отрабатывает безупречно: " + ATTACKER_SITE + " не "
                        + "может прочитать ни байта ответа банка, поэтому не узнаёт ни баланса, ни "
                        + "даже того, сработал ли запрос. Это стоит проговорить прямо, потому что "
                        + "именно это чаще всего упускают: CSRF — атака на запись. Злоумышленнику не "
                        + "нужно ничего читать, побочный эффект уже случился",
                List.of("outcome"), state());
    }

    private void reject(String reason, String en, String ru) {
        if (blockedBy == null) {
            blockedBy = reason;
        }
        blocked++;
        stage = "settled";
        Trace.event("REQUEST_REJECTED", en, ru, List.of("request", "outcome"), state());
    }

    // ------------------------------------------------------------------ state

    private void begin(String requestOrigin, String kind, String requestMethod, String path,
                       String payload, String pageMarkup, String requestPayee, int transferAmount,
                       boolean fromAnotherSite, boolean asked, boolean preflighted,
                       boolean allowedByLax) {
        this.started = true;
        this.origin = requestOrigin;
        this.deliveryKind = kind;
        this.method = requestMethod;
        this.target = path;
        this.body = payload;
        this.markup = pageMarkup;
        this.payee = requestPayee;
        this.amount = transferAmount;
        this.crossSite = fromAnotherSite;
        this.userInitiated = asked;
        this.preflightRequired = preflighted;
        this.cookieAllowed = cookieRidesAlong(allowedByLax);
        this.credentials = false;
        this.sentToken = null;
        this.reached = false;
        this.stage = "built";
        this.authenticated = false;
        this.performed = false;
        this.blockedBy = null;
        this.responseReadable = false;
    }

    /** Does the current SameSite policy let the cookie ride along on THIS request? */
    private boolean cookieRidesAlong(boolean allowedByLax) {
        return switch (sameSite) {
            case "Strict" -> false;
            case "Lax" -> allowedByLax;
            default -> true;
        };
    }

    private static String normalizePolicy(String policy) {
        String probe = policy == null ? "" : policy.trim().toLowerCase(Locale.ROOT);
        if (probe.startsWith("str")) {
            return "Strict";
        }
        if (probe.startsWith("lax")) {
            return "Lax";
        }
        return "None";
    }

    private static String markupFor(Delivery delivery, int transferAmount) {
        String query = "/transfer?to=" + ATTACKER_PAYEE + "&amount=" + transferAmount;
        return switch (delivery) {
            case IMAGE_TAG -> "<img src=\"" + SITE + query + "\" width=\"1\" height=\"1\">";
            case LINK_CLICK -> "<a href=\"" + SITE + query + "\">Claim your prize</a>";
            case AUTO_FORM -> "<form action=\"" + SITE + "/transfer\" method=\"post\">"
                    + "<input name=\"to\" value=\"" + ATTACKER_PAYEE + "\">"
                    + "<input name=\"amount\" value=\"" + transferAmount + "\"></form>"
                    + "<script>document.forms[0].submit()</script>";
            case FETCH_JSON -> "fetch(\"" + SITE + "/transfer\", { method: \"POST\", "
                    + "credentials: \"include\", headers: { \"Content-Type\": \"application/json\" } })";
        };
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        Map<String, Object> defences = new LinkedHashMap<>();
        defences.put("sameSite", sameSite);
        defences.put("csrfToken", csrfToken);
        defences.put("originCheck", originCheck);
        defences.put("postOnly", postOnly);
        defences.put("auth", auth);
        defences.put("corsReflectsOrigin", corsReflectsOrigin);
        s.put("defences", defences);

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("name", COOKIE_NAME);
        session.put("value", COOKIE_VALUE);
        session.put("sameSite", sameSite);
        session.put("ambient", "cookie".equals(auth));
        session.put("token", csrfToken ? CSRF_TOKEN : null);
        s.put("session", session);

        if (started) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("origin", origin);
            request.put("delivery", deliveryKind);
            request.put("method", method);
            request.put("target", target);
            request.put("body", body);
            request.put("markup", markup);
            request.put("crossSite", crossSite);
            request.put("userInitiated", userInitiated);
            request.put("credentials", credentials);
            request.put("token", sentToken);
            request.put("reached", reached);
            s.put("request", request);

            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("stage", stage);
            outcome.put("authenticated", authenticated);
            outcome.put("performed", performed);
            outcome.put("blockedBy", blockedBy);
            outcome.put("responseReadable", responseReadable);
            s.put("outcome", outcome);
        } else {
            s.put("request", null);
            s.put("outcome", null);
        }

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("balance", balance);
        account.put("stolen", stolen);
        s.put("account", account);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("attempts", attempts);
        stats.put("reachedServer", reachedServer);
        stats.put("changed", changed);
        stats.put("forged", forged);
        stats.put("blocked", blocked);
        s.put("stats", stats);
        return s;
    }
}
