package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of one REST resource and of the two ways a client can
 * change it: {@code PUT} (send the whole representation you want stored) and
 * {@code PATCH} (send only the change).
 *
 * <p>The model owns a single document at one URL plus its {@code ETag}. Each
 * request is applied by the rules of the method that carried it, and the thing to
 * watch is which fields the server still holds afterwards:
 * <ul>
 *   <li>{@link #put(Body)} replaces the entire representation — a field the body
 *       forgot is not "left unchanged", it is gone;</li>
 *   <li>{@link #patch(Body)} applies JSON Merge Patch: named members are merged,
 *       an explicit {@code null} removes a member, unmentioned fields are
 *       untouched;</li>
 *   <li>{@link #patchAppend(String, String)} applies a JSON Patch style
 *       {@code add} operation to a list — the standard example of a PATCH that is
 *       <em>not</em> idempotent;</li>
 *   <li>{@link #putIfMatch(String, Body)} is the same PUT behind an
 *       {@code If-Match} precondition, so a stale write is refused instead of
 *       silently overwriting someone else's change.</li>
 * </ul>
 *
 * <p>A resource that does not exist yet ({@link #emptyAt(String)}) separates the
 * two methods once more: PUT creates it, PATCH has nothing to merge into.
 *
 * <p>The model remembers the previous request, so an example can simply send the
 * same call twice and the model reports whether the second one had any effect at
 * all. Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualHttpResource {

    private static final String MERGE_PATCH = "application/merge-patch+json";
    private static final String JSON_PATCH = "application/json-patch+json";

    /**
     * An ordered request body. A {@code null} value is a real JSON {@code null},
     * which is what makes the merge-patch "remove this member" rule visible.
     */
    public static final class Body {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private Body() {
        }

        /** Starts a body with one field. */
        public static Body of(String field, String value) {
            return new Body().and(field, value);
        }

        /** Starts an empty body. */
        public static Body empty() {
            return new Body();
        }

        /** Adds another field, keeping the order the example wrote them in. */
        public Body and(String field, String value) {
            fields.put(field, value);
            return this;
        }
    }

    private final String path;
    /** The stored representation: field -> value, in a stable display order. */
    private final Map<String, String> document = new LinkedHashMap<>();
    /** How the current request touched each surviving field: added|changed|same|kept. */
    private final Map<String, String> marks = new LinkedHashMap<>();
    /** Fields the current request took away: {name, last value}. */
    private final List<String[]> removed = new ArrayList<>();

    private boolean exists;
    private int version;
    private int requests;
    private int fieldsWiped;
    /** Signature of the previous request, so an identical repeat is recognizable. */
    private String previousSignature = "";

    private String method;
    private String kind;
    private String ifMatch;
    private Map<String, String> requestBody;
    private String responseStatus;
    private boolean applied;

    private VisualHttpResource(String path) {
        this.path = path;
    }

    /** A server already holding a representation at {@code path}, with ETag "v1". */
    public static VisualHttpResource serving(String path, Body body) {
        VisualHttpResource resource = new VisualHttpResource(path);
        resource.document.putAll(body.fields);
        resource.exists = true;
        resource.version = 1;
        Trace.event("RESOURCE_READY",
                "GET " + path + " -> 200 OK, ETag \"v1\", " + resource.document.size()
                        + " field(s): {" + describe(resource.document) + "}",
                "GET " + path + " -> 200 OK, ETag \"v1\", полей: " + resource.document.size()
                        + " {" + describe(resource.document) + "}",
                List.of(), resource.state());
        return resource;
    }

    /** A URL with nothing stored at it yet. */
    public static VisualHttpResource emptyAt(String path) {
        VisualHttpResource resource = new VisualHttpResource(path);
        Trace.event("RESOURCE_READY",
                "GET " + path + " -> 404 Not Found: nothing is stored at this URL yet",
                "GET " + path + " -> 404 Not Found: по этому URL пока ничего не хранится",
                List.of(), resource.state());
        return resource;
    }

    /** The current entity tag ("v2"), or an empty string while the resource is absent. */
    public String etag() {
        return exists ? "v" + version : "";
    }

    /** PUT: "make this URL hold exactly this representation". */
    public void put(Body body) {
        doPut(body, null);
    }

    /** PUT behind an {@code If-Match} precondition: apply only if nothing moved since. */
    public void putIfMatch(String etag, Body body) {
        doPut(body, etag);
    }

    private void doPut(Body body, String precondition) {
        beginRequest("PUT", "full", precondition, body.fields);
        Trace.event("REQUEST_SENT",
                "PUT " + path + " carries the WHOLE representation the client wants stored: {"
                        + describe(requestBody) + "}"
                        + (precondition == null ? "" : ", If-Match: \"" + precondition + "\""),
                "PUT " + path + " несёт ЦЕЛИКОМ то представление, которое клиент хочет сохранить: {"
                        + describe(requestBody) + "}"
                        + (precondition == null ? "" : ", If-Match: \"" + precondition + "\""),
                List.of("request"), state());

        if (precondition != null && !precondition.equals(etag())) {
            responseStatus = "412 Precondition Failed";
            Trace.event("PRECONDITION_FAILED",
                    "The client built this body from \"" + precondition + "\", but the stored "
                            + "representation is " + etagLabelEn() + " — the server refuses the write "
                            + "instead of letting it overwrite a change the client never saw",
                    "Клиент собрал это тело по версии \"" + precondition + "\", но на сервере сейчас "
                            + etagLabelRu() + " — сервер отклоняет запись, вместо того чтобы дать ей "
                            + "затереть изменение, которого клиент не видел",
                    List.of("request"), state());
            endRequest();
            return;
        }

        if (!exists) {
            exists = true;
            version = 1;
            document.putAll(requestBody);
            for (String field : document.keySet()) {
                marks.put(field, "added");
            }
            responseStatus = "201 Created";
            applied = true;
            Trace.event("PUT_CREATED",
                    "Nothing was stored at " + path + ", and PUT means 'make this URL hold exactly "
                            + "this representation' — so the server creates it: 201 Created, ETag \"v1\"",
                    "По адресу " + path + " ничего не было, а PUT означает «сделай так, чтобы по этому "
                            + "URL лежало именно такое представление» — сервер создаёт его: 201 Created, "
                            + "ETag \"v1\"",
                    highlights(), state());
            endRequest();
            return;
        }

        Map<String, String> before = new LinkedHashMap<>(document);
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (!requestBody.containsKey(entry.getKey())) {
                removed.add(new String[]{entry.getKey(), entry.getValue()});
            }
        }

        // Keep the stored order for surviving fields, then append fields the body added.
        Map<String, String> next = new LinkedHashMap<>();
        for (String field : before.keySet()) {
            if (requestBody.containsKey(field)) {
                next.put(field, requestBody.get(field));
            }
        }
        next.putAll(requestBody);
        document.clear();
        document.putAll(next);

        List<String> changedFields = new ArrayList<>();
        List<String> addedFields = new ArrayList<>();
        for (Map.Entry<String, String> entry : document.entrySet()) {
            String field = entry.getKey();
            if (!before.containsKey(field)) {
                marks.put(field, "added");
                addedFields.add(field);
            } else if (same(before.get(field), entry.getValue())) {
                marks.put(field, "same");
            } else {
                marks.put(field, "changed");
                changedFields.add(field + ": " + before.get(field) + " -> " + entry.getValue());
            }
        }

        boolean changed = !before.equals(document);
        if (changed) {
            version++;
        }
        responseStatus = "200 OK";
        applied = true;

        Trace.event("PUT_REPLACED",
                "PUT replaced the ENTIRE representation with the body — "
                        + (changedFields.isEmpty() ? "no value changed" : "changed " + join(changedFields))
                        + (addedFields.isEmpty() ? "" : ", added " + join(addedFields))
                        + ". ETag is now \"" + etag() + "\"",
                "PUT заменил представление ЦЕЛИКОМ телом запроса — "
                        + (changedFields.isEmpty() ? "ни одно значение не изменилось"
                            : "изменено " + join(changedFields))
                        + (addedFields.isEmpty() ? "" : ", добавлено " + join(addedFields))
                        + ". Теперь ETag \"" + etag() + "\"",
                highlights(), state());

        if (!removed.isEmpty()) {
            fieldsWiped += removed.size();
            Trace.event("FIELD_LOST",
                    "The body never mentioned " + join(removedNames()) + ", and 'replace everything' "
                            + "includes the fields you forgot: they are gone from the resource, not "
                            + "merely left as they were",
                    "Тело запроса вообще не упомянуло " + join(removedNames()) + ", а «заменить всё» "
                            + "включает и те поля, о которых вы забыли: они исчезли из ресурса, а не "
                            + "остались прежними",
                    highlights(), state());
        }

        endRequestAndCheckRepeat(changed);
    }

    /**
     * PATCH with JSON Merge Patch semantics: the body lists only the members to
     * change, and an explicit {@code null} removes a member.
     */
    public void patch(Body body) {
        beginRequest("PATCH", "merge-patch", null, body.fields);
        Trace.event("REQUEST_SENT",
                "PATCH " + path + " (Content-Type: " + MERGE_PATCH + ") carries ONLY the change: {"
                        + describe(requestBody) + "}",
                "PATCH " + path + " (Content-Type: " + MERGE_PATCH + ") несёт ТОЛЬКО изменение: {"
                        + describe(requestBody) + "}",
                List.of("request"), state());

        if (!exists) {
            responseStatus = "404 Not Found";
            Trace.event("PATCH_NOT_FOUND",
                    "A patch describes how to change an existing representation, and there is nothing "
                            + "at " + path + " to merge into — 404. The very same field list sent as a "
                            + "PUT would have created the resource",
                    "Патч описывает, как изменить существующее представление, а по адресу " + path
                            + " сливать не с чем — 404. Тот же самый набор полей, отправленный как PUT, "
                            + "создал бы ресурс",
                    List.of("request"), state());
            endRequest();
            return;
        }

        Map<String, String> before = new LinkedHashMap<>(document);
        List<String> written = new ArrayList<>();
        List<String> nulled = new ArrayList<>();

        for (Map.Entry<String, String> entry : requestBody.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();
            if (value == null) {
                if (document.containsKey(field)) {
                    removed.add(new String[]{field, document.remove(field)});
                    nulled.add(field);
                }
            } else if (!document.containsKey(field)) {
                document.put(field, value);
                marks.put(field, "added");
                written.add(field + ": " + value);
            } else if (same(document.get(field), value)) {
                marks.put(field, "same");
            } else {
                written.add(field + ": " + document.get(field) + " -> " + value);
                document.put(field, value);
                marks.put(field, "changed");
            }
        }
        markUntouched();

        boolean changed = !before.equals(document);
        if (changed) {
            version++;
        }
        responseStatus = "200 OK";
        applied = true;

        int kept = countKept();
        Trace.event("PATCH_MERGED",
                "PATCH merged the body into the stored representation: "
                        + (written.isEmpty() ? "no value needed changing" : "wrote " + join(written))
                        + ". " + kept + " field(s) the body did not mention are exactly as they were. "
                        + "ETag is now \"" + etag() + "\"",
                "PATCH влил тело в хранимое представление: "
                        + (written.isEmpty() ? "менять было нечего" : "записано " + join(written))
                        + ". Полей, о которых тело не упоминало: " + kept
                        + " — они остались ровно такими же. Теперь ETag \"" + etag() + "\"",
                highlights(), state());

        if (!nulled.isEmpty()) {
            Trace.event("MERGE_PATCH_NULL",
                    "In JSON Merge Patch an explicit null does not mean 'store null' — it means REMOVE "
                            + "the member, so " + join(nulled) + " is gone. That is also why merge patch "
                            + "cannot store a null value at all; JSON Patch (" + JSON_PATCH + ") can, "
                            + "because there the operation is named instead of implied",
                    "В JSON Merge Patch явный null означает не «сохранить null», а УДАЛИТЬ поле, "
                            + "поэтому " + join(nulled) + " больше нет. По этой же причине merge patch "
                            + "вообще не может сохранить значение null; JSON Patch (" + JSON_PATCH
                            + ") может, потому что там операция названа явно, а не подразумевается",
                    highlights(), state());
        }

        endRequestAndCheckRepeat(changed);
    }

    /**
     * PATCH with JSON Patch semantics: the body is an operation, not a target
     * value. {@code {"op":"add","path":"/<field>/-","value":...}} appends to a
     * list, so running it twice appends twice.
     */
    public void patchAppend(String listField, String value) {
        Map<String, String> operation = new LinkedHashMap<>();
        operation.put("add /" + listField + "/-", value);
        beginRequest("PATCH", "json-patch", null, operation);
        Trace.event("REQUEST_SENT",
                "PATCH " + path + " (Content-Type: " + JSON_PATCH + ") carries an OPERATION, not a "
                        + "target value: [{\"op\":\"add\",\"path\":\"/" + listField + "/-\",\"value\":\""
                        + value + "\"}]",
                "PATCH " + path + " (Content-Type: " + JSON_PATCH + ") несёт ОПЕРАЦИЮ, а не желаемое "
                        + "значение: [{\"op\":\"add\",\"path\":\"/" + listField + "/-\",\"value\":\""
                        + value + "\"}]",
                List.of("request"), state());

        if (!exists) {
            responseStatus = "404 Not Found";
            Trace.event("PATCH_NOT_FOUND",
                    "There is nothing at " + path + " to apply the operation to — 404",
                    "По адресу " + path + " не к чему применить операцию — 404",
                    List.of("request"), state());
            endRequest();
            return;
        }

        String current = document.get(listField);
        String next = current == null || current.isEmpty() ? value : current + "," + value;
        marks.put(listField, document.containsKey(listField) ? "changed" : "added");
        document.put(listField, next);
        markUntouched();

        version++;
        responseStatus = "200 OK";
        applied = true;
        Trace.event("PATCH_MERGED",
                "The operation appended \"" + value + "\" to " + listField + ": ["
                        + (current == null ? "" : current) + "] -> [" + next + "]. ETag is now \""
                        + etag() + "\"",
                "Операция добавила \"" + value + "\" в " + listField + ": ["
                        + (current == null ? "" : current) + "] -> [" + next + "]. Теперь ETag \""
                        + etag() + "\"",
                highlights(), state());

        endRequestAndCheckRepeat(true);
    }

    /** Prints what the resource looks like after everything the example did. */
    public void report() {
        Trace.event("RESOURCE_AUDIT",
                "After " + requests + " request(s) " + path + " holds " + document.size()
                        + " field(s): {" + describe(document) + "}, ETag " + etagLabelEn()
                        + "; fields wiped because a PUT body omitted them: " + fieldsWiped,
                "После запросов (" + requests + ") по адресу " + path + " хранится полей: "
                        + document.size() + " {" + describe(document) + "}, ETag " + etagLabelRu()
                        + "; полей стёрто из-за того, что тело PUT их не упомянуло: " + fieldsWiped,
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    private void beginRequest(String httpMethod, String bodyKind, String precondition,
                              Map<String, String> body) {
        requests++;
        method = httpMethod;
        kind = bodyKind;
        ifMatch = precondition;
        requestBody = new LinkedHashMap<>(body);
        responseStatus = "pending";
        applied = false;
        marks.clear();
        removed.clear();
    }

    /** Closes a request that must not be reported as an idempotency observation. */
    private void endRequest() {
        previousSignature = signature();
    }

    /**
     * Closes a request and, when it was an exact repeat of the previous one,
     * says whether the second call had any effect.
     */
    private void endRequestAndCheckRepeat(boolean changed) {
        String signature = signature();
        if (signature.equals(previousSignature)) {
            if (changed) {
                Trace.event("NON_IDEMPOTENT_REPEAT",
                        "The identical request ran a SECOND time and changed the resource AGAIN. This "
                                + "body says 'append', not 'end up like this', so N calls give N "
                                + "results — nothing in HTTP promises that a PATCH is idempotent",
                        "Тот же самый запрос выполнился ВТОРОЙ раз и СНОВА изменил ресурс. Это тело "
                                + "говорит «добавь», а не «пусть станет так», поэтому N вызовов дают N "
                                + "результатов — HTTP нигде не обещает, что PATCH идемпотентен",
                        highlights(), state());
            } else {
                Trace.event("IDEMPOTENT_REPEAT",
                        "The identical request ran a SECOND time and changed nothing: same fields, "
                                + "same ETag \"" + etag() + "\". Idempotent means one call and N "
                                + "identical calls leave the same state — not that the responses are "
                                + "byte-identical",
                        "Тот же самый запрос выполнился ВТОРОЙ раз и ничего не изменил: те же поля, "
                                + "тот же ETag \"" + etag() + "\". Идемпотентность означает, что один "
                                + "вызов и N одинаковых вызовов оставляют одно и то же состояние, а не "
                                + "что ответы побайтово совпадают",
                        highlights(), state());
            }
        }
        previousSignature = signature;
    }

    /** Identity of a request, so an exact repeat of it can be recognised. */
    private String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append('|').append(kind).append('|').append(ifMatch).append('|');
        if (requestBody != null) {
            for (Map.Entry<String, String> entry : requestBody.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
            }
        }
        return sb.toString();
    }

    /** Fields the request did not mention keep their value and say so. */
    private void markUntouched() {
        for (String field : document.keySet()) {
            marks.putIfAbsent(field, "kept");
        }
    }

    private int countKept() {
        int kept = 0;
        for (String mark : marks.values()) {
            if ("kept".equals(mark)) {
                kept++;
            }
        }
        return kept;
    }

    private List<String> removedNames() {
        List<String> names = new ArrayList<>();
        for (String[] field : removed) {
            names.add(field[0]);
        }
        return names;
    }

    private List<String> highlights() {
        List<String> tokens = new ArrayList<>();
        tokens.add("request");
        for (Map.Entry<String, String> mark : marks.entrySet()) {
            if (!"kept".equals(mark.getValue()) && !"same".equals(mark.getValue())) {
                tokens.add("field:" + mark.getKey());
            }
        }
        for (String[] field : removed) {
            tokens.add("field:" + field[0]);
        }
        return tokens;
    }

    private String etagLabelEn() {
        return exists ? "\"" + etag() + "\"" : "absent";
    }

    private String etagLabelRu() {
        return exists ? "\"" + etag() + "\"" : "отсутствует";
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
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

    private static String join(List<String> parts) {
        return String.join(", ", parts);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("path", path);
        s.put("exists", exists);
        s.put("etag", etag());

        List<Object> fields = new ArrayList<>();
        for (Map.Entry<String, String> entry : document.entrySet()) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("field", entry.getKey());
            field.put("value", entry.getValue());
            field.put("status", marks.getOrDefault(entry.getKey(), "kept"));
            fields.add(field);
        }
        s.put("document", fields);

        List<Object> gone = new ArrayList<>();
        for (String[] field : removed) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", field[0]);
            entry.put("value", field[1]);
            gone.add(entry);
        }
        s.put("removed", gone);

        if (requestBody != null) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", method);
            request.put("kind", kind);
            request.put("ifMatch", ifMatch);
            List<Object> body = new ArrayList<>();
            for (Map.Entry<String, String> entry : requestBody.entrySet()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("field", entry.getKey());
                field.put("value", entry.getValue());
                body.add(field);
            }
            request.put("body", body);
            s.put("request", request);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", responseStatus);
            response.put("applied", applied);
            s.put("response", response);
        }

        s.put("requests", requests);
        s.put("fieldsWiped", fieldsWiped);
        return s;
    }
}
