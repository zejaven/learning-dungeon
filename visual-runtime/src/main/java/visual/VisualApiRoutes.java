package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of an HTTP API's <em>surface</em>: the URLs it
 * exposes and the methods each URL answers.
 *
 * <p>The whole topic fits in one sentence: the path names the <em>thing</em>
 * (a noun), the method names the <em>action</em>. So the model keeps a routing
 * table of paths, each holding the set of methods registered on it, and does
 * three things with it:
 * <ul>
 *   <li>{@link #expose(String, String)} registers an endpoint and runs the path
 *       through a small naming lint (verbs in the path, singular collections,
 *       camelCase, trailing slashes, file extensions, filters as segments,
 *       over-deep nesting);</li>
 *   <li>{@link #request(String, String)} routes a real request through the
 *       table, so a missing URL is a 404 and a URL that exists but does not
 *       answer that method is a 405 — the distinction that only exists because
 *       the noun and the verb are separate;</li>
 *   <li>{@link #rename(String, String, String, String)} rewrites an RPC-style
 *       name into resource + method, which is the fix the interview question is
 *       really asking about.</li>
 * </ul>
 *
 * <p>The lint is deliberately conservative, because "a verb in the path" is a
 * smell and not a law: only the CRUD verbs are flagged (a genuinely non-CRUD
 * command such as {@code POST /orders/{id}/cancellations} is left alone), and a
 * segment is only required to be plural when a template segment follows it, i.e.
 * when it is provably used as a collection. Plurality itself is judged by a
 * naive "ends with s, or is a known irregular" rule, which is enough for a
 * teaching model.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualApiRoutes {

    /** CRUD verbs that read. In a path they mean the method was thrown away. */
    private static final Set<String> READ_VERBS = Set.of(
            "get", "list", "fetch", "find", "read", "retrieve", "load", "show", "view");

    /** CRUD verbs that write. In a path they mean the method was thrown away. */
    private static final Set<String> WRITE_VERBS = Set.of(
            "create", "add", "new", "insert", "store", "save", "set", "update", "edit",
            "modify", "change", "delete", "remove", "destroy");

    /** Plurals the "ends with s" rule cannot recognise. */
    private static final Set<String> IRREGULAR_PLURALS = Set.of(
            "people", "children", "men", "women", "media", "data", "criteria");

    /** Segments that are routing prefixes rather than resources. */
    private static final Set<String> PREFIX_SEGMENTS = Set.of("api", "rest", "public", "internal");

    /** One naming finding: a technical code plus the bilingual reason. */
    private static final class Finding {
        final String code;
        final String en;
        final String ru;

        Finding(String code, String en, String ru) {
            this.code = code;
            this.en = en;
            this.ru = ru;
        }
    }

    /** One URL in the routing table, with every method registered on it. */
    private static final class Route {
        final String path;
        final List<String> segments;
        final String kind;
        final List<Finding> findings;
        final Set<String> methods = new LinkedHashSet<>();

        Route(String path, List<String> segments, String kind, List<Finding> findings) {
            this.path = path;
            this.segments = segments;
            this.kind = kind;
            this.findings = findings;
        }
    }

    private final String service;
    /** path -> route, in registration order. */
    private final Map<String, Route> routes = new LinkedHashMap<>();

    private int endpoints;
    private int smells;

    private String requestMethod;
    private String requestPath;
    private List<String[]> requestQuery;
    private String requestStatus;
    private String matchedPath;

    private VisualApiRoutes(String service) {
        this.service = service;
    }

    /** An API with nothing exposed yet. */
    public static VisualApiRoutes forService(String service) {
        VisualApiRoutes api = new VisualApiRoutes(service);
        Trace.event("API_READY",
                "The " + service + " surface starts empty. An endpoint is a NOUN (the resource the "
                        + "URL names) plus a METHOD (what to do with it): the URL says WHICH thing, "
                        + "the method says WHAT",
                "Поверхность " + service + " пока пуста. Эндпоинт — это СУЩЕСТВИТЕЛЬНОЕ (ресурс, "
                        + "который называет URL) плюс МЕТОД (что с ним сделать): URL говорит, КАКАЯ "
                        + "это вещь, а метод — ЧТО с ней делать",
                List.of(), api.state());
        return api;
    }

    /** Registers {@code method path} and reports what the path is named like. */
    public void expose(String method, String path) {
        clearRequest();
        String verb = method.toUpperCase(Locale.ROOT);
        Route route = routes.get(path);
        if (route == null) {
            List<String> segments = segments(path);
            route = new Route(path, segments, classify(segments), lint(path, segments));
            routes.put(path, route);
            smells += route.findings.size();
        }
        boolean added = route.methods.add(verb);
        if (added) {
            endpoints++;
        }

        Trace.event("ENDPOINT_ADDED",
                verb + " " + path + " joins the surface — " + kindEn(route.kind) + ". "
                        + endpoints + " endpoint(s) over " + routes.size() + " URL(s)",
                verb + " " + path + " добавлен на поверхность — " + kindRu(route.kind) + ". "
                        + "Эндпоинтов: " + endpoints + ", URL: " + routes.size(),
                highlight(route.path, verb), state());

        if (!route.findings.isEmpty() && added) {
            Trace.event("NAMING_SMELL",
                    path + " breaks " + route.findings.size() + " naming rule(s): " + joinEn(route.findings)
                            + ". None of this stops the endpoint from working — it stops the surface from "
                            + "being guessable",
                    path + " нарушает правил именования: " + route.findings.size() + " — " + joinRu(route.findings)
                            + ". Ничто из этого не мешает эндпоинту работать — это мешает поверхности "
                            + "быть предсказуемой",
                    highlight(route.path, verb), state());
        }

        if (added) {
            reportMethodMismatch(verb, route);
        }
    }

    /**
     * Replaces an RPC-style endpoint with the resource + method form. This is the
     * fix the naming question is asking for, so it gets its own event.
     */
    public void rename(String method, String path, String newMethod, String newPath) {
        clearRequest();
        String from = method.toUpperCase(Locale.ROOT);
        String to = newMethod.toUpperCase(Locale.ROOT);
        Route old = routes.get(path);
        if (old == null || !old.methods.remove(from)) {
            Trace.event("NO_ROUTE",
                    from + " " + path + " is not on the surface, so there is nothing to rename",
                    from + " " + path + " нет на поверхности, переименовывать нечего",
                    List.of(), state());
            return;
        }
        endpoints--;
        if (old.methods.isEmpty()) {
            routes.remove(path);
            smells -= old.findings.size();
        }

        expose(to, newPath);

        Route fresh = routes.get(newPath);
        Trace.event("ENDPOINT_RENAMED",
                from + " " + path + "  ->  " + to + " " + newPath + ": the verb moved out of the path "
                        + "and into the method. The call is the same, there is one less name to remember, "
                        + "and the URL is now free to answer other methods too — "
                        + fresh.methods.size() + " so far on this one",
                from + " " + path + "  ->  " + to + " " + newPath + ": глагол ушёл из пути в метод. "
                        + "Вызов тот же, запоминать нужно на одно имя меньше, и теперь этот URL может "
                        + "отвечать и на другие методы — сейчас их " + fresh.methods.size(),
                highlight(newPath, to), state());
    }

    /**
     * Sends a request at the surface. {@code target} may carry a query string
     * ({@code /employees?department=finance&page=2}).
     */
    public void request(String method, String target) {
        String verb = method.toUpperCase(Locale.ROOT);
        int mark = target.indexOf('?');
        String path = mark < 0 ? target : target.substring(0, mark);
        String query = mark < 0 ? "" : target.substring(mark + 1);

        requestMethod = verb;
        requestPath = path;
        requestQuery = parseQuery(query);
        requestStatus = "pending";
        matchedPath = null;

        Route route = match(path);
        if (route == null) {
            requestStatus = "404 Not Found";
            String suggestion = suggest(path);
            Trace.event("NO_ROUTE",
                    verb + " " + path + " -> 404 Not Found: no URL on this surface has that shape"
                            + (suggestion == null ? ""
                                : ". " + suggestion + " is registered — a path is compared byte for byte, "
                                    + "so singular vs plural and one capital letter are different URLs. "
                                    + "Pick one spelling and use it everywhere"),
                    verb + " " + path + " -> 404 Not Found: ни один URL этой поверхности не имеет такой формы"
                            + (suggestion == null ? ""
                                : ". Зарегистрирован " + suggestion + " — путь сравнивается побайтово, "
                                    + "поэтому единственное и множественное число и одна заглавная буква — "
                                    + "это разные URL. Выберите одно написание и держитесь его"),
                    List.of("request"), state());
            return;
        }

        matchedPath = route.path;
        if (!route.methods.contains(verb)) {
            requestStatus = "405 Method Not Allowed";
            Trace.event("METHOD_NOT_ALLOWED",
                    verb + " " + path + " -> 405 Method Not Allowed, Allow: " + String.join(", ", route.methods)
                            + ". The URL exists — the thing it names is right there — it just does not answer "
                            + "this verb. A server can only tell you that because the resource and the action "
                            + "are separate things",
                    verb + " " + path + " -> 405 Method Not Allowed, Allow: " + String.join(", ", route.methods)
                            + ". URL существует — вещь, которую он называет, на месте, — просто он не отвечает "
                            + "на этот глагол. Сервер может так ответить только потому, что ресурс и действие "
                            + "разделены",
                    highlight(route.path, null), state());
            return;
        }

        requestStatus = statusFor(verb);
        Trace.event("REQUEST_ROUTED",
                verb + " " + path + " matched " + route.path + " -> " + requestStatus + ". The path picked "
                        + "the thing (" + kindEn(route.kind) + "), the method picked what happens to it",
                verb + " " + path + " попал в " + route.path + " -> " + requestStatus + ". Путь выбрал вещь ("
                        + kindRu(route.kind) + "), метод выбрал, что с ней произойдёт",
                highlight(route.path, verb), state());

        if (!requestQuery.isEmpty()) {
            Trace.event("QUERY_FILTERED",
                    "The request carried " + requestQuery.size() + " query parameter(s): " + describeQuery()
                            + ". Filtering, sorting and paging do not make a new resource — they select part "
                            + "of the SAME collection, so they belong in the query string. As path segments "
                            + "every combination of filters would need its own URL",
                    "Запрос принёс параметров строки запроса: " + requestQuery.size() + " — " + describeQuery()
                            + ". Фильтрация, сортировка и постраничность не создают новый ресурс: они выбирают "
                            + "часть ТОЙ ЖЕ коллекции, поэтому им место в query string. В виде сегментов пути "
                            + "каждая комбинация фильтров потребовала бы собственного URL",
                    highlight(route.path, verb), state());
        }
    }

    /** Prints the surface as a whole: what a consumer would have to memorise. */
    public void review() {
        clearRequest();
        List<String> flagged = new ArrayList<>();
        for (Route route : routes.values()) {
            if (!route.findings.isEmpty()) {
                flagged.add(route.path);
            }
        }
        double perUrl = routes.isEmpty() ? 0 : (double) endpoints / routes.size();
        String ratio = String.format(Locale.ROOT, "%.1f", perUrl);

        Trace.event("API_REVIEW",
                service + ": " + endpoints + " endpoint(s) over " + routes.size() + " URL(s) ("
                        + ratio + " methods per URL), " + smells + " naming finding(s)"
                        + (flagged.isEmpty() ? "" : " on " + String.join(", ", flagged))
                        + ". Read the URL column alone: it should read as the nouns of the domain, and each "
                        + "noun should answer several methods instead of every action inventing a new name",
                service + ": эндпоинтов " + endpoints + " на URL: " + routes.size() + " ("
                        + ratio + " метода на URL), замечаний по именованию: " + smells
                        + (flagged.isEmpty() ? "" : " — " + String.join(", ", flagged))
                        + ". Прочитайте один только столбец URL: он должен читаться как существительные "
                        + "предметной области, и на каждое существительное должно приходиться несколько "
                        + "методов, а не новое имя на каждое действие",
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    /** Says when the method contradicts the action the path spells out. */
    private void reportMethodMismatch(String verb, Route route) {
        String leading = leadingVerb(route.segments);
        if (leading == null) {
            return;
        }
        if (("GET".equals(verb) || "HEAD".equals(verb)) && WRITE_VERBS.contains(leading)) {
            Trace.event("UNSAFE_GET",
                    verb + " " + route.path + " changes data, and GET is defined as SAFE. Browsers prefetch "
                            + "safe URLs, crawlers follow them, proxies cache them and a client may retry one "
                            + "after a timeout — all of which now happen to a write. Deleting is DELETE on the "
                            + "item URL, not a '" + leading + "' segment behind a GET",
                    verb + " " + route.path + " изменяет данные, а GET по определению БЕЗОПАСЕН. Браузеры "
                            + "предзагружают безопасные URL, краулеры по ним ходят, прокси их кэшируют, а "
                            + "клиент может повторить такой запрос после таймаута — и всё это теперь "
                            + "происходит с записью. Удаление — это DELETE по URL элемента, а не сегмент "
                            + "«" + leading + "» под GET",
                    highlight(route.path, verb), state());
        } else if (("POST".equals(verb) || "PUT".equals(verb)) && READ_VERBS.contains(leading)) {
            Trace.event("READ_BEHIND_POST",
                    verb + " " + route.path + " only reads, but it is wearing a write's method. The answer "
                            + "cannot be cached, the URL cannot be bookmarked or shared, and any intermediary "
                            + "must assume the call changed something and refuse to retry it. The same query "
                            + "as GET on the collection loses none of that",
                    verb + " " + route.path + " только читает, но надел метод записи. Ответ нельзя закэшировать, "
                            + "URL нельзя сохранить в закладки или передать, а любой посредник обязан считать, "
                            + "что вызов что-то изменил, и не станет его повторять. Тот же запрос как GET по "
                            + "коллекции ничего из этого не теряет",
                    highlight(route.path, verb), state());
        }
    }

    /** Runs the naming rules over a path. */
    private List<Finding> lint(String path, List<String> segments) {
        List<Finding> found = new ArrayList<>();

        if (path.length() > 1 && path.endsWith("/")) {
            found.add(new Finding("trailing-slash",
                    "it ends with a slash, so " + path + " and " + path.substring(0, path.length() - 1)
                            + " are two different URLs to every cache and router",
                    "он заканчивается слэшем, поэтому " + path + " и " + path.substring(0, path.length() - 1)
                            + " — два разных URL для любого кэша и маршрутизатора"));
        }

        int templates = 0;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            if (isTemplate(segment)) {
                templates++;
                continue;
            }
            List<String> words = words(segment);
            String head = words.isEmpty() ? "" : words.get(0);

            if (READ_VERBS.contains(head) || WRITE_VERBS.contains(head)) {
                found.add(new Finding("verb-in-path",
                        "the segment '" + segment + "' starts with the verb '" + head + "', which is the "
                                + "method's job — the path should only name the thing",
                        "сегмент «" + segment + "» начинается с глагола «" + head + "», а это работа метода: "
                                + "путь должен только называть вещь"));
            }
            if ("by".equals(head)) {
                found.add(new Finding("filter-in-path",
                        "'" + segment + "' turns a filter into a URL — the same list selected differently is "
                                + "still the same collection, so it belongs in the query string",
                        "«" + segment + "» превращает фильтр в URL, а иначе выбранный тот же список — всё та "
                                + "же коллекция, поэтому ему место в query string"));
            }
            if (!segment.equals(segment.toLowerCase(Locale.ROOT))) {
                found.add(new Finding("upper-case",
                        "'" + segment + "' mixes cases; paths are case-sensitive, so lower-case with hyphens "
                                + "is the convention that never surprises anyone",
                        "«" + segment + "» смешивает регистры; пути чувствительны к регистру, поэтому нижний "
                                + "регистр с дефисами — соглашение, которое никого не удивляет"));
            }
            if (segment.indexOf('.') >= 0) {
                found.add(new Finding("file-extension",
                        "'" + segment + "' carries a file extension; the format is negotiated with the Accept "
                                + "header, not baked into the resource's name",
                        "«" + segment + "» несёт расширение файла; формат согласуется заголовком Accept, а не "
                                + "вшивается в имя ресурса"));
            }
            boolean followedById = i + 1 < segments.size() && isTemplate(segments.get(i + 1));
            if (followedById && !PREFIX_SEGMENTS.contains(head) && !isPlural(words)) {
                found.add(new Finding("not-plural",
                        "'" + segment + "' is used as a collection (an id follows it) but is written in the "
                                + "singular; a collection holds many, so it reads as many",
                        "«" + segment + "» используется как коллекция (за ним идёт id), но написан в "
                                + "единственном числе; коллекция хранит много — пусть и читается как много"));
            }
        }

        if (templates > 2) {
            found.add(new Finding("deep-nesting",
                    "it nests " + templates + " ids deep; once an item has its own id you can address it "
                            + "directly instead of retracing the whole hierarchy",
                    "он вложен на " + templates + " id; если у элемента есть собственный id, к нему можно "
                            + "обращаться напрямую, а не проходить всю иерархию заново"));
        }
        return found;
    }

    /** The first CRUD verb spelled out in the path, or null when there is none. */
    private static String leadingVerb(List<String> segments) {
        for (String segment : segments) {
            if (isTemplate(segment)) {
                continue;
            }
            List<String> words = words(segment);
            String head = words.isEmpty() ? "" : words.get(0);
            if (READ_VERBS.contains(head) || WRITE_VERBS.contains(head)) {
                return head;
            }
        }
        return null;
    }

    /** Finds the registered route a request path lands on, if any. */
    private Route match(String path) {
        List<String> requested = segments(path);
        Route best = null;
        int bestTemplates = Integer.MAX_VALUE;
        for (Route route : routes.values()) {
            if (route.segments.size() != requested.size()) {
                continue;
            }
            int templates = 0;
            boolean ok = true;
            for (int i = 0; i < requested.size(); i++) {
                String pattern = route.segments.get(i);
                if (isTemplate(pattern)) {
                    templates++;
                } else if (!pattern.equals(requested.get(i))) {
                    ok = false;
                    break;
                }
            }
            // A literal segment beats a template, so /employees/count wins over /employees/{id}.
            if (ok && templates < bestTemplates) {
                best = route;
                bestTemplates = templates;
            }
        }
        return best;
    }

    /** The registered path a 404 was probably aiming at (case / plural only). */
    private String suggest(String path) {
        List<String> requested = segments(path);
        for (Route route : routes.values()) {
            if (route.segments.size() != requested.size()) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < requested.size(); i++) {
                String pattern = route.segments.get(i);
                if (isTemplate(pattern)) {
                    continue;
                }
                if (!fold(pattern).equals(fold(requested.get(i)))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return route.path;
            }
        }
        return null;
    }

    /** Lower-cases a segment and drops a trailing 's', so plural folds onto singular. */
    private static String fold(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        return lower.endsWith("s") ? lower.substring(0, lower.length() - 1) : lower;
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

    private static boolean isTemplate(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    /** Splits a segment into lower-case words across camelCase, '-', '_' and '.'. */
    private static List<String> words(String segment) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (Character.isUpperCase(c) && current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
                current.append(Character.toLowerCase(c));
            } else if (Character.isLetterOrDigit(c)) {
                current.append(Character.toLowerCase(c));
            } else if (current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            words.add(current.toString());
        }
        return words;
    }

    private static boolean isPlural(List<String> words) {
        if (words.isEmpty()) {
            return true;
        }
        String last = words.get(words.size() - 1);
        return last.endsWith("s") || IRREGULAR_PLURALS.contains(last);
    }

    /** What a path names: a collection, one item, or a part of an item. */
    private static String classify(List<String> segments) {
        if (segments.isEmpty()) {
            return "collection";
        }
        boolean hasTemplate = false;
        for (String segment : segments) {
            if (isTemplate(segment)) {
                hasTemplate = true;
                break;
            }
        }
        if (isTemplate(segments.get(segments.size() - 1))) {
            return "item";
        }
        return hasTemplate ? "nested" : "collection";
    }

    private static String kindEn(String kind) {
        return switch (kind) {
            case "item" -> "an item URL, the template says which one";
            case "nested" -> "a sub-resource, a part of the item named before it";
            default -> "a collection URL, it names many things of one kind";
        };
    }

    private static String kindRu(String kind) {
        return switch (kind) {
            case "item" -> "URL элемента, шаблон говорит, какого именно";
            case "nested" -> "вложенный ресурс, часть названного перед ним элемента";
            default -> "URL коллекции, он называет много вещей одного вида";
        };
    }

    private static String statusFor(String verb) {
        return switch (verb) {
            case "POST" -> "201 Created";
            case "DELETE" -> "204 No Content";
            default -> "200 OK";
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

    private String describeQuery() {
        StringBuilder sb = new StringBuilder();
        for (String[] pair : requestQuery) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(pair[0]).append('=').append(pair[1]);
        }
        return sb.toString();
    }

    private void clearRequest() {
        requestMethod = null;
        requestPath = null;
        requestQuery = null;
        requestStatus = null;
        matchedPath = null;
    }

    private static List<String> highlight(String path, String verb) {
        List<String> tokens = new ArrayList<>();
        tokens.add("route:" + path);
        if (verb != null) {
            tokens.add("verb:" + path + "#" + verb);
        }
        return tokens;
    }

    private static String joinEn(List<Finding> findings) {
        List<String> parts = new ArrayList<>();
        for (Finding finding : findings) {
            parts.add(finding.en);
        }
        return String.join("; ", parts);
    }

    private static String joinRu(List<Finding> findings) {
        List<String> parts = new ArrayList<>();
        for (Finding finding : findings) {
            parts.add(finding.ru);
        }
        return String.join("; ", parts);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("service", service);

        List<Object> table = new ArrayList<>();
        for (Route route : routes.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", route.path);
            entry.put("kind", route.kind);

            List<Object> methods = new ArrayList<>();
            for (String method : route.methods) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", method);
                item.put("current", route.path.equals(matchedPath) && method.equals(requestMethod));
                methods.add(item);
            }
            entry.put("methods", methods);

            List<Object> codes = new ArrayList<>();
            for (Finding finding : route.findings) {
                codes.add(finding.code);
            }
            entry.put("smells", codes);
            table.add(entry);
        }
        s.put("routes", table);

        if (requestMethod != null) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", requestMethod);
            request.put("path", requestPath);
            List<Object> query = new ArrayList<>();
            for (String[] pair : requestQuery) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", pair[0]);
                item.put("value", pair[1]);
                query.add(item);
            }
            request.put("query", query);
            request.put("status", requestStatus);
            request.put("matchedPath", matchedPath);
            s.put("request", request);
        }

        s.put("endpoints", endpoints);
        s.put("paths", routes.size());
        s.put("smells", smells);
        return s;
    }
}
