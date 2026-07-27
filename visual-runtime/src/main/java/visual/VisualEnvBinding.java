package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A <em>teaching model</em> of the rule that answers "what will the environment
 * variable look like?" — how a Spring Boot property name such as
 * {@code job.timeout} becomes the variable name {@code JOB_TIMEOUT} that
 * overrides it.
 *
 * <p>The rule itself is three lines long and never changes:
 * <ol>
 *   <li>replace every {@code .} with {@code _} (a list index {@code [0]} becomes
 *       {@code _0_});</li>
 *   <li><b>delete</b> every {@code -} — it does not become an underscore;</li>
 *   <li>uppercase the result.</li>
 * </ol>
 * So {@code job.timeout} is {@code JOB_TIMEOUT},
 * {@code job.read-timeout} is {@code JOB_READTIMEOUT}, and
 * {@code job.targets[0].url} is {@code JOB_TARGETS_0_URL}.
 *
 * <p>The conversion runs in one direction only: the code always asks for the
 * dotted name, and Spring converts <em>the key it was asked for</em> before
 * looking in the environment. The operating system never converts anything — it
 * stores whatever name you exported, verbatim, which is why
 * {@code System.getenv("job.timeout")} returns {@code null} in the very process
 * where the property resolves.
 *
 * <p>The model also knows the spellings that happen to work but should not be
 * written (a literal dotted name, a lowercase name, a dash spelled as an
 * underscore) and the near misses that silently do nothing, because a wrong
 * variable name is indistinguishable from no variable at all.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualEnvBinding {

    /** One stage of the name conversion, in the order the rule applies them. */
    private static final class Step {
        private final String id;
        private final String value;
        private final boolean changed;

        private Step(String id, String value, boolean changed) {
            this.id = id;
            this.value = value;
            this.changed = changed;
        }
    }

    /** One variable as the operating system stores it: a name and a string. */
    private static final class Variable {
        private final String id;
        private final String name;
        private final String value;
        /** Recomputed on every resolve: match / near-miss / nothing. */
        private String role = "";

        private Variable(String id, String name, String value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }
    }

    /** One configuration property, with the value the application ends up using. */
    private static final class Property {
        private final String id;
        private final String key;
        private final String envName;
        private String fileValue;
        private String value;
        private String source = "none";

        private Property(String id, String key, String envName) {
            this.id = id;
            this.key = key;
            this.envName = envName;
        }
    }

    /** The same variable, written the way one deployment tool wants it. */
    private static final class Form {
        private final String id;
        private final String platform;
        private final String snippet;

        private Form(String id, String platform, String snippet) {
            this.id = id;
            this.platform = platform;
            this.snippet = snippet;
        }
    }

    private final List<Variable> variables = new ArrayList<>();
    private final List<Property> properties = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final List<Form> forms = new ArrayList<>();
    private final List<String> nearMisses = new ArrayList<>();

    private String conversionKey = "";
    private String conversionEnvName = "";
    private boolean conversionDone;
    private boolean conversionIndexed;

    private String bindKey = "";
    private String bindEnvName = "";
    private boolean bindStarted;
    private boolean bindMatched;
    private String bindReason = "none";
    private String bindVariable = "";
    private String bindValue;
    private String bindFileValue;

    private String getenvName = "";
    private String getenvValue;
    private boolean getenvDone;

    private int derived;
    private int overridden;
    private int missed;
    private int counter;

    private VisualEnvBinding() {
    }

    /** An application with an empty environment and nothing configured yet. */
    public static VisualEnvBinding application() {
        VisualEnvBinding app = new VisualEnvBinding();
        Trace.event("BINDING_READY",
                "A property name and an environment variable name are two spellings of the same "
                        + "thing. The rule that turns one into the other has three parts and never "
                        + "changes: replace every dot with an underscore, DELETE every dash, uppercase "
                        + "the result. Everything else here follows from those three lines",
                "Имя свойства и имя переменной окружения — это два написания одного и того же. "
                        + "Правило перевода одного в другое состоит из трёх пунктов и не меняется "
                        + "никогда: каждую точку заменить подчёркиванием, каждый дефис УДАЛИТЬ, "
                        + "результат перевести в верхний регистр. Всё остальное здесь следует из этих "
                        + "трёх строк",
                List.of(), app.state());
        return app;
    }

    // ---------------------------------------------------------------- setting up

    /** A key with a value in {@code application.properties} — the baseline. */
    public VisualEnvBinding fileProperty(String key, String value) {
        Property p = property(key);
        p.fileValue = value;
        p.value = value;
        p.source = "file";
        Trace.event("PROPERTY_DECLARED",
                "application.properties says " + key + "=" + value + ". This is the spelling the code "
                        + "uses too — @Value(\"${" + key + "}\") and @ConfigurationProperties both ask "
                        + "for the dotted name, and neither of them ever sees a variable name. Nothing "
                        + "in the application has to change for a variable to override this line",
                "В application.properties написано " + key + "=" + value + ". Этим же написанием "
                        + "пользуется код: и @Value(\"${" + key + "}\"), и @ConfigurationProperties "
                        + "запрашивают имя с точками и никогда не видят имени переменной. Чтобы "
                        + "переменная перебила эту строку, в приложении менять ничего не нужно",
                List.of("property:" + p.id), state());
        return this;
    }

    /** {@code export NAME=value} — the operating system stores the name verbatim. */
    public VisualEnvBinding export(String name, String value) {
        variables.removeIf(v -> v.name.equals(name));
        Variable v = new Variable("env-" + (counter++), name, value);
        variables.add(v);
        Trace.event("VARIABLE_EXPORTED",
                "The environment now holds " + name + "=" + value + ". The operating system stores "
                        + "this name character for character: it knows nothing about Spring, about "
                        + "properties or about dots, and it will never tell you that the name is wrong. "
                        + "All the intelligence sits on the other side, in the conversion Spring runs on "
                        + "the key it is asked for",
                "В окружении теперь есть " + name + "=" + value + ". Операционная система хранит это "
                        + "имя символ в символ: она ничего не знает ни о Spring, ни о свойствах, ни о "
                        + "точках и никогда не скажет, что имя неверное. Вся сообразительность — на "
                        + "другой стороне, в преобразовании, которое Spring делает с запрошенным ключом",
                List.of("variable:" + v.id), state());
        return this;
    }

    // ------------------------------------------------------------ the name rule

    /**
     * Walks the three rules aloud and returns the environment variable name that
     * overrides {@code key} — the direct answer to the interview question.
     */
    public String envNameFor(String key) {
        beginConversion(key);

        step("start", key, false,
                "Start from the property name exactly as application.properties and the code spell "
                        + "it: " + key + ". Lowercase, dot-separated, dashes allowed inside a word — "
                        + "this is the canonical form, and the conversion only ever runs in this "
                        + "direction",
                "Начинаем с имени свойства ровно в том виде, в каком его пишут application.properties "
                        + "и код: " + key + ". Нижний регистр, разделение точками, дефисы внутри слова "
                        + "разрешены — это каноническая форма, и преобразование всегда идёт только в "
                        + "эту сторону");

        String separators = replaceSeparators(key);
        step("separators", separators, !separators.equals(key),
                "Rule 1 — every dot becomes an underscore: " + separators
                        + (conversionIndexed ? ". A list index is spelled the same way: [0] becomes _0_" : "")
                        + ". The dot cannot survive, because a shell variable name may contain only "
                        + "letters, digits and underscores: 'export " + key + "=30s' is not a variable "
                        + "with a strange name, it is a syntax error",
                "Правило 1 — каждая точка становится подчёркиванием: " + separators
                        + (conversionIndexed ? ". Индекс списка записывается так же: [0] превращается в _0_" : "")
                        + ". Точка не может уцелеть: имя переменной оболочки состоит только из букв, "
                        + "цифр и подчёркиваний, поэтому 'export " + key + "=30s' — это не переменная "
                        + "со странным именем, а синтаксическая ошибка");

        String noDashes = separators.replace("-", "");
        step("dashes", noDashes, !noDashes.equals(separators),
                "Rule 2 — dashes are DELETED, not turned into underscores: " + noDashes
                        + (noDashes.equals(separators)
                            ? ". There is no dash in this name, but this is the step that costs people "
                                + "an afternoon: read-timeout becomes READTIMEOUT, never READ_TIMEOUT"
                            : ". read-timeout becomes READTIMEOUT — an underscore here would read as "
                                + "another dot, i.e. a different property"),
                "Правило 2 — дефисы УДАЛЯЮТСЯ, а не превращаются в подчёркивания: " + noDashes
                        + (noDashes.equals(separators)
                            ? ". В этом имени дефиса нет, но именно на этом шаге теряют по полдня: "
                                + "read-timeout становится READTIMEOUT, а не READ_TIMEOUT"
                            : ". read-timeout становится READTIMEOUT — подчёркивание здесь читалось бы "
                                + "как ещё одна точка, то есть как другое свойство"));

        String upper = noDashes.toUpperCase(Locale.ROOT);
        step("upper", upper, !upper.equals(noDashes),
                "Rule 3 — uppercase: " + upper + ". Nothing in Linux requires capitals; it is a "
                        + "convention as old as sh, kept so that a variable never collides with a "
                        + "lowercase shell variable of your own",
                "Правило 3 — верхний регистр: " + upper + ". Linux капса не требует; это соглашение "
                        + "возрастом со сам sh, и держатся его для того, чтобы переменная никогда не "
                        + "столкнулась с вашей собственной переменной оболочки в нижнем регистре");

        conversionEnvName = upper;
        conversionDone = true;
        derived++;

        Trace.event("ENV_NAME_DERIVED",
                "To override " + key + " you set " + upper + ". That is the whole answer: dots to "
                        + "underscores, dashes deleted, uppercase. Note what is NOT converted — the "
                        + "value: it is handed over as the plain string you wrote, and Spring converts "
                        + "it to a Duration, an int or an enum afterwards",
                "Чтобы переопределить " + key + ", задают " + upper + ". Это и есть весь ответ: точки "
                        + "в подчёркивания, дефисы удалить, верхний регистр. Обратите внимание, что НЕ "
                        + "преобразуется значение: оно передаётся простой строкой, как написано, а уже "
                        + "потом Spring превращает её в Duration, int или enum",
                List.of("conversion"), state());

        if (conversionIndexed) {
            Trace.event("LIST_BINDING",
                    "A list element is addressed by its index with an underscore on each side, so "
                            + key + " is " + upper + ". The part that bites: lists are REPLACED, never "
                            + "merged. The highest-priority source that defines any element of the list "
                            + "supplies the whole list, so this variable does not patch element 0 — it "
                            + "makes the environment the owner of the list, and everything the file "
                            + "said about the other elements is gone",
                    "Элемент списка адресуется индексом с подчёркиванием с обеих сторон, поэтому "
                            + key + " — это " + upper + ". А вот что больно: списки ЗАМЕНЯЮТСЯ "
                            + "целиком, а не сливаются. Источник с наивысшим приоритетом, задающий хотя "
                            + "бы один элемент списка, отдаёт список целиком, поэтому эта переменная не "
                            + "правит элемент 0 — она делает владельцем списка окружение, и всё, что "
                            + "файл говорил про остальные элементы, исчезает",
                    List.of("conversion"), state());
        }
        return upper;
    }

    // ----------------------------------------------------------------- resolving

    /**
     * What the application actually reads for {@code key}: the value of the
     * matching variable if one exists, otherwise whatever the file said.
     */
    public String resolve(String key) {
        Property p = property(key);
        String canonical = envName(key);
        beginBinding(key, canonical, p.fileValue);

        Variable match = find(v -> v.name.equals(canonical));
        String reason = "canonical";
        if (match == null) {
            match = find(v -> v.name.equals(key));
            reason = "verbatim";
        }
        if (match == null) {
            String legacy = legacyName(key);
            match = legacy.equals(canonical) ? null : find(v -> v.name.equals(legacy));
            reason = "legacy";
        }
        if (match == null) {
            match = find(v -> v.name.equalsIgnoreCase(canonical));
            reason = "lowercase";
        }

        if (match == null) {
            return missedBinding(p, key, canonical);
        }
        return matchedBinding(p, match, key, canonical, reason);
    }

    private String matchedBinding(Property p, Variable match, String key, String canonical, String reason) {
        match.role = "match";
        bindMatched = true;
        bindReason = reason;
        bindVariable = match.name;
        bindValue = match.value;
        p.value = match.value;
        p.source = "env";
        overridden++;

        String shadowEn = bindFileValue == null
                ? ". Nothing in application.properties defines this key at all, and the application "
                    + "still starts: a variable is a full property source, not a patch on a file"
                : ". application.properties still says " + bindFileValue + " — that value is shadowed, "
                    + "not deleted, and it comes back the moment the variable is unset";
        String shadowRu = bindFileValue == null
                ? ". В application.properties этого ключа нет вообще, и приложение всё равно "
                    + "стартует: переменная — это полноценный источник свойств, а не заплатка к файлу"
                : ". В application.properties по-прежнему написано " + bindFileValue
                    + " — это значение затенено, а не стёрто, и вернётся, как только переменную уберут";

        String noteEn;
        String noteRu;
        switch (reason) {
            case "verbatim" -> {
                noteEn = "The variable is spelled exactly like the property, dots and all. Kubernetes "
                        + "accepts such a name and Spring checks the literal spelling first, so this "
                        + "does work — but bash cannot export it, so nobody can reproduce your setup "
                        + "locally. Write " + canonical + " instead";
                noteRu = "Переменная названа ровно как свойство, вместе с точками. Kubernetes такое имя "
                        + "принимает, а Spring сначала проверяет буквальное написание, так что это "
                        + "работает — но bash такую переменную не экспортирует, и локально ваш стенд "
                        + "никто не повторит. Пишите " + canonical;
            }
            case "legacy" -> {
                noteEn = "The variable spells the dash as an underscore. Spring still accepts that "
                        + "spelling for historical reasons, but it is ambiguous — " + bindVariable
                        + " could equally mean a key with one more dot in it. The documented rule "
                        + "deletes the dash: " + canonical;
                noteRu = "В переменной дефис записан подчёркиванием. Spring по историческим причинам "
                        + "такое написание принимает, но оно двусмысленно: " + bindVariable
                        + " с тем же успехом может означать ключ с ещё одной точкой. Документированное "
                        + "правило дефис удаляет: " + canonical;
            }
            case "lowercase" -> {
                noteEn = "The variable is lowercase. Spring's lookup is case-insensitive here, so it "
                        + "resolves — but the case-insensitivity is a courtesy of the framework, not a "
                        + "rule of the platform, and a reviewer reads a lowercase name as a local shell "
                        + "variable. Write " + canonical;
                noteRu = "Переменная в нижнем регистре. Поиск в Spring здесь нечувствителен к регистру, "
                        + "поэтому значение находится — но эта нечувствительность является любезностью "
                        + "фреймворка, а не правилом платформы, и на ревью имя в нижнем регистре читают "
                        + "как локальную переменную оболочки. Пишите " + canonical;
            }
            default -> {
                noteEn = "The names matched because the canonical conversion of the key produced "
                        + "exactly this variable — this is the spelling to use everywhere";
                noteRu = "Имена совпали, потому что каноническое преобразование ключа дало ровно эту "
                        + "переменную — именно это написание и стоит использовать везде";
            }
        }

        Trace.event("BINDING_MATCHED",
                key + " = " + match.value + ", taken from the variable " + match.name + ". " + noteEn
                        + shadowEn,
                key + " = " + match.value + ", значение взято из переменной " + match.name + ". "
                        + noteRu + shadowRu,
                List.of("variable:" + match.id, "property:" + p.id), state());

        replaceList(p, key);
        return match.value;
    }

    /**
     * Lists are replaced, not merged: once the environment supplies one element,
     * it owns the whole list and every other entry the file had is gone.
     */
    private void replaceList(Property bound, String key) {
        int bracket = key.indexOf('[');
        if (bracket < 0) {
            return;
        }
        String prefix = key.substring(0, bracket + 1);
        List<String> dropped = new ArrayList<>();
        for (Property other : properties) {
            if (other != bound && other.key.startsWith(prefix) && !"dropped".equals(other.source)) {
                other.value = null;
                other.source = "dropped";
                dropped.add(other.key);
            }
        }
        if (dropped.isEmpty()) {
            return;
        }
        Trace.event("LIST_REPLACED",
                "And now the expensive part: " + String.join(", ", dropped) + " went away. A list is "
                        + "replaced, not merged — the highest-priority source that defines any element "
                        + "owns the entire list, so one variable did not patch one element, it made the "
                        + "environment the owner of the whole list. If you override an element, set "
                        + "every element you still need, or pass the list as one comma-separated value",
                "А теперь дорогая часть: " + String.join(", ", dropped) + " исчезли. Список "
                        + "заменяется, а не сливается: источник с наивысшим приоритетом, задающий хотя "
                        + "бы один элемент, владеет всем списком, поэтому одна переменная не поправила "
                        + "один элемент, а сделала владельцем списка окружение. Переопределяете "
                        + "элемент — задайте все нужные элементы или передайте список одним значением "
                        + "через запятую",
                List.of("property:" + bound.id), state());
    }

    private String missedBinding(Property p, String key, String canonical) {
        missed++;
        for (Variable v : variables) {
            if (nearMiss(v.name, canonical)) {
                v.role = "near-miss";
                nearMisses.add(v.name);
            }
        }
        Trace.event("BINDING_MISSED",
                "No variable named " + canonical + " exists, so " + key + " still reads "
                        + (p.fileValue == null ? "null" : p.fileValue)
                        + ". Nothing failed, nothing was logged and nothing was validated: a wrong "
                        + "variable name is indistinguishable from no variable at all, which is why "
                        + "'my override is being ignored' is almost always a spelling question",
                "Переменной с именем " + canonical + " нет, поэтому " + key + " по-прежнему равно "
                        + (p.fileValue == null ? "null" : p.fileValue)
                        + ". Ничто не упало, ничто не записалось в лог и ничто не проверилось: неверное "
                        + "имя переменной неотличимо от полного отсутствия переменной — поэтому «моё "
                        + "переопределение игнорируется» почти всегда вопрос про написание",
                List.of("property:" + p.id), state());

        if (!nearMisses.isEmpty()) {
            Trace.event("NEAR_MISS",
                    "The environment does contain " + String.join(", ", nearMisses)
                            + " — close enough that somebody plainly meant it to work. Spring compares "
                            + "names, not intentions: only " + canonical + " is read. Check the name "
                            + "against the property, not against your memory — and remember that the "
                            + "same typo in a Kubernetes manifest passes review because it looks right",
                    "В окружении есть " + String.join(", ", nearMisses)
                            + " — настолько близко, что понятно: кто-то рассчитывал, что это сработает. "
                            + "Spring сравнивает имена, а не намерения: читается только " + canonical
                            + ". Сверяйте имя со свойством, а не с памятью — и помните, что такая же "
                            + "опечатка в манифесте Kubernetes спокойно проходит ревью, потому что "
                            + "выглядит правильно",
                    List.of("property:" + p.id), state());
        }
        return p.fileValue;
    }

    /**
     * {@code System.getenv(name)} — the raw operating-system lookup, with no
     * Spring in the way: the exact name or nothing.
     */
    public String getenv(String name) {
        Variable found = find(v -> v.name.equals(name));
        getenvName = name;
        getenvValue = found == null ? null : found.value;
        getenvDone = true;
        Trace.event("SYSTEM_GETENV",
                "System.getenv(\"" + name + "\") = " + (getenvValue == null ? "null" : getenvValue)
                        + ". This asks the operating system directly, so it matches the exact name and "
                        + "nothing else — no dots turned into underscores, no case folding, no files. "
                        + "The conversion rule belongs to Spring, not to the JVM, which is why reading "
                        + "configuration through System.getenv forces every caller to hard-code the "
                        + "shouty name and quietly loses every other property source",
                "System.getenv(\"" + name + "\") = " + (getenvValue == null ? "null" : getenvValue)
                        + ". Этот вызов спрашивает операционную систему напрямую, поэтому совпасть "
                        + "должно точное имя и ничего больше: никаких точек, ставших подчёркиваниями, "
                        + "никакого приведения регистра, никаких файлов. Правило преобразования "
                        + "принадлежит Spring, а не JVM, — поэтому чтение конфигурации через "
                        + "System.getenv заставляет каждого вызывающего зашивать имя капсом и молча "
                        + "теряет все остальные источники свойств",
                List.of("getenv"), state());
        return getenvValue;
    }

    /** The same variable, written the way each deployment tool wants it. */
    public VisualEnvBinding deploymentForms(String name, String value) {
        forms.clear();
        forms.add(new Form("shell", "shell", "export " + name + "=" + value));
        forms.add(new Form("inline", "one-off run", name + "=" + value + " java -jar app.jar"));
        forms.add(new Form("docker", "docker run", "docker run -e " + name + "=" + value + " app:1.0"));
        forms.add(new Form("compose", "docker compose",
                "environment:\n  " + name + ": \"" + value + "\""));
        forms.add(new Form("kubernetes", "Kubernetes",
                "env:\n  - name: " + name + "\n    value: \"" + value + "\""));
        Trace.event("DEPLOYMENT_FORMS",
                "The same variable, written the way each tool wants it. Notice what never changes: "
                        + "the name " + name + ". Two spellings on this screen are NOT environment "
                        + "variables and therefore keep their dots — '--" + conversionKeyOr(name)
                        + "=" + value + "' as a command line argument and '-D" + conversionKeyOr(name)
                        + "=" + value + "' as a JVM flag; both outrank the environment. In Kubernetes "
                        + "quote the value, or 30 becomes an integer and the manifest is rejected",
                "Одна и та же переменная, записанная так, как хочет каждый инструмент. Обратите "
                        + "внимание, что не меняется никогда: имя " + name + ". Два написания на этом "
                        + "экране — НЕ переменные окружения и поэтому сохраняют точки: '--"
                        + conversionKeyOr(name) + "=" + value + "' как аргумент командной строки и '-D"
                        + conversionKeyOr(name) + "=" + value + "' как флаг JVM; оба приоритетнее "
                        + "окружения. В Kubernetes значение берут в кавычки, иначе 30 станет числом и "
                        + "манифест не примут",
                List.of("forms"), state());
        return this;
    }

    /** Prints every property with its variable name and where its value came from. */
    public void report() {
        List<String> lines = new ArrayList<>();
        for (Property p : properties) {
            lines.add(p.key + " -> " + p.envName + " = " + (p.value == null ? "null" : p.value)
                    + " (" + p.source + ")");
        }
        Trace.event("NAMING_REPORT",
                "Every key with the variable that overrides it: " + String.join("; ", lines)
                        + ". Read the arrows in one direction only — a property name always has exactly "
                        + "one canonical variable name, while a variable name can be read back as "
                        + "several property names, which is precisely why the rule is defined from the "
                        + "property side. Derived " + derived + " name(s), " + overridden
                        + " value(s) came from the environment, " + missed + " lookup(s) found no "
                        + "variable at all",
                "Каждый ключ и переменная, которая его переопределяет: " + String.join("; ", lines)
                        + ". Читать стрелки следует только в одну сторону: у имени свойства всегда ровно "
                        + "одно каноническое имя переменной, а имя переменной можно прочитать обратно "
                        + "как несколько имён свойств — именно поэтому правило и определено со стороны "
                        + "свойства. Выведено имён: " + derived + ", значений пришло из окружения: "
                        + overridden + ", поисков без единой переменной: " + missed,
                List.of(), state());
    }

    // ------------------------------------------------------------------ the rule

    /**
     * The canonical environment variable name for a property: dots (and list
     * brackets) become underscores, dashes are deleted, everything is uppercased.
     */
    public static String envName(String key) {
        return replaceSeparators(key).replace("-", "").toUpperCase(Locale.ROOT);
    }

    /** Dots and index brackets become underscores; the rest is left alone. */
    private static String replaceSeparators(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == ']') {
                continue;
            }
            sb.append(c == '.' || c == '[' ? '_' : c);
        }
        return sb.toString();
    }

    /** The older spelling, where a dash became an underscore instead of vanishing. */
    private static String legacyName(String key) {
        return replaceSeparators(key).replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /** Letters and digits only — two names that differ just in separators squash alike. */
    private static String squash(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /** A name somebody clearly meant to work: same letters, wrong shape or scope. */
    private static boolean nearMiss(String variableName, String canonical) {
        String a = squash(variableName);
        String b = squash(canonical);
        if (a.length() < 3 || b.length() < 3) {
            return false;
        }
        return a.equals(b) || a.startsWith(b) || b.startsWith(a) || a.endsWith(b) || b.endsWith(a);
    }

    // ----------------------------------------------------------------- internals

    private void step(String id, String value, boolean changed, String descEn, String descRu) {
        steps.add(new Step(id, value, changed));
        Trace.event("NAME_STEP", descEn, descRu, List.of("conversion", "step:" + id), state());
    }

    private void beginConversion(String key) {
        conversionKey = key;
        conversionEnvName = "";
        conversionDone = false;
        conversionIndexed = key.indexOf('[') >= 0;
        steps.clear();
    }

    private void beginBinding(String key, String canonical, String fileValue) {
        bindKey = key;
        bindEnvName = canonical;
        bindStarted = true;
        bindMatched = false;
        bindReason = "none";
        bindVariable = "";
        bindValue = null;
        bindFileValue = fileValue;
        nearMisses.clear();
        for (Variable v : variables) {
            v.role = "";
        }
    }

    private Property property(String key) {
        for (Property p : properties) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        Property p = new Property("prop-" + (counter++), key, envName(key));
        properties.add(p);
        return p;
    }

    private Variable find(Predicate<Variable> test) {
        for (Variable v : variables) {
            if (test.test(v)) {
                return v;
            }
        }
        return null;
    }

    /** The dotted key behind a variable name, for the "not a variable" examples. */
    private String conversionKeyOr(String variableName) {
        return conversionKey.isEmpty() ? variableName.toLowerCase(Locale.ROOT).replace('_', '.')
                : conversionKey;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        if (conversionKey.isEmpty()) {
            s.put("conversion", null);
        } else {
            Map<String, Object> conversion = new LinkedHashMap<>();
            conversion.put("property", conversionKey);
            conversion.put("envName", conversionEnvName);
            conversion.put("done", conversionDone);
            conversion.put("indexed", conversionIndexed);
            List<Object> stepList = new ArrayList<>();
            for (Step step : steps) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", step.id);
                item.put("value", step.value);
                item.put("changed", step.changed);
                stepList.add(item);
            }
            conversion.put("steps", stepList);
            s.put("conversion", conversion);
        }

        List<Object> variableList = new ArrayList<>();
        for (Variable v : variables) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", v.id);
            item.put("name", v.name);
            item.put("value", v.value);
            item.put("role", v.role);
            variableList.add(item);
        }
        s.put("variables", variableList);

        List<Object> propertyList = new ArrayList<>();
        for (Property p : properties) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.id);
            item.put("key", p.key);
            item.put("envName", p.envName);
            item.put("fileValue", p.fileValue);
            item.put("value", p.value);
            item.put("source", p.source);
            propertyList.add(item);
        }
        s.put("properties", propertyList);

        if (!bindStarted) {
            s.put("binding", null);
        } else {
            Map<String, Object> binding = new LinkedHashMap<>();
            binding.put("key", bindKey);
            binding.put("envName", bindEnvName);
            binding.put("matched", bindMatched);
            binding.put("reason", bindReason);
            binding.put("variable", bindVariable);
            binding.put("value", bindValue);
            binding.put("fileValue", bindFileValue);
            binding.put("nearMisses", new ArrayList<>(nearMisses));
            s.put("binding", binding);
        }

        if (!getenvDone) {
            s.put("getenv", null);
        } else {
            Map<String, Object> getenv = new LinkedHashMap<>();
            getenv.put("name", getenvName);
            getenv.put("value", getenvValue);
            s.put("getenv", getenv);
        }

        List<Object> formList = new ArrayList<>();
        for (Form form : forms) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", form.id);
            item.put("platform", form.platform);
            item.put("snippet", form.snippet);
            formList.add(item);
        }
        s.put("forms", formList);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("derived", derived);
        stats.put("overridden", overridden);
        stats.put("missed", missed);
        s.put("stats", stats);
        return s;
    }
}
