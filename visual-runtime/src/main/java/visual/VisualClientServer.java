package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of the only thing that actually connects a frontend
 * to a backend: an HTTP message.
 *
 * <p>The two sides run in different processes, on different machines, in
 * different languages, and share no memory. So the frontend never "calls a
 * method" on the backend — it builds a text request (method, path, headers,
 * body), hands it to the network, and some time later gets back a text response
 * (status line, headers, body) or nothing at all. Everything else — REST,
 * JSON, tokens, status codes, loading spinners — exists to make that one
 * exchange workable.
 *
 * <p>The model owns both sides at once:
 * <ul>
 *   <li>the <em>browser app</em>: what is on screen, the data it has parsed into
 *       its own memory, and the token it has to attach to every call;</li>
 *   <li>the <em>API server</em>: a tiny routing table, a small store of
 *       employees, and — deliberately — no per-user memory whatsoever.</li>
 * </ul>
 *
 * <p>{@link #call(Request)} walks one round trip: the request is built, the
 * promise is pending, the router picks a handler, the handler answers with a
 * status, and the frontend parses the body and re-renders. The interesting
 * cases are the ones where that goes wrong: a rejected token, a validation
 * error, a 500, and — different from all of those — a network failure, where
 * there is no response to read at all.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualClientServer {

    /** Fields serialized as JSON numbers rather than quoted strings. */
    private static final Set<String> NUMERIC_FIELDS = Set.of("id", "salary");
    /** Methods that change server state, so the model asks them for a token. */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * How each stored field crosses the wire:
     * {Java type, JSON text form, what JavaScript ends up with, note code}.
     */
    private static final Map<String, String[]> WIRE_TYPES = new LinkedHashMap<>();

    static {
        WIRE_TYPES.put("id", new String[]{"long", "number", "number", ""});
        WIRE_TYPES.put("name", new String[]{"String", "string", "string", ""});
        WIRE_TYPES.put("role", new String[]{"String", "string", "string", ""});
        WIRE_TYPES.put("hiredAt", new String[]{"LocalDate", "string", "string", "no-date-type"});
        WIRE_TYPES.put("salary", new String[]{"BigDecimal", "number", "number", "precision"});
        WIRE_TYPES.put("phone", new String[]{"String (null)", "null", "null", "null-vs-missing"});
    }

    /** The routing table the server was started with, shown as-is in the UI. */
    private static final List<String> ROUTES = List.of(
            "POST /api/sessions",
            "GET /api/employees",
            "POST /api/employees",
            "GET /api/employees/{id}",
            "DELETE /api/employees/{id}",
            "GET /api/notifications",
            "GET /api/reports/payroll",
            "GET /ws/notifications");

    /**
     * One call the frontend makes. It is deliberately just data: a method, a
     * path, headers and an ordered body — exactly what ends up as text on the
     * wire.
     */
    public static final class Request {

        private final String method;
        private final String path;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> body = new LinkedHashMap<>();
        private boolean useStoredToken;

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

        /** Adds a request header exactly as the fetch call would. */
        public Request header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Sends {@code Authorization: Bearer <token>} with a token written by hand. */
        public Request bearer(String token) {
            return header("Authorization", "Bearer " + token);
        }

        /** Attaches whatever token the app is currently holding, or nothing if it has none. */
        public Request authenticated() {
            this.useStoredToken = true;
            return this;
        }

        /** Adds a JSON body field, setting {@code Content-Type: application/json}. */
        public Request json(String field, String value) {
            headers.put("Content-Type", "application/json");
            body.put(field, value);
            return this;
        }

        /** Adds another body field, keeping the order the example wrote them in. */
        public Request and(String field, String value) {
            body.put(field, value);
            return this;
        }

        /** {@code Idempotency-Key} — lets the server recognise a retry of this exact write. */
        public Request idempotencyKey(String key) {
            return header("Idempotency-Key", key);
        }
    }

    // ------------------------------------------------------------------ state

    private final String appName;
    private final String apiName;

    /** Frontend: what the component tree is showing right now. */
    private String uiStatus = "idle";
    private final List<String> uiItems = new ArrayList<>();
    private String uiError;
    private String uiErrorDetail = "";
    /** Frontend: the token it must re-attach to every single call. */
    private String token;
    /** Frontend: the parsed body of the last single-record response. */
    private Map<String, String> parsed = new LinkedHashMap<>();
    private boolean socketOpen;
    private String lastPush;

    /** Server: the whole persistent state there is — id -> ordered fields. */
    private final Map<String, Map<String, String>> employees = new LinkedHashMap<>();
    /** Server: writes already seen, keyed by Idempotency-Key -> the id they created. */
    private final Map<String, String> idempotencyKeys = new LinkedHashMap<>();
    private int nextId = 3;

    private boolean networkUp = true;
    /** When set, the next call is delivered and executed but its answer never arrives. */
    private boolean dropResponse;
    private List<String> uiItemsBefore = new ArrayList<>();
    private Map<String, String> parsedBefore = new LinkedHashMap<>();
    private String tokenBefore;

    private Request request;
    private String matchedRoute;
    private int status;
    private String statusText;
    private String responseKind;
    private Map<String, String> responseHeaders = new LinkedHashMap<>();
    private String responseBody;
    private boolean hasResponse;
    private String failure;
    private List<String[]> payload = new ArrayList<>();

    private int roundTrips;
    private int failedCalls;
    private int created;
    private int duplicates;
    private int polls;
    private int pushes;

    private VisualClientServer(String appName, String apiName) {
        this.appName = appName;
        this.apiName = apiName;
        employees.put("1", record("Ada Byron", "engineer", "2024-03-01", "1200.50", null));
        employees.put("2", record("Omar Diaz", "manager", "2023-11-15", "1800.00", null));
    }

    private static Map<String, String> record(String name, String role, String hiredAt,
                                              String salary, String phone) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("role", role);
        fields.put("hiredAt", hiredAt);
        fields.put("salary", salary);
        fields.put("phone", phone);
        return fields;
    }

    /**
     * The browser has just downloaded the app's HTML and JavaScript; from now on
     * every piece of data on the screen has to be asked for over HTTP.
     */
    public static VisualClientServer open(String appName, String apiName) {
        VisualClientServer app = new VisualClientServer(appName, apiName);
        Trace.event("APP_LOADED",
                "The browser downloaded " + appName + " (HTML + JavaScript) and now runs it in a tab. "
                        + "It starts with an EMPTY memory: it has no employees, no user, no database "
                        + "connection. " + apiName + " is a separate process that holds all of that — "
                        + "and the only way to reach it is to send it a message over HTTP",
                "Браузер загрузил " + appName + " (HTML + JavaScript) и теперь выполняет его во вкладке. "
                        + "Стартует он с ПУСТОЙ памятью: у него нет ни сотрудников, ни пользователя, ни "
                        + "подключения к базе. Всё это есть у " + apiName + " — отдельного процесса, и "
                        + "единственный способ до него добраться — отправить ему сообщение по HTTP",
                List.of("app", "api"), app.state());
        return app;
    }

    // ------------------------------------------------------------- round trips

    /** Runs one request/response round trip the way a browser and a server would. */
    public void call(Request call) {
        begin(call);

        Trace.event("REQUEST_SENT",
                "There is no method to call across a network, so the frontend WRITES a message: "
                        + requestLine() + describeHeadersEn() + describeBodyEn()
                        + ". That text is the whole interface between the two sides",
                "Через сеть нельзя вызвать метод, поэтому фронтенд ПИШЕТ сообщение: "
                        + requestLine() + describeHeadersRu() + describeBodyRu()
                        + ". Этот текст и есть весь интерфейс между двумя сторонами",
                List.of("request"), state());

        Trace.event("CALL_PENDING",
                "fetch() returns immediately with a promise — the answer is somewhere on a network, "
                        + "milliseconds away at best. The UI has to render SOMETHING right now, so it goes "
                        + "into a loading state. Every screen that talks to a backend has three states: "
                        + "loading, loaded, failed",
                "fetch() сразу возвращает промис — ответ где-то в сети, в лучшем случае в миллисекундах "
                        + "отсюда. Интерфейсу нужно прямо сейчас что-то нарисовать, поэтому он переходит в "
                        + "состояние загрузки. У любого экрана, который ходит в бэкенд, три состояния: "
                        + "загружается, загружено, ошибка",
                List.of("app", "request"), state());

        if (!networkUp) {
            failedCalls++;
            failure = "network";
            uiStatus = "error";
            uiError = "network";
            uiErrorDetail = "";
            Trace.event("NETWORK_FAILED",
                    "The request never got an answer: the promise REJECTS. Note what the frontend does "
                            + "not have — a status code. A 500 is the server telling you it failed; this is "
                            + "silence, and silence does not say whether the server got the request and did "
                            + "the work anyway. Retrying a read is safe; retrying a write is a decision",
                    "Запрос так и не получил ответа: промис ОТКЛОНЁН. Обратите внимание, чего у фронтенда "
                            + "нет — кода состояния. 500 — это сервер сам говорит, что у него не вышло; а "
                            + "здесь тишина, и тишина не сообщает, дошёл ли запрос до сервера и не сделал ли "
                            + "он работу всё равно. Повторить чтение безопасно, повторить запись — решение",
                    List.of("network", "app"), state());
            return;
        }

        matchedRoute = matchRoute(call.method, call.path);
        if (matchedRoute == null) {
            respond(404, "Not Found", "not-found",
                    "{\"error\":\"no route\",\"path\":\"" + pathOnly(call.path) + "\"}");
            uiStatus = "error";
            uiError = "http";
            uiErrorDetail = "404";
            Trace.event("NOT_FOUND",
                    "Nothing is mapped to " + call.method + " " + pathOnly(call.path)
                            + ". The server does not guess: a path is compared as text, so a typo, a "
                            + "missing /api prefix or a wrong plural is simply a different URL — and the "
                            + "frontend gets a perfectly valid 404 answer to a question nobody handles",
                    "На " + call.method + " " + pathOnly(call.path) + " ничего не отображено. Сервер не "
                            + "догадывается: путь сравнивается как текст, поэтому опечатка, забытый префикс "
                            + "/api или не то число — это просто другой URL, и фронтенд получает вполне "
                            + "корректный ответ 404 на вопрос, который никто не обрабатывает",
                    List.of("response", "api"), state());
            finish();
            return;
        }

        Trace.event("ROUTE_MATCHED",
                "The server does not \"go to\" a URL — a router matches the text against its table and "
                        + "picks one handler: " + matchedRoute + " -> " + handlerName()
                        + ". Path variables and the body are parsed into Java objects here; this is where "
                        + "an annotated controller method finally gets called",
                "Сервер никуда не «переходит» по URL — маршрутизатор сопоставляет текст со своей таблицей "
                        + "и выбирает один обработчик: " + matchedRoute + " -> " + handlerName()
                        + ". Здесь же переменные пути и тело разбираются в объекты Java; именно тут наконец "
                        + "вызывается метод контроллера с аннотацией",
                List.of("api", "request"), state());

        if (needsToken() && !validToken(bearerToken())) {
            respond(401, "Unauthorized", "unauthorized",
                    "{\"error\":\"unauthorized\"}");
            Trace.event("AUTH_REJECTED",
                    "The server has no idea who is calling unless the request says so. "
                            + authProblemEn()
                            + " HTTP keeps nothing between calls, so identity is not something you \"are\" "
                            + "on the server — it is a header you attach to every single request",
                    "Сервер понятия не имеет, кто к нему обращается, пока об этом не сказано в запросе. "
                            + authProblemRu()
                            + " HTTP ничего не хранит между вызовами, поэтому личность — это не то, чем вы "
                            + "«являетесь» на сервере, а заголовок, который вы прикрепляете к каждому "
                            + "запросу",
                    List.of("response", "request"), state());
            finish();
            return;
        }

        switch (matchedRoute) {
            case "POST /api/sessions" -> handleSignIn();
            case "GET /api/employees" -> handleList();
            case "POST /api/employees" -> handleCreate();
            case "GET /api/employees/{id}" -> handleReadOne();
            case "DELETE /api/employees/{id}" -> handleDelete();
            case "GET /api/notifications" -> handleNotifications();
            default -> handlePayrollReport();
        }
        finish();
    }

    /** Prints what each side ended up holding after everything the example did. */
    public void report() {
        Trace.event("INTERACTION_AUDIT",
                "After " + roundTrips + " round trip(s): the server stores " + employees.size()
                        + " employee(s) and 0 sessions, the browser holds " + uiItems.size()
                        + " item(s) it parsed for itself" + (token == null ? " and no token" : " and a token")
                        + ". Failed calls: " + failedCalls + ", records created: " + created
                        + ", duplicates created by a retry: " + duplicates
                        + ", polls that returned nothing: " + polls + ", messages pushed by the server: "
                        + pushes,
                "После обменов (" + roundTrips + "): сервер хранит сотрудников: " + employees.size()
                        + " и сессий: 0, браузер держит " + uiItems.size()
                        + " элемент(ов), которые разобрал сам"
                        + (token == null ? ", токена нет" : ", и токен")
                        + ". Неудачных вызовов: " + failedCalls + ", создано записей: " + created
                        + ", дублей из-за повтора: " + duplicates + ", пустых опросов: " + polls
                        + ", сообщений, отправленных сервером: " + pushes,
                List.of(), state());
    }

    // ---------------------------------------------------------------- handlers

    private void handleSignIn() {
        String user = request.body.getOrDefault("user", "user");
        String issued = "jwt." + user + ".s1";
        respond(200, "OK", "ok", "{\"token\":\"" + issued + "\"}");
        token = issued;
        uiStatus = "ready";
        Trace.event("TOKEN_STORED",
                "The server checked the password ONCE and answered with a signed token. It did not "
                        + "remember anything: there is still no session on the server, nothing to look up, "
                        + "nothing to replicate between instances. The token now lives in the browser, and "
                        + "attaching it to every later request is the frontend's job",
                "Сервер один раз проверил пароль и ответил подписанным токеном. Он ничего не запомнил: "
                        + "сессии на сервере по-прежнему нет, искать нечего, реплицировать между "
                        + "экземплярами нечего. Токен теперь живёт в браузере, и прикреплять его к каждому "
                        + "следующему запросу — задача фронтенда",
                List.of("app", "api"), state());
    }

    private void handleList() {
        StringBuilder json = new StringBuilder("[");
        List<String> rendered = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : employees.entrySet()) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(listJson(entry.getKey(), entry.getValue()));
            rendered.add(summary(entry.getKey(), entry.getValue()));
        }
        json.append(']');
        respond(200, "OK", "ok", json.toString());
        uiItems.clear();
        uiItems.addAll(rendered);
        uiStatus = "ready";
        parsed = new LinkedHashMap<>();
    }

    private void handleCreate() {
        String key = request.headers.get("Idempotency-Key");
        if (key != null && idempotencyKeys.containsKey(key)) {
            String existingId = idempotencyKeys.get(key);
            respond(200, "OK", "ok", fullJson(existingId, employees.get(existingId)));
            parsed = parsedOf(existingId, employees.get(existingId));
            uiStatus = "ready";
            Trace.event("IDEMPOTENT_REPLAY",
                    "The server has seen Idempotency-Key \"" + key + "\" before, so it did NOT create a "
                            + "second record — it returned the result of the first one (id " + existingId
                            + "). The retry became safe because the CLIENT gave the write a name; the "
                            + "network cannot promise that a request is delivered exactly once, so "
                            + "somebody has to make a repeat harmless",
                    "Сервер уже видел Idempotency-Key \"" + key + "\", поэтому НЕ создал вторую запись, а "
                            + "вернул результат первой (id " + existingId + "). Повтор стал безопасным "
                            + "потому, что КЛИЕНТ дал записи имя: сеть не может гарантировать ровно одну "
                            + "доставку, поэтому кто-то должен сделать повтор безвредным",
                    List.of("api", "response"), state());
            return;
        }

        String name = request.body.get("name");
        if (name == null || name.isBlank()) {
            respond(400, "Bad Request", "bad-request",
                    "{\"errors\":[{\"field\":\"name\",\"code\":\"required\"}]}");
            uiStatus = "error";
            uiError = "http";
            uiErrorDetail = "400";
            Trace.event("VALIDATION_FAILED",
                    "The frontend already validated this form, and the server validates it AGAIN — the "
                            + "browser is a place an attacker fully controls, so client-side checks are a "
                            + "convenience, never a rule. Note the body: a machine-readable field and code, "
                            + "not an English sentence, so the frontend can put the message next to the "
                            + "right input in its own language",
                    "Фронтенд эту форму уже проверил, и сервер проверяет её СНОВА — браузер полностью "
                            + "контролируется тем, кто по ту сторону, поэтому проверки на клиенте это "
                            + "удобство, а не правило. Обратите внимание на тело: машиночитаемые поле и код, "
                            + "а не фраза на английском, — чтобы фронтенд показал сообщение рядом с нужным "
                            + "полем и на своём языке",
                    List.of("response", "request"), state());
            return;
        }

        boolean alreadyThere = false;
        String twinId = null;
        for (Map.Entry<String, Map<String, String>> entry : employees.entrySet()) {
            if (name.equals(entry.getValue().get("name"))) {
                alreadyThere = true;
                twinId = entry.getKey();
                break;
            }
        }

        String id = String.valueOf(nextId++);
        Map<String, String> stored = record(name,
                request.body.getOrDefault("role", "engineer"),
                request.body.getOrDefault("hiredAt", "2024-06-01"),
                request.body.getOrDefault("salary", "1000.00"),
                request.body.get("phone"));
        employees.put(id, stored);
        created++;
        if (key != null) {
            idempotencyKeys.put(key, id);
        }
        respond(201, "Created", "created", fullJson(id, stored));
        responseHeaders.put("Location", "/api/employees/" + id);
        parsed = parsedOf(id, stored);
        uiItems.add(summary(id, stored));
        uiStatus = "ready";

        Trace.event("RESOURCE_CREATED",
                "The handler wrote the row and answered 201 Created with Location: /api/employees/" + id
                        + ". The id was invented by the SERVER, so this response is the only way the "
                        + "frontend can ever learn it — which is why a create is a request/response "
                        + "exchange and not a fire-and-forget message",
                "Обработчик записал строку и ответил 201 Created с Location: /api/employees/" + id
                        + ". Идентификатор придумал СЕРВЕР, поэтому этот ответ — единственный способ для "
                        + "фронтенда его узнать; вот почему создание это обмен «запрос-ответ», а не "
                        + "отправка сообщения «в один конец»",
                List.of("response", "api"), state());

        if (alreadyThere) {
            duplicates++;
            Trace.event("DUPLICATE_CREATED",
                    "There is now a SECOND employee named \"" + name + "\" (id " + twinId + " and id " + id
                            + "). Nothing went wrong technically: the server was asked to create an "
                            + "employee twice, and it did. A retry after a failed call is indistinguishable "
                            + "from a genuine second request unless the request itself carries something "
                            + "that identifies it",
                    "Теперь есть ВТОРОЙ сотрудник по имени «" + name + "» (id " + twinId + " и id " + id
                            + "). Технически ничего не сломалось: сервера дважды попросили создать "
                            + "сотрудника — он дважды и создал. Повтор после неудачного вызова неотличим от "
                            + "настоящего второго запроса, пока сам запрос не несёт чего-то, что его "
                            + "опознаёт",
                    List.of("api"), state());
        }
    }

    private void handleReadOne() {
        String id = idFromPath();
        Map<String, String> found = employees.get(id);
        if (found == null) {
            respond(404, "Not Found", "not-found", "{\"error\":\"employee not found\",\"id\":\"" + id + "\"}");
            uiStatus = "error";
            uiError = "http";
            uiErrorDetail = "404";
            Trace.event("NOT_FOUND",
                    "The route matched — the server understood the question perfectly — and the answer is "
                            + "that there is no employee " + id + ". 404 is a normal, successful "
                            + "conversation about a thing that does not exist; it is not an error in the "
                            + "call itself",
                    "Маршрут совпал — сервер прекрасно понял вопрос, — и ответ в том, что сотрудника " + id
                            + " не существует. 404 это нормальный, состоявшийся разговор о вещи, которой "
                            + "нет, а не ошибка самого вызова",
                    List.of("response"), state());
            return;
        }
        respond(200, "OK", "ok", fullJson(id, found));
        parsed = parsedOf(id, found);
        uiItems.clear();
        uiItems.add(summary(id, found));
        uiStatus = "ready";
    }

    private void handleDelete() {
        String id = idFromPath();
        if (employees.remove(id) == null) {
            respond(404, "Not Found", "not-found", "{\"error\":\"employee not found\",\"id\":\"" + id + "\"}");
            uiStatus = "error";
            uiError = "http";
            uiErrorDetail = "404";
            return;
        }
        respond(204, "No Content", "no-content", "");
        uiItems.clear();
        for (Map.Entry<String, Map<String, String>> entry : employees.entrySet()) {
            uiItems.add(summary(entry.getKey(), entry.getValue()));
        }
        uiStatus = "ready";
        parsed = new LinkedHashMap<>();
    }

    private void handleNotifications() {
        respond(200, "OK", "ok", "[]");
        uiStatus = "ready";
    }

    private void handlePayrollReport() {
        respond(500, "Internal Server Error", "server-error",
                "{\"error\":\"internal\",\"traceId\":\"a41f\"}");
        uiStatus = "error";
        uiError = "http";
        uiErrorDetail = "500";
        Trace.event("SERVER_ERROR",
                "The handler threw, and the framework turned the exception into a 500 with a traceId — "
                        + "the stack trace stays in the server log where it belongs. From the frontend's "
                        + "side this is still a completed round trip: it got an answer, so it knows the "
                        + "request arrived. There is nothing it can fix; all it can offer is a retry",
                "Обработчик бросил исключение, и фреймворк превратил его в 500 с traceId — стектрейс "
                        + "остаётся в логе сервера, где ему и место. Со стороны фронтенда это всё равно "
                        + "состоявшийся обмен: ответ получен, значит запрос дошёл. Исправить тут ничего "
                        + "нельзя, можно только предложить повтор",
                List.of("response", "api"), state());
    }

    // ----------------------------------------------------------- other actions

    /** Cuts the connection: the next calls get no response at all. */
    public void networkDown() {
        networkUp = false;
        Trace.event("NETWORK_CHANGED",
                "The connection is gone (a dropped Wi-Fi, a proxy, a dead pod). Nothing about either "
                        + "program changed — the frontend still runs, the server still runs; only the wire "
                        + "between them is missing, which is exactly the failure a local method call cannot "
                        + "have",
                "Соединения нет (пропал Wi-Fi, прокси, упавший под). Ни в одной из программ ничего не "
                        + "изменилось: фронтенд работает, сервер работает; исчез только провод между ними — "
                        + "именно та поломка, которой не бывает у локального вызова метода",
                List.of("network"), state());
    }

    /**
     * The next call is delivered and executed normally — only its answer is lost
     * on the way back. This is the failure that makes retrying a write hard.
     */
    public void dropNextResponse() {
        dropResponse = true;
        Trace.event("NETWORK_CHANGED",
                "The next answer will be lost on its way back: the request arrives, the handler runs, "
                        + "and the response never reaches the browser. From the frontend this looks exactly "
                        + "like a request that never arrived",
                "Следующий ответ потеряется на обратном пути: запрос дойдёт, обработчик отработает, а "
                        + "ответ до браузера не доберётся. Со стороны фронтенда это выглядит ровно так же, "
                        + "как запрос, который вообще не дошёл",
                List.of("network"), state());
    }

    /** The connection is back. */
    public void networkUp() {
        networkUp = true;
        Trace.event("NETWORK_CHANGED",
                "The connection is back. Every call from here on can reach the server again",
                "Соединение восстановлено. Все дальнейшие вызовы снова могут дойти до сервера",
                List.of("network"), state());
    }

    /** The user presses F5: the tab's JavaScript memory is thrown away and rebuilt. */
    public void reloadPage() {
        uiItems.clear();
        parsed = new LinkedHashMap<>();
        uiStatus = "idle";
        uiError = null;
        uiErrorDetail = "";
        socketOpen = false;
        lastPush = null;
        Trace.event("PAGE_RELOADED",
                "F5. Everything the frontend had parsed into memory is gone and the app starts empty "
                        + "again — it will have to re-request all of it. The server did not notice and had "
                        + "nothing to lose: it never kept anything about this user. The only reason the "
                        + "session survives is that the token was saved in the browser's own storage",
                "F5. Всё, что фронтенд разобрал в память, исчезло, и приложение снова стартует пустым — "
                        + "запрашивать придётся заново. Сервер этого не заметил, и терять ему было нечего: "
                        + "он никогда ничего про этого пользователя не хранил. Сессия уцелела только "
                        + "потому, что токен сохранён в хранилище самого браузера",
                List.of("app"), state());
    }

    /**
     * Shows what actually crossed the wire on the last single-record response:
     * a Java object on one side, a plain JavaScript object on the other, and
     * nothing but text in between.
     */
    public void inspectPayload() {
        if (parsed.isEmpty()) {
            Trace.event("PAYLOAD_INSPECTED",
                    "There is no record to inspect: the last call did not return a single object",
                    "Разбирать нечего: последний вызов не вернул одиночный объект",
                    List.of(), state());
            return;
        }
        payload = new ArrayList<>();
        for (Map.Entry<String, String> field : parsed.entrySet()) {
            String[] types = WIRE_TYPES.getOrDefault(field.getKey(),
                    new String[]{"String", "string", "string", ""});
            payload.add(new String[]{field.getKey(), types[0], types[1], types[2], types[3]});
        }
        Trace.event("PAYLOAD_INSPECTED",
                "No object was sent — an object cannot leave a JVM. Jackson SERIALIZED the entity into "
                        + "text, the browser parsed that text into a brand-new plain JavaScript object, and "
                        + "the two are related only by convention: the date became a string because JSON "
                        + "has no date type, the BigDecimal became a double-precision number, and "
                        + "phone: null means \"known to be empty\" while a MISSING key means \"the server "
                        + "never mentioned it\". Nothing on the browser side has any methods",
                "Никакой объект не отправлялся — объект не может покинуть JVM. Jackson СЕРИАЛИЗОВАЛ "
                        + "сущность в текст, браузер разобрал этот текст в совершенно новый обычный "
                        + "JavaScript-объект, и связывает их только договорённость: дата стала строкой, "
                        + "потому что в JSON нет типа даты, BigDecimal стал числом двойной точности, а "
                        + "phone: null означает «известно, что пусто», тогда как ОТСУТСТВУЮЩИЙ ключ "
                        + "означает «сервер о нём не сказал». Никаких методов на стороне браузера нет",
                List.of("response", "app"), state());
    }

    /** The backend team renames a field in the response — a contract change. */
    public void renameField(String from, String to) {
        for (Map.Entry<String, Map<String, String>> entry : employees.entrySet()) {
            Map<String, String> fields = entry.getValue();
            if (!fields.containsKey(from)) {
                continue;
            }
            Map<String, String> renamed = new LinkedHashMap<>();
            for (Map.Entry<String, String> field : fields.entrySet()) {
                renamed.put(field.getKey().equals(from) ? to : field.getKey(), field.getValue());
            }
            fields.clear();
            fields.putAll(renamed);
        }
        Trace.event("CONTRACT_CHANGED",
                "A new backend version is deployed: the field \"" + from + "\" is now called \"" + to
                        + "\". Every test on the server still passes — the change is invisible from inside "
                        + "one process. What broke is the AGREEMENT with a program the deploy never "
                        + "touched, and which is already running in somebody's browser",
                "Задеплоена новая версия бэкенда: поле «" + from + "» теперь называется «" + to
                        + "». Все тесты на сервере по-прежнему зелёные — изнутри одного процесса такое "
                        + "изменение незаметно. Сломалась ДОГОВОРЁННОСТЬ с программой, которую деплой не "
                        + "трогал и которая уже работает у кого-то в браузере",
                List.of("api"), state());
    }

    /** The already-deployed frontend reads a field out of the parsed response. */
    public void readField(String field) {
        if (parsed.containsKey(field)) {
            Trace.event("FIELD_READ",
                    "employee." + field + " is \"" + parsed.get(field) + "\" — the frontend and the "
                            + "backend agree on this name, which is the entire contract between them",
                    "employee." + field + " равно «" + parsed.get(field) + "» — фронтенд и бэкенд "
                            + "договорились об этом имени, и вся их договорённость в этом и состоит",
                    List.of("app"), state());
            return;
        }
        Trace.event("FIELD_MISSING",
                "employee." + field + " is undefined, and JavaScript did not throw: reading a property "
                        + "that is not there is legal. The call returned 200 OK, the server log is clean, "
                        + "the monitoring is green — and the screen says \"undefined\". This is why the "
                        + "response shape is a published contract: add fields freely, rename or remove them "
                        + "only with a version or a deprecation window",
                "employee." + field + " равно undefined, и JavaScript не упал: читать отсутствующее "
                        + "свойство — законно. Вызов вернул 200 OK, лог сервера чист, мониторинг зелёный — "
                        + "а на экране «undefined». Поэтому форма ответа это опубликованный контракт: поля "
                        + "можно свободно добавлять, а переименовывать и удалять — только с версией или "
                        + "периодом устаревания",
                List.of("app", "response"), state());
    }

    /**
     * Asks the server for news {@code times} times in a row. Each attempt is a
     * full round trip, reported as one event to keep the log readable.
     */
    public void pollForNews(int times) {
        for (int i = 1; i <= times; i++) {
            begin(Request.get("/api/notifications"));
            polls++;
            matchedRoute = "GET /api/notifications";
            respond(200, "OK", "ok", "[]");
            uiStatus = "ready";
            Trace.event("POLL_EMPTY",
                    "Poll " + i + " of " + times + ": GET /api/notifications -> 200 []. The client asks, "
                            + "the server answers \"nothing yet\", and both sides paid for a full round trip "
                            + "to establish that. Polling is the honest workaround for a protocol where only "
                            + "the client may start a conversation",
                    "Опрос " + i + " из " + times + ": GET /api/notifications -> 200 []. Клиент спросил, "
                            + "сервер ответил «пока ничего», и обе стороны заплатили за полный обмен, чтобы "
                            + "это выяснить. Опрос — честный обходной путь для протокола, в котором "
                            + "разговор может начать только клиент",
                    List.of("request", "response"), state());
        }
    }

    /** Upgrades the connection to a WebSocket, so the server can speak first. */
    public void openSocket() {
        begin(Request.get("/ws/notifications").header("Upgrade", "websocket"));
        matchedRoute = "GET /ws/notifications";
        respond(101, "Switching Protocols", "ok", "");
        socketOpen = true;
        uiStatus = "ready";
        Trace.event("SOCKET_OPENED",
                "The client starts the exchange one last time: an HTTP request with Upgrade: websocket, "
                        + "answered with 101 Switching Protocols. From now on the same TCP connection stays "
                        + "open and EITHER side may send a frame — which is the only way the backend ever "
                        + "gets to speak first",
                "Клиент в последний раз начинает обмен: HTTP-запрос с Upgrade: websocket, ответ — 101 "
                        + "Switching Protocols. Дальше то же TCP-соединение остаётся открытым, и кадр может "
                        + "отправить ЛЮБАЯ из сторон, — только так бэкенд вообще получает возможность "
                        + "заговорить первым",
                List.of("app", "api"), state());
    }

    /** The backend tries to tell the browser something on its own initiative. */
    public void pushFromServer(String message) {
        if (!socketOpen) {
            Trace.event("PUSH_IMPOSSIBLE",
                    "The server has news (\"" + message + "\") and no way to deliver it. A browser tab is "
                            + "not a server: it has no address to be called back on, and the HTTP "
                            + "connection is closed the moment a response is finished. Until the client "
                            + "asks again, the news simply waits",
                    "У сервера есть новость («" + message + "») и нет способа её доставить. Вкладка "
                            + "браузера — не сервер: у неё нет адреса, по которому можно перезвонить, а "
                            + "HTTP-соединение закрывается сразу после ответа. Пока клиент не спросит "
                            + "снова, новость просто ждёт",
                    List.of("api"), state());
            return;
        }
        pushes++;
        lastPush = message;
        uiItems.add("push: " + message);
        uiStatus = "ready";
        Trace.event("SERVER_PUSHED",
                "The server sent \"" + message + "\" without being asked, and the frontend updated the "
                        + "screen from an event instead of from a response it was waiting for. The "
                        + "trade-off is that the connection is now stateful: it belongs to one server "
                        + "instance, it has to be re-established after a reload, and a load balancer has to "
                        + "know about it",
                "Сервер отправил «" + message + "», ни о чём его не спрашивали, и фронтенд обновил экран "
                        + "по событию, а не по ответу, которого ждал. Плата за это — соединение стало "
                        + "состоянием: оно принадлежит конкретному экземпляру сервера, его нужно "
                        + "восстанавливать после перезагрузки страницы, и о нём должен знать балансировщик",
                List.of("app", "api"), state());
    }

    // --------------------------------------------------------------- internals

    private void begin(Request call) {
        roundTrips++;
        request = call;
        if (call.useStoredToken && token != null) {
            call.headers.put("Authorization", "Bearer " + token);
        }
        matchedRoute = null;
        status = 0;
        statusText = null;
        responseKind = null;
        responseHeaders = new LinkedHashMap<>();
        responseBody = null;
        hasResponse = false;
        failure = null;
        payload = new ArrayList<>();
        uiItemsBefore = new ArrayList<>(uiItems);
        parsedBefore = new LinkedHashMap<>(parsed);
        tokenBefore = token;
        uiStatus = "loading";
        uiError = null;
        uiErrorDetail = "";
    }

    private void respond(int code, String reason, String kind, String body) {
        status = code;
        statusText = reason;
        responseKind = kind;
        responseBody = body;
        hasResponse = true;
        responseHeaders = new LinkedHashMap<>();
        if (body != null && (body.startsWith("{") || body.startsWith("["))) {
            responseHeaders.put("Content-Type", "application/json");
        }
    }

    /** Emits the response and the re-render that ends every completed round trip. */
    private void finish() {
        if (dropResponse) {
            dropResponse = false;
            failedCalls++;
            failure = "response-lost";
            hasResponse = false;
            uiItems.clear();
            uiItems.addAll(uiItemsBefore);
            parsed = new LinkedHashMap<>(parsedBefore);
            token = tokenBefore;
            uiStatus = "error";
            uiError = "network";
            uiErrorDetail = "";
            Trace.event("RESPONSE_LOST",
                    "The handler ran and the server state already changed — and then the answer was lost "
                            + "on the way back. The frontend sees the same thing as a request that never "
                            + "arrived: a rejected promise and no status code. This is the case that makes "
                            + "\"just retry it\" a decision rather than a reflex",
                    "Обработчик отработал, состояние сервера уже изменилось — и после этого ответ "
                            + "потерялся на обратном пути. Фронтенд видит то же самое, что и при запросе, "
                            + "который вообще не дошёл: отклонённый промис и никакого кода состояния. "
                            + "Именно из-за этого случая «просто повтори» — решение, а не рефлекс",
                    List.of("network", "app"), state());
            return;
        }

        Trace.event("RESPONSE_SENT",
                "The answer travels back as text too: " + status + " " + statusText
                        + describeResponseHeadersEn() + describeBodyTextEn()
                        + ". The status code is the part machines act on — the frontend branches on the "
                        + "NUMBER, never on the wording of the message",
                "Ответ возвращается тоже текстом: " + status + " " + statusText
                        + describeResponseHeadersRu() + describeBodyTextRu()
                        + ". Код состояния — это то, на что реагируют программы: фронтенд ветвится по "
                        + "ЧИСЛУ, а не по формулировке сообщения",
                List.of("response"), state());

        Trace.event("UI_UPDATED",
                uiUpdateEn(), uiUpdateRu(),
                List.of("app"), state());
    }

    private String uiUpdateEn() {
        return switch (responseKind == null ? "" : responseKind) {
            case "ok", "created" -> "JSON.parse turns the text into a plain JavaScript value, the store "
                    + "keeps a COPY of it, and the component re-renders: " + screenEn()
                    + ". From here the two sides hold separate state that only agreed at this instant";
            case "no-content" -> "204 has no body to parse. The frontend removes the row from its own "
                    + "copy of the data — the server's state and the screen are two different things kept "
                    + "in step by hand: " + screenEn();
            case "bad-request" -> "The frontend reads the field and code out of the body and puts the "
                    + "message next to the input the user has to fix. A 4xx is not a crash; it is an "
                    + "answer the UI is expected to handle";
            case "unauthorized" -> "The frontend treats 401 as \"sign in again\": it drops the token it "
                    + "was holding and routes to the login screen. No retry can help — the request was "
                    + "understood and refused";
            case "not-found" -> "The frontend renders an empty state, not an error dialog: 404 says the "
                    + "thing is absent, which is a normal thing for a screen to show";
            default -> "The frontend shows a failure it cannot resolve and offers a retry. A 5xx is the "
                    + "server's problem, so the sensible response is to back off and try again";
        };
    }

    private String uiUpdateRu() {
        return switch (responseKind == null ? "" : responseKind) {
            case "ok", "created" -> "JSON.parse превращает текст в обычное значение JavaScript, хранилище "
                    + "держит его КОПИЮ, и компонент перерисовывается: " + screenRu()
                    + ". Дальше у сторон отдельные состояния, совпавшие только в это мгновение";
            case "no-content" -> "У 204 нет тела, разбирать нечего. Фронтенд удаляет строку из своей "
                    + "копии данных — состояние сервера и экран это две разные вещи, которые "
                    + "синхронизируют вручную: " + screenRu();
            case "bad-request" -> "Фронтенд достаёт из тела поле и код и показывает сообщение рядом с тем "
                    + "полем, которое нужно исправить. 4xx — это не падение, а ответ, который интерфейс "
                    + "обязан обработать";
            case "unauthorized" -> "401 фронтенд трактует как «войдите заново»: он выбрасывает токен, "
                    + "который держал, и уводит на экран входа. Повтор не поможет — запрос поняли и "
                    + "отклонили";
            case "not-found" -> "Фронтенд рисует пустое состояние, а не диалог ошибки: 404 говорит, что "
                    + "вещи нет, и показать это экрану совершенно нормально";
            default -> "Фронтенд показывает сбой, который сам исправить не может, и предлагает повтор. "
                    + "5xx — проблема сервера, поэтому разумно подождать и попробовать ещё раз";
        };
    }

    private String screenEn() {
        if (uiItems.isEmpty()) {
            return "an empty screen";
        }
        return uiItems.size() + " row(s): " + String.join(", ", uiItems);
    }

    private String screenRu() {
        if (uiItems.isEmpty()) {
            return "пустой экран";
        }
        return "строк: " + uiItems.size() + " — " + String.join(", ", uiItems);
    }

    private boolean needsToken() {
        return WRITE_METHODS.contains(request.method) && !"POST /api/sessions".equals(matchedRoute);
    }

    private String bearerToken() {
        String header = request.headers.get("Authorization");
        return header == null || !header.startsWith("Bearer ") ? null : header.substring(7);
    }

    private static boolean validToken(String value) {
        return value != null && value.startsWith("jwt.") && !value.endsWith(".expired");
    }

    private String authProblemEn() {
        String value = bearerToken();
        if (value == null) {
            return "This request carries no Authorization header at all, so the answer is 401 — not "
                    + "because the server forgot who signed in, but because it never knew.";
        }
        return "The Authorization header carries \"" + value + "\", which the server refuses (an expired "
                + "or unsigned token is worth exactly nothing), so the answer is 401.";
    }

    private String authProblemRu() {
        String value = bearerToken();
        if (value == null) {
            return "В этом запросе вообще нет заголовка Authorization, поэтому ответ 401 — не потому, что "
                    + "сервер забыл, кто входил, а потому, что он этого никогда и не знал.";
        }
        return "В заголовке Authorization лежит «" + value + "», и сервер его отвергает (просроченный или "
                + "неподписанный токен не стоит ровно ничего), поэтому ответ 401.";
    }

    private String handlerName() {
        return switch (matchedRoute) {
            case "POST /api/sessions" -> "SessionController.signIn(credentials)";
            case "GET /api/employees" -> "EmployeeController.list()";
            case "POST /api/employees" -> "EmployeeController.create(body)";
            case "GET /api/employees/{id}" -> "EmployeeController.getOne(" + idFromPath() + ")";
            case "DELETE /api/employees/{id}" -> "EmployeeController.delete(" + idFromPath() + ")";
            case "GET /api/notifications" -> "NotificationController.list()";
            default -> "ReportController.payroll()";
        };
    }

    private String idFromPath() {
        String path = pathOnly(request.path);
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String pathOnly(String path) {
        int question = path.indexOf('?');
        return question < 0 ? path : path.substring(0, question);
    }

    private String matchRoute(String method, String path) {
        String p = pathOnly(path);
        if (p.equals("/api/sessions")) {
            return "POST".equals(method) ? "POST /api/sessions" : null;
        }
        if (p.equals("/api/employees")) {
            if ("GET".equals(method)) {
                return "GET /api/employees";
            }
            return "POST".equals(method) ? "POST /api/employees" : null;
        }
        if (p.startsWith("/api/employees/") && p.indexOf('/', "/api/employees/".length()) < 0) {
            if ("GET".equals(method)) {
                return "GET /api/employees/{id}";
            }
            return "DELETE".equals(method) ? "DELETE /api/employees/{id}" : null;
        }
        if (p.equals("/api/notifications") && "GET".equals(method)) {
            return "GET /api/notifications";
        }
        if (p.equals("/api/reports/payroll") && "GET".equals(method)) {
            return "GET /api/reports/payroll";
        }
        return null;
    }

    // ------------------------------------------------------------ json / text

    private String requestLine() {
        return request.method + " " + request.path + " HTTP/1.1";
    }

    private String describeHeadersEn() {
        return request.headers.isEmpty() ? ", no headers" : ", headers " + describe(request.headers);
    }

    private String describeHeadersRu() {
        return request.headers.isEmpty() ? ", без заголовков" : ", заголовки " + describe(request.headers);
    }

    private String describeBodyEn() {
        return request.body.isEmpty() ? ", no body" : ", body " + bodyJson();
    }

    private String describeBodyRu() {
        return request.body.isEmpty() ? ", без тела" : ", тело " + bodyJson();
    }

    private String describeResponseHeadersEn() {
        return responseHeaders.isEmpty() ? "" : ", " + describe(responseHeaders);
    }

    private String describeResponseHeadersRu() {
        return responseHeaders.isEmpty() ? "" : ", " + describe(responseHeaders);
    }

    private String describeBodyTextEn() {
        return responseBody == null || responseBody.isEmpty() ? ", empty body" : ", body " + responseBody;
    }

    private String describeBodyTextRu() {
        return responseBody == null || responseBody.isEmpty() ? ", тело пустое" : ", тело " + responseBody;
    }

    private String bodyJson() {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, String> field : request.body.entrySet()) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(field.getKey()).append("\":").append(jsonValue(field.getKey(), field.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String jsonValue(String field, String value) {
        if (value == null) {
            return "null";
        }
        return NUMERIC_FIELDS.contains(field) ? value : "\"" + value + "\"";
    }

    /** The full representation of one employee, as the single-item endpoint returns it. */
    private static String fullJson(String id, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{\"id\":").append(id);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            sb.append(",\"").append(field.getKey()).append("\":")
                    .append(jsonValue(field.getKey(), field.getValue()));
        }
        return sb.append('}').toString();
    }

    /** The smaller shape the list endpoint returns: id plus the first two fields. */
    private static String listJson(String id, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{\"id\":").append(id);
        int taken = 0;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (taken++ == 2) {
                break;
            }
            sb.append(",\"").append(field.getKey()).append("\":")
                    .append(jsonValue(field.getKey(), field.getValue()));
        }
        return sb.append('}').toString();
    }

    /** What the frontend holds after parsing a single-record response. */
    private static Map<String, String> parsedOf(String id, Map<String, String> fields) {
        Map<String, String> copy = new LinkedHashMap<>();
        copy.put("id", id);
        copy.putAll(fields);
        return copy;
    }

    private static String summary(String id, Map<String, String> fields) {
        List<String> values = new ArrayList<>(fields.values());
        String first = values.isEmpty() ? "?" : values.get(0);
        String second = values.size() < 2 ? "" : " · " + values.get(1);
        return "#" + id + " " + first + second;
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

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", appName);
        app.put("status", uiStatus);
        app.put("items", new ArrayList<>(uiItems));
        app.put("token", token);
        app.put("error", uiError);
        app.put("errorDetail", uiErrorDetail);
        app.put("socket", socketOpen);
        app.put("lastPush", lastPush);
        s.put("app", app);

        Map<String, Object> api = new LinkedHashMap<>();
        api.put("name", apiName);
        api.put("sessions", 0);
        api.put("routes", new ArrayList<>(ROUTES));
        api.put("matchedRoute", matchedRoute);
        List<Object> store = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : employees.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getKey());
            row.put("label", summary(entry.getKey(), entry.getValue()));
            store.add(row);
        }
        api.put("store", store);
        s.put("api", api);

        s.put("network", networkUp);

        if (request != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("method", request.method);
            r.put("path", request.path);
            r.put("headers", headerList(request.headers));
            List<Object> body = new ArrayList<>();
            for (Map.Entry<String, String> field : request.body.entrySet()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("field", field.getKey());
                entry.put("value", field.getValue());
                body.add(entry);
            }
            r.put("body", body);
            s.put("request", r);
        }

        if (hasResponse) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", status);
            r.put("statusText", statusText);
            r.put("kind", responseKind);
            r.put("headers", headerList(responseHeaders));
            r.put("body", responseBody);
            s.put("response", r);
        }

        s.put("failure", failure);

        List<Object> fields = new ArrayList<>();
        for (String[] field : payload) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", field[0]);
            entry.put("java", field[1]);
            entry.put("json", field[2]);
            entry.put("js", field[3]);
            entry.put("note", field[4]);
            fields.add(entry);
        }
        s.put("payload", fields);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("roundTrips", roundTrips);
        stats.put("failed", failedCalls);
        stats.put("created", created);
        stats.put("duplicates", duplicates);
        stats.put("polls", polls);
        stats.put("pushes", pushes);
        s.put("stats", stats);
        return s;
    }

    private static List<Object> headerList(Map<String, String> headers) {
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", header.getKey());
            entry.put("value", header.getValue());
            out.add(entry);
        }
        return out;
    }
}
