package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A <em>teaching model</em> of Spring Boot's {@code Environment} and the ladder
 * of {@code PropertySource}s behind it — the machinery that decides whether
 * {@code application.properties} or an environment variable wins.
 *
 * <p>The whole subject rests on two facts:
 * <ul>
 *   <li>an {@code Environment} is an <b>ordered list</b> of property sources, and
 *       the order is fixed by the framework, not by the order in which you
 *       happen to set things;</li>
 *   <li>{@code getProperty(key)} walks that list from the top and returns the
 *       <b>first</b> value it finds — so "higher priority" literally means
 *       "sits closer to the top of the list".</li>
 * </ul>
 *
 * <p>The ranks modelled here, highest first, are the ones that matter when you
 * deploy something: inlined test properties, command line arguments,
 * {@code SPRING_APPLICATION_JSON}, {@code -D} system properties, OS environment
 * variables, then the four config-data files (profile-specific and plain, each
 * outside then inside the jar), then {@code @PropertySource}, then
 * {@code SpringApplication.setDefaultProperties}. Real Spring Boot has a few
 * more rungs (Devtools settings, servlet init parameters, JNDI, the random value
 * source); they are left out because they never decide this argument.
 *
 * <p>The environment-variable source is special in one way that costs people
 * hours: it stores {@code SERVER_PORT}, not {@code server.port}. A lookup for
 * {@code server.port} still finds it because Spring converts the requested key
 * into the environment-variable spelling (uppercase, dots to underscores,
 * dashes dropped) before checking. That conversion is <em>relaxed binding</em>,
 * it lives in Spring, and it is exactly why {@code System.getenv("server.port")}
 * returns {@code null} while {@code environment.getProperty("server.port")} does not.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualPropertySources {

    /** One rung of the ladder. Declaration order IS the precedence order. */
    public enum Kind {
        /** {@code @SpringBootTest(properties = …)} / {@code @TestPropertySource}. */
        TEST_PROPERTIES,
        /** {@code java -jar app.jar --server.port=9000}. */
        COMMAND_LINE,
        /** The {@code SPRING_APPLICATION_JSON} variable, flattened to dotted keys. */
        APPLICATION_JSON,
        /** {@code java -Dserver.port=9000 -jar app.jar}. */
        SYSTEM_PROPERTIES,
        /** OS environment variables: {@code SERVER_PORT=9000}. */
        SYSTEM_ENVIRONMENT,
        /** {@code ./config/application-{profile}.properties}, next to the jar. */
        PROFILE_EXTERNAL,
        /** {@code application-{profile}.properties}, packaged in the jar. */
        PROFILE_PACKAGED,
        /** {@code ./config/application.properties}, next to the jar. */
        EXTERNAL,
        /** {@code application.properties}, packaged in the jar. */
        PACKAGED,
        /** A file pulled in by {@code @PropertySource} on a {@code @Configuration} class. */
        ANNOTATION,
        /** {@code SpringApplication.setDefaultProperties(…)} — the floor. */
        DEFAULTS
    }

    /** One property as it is actually spelled inside one source. */
    private static final class Entry {
        private final String name;
        private final String value;
        /** How you would write this entry in that source, e.g. {@code -Dserver.port=9000}. */
        private final String display;
        /** Recomputed on every lookup: winner / shadowed / nothing. */
        private String role = "";

        private Entry(String name, String value, String display) {
            this.name = name;
            this.value = value;
            this.display = display;
        }
    }

    /** A hit inside one source, plus how the key had to be spelled to get it. */
    private static final class Match {
        private final Entry entry;
        private final boolean relaxed;

        private Match(Entry entry, boolean relaxed) {
            this.entry = entry;
            this.relaxed = relaxed;
        }
    }

    /** One property source: a named bag of entries at a fixed rank. */
    private static final class Source {
        private final Kind kind;
        private final String id;
        private final boolean envStyle;
        private String name;
        private String profile = "";
        private final List<Entry> entries = new ArrayList<>();
        private String status = "idle";

        private Source(Kind kind, String id, String name, boolean envStyle) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.envStyle = envStyle;
        }

        /** A profile-specific file exists but is ignored unless its profile is active. */
        private boolean active(List<String> activeProfiles) {
            return profile.isEmpty() || activeProfiles.contains(profile);
        }

        private void put(String name, String value, String display) {
            entries.removeIf(e -> e.name.equals(name));
            entries.add(new Entry(name, value, display));
        }

        /**
         * Looks one key up in this source. Ordinary sources compare the key
         * verbatim; the environment-variable source also tries the shouty
         * spelling, which is what relaxed binding does.
         */
        private Match match(String key) {
            for (Entry entry : entries) {
                if (entry.name.equals(key)) {
                    return new Match(entry, false);
                }
            }
            if (!envStyle) {
                return null;
            }
            String shouty = envName(key);
            for (Entry entry : entries) {
                if (entry.name.equals(shouty)) {
                    return new Match(entry, true);
                }
            }
            return null;
        }
    }

    /**
     * The spelling Spring looks for in the OS environment: uppercase, dots become
     * underscores, dashes are dropped. {@code spring.jpa.hibernate.ddl-auto}
     * becomes {@code SPRING_JPA_HIBERNATE_DDLAUTO}.
     */
    private static String envName(String key) {
        return key.toUpperCase(Locale.ROOT).replace("-", "").replace('.', '_');
    }

    private final List<Source> sources = new ArrayList<>();
    private final List<String> activeProfiles = new ArrayList<>();

    private String lookupKey = "";
    private boolean lookupDone;
    private boolean found;
    private String foundValue = "";
    private String winnerName = "";
    private int winnerRank;
    private String matchedName = "";
    private boolean relaxed;
    private final List<String> shadowed = new ArrayList<>();
    private String getenvName = "";
    private String getenvValue;
    private boolean getenvDone;

    private int lookups;
    private int overrides;
    private int missing;

    private VisualPropertySources() {
        add(Kind.TEST_PROPERTIES, "test-properties", "Inlined Test Properties", false);
        add(Kind.COMMAND_LINE, "command-line", "commandLineArgs", false);
        add(Kind.APPLICATION_JSON, "application-json", "spring.application.json", false);
        add(Kind.SYSTEM_PROPERTIES, "system-properties", "systemProperties", false);
        add(Kind.SYSTEM_ENVIRONMENT, "system-environment", "systemEnvironment", true);
        add(Kind.PROFILE_EXTERNAL, "profile-external", "file:./config/application-{profile}.properties", false);
        add(Kind.PROFILE_PACKAGED, "profile-packaged", "classpath:/application-{profile}.properties", false);
        add(Kind.EXTERNAL, "external", "file:./config/application.properties", false);
        add(Kind.PACKAGED, "packaged", "classpath:/application.properties", false);
        add(Kind.ANNOTATION, "annotation", "@PropertySource", false);
        add(Kind.DEFAULTS, "defaults", "defaultProperties", false);
    }

    private void add(Kind kind, String id, String name, boolean envStyle) {
        sources.add(new Source(kind, id, name, envStyle));
    }

    /** An Environment with the real ladder in place and nothing configured yet. */
    public static VisualPropertySources springBoot() {
        VisualPropertySources env = new VisualPropertySources();
        Trace.event("ENVIRONMENT_READY",
                "A Spring Boot Environment is an ORDERED list of " + env.sources.size()
                        + " property sources, highest priority at the top. The order is fixed by the "
                        + "framework — it does not depend on what you set last. getProperty(key) walks "
                        + "the list from the top and returns the FIRST value it finds, and that single "
                        + "rule is the entire answer to 'which one wins'",
                "Environment в Spring Boot — это УПОРЯДОЧЕННЫЙ список из " + env.sources.size()
                        + " источников свойств, наверху — самый приоритетный. Порядок задан фреймворком "
                        + "и не зависит от того, что вы задали последним. getProperty(key) идёт по "
                        + "списку сверху вниз и возвращает ПЕРВОЕ найденное значение — в этом одном "
                        + "правиле и есть весь ответ на вопрос «что победит»",
                List.of(), env.state());
        return env;
    }

    // ------------------------------------------------------------- populating

    /** {@code @SpringBootTest(properties = "server.port=0")} — the top of the ladder. */
    public VisualPropertySources testProperties(String... pairs) {
        return fill(Kind.TEST_PROPERTIES, "", pairs,
                "inlined into the test class, so a test ignores whatever the machine's environment says",
                "вписаны прямо в тестовый класс, поэтому тест игнорирует то, что задано в окружении машины");
    }

    /** {@code java -jar app.jar --server.port=9000} — the highest run-time override. */
    public VisualPropertySources commandLine(String... args) {
        return fill(Kind.COMMAND_LINE, "--", args,
                "arguments passed after the jar name; the last thing you can change without rebuilding "
                        + "or touching the environment",
                "аргументы после имени jar; последнее, что можно изменить, не пересобирая приложение "
                        + "и не трогая окружение");
    }

    /**
     * {@code SPRING_APPLICATION_JSON={"server":{"port":7000}}} — given here already
     * flattened, which is exactly what Spring does with the JSON before storing it.
     */
    public VisualPropertySources springApplicationJson(String... pairs) {
        return fill(Kind.APPLICATION_JSON, "", pairs,
                "one variable holding a JSON document; Spring flattens it to dotted keys, so nested "
                        + "JSON becomes ordinary properties that outrank both -D and plain variables",
                "одна переменная с документом JSON; Spring разворачивает её в ключи с точками, поэтому "
                        + "вложенный JSON превращается в обычные свойства, которые старше и -D, и "
                        + "обычных переменных");
    }

    /** {@code java -Dserver.port=9000 -jar app.jar} — JVM system properties. */
    public VisualPropertySources systemProperties(String... pairs) {
        return fill(Kind.SYSTEM_PROPERTIES, "-D", pairs,
                "JVM flags before the jar name; they keep the dotted spelling, unlike environment "
                        + "variables",
                "флаги JVM перед именем jar; в отличие от переменных окружения, они сохраняют "
                        + "написание с точками");
    }

    /** {@code SERVER_PORT=9000} — OS environment variables, as the shell spells them. */
    public VisualPropertySources environmentVariables(String... pairs) {
        return fill(Kind.SYSTEM_ENVIRONMENT, "", pairs,
                "set by the shell, docker run -e or a Kubernetes env block; note that the source stores "
                        + "the SHOUTY name, not the dotted one",
                "задаются оболочкой, docker run -e или блоком env в Kubernetes; обратите внимание: "
                        + "источник хранит ИМЯ_КАПСОМ, а не имя с точками");
    }

    /** {@code ./config/application-{profile}.properties}, sitting next to the jar. */
    public VisualPropertySources externalProfileProperties(String profile, String... pairs) {
        source(Kind.PROFILE_EXTERNAL).profile = profile;
        source(Kind.PROFILE_EXTERNAL).name = "file:./config/application-" + profile + ".properties";
        return fill(Kind.PROFILE_EXTERNAL, "", pairs,
                "a profile-specific file placed next to the jar — it beats everything that comes from "
                        + "inside the jar, and it is read only while its profile is active",
                "файл конкретного профиля рядом с jar — он старше всего, что лежит внутри jar, и "
                        + "читается, только пока его профиль активен");
    }

    /** {@code application-{profile}.properties}, packaged inside the jar. */
    public VisualPropertySources profileProperties(String profile, String... pairs) {
        source(Kind.PROFILE_PACKAGED).profile = profile;
        source(Kind.PROFILE_PACKAGED).name = "classpath:/application-" + profile + ".properties";
        return fill(Kind.PROFILE_PACKAGED, "", pairs,
                "a profile-specific file inside the jar; it outranks the plain application.properties "
                        + "next to it, but only while its profile is active",
                "файл конкретного профиля внутри jar; он старше обычного application.properties рядом "
                        + "с ним, но только пока его профиль активен");
    }

    /** {@code ./config/application.properties}, sitting next to the jar. */
    public VisualPropertySources externalProperties(String... pairs) {
        return fill(Kind.EXTERNAL, "", pairs,
                "the same file name, but on disk next to the jar instead of inside it — Spring Boot "
                        + "searches ./config/ and ./ as well as the classpath, and the outside copy wins",
                "тот же файл, но на диске рядом с jar, а не внутри него — Spring Boot ищет и в "
                        + "./config/, и в ./, и в classpath, и внешняя копия побеждает");
    }

    /** {@code application.properties}, packaged inside the jar: the baseline. */
    public VisualPropertySources packagedProperties(String... pairs) {
        return fill(Kind.PACKAGED, "", pairs,
                "the file that ships inside the jar: your defaults, the values that must be true for "
                        + "the application to start at all",
                "файл, который едет внутри jar: ваши значения по умолчанию, то, без чего приложение "
                        + "вообще не поднимется");
    }

    /** A file pulled in by {@code @PropertySource} on a {@code @Configuration} class. */
    public VisualPropertySources propertySourceAnnotation(String resource, String... pairs) {
        source(Kind.ANNOTATION).name = "@PropertySource(\"" + resource + "\")";
        return fill(Kind.ANNOTATION, "", pairs,
                "an extra file requested by an annotation; despite looking deliberate it lands BELOW "
                        + "application.properties, which surprises almost everybody",
                "дополнительный файл, затребованный аннотацией; хотя выглядит он как осознанное "
                        + "решение, попадает он НИЖЕ application.properties, и это удивляет почти всех");
    }

    /** {@code SpringApplication.setDefaultProperties(…)} — the floor of the ladder. */
    public VisualPropertySources defaultProperties(String... pairs) {
        return fill(Kind.DEFAULTS, "", pairs,
                "values registered in code as a last resort; anything at all overrides them, which is "
                        + "the point of them",
                "значения, зарегистрированные в коде на самый крайний случай; их перебивает что "
                        + "угодно, в этом и есть их смысл");
    }

    private VisualPropertySources fill(Kind kind, String prefix, String[] pairs,
                                       String noteEn, String noteRu) {
        Source target = source(kind);
        List<String> added = new ArrayList<>();
        for (String pair : pairs) {
            String text = pair.startsWith("--") ? pair.substring(2) : pair;
            int eq = text.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Expected 'name=value', got '" + pair + "'");
            }
            String name = text.substring(0, eq).trim();
            String value = text.substring(eq + 1).trim();
            String display = prefix + name + "=" + value;
            target.put(name, value, display);
            added.add(display);
        }
        Trace.event("SOURCE_ADDED",
                "Rank " + rankOf(kind) + " — " + target.name + " now holds " + String.join(", ", added)
                        + ". These are " + noteEn,
                "Ранг " + rankOf(kind) + " — в " + target.name + " теперь лежит "
                        + String.join(", ", added) + ". Это " + noteRu,
                List.of("source:" + target.id), state());
        return this;
    }

    /** Turns profiles on, the way {@code SPRING_PROFILES_ACTIVE=prod} would. */
    public VisualPropertySources activateProfiles(String... profiles) {
        for (String profile : profiles) {
            if (!activeProfiles.contains(profile)) {
                activeProfiles.add(profile);
            }
        }
        List<String> nowActive = new ArrayList<>();
        List<String> stillIgnored = new ArrayList<>();
        for (Source s : sources) {
            if (s.profile.isEmpty()) {
                continue;
            }
            (s.active(activeProfiles) ? nowActive : stillIgnored).add(s.name);
        }
        Trace.event("PROFILE_ACTIVATED",
                "Active profiles: " + String.join(", ", activeProfiles)
                        + ". A profile-specific file is not a lower-priority file — it is a file that "
                        + "does not exist at all until its profile is on"
                        + (nowActive.isEmpty() ? "" : ". Now readable: " + String.join(", ", nowActive))
                        + (stillIgnored.isEmpty() ? "" : ". Still ignored: " + String.join(", ", stillIgnored)),
                "Активные профили: " + String.join(", ", activeProfiles)
                        + ". Файл конкретного профиля — это не файл с меньшим приоритетом, а файл, "
                        + "которого до включения профиля вообще не существует"
                        + (nowActive.isEmpty() ? "" : ". Теперь читается: " + String.join(", ", nowActive))
                        + (stillIgnored.isEmpty() ? "" : ". Всё ещё игнорируется: "
                            + String.join(", ", stillIgnored)),
                List.of("profiles"), state());
        return this;
    }

    // ---------------------------------------------------------------- lookups

    /**
     * {@code environment.getProperty(key)} — walks the ladder top to bottom and
     * returns the first value it finds, or {@code null}.
     */
    public String getProperty(String key) {
        beginLookup(key);
        lookups++;
        Trace.event("LOOKUP_STARTED",
                "getProperty(\"" + key + "\") — the Environment starts at rank 1 and works downwards. "
                        + "This is the same path @Value(\"${" + key + "}\") and @ConfigurationProperties "
                        + "take: they are different ways of asking, not different ways of resolving",
                "getProperty(\"" + key + "\") — Environment начинает с ранга 1 и идёт вниз. Тем же "
                        + "путём идут @Value(\"${" + key + "}\") и @ConfigurationProperties: это разные "
                        + "способы спросить, а не разные способы разрешить значение",
                List.of("lookup"), state());

        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            if (s.entries.isEmpty()) {
                continue;
            }
            if (!s.active(activeProfiles)) {
                Trace.event("SOURCE_MISS",
                        "Rank " + (i + 1) + " " + s.name + " is skipped: profile '" + s.profile
                                + "' is not active, so the file is not part of the Environment at all",
                        "Ранг " + (i + 1) + " " + s.name + " пропущен: профиль «" + s.profile
                                + "» не активен, поэтому файла в Environment просто нет",
                        List.of("source:" + s.id), state());
                continue;
            }
            Match match = s.match(key);
            if (match == null) {
                s.status = "miss";
                Trace.event("SOURCE_MISS",
                        "Rank " + (i + 1) + " " + s.name + " has properties, but not '" + key
                                + "'. A source that does not define a key does not block anything — the "
                                + "search simply continues one rung down",
                        "Ранг " + (i + 1) + " " + s.name + " содержит свойства, но не «" + key
                                + "». Источник, который не задаёт ключ, ничего не блокирует — поиск "
                                + "просто идёт на ступеньку ниже",
                        List.of("source:" + s.id), state());
                continue;
            }
            resolveWith(i, s, match, key);
            return match.entry.value;
        }

        missing++;
        lookupDone = true;
        Trace.event("PROPERTY_MISSING",
                "No source defines '" + key + "', so getProperty returns null. In a real application "
                        + "this is where @Value(\"${" + key + "}\") fails the context with "
                        + "IllegalArgumentException at startup unless you gave it a ':default' — a "
                        + "misspelled environment variable name lands here and looks like 'my override "
                        + "was ignored'",
                "Ключ «" + key + "» не задан ни одним источником, поэтому getProperty вернёт null. В "
                        + "реальном приложении именно здесь @Value(\"${" + key + "}\") роняет контекст "
                        + "с IllegalArgumentException при старте, если не указано «:значение по "
                        + "умолчанию»; опечатка в имени переменной окружения приводит сюда и выглядит "
                        + "как «моё переопределение проигнорировали»",
                List.of("lookup"), state());
        return null;
    }

    private void resolveWith(int index, Source winner, Match match, String key) {
        winner.status = "hit";
        match.entry.role = "winner";
        found = true;
        lookupDone = true;
        foundValue = match.entry.value;
        winnerName = winner.name;
        winnerRank = index + 1;
        matchedName = match.entry.name;
        relaxed = match.relaxed;

        if (match.relaxed) {
            Trace.event("RELAXED_BINDING",
                    "Rank " + winnerRank + " " + winner.name + " holds '" + match.entry.name
                            + "', not '" + key + "' — a shell cannot put dots in a variable name. Spring "
                            + "converts the requested key to the environment spelling (uppercase, dots "
                            + "to underscores, dashes dropped) and matches on that. This is relaxed "
                            + "binding, and it is why your code never has to know how the variable was "
                            + "spelled",
                    "Ранг " + winnerRank + " " + winner.name + " содержит «" + match.entry.name
                            + "», а не «" + key + "»: оболочка не даст поставить точки в имени "
                            + "переменной. Spring переводит запрошенный ключ в написание для окружения "
                            + "(верхний регистр, точки в подчёркивания, дефисы убираются) и ищет по "
                            + "нему. Это relaxed binding, и благодаря ему коду не нужно знать, как была "
                            + "записана переменная",
                    List.of("source:" + winner.id, "lookup"), state());
        }

        // Everything below the winner still HAS the key — it just never gets asked.
        for (int i = index + 1; i < sources.size(); i++) {
            Source below = sources.get(i);
            if (below.entries.isEmpty() || !below.active(activeProfiles)) {
                continue;
            }
            Match hidden = below.match(key);
            if (hidden == null) {
                below.status = "not-reached";
                continue;
            }
            below.status = "shadowed";
            hidden.entry.role = "shadowed";
            shadowed.add(below.name + " (" + hidden.entry.value + ")");
        }

        Trace.event("PROPERTY_RESOLVED",
                "'" + key + "' = " + foundValue + ", taken from rank " + winnerRank + " " + winnerName
                        + ". The walk stopped at the first source that had the key; nothing below it was "
                        + "even consulted",
                "«" + key + "» = " + foundValue + ", взято из ранга " + winnerRank + " " + winnerName
                        + ". Обход остановился на первом источнике, где ключ нашёлся; всё, что ниже, "
                        + "даже не спрашивали",
                List.of("source:" + winner.id, "lookup"), state());

        if (!shadowed.isEmpty()) {
            overrides++;
            Trace.event("PROPERTY_OVERRIDDEN",
                    "The same key is also defined lower down — " + String.join(", ", shadowed)
                            + " — and those values are shadowed, not merged and not erased. This is the "
                            + "answer to the interview question: the environment sits ABOVE both copies "
                            + "of application.properties, so a variable wins over the file every time, "
                            + "and it does so without editing, rebuilding or redeploying anything",
                    "Тот же ключ задан и ниже — " + String.join(", ", shadowed)
                            + " — и эти значения затенены, а не слиты и не стёрты. Это и есть ответ на "
                            + "вопрос собеседования: окружение стоит ВЫШЕ обеих копий "
                            + "application.properties, поэтому переменная побеждает файл всегда — и "
                            + "делает это, ничего не редактируя, не пересобирая и не передеплоивая",
                    List.of("source:" + winner.id, "lookup"), state());
        }
    }

    /**
     * {@code System.getenv(name)} — the raw OS lookup, with no Spring in the way.
     * It is deliberately dumb: exact name or nothing.
     */
    public String getenv(String name) {
        Source env = source(Kind.SYSTEM_ENVIRONMENT);
        String value = null;
        for (Entry entry : env.entries) {
            if (entry.name.equals(name)) {
                value = entry.value;
            }
        }
        getenvName = name;
        getenvValue = value;
        getenvDone = true;
        Trace.event("SYSTEM_GETENV",
                "System.getenv(\"" + name + "\") = " + (value == null ? "null" : value)
                        + ". This asks the operating system directly, so it matches the exact name and "
                        + "nothing else — no relaxed binding, no files, no defaults. Relaxed binding "
                        + "belongs to Spring's Environment, not to the JVM, which is why "
                        + "getProperty(\"server.port\") can succeed on the very same process where "
                        + "getenv(\"server.port\") returns null",
                "System.getenv(\"" + name + "\") = " + (value == null ? "null" : value)
                        + ". Этот вызов спрашивает операционную систему напрямую, поэтому совпадать "
                        + "должно точное имя и ничего больше — ни relaxed binding, ни файлов, ни "
                        + "значений по умолчанию. Relaxed binding принадлежит Environment из Spring, а "
                        + "не JVM, поэтому в одном и том же процессе getProperty(\"server.port\") может "
                        + "сработать, а getenv(\"server.port\") — вернуть null",
                List.of("getenv"), state());
        return value;
    }

    /** Prints the whole ladder as it currently stands. */
    public void precedence() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            String note = s.entries.isEmpty() ? "empty"
                    : !s.active(activeProfiles) ? "inactive profile"
                    : s.entries.size() + " propert" + (s.entries.size() == 1 ? "y" : "ies");
            lines.add((i + 1) + ") " + s.name + " [" + note + "]");
        }
        Trace.event("PRECEDENCE_REPORT",
                "The ladder, highest priority first: " + String.join("; ", lines)
                        + ". Read it once and every 'why is my value ignored' question becomes a "
                        + "question of which rung the other value sits on. After " + lookups
                        + " lookup(s): " + overrides + " resolved above a lower definition, " + missing
                        + " found nothing at all",
                "Лестница, от самого приоритетного вниз: " + String.join("; ", lines)
                        + ". Достаточно один раз её прочитать — и любой вопрос «почему моё значение "
                        + "игнорируется» превращается в вопрос о том, на какой ступеньке лежит другое "
                        + "значение. После обращений (" + lookups + "): " + overrides
                        + " разрешено поверх более низкого определения, " + missing
                        + " не нашлось вовсе",
                List.of(), state());
    }

    // ------------------------------------------------------------- internals

    private void beginLookup(String key) {
        lookupKey = key;
        lookupDone = false;
        found = false;
        foundValue = "";
        winnerName = "";
        winnerRank = 0;
        matchedName = "";
        relaxed = false;
        shadowed.clear();
        for (Source s : sources) {
            for (Entry entry : s.entries) {
                entry.role = "";
            }
            s.status = s.entries.isEmpty() ? "empty"
                    : !s.active(activeProfiles) ? "inactive"
                    : "pending";
        }
    }

    private Source source(Kind kind) {
        for (Source s : sources) {
            if (s.kind == kind) {
                return s;
            }
        }
        throw new IllegalStateException("No source of kind " + kind);
    }

    private int rankOf(Kind kind) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).kind == kind) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("activeProfiles", new ArrayList<>(activeProfiles));

        List<Object> sourceList = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            Source src = sources.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("id", src.id);
            item.put("name", src.name);
            item.put("profile", src.profile);
            item.put("active", src.active(activeProfiles));
            item.put("status", src.status);
            List<Object> props = new ArrayList<>();
            for (int j = 0; j < src.entries.size(); j++) {
                Entry entry = src.entries.get(j);
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", src.id + "-" + j);
                p.put("display", entry.display);
                p.put("role", entry.role);
                props.add(p);
            }
            item.put("properties", props);
            sourceList.add(item);
        }
        s.put("sources", sourceList);

        if (lookupKey.isEmpty()) {
            s.put("lookup", null);
        } else {
            Map<String, Object> lookup = new LinkedHashMap<>();
            lookup.put("key", lookupKey);
            lookup.put("done", lookupDone);
            lookup.put("found", found);
            lookup.put("value", found ? foundValue : null);
            lookup.put("winner", winnerName);
            lookup.put("winnerRank", winnerRank);
            lookup.put("matchedName", matchedName);
            lookup.put("relaxed", relaxed);
            lookup.put("shadowed", new ArrayList<>(shadowed));
            s.put("lookup", lookup);
        }

        if (!getenvDone) {
            s.put("getenv", null);
        } else {
            Map<String, Object> getenv = new LinkedHashMap<>();
            getenv.put("name", getenvName);
            getenv.put("value", getenvValue);
            s.put("getenv", getenv);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("lookups", lookups);
        stats.put("overrides", overrides);
        stats.put("missing", missing);
        s.put("stats", stats);
        return s;
    }
}
