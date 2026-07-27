package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A <em>teaching model</em> of the error contract of ONE API.
 *
 * <p>An API answers failures the same way it answers successes: with a documented
 * shape. This model makes that shape explicit and then runs failing requests
 * through it, so the two halves of the question are visible at once:
 * <ul>
 *   <li>{@link #define(String, String, int, boolean)} writes one row of the error
 *       <em>catalog</em> — a stable machine-readable code, the exception that
 *       raises it, the HTTP status that carries it, and whether repeating the
 *       request can help. The row is reviewed as it is declared: a code that is
 *       really a sentence, a code nobody can attribute to a service, an error
 *       answered with a success status, and retry advice that contradicts the
 *       status are each flagged;</li>
 *   <li>{@link #callFails(String, String, String)} and its variants send a
 *       request through the five stages a failure actually has — call, throw,
 *       map, respond, log — so it is visible that a client gets a code, an
 *       operator gets a log line, and both are joined by one {@code traceId}.</li>
 * </ul>
 *
 * <p>{@link #swallow(String, String, String, String)} and
 * {@link #leakInternals(String, String, String, String)} model the two failures
 * of the contract itself: an error hidden behind {@code 200 OK}, and internals
 * shipped to the caller.
 *
 * <p>The checks are deliberately mechanical — no natural-language judgement, only
 * rules a reviewer could apply to a table. Every step emits a bilingual
 * {@link Trace} event; the class is intentionally dependency-free.
 */
public class VisualErrorContract {

    /** The five stages a failing request goes through, in order. */
    private static final String[] STAGE_NAMES = {"call", "throw", "map", "respond", "log"};

    /** Statuses where repeating the very same request is the correct behaviour. */
    private static boolean retryHelps(int status) {
        return status == 408 || status == 425 || status == 429
                || status == 502 || status == 503 || status == 504;
    }

    /** One stage of the request currently going through the API. */
    private static final class Stage {
        final String name;
        String state;
        String detail;

        Stage(String name) {
            this.name = name;
            this.state = "pending";
            this.detail = "";
        }
    }

    /** One row of the error catalog: the promise made about one kind of failure. */
    private static final class ErrorCode {
        final String code;
        final String exception;
        final int status;
        final boolean retryable;
        final List<String> issues = new ArrayList<>();

        ErrorCode(String code, String exception, int status, boolean retryable) {
            this.code = code;
            this.exception = exception;
            this.status = status;
            this.retryable = retryable;
        }

        void flag(String issue) {
            if (!issues.contains(issue)) {
                issues.add(issue);
            }
        }
    }

    private final String api;
    private final String namespace;
    /** code -> row, in declaration order. */
    private final Map<String, ErrorCode> codes = new LinkedHashMap<>();
    /** Exception types that reached the catch-all because nothing mapped them. */
    private final List<String> unmapped = new ArrayList<>();

    private int requests;

    private String method;
    private String path;
    private String status;
    private String currentCode;
    private List<Stage> stages;
    private List<String[]> body;
    private List<String> leaked;
    private String logLevel;
    private String logLine;
    private String traceId;

    private VisualErrorContract(String api, String namespace) {
        this.api = api;
        this.namespace = namespace;
    }

    /** An API with an empty error catalog: every failure would answer the same way. */
    public static VisualErrorContract forApi(String api, String namespace) {
        VisualErrorContract contract = new VisualErrorContract(api, namespace);
        Trace.event("CONTRACT_READY",
                api + " has no error catalog yet, so every failure it can have is currently the same answer: "
                        + "whatever the framework produces. An error contract is two decisions written down per "
                        + "failure — which HTTP status carries it, so machines can route and retry, and which "
                        + "stable code names it, so a client can branch on the exact reason. The status is the "
                        + "class; the code is the case",
                "У " + api + " ещё нет каталога ошибок, поэтому все возможные сбои сейчас — один и тот же "
                        + "ответ: то, что сгенерирует фреймворк. Контракт ошибок — это два записанных решения "
                        + "на каждый сбой: какой HTTP-статус его несёт, чтобы машины могли маршрутизировать и "
                        + "повторять, и какой стабильный код его называет, чтобы клиент мог ветвиться по "
                        + "точной причине. Статус — это класс, код — это случай",
                List.of(), contract.state());
        return contract;
    }

    /**
     * Declares one error code: the identifier clients branch on, the exception
     * that raises it, the status that carries it, and whether a retry can help.
     */
    public void define(String code, String exception, int status, boolean retryable) {
        clearRequest();
        boolean replaced = codes.containsKey(code);
        ErrorCode entry = new ErrorCode(code, exception, status, retryable);
        // put() on an existing key keeps the declaration order, so a fix stays in place.
        codes.put(code, entry);

        Trace.event("CODE_DEFINED",
                (replaced ? "Re-declared: " : "Catalog row: ") + exception + " -> " + statusLine(status)
                        + " with code '" + code + "'"
                        + (retryable ? ", declared retryable" : ", declared not retryable")
                        + ". Two audiences are being served by one row: the status is what a gateway, a cache "
                        + "and a retry policy read without parsing anything, and the code is what the client's "
                        + "own switch reads. Neither replaces the other — 409 alone does not say WHICH "
                        + "conflict, and a code alone leaves every proxy in the path guessing",
                (replaced ? "Переобъявлено: " : "Строка каталога: ") + exception + " -> " + statusLine(status)
                        + " с кодом «" + code + "»"
                        + (retryable ? ", объявлен повторяемым" : ", объявлен неповторяемым")
                        + ". Одна строка обслуживает две аудитории: статус читают шлюз, кэш и политика "
                        + "повторов, ничего не разбирая, а код читает switch в клиенте. Одно не заменяет "
                        + "другое: 409 сам по себе не говорит, КАКОЙ это конфликт, а код сам по себе "
                        + "оставляет гадать все прокси по пути",
                highlight(code), state());

        checkShape(entry);
        checkStatus(entry);
        checkRetry(entry);
    }

    /** Sends a request that succeeds — the baseline the error path is compared to. */
    public void call(String method, String path) {
        begin(method, path);
        stages.get(0).state = "done";
        stages.get(0).detail = "handler returns normally";
        stages.get(1).state = "skipped";
        stages.get(2).state = "skipped";
        stages.get(3).state = "done";
        stages.get(3).detail = "EmployeeView";
        stages.get(4).state = "skipped";
        status = statusLine(200);

        Trace.event("REQUEST_OK",
                method + " " + path + " -> " + status + ". Nothing here needs an error contract, which is "
                        + "exactly why the contract has to be designed rather than discovered: the shape of "
                        + "the happy answer gets reviewed by everyone, and the shape of the unhappy one is "
                        + "usually first seen in production by a client that cannot parse it",
                method + " " + path + " -> " + status + ". Здесь контракт ошибок не нужен — и именно поэтому "
                        + "его надо проектировать, а не обнаруживать: форму счастливого ответа рассматривают "
                        + "все, а форму несчастливого обычно впервые видят в проде — глазами клиента, который "
                        + "не может её разобрать",
                List.of("stage:respond"), state());
    }

    /**
     * Sends a request whose service throws. If the exception is in the catalog it
     * becomes that code and status; if it is not, it reaches the catch-all.
     */
    public void callFails(String method, String path, String exception) {
        begin(method, path);
        thrown(exception);

        ErrorCode entry = byException(exception);
        Stage map = stages.get(2);
        if (entry == null) {
            map.state = "failed";
            map.detail = "no @ExceptionHandler -> catch-all";
            currentCode = namespace + ".internal_error";
            status = statusLine(500);
            if (!unmapped.contains(exception)) {
                unmapped.add(exception);
            }
            Trace.event("UNMAPPED_EXCEPTION",
                    "Nothing in the advice mentions " + exception + ", so it falls through to the catch-all "
                            + "and becomes " + status + " with the generic code '" + currentCode + "'. That is "
                            + "the correct default — an unrecognised failure IS a server error until someone "
                            + "decides otherwise — but it is also the failure mode to watch: every unmapped "
                            + "exception is a case the client cannot handle specifically, and a 5xx that pages "
                            + "somebody for what may well be a client mistake",
                    "В advice нет ни слова про " + exception + ", поэтому исключение проваливается в общий "
                            + "перехватчик и превращается в " + status + " с обобщённым кодом «" + currentCode
                            + "». Это правильное поведение по умолчанию — нераспознанный сбой ЯВЛЯЕТСЯ "
                            + "серверной ошибкой, пока не решено иначе, — но за этим режимом надо следить: "
                            + "каждое неотображённое исключение — это случай, который клиент не может "
                            + "обработать точно, и 5xx, поднимающий по тревоге людей из-за того, что вполне "
                            + "может быть ошибкой клиента",
                    List.of("stage:map"), state());
        } else {
            map.state = "done";
            map.detail = entry.code + " -> " + statusLine(entry.status);
            currentCode = entry.code;
            status = statusLine(entry.status);
            Trace.event("EXCEPTION_MAPPED",
                    "One @RestControllerAdvice turns " + exception + " into " + status + " and code '"
                            + entry.code + "'. The mapping lives in exactly one class, so the whole API agrees "
                            + "what 'not found' looks like. A try/catch in the handler would put that decision "
                            + "in every method, and the domain code stays free of HTTP: the service throws a "
                            + "word from its own vocabulary and never imports a status",
                    "Один @RestControllerAdvice превращает " + exception + " в " + status + " и код «"
                            + entry.code + "». Отображение живёт ровно в одном классе, поэтому весь API "
                            + "одинаково понимает, как выглядит «не найдено». try/catch в обработчике "
                            + "разложил бы это решение по всем методам, а доменный код остаётся свободным от "
                            + "HTTP: сервис бросает слово из собственного словаря и не импортирует статусы",
                    highlight(entry.code, "map"), state());
        }

        respond(entry == null ? false : entry.retryable, List.of());
        logIt(exception);
    }

    /**
     * Sends a request whose body breaks its constraints — the error that has to
     * name the offending fields. {@code fieldRules} are "field: rule" pairs.
     */
    public void callInvalid(String method, String path, String... fieldRules) {
        begin(method, path);
        String exception = "MethodArgumentNotValidException";
        thrown(exception);

        ErrorCode entry = byException(exception);
        String code = entry == null ? namespace + ".validation_failed" : entry.code;
        int code400 = entry == null ? 400 : entry.status;
        Stage map = stages.get(2);
        map.state = "done";
        map.detail = code + " -> " + statusLine(code400);
        currentCode = code;
        status = statusLine(code400);

        List<String> details = List.of(fieldRules);
        Trace.event("VALIDATION_REJECTED",
                "The body breaks " + details.size() + " constraint(s), so the answer is " + status + " with "
                        + "code '" + code + "' AND a per-field list: " + String.join("; ", details) + ". This "
                        + "is the case where one code is not enough — the client needs to paint the right "
                        + "input red, and it cannot do that from a status line. Field errors go in a "
                        + "structured array, never in a prose sentence a client would have to parse",
                "Тело нарушает " + details.size() + " ограничение(й), поэтому ответ — " + status + " с кодом «"
                        + code + "» И списком по полям: " + String.join("; ", details) + ". Это тот случай, "
                        + "когда одного кода мало: клиенту нужно подсветить красным правильное поле, а из "
                        + "строки статуса он этого не узнает. Ошибки полей едут структурированным массивом, "
                        + "а не фразой, которую клиенту пришлось бы разбирать",
                highlight(code, "map"), state());

        respond(false, details);
        logIt(exception);
    }

    /**
     * Sends a failing request whose answer is built from the raw exception —
     * the shape that ships internals to whoever asked.
     */
    public void leakInternals(String method, String path, String exception, String rawMessage) {
        begin(method, path);
        thrown(exception);

        ErrorCode entry = byException(exception);
        Stage map = stages.get(2);
        map.state = "failed";
        map.detail = "default error attributes";
        currentCode = entry == null ? namespace + ".internal_error" : entry.code;
        status = statusLine(entry == null ? 500 : entry.status);

        Stage respond = stages.get(3);
        respond.state = "failed";
        respond.detail = "raw exception in the body";
        body = new ArrayList<>();
        body.add(new String[]{"status", String.valueOf(entry == null ? 500 : entry.status)});
        body.add(new String[]{"message", rawMessage});
        body.add(new String[]{"exception", "org.springframework.dao." + exception});
        body.add(new String[]{"trace", "at com.acme.payroll.EmployeeService.create(EmployeeService.java:73)"});
        leaked = List.of("message", "exception", "trace");

        Trace.event("INTERNALS_LEAKED",
                "The answer is whatever the exception happened to say: " + rawMessage + " — plus the class "
                        + "name and a stack frame. Three things are wrong at once. It is a free map of the "
                        + "inside for anyone probing the API: table names, constraint names, framework, "
                        + "package layout. It is unusable as a contract, because the next library upgrade "
                        + "rewords it and every client matching on the text breaks. And it still does not "
                        + "carry a code the client can branch on. What belongs in the response is the code and "
                        + "the traceId; the stack belongs in the log",
                "Ответ — это то, что случайно сказало исключение: " + rawMessage + " — плюс имя класса и кадр "
                        + "стека. Здесь неверно сразу три вещи. Это бесплатная карта внутренностей для любого, "
                        + "кто прощупывает API: имена таблиц, имена ограничений, фреймворк, раскладка пакетов. "
                        + "Это непригодно как контракт, потому что следующее обновление библиотеки перепишет "
                        + "формулировку и все клиенты, сопоставляющие текст, сломаются. И кода, по которому "
                        + "клиент мог бы ветвиться, здесь всё равно нет. В ответе место коду и traceId, "
                        + "а стеку — в логе",
                List.of("stage:respond", "field:message", "field:exception", "field:trace"), state());

        logIt(exception);
    }

    /**
     * Sends a failing request the controller catches itself and reports as a
     * success — the error nothing downstream can see.
     */
    public void swallow(String method, String path, String exception, String rawMessage) {
        begin(method, path);
        thrown(exception);

        Stage map = stages.get(2);
        map.state = "failed";
        map.detail = "try/catch inside the handler";
        currentCode = null;
        status = statusLine(200);

        Stage respond = stages.get(3);
        respond.state = "failed";
        respond.detail = "200 with an error inside";
        body = new ArrayList<>();
        body.add(new String[]{"success", "false"});
        body.add(new String[]{"error", rawMessage});
        leaked = List.of();

        Stage log = stages.get(4);
        log.state = "skipped";
        log.detail = "nothing logged";
        logLevel = null;
        logLine = null;

        Trace.event("ERROR_SWALLOWED",
                method + " " + path + " catches " + exception + " and answers " + status
                        + " with {\"success\": false}. Everything built to notice failures now believes this "
                        + "request worked: the client's error branch never runs, the gateway does not retry, "
                        + "the circuit breaker stays closed, the dashboard shows 100% success and no alert "
                        + "fires. An error smuggled through as a success is strictly worse than a 500 — a 500 "
                        + "at least tells the truth to everyone at once",
                method + " " + path + " перехватывает " + exception + " и отвечает " + status
                        + " с {\"success\": false}. Всё, что построено замечать сбои, теперь считает, что "
                        + "запрос удался: ветка ошибки в клиенте не выполняется, шлюз не повторяет, "
                        + "предохранитель не размыкается, на дашборде 100% успеха, тревога не срабатывает. "
                        + "Ошибка, провезённая контрабандой как успех, строго хуже 500: 500 хотя бы говорит "
                        + "правду сразу всем",
                List.of("stage:respond", "field:success"), state());
    }

    /** Reviews the catalog as a whole: what a client can rely on and what is still open. */
    public void review() {
        clearRequest();
        int issues = issueCount();
        int statuses = distinctStatuses();
        Trace.event("CONTRACT_REVIEW",
                api + ": " + codes.size() + " code(s) over " + statuses + " status(es), " + issues
                        + " finding(s)" + (issues == 0 ? "" : " — " + String.join(", ", flagged()))
                        + (unmapped.isEmpty() ? "" : ", " + unmapped.size() + " exception(s) still unmapped: "
                                + String.join(", ", unmapped))
                        + ". Read it as a client would: this table is the only thing they can write code "
                        + "against. A code that is in it can be handled; a code that is not is a 500 and a "
                        + "support ticket. That is why the catalog is published with the API, versioned with "
                        + "it, and only ever grows — removing or renaming a code breaks callers exactly as "
                        + "hard as removing an endpoint",
                api + ": кодов — " + codes.size() + ", статусов — " + statuses + ", замечаний — " + issues
                        + (issues == 0 ? "" : ": " + String.join(", ", flagged()))
                        + (unmapped.isEmpty() ? "" : ", неотображённых исключений — " + unmapped.size() + ": "
                                + String.join(", ", unmapped))
                        + ". Прочитайте это глазами клиента: только эта таблица — то, под что он может "
                        + "написать код. Код, который в ней есть, можно обработать; кода, которого нет, — это "
                        + "500 и обращение в поддержку. Поэтому каталог публикуется вместе с API, версионируется "
                        + "вместе с ним и только растёт: удалить или переименовать код ломает вызывающих ровно "
                        + "так же сильно, как удалить эндпоинт",
                List.of(), state());
    }

    // ---------------------------------------------------------------- checks

    /** Flags a code that cannot survive as an identifier. */
    private void checkShape(ErrorCode entry) {
        boolean prose = entry.code.indexOf(' ') >= 0 || !entry.code.equals(entry.code.toLowerCase(Locale.ROOT));
        if (prose) {
            entry.flag("prose-code");
            Trace.event("CODE_UNSTABLE",
                    "'" + entry.code + "' is a sentence, not an identifier. The moment someone improves the "
                            + "wording, translates it, or fixes a typo, every client matching on that string "
                            + "stops recognising the error — and nobody will notice until support does. A code "
                            + "is lowercase, has no spaces, and never changes; the human sentence goes in a "
                            + "separate 'detail' field where it is free to change every release",
                    "«" + entry.code + "» — это фраза, а не идентификатор. Как только кто-нибудь улучшит "
                            + "формулировку, переведёт её или исправит опечатку, все клиенты, сравнивающие "
                            + "эту строку, перестанут узнавать ошибку — и никто не заметит этого раньше "
                            + "поддержки. Код пишется строчными буквами, без пробелов, и не меняется никогда; "
                            + "человеческая фраза живёт в отдельном поле 'detail', где ей можно меняться "
                            + "хоть каждый релиз",
                    highlight(entry.code), state());
        }
        if (entry.code.indexOf('.') < 0) {
            entry.flag("unnamespaced-code");
            Trace.event("CODE_UNSTABLE",
                    "'" + entry.code + "' has no namespace. Inside one service that reads fine; inside a "
                            + "client that calls six services through a gateway, three of them answer "
                            + "'not_found' and mean three different things, and the aggregated log cannot say "
                            + "which one. Prefixing with the owning domain — '" + namespace + "."
                            + entry.code + "' — makes the code globally unique and self-documenting, at the "
                            + "cost of nothing",
                    "У «" + entry.code + "» нет пространства имён. Внутри одного сервиса это читается "
                            + "нормально; внутри клиента, который через шлюз ходит в шесть сервисов, трое из "
                            + "них отвечают «not_found» и имеют в виду три разные вещи, а в сводном логе не "
                            + "разобрать, какая именно. Префикс владеющего домена — «" + namespace + "."
                            + entry.code + "» — делает код глобально уникальным и самодокументируемым, "
                            + "и не стоит ничего",
                    highlight(entry.code), state());
        }
    }

    /** Flags an error announced with a status that says nothing failed. */
    private void checkStatus(ErrorCode entry) {
        if (entry.status >= 400) {
            return;
        }
        entry.flag("success-status");
        Trace.event("STATUS_MISUSED",
                "'" + entry.code + "' is a failure carried by " + statusLine(entry.status) + ". Only your own "
                        + "client will ever read the body; everything between you and it reads the status "
                        + "line. A 2xx means the proxy caches the failure, the retry policy does not retry, "
                        + "the circuit breaker counts it as healthy, and the error-rate graph stays flat "
                        + "through an outage. Pick the code that classifies it: 400 for a malformed request, "
                        + "422 when the syntax is fine but the content is not, 404 for a thing that is not "
                        + "there, 409 for a conflict with the current state",
                "«" + entry.code + "» — это сбой, который несёт " + statusLine(entry.status) + ". Тело "
                        + "прочитает только ваш собственный клиент; всё, что между вами и им, читает строку "
                        + "статуса. 2xx означает, что прокси закэширует сбой, политика повторов не повторит, "
                        + "предохранитель посчитает вызов здоровым, а график ошибок останется плоским на "
                        + "протяжении всей аварии. Выберите код, который классифицирует ошибку: 400 — "
                        + "некорректный запрос, 422 — синтаксис верен, а содержимое нет, 404 — вещи нет, "
                        + "409 — конфликт с текущим состоянием",
                highlight(entry.code), state());
    }

    /** Flags retry advice that contradicts the status it travels with. */
    private void checkRetry(ErrorCode entry) {
        boolean helps = retryHelps(entry.status);
        if (entry.retryable == helps) {
            return;
        }
        entry.flag("retry-mismatch");
        Trace.event("RETRY_ADVICE_WRONG",
                entry.retryable
                        ? "'" + entry.code + "' answers " + statusLine(entry.status) + " and calls itself "
                            + "retryable. Nothing about sending the identical request again changes a "
                            + "rejection that was about the request itself — the client just burns its budget "
                            + "and yours. Retryable belongs to the statuses where the failure is about "
                            + "timing and not content: 408, 425, 429, 502, 503, 504"
                        : "'" + entry.code + "' answers " + statusLine(entry.status) + " and calls itself "
                            + "non-retryable, which is throwing away the one thing this status is for. It "
                            + "means 'not now' — the correct client behaviour is to back off and try again, "
                            + "and a Retry-After header turns that from a guess into an instruction",
                entry.retryable
                        ? "«" + entry.code + "» отвечает " + statusLine(entry.status) + " и называет себя "
                            + "повторяемым. Повторная отправка того же самого запроса ничего не меняет в "
                            + "отказе, который был про сам запрос: клиент просто сожжёт свой бюджет и ваш. "
                            + "Повторяемость относится к статусам, где сбой про время, а не про содержимое: "
                            + "408, 425, 429, 502, 503, 504"
                        : "«" + entry.code + "» отвечает " + statusLine(entry.status) + " и называет себя "
                            + "неповторяемым — это отказ от единственного, ради чего этот статус существует. "
                            + "Он означает «не сейчас»: правильное поведение клиента — отступить и "
                            + "попробовать снова, а заголовок Retry-After превращает догадку в инструкцию",
                highlight(entry.code), state());
    }

    // --------------------------------------------------------------- request

    /** Starts a request: resets the stage row and gives it a correlation id. */
    private void begin(String httpMethod, String target) {
        this.method = httpMethod.toUpperCase(Locale.ROOT);
        this.path = target;
        this.status = "pending";
        this.currentCode = null;
        this.stages = freshStages();
        this.body = new ArrayList<>();
        this.leaked = new ArrayList<>();
        this.logLevel = null;
        this.logLine = null;
        this.traceId = traceId(this.method + " " + target + " #" + (++requests));
    }

    /** Runs the "the service threw" stage of the current request. */
    private void thrown(String exception) {
        Stage call = stages.get(0);
        call.state = "done";
        call.detail = "service call";
        Stage fail = stages.get(1);
        fail.state = "failed";
        fail.detail = exception;

        Trace.event("EXCEPTION_THROWN",
                "The service throws " + exception + " — a word from the domain's own vocabulary, not an HTTP "
                        + "code. That separation is what lets the same rule be enforced from a scheduler or a "
                        + "message consumer, where there is no response to put a status in. Turning it into "
                        + "HTTP is the job of the edge, and it happens next",
                "Сервис бросает " + exception + " — слово из собственного словаря домена, а не HTTP-код. "
                        + "Именно это разделение позволяет применять то же правило из планировщика или "
                        + "консьюмера сообщений, где нет ответа, куда положить статус. Перевод в HTTP — "
                        + "работа границы, и он произойдёт следующим шагом",
                List.of("stage:throw"), state());
    }

    /** Builds the response body every failure of this API shares. */
    private void respond(boolean retryable, List<String> details) {
        Stage respond = stages.get(3);
        respond.state = "done";
        respond.detail = "problem+json";

        body = new ArrayList<>();
        body.add(new String[]{"type", "/errors/" + currentCode});
        body.add(new String[]{"status", status.substring(0, 3)});
        body.add(new String[]{"code", currentCode});
        body.add(new String[]{"traceId", traceId});
        body.add(new String[]{"retryable", String.valueOf(retryable)});
        body.add(new String[]{"instance", path});
        for (int i = 0; i < details.size(); i++) {
            body.add(new String[]{"errors[" + i + "]", details.get(i)});
        }

        Trace.event("ERROR_RESPONSE",
                method + " " + path + " -> " + status + ", one body shape for every failure this API has: "
                        + "code '" + currentCode + "' to branch on, type as the link to its documentation, "
                        + "instance saying what was asked, traceId to quote to support, retryable="
                        + retryable + " as the instruction"
                        + (details.isEmpty() ? "" : ", plus the per-field list")
                        + ". Content-Type is application/problem+json, so a client can tell an error body "
                        + "from a resource body without guessing. One shape is the point: a client writes the "
                        + "parsing once and it works on every endpoint, including ones added after it shipped",
                method + " " + path + " -> " + status + ", одна форма тела для всех сбоев этого API: код «"
                        + currentCode + "», по которому ветвятся, type — ссылка на его документацию, instance "
                        + "говорит, что запрашивали, traceId называют поддержке, retryable=" + retryable
                        + " — инструкция"
                        + (details.isEmpty() ? "" : ", плюс список по полям")
                        + ". Content-Type — application/problem+json, поэтому клиент отличает тело ошибки от "
                        + "тела ресурса, не гадая. Смысл в единственности формы: клиент пишет разбор один раз, "
                        + "и он работает на всех эндпоинтах, включая добавленные после его релиза",
                List.of("stage:respond", "field:code", "field:traceId"), state());
    }

    /** Runs the logging stage: who gets woken up, and with what. */
    private void logIt(String exception) {
        boolean server = status.startsWith("5");
        logLevel = server ? "ERROR" : "DEBUG";
        logLine = "traceId=" + traceId + " " + method + " " + path + " -> " + status + " "
                + (currentCode == null ? "-" : currentCode) + " (" + exception + ")"
                + (server ? " + stack trace" : "");

        Stage log = stages.get(4);
        log.state = "done";
        log.detail = logLevel;

        Trace.event("ERROR_LOGGED",
                server
                        ? "A 5xx is your bug until proven otherwise, so it is logged at ERROR with the full "
                            + "stack and it is what the alert watches. The traceId in the log line is the same "
                            + "one the client was given: support pastes it in, and the exact request appears — "
                            + "without it, 'it failed around three o'clock' is the whole investigation"
                        : "A 4xx is the client's mistake, not an incident, so it is logged at DEBUG with no "
                            + "stack. Logging every rejected form at ERROR is how an alert becomes noise "
                            + "nobody reads. The traceId still goes in, and it is the same one the client "
                            + "was given: that pair is what turns a support ticket into one grep",
                server
                        ? "5xx — это ваша ошибка, пока не доказано обратное, поэтому она пишется на уровне "
                            + "ERROR с полным стеком, и именно за ней следит алерт. traceId в строке лога — "
                            + "тот же, что получил клиент: поддержка вставляет его и находит конкретный "
                            + "запрос; без него всё расследование — это «сломалось где-то около трёх»"
                        : "4xx — ошибка клиента, а не инцидент, поэтому она пишется на уровне DEBUG и без "
                            + "стека. Писать каждую отклонённую форму на уровне ERROR — верный способ "
                            + "превратить алерт в шум, который никто не читает. traceId всё равно попадает в "
                            + "лог, и это тот же traceId, что отдали клиенту: эта пара превращает обращение "
                            + "в поддержку в один grep",
                List.of("stage:log", "field:traceId"), state());
    }

    // --------------------------------------------------------------- helpers

    private ErrorCode byException(String exception) {
        for (ErrorCode entry : codes.values()) {
            if (entry.exception.equals(exception)) {
                return entry;
            }
        }
        return null;
    }

    /** A stable, run-independent correlation id, so replays match. */
    private static String traceId(String seed) {
        long h = 1125899906842597L;
        for (int i = 0; i < seed.length(); i++) {
            h = 31 * h + seed.charAt(i);
        }
        return String.format("%012x", h & 0xFFFFFFFFFFFFL);
    }

    private static String statusLine(int code) {
        return switch (code) {
            case 200 -> "200 OK";
            case 201 -> "201 Created";
            case 202 -> "202 Accepted";
            case 204 -> "204 No Content";
            case 400 -> "400 Bad Request";
            case 401 -> "401 Unauthorized";
            case 403 -> "403 Forbidden";
            case 404 -> "404 Not Found";
            case 408 -> "408 Request Timeout";
            case 409 -> "409 Conflict";
            case 422 -> "422 Unprocessable Entity";
            case 429 -> "429 Too Many Requests";
            case 500 -> "500 Internal Server Error";
            case 502 -> "502 Bad Gateway";
            case 503 -> "503 Service Unavailable";
            case 504 -> "504 Gateway Timeout";
            default -> String.valueOf(code);
        };
    }

    private static List<Stage> freshStages() {
        List<Stage> fresh = new ArrayList<>();
        for (String name : STAGE_NAMES) {
            fresh.add(new Stage(name));
        }
        return fresh;
    }

    private void clearRequest() {
        method = null;
        path = null;
        status = null;
        currentCode = null;
        stages = null;
        body = null;
        leaked = null;
        logLevel = null;
        logLine = null;
        traceId = null;
    }

    private static List<String> highlight(String code) {
        return List.of("code:" + code);
    }

    private static List<String> highlight(String code, String stage) {
        return List.of("code:" + code, "stage:" + stage);
    }

    private int issueCount() {
        int total = 0;
        for (ErrorCode entry : codes.values()) {
            total += entry.issues.size();
        }
        return total;
    }

    private List<String> flagged() {
        List<String> names = new ArrayList<>();
        for (ErrorCode entry : codes.values()) {
            if (!entry.issues.isEmpty()) {
                names.add(entry.code);
            }
        }
        return names;
    }

    private int distinctStatuses() {
        List<Integer> seen = new ArrayList<>();
        for (ErrorCode entry : codes.values()) {
            if (!seen.contains(entry.status)) {
                seen.add(entry.status);
            }
        }
        return seen.size();
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("api", api);
        s.put("namespace", namespace);

        List<Object> catalog = new ArrayList<>();
        for (ErrorCode entry : codes.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", entry.code);
            row.put("exception", entry.exception);
            row.put("status", entry.status);
            row.put("statusLine", statusLine(entry.status));
            row.put("retryable", entry.retryable);
            row.put("issues", new ArrayList<>(entry.issues));
            row.put("current", entry.code.equals(currentCode));
            catalog.add(row);
        }
        s.put("codes", catalog);

        if (method != null) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", method);
            request.put("path", path);
            request.put("status", status);
            request.put("code", currentCode);
            request.put("traceId", traceId);

            List<Object> steps = new ArrayList<>();
            for (Stage stage : stages) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", stage.name);
                item.put("state", stage.state);
                item.put("detail", stage.detail);
                steps.add(item);
            }
            request.put("stages", steps);

            List<Object> fields = new ArrayList<>();
            for (String[] pair : body) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", pair[0]);
                field.put("value", pair[1]);
                field.put("leaked", leaked.contains(pair[0]));
                fields.add(field);
            }
            request.put("body", fields);

            if (logLine != null) {
                Map<String, Object> log = new LinkedHashMap<>();
                log.put("level", logLevel);
                log.put("line", logLine);
                request.put("log", log);
            }
            s.put("request", request);
        }

        s.put("codeCount", codes.size());
        s.put("issues", issueCount());
        s.put("unmapped", new ArrayList<>(unmapped));
        return s;
    }
}
