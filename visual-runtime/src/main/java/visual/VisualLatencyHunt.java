package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of finding out why a website is slow — the hunt that
 * starts with "the site is slow" and has to end with a number that moved.
 *
 * <p>The model is a stopwatch and a latency budget. "Slow" is turned into a
 * measured total, the total is split into segments, and the biggest segment is
 * split again, until what is left is small enough to be a cause:
 * <ul>
 *   <li>{@link #guess(String)} is the shortcut everybody takes first — an
 *       optimisation chosen before anything was measured;</li>
 *   <li>{@link #clarify(String, String)} turns "slow" into which page, whose
 *       request, how slow, since when, and {@link #target(String, int)} writes
 *       down the number that counts as "fast enough";</li>
 *   <li>{@link #distribution(int, int, int, int)} reads the shape of the
 *       latency rather than its average — the complaining user lives in the
 *       tail;</li>
 *   <li>{@link #measure(String, int, String)} adds one segment of the current
 *       level's budget, {@link #split()} reads the level back and names the
 *       segment holding the time, {@link #ruleOut(String, String)} closes a
 *       segment with evidence, and {@link #drillInto(String, String)} opens the
 *       hotspot up into its own level — that is the whole method, applied
 *       recursively;</li>
 *   <li>{@link #underLoad(int, int)} and {@link #resource(String, String, boolean)}
 *       answer the other question: is the work slow, or is the wait long?</li>
 *   <li>{@link #ceiling(String, String, int)} prices a proposed optimisation
 *       against the whole request before anybody writes it (Amdahl's law as a
 *       one-line calculation);</li>
 *   <li>{@link #confirm(String, String)}, {@link #fix(String, int)},
 *       {@link #remeasure(int)} and {@link #guard(String)} close the hunt: the
 *       cause, the change, the same measurement taken again, and the monitoring
 *       that makes the next hunt start from data.</li>
 * </ul>
 *
 * <p>The mistakes are not separate methods — they fall out of the order the
 * methods are called in. Changing code with no confirmed cause is a
 * {@code BLIND_OPTIMIZATION}; drilling into a segment that is not the hotspot
 * is recorded as chasing a small slice; a fix that never gets re-measured is
 * flagged at review. Every step emits a bilingual {@link Trace} event; the class
 * is intentionally dependency-free.
 */
public class VisualLatencyHunt {

    /** The stages a hunt passes through, in order; used to keep the state monotonic. */
    private static final String[] STAGES = {
            "reported", "scoped", "measuring", "localized", "confirmed", "fixed", "verified"
    };

    /** A gain smaller than this is measurement noise, not an improvement. */
    private static final int NOISE_PERCENT = 10;

    /** One slice of the current level's latency budget. */
    private static final class Segment {
        final String name;
        final int millis;
        final String tool;
        boolean cleared;
        boolean drilled;
        String note = "";

        Segment(String name, int millis, String tool) {
            this.name = name;
            this.millis = millis;
            this.tool = tool;
        }
    }

    /** One level of the hunt: a total split into segments. The root is end-to-end. */
    private static final class Level {
        final String name;
        final List<Segment> segments = new ArrayList<>();

        Level(String name) {
            this.name = name;
        }

        int total() {
            int sum = 0;
            for (Segment segment : segments) {
                sum += segment.millis;
            }
            return sum;
        }

        Segment hotspot() {
            Segment best = null;
            for (Segment segment : segments) {
                if (segment.cleared) {
                    continue;
                }
                if (best == null || segment.millis > best.millis) {
                    best = segment;
                }
            }
            return best;
        }

        Segment find(String name) {
            for (Segment segment : segments) {
                if (segment.name.equals(name)) {
                    return segment;
                }
            }
            return null;
        }
    }

    /** One resource reading: utilization, saturation or errors. */
    private static final class Resource {
        final String name;
        final String reading;
        final boolean saturated;

        Resource(String name, String reading, boolean saturated) {
            this.name = name;
            this.reading = reading;
            this.saturated = saturated;
        }
    }

    /** The priced ceiling of a proposed optimisation, before anybody writes it. */
    private static final class Ceiling {
        final String change;
        final String segment;
        final int speedup;
        final int savedMs;
        final int gainPercent;

        Ceiling(String change, String segment, int speedup, int savedMs, int gainPercent) {
            this.change = change;
            this.segment = segment;
            this.speedup = speedup;
            this.savedMs = savedMs;
            this.gainPercent = gainPercent;
        }
    }

    private final String site;
    private final String journey;
    private final String complaint;
    private final List<Level> levels = new ArrayList<>();
    /** {question, answer} pairs collected while turning "slow" into a request. */
    private final List<String[]> facts = new ArrayList<>();
    private final List<Resource> resources = new ArrayList<>();
    private final List<Ceiling> ceilings = new ArrayList<>();
    private final List<String> missingSignals = new ArrayList<>();
    private final List<String> guards = new ArrayList<>();
    private final List<String> missteps = new ArrayList<>();

    private int minutes;
    private String stage = "reported";

    private String targetLabel;
    private int budgetMs;

    private boolean distributionRead;
    private int avgMs;
    private int p50Ms;
    private int p95Ms;
    private int p99Ms;

    private boolean loadCompared;
    private int idleMs;
    private int peakMs;

    private String cause;
    private String causeEvidence;
    private String fixChange;
    private int fixExpectedMs;
    private boolean remeasured;
    private int beforeMs;
    private int afterMs;
    private int gainPercent;
    private boolean improved;

    private VisualLatencyHunt(String site, String journey, String complaint) {
        this.site = site;
        this.journey = journey;
        this.complaint = complaint;
        levels.add(new Level("end-to-end"));
    }

    /** A complaint lands. It contains no number, so it contains no bug yet. */
    public static VisualLatencyHunt reported(String site, String journey, String complaint) {
        VisualLatencyHunt hunt = new VisualLatencyHunt(site, journey, complaint);
        Trace.event("SLOWNESS_REPORTED",
                site + ": \"" + complaint + "\" about " + journey + ". Notice what is missing from that "
                        + "sentence: a number, a page, a user, and a time. \"Slow\" is a feeling that something "
                        + "measurable caused, and the entire skill in this question is refusing to skip from the "
                        + "feeling straight to a fix. There is a reason everybody skips it anyway — by the time "
                        + "somebody complains you already have three theories, and all three are about code you "
                        + "personally dislike. The discipline is one sentence long: find where the time goes "
                        + "before deciding what to make faster. A request that takes 4 seconds spends those 4 "
                        + "seconds somewhere specific, and every second is in exactly one place at a time",
                site + ": «" + complaint + "» про " + journey + ". Обратите внимание, чего в этой фразе нет: "
                        + "числа, страницы, пользователя и времени. «Медленно» — это ощущение, вызванное чем-то "
                        + "измеримым, и всё умение в этом вопросе состоит в отказе прыгнуть от ощущения сразу к "
                        + "исправлению. Причина, по которой все всё равно прыгают, понятна: к моменту жалобы у "
                        + "вас уже есть три теории, и все три — про код, который вам лично не нравится. "
                        + "Дисциплина умещается в одну фразу: сначала выясните, куда уходит время, и только "
                        + "потом решайте, что ускорять. Запрос, который идёт 4 секунды, тратит эти 4 секунды "
                        + "где-то конкретно, и каждая секунда в один момент находится ровно в одном месте",
                List.of("level:0"), hunt.state());
        return hunt;
    }

    // ------------------------------------------------------------ the shortcut

    /** Optimises something before anything has been measured. Everybody's first move. */
    public void guess(String change) {
        tick(120);
        flag("optimized-on-a-hunch");
        Trace.event("GUESS_MADE",
                "\"" + change + "\" — chosen before a single measurement, and two days later nobody can say "
                        + "whether it helped. This is the default failure mode of the whole topic, and it does "
                        + "not feel like a failure while it is happening: you are writing real code, fixing a "
                        + "real inefficiency, and it is genuinely faster than it was. The problem is arithmetic. "
                        + "If the thing you improved was 4% of the request, the user cannot notice it no matter "
                        + "how much better it gets, and you have now added a change to a system whose behaviour "
                        + "you still cannot explain. Worse, a guess that appears to work is the expensive one: "
                        + "the complaint stops for unrelated reasons, you keep the belief, and you carry it into "
                        + "the next three systems",
                "«" + change + "» — выбрано до единого измерения, и через два дня никто не может сказать, "
                        + "помогло ли. Это режим отказа по умолчанию для всей темы, и в процессе он вовсе не "
                        + "ощущается как отказ: вы пишете настоящий код, чините настоящую неэффективность, и "
                        + "она действительно становится быстрее, чем была. Проблема арифметическая. Если "
                        + "улучшенное вами было 4% запроса, пользователь не заметит этого, как бы хорошо оно ни "
                        + "стало, а вы добавили изменение в систему, поведение которой до сих пор не умеете "
                        + "объяснить. Хуже того, дорого обходится именно догадка, которая как будто сработала: "
                        + "жалобы прекратились по посторонним причинам, вера осталась — и вы унесёте её в "
                        + "следующие три системы",
                List.of("misstep:optimized-on-a-hunch"), state());
    }

    // --------------------------------------------------------------- scoping

    /** Turns one word of the complaint into something with an answer attached. */
    public void clarify(String question, String answer) {
        tick(3);
        advance("scoped");
        facts.add(new String[]{question, answer});
        Trace.event("SCOPE_NARROWED",
                "\"" + question + "\" -> \"" + answer + "\". Five answers turn a mood into a measurement, and "
                        + "you can get all five in one message: WHICH page or request (a URL, not \"the site\"), "
                        + "WHO sees it (one user, one region, one device, everybody), HOW slow (seconds, "
                        + "measured, versus what it used to be), SINCE WHEN (gradually over a month, or at "
                        + "14:20 on Tuesday — one is growth, the other is a change), and HOW OFTEN (every time "
                        + "or one in ten). The last two matter more than they look. A cliff points at a deploy, "
                        + "a config change or a dependency; a slope points at data volume, a cache that stopped "
                        + "fitting, or a leak. And \"one request in ten\" is not a slow system, it is a tail — "
                        + "which is a different investigation",
                "«" + question + "» -> «" + answer + "». Пять ответов превращают настроение в измерение, и все "
                        + "пять можно получить одним сообщением: КАКАЯ страница или запрос (URL, а не «сайт»), "
                        + "КТО это видит (один пользователь, один регион, одно устройство, все), НАСКОЛЬКО "
                        + "медленно (в секундах, измеренных, по сравнению с тем, как было), С КАКОГО МОМЕНТА "
                        + "(постепенно за месяц или в 14:20 во вторник — первое рост, второе изменение) и КАК "
                        + "ЧАСТО (каждый раз или один раз из десяти). Последние два важнее, чем кажутся. "
                        + "Обрыв указывает на деплой, правку конфигурации или зависимость; наклон — на объём "
                        + "данных, кэш, переставший помещаться, или утечку. А «один запрос из десяти» — это не "
                        + "медленная система, это хвост распределения, и расследование там другое",
                List.of("fact:" + facts.size()), state());
    }

    /** Writes down the number that counts as "fast enough", before optimising anything. */
    public void target(String description, int millis) {
        tick(5);
        advance("scoped");
        targetLabel = description;
        budgetMs = millis;
        Trace.event("BUDGET_SET",
                description + " must be under " + millis + "ms. Without this line the hunt has no end "
                        + "condition, and \"faster\" becomes a job rather than a task — there is always one more "
                        + "query to tune. A budget also decides which findings matter: at a 800ms target a 40ms "
                        + "segment is noise you should refuse to look at, and the same 40ms is the whole problem "
                        + "when the target is 100ms. Pick the number from the outside, not the inside: what the "
                        + "user is doing (typing-latency, a page they wait on, a report they go and make coffee "
                        + "for), what the business promised, and at which percentile — a budget without a "
                        + "percentile attached is half a sentence",
                description + " должно укладываться в " + millis + "мс. Без этой строки у охоты нет условия "
                        + "остановки, и «быстрее» превращается из задачи в работу — всегда найдётся ещё один "
                        + "запрос, который можно подтюнить. Бюджет ещё и решает, какие находки важны: при цели "
                        + "800мс сегмент в 40мс — это шум, на который нужно отказаться смотреть, а те же 40мс "
                        + "составляют всю проблему, когда цель 100мс. Берите число снаружи, а не изнутри: что "
                        + "делает пользователь (задержка ввода, страница, которую ждут, отчёт, под который "
                        + "уходят за кофе), что пообещал бизнес и на каком перцентиле — бюджет без перцентиля "
                        + "это половина фразы",
                List.of("budget"), state());
    }

    /** Reads the shape of the latency instead of its average. */
    public void distribution(int avg, int p50, int p95, int p99) {
        tick(4);
        advance("scoped");
        distributionRead = true;
        avgMs = avg;
        p50Ms = p50;
        p95Ms = p95;
        p99Ms = p99;
        int spread = p50 > 0 ? p99 / p50 : 0;
        Trace.event("PERCENTILES_READ",
                "avg " + avg + "ms, p50 " + p50 + "ms, p95 " + p95 + "ms, p99 " + p99 + "ms"
                        + (spread >= 3
                                ? " — the tail is " + spread + "x the median, so the average is describing "
                                        + "nobody"
                                : " — a tight distribution, so the median is the story")
                        + ". The average is the one number that is always available and almost never useful: it "
                        + "mixes the cached hits with the misses and the empty accounts with the large ones, and "
                        + "a single 30-second request can drag it up while everything else is fine. Read p50 for "
                        + "\"what a normal visit looks like\", p95 for \"what the person complaining is living "
                        + "through\", and p99 for the shape of the tail. And when the average looks fine and the "
                        + "tail is terrible, believe the tail — because a user who does six requests per page "
                        + "meets your p95 more often than you would like",
                "среднее " + avg + "мс, p50 " + p50 + "мс, p95 " + p95 + "мс, p99 " + p99 + "мс"
                        + (spread >= 3
                                ? " — хвост в " + spread + " раз больше медианы, то есть среднее не описывает "
                                        + "никого"
                                : " — распределение плотное, значит вся история в медиане")
                        + ". Среднее — единственное число, которое доступно всегда и почти никогда не полезно: "
                        + "оно смешивает попадания в кэш с промахами, а пустые аккаунты с большими, и один "
                        + "30-секундный запрос вытянет его вверх, пока всё остальное в порядке. Читайте p50 как "
                        + "«как выглядит обычный визит», p95 как «что переживает жалующийся» и p99 как форму "
                        + "хвоста. А когда среднее выглядит нормально, а хвост ужасен, верьте хвосту: "
                        + "пользователь, делающий шесть запросов на страницу, встречает ваш p95 чаще, чем вам "
                        + "хотелось бы",
                List.of("percentiles"), state());
    }

    /** Names a measurement that does not exist — the reason the hunt is guesswork. */
    public void missingSignal(String name) {
        tick(2);
        flag("no-measurement");
        missingSignals.add(name);
        Trace.event("SIGNAL_MISSING",
                "There is no " + name + ". Write it down, because it is the actual answer to the interview "
                        + "question in most real systems: you cannot find where the time goes if nothing records "
                        + "where the time goes, and the honest next step is to add the measurement rather than "
                        + "to start guessing faster. The good news is how cheap the first layer is — server "
                        + "access logs already have per-request duration, the browser has a waterfall with no "
                        + "setup at all, and one request id logged at the boundary of every call turns four "
                        + "disconnected logs into one timeline. Distributed tracing is the version of this you "
                        + "buy on purpose; the version you get for free is still enough to name the segment",
                "Нет: " + name + ". Запишите это, потому что в большинстве реальных систем именно здесь и "
                        + "лежит ответ на вопрос собеседования: нельзя найти, куда уходит время, если ничто не "
                        + "записывает, куда уходит время, и честный следующий шаг — добавить измерение, а не "
                        + "начать быстрее гадать. Хорошая новость в том, насколько дёшев первый слой: в логах "
                        + "доступа сервера уже есть длительность каждого запроса, у браузера есть waterfall "
                        + "вообще без настройки, а один идентификатор запроса, записанный на границе каждого "
                        + "вызова, превращает четыре разрозненных лога в одну шкалу времени. Распределённая "
                        + "трассировка — это версия того же, которую покупают осознанно; бесплатной версии "
                        + "хватает, чтобы назвать сегмент",
                List.of("misstep:no-measurement"), state());
    }

    // ------------------------------------------------------------- the budget

    /** Adds one measured slice to the level currently being split. */
    public void measure(String name, int millis, String tool) {
        tick(4);
        advance("measuring");
        current().segments.add(new Segment(name, millis, tool));
        Trace.event("SEGMENT_MEASURED",
                name + ": " + millis + "ms (" + tool + "). One slice of " + current().name + ", measured "
                        + "rather than assumed. The rule that makes this work is that the slices must ADD UP to "
                        + "the number the user experiences — if the parts sum to 400ms and the page takes 4 "
                        + "seconds, the interesting 3.6 seconds is in a part you have not instrumented, and "
                        + "that gap is the finding. This is why the browser's network panel is the right first "
                        + "screen for a website: DNS, connect, TLS, waiting for the first byte, downloading, "
                        + "then rendering and script execution — a complete, already-installed decomposition of "
                        + "the exact thing the person complained about",
                name + ": " + millis + "мс (" + tool + "). Один срез уровня «" + current().name + "», "
                        + "измеренный, а не предположенный. Работает это благодаря правилу: срезы обязаны "
                        + "СКЛАДЫВАТЬСЯ в то число, которое переживает пользователь. Если части дают в сумме "
                        + "400мс, а страница идёт 4 секунды, то интересные 3,6 секунды сидят в части, которую "
                        + "вы не инструментировали, и этот разрыв и есть находка. Поэтому для сайта правильный "
                        + "первый экран — сетевая панель браузера: DNS, соединение, TLS, ожидание первого "
                        + "байта, загрузка, затем рендеринг и выполнение скриптов — полная и уже установленная "
                        + "декомпозиция ровно того, на что пожаловались",
                List.of("level:" + (levels.size() - 1), "segment:" + name), state());
    }

    /** Reads the current level back and names the segment holding the time. */
    public void split() {
        tick(2);
        Level level = current();
        Segment hotspot = level.hotspot();
        int total = level.total();
        int share = hotspot == null || total == 0 ? 0 : hotspot.millis * 100 / total;
        if (hotspot != null) {
            advance("localized");
        }
        Trace.event("TIME_SPLIT",
                level.name + " = " + total + "ms across " + level.segments.size() + " segment(s)"
                        + (hotspot == null ? ", nothing measured yet"
                                : "; " + hotspot.name + " holds " + hotspot.millis + "ms = " + share + "% of it")
                        + ". This is the whole method and it fits in one line: split the time, follow the "
                        + "biggest piece, split it again, stop when what is left is small enough to be a cause. "
                        + "Two things make it reliable. It is measurement, so your opinion about which layer is "
                        + "\"probably\" slow never gets a vote; and it converges, because each split multiplies "
                        + "your resolution instead of adding to a list of things to check. What it gives you is "
                        + "not just a suspect — it is an upper bound on every fix you could possibly apply "
                        + "outside that " + share + "%",
                level.name + " = " + total + "мс на " + level.segments.size() + " сегмент(ов)"
                        + (hotspot == null ? ", пока ничего не измерено"
                                : "; " + hotspot.name + " держит " + hotspot.millis + "мс = " + share + "% от них")
                        + ". Это весь метод, и он умещается в одну строку: разбей время, иди за самым большим "
                        + "куском, разбей его снова, остановись, когда оставшееся достаточно мало, чтобы быть "
                        + "причиной. Надёжным его делают две вещи. Это измерение — поэтому ваше мнение о том, "
                        + "какой слой «наверное» медленный, не имеет права голоса; и оно сходится, потому что "
                        + "каждое разбиение умножает разрешение, а не удлиняет список того, что надо проверить. "
                        + "И даёт он не только подозреваемого, но и верхнюю границу для любого исправления за "
                        + "пределами этих " + share + "%",
                hotspot == null
                        ? List.of("level:" + (levels.size() - 1))
                        : List.of("level:" + (levels.size() - 1), "segment:" + hotspot.name, "hotspot"),
                state());
    }

    /** Closes a segment with the number that cleared it. */
    public void ruleOut(String segmentName, String evidence) {
        tick(2);
        Segment segment = current().find(segmentName);
        if (segment == null) {
            segment = new Segment(segmentName, 0, "checked");
            current().segments.add(segment);
        }
        segment.cleared = true;
        segment.note = evidence;
        Trace.event("SEGMENT_CLEARED",
                segmentName + " is out: " + evidence + ". A segment leaves with a number attached, never with "
                        + "\"that part is probably fine\" — the segment everybody clears by reputation is the "
                        + "one the cause is hiding in. Clearing is not a formality either: it is what stops the "
                        + "hunt going in circles at hour three, and it is what lets you say later that you "
                        + "checked. Keep the eliminations where the next person can read them, because half of "
                        + "them are worth more to the team than the fix — \"the CDN is not the problem, here is "
                        + "the measurement\" outlives this incident",
                segmentName + " отпадает: " + evidence + ". Сегмент уходит с приложенным числом, а не со "
                        + "словами «эта часть, наверное, в порядке»: причина прячется ровно в том сегменте, "
                        + "который все закрывают по репутации. И закрытие — не формальность: именно оно не даёт "
                        + "охоте пойти по кругу на третьем часу и именно оно позволяет потом сказать, что вы "
                        + "это проверяли. Держите исключения там, где их прочитает следующий: половина из них "
                        + "ценнее для команды, чем само исправление, — «CDN тут ни при чём, вот измерение» "
                        + "переживёт этот инцидент",
                List.of("level:" + (levels.size() - 1), "segment:" + segmentName), state());
    }

    /** Opens the hotspot into its own level and keeps splitting. */
    public void drillInto(String segmentName, String tool) {
        tick(6);
        Level level = current();
        Segment segment = level.find(segmentName);
        Segment hotspot = level.hotspot();
        int millis = segment == null ? 0 : segment.millis;

        if (segment == null) {
            flag("drilled-before-measuring");
        } else {
            segment.drilled = true;
            if (hotspot != null && !hotspot.name.equals(segmentName)) {
                flag("chased-a-small-slice");
            }
        }
        levels.add(new Level(segmentName));

        Trace.event("DRILL_DOWN",
                "Opening " + segmentName + (millis > 0 ? " (" + millis + "ms)" : "") + " with " + tool
                        + ". The same question, one level lower — where inside THIS does the time go — and the "
                        + "answer is always another sum of parts. A slow first byte splits into queueing, "
                        + "application code, database calls and outbound calls; a slow database call splits "
                        + "into the number of queries and the cost of each; a slow query splits into what its "
                        + "plan actually does. Every level needs its own tool and none of them are exotic: the "
                        + "network panel, a request log with durations, a trace or a profile, then EXPLAIN. "
                        + "Drill into the biggest slice and only the biggest slice — a level you open out of "
                        + "curiosity is a level you will optimise out of sunk cost",
                "Раскрываем " + segmentName + (millis > 0 ? " (" + millis + "мс)" : "") + " инструментом "
                        + tool + ". Тот же вопрос уровнем ниже — куда уходит время ВНУТРИ этого, — и ответ "
                        + "снова оказывается суммой частей. Медленный первый байт распадается на ожидание в "
                        + "очереди, код приложения, обращения к базе и исходящие вызовы; медленное обращение к "
                        + "базе — на число запросов и цену каждого; медленный запрос — на то, что реально "
                        + "делает его план. Каждому уровню нужен свой инструмент, и ни один из них не "
                        + "экзотичен: сетевая панель, лог запросов с длительностями, трассировка или "
                        + "профилировщик, затем EXPLAIN. Углубляйтесь в самый большой срез и только в него: "
                        + "уровень, открытый из любопытства, — это уровень, который вы потом оптимизируете из "
                        + "нежелания признать потраченное время",
                List.of("level:" + (levels.size() - 1), "segment:" + segmentName), state());
    }

    // ---------------------------------------------------------- work vs wait

    /** Compares the same request with nobody on the site and at peak. */
    public void underLoad(int idle, int peak) {
        tick(10);
        loadCompared = true;
        idleMs = idle;
        peakMs = peak;
        boolean queueing = peak >= idle * 2;
        Trace.event("LOAD_COMPARED",
                "The same request: " + idle + "ms with nobody on the site, " + peak + "ms at peak"
                        + (queueing
                                ? " — so most of what the user waits for is not work, it is a queue"
                                : " — so the work itself is what takes that long")
                        + ". This single comparison decides which half of the world you are in, and the two "
                        + "halves have nothing in common. If it is slow when idle, the work is slow: the code, "
                        + "the query, the payload, the number of round trips — and the fix is in a file. If it "
                        + "is only slow when busy, the code never changed; the time is spent waiting for a "
                        + "thread, a connection from the pool, a lock, a CPU or a disk, and profiling the "
                        + "handler will show you nothing at all. That half is a capacity problem, and it is "
                        + "solved with limits, pools, caching and instances rather than with cleverness",
                "Один и тот же запрос: " + idle + "мс, когда на сайте никого, и " + peak + "мс на пике"
                        + (queueing
                                ? " — значит большая часть ожидания пользователя это не работа, а очередь"
                                : " — значит столько занимает сама работа")
                        + ". Это одно сравнение решает, в какой половине мира вы находитесь, а половины эти не "
                        + "имеют между собой ничего общего. Медленно при простое — медленна работа: код, "
                        + "запрос, объём данных, число round trip'ов, и исправление лежит в файле. Медленно "
                        + "только под нагрузкой — код не менялся; время уходит на ожидание потока, соединения "
                        + "из пула, блокировки, процессора или диска, и профилирование обработчика не покажет "
                        + "вам ровным счётом ничего. Эта половина — про мощность, и лечится она лимитами, "
                        + "пулами, кэшированием и экземплярами, а не сообразительностью",
                List.of("load"), state());
    }

    /** Reads one resource: is it utilized, is it saturated, is it erroring. */
    public void resource(String name, String reading, boolean saturated) {
        tick(3);
        resources.add(new Resource(name, reading, saturated));
        Trace.event("RESOURCE_CHECKED",
                name + ": " + reading + (saturated ? " — saturated." : " — has headroom.")
                        + " Walk the resources the request needs and ask three things about each: how busy it "
                        + "is, whether anything is waiting in line for it, and whether it is refusing work. The "
                        + "middle one is what people skip, and it is the one that explains latency — a pool at "
                        + "100% utilization with nobody queued is a pool doing its job, while a pool at 60% "
                        + "with a queue behind it is where seconds disappear. The usual suspects on a web "
                        + "request are few enough to memorise: CPU, memory and GC pauses, the connection pool, "
                        + "the database's own locks and IO, the thread pool, and the network to whatever you "
                        + "call. A saturated resource explains latency for every endpoint at once, which is "
                        + "exactly what \"the whole site got slow\" means",
                name + ": " + reading + (saturated ? " — насыщен." : " — есть запас.")
                        + " Пройдите по ресурсам, которые нужны запросу, и спросите о каждом три вещи: "
                        + "насколько он занят, стоит ли кто-нибудь к нему в очереди и не отказывает ли он в "
                        + "работе. Пропускают обычно второе — а именно оно объясняет задержку: пул на 100% "
                        + "загрузки без очереди это пул, делающий свою работу, а пул на 60% с очередью позади "
                        + "— это место, где исчезают секунды. Обычных подозреваемых на веб-запросе достаточно "
                        + "мало, чтобы их запомнить: процессор, память и паузы GC, пул соединений, блокировки "
                        + "и ввод-вывод самой базы, пул потоков и сеть до того, кого вы вызываете. Насыщенный "
                        + "ресурс объясняет задержку сразу всех эндпоинтов — а это ровно то, что означает "
                        + "«весь сайт стал медленным»",
                List.of("resource:" + name), state());
    }

    // ----------------------------------------------------------- the ceiling

    /** Prices a proposed optimisation against the whole request, before writing it. */
    public void ceiling(String change, String segmentName, int speedup) {
        tick(3);
        int factor = Math.max(1, speedup);
        Segment segment = findAnywhere(segmentName);
        int segmentMs = segment == null ? 0 : segment.millis;
        int saved = segmentMs - segmentMs / factor;
        int total = endToEndMs();
        int gain = total > 0 ? saved * 100 / total : 0;
        ceilings.add(new Ceiling(change, segmentName, factor, saved, gain));

        Trace.event("CEILING_COMPUTED",
                change + ": making " + segmentName + " " + factor + "x faster removes " + saved + "ms of "
                        + segmentMs + "ms, which is " + gain + "% of the " + total + "ms the user waits"
                        + (gain >= NOISE_PERCENT
                                ? " — worth building"
                                : " — invisible, and worth saying out loud before anybody starts")
                        + ". This one line of arithmetic is Amdahl's law and it settles most performance "
                        + "arguments before they happen: the ceiling on any optimisation is the size of the "
                        + "slice it touches, so even making that slice infinitely fast only buys you the slice. "
                        + "Run this calculation on your own idea first, because it is equally good at killing "
                        + "the rewrite you wanted to do and at justifying the boring cache you did not. And "
                        + "note the direction it points: two seconds of waiting for a database is a bigger "
                        + "prize than every micro-optimisation in your codebase combined",
                change + ": ускорение " + segmentName + " в " + factor + " раз убирает " + saved + "мс из "
                        + segmentMs + "мс, а это " + gain + "% от тех " + total + "мс, которые ждёт "
                        + "пользователь"
                        + (gain >= NOISE_PERCENT
                                ? " — стоит делать"
                                : " — незаметно, и сказать это вслух стоит до того, как кто-то начнёт")
                        + ". Эта одна строчка арифметики и есть закон Амдала, и она решает большинство споров "
                        + "о производительности до их начала: потолок любой оптимизации равен размеру среза, "
                        + "которого она касается, так что даже бесконечное ускорение этого среза приносит вам "
                        + "только сам срез. Прогоняйте этот расчёт сначала на собственной идее — он одинаково "
                        + "хорош и в том, чтобы убить желанный переписыватель, и в том, чтобы обосновать "
                        + "скучный кэш, которого не хотелось. И заметьте, куда он указывает: две секунды "
                        + "ожидания базы — приз крупнее, чем все микрооптимизации вашего кода вместе взятые",
                List.of("ceiling:" + ceilings.size(), "segment:" + segmentName), state());
    }

    // ------------------------------------------------------- cause and change

    /** Names the cause, with the measurement that made it more than a story. */
    public void confirm(String name, String evidence) {
        tick(8);
        advance("confirmed");
        cause = name;
        causeEvidence = evidence;
        Trace.event("CAUSE_CONFIRMED",
                "Cause: " + name + ". Evidence: " + evidence + ". The bar for that word is that the cause "
                        + "accounts for the time — not that it is suspicious, not that it is bad code, but that "
                        + "its milliseconds add up to the milliseconds you are missing. If your cause explains "
                        + "300ms of a 3-second page, you have found A cause and not THE cause, and shipping it "
                        + "will produce a graph that does not move and a stakeholder who stops believing you. "
                        + "The cheapest confirmation is usually available before any code changes: remove the "
                        + "suspected work in a scratch environment, or run the same request against the same "
                        + "data with the suspect disabled, and watch the number come down",
                "Причина: " + name + ". Улика: " + evidence + ". Планка для этого слова такова: причина "
                        + "объясняет время — не то, что она подозрительна, и не то, что это плохой код, а то, "
                        + "что её миллисекунды складываются в те миллисекунды, которых вы недосчитались. Если "
                        + "ваша причина объясняет 300мс трёхсекундной страницы, вы нашли ОДНУ причину, а не ТУ "
                        + "САМУЮ, и её выкатка даст график, который не сдвинулся, и заказчика, который "
                        + "перестал вам верить. Самое дешёвое подтверждение обычно доступно до любых правок "
                        + "кода: уберите подозреваемую работу в тестовом окружении или выполните тот же запрос "
                        + "на тех же данных с отключённым подозреваемым и посмотрите, как число падает",
                List.of("cause"), state());
    }

    /** Changes the system. Whether that is engineering or gambling depends on the order. */
    public void fix(String change, int expectedSavingMs) {
        tick(30);
        fixChange = change;
        fixExpectedMs = expectedSavingMs;

        if (cause == null) {
            flag("changed-without-a-cause");
            Trace.event("BLIND_OPTIMIZATION",
                    "Shipping \"" + change + "\" with no confirmed cause. It may even be a real improvement — "
                            + "that is what makes this so hard to argue against — but you have no way to tell, "
                            + "because you never wrote down the number it was supposed to move. Three costs, all "
                            + "of them ordinary. The change carries risk and pays for it with an unknown gain. "
                            + "If the complaint stops, you will credit this, and the belief goes into the next "
                            + "system with you. And you have spent the one thing performance work actually runs "
                            + "on, which is the trust that buys you time to measure properly next time. Guessing "
                            + "is fine in a branch where being wrong is free; it is not fine as a plan",
                    "Выкатка «" + change + "» без подтверждённой причины. Возможно, это даже настоящее "
                            + "улучшение — именно поэтому с таким трудно спорить, — но узнать этого вы не "
                            + "можете, потому что не записали число, которое оно должно было сдвинуть. Три "
                            + "цены, и все обыденные. Изменение несёт риск и платит за него неизвестной "
                            + "выгодой. Если жалобы прекратятся, вы запишете это себе — и вера уедет вместе с "
                            + "вами в следующую систему. И вы потратили то единственное, на чём вообще держится "
                            + "работа над производительностью: доверие, которое покупает вам время нормально "
                            + "измерить в следующий раз. Гадать нормально в ветке, где ошибиться бесплатно; "
                            + "гадать как план — нет",
                    List.of("misstep:changed-without-a-cause"), state());
            return;
        }

        advance("fixed");
        Trace.event("FIX_APPLIED",
                "\"" + change + "\" ships against " + cause + ", expected to remove about " + expectedSavingMs
                        + "ms. Write the expected number down before the deploy — that is what makes the next "
                        + "step a check rather than a vibe. Keep the change alone, too: the refactor you "
                        + "noticed on the way and the second query you want to tune are separate deploys, "
                        + "because two changes at once means neither one has a measurement. And prefer the "
                        + "boring fix. Most real wins on a slow website are an index, a missing cache, one "
                        + "query instead of N, a smaller payload, a call moved off the request path or done in "
                        + "parallel — the exotic rewrite is usually further down the list than it feels",
                "«" + change + "» выкатывается против «" + cause + "» и должно убрать примерно "
                        + expectedSavingMs + "мс. Запишите ожидаемое число до деплоя — именно это превращает "
                        + "следующий шаг в проверку, а не в ощущение. И пусть изменение будет одиноким: "
                        + "рефакторинг, замеченный по дороге, и второй запрос, который хочется подтюнить, — "
                        + "это отдельные выкатки, потому что два изменения сразу означают, что измерения нет "
                        + "ни у одного. Предпочитайте скучное исправление. Большинство настоящих побед на "
                        + "медленном сайте — это индекс, недостающий кэш, один запрос вместо N, меньший объём "
                        + "ответа, вызов, унесённый с пути запроса или выполненный параллельно; экзотическое "
                        + "переписывание обычно стоит в списке ниже, чем кажется",
                List.of("fix"), state());
    }

    /** Takes the same measurement again. Anything else is a feeling, not a check. */
    public void remeasure(int millis) {
        tick(6);
        remeasured = true;
        beforeMs = endToEndMs() > 0 ? endToEndMs() : (distributionRead ? p95Ms : 0);
        afterMs = millis;
        gainPercent = beforeMs > 0 ? (beforeMs - afterMs) * 100 / beforeMs : 0;
        improved = gainPercent >= NOISE_PERCENT;
        boolean metBudget = budgetMs > 0 && afterMs <= budgetMs;
        stage = improved ? "verified" : "unchanged";

        if (!improved) {
            flag("no-measurable-gain");
            Trace.event("NO_IMPROVEMENT",
                    beforeMs + "ms -> " + afterMs + "ms: " + gainPercent + "%, which is noise. Good — you "
                            + "found out, and you found out because there was a before-number to compare "
                            + "against. Now resist the urge to add a second optimisation on top; revert, and go "
                            + "back to the split. There are only three reasons for this outcome and all of them "
                            + "are informative: the segment you fixed was not where the time was (check the "
                            + "ceiling you should have computed), the fix did not do what you thought (measure "
                            + "the segment itself, not the page), or the time simply moved somewhere else — "
                            + "which happens constantly, because removing one bottleneck just promotes the next "
                            + "one",
                    beforeMs + "мс -> " + afterMs + "мс: " + gainPercent + "%, то есть шум. И хорошо — вы это "
                            + "узнали, и узнали потому, что было число «до», с которым можно сравнить. Теперь "
                            + "не поддавайтесь желанию положить сверху вторую оптимизацию: откатитесь и "
                            + "вернитесь к разбиению. Причин у такого исхода всего три, и все они полезны: "
                            + "сегмент, который вы починили, был не там, где время (проверьте потолок, который "
                            + "стоило посчитать заранее), исправление сделало не то, что вы думали (измеряйте "
                            + "сам сегмент, а не страницу), или время просто переехало в другое место — а это "
                            + "происходит постоянно, потому что снятие одного узкого места лишь повышает в "
                            + "должности следующее",
                    List.of("result", "misstep:no-measurable-gain"), state());
            return;
        }

        Trace.event("IMPROVEMENT_VERIFIED",
                beforeMs + "ms -> " + afterMs + "ms: " + gainPercent + "% faster"
                        + (budgetMs > 0
                                ? (metBudget
                                        ? ", inside the " + budgetMs + "ms budget — the hunt has an end and "
                                                + "this is it"
                                        : ", still over the " + budgetMs + "ms budget, so split the new total "
                                                + "and go again")
                                : "")
                        + ". Verification means the same measurement, on the same page, at the same percentile, "
                        + "in production — not a local run, not a benchmark of the method you changed, and not "
                        + "\"it feels snappier\". Two more checks before you call it done: look at the whole "
                        + "distribution rather than the median, because a fix can improve p50 and leave the p99 "
                        + "that people complain about untouched; and glance at the graphs you were not "
                        + "optimising, since performance work moves load around and the win you just took may "
                        + "be sitting on somebody else's queue",
                beforeMs + "мс -> " + afterMs + "мс: быстрее на " + gainPercent + "%"
                        + (budgetMs > 0
                                ? (metBudget
                                        ? ", внутри бюджета " + budgetMs + "мс — у охоты есть конец, и это он"
                                        : ", всё ещё выше бюджета " + budgetMs + "мс, так что разбивайте новую "
                                                + "сумму и идите по кругу")
                                : "")
                        + ". Проверка означает то же измерение, на той же странице, на том же перцентиле, в "
                        + "проде — не локальный прогон, не бенчмарк изменённого метода и не «ощущается "
                        + "бодрее». Ещё две проверки, прежде чем считать дело закрытым: смотрите на всё "
                        + "распределение, а не на медиану, потому что исправление умеет улучшить p50 и не "
                        + "тронуть тот p99, на который жалуются; и загляните в графики, которые вы не "
                        + "оптимизировали, — работа над производительностью перекладывает нагрузку, и "
                        + "выигрыш, который вы только что взяли, может лежать в чужой очереди",
                List.of("result"), state());
    }

    /** Adds the measurement that makes the next hunt start from data. */
    public void guard(String guard) {
        tick(12);
        guards.add(guard);
        Trace.event("MONITORING_ADDED",
                "Follow-up: " + guard + ". The permanent output of a performance hunt is not the fix, it is "
                        + "the measurement you did not have when it started, because the same page will get "
                        + "slow again for a different reason. Three follow-ups pay for themselves: the "
                        + "percentile graph for this journey, so next time the answer to \"how slow, since "
                        + "when\" is a screen rather than a conversation; an alert on the budget you agreed, so "
                        + "a regression is noticed by a machine instead of a customer; and per-request timings "
                        + "with a shared request id, so the next split takes minutes. Performance is not a "
                        + "project you finish — it is a number somebody watches",
                "Последующая задача: " + guard + ". Постоянный результат охоты за производительностью — не "
                        + "исправление, а то измерение, которого у вас не было в начале: та же страница снова "
                        + "станет медленной, только по другой причине. Три задачи окупаются всегда: график "
                        + "перцентилей для этого сценария, чтобы в следующий раз ответ на «насколько медленно "
                        + "и с какого момента» был экраном, а не перепиской; алерт на согласованный бюджет, "
                        + "чтобы регрессию заметила машина, а не клиент; и тайминги каждого запроса с общим "
                        + "идентификатором, чтобы следующее разбиение заняло минуты. Производительность — это "
                        + "не проект, который заканчивают, а число, за которым кто-то следит",
                List.of("guard:" + guards.size()), state());
    }

    /** Reads the worksheet back: where the time was, what was proven, what it cost. */
    public void review() {
        if (fixChange != null && !remeasured) {
            flag("never-remeasured");
        }
        Level root = levels.get(0);
        Segment hotspot = current().hotspot();
        Trace.event("HUNT_REVIEW",
                site + " " + journey + " at T+" + minutes + "m: state " + stage + ", end-to-end "
                        + (root.total() > 0 ? root.total() + "ms" : "never measured")
                        + (budgetMs > 0 ? " against a " + budgetMs + "ms budget" : ", no budget agreed")
                        + ", " + levels.size() + " level(s) of split, "
                        + (hotspot == null ? "no hotspot named" : "deepest hotspot " + hotspot.name)
                        + ", cause " + (cause == null ? "not found" : cause)
                        + (missteps.isEmpty() ? ", no missteps" : ", missteps: " + String.join(", ", missteps))
                        + ". Read the levels back as the answer to the interview question, because that is what "
                        + "is being asked: not which bug it was, but whether you have a method. Turn \"slow\" "
                        + "into a page, a percentile and a target; measure the end-to-end total; split it and "
                        + "follow the biggest slice down until the remainder is a cause; check whether you are "
                        + "looking at slow work or at a long queue; price the fix against the whole request "
                        + "before you build it; then take the same measurement again — and leave the graph "
                        + "behind so the next one starts from data instead of from a feeling",
                site + " " + journey + " на T+" + minutes + "м: состояние " + stage + ", сквозное время "
                        + (root.total() > 0 ? root.total() + "мс" : "так и не измерено")
                        + (budgetMs > 0 ? " при бюджете " + budgetMs + "мс" : ", бюджет не согласован")
                        + ", уровней разбиения: " + levels.size() + ", "
                        + (hotspot == null ? "горячая точка не названа" : "глубочайшая горячая точка: " + hotspot.name)
                        + ", причина: " + (cause == null ? "не найдена" : cause)
                        + (missteps.isEmpty() ? ", промахов нет" : ", промахи: " + String.join(", ", missteps))
                        + ". Читайте уровни как ответ на вопрос собеседования, потому что спрашивают именно "
                        + "это: не какой это был баг, а есть ли у вас метод. Превратить «медленно» в страницу, "
                        + "перцентиль и цель; измерить сквозное время; разбить его и идти за самым большим "
                        + "срезом вниз, пока остаток не станет причиной; проверить, на что вы смотрите — на "
                        + "медленную работу или на длинную очередь; оценить исправление относительно всего "
                        + "запроса до того, как его писать; затем снять то же измерение снова — и оставить "
                        + "после себя график, чтобы следующая охота начиналась с данных, а не с ощущения",
                List.of(), state());
    }

    // -------------------------------------------------------------- helpers

    private Level current() {
        return levels.get(levels.size() - 1);
    }

    /** End-to-end milliseconds: the root split if measured, else the read p95. */
    private int endToEndMs() {
        int root = levels.get(0).total();
        if (root > 0) {
            return root;
        }
        return distributionRead ? p95Ms : 0;
    }

    private Segment findAnywhere(String name) {
        for (int i = levels.size() - 1; i >= 0; i--) {
            Segment segment = levels.get(i).find(name);
            if (segment != null) {
                return segment;
            }
        }
        return null;
    }

    private void flag(String misstep) {
        if (!missteps.contains(misstep)) {
            missteps.add(misstep);
        }
    }

    private void tick(int cost) {
        minutes += cost;
    }

    /** Moves the stage forward only; a later call never rewinds the worksheet. */
    private void advance(String next) {
        if (rank(next) > rank(stage)) {
            stage = next;
        }
    }

    private static int rank(String stage) {
        for (int i = 0; i < STAGES.length; i++) {
            if (STAGES[i].equals(stage)) {
                return i;
            }
        }
        // "unchanged" is a terminal verdict, not a step on the way.
        return STAGES.length;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("site", site);
        s.put("journey", journey);
        s.put("complaint", complaint);
        s.put("stage", stage);
        s.put("minutes", minutes);
        s.put("budgetMs", budgetMs);
        s.put("budgetLabel", targetLabel);
        s.put("endToEndMs", endToEndMs());

        if (distributionRead) {
            Map<String, Object> percentiles = new LinkedHashMap<>();
            percentiles.put("avg", avgMs);
            percentiles.put("p50", p50Ms);
            percentiles.put("p95", p95Ms);
            percentiles.put("p99", p99Ms);
            s.put("percentiles", percentiles);
        }

        List<Object> split = new ArrayList<>();
        for (Level level : levels) {
            int total = level.total();
            Segment hotspot = level.hotspot();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", level.name);
            item.put("totalMs", total);

            List<Object> parts = new ArrayList<>();
            for (Segment segment : level.segments) {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("name", segment.name);
                part.put("millis", segment.millis);
                part.put("share", total > 0 ? segment.millis * 100 / total : 0);
                part.put("tool", segment.tool);
                part.put("status", segment.cleared ? "cleared"
                        : segment.drilled ? "drilled"
                        : segment == hotspot ? "hotspot"
                        : "measured");
                part.put("note", segment.note);
                parts.add(part);
            }
            item.put("segments", parts);
            split.add(item);
        }
        s.put("levels", split);
        Segment deepest = current().hotspot();
        s.put("hotspot", deepest == null ? null : deepest.name);

        List<Object> questions = new ArrayList<>();
        for (String[] fact : facts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", fact[0]);
            item.put("answer", fact[1]);
            questions.add(item);
        }
        s.put("facts", questions);

        if (loadCompared) {
            Map<String, Object> load = new LinkedHashMap<>();
            load.put("idleMs", idleMs);
            load.put("peakMs", peakMs);
            load.put("queueing", peakMs >= idleMs * 2);
            s.put("load", load);
        }

        List<Object> readings = new ArrayList<>();
        for (Resource resource : resources) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", resource.name);
            item.put("reading", resource.reading);
            item.put("saturated", resource.saturated);
            readings.add(item);
        }
        s.put("resources", readings);

        List<Object> priced = new ArrayList<>();
        for (Ceiling entry : ceilings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("change", entry.change);
            item.put("segment", entry.segment);
            item.put("speedup", entry.speedup);
            item.put("savedMs", entry.savedMs);
            item.put("gainPercent", entry.gainPercent);
            item.put("worthIt", entry.gainPercent >= NOISE_PERCENT);
            priced.add(item);
        }
        s.put("ceilings", priced);

        s.put("missingSignals", new ArrayList<>(missingSignals));
        s.put("cause", cause);
        s.put("causeEvidence", causeEvidence);

        if (fixChange != null) {
            Map<String, Object> fix = new LinkedHashMap<>();
            fix.put("change", fixChange);
            fix.put("expectedMs", fixExpectedMs);
            fix.put("blind", cause == null);
            s.put("fix", fix);
        }

        if (remeasured) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("beforeMs", beforeMs);
            result.put("afterMs", afterMs);
            result.put("gainPercent", gainPercent);
            result.put("improved", improved);
            result.put("metBudget", budgetMs > 0 && afterMs <= budgetMs);
            s.put("result", result);
        }

        s.put("guards", new ArrayList<>(guards));
        s.put("missteps", new ArrayList<>(missteps));
        return s;
    }
}
