package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A <em>teaching model</em> of cross-site scripting: a web page that takes a value
 * somebody else typed and puts it into the document, and a browser that then has to
 * decide whether that value is <em>data</em> or <em>code</em>.
 *
 * <p>The whole vulnerability lives in that decision. The server builds one string of
 * HTML; the browser parses it and cannot tell which characters came from the template
 * and which came from an attacker. If the untrusted part contains markup, the browser
 * obeys it — and the resulting script runs inside the victim's session, on the site's
 * own origin, with the site's cookies and the user's privileges.
 *
 * <p>The model makes three things visible that prose usually hides:
 * <ul>
 *   <li><b>the sink</b> — <em>where</em> the value lands ({@link Sink}). The same
 *       payload is harmless in one place and executable in another, which is why
 *       "we escape user input" is not by itself a defence;</li>
 *   <li><b>the source</b> — reflected (echoed back from the request), stored (saved
 *       and served to everyone afterwards) or DOM-based (the page's own JavaScript
 *       does it, and the server never sees the value at all);</li>
 *   <li><b>the layers</b> — output encoding and allowlist sanitizing actually fix it;
 *       a Content-Security-Policy and HttpOnly cookies only limit the damage.</li>
 * </ul>
 *
 * <p>The escaping, sanitizing and "does this execute?" rules here are deliberate
 * simplifications of what a real browser and a real encoder do — enough to be
 * faithful about <em>which defence stops which attack</em>, and small enough to read.
 * Every step emits a bilingual {@link Trace} event; the class is dependency-free.
 */
public class VisualXss {

    /** The session cookie an XSS payload is usually after. */
    public static final String COOKIE_NAME = "session";
    /** Its value — the thing that lets the attacker become the victim. */
    public static final String COOKIE_VALUE = "s3cr3t-9f2a";

    private static final Pattern TAG_START = Pattern.compile("<\\s*[a-z][a-z0-9]*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_TAG = Pattern.compile("<\\s*script", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\bon[a-z]+\\s*=",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_URL = Pattern.compile("^\\s*javascript\\s*:",
            Pattern.CASE_INSENSITIVE);

    /**
     * Where in the page the untrusted value ends up. Each sink is a different
     * language with different metacharacters, and each therefore needs a different
     * encoder — the single most misunderstood point about XSS.
     */
    public enum Sink {

        /** Between two tags: the plain "print what the user typed" case. */
        HTML_TEXT("<p>", "</p>",
                "between two tags in the page body",
                "между тегами в теле страницы"),
        /** Inside a double-quoted attribute value, e.g. the value of a search box. */
        ATTRIBUTE("<input value=\"", "\">",
                "inside a quoted HTML attribute",
                "внутри HTML-атрибута в кавычках"),
        /** Inside a script block: the value becomes part of the JavaScript source itself. */
        SCRIPT("<script>var q = '", "';</script>",
                "inside a <script> block, as part of the JavaScript source",
                "внутри блока <script>, как часть исходного кода JavaScript"),
        /** As the target of a link, where the danger is the scheme, not the characters. */
        URL("<a href=\"", "\">details</a>",
                "as the target of a link",
                "в адресе ссылки"),
        /** Written into the document by the page's own JavaScript, with no server involved. */
        INNER_HTML("box.innerHTML = \"", "\";",
                "assigned to innerHTML by the page's own JavaScript",
                "в innerHTML, куда его запишет собственный JavaScript страницы");

        private final String before;
        private final String after;
        private final String en;
        private final String ru;

        Sink(String before, String after, String en, String ru) {
            this.before = before;
            this.after = after;
            this.en = en;
            this.ru = ru;
        }
    }

    // ------------------------------------------------------------------- state

    /** none | naive | context-aware. */
    private String encoding = "none";
    private boolean sanitizer;
    private String csp;
    private boolean httpOnly;

    private final Map<String, String> stored = new LinkedHashMap<>();
    private int visitors;

    private String source;
    private Sink sink;
    private String input;
    private String output;
    private boolean encodedNow;
    private boolean sanitizedNow;
    private String html;
    private String stage = "idle";
    private boolean injected;
    private boolean executed;
    private String blockedBy;
    private boolean cookieStolen;
    private final List<Map<String, Object>> nodes = new ArrayList<>();

    private int renders;
    private int injections;
    private int executions;
    private int cookiesStolen;
    private int blockedByCsp;
    private int neutralized;

    private VisualXss() {
    }

    /**
     * A site that renders whatever it is given, exactly as it was given. This is not a
     * contrived setup: it is what string concatenation, an unescaped template and
     * {@code innerHTML} all do by default.
     */
    public static VisualXss site() {
        VisualXss site = new VisualXss();
        Trace.event("XSS_SETUP",
                "A page that builds its HTML by pasting values into a template, with no output "
                        + "encoding anywhere. The user is signed in, so the browser holds a session "
                        + "cookie (" + COOKIE_NAME + ") for this origin — remember that any script "
                        + "running on this page can read and use it, because the browser has no way to "
                        + "tell the site's own script from an injected one",
                "Страница, которая собирает свой HTML, подставляя значения в шаблон, вообще без "
                        + "экранирования вывода. Пользователь залогинен, поэтому в браузере есть "
                        + "cookie сессии (" + COOKIE_NAME + ") для этого origin — и любой скрипт, "
                        + "выполняющийся на этой странице, может его прочитать и использовать, потому "
                        + "что браузер никак не отличает собственный скрипт сайта от внедрённого",
                List.of("defences", "cookie"), site.state());
        return site;
    }

    // ---------------------------------------------------------------- defences

    /**
     * The escaper everybody writes first: it replaces {@code & < > "} and stops there.
     * Correct for the HTML body, and quietly useless in a script block or a URL.
     */
    public VisualXss escapeAngleBrackets() {
        encoding = "naive";
        Trace.event("DEFENCE_ENABLED",
                "Output encoding is switched on, but only the classic four characters are replaced: "
                        + "& < > and the double quote. That is genuinely enough to stop a tag from "
                        + "being opened in the page body — and it says nothing at all about the other "
                        + "places a value can land, because those places are not HTML",
                "Включено экранирование вывода, но заменяются только четыре классических символа: "
                        + "& < > и двойная кавычка. Открыть тег в теле страницы этого действительно "
                        + "достаточно, чтобы не дать, — и это ничего не говорит про остальные места, "
                        + "куда может попасть значение, потому что те места — не HTML",
                List.of("defences"), state());
        return this;
    }

    /**
     * What a modern template engine does: pick the encoder from the sink — HTML entities
     * in markup, {@code \\uXXXX} escapes in JavaScript, a scheme allowlist in a URL.
     */
    public VisualXss encodeForContext() {
        encoding = "context-aware";
        Trace.event("DEFENCE_ENABLED",
                "Context-aware output encoding is switched on: the encoder is chosen by WHERE the "
                        + "value is going. HTML entities between tags and in attributes, \\uXXXX "
                        + "escapes inside a script block, and a scheme allowlist for a link target. "
                        + "This is the actual fix for XSS, and it is the one thing a template engine "
                        + "can do for you automatically",
                "Включено контекстно-зависимое экранирование вывода: кодировщик выбирается по тому, "
                        + "КУДА идёт значение. HTML-сущности между тегами и в атрибутах, "
                        + "экранирование \\uXXXX внутри блока script и белый список схем для адреса "
                        + "ссылки. Именно это и чинит XSS, и именно это шаблонизатор может сделать за "
                        + "вас автоматически",
                List.of("defences"), state());
        return this;
    }

    /**
     * For the case where the value must stay HTML — a rich-text comment. An allowlist
     * sanitizer keeps the handful of tags you decided to permit and drops everything
     * else; a blocklist ("strip the word script") loses to the next encoding trick.
     */
    public VisualXss sanitizeRichText() {
        sanitizer = true;
        Trace.event("DEFENCE_ENABLED",
                "An allowlist sanitizer is switched on for rich text: parse the HTML, keep the few "
                        + "tags and attributes that were explicitly permitted, drop everything else. "
                        + "Use this only where the value really must remain markup — everywhere else, "
                        + "encoding is simpler and safer. And it must be an allowlist: a blocklist "
                        + "loses to the first payload you did not think of",
                "Для форматированного текста включён санитайзер с белым списком: разобрать HTML, "
                        + "оставить те немногие теги и атрибуты, которые явно разрешены, всё "
                        + "остальное выбросить. Это нужно только там, где значение обязано остаться "
                        + "разметкой, — во всех прочих местах экранирование проще и надёжнее. И "
                        + "список должен быть именно белым: чёрный проигрывает первой же полезной "
                        + "нагрузке, о которой вы не подумали",
                List.of("defences"), state());
        return this;
    }

    /**
     * A Content-Security-Policy. It does not stop the injection; it stops the injected
     * script from running — a second line of defence for the bug you missed.
     */
    public VisualXss contentSecurityPolicy(String policy) {
        csp = policy;
        boolean effective = blocksInlineScript(policy);
        Trace.event("DEFENCE_ENABLED",
                "A Content-Security-Policy header is sent: " + policy + ". "
                        + (effective
                            ? "Inline script is not on that list, so a script the browser finds in the "
                              + "markup is refused before it runs. Note what this is: a safety net for "
                              + "the encoding bug you have not found yet, not a substitute for the "
                              + "encoding"
                            : "It allows 'unsafe-inline', which is exactly what an injected inline "
                              + "script is — so this policy will not stop the attack it looks like it "
                              + "was written to stop"),
                "Отправляется заголовок Content-Security-Policy: " + policy + ". "
                        + (effective
                            ? "Встроенных скриптов в этом списке нет, поэтому скрипт, найденный "
                              + "браузером в разметке, отклоняется до выполнения. Обратите внимание, "
                              + "что это такое: страховка для ещё не найденной ошибки в "
                              + "экранировании, а не замена самому экранированию"
                            : "В ней разрешён 'unsafe-inline' — а внедрённый скрипт как раз "
                              + "встроенный, поэтому такая политика не остановит ту самую атаку, "
                              + "ради которой её как будто и писали"),
                List.of("defences"), state());
        return this;
    }

    /**
     * Marks the session cookie HttpOnly, so {@code document.cookie} cannot see it.
     * The script still runs — it simply has to attack through the browser instead of
     * mailing the cookie home.
     */
    public VisualXss httpOnlySession() {
        httpOnly = true;
        Trace.event("DEFENCE_ENABLED",
                "The session cookie is now HttpOnly: JavaScript cannot read it through "
                        + "document.cookie. This shortens the blast radius and it is worth doing, but "
                        + "be clear about what it does not do — the injected script still runs on your "
                        + "origin, and the browser still attaches that cookie to every request the "
                        + "script makes. It can act as the user without ever seeing the cookie",
                "Cookie сессии теперь HttpOnly: JavaScript не может прочитать её через "
                        + "document.cookie. Это сокращает радиус поражения, и делать так стоит, но "
                        + "надо понимать, чего это не делает: внедрённый скрипт по-прежнему "
                        + "выполняется на вашем origin, и браузер по-прежнему прикрепляет эту cookie "
                        + "к каждому запросу, который скрипт отправит. Он может действовать от имени "
                        + "пользователя, вообще не видя cookie",
                List.of("defences", "cookie"), state());
        return this;
    }

    // ----------------------------------------------------------------- attacks

    /**
     * Reflected XSS: the value arrives in the request (a query parameter, usually) and
     * the very same response echoes it back. The attack needs a victim to open a
     * prepared link, and it leaves nothing behind on the server.
     */
    public void reflect(Sink sink, String value) {
        render("reflected", sink, value,
                "The value arrives as a request parameter and this same response prints it back — "
                        + "a reflected payload. It is not stored anywhere; the attacker's delivery "
                        + "vehicle is a link they get the victim to click, which is why these turn up "
                        + "in phishing mail",
                "Значение приходит параметром запроса, и этот же ответ печатает его обратно — "
                        + "отражённая полезная нагрузка. Нигде не сохраняется; способ доставки — "
                        + "ссылка, по которой злоумышленник уговаривает жертву кликнуть, потому такие "
                        + "штуки и встречаются в фишинговых письмах");
    }

    /** Persists an untrusted value — a comment, a profile field, a support ticket. */
    public VisualXss save(String field, String value) {
        stored.put(field, value);
        boolean dangerous = looksExecutable(value);
        Trace.event("INPUT_STORED",
                "'" + field + "' is saved to the database exactly as it was typed: " + value
                        + (dangerous
                            ? ". Storing it is not the bug — a database column holding angle brackets "
                              + "harms nobody. The bug will happen later, when this string is printed "
                              + "into a page, and it will happen once per reader"
                            : ". Nothing about it is executable, but nothing about the storage step "
                              + "checked that either"),
                "«" + field + "» сохраняется в базу ровно в том виде, в каком его напечатали: " + value
                        + (dangerous
                            ? ". Само по себе сохранение — не ошибка: колонка в базе с угловыми "
                              + "скобками никому не вредит. Ошибка случится позже, когда эту строку "
                              + "напечатают в страницу, и случится по одному разу на каждого читателя"
                            : ". Ничего исполняемого в нём нет, но и на этапе сохранения этого никто "
                              + "не проверял"),
                List.of("stored"), state());
        return this;
    }

    /**
     * Stored XSS: one visitor opens the page and gets served what somebody else saved.
     * No link to click, no victim to lure — everyone who reads the page is hit.
     */
    public void showSaved(Sink sink, String field) {
        visitors++;
        String value = stored.getOrDefault(field, "");
        Trace.event("STORED_PAYLOAD_SERVED",
                "Visitor " + visitors + " opens the page, and the server hands them the saved '"
                        + field + "' along with the rest of it. This is why stored XSS is the "
                        + "expensive kind: the attacker submitted once, the site serves it to every "
                        + "reader from then on, and the victims did nothing but visit a page they "
                        + "already trusted",
                "Посетитель " + visitors + " открывает страницу, и сервер отдаёт ему сохранённое "
                        + "поле «" + field + "» вместе со всем остальным. Именно поэтому хранимый XSS "
                        + "— дорогой: злоумышленник отправил один раз, а сайт с тех пор отдаёт это "
                        + "каждому читателю, и жертвы не сделали ничего, кроме как зашли на "
                        + "страницу, которой и так доверяли",
                List.of("stored"), state());
        render("stored", sink, value,
                "The saved value is printed into the page for this visitor",
                "Сохранённое значение печатается в страницу для этого посетителя");
    }

    /**
     * DOM-based XSS: the page's own JavaScript reads part of the URL and writes it into
     * the document. The fragment after {@code #} is never sent to the server, so nothing
     * on the server side — not the encoder, not the WAF, not the access log — sees it.
     */
    public void domRender(String urlFragment) {
        render("dom", Sink.INNER_HTML, urlFragment,
                "The page's own JavaScript reads location.hash and assigns it to innerHTML. Look at "
                        + "what that means for the server: the fragment after '#' is never sent, so "
                        + "the request log, the template encoder and the WAF all see a perfectly "
                        + "ordinary page load",
                "Собственный JavaScript страницы читает location.hash и присваивает его в innerHTML. "
                        + "Посмотрите, что это значит для сервера: фрагмент после «#» вообще не "
                        + "отправляется, поэтому и лог запросов, и экранирование в шаблоне, и WAF "
                        + "видят совершенно обычную загрузку страницы");
    }

    /** Prints what the whole run added up to. */
    public void report() {
        Trace.event("XSS_AUDIT",
                "After " + renders + " render(s): the untrusted value became markup " + injections
                        + " time(s), a script actually ran " + executions + " time(s), the session "
                        + "cookie was read " + cookiesStolen + " time(s), CSP stopped a script "
                        + blockedByCsp + " time(s), and the payload was reduced to harmless text "
                        + neutralized + " time(s). The number to design for is the second one, and "
                        + "the only reliable way to keep it at zero is to encode on output, in every "
                        + "sink, by default",
                "После отрисовок (" + renders + "): недоверенное значение стало разметкой "
                        + injections + " раз, скрипт реально выполнился " + executions + " раз, "
                        + "cookie сессии прочитали " + cookiesStolen + " раз, CSP остановил скрипт "
                        + blockedByCsp + " раз, а полезная нагрузка превратилась в безобидный текст "
                        + neutralized + " раз. Проектировать надо по второму числу, и единственный "
                        + "надёжный способ держать его на нуле — экранировать на выводе, в каждом "
                        + "месте вывода, по умолчанию",
                List.of(), state());
    }

    // -------------------------------------------------------------- the engine

    private void render(String source, Sink sink, String value, String originEn, String originRu) {
        renders++;
        beginRender(source, sink, value);

        boolean serverSide = !"dom".equals(source);
        Trace.event("INPUT_RECEIVED",
                originEn + ". It will end up " + sink.en + ", and right now it is just a string: "
                        + describe(value),
                originRu + ". Оно окажется " + sink.ru + ", и прямо сейчас это просто строка: "
                        + describe(value),
                List.of("input"), state());

        String working = value;
        if (serverSide && sanitizer && (sink == Sink.HTML_TEXT || sink == Sink.INNER_HTML)) {
            String clean = sanitize(working);
            if (!clean.equals(working)) {
                sanitizedNow = true;
                working = clean;
                output = working;
                Trace.event("INPUT_SANITIZED",
                        "The sanitizer parsed the value as HTML and kept only what the allowlist "
                                + "permits, so what is left is " + describe(working) + ". The "
                                + "formatting the user asked for survives; the script tag and the "
                                + "event handlers do not",
                        "Санитайзер разобрал значение как HTML и оставил только то, что разрешает "
                                + "белый список, — осталось " + describe(working) + ". "
                                + "Форматирование, о котором просил пользователь, сохранилось; тег "
                                + "script и обработчики событий — нет",
                        List.of("input", "defences"), state());
            }
        }

        if (serverSide && !"none".equals(encoding)) {
            String encoded = "naive".equals(encoding) ? escapeBasic(working) : encodeFor(sink, working);
            encodedNow = true;
            working = encoded;
            output = working;
            Trace.event("INPUT_ENCODED",
                    "The " + ("naive".equals(encoding) ? "& < > \" escaper" : "encoder for this sink")
                            + " rewrites the value to " + describe(working)
                            + ". Nothing was removed and nothing was validated — the characters that "
                            + "would have meant something to the parser were replaced with characters "
                            + "that only mean themselves",
                    "" + ("naive".equals(encoding)
                            ? "Экранирование символов & < > \""
                            : "Кодировщик для этого места вывода")
                            + " переписывает значение в " + describe(working)
                            + ". Ничего не удалено и ничего не проверено — символы, которые что-то "
                            + "значили бы для парсера, заменены на символы, значащие только самих себя",
                    List.of("input", "defences"), state());
        } else if (!serverSide && !"none".equals(encoding)) {
            Trace.event("ENCODING_SKIPPED",
                    "The server's encoder is configured and it does not run here: this value never "
                            + "travelled through the server. Server-side encoding protects the HTML "
                            + "the server builds, and this line of HTML is built in the browser",
                    "Экранирование на сервере настроено, и здесь оно не срабатывает: это значение "
                            + "через сервер не проходило. Серверное экранирование защищает тот HTML, "
                            + "который собирает сервер, а эта разметка собирается в браузере",
                    List.of("input", "defences"), state());
        }

        output = working;
        html = sink.before + working + sink.after;
        stage = "rendered";
        Trace.event("PAGE_RENDERED",
                "The page is now one flat string of markup: " + html
                        + ". This is the moment the value stops being 'user input' and becomes "
                        + "indistinguishable from the template around it — nothing downstream can "
                        + "tell which characters somebody else chose",
                "Страница теперь — одна плоская строка разметки: " + html
                        + ". Именно здесь значение перестаёт быть «пользовательским вводом» и "
                        + "становится неотличимым от шаблона вокруг него: дальше уже никто не "
                        + "определит, какие символы выбрал посторонний",
                List.of("html"), state());

        injected = breaksOut(sink, working);
        buildNodes(sink, working);
        stage = "parsed";
        Trace.event("BROWSER_PARSED",
                "The browser parses that string and decides what each part IS: template markup, a "
                        + "text node, or code. The untrusted part came out as "
                        + (injected ? "code" : "a text node")
                        + ", and that single classification is the whole difference between a "
                        + "rendered comment and a compromised account",
                "Браузер разбирает эту строку и решает, ЧЕМ является каждая часть: разметкой "
                        + "шаблона, текстовым узлом или кодом. Недоверенная часть оказалась "
                        + (injected ? "кодом" : "текстовым узлом")
                        + ", и вся разница между отрисованным комментарием и угнанным аккаунтом — "
                        + "именно в этой классификации",
                List.of("nodes"), state());

        if (!injected) {
            neutralize(sink, value);
            return;
        }
        escalate(source, sink, value);
    }

    /** The payload did not become code: say precisely what stopped it, or that nothing had to. */
    private void neutralize(Sink sink, String rawValue) {
        boolean wasDangerous = breaksOut(sink, rawValue);
        if (sanitizedNow) {
            blockedBy = "sanitizer";
        } else if (encodedNow && wasDangerous) {
            blockedBy = "encoding";
        }
        neutralized++;
        stage = "settled";
        String how;
        String howRu;
        if (blockedBy == null) {
            how = "nothing in this value meant anything to the parser in the first place";
            howRu = "в этом значении и не было ничего, что что-то значило бы для парсера";
        } else if ("sanitizer".equals(blockedBy)) {
            how = "the sanitizer had already removed the parts that would have executed";
            howRu = "санитайзер уже удалил те части, которые бы выполнились";
        } else if (sink == Sink.URL) {
            how = "the scheme was not on the allowlist, so the link now points at about:blank and "
                    + "clicking it does nothing";
            howRu = "схемы не было в белом списке, поэтому ссылка теперь ведёт на about:blank и клик "
                    + "по ней ничего не делает";
        } else if (sink == Sink.SCRIPT) {
            how = "the quote that would have closed the JavaScript string is a \\uXXXX escape now, so "
                    + "it stays a character inside the string instead of ending it";
            howRu = "кавычка, которая закрыла бы строку JavaScript, теперь записана как \\uXXXX, "
                    + "поэтому она остаётся символом внутри строки, а не завершает её";
        } else {
            how = "the characters that would have opened a tag are entities now, and an entity is text";
            howRu = "символы, которые открыли бы тег, теперь сущности, а сущность — это текст";
        }
        Trace.event("RENDERED_AS_TEXT",
                "The value is displayed, not executed: " + how
                        + ". The user sees exactly what was typed, which is also the point — a correct "
                        + "defence does not mangle the data, it just refuses to let it change the "
                        + "structure of the document",
                "Значение показано, а не выполнено: " + howRu
                        + ". Пользователь видит ровно то, что было напечатано, — и в этом тоже смысл: "
                        + "правильная защита не портит данные, она лишь не даёт им менять структуру "
                        + "документа",
                List.of("nodes", "outcome"), state());
    }

    /** The payload became code: walk the consequences, layer by layer. */
    private void escalate(String source, Sink sink, String rawValue) {
        injections++;
        Trace.event("SCRIPT_INJECTED",
                "The untrusted value did not stay inside its slot — " + breakoutEn(sink)
                        + ". From the browser's point of view this is now part of the page the site "
                        + "sent, and it carries the site's origin: same cookies, same localStorage, "
                        + "same permissions, same everything",
                "Недоверенное значение не осталось в своём месте — " + breakoutRu(sink)
                        + ". С точки зрения браузера это теперь часть страницы, которую прислал сайт, "
                        + "и она несёт origin сайта: те же cookie, тот же localStorage, те же "
                        + "разрешения, то же всё",
                List.of("nodes"), state());

        if (encodedNow) {
            Trace.event("WRONG_CONTEXT",
                    "Notice that the value WAS escaped and it executed anyway: " + wrongContextEn(sink)
                            + ". This is the trap in 'we escape user input' — escaping is not one "
                            + "operation, it is one operation per context, and applying the HTML one "
                            + "everywhere leaves the other contexts exactly as open as before",
                    "Обратите внимание: значение БЫЛО экранировано и всё равно выполнилось — "
                            + wrongContextRu(sink)
                            + ". В этом и ловушка фразы «мы экранируем пользовательский ввод»: "
                            + "экранирование — это не одна операция, а по одной операции на каждый "
                            + "контекст, и если применять везде HTML-вариант, остальные контексты "
                            + "остаются ровно так же открыты",
                    List.of("nodes", "defences"), state());
        }

        if ("dom".equals(source)) {
            Trace.event("DOM_XSS",
                    "This one is DOM-based: the payload went from the URL into the document without "
                            + "the server ever being told. Nothing is in the access log, nothing is in "
                            + "the database, and the server-side template was never involved — so the "
                            + "fix has to be in the browser code: assign to textContent, or sanitize "
                            + "before touching innerHTML",
                    "Этот XSS — DOM-based: полезная нагрузка попала из URL в документ, и серверу об "
                            + "этом никто не сообщил. В логе доступа ничего нет, в базе ничего нет, "
                            + "серверный шаблон вообще не участвовал — значит, и чинить надо в коде "
                            + "браузера: присваивать в textContent или санитайзить перед тем, как "
                            + "трогать innerHTML",
                    List.of("nodes", "outcome"), state());
        }

        if (csp != null && blocksInlineScript(csp)) {
            blockedBy = "csp";
            blockedByCsp++;
            stage = "settled";
            Trace.event("CSP_BLOCKED",
                    "The browser refuses to run it: the policy " + csp + " does not permit inline "
                            + "script, so the injected code is parsed and then dropped. The injection "
                            + "still happened — the document is still wrong, the report is still worth "
                            + "reading — but this particular attempt earns a console error instead of "
                            + "a stolen session",
                    "Браузер отказывается это выполнять: политика " + csp + " не разрешает встроенные "
                            + "скрипты, поэтому внедрённый код разбирается и отбрасывается. Внедрение "
                            + "всё равно произошло — документ по-прежнему неправильный, отчёт "
                            + "по-прежнему стоит прочитать, — но конкретно эта попытка получает ошибку "
                            + "в консоли вместо угнанной сессии",
                    List.of("nodes", "defences", "outcome"), state());
            return;
        }

        executed = true;
        executions++;
        stage = "settled";
        Trace.event("SCRIPT_EXECUTED",
                "Somebody else's JavaScript is now running inside this user's session, on this site's "
                        + "origin. That is the definition of XSS, and it is worth spelling out what it "
                        + "buys the attacker: read anything on the page, rewrite anything on the page, "
                        + "and call any endpoint as this user — every request the script makes carries "
                        + "the user's cookies, and the server cannot tell it apart from a real click",
                "Внутри сессии этого пользователя, на origin этого сайта, теперь выполняется чужой "
                        + "JavaScript. Это и есть определение XSS, и стоит проговорить, что оно даёт "
                        + "злоумышленнику: читать что угодно на странице, переписывать что угодно на "
                        + "странице и обращаться к любому эндпоинту от имени этого пользователя — "
                        + "каждый запрос скрипта несёт cookie пользователя, и сервер не отличит его от "
                        + "настоящего клика",
                List.of("nodes", "outcome"), state());
        stealCookie();
    }

    private void stealCookie() {
        if (httpOnly) {
            Trace.event("COOKIE_PROTECTED",
                    "The script reads document.cookie and gets nothing: the session cookie is "
                            + "HttpOnly. This is a real reduction in damage and it is not a fix — the "
                            + "script simply switches tactics and calls POST /transfer directly. The "
                            + "browser attaches the cookie for it, so it never needed to see it",
                    "Скрипт читает document.cookie и не получает ничего: cookie сессии помечена "
                            + "HttpOnly. Ущерб действительно уменьшился, но это не починка — скрипт "
                            + "просто меняет тактику и сам вызывает POST /transfer. Браузер "
                            + "прикрепляет cookie за него, так что видеть её ему и не требовалось",
                    List.of("cookie", "outcome"), state());
            return;
        }
        cookieStolen = true;
        cookiesStolen++;
        Trace.event("COOKIE_STOLEN",
                "document.cookie hands over " + COOKIE_NAME + "=" + COOKIE_VALUE
                        + " and the script sends it to a server the attacker controls. From now on "
                        + "they do not need the victim at all: replaying that cookie logs them in as "
                        + "this user, and nothing in the login history will look unusual",
                "document.cookie отдаёт " + COOKIE_NAME + "=" + COOKIE_VALUE
                        + ", и скрипт отправляет это на сервер злоумышленника. С этого момента жертва "
                        + "ему больше не нужна: повторив эту cookie, он входит как этот пользователь, "
                        + "и в истории входов ничего необычного видно не будет",
                List.of("cookie", "outcome"), state());
    }

    // ---------------------------------------------------------------- analysis

    /** Whether the value escapes its slot in this sink and turns into something executable. */
    private static boolean breaksOut(Sink sink, String value) {
        return switch (sink) {
            case HTML_TEXT, INNER_HTML -> looksExecutable(value);
            case ATTRIBUTE -> value.contains("\"")
                    && (looksExecutable(value) || EVENT_HANDLER.matcher(value).find());
            case SCRIPT -> value.contains("'") || value.contains("\"")
                    || value.toLowerCase(Locale.ROOT).contains("</script");
            case URL -> JS_URL.matcher(value).find();
        };
    }

    /** A tag that either is a script or carries an event handler. */
    private static boolean looksExecutable(String value) {
        if (SCRIPT_TAG.matcher(value).find()) {
            return true;
        }
        return TAG_START.matcher(value).find() && EVENT_HANDLER.matcher(value).find();
    }

    /** The escaper that only knows about HTML's four obvious metacharacters. */
    private static String escapeBasic(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** The right encoder for each sink — the point of the whole model. */
    private static String encodeFor(Sink sink, String value) {
        return switch (sink) {
            case HTML_TEXT, ATTRIBUTE, INNER_HTML -> escapeBasic(value).replace("'", "&#39;");
            case SCRIPT -> escapeForJavaScript(value);
            case URL -> allowlistUrl(value);
        };
    }

    /** Inside a script block the metacharacters are JavaScript's, so escape them as \\uXXXX. */
    private static String escapeForJavaScript(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' || c == '"' || c == '\\' || c == '<' || c == '>' || c == '&' || c == '/') {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** In a link the danger is the scheme, so allow the two that are not code. */
    private static String allowlistUrl(String value) {
        String probe = value.trim().toLowerCase(Locale.ROOT);
        boolean allowed = probe.startsWith("http://") || probe.startsWith("https://")
                || probe.startsWith("/");
        return allowed ? escapeBasic(value) : "about:blank";
    }

    /** The allowlist sanitizer: keep the permitted markup, drop the executable parts. */
    private static String sanitize(String value) {
        String out = value.replaceAll("(?is)<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>", "");
        out = out.replaceAll("(?is)<\\s*/?\\s*script[^>]*>", "");
        out = out.replaceAll("(?is)<\\s*/?\\s*(iframe|object|embed|svg)[^>]*>", "");
        out = out.replaceAll("(?is)\\son[a-z]+\\s*=\\s*\"[^\"]*\"", "");
        out = out.replaceAll("(?is)\\son[a-z]+\\s*=\\s*'[^']*'", "");
        out = out.replaceAll("(?is)\\son[a-z]+\\s*=\\s*[^\\s>]+", "");
        out = out.replaceAll("(?is)javascript\\s*:", "");
        return out;
    }

    private static boolean blocksInlineScript(String policy) {
        String probe = policy.toLowerCase(Locale.ROOT);
        return probe.contains("script-src") && !probe.contains("unsafe-inline");
    }

    private static String describe(String value) {
        return value.isEmpty() ? "(empty)" : value;
    }

    private static String breakoutEn(Sink sink) {
        return switch (sink) {
            case HTML_TEXT, INNER_HTML -> "the '<' it contains opened a real tag, so the browser built "
                    + "an element where the template meant to put text";
            case ATTRIBUTE -> "the quote it contains closed the attribute early, and everything after "
                    + "it was read as more attributes on that tag";
            case SCRIPT -> "the quote it contains closed the JavaScript string, so the rest of the "
                    + "value was read as more JavaScript";
            case URL -> "the link's scheme is javascript:, so 'following' this link means running it";
        };
    }

    private static String breakoutRu(Sink sink) {
        return switch (sink) {
            case HTML_TEXT, INNER_HTML -> "содержащийся в нём символ «<» открыл настоящий тег, и "
                    + "браузер построил элемент там, где шаблон собирался вывести текст";
            case ATTRIBUTE -> "содержащаяся в нём кавычка досрочно закрыла атрибут, и всё, что после "
                    + "неё, прочитано как дополнительные атрибуты этого тега";
            case SCRIPT -> "содержащаяся в нём кавычка закрыла строку JavaScript, и остаток значения "
                    + "прочитан как продолжение кода JavaScript";
            case URL -> "схема ссылки — javascript:, поэтому «перейти» по такой ссылке значит "
                    + "выполнить её";
        };
    }

    private static String wrongContextEn(Sink sink) {
        return switch (sink) {
            case SCRIPT -> "HTML entities are decoded by the HTML parser, and the inside of a script "
                    + "block is not parsed as HTML — it is JavaScript source, where the character that "
                    + "matters is the quote, and the escaper left it alone";
            case URL -> "there is nothing to escape: javascript:alert(1) contains no angle bracket and "
                    + "no quote, so an HTML escaper passes it through untouched. A URL needs its "
                    + "SCHEME checked, not its characters replaced";
            case ATTRIBUTE, HTML_TEXT, INNER_HTML -> "the encoder that ran does not cover the "
                    + "characters that carry meaning here, so it replaced the harmless ones and left "
                    + "the dangerous one alone";
        };
    }

    private static String wrongContextRu(Sink sink) {
        return switch (sink) {
            case SCRIPT -> "HTML-сущности раскрывает HTML-парсер, а внутренность блока script как HTML "
                    + "не разбирается — это исходник JavaScript, где важна кавычка, а её "
                    + "экранирование не тронуло";
            case URL -> "экранировать тут нечего: в javascript:alert(1) нет ни угловой скобки, ни "
                    + "кавычки, поэтому HTML-экранирование пропускает такое нетронутым. У адреса надо "
                    + "проверять СХЕМУ, а не заменять символы";
            case ATTRIBUTE, HTML_TEXT, INNER_HTML -> "сработавший кодировщик не покрывает те символы, "
                    + "которые здесь что-то значат: безобидные он заменил, а опасный оставил как есть";
        };
    }

    // ------------------------------------------------------------------ state

    private void beginRender(String source, Sink sink, String value) {
        this.source = source;
        this.sink = sink;
        this.input = value;
        this.output = value;
        this.encodedNow = false;
        this.sanitizedNow = false;
        this.html = null;
        this.stage = "idle";
        this.injected = false;
        this.executed = false;
        this.blockedBy = null;
        this.cookieStolen = false;
        this.nodes.clear();
    }

    private void buildNodes(Sink sink, String value) {
        nodes.clear();
        nodes.add(node("markup", sink.before, false));
        nodes.add(node(injected ? "script" : "text", value, injected));
        nodes.add(node("markup", sink.after, false));
    }

    private static Map<String, Object> node(String kind, String text, boolean danger) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("kind", kind);
        n.put("text", text);
        n.put("danger", danger);
        return n;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        Map<String, Object> defences = new LinkedHashMap<>();
        defences.put("encoding", encoding);
        defences.put("sanitizer", sanitizer);
        defences.put("csp", csp);
        defences.put("httpOnly", httpOnly);
        s.put("defences", defences);

        Map<String, Object> cookie = new LinkedHashMap<>();
        cookie.put("name", COOKIE_NAME);
        cookie.put("value", COOKIE_VALUE);
        cookie.put("httpOnly", httpOnly);
        cookie.put("stolen", cookieStolen);
        s.put("cookie", cookie);

        List<Object> storedList = new ArrayList<>();
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field", entry.getKey());
            row.put("value", entry.getValue());
            row.put("dangerous", looksExecutable(entry.getValue()));
            storedList.add(row);
        }
        s.put("stored", storedList);

        if (sink != null) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("source", source);
            render.put("sink", sink.name());
            render.put("input", input);
            render.put("output", output);
            render.put("encoded", encodedNow);
            render.put("sanitized", sanitizedNow);
            render.put("html", html);
            s.put("render", render);

            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("stage", stage);
            outcome.put("injected", injected);
            outcome.put("executed", executed);
            outcome.put("blockedBy", blockedBy);
            s.put("outcome", outcome);
        } else {
            s.put("render", null);
            s.put("outcome", null);
        }

        s.put("nodes", new ArrayList<>(nodes));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("renders", renders);
        stats.put("injections", injections);
        stats.put("executions", executions);
        stats.put("cookiesStolen", cookiesStolen);
        stats.put("blockedByCsp", blockedByCsp);
        stats.put("neutralized", neutralized);
        s.put("stats", stats);
        return s;
    }
}
