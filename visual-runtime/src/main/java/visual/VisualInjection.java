package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A <em>teaching model</em> of injection attacks: what happens when a string somebody
 * else typed is pasted into a language that an interpreter is about to parse.
 *
 * <p>Every injection — SQL, XML/XXE, OS command, LDAP, template — is the same bug seen
 * through a different parser. The application builds one flat string out of two things
 * it knows to be different (its own instructions and someone else's data), hands that
 * string to an interpreter, and the interpreter has no way to tell the halves apart. If
 * the untrusted half contains characters that mean something to <em>that</em> grammar,
 * they are obeyed, and data has become code.
 *
 * <p>The model makes three things visible that prose usually hides:
 * <ul>
 *   <li><b>the channel</b> — whether the value travelled <em>inside</em> the statement
 *       text (in-band, so the parser sees it) or <em>beside</em> it as a bound parameter
 *       (out-of-band, so the parser never does). This, and not "escaping", is what a
 *       {@code PreparedStatement} actually changes;</li>
 *   <li><b>the tokens</b> — what the parser made of each fragment. The whole
 *       vulnerability is one bit per token: did the untrusted characters stay inside a
 *       literal, or did they become keywords, operators and comments?</li>
 *   <li><b>the cases binding does not cover</b> — an identifier that cannot be a
 *       parameter, a "prepared" statement that was concatenated first, a value bound
 *       safely on write and concatenated later, dynamic SQL built inside the database,
 *       and a completely different parser (XML) where binding has no meaning at all.</li>
 * </ul>
 *
 * <p>The SQL tokenizer and the row results here are deliberate simplifications of what a
 * real database does — enough to be faithful about <em>which defence stops which
 * attack</em>, and small enough to read. Every step emits a bilingual {@link Trace}
 * event; the class is dependency-free.
 */
public class VisualInjection {

    /** A file the web server may read and the web user certainly may not. */
    public static final String SECRET_FILE = "file:///etc/passwd";
    /** Its (fake, fixed) contents, so runs stay deterministic. */
    public static final String SECRET_FILE_CONTENT = "root:x:0:0:root:/root:/bin/bash";
    /** An address only reachable from inside the network — the classic SSRF target. */
    public static final String INTERNAL_URL = "http://169.254.169.254/latest/meta-data/";
    /** What that address answers with. */
    public static final String INTERNAL_RESPONSE = "iam/security-credentials/app-role";

    // The seeded database. Deterministic and tiny on purpose.
    private static final List<String[]> USERS = List.of(
            new String[]{"1", "alice", "user", "true"},
            new String[]{"2", "bob", "user", "true"},
            new String[]{"3", "carol", "admin", "true"},
            new String[]{"4", "admin", "admin", "false"});

    private static final List<String[]> CARDS = List.of(
            new String[]{"1", "4111-1111-1111-1111", "alice"},
            new String[]{"2", "5500-0000-0000-0004", "bob"});

    private static final List<String> USER_COLUMNS = List.of("id", "name", "role");
    /** The only column names the sort endpoint will ever put into SQL. */
    private static final List<String> SORTABLE = List.of("id", "name", "role");

    private static final String FIND_PREFIX = "SELECT id, name, role FROM users WHERE name = '";
    private static final String FIND_SUFFIX = "' AND active = TRUE";
    private static final String FIND_BOUND = "SELECT id, name, role FROM users WHERE name = ? AND active = TRUE";
    private static final String BY_ID_PREFIX = "SELECT id, name, role FROM users WHERE id = ";
    private static final String BY_ID_SUFFIX = " AND active = TRUE";
    private static final String SORT_PREFIX = "SELECT id, name, role FROM users ORDER BY ";
    private static final String ROLE_PREFIX = "SELECT id, name, role FROM users WHERE role = '";
    private static final String ROLE_SUFFIX = "'";

    private static final List<String> KEYWORDS = List.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "TRUE", "FALSE", "UNION",
            "ALL", "ORDER", "BY", "ASC", "DESC", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "DROP", "TABLE", "CALL", "EXECUTE", "IMMEDIATE", "LIKE", "JOIN", "ON",
            "GROUP", "HAVING", "LIMIT");

    private static final Pattern EXTERNAL_ENTITY =
            Pattern.compile("<!ENTITY\\s+(\\w+)\\s+SYSTEM\\s+\"([^\"]+)\"");
    private static final Pattern ANY_ENTITY = Pattern.compile("<!ENTITY\\s");

    /** Which grammar the untrusted value lands in — it decides what "dangerous" means. */
    private enum Slot {
        /** Between two single quotes: the attacker needs a quote to get out. */
        STRING_LITERAL,
        /** A bare number: no quote needed at all, because there is no quote to close. */
        NUMBER,
        /** A table/column name: cannot be a bind parameter, so only an allowlist helps. */
        IDENTIFIER,
        /** Nothing untrusted reached the text. */
        NONE
    }

    // ------------------------------------------------------------------- config

    private boolean escapeQuotes;
    private boolean doctypeAllowed = true;

    // -------------------------------------------------------- current statement

    private String language;
    private String binding;
    private String template;
    private String text;
    private String input;
    private String channel;
    private String stage = "idle";
    private boolean injected;
    private String impact = "none";
    private String blockedBy;
    private List<Map<String, Object>> tokens = new ArrayList<>();
    private Map<String, Object> result;

    /** A value written earlier by one request and read back by a later one. */
    private String stored = "";

    private int statements;
    private int injections;
    private int leaked;
    private int stayedData;

    private VisualInjection() {
    }

    /**
     * An application with a small user table, a card table, an XML endpoint and no
     * defences: queries are built by string concatenation and the XML parser has the
     * historical JAXP defaults, which resolve external entities.
     */
    public static VisualInjection app() {
        VisualInjection app = new VisualInjection();
        Trace.event("INJECTION_SETUP",
                "An application over a users table (alice, bob, carol, and a locked 'admin' "
                        + "account) plus a card table nobody is supposed to read through this "
                        + "endpoint. Queries are built by pasting values into SQL text, and the XML "
                        + "parser still has the old defaults that resolve external entities. Neither "
                        + "of those is exotic — both are what you get by writing the shortest code "
                        + "that works",
                "Приложение с таблицей users (alice, bob, carol и заблокированный аккаунт «admin») "
                        + "и таблицей карт, читать которую через этот эндпоинт никто не должен. "
                        + "Запросы собираются подстановкой значений в текст SQL, а у XML-парсера "
                        + "остались старые настройки, которые раскрывают внешние сущности. Ни то ни "
                        + "другое не экзотика — и то и другое получается, если написать самый "
                        + "короткий работающий код",
                List.of("config"), app.state());
        return app;
    }

    // ----------------------------------------------------------------- defences

    /**
     * The defence everybody writes first: double every quote in the value before pasting
     * it. It is real, it is what an escaping library does — and it only ever protects the
     * one context whose metacharacter is the quote.
     */
    public VisualInjection escapeQuotes() {
        escapeQuotes = true;
        Trace.event("DEFENCE_ENABLED",
                "Hand-written escaping is switched on: every ' in the value becomes '' before the "
                        + "value is concatenated. That is genuinely the rule for a SQL string "
                        + "literal, so payloads that rely on closing the quote now stay inside it. "
                        + "Keep in mind what it is: a rule about ONE context, applied by code that "
                        + "has to remember to apply it every single time",
                "Включено самописное экранирование: каждая ' в значении превращается в '' до "
                        + "конкатенации. Для строкового литерала SQL это действительно верное "
                        + "правило, поэтому нагрузки, рассчитанные на закрытие кавычки, теперь "
                        + "остаются внутри неё. Стоит помнить, что это такое: правило про ОДИН "
                        + "контекст, которое код обязан не забыть применить каждый раз",
                List.of("config"), state());
        return this;
    }

    /**
     * Turns off DTD support in the XML parser — {@code disallow-doctype-decl}, the one
     * line that ends XXE for good.
     */
    public VisualInjection secureXmlParser() {
        doctypeAllowed = false;
        Trace.event("DEFENCE_ENABLED",
                "The XML parser is reconfigured: DOCTYPE declarations are refused outright "
                        + "(disallow-doctype-decl = true). There is no entity to resolve if there is "
                        + "no DTD to declare it in, so this closes external-entity reads, "
                        + "entity-expansion denial of service and DTD-based server-side requests in "
                        + "one setting",
                "XML-парсер перенастроен: объявления DOCTYPE отвергаются сразу "
                        + "(disallow-doctype-decl = true). Если DTD объявить нельзя, то и раскрывать "
                        + "нечего — одна настройка закрывает и чтение файлов через внешние сущности, "
                        + "и отказ в обслуживании через разворачивание сущностей, и серверные "
                        + "запросы из DTD",
                List.of("config"), state());
        return this;
    }

    // --------------------------------------------------------------- SQL: paste

    /** The query everyone writes on day one: the name is pasted into the SQL text. */
    public void findByNameConcatenated(String name) {
        String value = escapeQuotes ? name.replace("'", "''") : name;
        String sql = FIND_PREFIX + value + FIND_SUFFIX;
        begin("SQL", escapeQuotes ? "escaping" : "concatenation",
                "\"SELECT id, name, role FROM users WHERE name = '\" + name + \"' AND active = TRUE\"",
                sql, name, "in-band");
        Trace.event("SQL_BUILT",
                "The statement is finished before the database has seen anything: one flat string, "
                        + "half written by us and half typed by whoever called the endpoint. Nothing "
                        + "in that string records which half is which — that information existed in "
                        + "the Java code and was thrown away by the + operator",
                "Запрос закончен ещё до того, как база что-то увидела: одна плоская строка, "
                        + "наполовину написанная нами, наполовину напечатанная тем, кто вызвал "
                        + "эндпоинт. В самой строке не записано, где какая половина, — эта "
                        + "информация была в Java-коде, и оператор + её выбросил",
                List.of("statement"), state());
        if (escapeQuotes) {
            noteEscaping(name, value, Slot.STRING_LITERAL);
            blockedBy = "escaping";
        }
        parseAndRun(FIND_PREFIX.length(), value.length(), Slot.STRING_LITERAL,
                matchByName(name, true));
    }

    /**
     * The same lookup by a numeric id. People assume this one is safe because "there are
     * no quotes" — which is exactly the problem: there is no quote to close either.
     */
    public void findByIdConcatenated(String id) {
        String value = escapeQuotes ? id.replace("'", "''") : id;
        String sql = BY_ID_PREFIX + value + BY_ID_SUFFIX;
        begin("SQL", escapeQuotes ? "escaping" : "concatenation",
                "\"SELECT id, name, role FROM users WHERE id = \" + id + \" AND active = TRUE\"",
                sql, id, "in-band");
        Trace.event("SQL_BUILT",
                "Same construction, numeric column. The value is not quoted here, because a number "
                        + "in SQL is not written in quotes — so whatever arrives is dropped straight "
                        + "into the expression, next to the operators, with nothing around it",
                "Та же сборка, числовая колонка. Значение здесь не в кавычках, потому что число в "
                        + "SQL кавычками не оформляют, — и что бы ни пришло, оно падает прямо в "
                        + "выражение, рядом с операторами, и ничем не обёрнуто",
                List.of("statement"), state());
        if (escapeQuotes) {
            noteEscaping(id, value, Slot.NUMBER);
        }
        parseAndRun(BY_ID_PREFIX.length(), value.length(), Slot.NUMBER, matchById(id));
    }

    /**
     * The trap that catches careful people: {@code prepareStatement} is called, so the
     * code review sees a PreparedStatement — but the string handed to it was built by
     * concatenation, so there is nothing left to bind.
     */
    public void preparedButConcatenated(String name) {
        String sql = FIND_PREFIX + name + FIND_SUFFIX;
        begin("SQL", "prepared-but-concatenated",
                "conn.prepareStatement(\"SELECT ... WHERE name = '\" + name + \"' AND active = TRUE\")",
                sql, name, "in-band");
        Trace.event("PREPARED_IN_NAME_ONLY",
                "prepareStatement really is being called, and it changes nothing here. Look at the "
                        + "order of operations: the + ran first, so by the time the driver receives "
                        + "the statement the value is already part of the SQL text. A "
                        + "PreparedStatement with no ? in it has no parameters to keep out of the "
                        + "query — the protection comes from the placeholder, not from the class name",
                "prepareStatement действительно вызывается — и здесь это ничего не меняет. "
                        + "Посмотрите на порядок операций: сначала отработал +, поэтому к моменту, "
                        + "когда драйвер получает запрос, значение уже часть текста SQL. У "
                        + "PreparedStatement без ? нет параметров, которые можно было бы держать вне "
                        + "запроса: защиту даёт плейсхолдер, а не имя класса",
                List.of("statement", "config"), state());
        parseAndRun(FIND_PREFIX.length(), name.length(), Slot.STRING_LITERAL,
                matchByName(name, true));
    }

    // ---------------------------------------------------------------- SQL: bind

    /**
     * The fix: the SQL text is a constant with a {@code ?} in it, and the value travels
     * to the database separately, after the statement has already been parsed.
     */
    public void findByNameBound(String name) {
        begin("SQL", "bind-parameter", FIND_BOUND, FIND_BOUND, name, "out-of-band");
        tokens = tokenMaps(tokenize(FIND_BOUND));
        Trace.event("STATEMENT_PREPARED",
                "The database is given the statement text first, and that text is a compile-time "
                        + "constant: no caller can influence a single character of it. It is parsed "
                        + "now, into a plan with a hole where the value goes. Note the tense — the "
                        + "grammar is decided BEFORE the value exists, which is why no value can "
                        + "change it afterwards",
                "База сначала получает текст запроса, и этот текст — константа времени компиляции: "
                        + "ни один вызывающий не может повлиять ни на один её символ. Он "
                        + "разбирается сейчас, в план с дыркой на месте значения. Обратите внимание "
                        + "на время: грамматика определена ДО того, как значение появилось, — "
                        + "поэтому никакое значение уже не может её изменить",
                List.of("statement", "tokens"), state());
        stage = "parsed";
        Trace.event("PARAMETER_BOUND",
                "setString(1, name) sends the value down a different part of the protocol, tagged "
                        + "as a parameter of the statement that was already parsed. It is never "
                        + "concatenated, never quoted and never escaped — it is not text in a query, "
                        + "it is an argument, the way a method argument is not part of the method's "
                        + "source code",
                "setString(1, name) отправляет значение отдельной частью протокола, помеченной как "
                        + "параметр уже разобранного запроса. Его не конкатенируют, не берут в "
                        + "кавычки и не экранируют — это не текст в запросе, а аргумент, ровно так "
                        + "же, как аргумент метода не является частью его исходного кода",
                List.of("statement", "tokens"), state());
        settleSafe("binding", matchByName(name, true));
    }

    /** Writes an untrusted value correctly — bound — and keeps it for later. */
    public void saveProfileBound(String name) {
        String sql = "INSERT INTO users(name, role, active) VALUES (?, 'user', TRUE)";
        begin("SQL", "bind-parameter", sql, sql, name, "out-of-band");
        stored = name;
        tokens = tokenMaps(tokenize(sql));
        Trace.event("STATEMENT_PREPARED",
                "An INSERT with a placeholder. Whatever the value contains — quotes, semicolons, a "
                        + "whole SELECT — it is stored as those characters and nothing more",
                "INSERT с плейсхолдером. Что бы ни было в значении — кавычки, точки с запятой, "
                        + "целый SELECT, — оно сохранится как эти самые символы и ничего больше",
                List.of("statement"), state());
        stage = "parsed";
        Trace.event("PARAMETER_BOUND",
                "The value goes out of band again and lands in the column verbatim. This row is now "
                        + "in your database, it looks like data, and it is exactly as untrusted as it "
                        + "was when it arrived",
                "Значение снова идёт вне текста запроса и попадает в колонку дословно. Эта строка "
                        + "теперь в вашей базе, выглядит как данные и ровно настолько же "
                        + "недоверенная, как в момент, когда пришла",
                List.of("statement"), state());
        result = textResult("1 row inserted: name = " + name);
        stage = "settled";
        blockedBy = "binding";
        stayedData++;
        Trace.event("VALUE_STAYED_DATA",
                "The write is safe. Remember that this says nothing about the read: the string in "
                        + "that column is still a payload, and it is one query away from being "
                        + "concatenated by somebody who assumed 'it came from our own database'",
                "Запись безопасна. Учтите, что про чтение это не говорит ничего: строка в этой "
                        + "колонке по-прежнему полезная нагрузка, и до конкатенации ей остался один "
                        + "запрос — тот, где кто-то решит, что «это же из нашей собственной базы»",
                List.of("outcome"), state());
    }

    /**
     * Second-order injection: a later query concatenates the row that was written safely.
     * The value never touches user input on this code path, and it is still an attack.
     */
    public void auditSavedProfile() {
        String value = stored;
        String sql = FIND_PREFIX + value + FIND_SUFFIX;
        begin("SQL", "concatenation",
                "\"SELECT ... WHERE name = '\" + rowLoadedFromOurDatabase + \"' AND active = TRUE\"",
                sql, value, "in-band");
        Trace.event("SECOND_ORDER_INJECTION",
                "A different endpoint, a different day, and no user input in sight: this value was "
                        + "read out of our own users table. That is the whole trick — 'trusted "
                        + "source' is not a property of a string, and a value written safely is not "
                        + "a value that is safe to concatenate. Taint does not wear off in storage",
                "Другой эндпоинт, другой день, и пользовательского ввода нигде не видно: это "
                        + "значение прочитано из нашей же таблицы users. В этом весь фокус: "
                        + "«доверенный источник» — не свойство строки, и значение, безопасно "
                        + "записанное, не становится значением, которое безопасно конкатенировать. "
                        + "В хранилище пометка «недоверенное» не выветривается",
                List.of("statement"), state());
        Trace.event("SQL_BUILT",
                "The text the database will parse: " + sql,
                "Текст, который разберёт база: " + sql,
                List.of("statement"), state());
        parseAndRun(FIND_PREFIX.length(), value.length(), Slot.STRING_LITERAL,
                matchByName(value, true));
    }

    /**
     * A stored procedure called with a proper bind parameter — which then builds SQL by
     * concatenation inside the database. The binding was real and it protected nothing,
     * because the concatenation simply moved somewhere the driver cannot see.
     */
    public void callReportProcedure(String role) {
        String call = "{ call build_report(?) }";
        begin("SQL", "bind-parameter", call, call, role, "out-of-band");
        Trace.event("STATEMENT_PREPARED",
                "A CallableStatement with a placeholder — from the driver's side this is textbook "
                        + "correct code",
                "CallableStatement с плейсхолдером — со стороны драйвера это образцово правильный "
                        + "код",
                List.of("statement"), state());
        Trace.event("PARAMETER_BOUND",
                "The value is bound and travels out of band to the procedure. Everything a JDBC "
                        + "code review looks at is now green",
                "Значение привязано и уходит к процедуре вне текста запроса. Всё, на что смотрит "
                        + "ревью JDBC-кода, теперь зелёное",
                List.of("statement"), state());
        String inner = ROLE_PREFIX + role + ROLE_SUFFIX;
        binding = "dynamic-sql";
        template = "EXECUTE IMMEDIATE 'SELECT ... WHERE role = ''' || p_role || ''''";
        text = inner;
        channel = "in-band";
        Trace.event("DYNAMIC_SQL_IN_DATABASE",
                "Inside the procedure the parameter is concatenated into a new statement and run "
                        + "with EXECUTE IMMEDIATE. The bind parameter did its job perfectly and then "
                        + "handed the value to code that undoes it — the value is back inside SQL "
                        + "text, one layer deeper than anything the application can inspect",
                "Внутри процедуры параметр конкатенируется в новый запрос и выполняется через "
                        + "EXECUTE IMMEDIATE. Привязка отработала идеально и передала значение коду, "
                        + "который её отменяет: значение снова внутри текста SQL, на слой глубже "
                        + "всего, что может проверить приложение",
                List.of("statement"), state());
        parseAndRun(ROLE_PREFIX.length(), role.length(), Slot.STRING_LITERAL, matchByRole(role));
    }

    // ---------------------------------------------------------- SQL: identifier

    /**
     * A sortable table: the column name comes from the request. No database lets you bind
     * an identifier, so this one really is concatenated — the question is what checks it.
     */
    public void sortByColumnConcatenated(String column) {
        String sql = SORT_PREFIX + column;
        begin("SQL", "concatenation", "\"SELECT id, name, role FROM users ORDER BY \" + column",
                sql, column, "in-band");
        Trace.event("IDENTIFIER_INTERPOLATED",
                "This one cannot be fixed with a placeholder, and it is worth being precise about "
                        + "why: a ? stands for a VALUE, and the plan is built before the value "
                        + "arrives. A column name is part of the plan — the database has to know it "
                        + "to parse the statement at all. ORDER BY ?, table names, ASC/DESC and "
                        + "whole WHERE fragments are all in this category",
                "Вот это плейсхолдером не чинится, и стоит точно сказать почему: ? обозначает "
                        + "ЗНАЧЕНИЕ, а план строится до того, как значение придёт. Имя колонки — "
                        + "часть плана: без него база вообще не разберёт запрос. ORDER BY ?, имена "
                        + "таблиц, ASC/DESC и целые куски WHERE — всё это из этой категории",
                List.of("statement", "config"), state());
        Trace.event("SQL_BUILT",
                "The text the database will parse: " + sql,
                "Текст, который разберёт база: " + sql,
                List.of("statement"), state());
        parseAndRun(SORT_PREFIX.length(), column.length(), Slot.IDENTIFIER, sortedUsers(column));
    }

    /**
     * The same endpoint done properly: the request selects from a fixed set of column
     * names, and the string that reaches the SQL is one of ours, not one of theirs.
     */
    public void sortByColumnAllowlisted(String column) {
        boolean allowed = SORTABLE.contains(column);
        String safe = allowed ? column : "id";
        String sql = SORT_PREFIX + safe;
        begin("SQL", "allowlist", "\"SELECT ... ORDER BY \" + allowlist.get(column)",
                sql, column, allowed ? "in-band" : "rejected");
        blockedBy = "allowlist";
        Trace.event("ALLOWLIST_CHECKED",
                "The requested column '" + column + "' is looked up in a fixed set {id, name, role}"
                        + (allowed
                            ? " and matches, so the SQL is built from OUR copy of that name. The "
                              + "user's string was used to choose a constant, never to become one"
                            : " and does not match, so it is dropped and the default 'id' is used. "
                              + "Nothing about the input is escaped, filtered or repaired — it "
                              + "simply never reaches the statement")
                        + ". This is the pattern for every part of a query a placeholder cannot "
                        + "cover: map the input to a value you wrote yourself",
                "Запрошенная колонка «" + column + "» ищется в фиксированном наборе {id, name, role}"
                        + (allowed
                            ? " и находится, поэтому SQL собирается из НАШЕЙ копии этого имени. "
                              + "Строка пользователя послужила выбором константы, но константой не "
                              + "стала"
                            : " и не находится, поэтому отбрасывается, а берётся значение по "
                              + "умолчанию «id». Ввод при этом не экранируется, не фильтруется и не "
                              + "исправляется — он просто не доходит до запроса")
                        + ". Это и есть приём для любой части запроса, которую не закрывает "
                        + "плейсхолдер: отобразить ввод в значение, которое написали вы сами",
                List.of("statement", "config"), state());
        parseAndRun(allowed ? SORT_PREFIX.length() : -1, allowed ? safe.length() : 0,
                Slot.IDENTIFIER, sortedUsers(safe));
    }

    // --------------------------------------------------------------------- XML

    /**
     * Parses an uploaded XML document. Same bug, different grammar: here the untrusted
     * document may declare its own entities, and a parser with DTD support switched on
     * will happily go and fetch whatever they point at.
     */
    public void parseXml(String xml) {
        begin("XML", doctypeAllowed ? "external-entities" : "secure-parser", xml, xml, xml, "in-band");
        tokens = xmlTokens(xml);
        Trace.event("XML_RECEIVED",
                "An XML document arrives — an invoice, a SOAP body, an SVG, an office file, a SAML "
                        + "assertion. Every byte of it is untrusted, including the part before the "
                        + "root element, which most people never think of as input at all",
                "Приходит XML-документ — счёт, тело SOAP, SVG, офисный файл, SAML-утверждение. "
                        + "Недоверенный в нём каждый байт, включая ту часть, что идёт до корневого "
                        + "элемента и которую обычно вообще не считают вводом",
                List.of("statement", "tokens"), state());

        boolean hasDoctype = xml.contains("<!DOCTYPE");
        if (hasDoctype && !doctypeAllowed) {
            blockedBy = "parser-config";
            stage = "settled";
            result = textResult("SAXParseException: DOCTYPE is disallowed when the feature "
                    + "\"http://apache.org/xml/features/disallow-doctype-decl\" is set to true");
            stayedData++;
            Trace.event("XXE_BLOCKED",
                    "The parser refuses the document at the DOCTYPE, before any entity exists to "
                            + "resolve. Note how unlike escaping this is: nothing inspected the "
                            + "payload and nothing tried to recognise an attack — a capability the "
                            + "application never needed was simply switched off",
                    "Парсер отвергает документ на DOCTYPE, ещё до того, как появится сущность, "
                            + "которую можно раскрыть. Обратите внимание, насколько это не похоже на "
                            + "экранирование: нагрузку никто не разглядывал и атаку никто не "
                            + "пытался распознать — просто выключили возможность, которая "
                            + "приложению и не была нужна",
                    List.of("outcome", "config"), state());
            return;
        }

        Matcher external = EXTERNAL_ENTITY.matcher(xml);
        if (external.find()) {
            String name = external.group(1);
            String uri = external.group(2);
            injected = true;
            injections++;
            Trace.event("DTD_DECLARED",
                    "The document brought its own DTD and declared the entity &" + name + "; as "
                            + "SYSTEM \"" + uri + "\". Read that as what it is: the input is not "
                            + "just data for the parser any more, it is an instruction to the "
                            + "parser — go and open this",
                    "Документ принёс собственный DTD и объявил сущность &" + name + "; как SYSTEM "
                            + "«" + uri + "». Прочитайте это как есть: ввод больше не просто данные "
                            + "для парсера, это инструкция парсеру — пойди и открой вот это",
                    List.of("tokens"), state());
            Trace.event("ENTITY_RESOLVED",
                    "The parser resolves the reference, which means it performs I/O on behalf of "
                            + "the attacker, as the application's OS user and from inside the "
                            + "application's network. The XML library is doing precisely what the "
                            + "XML specification says it should",
                    "Парсер раскрывает ссылку, то есть выполняет ввод-вывод от имени "
                            + "злоумышленника — под системным пользователем приложения и изнутри "
                            + "его сети. XML-библиотека делает ровно то, что предписывает "
                            + "спецификация XML",
                    List.of("tokens"), state());
            stage = "settled";
            leaked++;
            if (uri.startsWith("file:")) {
                impact = "file-read";
                result = textResult(SECRET_FILE_CONTENT);
                Trace.event("XXE_FILE_DISCLOSED",
                        "The file's contents are substituted into the document and come back in the "
                                + "response: " + SECRET_FILE_CONTENT + ". Point the same entity at a "
                                + "config file and it returns your database password — this is a "
                                + "read primitive over the whole filesystem the process can see, "
                                + "reached through an endpoint that only meant to accept an invoice",
                        "Содержимое файла подставляется в документ и возвращается в ответе: "
                                + SECRET_FILE_CONTENT + ". Направьте ту же сущность на файл "
                                + "конфигурации — и вернётся пароль от базы. Это примитив чтения по "
                                + "всей файловой системе, видимой процессу, доступный через "
                                + "эндпоинт, который собирался принимать всего лишь счёт",
                        List.of("result", "outcome"), state());
            } else {
                impact = "ssrf";
                result = textResult(INTERNAL_RESPONSE);
                Trace.event("XXE_SSRF",
                        "The URI was not a file, so the parser made an HTTP request from inside "
                                + "your network to " + uri + " and inlined the answer: "
                                + INTERNAL_RESPONSE + ". Your firewall sees a call from a trusted "
                                + "host, because it is one — this is server-side request forgery "
                                + "with an XML parser as the client",
                        "URI оказался не файлом, поэтому парсер сделал HTTP-запрос изнутри вашей "
                                + "сети на " + uri + " и подставил ответ: " + INTERNAL_RESPONSE
                                + ". Ваш файрвол видит обращение с доверенного узла — потому что "
                                + "так и есть. Это подделка запроса со стороны сервера, где "
                                + "клиентом выступает XML-парсер",
                        List.of("result", "outcome"), state());
            }
            return;
        }

        if (hasDoctype && countEntities(xml) >= 3) {
            injected = true;
            injections++;
            impact = "dos";
            stage = "settled";
            result = textResult("heap exhausted while expanding nested entities");
            Trace.event("ENTITY_EXPANSION",
                    "No external resource this time — the entities only reference each other, and "
                            + "each level multiplies the one below it. A document of a few hundred "
                            + "bytes expands to gigabytes of text in memory. Same root cause as the "
                            + "file read (the document controls the parser), different outcome: the "
                            + "service simply stops",
                    "Внешних ресурсов здесь нет — сущности ссылаются только друг на друга, и "
                            + "каждый уровень умножает предыдущий. Документ в несколько сотен байт "
                            + "разворачивается в гигабайты текста в памяти. Причина та же, что и у "
                            + "чтения файла (документ управляет парсером), а исход другой: сервис "
                            + "просто перестаёт работать",
                    List.of("result", "outcome"), state());
            return;
        }

        stage = "settled";
        stayedData++;
        result = textResult("parsed " + countElements(xml) + " element(s) as data");
        Trace.event("XML_PARSED",
                "An ordinary document with no DTD: the parser builds elements out of it and never "
                        + "does anything on its behalf. This is what every XML upload should look "
                        + "like, and the only reason it does here is that this document chose not "
                        + "to declare a DOCTYPE",
                "Обычный документ без DTD: парсер строит из него элементы и ничего не делает по "
                        + "его поручению. Именно так должна выглядеть любая загрузка XML, и "
                        + "единственная причина, почему так вышло здесь, — этот документ сам не "
                        + "стал объявлять DOCTYPE",
                List.of("result", "outcome"), state());
    }

    // ------------------------------------------------------------------ report

    /** Prints what the whole run added up to. */
    public void report() {
        Trace.event("INJECTION_AUDIT",
                "After " + statements + " statement(s): untrusted characters became part of the "
                        + "grammar " + injections + " time(s), data the caller had no right to see "
                        + "came back " + leaked + " time(s), and the value stayed data " + stayedData
                        + " time(s). Every number above zero in the middle column came from the same "
                        + "cause — a string that carried both instructions and input into a parser. "
                        + "Keep them in separate channels and the column stays empty",
                "После запросов (" + statements + "): недоверенные символы стали частью грамматики "
                        + injections + " раз, данные, которые вызывающему видеть не полагалось, "
                        + "вернулись " + leaked + " раз, а значение осталось данными " + stayedData
                        + " раз. Всё, что в середине больше нуля, пришло из одной причины — строки, "
                        + "внёсшей в парсер и инструкции, и ввод одновременно. Разведите их по "
                        + "разным каналам, и середина останется пустой",
                List.of(), state());
    }

    // ------------------------------------------------------------- the engine

    /** Tokenizes the finished SQL, decides whether the input became grammar, and runs it. */
    private void parseAndRun(int inputStart, int inputLen, Slot slot, Rows benign) {
        List<Tok> toks = tokenize(text);
        injected = mark(toks, inputStart, inputLen, slot);
        tokens = tokenMaps(toks);
        stage = "parsed";
        Trace.event("SQL_PARSED",
                "The database splits that text into tokens and each one gets a job: keyword, "
                        + "identifier, literal, operator, comment. The untrusted characters are not "
                        + "marked in any way here — they are judged by the same grammar as ours, "
                        + "which is the entire vulnerability in one sentence",
                "База разбивает этот текст на токены, и у каждого появляется роль: ключевое слово, "
                        + "идентификатор, литерал, оператор, комментарий. Недоверенные символы здесь "
                        + "ничем не помечены — их судят по той же грамматике, что и наши, и в этой "
                        + "фразе вся уязвимость целиком",
                List.of("tokens"), state());

        if (injected) {
            injections++;
            Trace.event("SQL_INJECTED",
                    "The value did not stay in its slot: " + breakoutEn(slot) + ". The statement "
                            + "that runs is not the statement anybody wrote — its shape was decided "
                            + "by the caller, and the database has no idea, because a parser's job "
                            + "is to obey valid syntax, not to guess who typed it",
                    "Значение не осталось в своём месте: " + breakoutRu(slot) + ". Выполняется не "
                            + "тот запрос, который кто-либо писал: его форму определил вызывающий, а "
                            + "база об этом не знает, потому что задача парсера — подчиняться "
                            + "корректному синтаксису, а не угадывать, кто его напечатал",
                    List.of("tokens", "outcome"), state());
            executeInjected(slot);
            return;
        }
        settleSafe(blockedBy, benign);
    }

    /** Nothing broke out: report the honest reason and the ordinary result. */
    private void settleSafe(String reason, Rows benign) {
        blockedBy = reason;
        stage = "settled";
        result = rowsResult(benign.columns, benign.rows);
        if (reason != null) {
            stayedData++;
            Trace.event("VALUE_STAYED_DATA",
                    "The payload is compared as a value, not obeyed as syntax — " + safeEn(reason)
                            + ". The query returns what it should have returned all along: "
                            + benign.rows.size() + " row(s). That is the shape of a correct defence, "
                            + "by the way: the data is not mangled and the attack simply has nowhere "
                            + "to happen",
                    "Нагрузка сравнивается как значение, а не выполняется как синтаксис — "
                            + safeRu(reason) + ". Запрос возвращает то, что и должен был с самого "
                            + "начала: строк — " + benign.rows.size() + ". Кстати, именно так "
                            + "выглядит правильная защита: данные не испорчены, а атаке просто негде "
                            + "случиться",
                    List.of("result", "outcome"), state());
            return;
        }
        Trace.event("STATEMENT_EXECUTED",
                "This particular input contained nothing the parser cares about, so the query "
                        + "means what it looks like it means and returns " + benign.rows.size()
                        + " row(s). Nothing was defended — the code is built exactly the same way "
                        + "for the next caller, who may type something else",
                "Конкретно в этом вводе не было ничего интересного для парсера, поэтому запрос "
                        + "значит то, на что похож, и возвращает строк: " + benign.rows.size()
                        + ". Никакой защиты не сработало — код собран ровно так же и для следующего "
                        + "вызывающего, который напечатает что-нибудь другое",
                List.of("result", "outcome"), state());
    }

    /** Something broke out: work out what the rewritten statement actually does. */
    private void executeInjected(Slot slot) {
        String probe = input.toLowerCase(Locale.ROOT);
        stage = "settled";
        if (probe.contains(";")) {
            impact = "schema-change";
            result = textResult("users: table not found (the second statement ran)");
            Trace.event("STATEMENT_EXECUTED",
                    "The semicolon ended our statement and started the attacker's, so a second, "
                            + "unrelated command ran with the application's database privileges. "
                            + "Many JDBC drivers refuse batched statements by default and this one "
                            + "would fail — which is worth knowing and is not a defence: it depends "
                            + "on the driver, and UPDATE and DELETE need no second statement at all",
                    "Точка с запятой закончила наш запрос и начала чужой, поэтому вторая, никак не "
                            + "связанная команда выполнилась с правами приложения на базу. Многие "
                            + "JDBC-драйверы по умолчанию отказываются выполнять несколько запросов "
                            + "разом, и здесь это упало бы, — знать про это полезно, но защитой оно "
                            + "не является: всё зависит от драйвера, а UPDATE и DELETE во втором "
                            + "запросе вообще не нуждаются",
                    List.of("result", "outcome"), state());
            return;
        }
        if (probe.contains("union")) {
            impact = "data-theft";
            leaked++;
            result = rowsResult(USER_COLUMNS, copy(CARDS));
            Trace.event("STATEMENT_EXECUTED",
                    "UNION glued a second SELECT onto ours, so rows from a table this endpoint "
                            + "never mentions come back through it — under our column headers, "
                            + "because a UNION takes its names from the first query. No privilege "
                            + "was escalated: the application's own database user can read that "
                            + "table, and now so can anyone who can type in the search box",
                    "UNION приклеил к нашему второй SELECT, и через эндпоинт вернулись строки из "
                            + "таблицы, которую он вообще не упоминает, — под нашими заголовками "
                            + "колонок, потому что имена в UNION берутся из первого запроса. "
                            + "Никаких прав никто не повышал: пользователь базы, под которым ходит "
                            + "приложение, эту таблицу читать может, а теперь может и любой, кто "
                            + "умеет печатать в строке поиска",
                    List.of("result", "outcome"), state());
            return;
        }
        if (probe.contains("--") || probe.contains("/*")) {
            impact = "auth-bypass";
            leaked++;
            result = rowsResult(USER_COLUMNS, matchByName(beforeQuote(input), false).rows);
            Trace.event("STATEMENT_EXECUTED",
                    "The comment marker deleted the rest of our statement, including the "
                            + "'AND active = TRUE' guard — so a locked account comes back as a "
                            + "perfectly good login. This is why injection is an authentication bug "
                            + "as often as it is a data bug: the check is still in the source code, "
                            + "it just is not in the statement any more",
                    "Маркер комментария удалил остаток нашего запроса вместе с проверкой "
                            + "«AND active = TRUE» — и заблокированный аккаунт возвращается как "
                            + "вполне пригодный для входа. Вот почему инъекция — это ошибка "
                            + "аутентификации не реже, чем ошибка доступа к данным: проверка "
                            + "по-прежнему есть в исходном коде, её просто больше нет в запросе",
                    List.of("result", "outcome"), state());
            return;
        }
        impact = "extra-rows";
        leaked++;
        result = rowsResult(USER_COLUMNS, activeUsers());
        Trace.event("STATEMENT_EXECUTED",
                "The extra condition is always true, so the WHERE clause stopped filtering and the "
                        + "endpoint returned every active row instead of one. Scale that up: the "
                        + "same two characters against a customer table are a data breach, and the "
                        + "access log records one ordinary GET",
                "Дополнительное условие истинно всегда, поэтому WHERE перестал фильтровать и "
                        + "эндпоинт вернул все активные строки вместо одной. Умножьте на масштаб: те "
                        + "же два символа против таблицы клиентов — это утечка персональных данных, "
                        + "а в логе доступа записан один обычный GET",
                List.of("result", "outcome"), state());
    }

    /** Explains what escaping did or failed to do for this slot. */
    private void noteEscaping(String raw, String escaped, Slot slot) {
        if (slot == Slot.STRING_LITERAL) {
            Trace.event("ESCAPING_ATTEMPTED",
                    "Before concatenation every ' in the value was doubled: " + raw + " became "
                            + escaped + ". Inside a quoted literal that is the correct rule, and it "
                            + "holds — as long as the value is inside a quoted literal, as long as "
                            + "the developer remembered, and as long as the database's escaping "
                            + "rules are the ones this code assumed",
                    "Перед конкатенацией каждая ' в значении удвоена: " + raw + " превратилось в "
                            + escaped + ". Внутри литерала в кавычках это верное правило, и оно "
                            + "работает — пока значение находится внутри литерала в кавычках, пока "
                            + "разработчик не забыл его применить и пока правила экранирования базы "
                            + "совпадают с теми, что заложены в этот код",
                    List.of("statement", "config"), state());
            return;
        }
        Trace.event("ESCAPING_BYPASSED",
                "The escaper ran and had nothing to do: there is no quote in this value, because "
                        + "there is no quote in this context. A number is written bare, so the "
                        + "payload needs no quote to escape from — it is already standing next to "
                        + "the operators. Escaping is a rule about one context; the value moved to "
                        + "another one and the rule stayed behind",
                "Экранирование отработало вхолостую: кавычки в этом значении нет, потому что "
                        + "кавычки нет в этом контексте. Число пишется без кавычек, поэтому нагрузке "
                        + "и не нужно ниоткуда выбираться — она уже стоит рядом с операторами. "
                        + "Экранирование — правило про один контекст; значение переехало в другой, а "
                        + "правило осталось",
                List.of("statement", "config"), state());
    }

    // ------------------------------------------------------------- SQL parsing

    /** One lexical token of the statement, with where it came from. */
    private static final class Tok {
        final String text;
        final String kind;
        final int start;
        final int end;
        boolean fromInput;
        boolean danger;

        Tok(String text, String kind, int start, int end) {
            this.text = text;
            this.kind = kind;
            this.start = start;
            this.end = end;
        }
    }

    /** A deliberately small SQL lexer: enough to show literal vs keyword vs comment. */
    private static List<Tok> tokenize(String sql) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (c == '\'') {
                i++;
                while (i < n) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                out.add(new Tok(sql.substring(start, i), "literal", start, i));
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = n;
                out.add(new Tok(sql.substring(start, i), "comment", start, i));
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int close = sql.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
                out.add(new Tok(sql.substring(start, i), "comment", start, i));
            } else if (c == '?') {
                i++;
                out.add(new Tok("?", "parameter", start, i));
            } else if (isWordChar(c)) {
                while (i < n && isWordChar(sql.charAt(i))) {
                    i++;
                }
                String w = sql.substring(start, i);
                String kind = KEYWORDS.contains(w.toUpperCase(Locale.ROOT)) ? "keyword"
                        : isNumber(w) ? "literal" : "identifier";
                out.add(new Tok(w, kind, start, i));
            } else {
                i++;
                out.add(new Tok(sql.substring(start, i), "operator", start, i));
            }
        }
        return out;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private static boolean isNumber(String w) {
        return w.matches("\\d+(\\.\\d+)?");
    }

    /**
     * Marks which tokens came from the untrusted value and which of those are dangerous.
     * A value that produced only literals never left its slot; anything else did.
     */
    private static boolean mark(List<Tok> toks, int inputStart, int inputLen, Slot slot) {
        int end = inputStart + inputLen;
        List<Tok> mine = new ArrayList<>();
        for (Tok t : toks) {
            t.fromInput = inputStart >= 0 && t.end > inputStart && t.start < end;
            if (t.fromInput) {
                mine.add(t);
            }
        }
        if (mine.isEmpty()) {
            return false;
        }
        if (slot == Slot.IDENTIFIER) {
            if (isPlainIdentifier(mine)) {
                return false;
            }
            for (Tok t : mine) {
                t.danger = true;
            }
            return true;
        }
        boolean bad = false;
        for (Tok t : mine) {
            if (!"literal".equals(t.kind)) {
                t.danger = true;
                bad = true;
            }
        }
        return bad;
    }

    /** A safe identifier slot is one bare name, optionally followed by ASC or DESC. */
    private static boolean isPlainIdentifier(List<Tok> mine) {
        if (mine.size() == 1) {
            return "identifier".equals(mine.get(0).kind);
        }
        if (mine.size() == 2 && "identifier".equals(mine.get(0).kind)) {
            String second = mine.get(1).text.toUpperCase(Locale.ROOT);
            return "ASC".equals(second) || "DESC".equals(second);
        }
        return false;
    }

    private static List<Map<String, Object>> tokenMaps(List<Tok> toks) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tok t : toks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", t.text);
            m.put("kind", t.kind);
            m.put("fromInput", t.fromInput);
            m.put("danger", t.danger);
            out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------- XML parsing

    /** Splits the document into lines and labels the ones that are not data. */
    private static List<Map<String, Object>> xmlTokens(String xml) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String line : xml.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String kind;
            boolean danger;
            if (trimmed.contains("<!DOCTYPE")) {
                kind = "dtd";
                danger = true;
            } else if (trimmed.contains("<!ENTITY")) {
                kind = "entity";
                danger = true;
            } else if (trimmed.contains("&") && trimmed.contains(";")) {
                kind = "reference";
                danger = true;
            } else {
                kind = "markup";
                danger = false;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", trimmed);
            m.put("kind", kind);
            m.put("fromInput", true);
            m.put("danger", danger);
            out.add(m);
        }
        return out;
    }

    private static int countEntities(String xml) {
        Matcher m = ANY_ENTITY.matcher(xml);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static int countElements(String xml) {
        int n = 0;
        for (int i = 0; i < xml.length() - 1; i++) {
            if (xml.charAt(i) == '<' && Character.isLetter(xml.charAt(i + 1))) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------- rows

    /** A result set: column names plus rows, in a fixed order. */
    private static final class Rows {
        final List<String> columns;
        final List<List<String>> rows;

        Rows(List<String> columns, List<List<String>> rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }

    private static Rows matchByName(String name, boolean activeOnly) {
        List<List<String>> rows = new ArrayList<>();
        for (String[] u : USERS) {
            boolean active = "true".equals(u[3]);
            if (u[1].equals(name) && (!activeOnly || active)) {
                rows.add(List.of(u[0], u[1], u[2]));
            }
        }
        return new Rows(USER_COLUMNS, rows);
    }

    private static Rows matchById(String id) {
        List<List<String>> rows = new ArrayList<>();
        for (String[] u : USERS) {
            if (u[0].equals(id) && "true".equals(u[3])) {
                rows.add(List.of(u[0], u[1], u[2]));
            }
        }
        return new Rows(USER_COLUMNS, rows);
    }

    private static Rows matchByRole(String role) {
        List<List<String>> rows = new ArrayList<>();
        for (String[] u : USERS) {
            if (u[2].equals(role)) {
                rows.add(List.of(u[0], u[1], u[2]));
            }
        }
        return new Rows(USER_COLUMNS, rows);
    }

    private static Rows sortedUsers(String column) {
        int index = Math.max(0, USER_COLUMNS.indexOf(column));
        List<List<String>> rows = new ArrayList<>();
        for (String[] u : USERS) {
            rows.add(List.of(u[0], u[1], u[2]));
        }
        rows.sort((a, b) -> a.get(index).compareTo(b.get(index)));
        return new Rows(USER_COLUMNS, rows);
    }

    private static List<List<String>> activeUsers() {
        List<List<String>> rows = new ArrayList<>();
        for (String[] u : USERS) {
            if ("true".equals(u[3])) {
                rows.add(List.of(u[0], u[1], u[2]));
            }
        }
        return rows;
    }

    private static List<List<String>> copy(List<String[]> table) {
        List<List<String>> rows = new ArrayList<>();
        for (String[] r : table) {
            rows.add(List.of(r));
        }
        return rows;
    }

    private static String beforeQuote(String value) {
        int q = value.indexOf('\'');
        return q < 0 ? value : value.substring(0, q);
    }

    // ------------------------------------------------------------------ words

    private static String breakoutEn(Slot slot) {
        return switch (slot) {
            case STRING_LITERAL -> "the quote it contains closed the string literal early, so "
                    + "everything after it was read as more SQL";
            case NUMBER -> "there was no quote to close, so the characters landed directly in the "
                    + "expression and became operators and keywords";
            case IDENTIFIER -> "an identifier slot accepts a bare name, and this value is not one — "
                    + "the extra tokens were parsed as more of the statement";
            case NONE -> "the value reached the statement text";
        };
    }

    private static String breakoutRu(Slot slot) {
        return switch (slot) {
            case STRING_LITERAL -> "содержащаяся в нём кавычка досрочно закрыла строковый литерал, и "
                    + "всё, что после неё, прочитано как продолжение SQL";
            case NUMBER -> "закрывать было нечего — кавычки тут нет, поэтому символы попали прямо в "
                    + "выражение и стали операторами и ключевыми словами";
            case IDENTIFIER -> "в месте идентификатора ожидается голое имя, а это значение им не "
                    + "является: лишние токены разобраны как продолжение запроса";
            case NONE -> "значение попало в текст запроса";
        };
    }

    private static String safeEn(String reason) {
        return switch (reason) {
            case "binding" -> "it never entered the statement text at all, so there was no grammar "
                    + "for it to join";
            case "escaping" -> "the doubled quotes kept every character inside one string literal";
            case "allowlist" -> "the string that reached the SQL was chosen from a set we wrote, not "
                    + "supplied by the caller";
            case "parser-config" -> "the parser was not willing to act on it";
            default -> "nothing in it meant anything to the parser";
        };
    }

    private static String safeRu(String reason) {
        return switch (reason) {
            case "binding" -> "оно вообще не попало в текст запроса, и присоединяться к грамматике "
                    + "ему было негде";
            case "escaping" -> "удвоенные кавычки удержали каждый символ внутри одного строкового "
                    + "литерала";
            case "allowlist" -> "строка, дошедшая до SQL, выбрана из набора, который написали мы, а "
                    + "не прислал вызывающий";
            case "parser-config" -> "парсер не согласился по нему действовать";
            default -> "в нём не было ничего значащего для парсера";
        };
    }

    // ------------------------------------------------------------------ state

    private void begin(String language, String binding, String template, String text,
                       String input, String channel) {
        statements++;
        this.language = language;
        this.binding = binding;
        this.template = template;
        this.text = text;
        this.input = input;
        this.channel = channel;
        this.stage = "built";
        this.injected = false;
        this.impact = "none";
        this.blockedBy = null;
        this.tokens = new ArrayList<>();
        this.result = null;
    }

    private static Map<String, Object> rowsResult(List<String> columns, List<List<String>> rows) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kind", "rows");
        r.put("columns", columns);
        r.put("rows", rows);
        r.put("text", null);
        return r;
    }

    private static Map<String, Object> textResult(String text) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kind", "text");
        r.put("columns", List.of());
        r.put("rows", List.of());
        r.put("text", text);
        return r;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("escapeQuotes", escapeQuotes);
        config.put("xmlDoctype", doctypeAllowed ? "allowed" : "disallowed");
        s.put("config", config);

        if (language != null) {
            Map<String, Object> statement = new LinkedHashMap<>();
            statement.put("language", language);
            statement.put("binding", binding);
            statement.put("template", template);
            statement.put("text", text);
            statement.put("input", input);
            statement.put("channel", channel);
            s.put("statement", statement);

            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("stage", stage);
            outcome.put("injected", injected);
            outcome.put("impact", impact);
            outcome.put("blockedBy", blockedBy);
            s.put("outcome", outcome);
        } else {
            s.put("statement", null);
            s.put("outcome", null);
        }

        s.put("tokens", new ArrayList<>(tokens));
        s.put("result", result);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("statements", statements);
        stats.put("injections", injections);
        stats.put("leaked", leaked);
        stats.put("stayedData", stayedData);
        s.put("stats", stats);
        return s;
    }
}
