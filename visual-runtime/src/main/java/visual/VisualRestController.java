package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A <em>teaching model</em> of the design of ONE REST controller.
 *
 * <p>Naming the URLs is a different question. This model starts after the URL is
 * chosen and asks what each handler method actually encodes: what binds out of
 * the request, which types cross the HTTP boundary, which status answers, what
 * is validated, and who does the work.
 *
 * <p>It does two things with that:
 * <ul>
 *   <li>{@link #handler(String, String, String, String, int)} declares one
 *       handler and reviews the declaration — the status has to match what the
 *       method promises, a body has to be present exactly where one is defined,
 *       and the persistence entity must not be the type the API speaks;
 *       {@link #paging(String, int, int, String...)},
 *       {@link #validation(String, String, String...)},
 *       {@link #businessLogic(String, String, String)} and
 *       {@link #delegate(String, String)} refine it;</li>
 *   <li>{@link #call(String, String)} and its unhappy variants run a request
 *       through the four stages a controller method really has — bind, validate,
 *       delegate, map to a status — so it is visible that the controller is an
 *       adapter and nothing else.</li>
 * </ul>
 *
 * <p>The checks are deliberately conservative: only codes that genuinely
 * contradict the method are flagged (202 Accepted is accepted everywhere an
 * async answer makes sense), and the entity check is a name comparison, because
 * a teaching model has no type system to consult.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualRestController {

    /** Marker for "no request body" / "no response body". */
    private static final String NONE = "-";

    /** The four stages of a request inside a controller, in order. */
    private static final String[] STAGE_NAMES = {"bind", "validate", "service", "respond"};

    /** One stage of the request currently going through the controller. */
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

    /** One handler method: the design decision, written down. */
    private static final class Handler {
        final String method;
        final String path;
        final List<String> segments;
        final List<String> pathVars;
        final String in;
        final String out;
        final int status;
        final List<String> query = new ArrayList<>();
        final List<String> rules = new ArrayList<>();
        final List<String> issues = new ArrayList<>();
        boolean validated;
        boolean paged;
        int defaultSize;
        int maxSize;
        String logic;

        Handler(String method, String path, String in, String out, int status) {
            this.method = method;
            this.path = path;
            this.segments = segments(path);
            this.pathVars = new ArrayList<>();
            for (String segment : this.segments) {
                if (isTemplate(segment)) {
                    this.pathVars.add(segment.substring(1, segment.length() - 1));
                }
            }
            this.in = in;
            this.out = out;
            this.status = status;
        }

        String key() {
            return method + " " + path;
        }

        void flag(String issue) {
            if (!issues.contains(issue)) {
                issues.add(issue);
            }
        }
    }

    private final String controller;
    private final String basePath;
    private final String entity;
    private final String service;
    /** "METHOD /path" -> handler, in declaration order. */
    private final Map<String, Handler> handlers = new LinkedHashMap<>();

    private String requestMethod;
    private String requestPath;
    private String requestKey;
    private String requestStatus;
    private List<Stage> stages;
    private Map<String, String> bound;
    private List<String[]> params;

    private VisualRestController(String controller, String basePath, String entity) {
        this.controller = controller;
        this.basePath = basePath;
        this.entity = entity;
        this.service = serviceName(controller);
    }

    /** A controller over one resource, with nothing declared yet. */
    public static VisualRestController forResource(String controller, String basePath, String entity) {
        VisualRestController api = new VisualRestController(controller, basePath, entity);
        Trace.event("CONTROLLER_READY",
                controller + " owns one resource at " + basePath + ". Designing it is four decisions per "
                        + "operation: which method and URL name it, what binds out of the request, which type "
                        + "crosses the boundary, and which status says what happened. " + entity + " is the "
                        + "persistence type behind it — the API is not obliged to speak it, and mostly should "
                        + "not",
                controller + " владеет одним ресурсом по " + basePath + ". Спроектировать его — это четыре "
                        + "решения на операцию: какие метод и URL её называют, что извлекается из запроса, "
                        + "какой тип пересекает границу и какой статус сообщает, что произошло. " + entity
                        + " — тип персистентности за ним; API не обязан говорить на нём и чаще всего не должен",
                List.of(), api.state());
        return api;
    }

    /**
     * Declares one handler method. {@code requestType} / {@code responseType} are
     * type names, or {@code "-"} for "no body".
     */
    public void handler(String httpMethod, String path, String requestType, String responseType, int status) {
        clearRequest();
        String verb = httpMethod.toUpperCase(Locale.ROOT);
        String in = normalize(requestType);
        String out = normalize(responseType);
        Handler h = new Handler(verb, path, in, out, status);
        boolean replaced = handlers.containsKey(h.key());
        // put() on an existing key keeps the declaration order, so a fix stays in place.
        handlers.put(h.key(), h);

        Trace.event("HANDLER_ADDED",
                verb + " " + path + (replaced ? " is re-declared: " : " becomes one handler method: ")
                        + signature(h) + ". " + bindingEn(h) + " The signature IS the contract — everything "
                        + "the method needs is a parameter, and everything it promises is the return type "
                        + "plus the status",
                verb + " " + path + (replaced ? " объявлен заново: " : " становится одним методом-обработчиком: ")
                        + signature(h) + ". " + bindingRu(h) + " Сигнатура И ЕСТЬ контракт: всё, что методу "
                        + "нужно, — это параметр, а всё, что он обещает, — тип возврата плюс статус",
                highlight(h.key()), state());

        checkStatus(h);
        checkBody(h);
        checkEntity(h);
    }

    /**
     * Declares that the collection {@code GET path} is paged, with a default and
     * a hard maximum page size, plus the filters it accepts in the query string.
     */
    public void paging(String path, int defaultSize, int maxSize, String... filters) {
        clearRequest();
        Handler h = handlers.get("GET " + path);
        if (h == null) {
            missing("GET", path);
            return;
        }
        h.paged = true;
        h.defaultSize = defaultSize;
        h.maxSize = maxSize;
        addQuery(h, "page");
        addQuery(h, "size");
        addQuery(h, "sort");
        for (String filter : filters) {
            addQuery(h, filter);
        }
        h.issues.remove("unbounded-collection");

        Trace.event("PAGING_ADDED",
                "GET " + path + " now takes page, size (default " + defaultSize + ", capped at " + maxSize
                        + "), sort" + (filters.length == 0 ? "" : " and " + String.join(", ", filters))
                        + ". The cap is the point: a page size the CLIENT chooses is a way to ask one request "
                        + "for the whole table. Filtering and sorting select part of the same collection, so "
                        + "they are query parameters — not new endpoints",
                "GET " + path + " теперь принимает page, size (по умолчанию " + defaultSize + ", максимум "
                        + maxSize + "), sort" + (filters.length == 0 ? "" : " и " + String.join(", ", filters))
                        + ". Смысл в ограничении: размер страницы, который выбирает КЛИЕНТ, — это способ "
                        + "запросить одним запросом всю таблицу. Фильтрация и сортировка выбирают часть той "
                        + "же коллекции, поэтому это параметры запроса, а не новые эндпоинты",
                highlight(h.key()), state());
    }

    /** Declares the constraints the request body of a handler must satisfy. */
    public void validation(String httpMethod, String path, String... rules) {
        clearRequest();
        String verb = httpMethod.toUpperCase(Locale.ROOT);
        Handler h = handlers.get(verb + " " + path);
        if (h == null) {
            missing(verb, path);
            return;
        }
        h.validated = true;
        for (String rule : rules) {
            if (!h.rules.contains(rule)) {
                h.rules.add(rule);
            }
        }

        Trace.event("VALIDATION_ADDED",
                verb + " " + path + " validates its body before anything else runs: " + String.join("; ", h.rules)
                        + ". Shape belongs at the edge — required, non-blank, in range, well-formed — so a "
                        + "malformed request never becomes a service call or a database round trip. Business "
                        + "invariants ('this employee may not exceed their grade cap') stay in the service, "
                        + "where they can see the data",
                verb + " " + path + " проверяет тело до того, как выполнится что-либо ещё: "
                        + String.join("; ", h.rules) + ". Форма — забота границы: обязательность, непустота, "
                        + "диапазон, корректный формат, — чтобы некорректный запрос не превратился в вызов "
                        + "сервиса и поход в базу. Бизнес-инварианты («этот сотрудник не может превысить "
                        + "потолок своего грейда») остаются в сервисе, где видны данные",
                highlight(h.key()), state());
    }

    /** Parks a piece of business logic inside the controller — the classic mistake. */
    public void businessLogic(String httpMethod, String path, String what) {
        clearRequest();
        String verb = httpMethod.toUpperCase(Locale.ROOT);
        Handler h = handlers.get(verb + " " + path);
        if (h == null) {
            missing(verb, path);
            return;
        }
        h.logic = what;
        h.flag("logic-in-controller");

        Trace.event("LOGIC_IN_CONTROLLER",
                verb + " " + path + " now decides " + what + " itself. A controller is an adapter between HTTP "
                        + "and the domain; a rule that lives inside one can only be reached by an HTTP request, "
                        + "so a scheduled job, a message consumer and a unit test each need their own copy. It "
                        + "also cannot be transactional at the right boundary, because the transaction starts "
                        + "one layer down",
                verb + " " + path + " теперь сам решает " + what + ". Контроллер — адаптер между HTTP и "
                        + "доменом; до правила, живущего внутри него, можно добраться только HTTP-запросом, "
                        + "поэтому планировщику, консьюмеру сообщений и юнит-тесту нужна каждому своя копия. "
                        + "И транзакционной границы в нужном месте у него не будет: транзакция начинается "
                        + "слоем ниже",
                highlight(h.key()), state());
    }

    /** Moves that logic down into the service, which is where it belonged. */
    public void delegate(String httpMethod, String path) {
        clearRequest();
        String verb = httpMethod.toUpperCase(Locale.ROOT);
        Handler h = handlers.get(verb + " " + path);
        if (h == null) {
            missing(verb, path);
            return;
        }
        String what = h.logic == null ? "the rule" : h.logic;
        h.logic = null;
        h.issues.remove("logic-in-controller");

        Trace.event("LOGIC_DELEGATED",
                what + " moves into " + service + ", and " + verb + " " + path + " goes back to four lines: "
                        + "bind, validate the shape, call the service, map the result to a status. The same "
                        + "rule is now reachable from a scheduler or a consumer, it can be tested without "
                        + "HTTP, and the transaction wraps the whole operation instead of half of it",
                what + " переезжает в " + service + ", и " + verb + " " + path + " возвращается к четырём "
                        + "строчкам: извлечь, проверить форму, вызвать сервис, отобразить результат в статус. "
                        + "То же правило теперь доступно планировщику или консьюмеру, тестируется без HTTP, "
                        + "а транзакция охватывает операцию целиком, а не её половину",
                highlight(h.key()), state());
    }

    /**
     * Sends a request that succeeds. {@code target} may carry a query string
     * ({@code /employees?department=finance&page=2}).
     */
    public void call(String httpMethod, String target) {
        Handler h = begin(httpMethod, target);
        if (h == null) {
            return;
        }
        validateStage(h);

        Stage call = stages.get(2);
        call.state = "done";
        call.detail = serviceCall(h);
        Trace.event("SERVICE_CALLED",
                "The controller hands over: " + call.detail + ". That is the whole body of a well-designed "
                        + "handler — it does not open a transaction, does not touch a repository and does not "
                        + "decide anything about the domain. It translates HTTP into a call and back",
                "Контроллер передаёт работу дальше: " + call.detail + ". Это и есть всё тело хорошо "
                        + "спроектированного обработчика: он не открывает транзакцию, не трогает репозиторий "
                        + "и ничего не решает про домен. Он переводит HTTP в вызов и обратно",
                highlight(h.key(), "service"), state());

        Stage respond = stages.get(3);
        respond.state = "done";
        respond.detail = h.out.equals(NONE) ? "no body" : h.out;
        requestStatus = statusLine(h.status);
        Trace.event("RESPONSE_SENT",
                requestMethod + " " + requestPath + " -> " + requestStatus + ", "
                        + (h.out.equals(NONE) ? "with no body" : "body " + h.out)
                        + (h.status == 201 ? ", plus Location pointing at the URL the server just created" : "")
                        + ". The status line is the part of the answer machines read: a client, a gateway and "
                        + "a retry policy all branch on it before anyone parses JSON",
                requestMethod + " " + requestPath + " -> " + requestStatus + ", "
                        + (h.out.equals(NONE) ? "без тела" : "тело " + h.out)
                        + (h.status == 201 ? ", плюс Location с URL только что созданного ресурса" : "")
                        + ". Строка статуса — та часть ответа, которую читают машины: клиент, шлюз и политика "
                        + "повторов ветвятся по ней ещё до того, как кто-либо разберёт JSON",
                highlight(h.key(), "respond"), state());
    }

    /** Sends a request whose body breaks a constraint — the 400 path. */
    public void callInvalid(String httpMethod, String target, String field, String rule) {
        Handler h = begin(httpMethod, target);
        if (h == null) {
            return;
        }
        Stage check = stages.get(1);
        Stage call = stages.get(2);
        check.state = h.validated ? "failed" : "skipped";
        check.detail = field + ": " + rule;
        call.state = h.validated ? "skipped" : "failed";
        requestStatus = "400 Bad Request";

        Trace.event("VALIDATION_FAILED",
                h.validated
                        ? "The body fails a declared constraint (" + field + ": " + rule + "), so the request "
                            + "stops at the edge: " + service + " is never called, no transaction opens and no "
                            + "row is read. Rejecting early is not only cheaper — it is the only place that can "
                            + "still say WHICH field was wrong"
                        : "The body is wrong (" + field + ": " + rule + ") and nothing on this handler checks "
                            + "it. Without validation at the edge the bad value travels down and surfaces as a "
                            + "constraint violation or a NullPointerException, which the client sees as a 500 "
                            + "with no idea which field to fix",
                h.validated
                        ? "Тело нарушает объявленное ограничение (" + field + ": " + rule + "), поэтому запрос "
                            + "останавливается на границе: " + service + " не вызывается, транзакция не "
                            + "открывается, ни одна строка не читается. Ранний отказ не просто дешевле — это "
                            + "единственное место, где ещё можно сказать, КАКОЕ поле неверно"
                        : "Тело неверно (" + field + ": " + rule + "), и на этом обработчике его никто не "
                            + "проверяет. Без валидации на границе плохое значение уходит вглубь и всплывает "
                            + "нарушением ограничения или NullPointerException, а клиент видит 500 и не знает, "
                            + "какое поле чинить",
                highlight(h.key(), "validate"), state());

        Stage respond = stages.get(3);
        respond.state = "done";
        respond.detail = "ErrorResponse{code=validation_failed, field=" + field + "}";
        Trace.event("ERROR_MAPPED",
                "One @RestControllerAdvice turns the failure into 400 Bad Request with the API's single error "
                        + "shape: " + respond.detail + ". No handler contains a try/catch, so every endpoint "
                        + "reports errors identically — which is what makes the error format part of the "
                        + "contract instead of an accident per method",
                "Один @RestControllerAdvice превращает сбой в 400 Bad Request с единой для API формой ошибки: "
                        + respond.detail + ". Ни в одном обработчике нет try/catch, поэтому все эндпоинты "
                        + "сообщают об ошибках одинаково — именно это делает формат ошибки частью контракта, "
                        + "а не случайностью каждого метода",
                highlight(h.key(), "respond"), state());
    }

    /** Sends a request for something that is not there — the 404 path. */
    public void callNotFound(String httpMethod, String target) {
        Handler h = begin(httpMethod, target);
        if (h == null) {
            return;
        }
        validateStage(h);

        String exception = entity + "NotFoundException";
        Stage call = stages.get(2);
        call.state = "active";
        call.detail = serviceCall(h);
        Trace.event("SERVICE_CALLED",
                "The controller hands over: " + call.detail + ". It does not check first whether the row "
                        + "exists — an 'exists?' call followed by a 'load' is two queries and a race between "
                        + "them. The service is the one that knows",
                "Контроллер передаёт работу дальше: " + call.detail + ". Он не проверяет заранее, есть ли "
                        + "строка: «существует?» плюс «загрузи» — это два запроса и гонка между ними. Знает "
                        + "об этом сервис",
                highlight(h.key(), "service"), state());

        call.state = "failed";
        call.detail = call.detail + " -> " + exception;
        requestStatus = "404 Not Found";
        Stage respond = stages.get(3);
        respond.state = "done";
        respond.detail = "ErrorResponse{code=not_found}";
        Trace.event("ERROR_MAPPED",
                service + " throws " + exception + " and the handler lets it fly. The same "
                        + "@RestControllerAdvice maps that domain exception to 404 Not Found, so 'missing' is "
                        + "translated into HTTP in exactly one place. Catching it in the controller would "
                        + "duplicate that decision in every method, and returning an empty 200 would tell the "
                        + "client that nothing is wrong",
                service + " бросает " + exception + ", а обработчик не мешает исключению лететь дальше. Тот же "
                        + "@RestControllerAdvice отображает это доменное исключение в 404 Not Found, поэтому "
                        + "«не найдено» переводится в HTTP ровно в одном месте. Перехват в контроллере "
                        + "продублировал бы это решение в каждом методе, а пустой 200 сообщил бы клиенту, "
                        + "что всё в порядке",
                highlight(h.key(), "respond"), state());
    }

    /** Reviews the controller as a whole: what a consumer gets and what is still open. */
    public void review() {
        clearRequest();
        for (Handler h : handlers.values()) {
            if (!"GET".equals(h.method) || isItem(h.segments) || !isList(h.out) || h.paged) {
                continue;
            }
            h.flag("unbounded-collection");
            Trace.event("UNBOUNDED_COLLECTION",
                    "GET " + h.path + " returns " + h.out + " with no page size. It is fine on the developer's "
                            + "20 rows and it is an outage on production's two million: one request builds the "
                            + "whole list in heap, serializes it, and the client waits for all of it. Paging is "
                            + "part of designing the endpoint, not a later optimisation",
                    "GET " + h.path + " возвращает " + h.out + " без размера страницы. На двадцати строках "
                            + "разработчика это нормально, а на двух миллионах в проде — авария: один запрос "
                            + "собирает весь список в куче, сериализует его, и клиент ждёт всё целиком. "
                            + "Постраничность — часть проектирования эндпоинта, а не поздняя оптимизация",
                    highlight(h.key()), state());
        }

        int issues = issueCount();
        Trace.event("CONTROLLER_REVIEW",
                controller + ": " + handlers.size() + " handler(s), " + issues + " design finding(s)"
                        + (issues == 0 ? "" : " — " + String.join(", ", flagged()))
                        + ". Read the table as a consumer would: every row is one operation with one status, "
                        + "the types are the API's own, the collection is bounded, the writes are validated, "
                        + "and no row does any work of its own",
                controller + ": обработчиков — " + handlers.size() + ", замечаний по дизайну — " + issues
                        + (issues == 0 ? "" : ": " + String.join(", ", flagged()))
                        + ". Прочитайте таблицу глазами потребителя: каждая строка — одна операция с одним "
                        + "статусом, типы — собственные типы API, коллекция ограничена, записи проверяются, "
                        + "и ни одна строка не делает работу сама",
                List.of(), state());
    }

    // ---------------------------------------------------------------- checks

    /** Flags a status code that contradicts what the method promises. */
    private void checkStatus(Handler h) {
        boolean collection = !isItem(h.segments);
        String en = null;
        String ru = null;
        switch (h.method) {
            case "GET" -> {
                if (h.status != 200) {
                    en = "a read answers 200 OK; anything else tells the client that something happened, and "
                            + "nothing happened";
                    ru = "чтение отвечает 200 OK; любой другой код сообщает клиенту, что что-то произошло, — "
                            + "а не произошло ничего";
                }
            }
            case "POST" -> {
                if (collection && h.status != 201 && h.status != 202) {
                    en = "a create answers 201 Created with a Location header naming the new URL (or 202 "
                            + "Accepted when the work is queued); 200 makes every client parse the body to "
                            + "find out whether anything was created and where it now lives";
                    ru = "создание отвечает 201 Created с заголовком Location, называющим новый URL (или 202 "
                            + "Accepted, если работа поставлена в очередь); 200 заставляет каждого клиента "
                            + "разбирать тело, чтобы понять, создалось ли что-нибудь и где оно теперь";
                }
            }
            case "PUT" -> {
                if (h.status != 200 && h.status != 201 && h.status != 204) {
                    en = "a replace answers 200 OK with the new representation, 204 No Content without one, "
                            + "or 201 Created when the PUT created the resource";
                    ru = "замена отвечает 200 OK с новым представлением, 204 No Content без него или 201 "
                            + "Created, если PUT создал ресурс";
                }
            }
            case "PATCH" -> {
                if (h.status != 200 && h.status != 204) {
                    en = "a partial update answers 200 OK with the patched representation or 204 No Content";
                    ru = "частичное изменение отвечает 200 OK с изменённым представлением или 204 No Content";
                }
            }
            case "DELETE" -> {
                if (h.status != 204 && h.status != 202) {
                    en = "a delete answers 204 No Content (or 202 Accepted when it is asynchronous) — there is "
                            + "nothing left to send back";
                    ru = "удаление отвечает 204 No Content (или 202 Accepted, если оно асинхронное) — "
                            + "отправлять назад уже нечего";
                }
            }
            default -> {
                // Other methods carry no expectation in this model.
            }
        }
        if (en == null) {
            return;
        }
        h.flag("status-mismatch");
        Trace.event("STATUS_MISMATCH",
                h.method + " " + h.path + " answers " + statusLine(h.status) + ", but " + en,
                h.method + " " + h.path + " отвечает " + statusLine(h.status) + ", а " + ru,
                highlight(h.key()), state());
    }

    /** Flags a body where the method defines none, or a missing one where it does. */
    private void checkBody(Handler h) {
        boolean read = "GET".equals(h.method) || "DELETE".equals(h.method);
        boolean write = "POST".equals(h.method) || "PUT".equals(h.method) || "PATCH".equals(h.method);

        if (read && !h.in.equals(NONE)) {
            report(h, "body-mismatch",
                    h.method + " " + h.path + " declares a " + h.in + " request body. " + h.method + " has no "
                            + "defined body semantics: caches key on the URL alone, several proxies and "
                            + "servers drop the body, and a retry may not resend it. A read that needs a large "
                            + "filter is POST on a search sub-resource; a delete that needs a reason puts it "
                            + "in the query string or in a header",
                    h.method + " " + h.path + " объявляет тело запроса " + h.in + ". У " + h.method + " нет "
                            + "определённой семантики тела: кэши ключуются только по URL, часть прокси и "
                            + "серверов тело выбрасывает, а повтор может его не отправить. Чтение с большим "
                            + "фильтром — это POST по под-ресурсу поиска; удаление с причиной кладёт её в "
                            + "query string или в заголовок");
        }
        if (write && h.in.equals(NONE)) {
            report(h, "body-mismatch",
                    h.method + " " + h.path + " writes but declares no request body, which means the data is "
                            + "riding in query parameters — where it lands in access logs and browser history, "
                            + "hits the URL length limit, and cannot be typed or validated as a DTO",
                    h.method + " " + h.path + " пишет, но не объявляет тело запроса — значит, данные едут в "
                            + "параметрах строки запроса, где попадают в логи доступа и историю браузера, "
                            + "упираются в ограничение длины URL и не могут быть типизированы и проверены "
                            + "как DTO");
        }
        if (h.status == 204 && !h.out.equals(NONE)) {
            report(h, "body-mismatch",
                    h.method + " " + h.path + " answers 204 No Content and returns " + h.out + ". 204 promises "
                            + "there is nothing to read, so a client is entitled to close the stream without "
                            + "looking. Return the body with 200, or return nothing with 204 — not both",
                    h.method + " " + h.path + " отвечает 204 No Content и возвращает " + h.out + ". 204 "
                            + "обещает, что читать нечего, поэтому клиент вправе закрыть поток, не заглядывая "
                            + "в него. Либо тело и 200, либо ничего и 204 — но не оба сразу");
        }
    }

    /** Flags the persistence entity being used as the API's own type. */
    private void checkEntity(Handler h) {
        boolean inLeaks = mentionsEntity(h.in);
        boolean outLeaks = mentionsEntity(h.out);
        if (!inLeaks && !outLeaks) {
            return;
        }
        String where = inLeaks && outLeaks ? "in and out" : (inLeaks ? "in" : "out");
        String whereRu = inLeaks && outLeaks ? "и на вход, и на выход" : (inLeaks ? "на вход" : "на выход");
        report(h, "entity-exposed",
                h.method + " " + h.path + " passes the entity " + entity + " " + where + ". Then the JSON is "
                        + "whatever the table happens to hold: renaming a column breaks every client, a lazy "
                        + "association turns serialization into extra queries, and a field nobody meant to "
                        + "publish (password hash, internal salary) is one forgotten annotation away from the "
                        + "wire. On the way in it is worse — a client can set id, createdAt or role. A request "
                        + "DTO and a response DTO are two small classes that decouple the API from the schema",
                h.method + " " + h.path + " пропускает сущность " + entity + " " + whereRu + ". Тогда JSON — "
                        + "это то, что оказалось в таблице: переименование колонки ломает всех клиентов, "
                        + "ленивая связь превращает сериализацию в дополнительные запросы, а поле, которое "
                        + "никто не собирался публиковать (хеш пароля, внутренняя зарплата), отделено от "
                        + "провода одной забытой аннотацией. На входе хуже: клиент может выставить id, "
                        + "createdAt или role. DTO запроса и DTO ответа — два маленьких класса, которые "
                        + "отвязывают API от схемы");
    }

    private void report(Handler h, String issue, String en, String ru) {
        h.flag(issue);
        Trace.event(issue.equals("entity-exposed") ? "ENTITY_EXPOSED" : "BODY_MISMATCH",
                en, ru, highlight(h.key()), state());
    }

    // --------------------------------------------------------------- request

    /** Starts a request: routes it, binds it, and returns the handler it hit. */
    private Handler begin(String httpMethod, String target) {
        String verb = httpMethod.toUpperCase(Locale.ROOT);
        int mark = target.indexOf('?');
        String path = mark < 0 ? target : target.substring(0, mark);
        String query = mark < 0 ? "" : target.substring(mark + 1);

        requestMethod = verb;
        requestPath = path;
        requestKey = null;
        requestStatus = "pending";
        stages = freshStages();
        bound = new LinkedHashMap<>();
        params = parseQuery(query);

        Handler h = match(verb, path);
        if (h == null) {
            requestStatus = "404 Not Found";
            stages.get(0).state = "failed";
            Trace.event("NO_HANDLER",
                    verb + " " + path + " -> 404 Not Found: this controller declares no handler with that "
                            + "method and shape, so the request never reaches any of your code",
                    verb + " " + path + " -> 404 Not Found: в этом контроллере нет обработчика с таким методом "
                            + "и формой, поэтому запрос не доходит ни до одной строчки вашего кода",
                    List.of("stage:bind"), state());
            return null;
        }

        requestKey = h.key();
        List<String> requested = segments(path);
        for (int i = 0; i < h.segments.size(); i++) {
            if (isTemplate(h.segments.get(i))) {
                bound.put(h.segments.get(i).substring(1, h.segments.get(i).length() - 1), requested.get(i));
            }
        }

        Stage bind = stages.get(0);
        bind.state = "done";
        bind.detail = describeBinding(h);
        Trace.event("REQUEST_BOUND",
                "Binding happens before your method runs: " + bind.detail + ". Spring reads the path template "
                        + "into the parameters, the query string into the ones with defaults, and the JSON "
                        + "into the request type — so a letter where a number belongs is a 400 that never "
                        + "enters the method body. That is why the parameter types are a design decision",
                "Привязка происходит до того, как выполнится ваш метод: " + bind.detail + ". Spring "
                        + "раскладывает шаблон пути по параметрам, строку запроса — по параметрам со "
                        + "значениями по умолчанию, а JSON — в тип запроса, поэтому буква там, где ждали "
                        + "число, — это 400, который вообще не входит в тело метода. Поэтому типы параметров "
                        + "и есть проектное решение",
                highlight(h.key(), "bind"), state());
        return h;
    }

    /** Runs the validation stage of the current request. */
    private void validateStage(Handler h) {
        Stage check = stages.get(1);
        if (!h.validated) {
            check.state = "skipped";
            check.detail = "no constraints declared";
            return;
        }
        check.state = "done";
        check.detail = String.join(", ", h.rules);
        Trace.event("VALIDATION_PASSED",
                "The body satisfies every declared constraint (" + check.detail + "), so the request is "
                        + "allowed further in. Validation at the edge is what lets the service assume its "
                        + "arguments are well formed instead of re-checking them",
                "Тело удовлетворяет всем объявленным ограничениям (" + check.detail + "), поэтому запрос "
                        + "пропускают дальше. Валидация на границе — это то, что позволяет сервису считать "
                        + "свои аргументы корректными, а не перепроверять их заново",
                highlight(h.key(), "validate"), state());
    }

    private void missing(String verb, String path) {
        Trace.event("NO_HANDLER",
                verb + " " + path + " is not declared on " + controller + ", so there is nothing to refine",
                verb + " " + path + " не объявлен в " + controller + ", уточнять нечего",
                List.of(), state());
    }

    /** Finds the declared handler a request lands on, if any. */
    private Handler match(String verb, String path) {
        List<String> requested = segments(path);
        Handler best = null;
        int bestTemplates = Integer.MAX_VALUE;
        for (Handler h : handlers.values()) {
            if (!h.method.equals(verb) || h.segments.size() != requested.size()) {
                continue;
            }
            int templates = 0;
            boolean ok = true;
            for (int i = 0; i < requested.size(); i++) {
                String pattern = h.segments.get(i);
                if (isTemplate(pattern)) {
                    templates++;
                } else if (!pattern.equals(requested.get(i))) {
                    ok = false;
                    break;
                }
            }
            // A literal segment beats a template, so /employees/summary wins over /employees/{id}.
            if (ok && templates < bestTemplates) {
                best = h;
                bestTemplates = templates;
            }
        }
        return best;
    }

    // ------------------------------------------------------------- rendering

    /** The Java signature this design implies — the shape the learner would write. */
    private String signature(Handler h) {
        String returns;
        if (h.out.equals(NONE)) {
            returns = "void";
        } else if (h.status == 201) {
            returns = "ResponseEntity<" + h.out + ">";
        } else if (h.paged && isList(h.out)) {
            returns = "Page<" + h.out.substring(5, h.out.length() - 1) + ">";
        } else {
            returns = h.out;
        }

        List<String> parts = new ArrayList<>();
        for (String var : h.pathVars) {
            parts.add("@PathVariable " + (isIdName(var) ? "long " : "String ") + var);
        }
        for (String param : h.query) {
            if (param.equals("page") || param.equals("size") || param.equals("sort")) {
                continue;
            }
            parts.add("@RequestParam(required = false) String " + param);
        }
        if (h.paged) {
            parts.add("Pageable pageable");
        }
        if (!h.in.equals(NONE)) {
            parts.add((h.validated ? "@Valid " : "") + "@RequestBody " + h.in + " request");
        }
        return returns + " " + operation(h) + "(" + String.join(", ", parts) + ")";
    }

    /** The service call this handler exists to make. */
    private String serviceCall(Handler h) {
        List<String> args = new ArrayList<>(bound.values());
        for (String[] pair : params) {
            args.add(pair[0] + "=" + pair[1]);
        }
        if (!h.in.equals(NONE)) {
            args.add("request");
        }
        return service + "." + operation(h) + "(" + String.join(", ", args) + ")";
    }

    /** A method name for the operation, from the method and the shape of the path. */
    private static String operation(Handler h) {
        boolean item = isItem(h.segments);
        boolean sub = !item && h.segments.size() > 1 && h.pathVars.size() > 0;
        if (sub) {
            String tail = capitalize(camel(h.segments.get(h.segments.size() - 1)));
            return switch (h.method) {
                case "GET" -> "get" + tail;
                case "POST" -> "add" + tail;
                case "PUT" -> "set" + tail;
                case "PATCH" -> "update" + tail;
                case "DELETE" -> "remove" + tail;
                default -> h.method.toLowerCase(Locale.ROOT) + tail;
            };
        }
        return switch (h.method) {
            case "GET" -> item ? "getById" : "list";
            case "POST" -> "create";
            case "PUT" -> "replace";
            case "PATCH" -> "update";
            case "DELETE" -> "delete";
            default -> h.method.toLowerCase(Locale.ROOT);
        };
    }

    private String describeBinding(Handler h) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : bound.entrySet()) {
            parts.add(e.getKey() + "=" + e.getValue());
        }
        for (String[] pair : params) {
            parts.add(pair[0] + "=" + pair[1]);
        }
        if (h.paged && !hasParam("size")) {
            parts.add("size=" + h.defaultSize + " (default)");
        }
        if (!h.in.equals(NONE)) {
            parts.add("body -> " + h.in);
        }
        return parts.isEmpty() ? "nothing to bind" : String.join(", ", parts);
    }

    private String bindingEn(Handler h) {
        StringBuilder sb = new StringBuilder();
        sb.append(h.pathVars.isEmpty()
                ? "The path carries no variables, so it names the collection itself. "
                : "The path template gives " + String.join(", ", h.pathVars) + " as a typed parameter. ");
        sb.append(h.in.equals(NONE)
                ? "There is no request body. "
                : "The JSON body is deserialized into " + h.in + ". ");
        sb.append("It answers ")
                .append(h.out.equals(NONE) ? "with no body" : h.out)
                .append(" and ").append(statusLine(h.status)).append('.');
        return sb.toString();
    }

    private String bindingRu(Handler h) {
        StringBuilder sb = new StringBuilder();
        sb.append(h.pathVars.isEmpty()
                ? "В пути нет переменных, значит он называет саму коллекцию. "
                : "Шаблон пути отдаёт " + String.join(", ", h.pathVars) + " как типизированный параметр. ");
        sb.append(h.in.equals(NONE)
                ? "Тела запроса нет. "
                : "JSON-тело десериализуется в " + h.in + ". ");
        sb.append("Отвечает ")
                .append(h.out.equals(NONE) ? "без тела" : h.out)
                .append(" и ").append(statusLine(h.status)).append('.');
        return sb.toString();
    }

    // --------------------------------------------------------------- helpers

    private boolean hasParam(String name) {
        for (String[] pair : params) {
            if (pair[0].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void addQuery(Handler h, String param) {
        if (!h.query.contains(param)) {
            h.query.add(param);
        }
    }

    private boolean mentionsEntity(String type) {
        return type.equals(entity) || type.equals("List<" + entity + ">") || type.equals("Page<" + entity + ">");
    }

    private static boolean isList(String type) {
        return type.startsWith("List<") && type.endsWith(">");
    }

    private static boolean isItem(List<String> segments) {
        return !segments.isEmpty() && isTemplate(segments.get(segments.size() - 1));
    }

    private static boolean isTemplate(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private static boolean isIdName(String name) {
        return name.equals("id") || name.endsWith("Id");
    }

    private static String normalize(String type) {
        return type == null || type.isBlank() || type.equals(NONE) ? NONE : type.trim();
    }

    private static List<String> segments(String path) {
        List<String> segments = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                segments.add(part);
            }
        }
        return segments;
    }

    /** "EmployeeController" -> "employeeService". */
    private static String serviceName(String controller) {
        String base = controller.endsWith("Controller")
                ? controller.substring(0, controller.length() - "Controller".length())
                : controller;
        if (base.isEmpty()) {
            return "service";
        }
        return Character.toLowerCase(base.charAt(0)) + base.substring(1) + "Service";
    }

    /** "salary-history" -> "salaryHistory". */
    private static String camel(String segment) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '-' || c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String capitalize(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private static String statusLine(int code) {
        return switch (code) {
            case 200 -> "200 OK";
            case 201 -> "201 Created";
            case 202 -> "202 Accepted";
            case 204 -> "204 No Content";
            case 400 -> "400 Bad Request";
            case 404 -> "404 Not Found";
            case 409 -> "409 Conflict";
            case 422 -> "422 Unprocessable Entity";
            default -> String.valueOf(code);
        };
    }

    private static List<String[]> parseQuery(String query) {
        List<String[]> parsed = new ArrayList<>();
        if (query.isEmpty()) {
            return parsed;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                parsed.add(new String[]{pair, ""});
            } else {
                parsed.add(new String[]{pair.substring(0, eq), pair.substring(eq + 1)});
            }
        }
        return parsed;
    }

    private static List<Stage> freshStages() {
        List<Stage> fresh = new ArrayList<>();
        for (String name : STAGE_NAMES) {
            fresh.add(new Stage(name));
        }
        return fresh;
    }

    private void clearRequest() {
        requestMethod = null;
        requestPath = null;
        requestKey = null;
        requestStatus = null;
        stages = null;
        bound = null;
        params = null;
    }

    private static List<String> highlight(String key) {
        return List.of("handler:" + key);
    }

    private static List<String> highlight(String key, String stage) {
        return List.of("handler:" + key, "stage:" + stage);
    }

    private int issueCount() {
        int total = 0;
        for (Handler h : handlers.values()) {
            total += h.issues.size();
        }
        return total;
    }

    private List<String> flagged() {
        List<String> keys = new ArrayList<>();
        for (Handler h : handlers.values()) {
            if (!h.issues.isEmpty()) {
                keys.add(h.key());
            }
        }
        return keys;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("controller", controller);
        s.put("basePath", basePath);
        s.put("entity", entity);
        s.put("service", service);

        List<Object> table = new ArrayList<>();
        for (Handler h : handlers.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", h.key());
            entry.put("method", h.method);
            entry.put("path", h.path);
            entry.put("in", h.in);
            entry.put("out", h.out);
            entry.put("status", h.status);
            entry.put("statusLine", statusLine(h.status));
            entry.put("signature", signature(h));
            entry.put("query", new ArrayList<>(h.query));
            entry.put("validated", h.validated);
            entry.put("logic", h.logic);
            entry.put("issues", new ArrayList<>(h.issues));
            entry.put("current", h.key().equals(requestKey));
            table.add(entry);
        }
        s.put("handlers", table);

        if (requestMethod != null) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", requestMethod);
            request.put("path", requestPath);
            request.put("handler", requestKey);
            request.put("status", requestStatus);
            List<Object> steps = new ArrayList<>();
            for (Stage stage : stages) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", stage.name);
                item.put("state", stage.state);
                item.put("detail", stage.detail);
                steps.add(item);
            }
            request.put("stages", steps);
            s.put("request", request);
        }

        s.put("handlerCount", handlers.size());
        s.put("issues", issueCount());
        return s;
    }
}
