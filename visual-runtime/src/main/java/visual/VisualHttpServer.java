package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of one small HTTP server and of the methods a client
 * can aim at it.
 *
 * <p>HTTP is a stateless request/response protocol: the client sends a method, a
 * path and headers (optionally a body), the server answers with a status line,
 * headers and an optional body. What separates the methods is not the wire format
 * — it is identical — but the promise each one makes:
 * <ul>
 *   <li><b>safe</b> — the request is not supposed to change server state
 *       ({@code GET}, {@code HEAD}, {@code OPTIONS});</li>
 *   <li><b>idempotent</b> — N identical requests leave the same state as one
 *       ({@code GET}, {@code HEAD}, {@code OPTIONS}, {@code PUT},
 *       {@code DELETE});</li>
 *   <li><b>cacheable</b> — the response may be reused for a later request
 *       ({@code GET}, {@code HEAD}).</li>
 * </ul>
 *
 * <p>The model owns one collection ({@code /orders}) and the item resources under
 * it. Each call applies the rules of the method that carried it and then states a
 * verdict — {@link #safeRead} for "server state untouched", a state change, or an
 * idempotent repeat that changed nothing the second time. The interesting cases
 * are the ones examples can reproduce: {@code POST} sent twice creating two
 * resources, {@code DELETE} sent twice answering differently but ending in the
 * same state, a wrong method answered with {@code 405} plus an {@code Allow}
 * header, and a {@code GET} whose handler mutates — the one bug the entire
 * caching layer of the web is unprepared for.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free.
 */
public class VisualHttpServer {

    /** An ordered representation or request body, in the order the example wrote it. */
    public static final class Body {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private Body() {
        }

        /** Starts a body with one field. */
        public static Body of(String field, String value) {
            return new Body().and(field, value);
        }

        /** Adds another field. */
        public Body and(String field, String value) {
            fields.put(field, value);
            return this;
        }
    }

    private static final List<String> COLLECTION_METHODS = List.of("GET", "HEAD", "POST", "OPTIONS");
    private static final List<String> ITEM_METHODS =
            List.of("GET", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS");

    private final String base;
    /** Stored resources: path -> representation, in a stable display order. */
    private final Map<String, Map<String, String>> store = new LinkedHashMap<>();
    /** How the current request touched each surviving resource: created|updated|kept. */
    private final Map<String, String> marks = new LinkedHashMap<>();
    /** Resources the current request removed. */
    private final List<String> gone = new ArrayList<>();
    /** Paths the client holds a fresh cached response for. */
    private final List<String> cached = new ArrayList<>();
    /** Bodies already POSTed to a collection, so a repeated POST is recognizable. */
    private final List<String> postedBodies = new ArrayList<>();

    private int nextId = 1;
    private int requests;
    private int mutations;
    private int created;
    private int unsafeReads;
    /** Signature of the previous request, so an identical repeat is recognizable. */
    private String previousSignature = "";

    private String method;
    private String path;
    /** The resource the current request acted on, which is not always its path. */
    private String touched;
    private Map<String, String> requestBody;
    private String status;
    private String location;
    private List<String> allow = List.of();
    private boolean fromCache;
    private boolean hasResponseBody;
    private boolean applied;
    private boolean exchangeStarted;
    /** Snapshot taken before the current request, to tell whether state moved. */
    private Map<String, Map<String, String>> before = Map.of();

    private VisualHttpServer(String base) {
        this.base = base;
    }

    /**
     * A server exposing {@code base} and already holding the given representations
     * at {@code base + "/1"}, {@code base + "/2"}, and so on.
     */
    public static VisualHttpServer serving(String base, Body... resources) {
        VisualHttpServer server = new VisualHttpServer(base);
        for (Body body : resources) {
            server.store.put(base + "/" + server.nextId++, new LinkedHashMap<>(body.fields));
        }
        Trace.event("SERVER_READY",
                "HTTP is a stateless request/response protocol: the client sends a method + path + "
                        + "headers (and maybe a body), the server answers with a status line + headers "
                        + "(and maybe a body). This server exposes " + base + " and holds "
                        + server.store.size() + " resource(s): " + server.paths(),
                "HTTP — это протокол «запрос-ответ» без состояния: клиент отправляет метод + путь + "
                        + "заголовки (и, возможно, тело), сервер отвечает строкой статуса + заголовками "
                        + "(и, возможно, телом). Этот сервер отдаёт " + base + " и хранит ресурсов: "
                        + server.store.size() + " — " + server.paths(),
                List.of(), server.state());
        return server;
    }

    // ------------------------------------------------------------ safe methods

    /** GET: "send me the representation of this URL", changing nothing. */
    public void get(String target) {
        begin("GET", target, null);
        if (missing(target)) {
            notFound();
            end();
            return;
        }
        if (cached.contains(target)) {
            fromCache = true;
            status = "200 OK";
            hasResponseBody = true;
            Trace.event("CACHE_HIT",
                    "The client already holds a fresh copy of " + target + " and answers this GET from "
                            + "its own cache — the request never reaches the server. Only a response to "
                            + "GET or HEAD may be reused like this by default",
                    "У клиента уже есть свежая копия " + target + ", и он отвечает на этот GET из "
                            + "своего кэша — запрос вообще не доходит до сервера. По умолчанию так "
                            + "переиспользовать можно только ответ на GET или HEAD",
                    highlights(target), state());
        } else {
            status = "200 OK";
            hasResponseBody = true;
            cached.add(target);
            Trace.event("RESPONSE_RECEIVED",
                    "200 OK, Content-Type: application/json, body: " + describeTarget(target)
                            + ". A response to GET is cacheable by default, so the client keeps a copy",
                    "200 OK, Content-Type: application/json, тело: " + describeTarget(target)
                            + ". Ответ на GET по умолчанию кэшируем, поэтому клиент сохраняет копию",
                    highlights(target), state());
        }
        end();
    }

    /** HEAD: exactly the GET response, headers only — no body is transferred. */
    public void head(String target) {
        begin("HEAD", target, null);
        if (missing(target)) {
            notFound();
            end();
            return;
        }
        status = "200 OK";
        hasResponseBody = false;
        Trace.event("HEAD_NO_BODY",
                "200 OK with exactly the headers a GET would return (Content-Type, Content-Length, "
                        + "ETag) and NO body. HEAD is how a client checks whether something exists, how "
                        + "big it is or whether its copy is stale, without downloading it",
                "200 OK ровно с теми заголовками, которые вернул бы GET (Content-Type, Content-Length, "
                        + "ETag), и БЕЗ тела. HEAD нужен, чтобы проверить, существует ли ресурс, каков "
                        + "его размер и не устарела ли копия, ничего не скачивая",
                highlights(target), state());
        end();
    }

    /** OPTIONS: "what can I do with this URL?" — answered with an Allow header. */
    public void options(String target) {
        begin("OPTIONS", target, null);
        allow = allowedFor(target);
        status = "204 No Content";
        hasResponseBody = false;
        Trace.event("OPTIONS_ALLOW",
                "204 No Content, Allow: " + String.join(", ", allow) + " — OPTIONS asks what this URL "
                        + "supports instead of touching it. A browser sends it by itself as a CORS "
                        + "preflight before a cross-origin write",
                "204 No Content, Allow: " + String.join(", ", allow) + " — OPTIONS спрашивает, что "
                        + "поддерживает этот URL, вместо того чтобы его трогать. Браузер сам шлёт такой "
                        + "запрос как CORS-preflight перед кросс-origin записью",
                highlights(target), state());
        end();
    }

    // ---------------------------------------------------------- unsafe methods

    /** POST: "process this body under this collection" — the server picks the new URL. */
    public void post(String target, Body body) {
        begin("POST", target, body);
        if (methodNotAllowed(target)) {
            return;
        }
        String bodySignature = target + "|" + describe(requestBody);
        boolean repeatedBody = postedBodies.contains(bodySignature);
        postedBodies.add(bodySignature);

        String createdPath = target + "/" + nextId++;
        touched = createdPath;
        store.put(createdPath, new LinkedHashMap<>(requestBody));
        marks.put(createdPath, "created");
        invalidateCache();
        created++;
        location = createdPath;
        status = "201 Created";
        hasResponseBody = true;
        applied = true;

        Trace.event("RESOURCE_CREATED",
                "201 Created, Location: " + createdPath + " — POST asked the collection to create a "
                        + "subordinate resource, and the SERVER chose its URL. That is why the client "
                        + "cannot know in advance what it is creating",
                "201 Created, Location: " + createdPath + " — POST попросил коллекцию создать "
                        + "подчинённый ресурс, и URL выбрал СЕРВЕР. Именно поэтому клиент заранее не "
                        + "знает, что именно он создаёт",
                highlights(createdPath), state());

        if (repeatedBody) {
            Trace.event("DUPLICATE_CREATED",
                    "The identical body was POSTed a SECOND time and produced a SECOND, independent "
                            + "resource — " + created + " now exist from the same request. POST is not "
                            + "idempotent: this is the double-order bug behind every \"do not press the "
                            + "button twice\" banner, and the reason retries need an idempotency key",
                    "То же самое тело отправили POST-ом ВТОРОЙ раз, и появился ВТОРОЙ независимый "
                            + "ресурс — уже " + created + " из одного и того же запроса. POST не "
                            + "идемпотентен: это тот самый баг двойного заказа за каждым баннером «не "
                            + "нажимайте кнопку дважды» и причина, по которой повторам нужен ключ "
                            + "идемпотентности",
                    highlights(createdPath), state());
        }
        end();
    }

    /** PUT: "let this URL hold exactly this representation" — the client names the URL. */
    public void put(String target, Body body) {
        begin("PUT", target, body);
        if (methodNotAllowed(target)) {
            return;
        }
        boolean existed = store.containsKey(target);
        store.put(target, new LinkedHashMap<>(requestBody));
        marks.put(target, existed ? "updated" : "created");
        invalidateCache();
        applied = true;
        hasResponseBody = true;

        if (existed) {
            status = "200 OK";
            Trace.event("RESOURCE_UPDATED",
                    "200 OK — PUT replaced the WHOLE representation at " + target + " with the body: "
                            + describeTarget(target) + ". Send the same body again and the resource "
                            + "lands in the same place, which is what makes PUT idempotent",
                    "200 OK — PUT заменил представление по адресу " + target + " ЦЕЛИКОМ телом запроса: "
                            + describeTarget(target) + ". Отправьте то же тело ещё раз, и ресурс "
                            + "окажется в том же состоянии — именно это делает PUT идемпотентным",
                    highlights(target), state());
        } else {
            created++;
            location = target;
            status = "201 Created";
            Trace.event("RESOURCE_CREATED",
                    "201 Created — PUT names the URL itself (\"let " + target + " hold exactly this\"), "
                            + "and nothing was stored there, so the server created it. POST could not "
                            + "have done this: it would have invented a different URL",
                    "201 Created — PUT сам называет URL («пусть по " + target + " лежит именно это»), а "
                            + "там ничего не было, поэтому сервер создал ресурс. POST так не смог бы: он "
                            + "придумал бы другой URL",
                    highlights(target), state());
        }
        end();
    }

    /** PATCH: "apply this change" — only the named fields are merged. */
    public void patch(String target, Body body) {
        begin("PATCH", target, body);
        if (methodNotAllowed(target)) {
            return;
        }
        if (missing(target)) {
            notFound();
            end();
            return;
        }
        Map<String, String> representation = store.get(target);
        representation.putAll(requestBody);
        marks.put(target, "updated");
        invalidateCache();
        applied = true;
        status = "200 OK";
        hasResponseBody = true;

        Trace.event("RESOURCE_UPDATED",
                "200 OK — PATCH carried ONLY the change (" + describe(requestBody) + ") and the server "
                        + "merged it into " + target + ": " + describeTarget(target) + ". Fields the "
                        + "body never mentioned kept their values",
                "200 OK — PATCH принёс ТОЛЬКО изменение (" + describe(requestBody) + "), и сервер влил "
                        + "его в " + target + ": " + describeTarget(target) + ". Поля, о которых тело не "
                        + "упоминало, сохранили свои значения",
                highlights(target), state());
        end();
    }

    /** DELETE: "remove the resource at this URL". */
    public void delete(String target) {
        begin("DELETE", target, null);
        if (methodNotAllowed(target)) {
            return;
        }
        if (missing(target)) {
            notFound();
            end();
            return;
        }
        store.remove(target);
        gone.add(target);
        invalidateCache();
        applied = true;
        status = "204 No Content";
        hasResponseBody = false;

        Trace.event("RESOURCE_DELETED",
                "204 No Content — " + target + " is gone. There is no representation left to send back, "
                        + "which is why the response carries no body",
                "204 No Content — ресурса " + target + " больше нет. Отдавать нечего, поэтому в ответе "
                        + "нет тела",
                highlights(target), state());
        end();
    }

    /**
     * A GET whose handler changes state — the endpoint a team exposed as
     * {@code GET /orders/1/cancel} because it is easy to put behind a link. The
     * method still says "safe"; the code no longer is.
     */
    public void mutatingGet(String requestPath, String target, Body change) {
        begin("GET", requestPath, null);
        if (missing(target)) {
            notFound();
            end();
            return;
        }
        touched = target;
        Map<String, String> representation = store.get(target);
        representation.putAll(change.fields);
        marks.put(target, "updated");
        invalidateCache();
        unsafeReads++;
        applied = true;
        status = "200 OK";
        hasResponseBody = true;

        Trace.event("UNSAFE_GET",
                "The handler behind this GET changed " + target + " (" + describe(change.fields)
                        + "). Nothing stops code from doing that — but every browser prefetch, proxy "
                        + "cache, crawler and automatic retry in the path believes a GET is safe, so "
                        + "they will fire it again on their own",
                "Обработчик этого GET изменил " + target + " (" + describe(change.fields) + "). Написать "
                        + "такой код никто не мешает — но предзагрузка браузера, кэш прокси, поисковый "
                        + "робот и автоматический повтор на пути считают, что GET безопасен, и повторят "
                        + "его сами",
                highlights(target), state());
        end();
    }

    /** Prints what the server looks like after everything the example did. */
    public void report() {
        marks.clear();
        gone.clear();
        Trace.event("SERVER_AUDIT",
                "After " + requests + " request(s): " + mutations + " changed server state, " + created
                        + " resource(s) were created, " + unsafeReads + " read(s) were not actually safe. "
                        + base + " now holds " + store.size() + " resource(s): " + paths(),
                "После запросов (" + requests + "): изменили состояние сервера — " + mutations
                        + ", создано ресурсов — " + created + ", чтений, которые на деле не были "
                        + "безопасными — " + unsafeReads + ". Сейчас " + base + " хранит ресурсов: "
                        + store.size() + " — " + paths(),
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    private void begin(String httpMethod, String target, Body body) {
        requests++;
        exchangeStarted = true;
        method = httpMethod;
        path = target;
        touched = target;
        requestBody = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body.fields);
        status = "pending";
        location = null;
        allow = List.of();
        fromCache = false;
        hasResponseBody = false;
        applied = false;
        marks.clear();
        gone.clear();
        before = snapshot();
        for (String stored : store.keySet()) {
            marks.put(stored, "kept");
        }

        Trace.event("REQUEST_SENT",
                method + " " + path + " — " + properties(true) + "; "
                        + (requestBody.isEmpty() ? "no request body"
                            : "Content-Type: application/json, body {" + describe(requestBody) + "}"),
                method + " " + path + " — " + properties(false) + "; "
                        + (requestBody.isEmpty() ? "тела запроса нет"
                            : "Content-Type: application/json, тело {" + describe(requestBody) + "}"),
                List.of("request"), state());
    }

    /** Answers 405 when the method does not apply to this kind of URL. */
    private boolean methodNotAllowed(String target) {
        List<String> allowed = allowedFor(target);
        if (allowed.contains(method)) {
            return false;
        }
        allow = allowed;
        status = "405 Method Not Allowed";
        hasResponseBody = false;
        Trace.event("METHOD_NOT_ALLOWED",
                "405 Method Not Allowed, Allow: " + String.join(", ", allow) + " — the URL exists, the "
                        + "method does not apply to it. 404 would mean \"no such URL\" and 501 would "
                        + "mean \"this server implements no such method at all\"",
                "405 Method Not Allowed, Allow: " + String.join(", ", allow) + " — URL существует, но "
                        + "метод к нему неприменим. 404 означал бы «такого URL нет», а 501 — «сервер "
                        + "вообще не умеет такой метод»",
                List.of("request"), state());
        previousSignature = signature();
        return true;
    }

    private void notFound() {
        status = "404 Not Found";
        hasResponseBody = false;
        Trace.event("NOT_FOUND",
                "404 Not Found — " + path + " is not a resource on this server. The method was fine; "
                        + "the target was not",
                "404 Not Found — по адресу " + path + " на этом сервере ресурса нет. С методом всё в "
                        + "порядке, а вот с целью — нет",
                List.of("request"), state());
    }

    /** States what the finished request did to server state. */
    private void end() {
        boolean changed = !before.equals(store);
        boolean repeat = signature().equals(previousSignature);
        if (changed) {
            mutations++;
            String movedEn = before.size() == store.size()
                    ? "the representation at " + touched + " is no longer the one it held"
                    : base + " went from " + before.size() + " to " + store.size() + " resource(s)";
            String movedRu = before.size() == store.size()
                    ? "представление по адресу " + touched + " уже не то, что лежало там раньше"
                    : "в " + base + " было ресурсов: " + before.size() + ", стало: " + store.size();
            Trace.event("STATE_CHANGED",
                    "Not safe: this request changed server state — " + movedEn + ", writes so far: "
                            + mutations + ". A client that never saw the response cannot simply send it "
                            + "again without asking what that would do",
                    "Небезопасно: запрос изменил состояние сервера — " + movedRu + ", всего записей: "
                            + mutations + ". Клиент, не увидевший ответа, не может просто отправить его "
                            + "снова, не подумав о последствиях",
                    highlights(touched), state());
        } else if (safe(method)) {
            Trace.event("SAFE_READ",
                    "Safe: the server still holds the same " + store.size() + " resource(s) it held "
                            + "before the request. That is exactly why a browser may prefetch a GET, a "
                            + "proxy may cache it and a crawler may follow it without asking you",
                    "Безопасно: на сервере остались те же ресурсы (" + store.size() + " шт.), что и до "
                            + "запроса. Именно поэтому браузер может заранее подгрузить GET, прокси — "
                            + "закэшировать его, а поисковый робот — перейти по нему, не спрашивая вас",
                    List.of("request"), state());
        } else if (applied) {
            Trace.event("IDEMPOTENT_REPEAT",
                    (repeat ? "The identical " + method + " ran a SECOND time"
                            : "This " + method + " was applied")
                            + " and the state is exactly what it already was. Idempotent means N "
                            + "identical calls leave the same state as one — not that the responses are "
                            + "identical, and not that nothing happened on the server",
                    (repeat ? "Тот же самый " + method + " выполнился ВТОРОЙ раз"
                            : "Этот " + method + " применился")
                            + ", и состояние осталось ровно таким же, каким уже было. Идемпотентность "
                            + "означает, что N одинаковых вызовов оставляют то же состояние, что и "
                            + "один, а не что ответы совпадают и не что на сервере ничего не произошло",
                    highlights(touched), state());
        } else if ("DELETE".equals(method)) {
            Trace.event("IDEMPOTENT_REPEAT",
                    "The second DELETE answered 404 instead of 204, yet " + path + " is just as absent "
                            + "as it was after the first one. Idempotency is about the STATE you end up "
                            + "in, not about the status code — which is why some APIs answer 204 here",
                    "Второй DELETE ответил 404 вместо 204, но ресурса " + path + " нет ровно так же, "
                            + "как и после первого. Идемпотентность — про итоговое СОСТОЯНИЕ, а не про "
                            + "код ответа; поэтому некоторые API отвечают здесь 204",
                    List.of("request"), state());
        }
        previousSignature = signature();
    }

    /** A write makes every copy the client is holding stale. */
    private void invalidateCache() {
        cached.clear();
    }

    private boolean missing(String target) {
        return !target.equals(base) && !store.containsKey(target);
    }

    private List<String> allowedFor(String target) {
        return target.equals(base) ? COLLECTION_METHODS : ITEM_METHODS;
    }

    private static boolean safe(String httpMethod) {
        return "GET".equals(httpMethod) || "HEAD".equals(httpMethod) || "OPTIONS".equals(httpMethod);
    }

    private static boolean idempotent(String httpMethod) {
        return safe(httpMethod) || "PUT".equals(httpMethod) || "DELETE".equals(httpMethod);
    }

    private static boolean cacheable(String httpMethod) {
        return "GET".equals(httpMethod) || "HEAD".equals(httpMethod);
    }

    /** The three promises of the current method, spelled out for the event log. */
    private String properties(boolean english) {
        List<String> parts = new ArrayList<>();
        if (english) {
            parts.add(safe(method) ? "safe" : "not safe");
            parts.add("PATCH".equals(method) ? "idempotency not guaranteed"
                    : idempotent(method) ? "idempotent" : "not idempotent");
            parts.add(cacheable(method) ? "cacheable" : "not cacheable");
        } else {
            parts.add(safe(method) ? "безопасный" : "небезопасный");
            parts.add("PATCH".equals(method) ? "идемпотентность не гарантируется"
                    : idempotent(method) ? "идемпотентный" : "неидемпотентный");
            parts.add(cacheable(method) ? "кэшируемый" : "не кэшируемый");
        }
        return String.join(", ", parts);
    }

    private String signature() {
        return method + "|" + path + "|" + describe(requestBody);
    }

    private Map<String, Map<String, String>> snapshot() {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : store.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private List<String> highlights(String target) {
        return List.of("request", "res:" + target);
    }

    private String paths() {
        return store.isEmpty() ? "none" : String.join(", ", store.keySet());
    }

    private String describeTarget(String target) {
        if (target.equals(base)) {
            return "[" + paths() + "]";
        }
        return "{" + describe(store.get(target)) + "}";
    }

    private static String describe(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("base", base);

        List<Object> resources = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : store.entrySet()) {
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("path", entry.getKey());
            List<Object> fields = new ArrayList<>();
            for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("field", field.getKey());
                pair.put("value", field.getValue());
                fields.add(pair);
            }
            resource.put("fields", fields);
            resource.put("mark", marks.getOrDefault(entry.getKey(), "kept"));
            resources.add(resource);
        }
        s.put("resources", resources);
        s.put("gone", List.copyOf(gone));
        s.put("cached", List.copyOf(cached));

        if (exchangeStarted) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", method);
            request.put("path", path);
            request.put("safe", safe(method));
            request.put("idempotent", idempotent(method));
            request.put("cacheable", cacheable(method));
            List<Object> body = new ArrayList<>();
            for (Map.Entry<String, String> field : requestBody.entrySet()) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("field", field.getKey());
                pair.put("value", field.getValue());
                body.add(pair);
            }
            request.put("body", body);
            s.put("request", request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", status);
            response.put("hasBody", hasResponseBody);
            response.put("fromCache", fromCache);
            response.put("location", location);
            response.put("allow", List.copyOf(allow));
            s.put("response", response);
        }

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("requests", requests);
        counters.put("mutations", mutations);
        counters.put("created", created);
        counters.put("unsafeReads", unsafeReads);
        s.put("counters", counters);
        return s;
    }
}
