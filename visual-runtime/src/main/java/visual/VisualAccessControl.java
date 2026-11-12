package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of an orders API in which every caller is already
 * logged in, so the only question left is <em>authorization</em>: may this
 * caller do this, to this record, with these fields?
 *
 * <p>Each request walks the same four checks, named after the layers OWASP uses
 * for broken access control in APIs:
 * <ul>
 *   <li><b>authentication</b> — who is calling. Always passes here; the topic is
 *       what happens after it;</li>
 *   <li><b>functionLevel</b> — may this caller use this endpoint at all (a role
 *       check on {@code /admin/**}). Missing it is <em>vertical</em> escalation;</li>
 *   <li><b>objectLevel</b> — may this caller touch <em>this</em> record, or see
 *       these rows of a list. Missing it is IDOR, <em>horizontal</em>
 *       escalation;</li>
 *   <li><b>propertyLevel</b> — may this caller set <em>these fields</em>. Binding
 *       the request body straight onto the entity is <em>mass assignment</em>;</li>
 *   <li><b>handler</b> — the business code.</li>
 * </ul>
 *
 * <p>Every defense starts switched off in {@link #naive()} and on in
 * {@link #secure()}, and {@link #enforce(Defense)} turns one on, so an example
 * can show the same request served as a breach and then refused. A request that
 * runs and should not have is reported as {@code decision: "breach"}.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualAccessControl {

    /** The checks, in the order every request walks them. */
    private static final List<String> GATES = List.of(
            "authentication", "functionLevel", "objectLevel", "propertyLevel", "handler");

    /** Order fields a client may set: everything else is decided by the server. */
    private static final Set<String> ORDER_EDITABLE = Set.of("note");
    /** Order fields the server owns. */
    private static final Set<String> ORDER_SERVER_FIELDS = Set.of("id", "owner", "price", "status");
    /** Profile fields a client may set. */
    private static final Set<String> PROFILE_EDITABLE = Set.of("displayName");
    /** Profile fields the server owns. */
    private static final Set<String> PROFILE_SERVER_FIELDS = Set.of("role");

    // ---------------------------------------------------------------- defenses

    /** One server-side defense; each closes one kind of hole. */
    public enum Defense {
        /** Reads load the record by id AND owner, not by id alone. */
        OWNER_ON_READ("ownerOnRead",
                "ownership check on reads", "проверка владельца при чтении"),
        /** Updates and deletes check ownership of the record they change. */
        OWNER_ON_WRITE("ownerOnWrite",
                "ownership check on writes", "проверка владельца при записи"),
        /** List queries take the owner from the session, never from a parameter. */
        SCOPED_LISTS("scopedLists",
                "lists filtered by the caller", "списки фильтруются по вызывающему"),
        /** {@code /admin/**} checks the role on the server, whatever the UI shows. */
        ROLE_CHECK("roleCheck",
                "server-side role check on /admin", "серверная проверка роли на /admin"),
        /** The body binds into a DTO that only has the fields a client may set. */
        FIELD_ALLOWLIST("fieldAllowlist",
                "request DTO with an allowlist of fields", "DTO запроса со списком разрешённых полей"),
        /** Someone else's record answers 404, so the refusal does not confirm it exists. */
        NOT_FOUND_FOR_FOREIGN("notFoundForForeign",
                "404 for someone else's record", "404 на чужую запись");

        private final String key;
        private final String en;
        private final String ru;

        Defense(String key, String en, String ru) {
            this.key = key;
            this.en = en;
            this.ru = ru;
        }
    }

    // ----------------------------------------------------------------- callers

    /** A logged-in user. Roles live in the API, so a mass-assigned role sticks. */
    public static final class Caller {
        private final String subject;

        private Caller(String subject) {
            this.subject = subject;
        }

        /** The user id taken from the verified session or token. */
        public String subject() {
            return subject;
        }

        @Override
        public String toString() {
            return subject;
        }
    }

    private static final class Account {
        final String subject;
        String role;
        String displayName;

        Account(String subject, String role) {
            this.subject = subject;
            this.role = role;
            this.displayName = subject;
        }
    }

    private static final class Order {
        String id;
        String owner;
        int price;
        String status = "NEW";
        String note = "";
        boolean exposed;
        boolean tampered;
        boolean deleted;
        boolean inResponse;

        Order(String id, String owner, int price) {
            this.id = id;
            this.owner = owner;
            this.price = price;
        }
    }

    // ------------------------------------------------------------------- state

    private final Map<Defense, Boolean> defenses = new LinkedHashMap<>();
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Order> orders = new LinkedHashMap<>();

    private Map<String, Object> request;
    private final Map<String, Map<String, Object>> gates = new LinkedHashMap<>();
    private Map<String, Object> outcome;
    private final Set<String> breachKinds = new LinkedHashSet<>();

    private int requests;
    private int allowed;
    private int denied;
    private int breaches;
    private int horizontal;
    private int vertical;
    private int massAssignment;
    private int leakedRows;

    private VisualAccessControl(boolean on) {
        for (Defense d : Defense.values()) {
            defenses.put(d, on);
        }
        resetGates();
    }

    /**
     * An API that trusts the caller to ask only for their own things: every
     * endpoint requires a login and nothing else.
     */
    public static VisualAccessControl naive() {
        VisualAccessControl api = new VisualAccessControl(false);
        api.emit("API_SETUP",
                "An orders API that only checks the caller is logged in. Which record, which endpoint and which fields are all left to the client.",
                "API заказов, которое проверяет только то, что вызывающий вошёл в систему. Какая запись, какой эндпоинт и какие поля — всё оставлено на усмотрение клиента.",
                List.of());
        return api;
    }

    /** An API with every defense of this model switched on. */
    public static VisualAccessControl secure() {
        VisualAccessControl api = new VisualAccessControl(true);
        api.emit("API_SETUP",
                "An orders API that decides on the server: ownership on reads, writes and lists, a role check on /admin, an allowlist of fields, and 404 for someone else's record.",
                "API заказов, которое всё решает на сервере: владелец при чтении, записи и в списках, проверка роли на /admin, список разрешённых полей и 404 на чужую запись.",
                List.of());
        return api;
    }

    /** Switches one defense on. */
    public VisualAccessControl enforce(Defense defense) {
        defenses.put(defense, true);
        emit("DEFENSE_ENABLED",
                "Defense switched on: " + defense.en + ".",
                "Включена защита: " + defense.ru + ".",
                List.of("defense:" + defense.key));
        return this;
    }

    /** Registers an ordinary user (role USER). */
    public Caller user(String subject) {
        return account(subject, "USER");
    }

    /** Registers an administrator (role ADMIN). */
    public Caller admin(String subject) {
        return account(subject, "ADMIN");
    }

    private Caller account(String subject, String role) {
        accounts.put(subject, new Account(subject, role));
        emit("USER_ADDED",
                "User " + subject + " signs in with role " + role + ".",
                "Пользователь " + subject + " входит с ролью " + role + ".",
                List.of("caller:" + subject));
        return new Caller(subject);
    }

    /** Stores an order owned by {@code owner}. */
    public VisualAccessControl order(String id, String owner, int price) {
        orders.put(id, new Order(id, owner, price));
        emit("RECORD_ADDED",
                "Order " + id + " belongs to " + owner + " (price " + price + ").",
                "Заказ " + id + " принадлежит " + owner + " (цена " + price + ").",
                List.of("record:" + id));
        return this;
    }

    // ---------------------------------------------------------------- requests

    /** {@code GET /orders/{id}}: read one order. */
    public void get(Caller caller, String id) {
        begin(caller, "GET", "/orders/" + id, List.of());
        gate("functionLevel", "passed", "USER");
        Order order = find(id);
        if (order == null) {
            notFound(id);
            return;
        }
        if (!checkOwnership(caller, order, enabled(Defense.OWNER_ON_READ), "read")) {
            return;
        }
        if (!order.owner.equals(caller.subject)) {
            order.exposed = true;
            leakedRows++;
        }
        order.inResponse = true;
        gate("propertyLevel", "skipped", "");
        handler(200, "order " + id + " returned", "заказ " + id + " возвращён", List.of("record:" + id));
    }

    /** {@code GET /orders}: list the orders the API thinks are the caller's. */
    public void list(Caller caller) {
        listOrders(caller, null);
    }

    /** {@code GET /orders?ownerId=...}: list with an owner filter the client chose. */
    public void listByOwner(Caller caller, String ownerId) {
        listOrders(caller, ownerId);
    }

    private void listOrders(Caller caller, String ownerParam) {
        begin(caller, "GET", ownerParam == null ? "/orders" : "/orders?ownerId=" + ownerParam, List.of());
        gate("functionLevel", "passed", "USER");
        List<Order> rows = new ArrayList<>();
        boolean scoped = enabled(Defense.SCOPED_LISTS);
        for (Order o : orders.values()) {
            if (o.deleted) {
                continue;
            }
            boolean match;
            if (scoped) {
                match = o.owner.equals(caller.subject);
            } else {
                match = ownerParam == null || o.owner.equals(ownerParam);
            }
            if (match) {
                rows.add(o);
            }
        }
        List<String> hl = new ArrayList<>();
        int foreign = 0;
        for (Order o : rows) {
            o.inResponse = true;
            hl.add("record:" + o.id);
            if (!o.owner.equals(caller.subject)) {
                o.exposed = true;
                foreign++;
            }
        }
        if (scoped) {
            gate("objectLevel", "passed", "owner = " + caller.subject);
            String ignoredEn = ownerParam != null && !ownerParam.equals(caller.subject)
                    ? " The ownerId=" + ownerParam + " parameter is ignored: the owner comes from the session."
                    : "";
            String ignoredRu = ownerParam != null && !ownerParam.equals(caller.subject)
                    ? " Параметр ownerId=" + ownerParam + " игнорируется: владелец берётся из сессии."
                    : "";
            emit("LIST_SCOPED",
                    "The query is WHERE owner = '" + caller.subject + "', taken from the session: "
                            + rows.size() + " row(s), all " + caller.subject + "'s." + ignoredEn,
                    "Запрос — WHERE owner = '" + caller.subject + "', владелец взят из сессии: "
                            + rows.size() + " строк(и), все принадлежат " + caller.subject + "." + ignoredRu,
                    hl);
        } else if (foreign > 0) {
            leakedRows += foreign;
            breachKinds.add("horizontal");
            gate("objectLevel", "breach", foreign + " foreign");
            String filter = ownerParam == null ? "no owner filter at all" : "the owner the client asked for (" + ownerParam + ")";
            String filterRu = ownerParam == null ? "вообще без фильтра по владельцу" : "по владельцу, которого попросил клиент (" + ownerParam + ")";
            emit("LIST_LEAKED",
                    "The list is filtered by " + filter + ": " + foreign + " of " + rows.size()
                            + " row(s) belong to somebody else. IDOR on a collection — no id was even guessed.",
                    "Список отфильтрован " + filterRu + ": " + foreign + " из " + rows.size()
                            + " строк(и) принадлежат другим. IDOR на коллекции — даже id угадывать не пришлось.",
                    hl);
        } else {
            gate("objectLevel", "passed", ownerParam == null ? "all rows" : "ownerId=" + ownerParam);
        }
        gate("propertyLevel", "skipped", "");
        handler(200, rows.size() + " row(s) returned", "возвращено строк: " + rows.size(), hl);
    }

    /**
     * {@code PATCH /orders/{id}} with a body of field/value pairs, e.g.
     * {@code update(alice, "7", "note", "ring twice", "price", "1")}.
     */
    public void update(Caller caller, String id, String... fieldValuePairs) {
        Map<String, String> body = pairs(fieldValuePairs);
        begin(caller, "PATCH", "/orders/" + id, bodyState(body, ORDER_EDITABLE));
        gate("functionLevel", "passed", "USER");
        Order order = find(id);
        if (order == null) {
            notFound(id);
            return;
        }
        if (!checkOwnership(caller, order, enabled(Defense.OWNER_ON_WRITE), "write")) {
            return;
        }
        Order target = order;
        boolean allowlist = enabled(Defense.FIELD_ALLOWLIST);
        List<String> assigned = new ArrayList<>();
        List<String> hl = new ArrayList<>();
        hl.add("record:" + id);

        String bodyId = body.get("id");
        if (bodyId != null && !bodyId.equals(id)) {
            if (allowlist) {
                ignored("id", bodyId, "record:" + id);
            } else {
                Order other = find(bodyId);
                if (other != null) {
                    target = other;
                    hl.add("record:" + bodyId);
                    assigned.add("id");
                    breachKinds.add("massAssignment");
                    if (!other.owner.equals(caller.subject)) {
                        breachKinds.add("horizontal");
                    }
                    emit("BODY_ID_OVERRIDE",
                            "The ownership check ran on /orders/" + id + ", but the body says id=" + bodyId
                                    + " and the entity is saved by that id. The write lands on " + other.owner
                                    + "'s order, which nobody checked.",
                            "Проверка владельца прошла для /orders/" + id + ", но в теле id=" + bodyId
                                    + ", и сущность сохраняется по нему. Запись попадает в заказ "
                                    + other.owner + ", который никто не проверял.",
                            List.of("record:" + id, "record:" + bodyId));
                }
            }
        }
        for (Map.Entry<String, String> e : body.entrySet()) {
            String field = e.getKey();
            String value = e.getValue();
            if (field.equals("id")) {
                continue;
            }
            if (ORDER_EDITABLE.contains(field)) {
                target.note = value;
                continue;
            }
            if (allowlist || !ORDER_SERVER_FIELDS.contains(field)) {
                ignored(field, value, "record:" + target.id);
                continue;
            }
            String before = orderField(target, field);
            setOrderField(target, field, value);
            assigned.add(field);
            breachKinds.add("massAssignment");
            emit("MASS_ASSIGNMENT",
                    "The body is bound straight onto the entity, so the client just set the server-owned field "
                            + field + ": " + before + " → " + value + ".",
                    "Тело запроса привязано прямо к сущности, и клиент только что выставил серверное поле "
                            + field + ": " + before + " → " + value + ".",
                    List.of("record:" + target.id, "field:" + field));
        }
        if (!target.owner.equals(caller.subject) || !assigned.isEmpty()) {
            target.tampered = true;
        }
        gate("propertyLevel", assigned.isEmpty() ? "passed" : "breach",
                assigned.isEmpty() ? String.join(", ", ORDER_EDITABLE) : String.join(", ", assigned));
        handler(200, "order " + target.id + " saved", "заказ " + target.id + " сохранён", hl);
    }

    /** {@code DELETE /orders/{id}}. */
    public void delete(Caller caller, String id) {
        begin(caller, "DELETE", "/orders/" + id, List.of());
        gate("functionLevel", "passed", "USER");
        Order order = find(id);
        if (order == null) {
            notFound(id);
            return;
        }
        if (!checkOwnership(caller, order, enabled(Defense.OWNER_ON_WRITE), "write")) {
            return;
        }
        order.deleted = true;
        if (!order.owner.equals(caller.subject)) {
            order.tampered = true;
        }
        gate("propertyLevel", "skipped", "");
        handler(204, "order " + id + " deleted", "заказ " + id + " удалён", List.of("record:" + id));
    }

    /** {@code GET /admin/orders}: every order of every user. Admins only. */
    public void adminExport(Caller caller) {
        begin(caller, "GET", "/admin/orders", List.of());
        String role = accounts.get(caller.subject).role;
        if (!role.equals("ADMIN")) {
            if (enabled(Defense.ROLE_CHECK)) {
                gate("functionLevel", "denied", role);
                deny("ROLE_DENIED", 403, "role", role,
                        "The server checks the role itself: " + caller.subject + " is " + role
                                + ", /admin needs ADMIN. 403, whatever the frontend did or did not show.",
                        "Сервер сам проверяет роль: у " + caller.subject + " роль " + role
                                + ", а /admin требует ADMIN. 403 — что бы ни показывал или скрывал фронтенд.",
                        List.of("gate:functionLevel", "caller:" + caller.subject));
                return;
            }
            breachKinds.add("vertical");
            gate("functionLevel", "breach", role);
            emit("ROLE_CHECK_MISSING",
                    "Nothing on the server checks the role: the admin button is hidden in the UI, but "
                            + caller.subject + " (" + role + ") typed the URL. Vertical escalation.",
                    "На сервере роль никто не проверяет: кнопка администратора скрыта в UI, но "
                            + caller.subject + " (" + role + ") просто набрал URL. Вертикальное повышение привилегий.",
                    List.of("gate:functionLevel", "caller:" + caller.subject));
        } else {
            gate("functionLevel", "passed", "ADMIN");
        }
        List<String> hl = new ArrayList<>();
        int foreign = 0;
        for (Order o : orders.values()) {
            if (o.deleted) {
                continue;
            }
            o.inResponse = true;
            hl.add("record:" + o.id);
            if (!role.equals("ADMIN") && !o.owner.equals(caller.subject)) {
                o.exposed = true;
                foreign++;
            }
        }
        leakedRows += foreign;
        gate("objectLevel", "skipped", "all");
        gate("propertyLevel", "skipped", "");
        handler(200, hl.size() + " row(s) exported", "выгружено строк: " + hl.size(), hl);
    }

    /**
     * {@code PATCH /users/me}: the caller edits their own profile, e.g.
     * {@code updateProfile(alice, "displayName", "Al", "role", "ADMIN")}.
     */
    public void updateProfile(Caller caller, String... fieldValuePairs) {
        Map<String, String> body = pairs(fieldValuePairs);
        begin(caller, "PATCH", "/users/me", bodyState(body, PROFILE_EDITABLE));
        gate("functionLevel", "passed", "USER");
        gate("objectLevel", "passed", "me = " + caller.subject);
        Account account = accounts.get(caller.subject);
        boolean allowlist = enabled(Defense.FIELD_ALLOWLIST);
        List<String> assigned = new ArrayList<>();
        for (Map.Entry<String, String> e : body.entrySet()) {
            String field = e.getKey();
            String value = e.getValue();
            if (PROFILE_EDITABLE.contains(field)) {
                account.displayName = value;
                continue;
            }
            if (allowlist || !PROFILE_SERVER_FIELDS.contains(field)) {
                ignored(field, value, "caller:" + caller.subject);
                continue;
            }
            String before = account.role;
            account.role = value;
            assigned.add(field);
            breachKinds.add("massAssignment");
            if (value.equals("ADMIN") && !before.equals("ADMIN")) {
                breachKinds.add("vertical");
            }
            emit("PRIVILEGE_ESCALATED",
                    "The profile body is bound onto the User entity, role included: " + caller.subject
                            + " is now " + value + " (was " + before + "). Mass assignment turned into vertical escalation.",
                    "Тело профиля привязано к сущности User вместе с ролью: " + caller.subject
                            + " теперь " + value + " (был " + before + "). Mass assignment превратился в вертикальное повышение привилегий.",
                    List.of("caller:" + caller.subject, "field:" + field));
        }
        gate("propertyLevel", assigned.isEmpty() ? "passed" : "breach",
                assigned.isEmpty() ? String.join(", ", PROFILE_EDITABLE) : String.join(", ", assigned));
        handler(200, "profile saved", "профиль сохранён", List.of("caller:" + caller.subject));
    }

    /** Prints and emits the totals of the run. */
    public void report() {
        String line = "audit: requests " + requests + ", served " + allowed + ", refused " + denied
                + ", served that should NOT have been " + breaches
                + " (horizontal " + horizontal + ", vertical " + vertical
                + ", mass assignment " + massAssignment + "), foreign rows leaked " + leakedRows;
        System.out.println(line);
        resetGates();
        request = null;
        outcome = null;
        emit("ACCESS_AUDIT",
                "Audit: " + requests + " request(s), " + breaches + " served that should not have been ("
                        + horizontal + " horizontal, " + vertical + " vertical, " + massAssignment
                        + " mass assignment), " + leakedRows + " foreign row(s) leaked.",
                "Итог: запросов " + requests + ", обслужено зря " + breaches + " (горизонтальных "
                        + horizontal + ", вертикальных " + vertical + ", mass assignment " + massAssignment
                        + "), чужих строк утекло " + leakedRows + ".",
                List.of());
    }

    // ------------------------------------------------------------------ checks

    /**
     * The object-level gate. Returns false when the request was refused.
     */
    private boolean checkOwnership(Caller caller, Order order, boolean enforced, String kind) {
        if (order.owner.equals(caller.subject)) {
            gate("objectLevel", "passed", "owner = " + order.owner);
            return true;
        }
        if (enforced) {
            gate("objectLevel", "denied", "owner = " + order.owner);
            boolean hide = enabled(Defense.NOT_FOUND_FOR_FOREIGN);
            if (hide) {
                deny("OWNERSHIP_DENIED", 404, "not-owner", order.owner,
                        "Order " + order.id + " belongs to " + order.owner + ", not " + caller.subject
                                + ". The query is WHERE id = ? AND owner = ?, finds nothing and answers 404 — exactly what a missing id answers.",
                        "Заказ " + order.id + " принадлежит " + order.owner + ", а не " + caller.subject
                                + ". Запрос WHERE id = ? AND owner = ? ничего не находит и отвечает 404 — так же, как на несуществующий id.",
                        List.of("gate:objectLevel", "record:" + order.id));
            } else {
                deny("OWNERSHIP_DENIED", 403, "not-owner", order.owner,
                        "Order " + order.id + " belongs to " + order.owner + ", not " + caller.subject
                                + ". Refused with 403 — which also confirms that order " + order.id + " exists.",
                        "Заказ " + order.id + " принадлежит " + order.owner + ", а не " + caller.subject
                                + ". Отказ 403 — который заодно подтверждает, что заказ " + order.id + " существует.",
                        List.of("gate:objectLevel", "record:" + order.id));
            }
            return false;
        }
        breachKinds.add("horizontal");
        gate("objectLevel", "breach", "owner = " + order.owner);
        String opEn = kind.equals("read") ? "loaded by id alone" : "changed by id alone";
        String opRu = kind.equals("read") ? "загружается только по id" : "меняется только по id";
        emit("OWNERSHIP_CHECK_MISSING",
                "Order " + order.id + " belongs to " + order.owner + ", but it is " + opEn + ": "
                        + caller.subject + " only had to change the number in the URL. IDOR, horizontal escalation.",
                "Заказ " + order.id + " принадлежит " + order.owner + ", но " + opRu + ": "
                        + caller.subject + " достаточно было поменять число в URL. IDOR, горизонтальное повышение привилегий.",
                List.of("gate:objectLevel", "record:" + order.id));
        return true;
    }

    private void notFound(String id) {
        gate("objectLevel", "denied", "no " + id);
        deny("RECORD_NOT_FOUND", 404, "not-found", id,
                "There is no order " + id + ": 404.",
                "Заказа " + id + " нет: 404.",
                List.of("gate:objectLevel"));
    }

    private void ignored(String field, String value, String target) {
        emit("FIELD_IGNORED",
                "The request DTO has no field " + field + ", so " + field + "=" + value
                        + " never reaches the entity. The server decides it.",
                "В DTO запроса нет поля " + field + ", поэтому " + field + "=" + value
                        + " до сущности не доходит. Его решает сервер.",
                List.of(target, "field:" + field));
    }

    // ------------------------------------------------------------- lifecycle

    private void begin(Caller caller, String method, String path, List<Map<String, Object>> body) {
        requests++;
        breachKinds.clear();
        resetGates();
        for (Order o : orders.values()) {
            o.inResponse = false;
        }
        request = new LinkedHashMap<>();
        request.put("caller", caller.subject);
        request.put("method", method);
        request.put("path", path);
        request.put("body", body);
        outcome = new LinkedHashMap<>();
        outcome.put("status", null);
        outcome.put("decision", "pending");
        outcome.put("reason", null);
        outcome.put("detail", "");
        outcome.put("kinds", List.of());
        gate("authentication", "passed", caller.subject);
        emit("REQUEST_RECEIVED",
                caller.subject + " sends " + method + " " + path + ". The session is valid; authentication passes.",
                caller.subject + " отправляет " + method + " " + path + ". Сессия действительна, аутентификация пройдена.",
                List.of("gate:authentication", "caller:" + caller.subject));
    }

    private void deny(String event, int status, String reason, String detail,
                      String en, String ru, List<String> hl) {
        denied++;
        outcome.put("status", status);
        outcome.put("decision", "denied");
        outcome.put("reason", reason);
        outcome.put("detail", detail);
        System.out.println(request.get("method") + " " + request.get("path") + " as "
                + request.get("caller") + " -> " + status + " refused (" + reason + ")");
        emit(event, en, ru, hl);
    }

    private void handler(int status, String doneEn, String doneRu, List<String> hl) {
        boolean breach = !breachKinds.isEmpty();
        gate("handler", breach ? "breach" : "passed", String.valueOf(status));
        allowed++;
        if (breach) {
            breaches++;
            if (breachKinds.contains("horizontal")) {
                horizontal++;
            }
            if (breachKinds.contains("vertical")) {
                vertical++;
            }
            if (breachKinds.contains("massAssignment")) {
                massAssignment++;
            }
        }
        outcome.put("status", status);
        outcome.put("decision", breach ? "breach" : "allowed");
        outcome.put("kinds", new ArrayList<>(breachKinds));
        String kinds = String.join(", ", breachKinds);
        System.out.println(request.get("method") + " " + request.get("path") + " as "
                + request.get("caller") + " -> " + status + (breach ? " served, BREACH: " + kinds : " served"));
        List<String> highlight = new ArrayList<>(hl);
        highlight.add("gate:handler");
        if (breach) {
            emit("HANDLER_RAN",
                    "The handler ran (" + status + "): " + doneEn + ". It should not have — " + kinds + ".",
                    "Обработчик выполнился (" + status + "): " + doneRu + ". А не должен был — " + kindsRu() + ".",
                    highlight);
        } else {
            emit("HANDLER_RAN",
                    "The handler ran (" + status + "): " + doneEn + ". Every check agreed.",
                    "Обработчик выполнился (" + status + "): " + doneRu + ". Все проверки согласны.",
                    highlight);
        }
    }

    private String kindsRu() {
        List<String> out = new ArrayList<>();
        for (String k : breachKinds) {
            switch (k) {
                case "horizontal" -> out.add("горизонтальное повышение");
                case "vertical" -> out.add("вертикальное повышение");
                default -> out.add("mass assignment");
            }
        }
        return String.join(", ", out);
    }

    // ----------------------------------------------------------------- helpers

    private boolean enabled(Defense d) {
        return defenses.get(d);
    }

    private Order find(String id) {
        Order o = orders.get(id);
        return o == null || o.deleted ? null : o;
    }

    private void resetGates() {
        gates.clear();
        for (String id : GATES) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("id", id);
            g.put("status", "pending");
            g.put("detail", "");
            gates.put(id, g);
        }
    }

    private void gate(String id, String status, String detail) {
        Map<String, Object> g = gates.get(id);
        g.put("status", status);
        g.put("detail", detail);
    }

    private static Map<String, String> pairs(String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("Pass field/value pairs, e.g. \"note\", \"hi\"");
        }
        Map<String, String> body = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            body.put(kv[i], kv[i + 1]);
        }
        return body;
    }

    private static List<Map<String, Object>> bodyState(Map<String, String> body, Set<String> editable) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : body.entrySet()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("field", e.getKey());
            f.put("value", e.getValue());
            f.put("clientEditable", editable.contains(e.getKey()));
            out.add(f);
        }
        return out;
    }

    private static String orderField(Order o, String field) {
        return switch (field) {
            case "owner" -> o.owner;
            case "price" -> String.valueOf(o.price);
            case "status" -> o.status;
            default -> o.id;
        };
    }

    private static void setOrderField(Order o, String field, String value) {
        switch (field) {
            case "owner" -> o.owner = value;
            case "price" -> o.price = Integer.parseInt(value);
            case "status" -> o.status = value;
            default -> { }
        }
    }

    private void emit(String event, String en, String ru, List<String> highlight) {
        Trace.event(event, en, ru, highlight, snapshot());
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> s = new LinkedHashMap<>();
        Map<String, Object> d = new LinkedHashMap<>();
        for (Map.Entry<Defense, Boolean> e : defenses.entrySet()) {
            d.put(e.getKey().key, e.getValue());
        }
        s.put("defenses", d);
        List<Map<String, Object>> callers = new ArrayList<>();
        for (Account a : accounts.values()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("subject", a.subject);
            c.put("role", a.role);
            c.put("displayName", a.displayName);
            callers.add(c);
        }
        s.put("callers", callers);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Order o : orders.values()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", o.id);
            r.put("owner", o.owner);
            r.put("price", o.price);
            r.put("status", o.status);
            r.put("note", o.note);
            r.put("exposed", o.exposed);
            r.put("tampered", o.tampered);
            r.put("deleted", o.deleted);
            r.put("inResponse", o.inResponse);
            records.add(r);
        }
        s.put("records", records);
        if (request != null) {
            s.put("request", new LinkedHashMap<>(request));
        }
        List<Map<String, Object>> g = new ArrayList<>();
        for (Map<String, Object> gate : gates.values()) {
            g.add(new LinkedHashMap<>(gate));
        }
        s.put("gates", g);
        if (outcome != null) {
            s.put("outcome", new LinkedHashMap<>(outcome));
        }
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("requests", requests);
        st.put("allowed", allowed);
        st.put("denied", denied);
        st.put("breaches", breaches);
        st.put("horizontal", horizontal);
        st.put("vertical", vertical);
        st.put("massAssignment", massAssignment);
        st.put("leakedRows", leakedRows);
        s.put("stats", st);
        return s;
    }
}
