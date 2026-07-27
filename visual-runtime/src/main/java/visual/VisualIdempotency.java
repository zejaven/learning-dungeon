package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of <strong>idempotency</strong>: an operation is
 * idempotent when N identical calls leave the system in the same state as one
 * call.
 *
 * <p>The model is a tiny server plus the unreliable network in front of it,
 * because that network is the whole reason the property matters. A request whose
 * response never comes back leaves the client unable to tell "the request never
 * arrived" from "the answer got lost", and the only recovery it has is to send
 * the request again. Whether that retry is harmless is exactly the question
 * idempotency answers.
 *
 * <p>Every exchange is measured, not asserted: the model snapshots the stored
 * resources before the request and compares them afterwards, so a repeat is
 * reported as {@code IDEMPOTENT_REPEAT} (state unchanged) or
 * {@code DUPLICATE_EFFECT} (state moved again) from what actually happened
 * rather than from the name of the method. The interesting cases examples can
 * reproduce:
 * <ul>
 *   <li>{@link #put(String, Body)} / {@link #delete(String)} repeated — the
 *       state lands in the same place even when the two responses differ;</li>
 *   <li>{@link #post(String, Body)} repeated — a second, independent resource;</li>
 *   <li>{@link #patchMerge(String, Body)} vs
 *       {@link #patchIncrement(String, String, int)} — the same method,
 *       idempotent or not depending on whether the body is an absolute value or
 *       a relative operation;</li>
 *   <li>{@link #postWithKey(String, String, Body)} — a non-idempotent method
 *       made idempotent by the handler, with an idempotency key;</li>
 *   <li>{@link #putNotifying(String, Body, String)} — the trap: an idempotent
 *       method whose handler fires a side effect on every call.</li>
 * </ul>
 *
 * <p>{@link #dropNextResponse()} makes the network eat the next answer and
 * {@link #retry()} resends the last request byte for byte, which is how the
 * examples reproduce a real timeout-and-retry.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualIdempotency {

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

    /** What the client asked for, and which HTTP method carried it. */
    private enum Kind {
        GET("GET"),
        HEAD("HEAD"),
        PUT("PUT"),
        PUT_NOTIFYING("PUT"),
        POST("POST"),
        POST_WITH_KEY("POST"),
        PATCH_MERGE("PATCH"),
        PATCH_INCREMENT("PATCH"),
        DELETE("DELETE");

        private final String method;

        Kind(String method) {
            this.method = method;
        }
    }

    /** One row of the attempt ledger: what was sent and what came of it. */
    private static final class Attempt {

        private final int seq;
        private final String method;
        private final String path;
        private final int number;
        private final String key;
        private String status = "pending";
        private boolean delivered = true;
        private String outcome = "pending";

        private Attempt(int seq, String method, String path, int number, String key) {
            this.seq = seq;
            this.method = method;
            this.path = path;
            this.number = number;
            this.key = key;
        }
    }

    private final String base;
    /** Stored resources: path -> representation, in a stable display order. */
    private final Map<String, Map<String, String>> store = new LinkedHashMap<>();
    /** How the current request touched each surviving resource: created|updated|kept. */
    private final Map<String, String> marks = new LinkedHashMap<>();
    /** Resources the current request removed. */
    private final List<String> gone = new ArrayList<>();
    /** The idempotency-key record: key -> "path|status" of the response it stored. */
    private final Map<String, String> keyStore = new LinkedHashMap<>();
    /** Effects outside the resource itself: mails, webhooks, downstream calls. */
    private final List<String> sideEffects = new ArrayList<>();
    /** Every attempt in the order it was sent. */
    private final List<Attempt> ledger = new ArrayList<>();
    /** How many times each request signature has been sent. */
    private final Map<String, Integer> sent = new LinkedHashMap<>();
    /** The status the first send of each signature answered with. */
    private final Map<String, String> firstStatus = new LinkedHashMap<>();

    private int nextId = 1;
    private int attempts;
    private int lost;
    private int effects;
    private int duplicates;
    private int replays;

    // ---- the exchange being processed -------------------------------------
    private Kind kind;
    private String path;
    /** The resource the current request acted on, which is not always its path. */
    private String touched;
    private Map<String, String> requestBody = new LinkedHashMap<>();
    private String key;
    private int number;
    private boolean repeat;
    private String status;
    private boolean delivered;
    private boolean replayed;
    private boolean missing;
    private Attempt current;
    private Map<String, Map<String, String>> before = Map.of();
    private int beforeEffects;

    // ---- client / network state -------------------------------------------
    private boolean dropNext;
    private boolean retrying;
    private Kind lastKind;
    private String lastPath;
    private Map<String, String> lastBody = new LinkedHashMap<>();
    private String lastKey;
    private String lastNote;
    private String lastField;
    private int lastDelta;

    private VisualIdempotency(String base) {
        this.base = base;
    }

    /**
     * A server exposing {@code base} and already holding the given
     * representations at {@code base + "/1"}, {@code base + "/2"}, and so on.
     */
    public static VisualIdempotency serving(String base, Body... resources) {
        VisualIdempotency api = new VisualIdempotency(base);
        for (Body body : resources) {
            api.store.put(base + "/" + api.nextId++, new LinkedHashMap<>(body.fields));
        }
        Trace.event("SERVER_READY",
                "An operation is idempotent when N identical calls leave the system in the same state "
                        + "as one call. This server exposes " + base + " and holds "
                        + api.store.size() + " resource(s): " + api.paths()
                        + ". Every request below is measured against that definition instead of trusting "
                        + "the name of the method",
                "Операция идемпотентна, когда N одинаковых вызовов оставляют систему в том же "
                        + "состоянии, что и один вызов. Этот сервер отдаёт " + base + " и хранит ресурсов: "
                        + api.store.size() + " — " + api.paths()
                        + ". Каждый запрос ниже проверяется по этому определению, а не по названию метода",
                List.of(), api.state());
        return api;
    }

    // ------------------------------------------------------------ the network

    /**
     * The network will swallow the answer to the next request: the server still
     * processes it, the client only sees a timeout.
     */
    public void dropNextResponse() {
        dropNext = true;
        Trace.event("NETWORK_UNRELIABLE",
                "The answer to the next request will be lost on the way back. The server will do its "
                        + "work; the client will only see a timeout",
                "Ответ на следующий запрос потеряется по пути обратно. Сервер сделает свою работу, а "
                        + "клиент увидит только таймаут",
                List.of("network"), state());
    }

    /** Resends the last request byte for byte — what a retrying client does. */
    public void retry() {
        if (lastKind == null) {
            throw new IllegalStateException("nothing has been sent yet");
        }
        retrying = true;
        switch (lastKind) {
            case GET -> sendGet(lastPath);
            case HEAD -> sendHead(lastPath);
            case PUT -> sendPut(lastPath, lastBody);
            case PUT_NOTIFYING -> sendPutNotifying(lastPath, lastBody, lastNote);
            case POST -> sendPost(lastPath, lastBody, null);
            case POST_WITH_KEY -> sendPost(lastPath, lastBody, lastKey);
            case PATCH_MERGE -> sendPatchMerge(lastPath, lastBody);
            case PATCH_INCREMENT -> sendPatchIncrement(lastPath, lastField, lastDelta);
            case DELETE -> sendDelete(lastPath);
        }
    }

    // ------------------------------------------------------------ the methods

    /** GET: read the representation, change nothing. */
    public void get(String target) {
        sendGet(target);
    }

    /** HEAD: the same answer as GET, headers only. */
    public void head(String target) {
        sendHead(target);
    }

    /** PUT: "let this URL hold exactly this representation". */
    public void put(String target, Body body) {
        sendPut(target, body.fields);
    }

    /**
     * PUT whose handler also fires an effect outside the resource — a
     * confirmation mail, a webhook, a downstream charge — on every single call.
     */
    public void putNotifying(String target, Body body, String notice) {
        sendPutNotifying(target, body.fields, notice);
    }

    /** POST: "create a subordinate resource under this collection". */
    public void post(String target, Body body) {
        sendPost(target, body.fields, null);
    }

    /**
     * POST carrying an {@code Idempotency-Key}: the handler recognizes a repeat
     * and replays the stored response instead of creating a second resource.
     */
    public void postWithKey(String target, String idempotencyKey, Body body) {
        sendPost(target, body.fields, idempotencyKey);
    }

    /** PATCH whose body is a set of absolute values ("status is paid"). */
    public void patchMerge(String target, Body change) {
        sendPatchMerge(target, change.fields);
    }

    /** PATCH whose body is a relative operation ("add 1 to qty"). */
    public void patchIncrement(String target, String field, int delta) {
        sendPatchIncrement(target, field, delta);
    }

    /** DELETE: "remove the resource at this URL". */
    public void delete(String target) {
        sendDelete(target);
    }

    /** Prints what the run added up to. */
    public void report() {
        marks.clear();
        gone.clear();
        Trace.event("AUDIT",
                attempts + " attempt(s) carrying " + firstStatus.size() + " distinct request(s): "
                        + effects + " actually changed state, " + duplicates
                        + " of those were unintended repeats, " + replays
                        + " were recognized by an idempotency key, " + lost
                        + " answer(s) never reached the client. " + base + " holds "
                        + store.size() + " resource(s): " + paths() + "; side effects fired: "
                        + sideEffects.size(),
                "Попыток: " + attempts + ", различных запросов среди них: " + firstStatus.size()
                        + "; реально изменили состояние — " + effects + ", из них незапланированных "
                        + "повторов — " + duplicates + ", распознано по ключу идемпотентности — "
                        + replays + ", ответов не дошло до клиента — " + lost + ". В " + base
                        + " сейчас ресурсов: " + store.size() + " — " + paths()
                        + "; побочных эффектов сработало: " + sideEffects.size(),
                List.of(), state());
    }

    // ------------------------------------------------------------- handlers

    private void sendGet(String target) {
        begin(Kind.GET, target, Map.of(), null);
        if (absent(target)) {
            notFound();
        } else {
            status = "200 OK";
        }
        finish();
    }

    private void sendHead(String target) {
        begin(Kind.HEAD, target, Map.of(), null);
        if (absent(target)) {
            notFound();
        } else {
            status = "200 OK";
        }
        finish();
    }

    private void sendPut(String target, Map<String, String> body) {
        begin(Kind.PUT, target, body, null);
        applyPut(target);
        finish();
    }

    private void sendPutNotifying(String target, Map<String, String> body, String notice) {
        lastNote = notice;
        begin(Kind.PUT_NOTIFYING, target, body, null);
        applyPut(target);
        sideEffects.add(notice);
        if (!repeat) {
            Trace.event("SIDE_EFFECT_FIRED",
                    "Besides storing the representation, the handler did something the resource does not "
                            + "show: " + notice + ". Nothing in HTTP knows about it, so nothing on the "
                            + "way can undo it",
                    "Кроме сохранения представления обработчик сделал то, чего в ресурсе не видно: "
                            + notice + ". HTTP об этом ничего не знает, поэтому никто по пути этого не "
                            + "отменит",
                    List.of("effects"), state());
        }
        finish();
    }

    private void sendPost(String target, Map<String, String> body, String idempotencyKey) {
        begin(idempotencyKey == null ? Kind.POST : Kind.POST_WITH_KEY, target, body, idempotencyKey);
        if (idempotencyKey != null && keyStore.containsKey(idempotencyKey)) {
            String stored = keyStore.get(idempotencyKey);
            String storedPath = stored.substring(0, stored.indexOf('|'));
            status = stored.substring(stored.indexOf('|') + 1);
            replayed = true;
            replays++;
            Trace.event("KEY_REPLAYED",
                    "Idempotency-Key: " + idempotencyKey + " has been seen before, so the handler "
                            + "executed NOTHING and replayed the stored answer: " + status + " for "
                            + storedPath + ". POST is still not an idempotent method — this ENDPOINT is, "
                            + "because the handler was written to be",
                    "Idempotency-Key: " + idempotencyKey + " уже встречался, поэтому обработчик НИЧЕГО "
                            + "не выполнил и повторил сохранённый ответ: " + status + " для "
                            + storedPath + ". POST по-прежнему неидемпотентный метод — идемпотентен "
                            + "этот ЭНДПОИНТ, потому что так написан обработчик",
                    List.of("key:" + idempotencyKey, "res:" + storedPath), state());
            finish();
            return;
        }

        String created = target + "/" + nextId++;
        touched = created;
        store.put(created, new LinkedHashMap<>(requestBody));
        marks.put(created, "created");
        status = "201 Created";
        if (idempotencyKey != null) {
            keyStore.put(idempotencyKey, created + "|" + status);
            Trace.event("KEY_STORED",
                    "Idempotency-Key: " + idempotencyKey + " was new, so the handler created " + created
                            + " and recorded (" + idempotencyKey + " -> " + status + ", " + created
                            + ") in the same transaction. That record is what will recognize a retry",
                    "Idempotency-Key: " + idempotencyKey + " встретился впервые, поэтому обработчик "
                            + "создал " + created + " и записал (" + idempotencyKey + " -> " + status
                            + ", " + created + ") в той же транзакции. Именно эта запись и распознает "
                            + "повтор",
                    List.of("key:" + idempotencyKey, "res:" + created), state());
        }
        finish();
    }

    private void sendPatchMerge(String target, Map<String, String> change) {
        begin(Kind.PATCH_MERGE, target, change, null);
        if (absent(target)) {
            notFound();
            finish();
            return;
        }
        store.get(target).putAll(requestBody);
        marks.put(target, "updated");
        status = "200 OK";
        finish();
    }

    private void sendPatchIncrement(String target, String field, int delta) {
        lastField = field;
        lastDelta = delta;
        Map<String, String> body = new LinkedHashMap<>();
        body.put(field, (delta >= 0 ? "+" : "") + delta);
        begin(Kind.PATCH_INCREMENT, target, body, null);
        if (absent(target)) {
            notFound();
            finish();
            return;
        }
        Map<String, String> representation = store.get(target);
        int value = Integer.parseInt(representation.getOrDefault(field, "0"));
        representation.put(field, String.valueOf(value + delta));
        marks.put(target, "updated");
        status = "200 OK";
        Trace.event("RELATIVE_UPDATE",
                "The body says \"" + field + " " + (delta >= 0 ? "+" : "") + delta
                        + "\", not \"" + field + " = " + (value + delta) + "\". A relative operation is "
                        + "applied to whatever is there NOW, so its result depends on how many times it "
                        + "ran — the body, not the method, is what breaks idempotency here",
                "Тело говорит «" + field + " " + (delta >= 0 ? "+" : "") + delta + "», а не «" + field
                        + " = " + (value + delta) + "». Относительная операция применяется к тому, что "
                        + "лежит СЕЙЧАС, поэтому результат зависит от числа применений — идемпотентность "
                        + "здесь ломает тело запроса, а не метод",
                List.of("res:" + target), state());
        finish();
    }

    private void sendDelete(String target) {
        begin(Kind.DELETE, target, Map.of(), null);
        if (absent(target)) {
            notFound();
            finish();
            return;
        }
        store.remove(target);
        gone.add(target);
        status = "204 No Content";
        finish();
    }

    private void applyPut(String target) {
        boolean existed = store.containsKey(target);
        store.put(target, new LinkedHashMap<>(requestBody));
        marks.put(target, existed ? "updated" : "created");
        status = existed ? "200 OK" : "201 Created";
    }

    // ------------------------------------------------------------- internals

    private void begin(Kind requestKind, String target, Map<String, String> body, String idempotencyKey) {
        attempts++;
        kind = requestKind;
        path = target;
        touched = target;
        requestBody = new LinkedHashMap<>(body);
        key = idempotencyKey;
        status = "pending";
        replayed = false;
        missing = false;
        delivered = !dropNext;
        dropNext = false;
        marks.clear();
        gone.clear();
        for (String stored : store.keySet()) {
            marks.put(stored, "kept");
        }
        before = snapshot();
        beforeEffects = sideEffects.size();

        String signature = signature();
        number = sent.getOrDefault(signature, 0) + 1;
        sent.put(signature, number);
        repeat = number > 1;

        current = new Attempt(attempts, kind.method, path, number, key);
        current.delivered = delivered;
        ledger.add(current);

        lastKind = kind;
        lastPath = path;
        lastBody = new LinkedHashMap<>(requestBody);
        lastKey = key;

        String keyEn = key == null ? "" : ", Idempotency-Key: " + key;
        String bodyEn = requestBody.isEmpty() ? "no body" : "body {" + describe(requestBody) + "}";
        if (retrying) {
            retrying = false;
            Trace.event("RETRY_SENT",
                    "Attempt " + number + ": the client never saw an answer, so it sends the IDENTICAL "
                            + kind.method + " " + path + " again" + keyEn + ". A timeout does not say "
                            + "whether the server applied the first one — only that the answer did not "
                            + "come back, and that is exactly the case idempotency has to survive",
                    "Попытка " + number + ": клиент так и не увидел ответа, поэтому отправляет ТОТ ЖЕ "
                            + kind.method + " " + path + " ещё раз" + keyEn + ". Таймаут не говорит, "
                            + "применил ли сервер первый запрос, — он говорит лишь, что ответ не "
                            + "вернулся; именно этот случай и должна пережить идемпотентность",
                    List.of("request"), state());
        } else {
            Trace.event("REQUEST_SENT",
                    kind.method + " " + path + " — " + promiseEn() + keyEn + "; " + bodyEn,
                    kind.method + " " + path + " — " + promiseRu() + keyEn + "; "
                            + (requestBody.isEmpty() ? "без тела" : "тело {" + describe(requestBody) + "}"),
                    List.of("request"), state());
        }
    }

    /** States what the finished request did, then what the client learned. */
    private void finish() {
        boolean changed = !before.equals(store);
        boolean fired = sideEffects.size() > beforeEffects;
        if (changed || fired) {
            effects++;
        }

        if (replayed) {
            current.outcome = "replayed";
        } else if (repeat && changed) {
            duplicates++;
            current.outcome = "duplicate";
            Trace.event("DUPLICATE_EFFECT",
                    "The identical " + kind.method + " ran a " + ordinal(number) + " time and changed "
                            + "the state AGAIN — " + movedEn() + ". " + number + " calls did not equal "
                            + "one call, so this operation is NOT idempotent: a retry after a timeout "
                            + "would charge the customer twice",
                    "Тот же самый " + kind.method + " выполнился " + ordinalRu(number)
                            + " раз и снова изменил состояние — " + movedRu() + ". " + number
                            + " вызова(-ов) не равны одному, значит операция НЕ идемпотентна: повтор "
                            + "после таймаута спишет с клиента дважды",
                    List.of("request", "res:" + touched), state());
        } else if (repeat && fired) {
            duplicates++;
            current.outcome = "leak";
            Trace.event("SIDE_EFFECT_LEAK",
                    "The stored representation is exactly what it already was — and the handler fired "
                            + "its side effect for the " + ordinal(number) + " time anyway ("
                            + sideEffects.size() + " so far). " + kind.method + " is an idempotent "
                            + "method; this HANDLER is not. Idempotency covers every observable effect, "
                            + "not only the row in the table",
                    "Хранимое представление осталось ровно тем же — а обработчик всё равно "
                            + ordinalRu(number) + " раз выполнил свой побочный эффект (всего: "
                            + sideEffects.size() + "). " + kind.method + " — идемпотентный метод, а вот "
                            + "этот ОБРАБОТЧИК — нет. Идемпотентность про все наблюдаемые эффекты, а не "
                            + "только про строку в таблице",
                    List.of("effects", "res:" + touched), state());
        } else if (repeat) {
            current.outcome = "repeat";
            String first = firstStatus.getOrDefault(signature(), status);
            Trace.event("IDEMPOTENT_REPEAT",
                    "The identical " + kind.method + " ran a " + ordinal(number) + " time and the state "
                            + "is exactly what it already was: " + base + " still holds " + store.size()
                            + " resource(s). The first answer was " + first + " and this one is " + status
                            + " — idempotency is about the STATE you end up in, not about the response",
                    "Тот же самый " + kind.method + " выполнился " + ordinalRu(number)
                            + " раз, и состояние осталось ровно тем же: в " + base + " по-прежнему "
                            + store.size() + " ресурс(-а/-ов). Первый ответ был " + first
                            + ", а этот — " + status + ": идемпотентность про итоговое СОСТОЯНИЕ, а не "
                            + "про ответ",
                    List.of("request", "res:" + touched), state());
        } else if (changed || fired) {
            current.outcome = "applied";
            Trace.event("EFFECT_APPLIED",
                    kind.method + " " + path + " took effect: " + movedEn() + ". This is the intent the "
                            + "client actually meant to express — everything after it is a question of "
                            + "what a repeat would do",
                    kind.method + " " + path + " применился: " + movedRu() + ". Именно это клиент и "
                            + "хотел выразить — дальше вопрос лишь в том, что сделает повтор",
                    List.of("request", "res:" + touched), state());
        } else if (!missing && safe()) {
            current.outcome = "read";
            Trace.event("SAFE_READ",
                    kind.method + " changed nothing: " + base + " holds the same " + store.size()
                            + " resource(s) it held before. A safe method is automatically idempotent — "
                            + "doing nothing N times is the same as doing nothing once — which is why "
                            + "browsers, proxies and crawlers repeat these on their own",
                    kind.method + " ничего не изменил: в " + base + " те же ресурсы (" + store.size()
                            + " шт.), что и до запроса. Безопасный метод автоматически идемпотентен — "
                            + "ничего не сделать N раз это то же, что ничего не сделать один раз, — "
                            + "поэтому браузеры, прокси и поисковые роботы повторяют их сами",
                    List.of("request"), state());
        } else {
            current.outcome = "none";
        }

        current.status = status;
        firstStatus.putIfAbsent(signature(), status);

        if (delivered) {
            Trace.event("RESPONSE_RECEIVED",
                    status + " — the client knows how the request ended and has nothing to decide",
                    status + " — клиент знает, чем кончился запрос, и решать ему нечего",
                    List.of("request"), state());
        } else {
            lost++;
            Trace.event("RESPONSE_LOST",
                    "Timeout: the server answered " + status + ", but the answer never reached the "
                            + "client. From the client's side \"the request never arrived\" and \"the "
                            + "response was lost\" look identical, so its only options are to retry and "
                            + "risk a duplicate, or give up and risk losing the work",
                    "Таймаут: сервер ответил " + status + ", но ответ до клиента не дошёл. Со стороны "
                            + "клиента «запрос не дошёл» и «ответ потерялся» выглядят одинаково, поэтому "
                            + "выбор у него один: повторить и рискнуть дублем или сдаться и рискнуть "
                            + "потерять работу",
                    List.of("request", "network"), state());
        }
    }

    private void notFound() {
        missing = true;
        status = "404 Not Found";
        Trace.event("NOT_FOUND",
                "404 Not Found — there is nothing at " + path + " to act on",
                "404 Not Found — по адресу " + path + " действовать не над чем",
                List.of("request"), state());
    }

    private boolean absent(String target) {
        return !store.containsKey(target);
    }

    private boolean safe() {
        return kind == Kind.GET || kind == Kind.HEAD;
    }

    /** What the HTTP spec promises about repeating this method. */
    private String idempotencyBySpec() {
        return switch (kind) {
            case GET, HEAD, PUT, PUT_NOTIFYING, DELETE -> "yes";
            case POST, POST_WITH_KEY -> "no";
            case PATCH_MERGE, PATCH_INCREMENT -> "unspecified";
        };
    }

    private String promiseEn() {
        return switch (idempotencyBySpec()) {
            case "yes" -> "idempotent by the HTTP spec";
            case "no" -> "not idempotent by the HTTP spec";
            default -> "idempotency not guaranteed by the HTTP spec";
        };
    }

    private String promiseRu() {
        return switch (idempotencyBySpec()) {
            case "yes" -> "идемпотентен по спецификации HTTP";
            case "no" -> "неидемпотентен по спецификации HTTP";
            default -> "идемпотентность не гарантируется спецификацией HTTP";
        };
    }

    private String movedEn() {
        if (before.size() != store.size()) {
            return base + " went from " + before.size() + " to " + store.size() + " resource(s)";
        }
        if (store.containsKey(touched) && !before.equals(store)) {
            return "the representation at " + touched + " is now {" + describe(store.get(touched)) + "}";
        }
        return "the side effect \"" + sideEffects.get(sideEffects.size() - 1) + "\" fired";
    }

    private String movedRu() {
        if (before.size() != store.size()) {
            return "в " + base + " было ресурсов: " + before.size() + ", стало: " + store.size();
        }
        if (store.containsKey(touched) && !before.equals(store)) {
            return "представление по адресу " + touched + " теперь {"
                    + describe(store.get(touched)) + "}";
        }
        return "сработал побочный эффект «" + sideEffects.get(sideEffects.size() - 1) + "»";
    }

    private String signature() {
        return kind.method + "|" + path + "|" + describe(requestBody) + "|" + key;
    }

    private static String ordinal(int n) {
        return switch (n) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> n + "th";
        };
    }

    private static String ordinalRu(int n) {
        return switch (n) {
            case 1 -> "первый";
            case 2 -> "второй";
            case 3 -> "третий";
            default -> n + "-й";
        };
    }

    private Map<String, Map<String, String>> snapshot() {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : store.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private String paths() {
        return store.isEmpty() ? "none" : String.join(", ", store.keySet());
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

    private static List<Object> pairs(Map<String, String> fields) {
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("field", entry.getKey());
            pair.put("value", entry.getValue());
            list.add(pair);
        }
        return list;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("base", base);

        List<Object> resources = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : store.entrySet()) {
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("path", entry.getKey());
            resource.put("fields", pairs(entry.getValue()));
            resource.put("mark", marks.getOrDefault(entry.getKey(), "kept"));
            resources.add(resource);
        }
        s.put("resources", resources);
        s.put("gone", List.copyOf(gone));

        List<Object> storedKeys = new ArrayList<>();
        for (Map.Entry<String, String> entry : keyStore.entrySet()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("key", entry.getKey());
            record.put("path", entry.getValue().substring(0, entry.getValue().indexOf('|')));
            record.put("status", entry.getValue().substring(entry.getValue().indexOf('|') + 1));
            storedKeys.add(record);
        }
        s.put("keys", storedKeys);
        s.put("sideEffects", List.copyOf(sideEffects));

        if (kind != null) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", kind.method);
            request.put("path", path);
            request.put("attempt", number);
            request.put("key", key);
            request.put("idempotentBySpec", idempotencyBySpec());
            request.put("relative", kind == Kind.PATCH_INCREMENT);
            request.put("body", pairs(requestBody));
            s.put("request", request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", status);
            response.put("delivered", delivered);
            response.put("replayed", replayed);
            s.put("response", response);
        }

        List<Object> rows = new ArrayList<>();
        for (Attempt attempt : ledger) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", attempt.seq);
            row.put("method", attempt.method);
            row.put("path", attempt.path);
            row.put("attempt", attempt.number);
            row.put("key", attempt.key);
            row.put("status", attempt.status);
            row.put("delivered", attempt.delivered);
            row.put("outcome", attempt.outcome);
            rows.add(row);
        }
        s.put("ledger", rows);

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("attempts", attempts);
        counters.put("lost", lost);
        counters.put("effects", effects);
        counters.put("duplicates", duplicates);
        counters.put("replays", replays);
        s.put("counters", counters);
        return s;
    }
}
