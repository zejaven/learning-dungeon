package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the incident that starts with "the service is not
 * responding" — no answer at all, rather than a wrong answer or a slow one.
 *
 * <p>The model is a triage worksheet with a stopwatch. It walks the procedure in
 * the order that actually pays off — probe from the outside in, capture the
 * evidence that a restart would destroy, read where the threads are, restore
 * service, only then name the cause, fix, verify and leave a guard behind — and
 * it charges every action minutes, so doing them out of order is visible rather
 * than argued about:
 * <ul>
 *   <li>{@link #probe(String, String, String, String)} asks the only question that
 *       partitions the search space: <em>which kind</em> of "not responding" is
 *       this — nothing listening, listening but silent, health green while the
 *       real endpoint hangs, or merely slow — and {@link #classify()} collapses
 *       the probes into that verdict;</li>
 *   <li>{@link #restartFirst(String)} is the reflex: it restores service and
 *       deletes the cause, which is why the same outage returns at 03:00;</li>
 *   <li>{@link #capture(String, String)} takes the artifacts that only exist
 *       while the process is still hung;</li>
 *   <li>{@link #threads(String, int, String, String)}, {@link #deadlock(String, String, String)},
 *       {@link #pool(String, int, int, int)}, {@link #gc(int, int)} and
 *       {@link #resource(String, String, boolean)} read the evidence, and
 *       {@link #capacity(int, int, int)} does the arithmetic that explains why a
 *       thread pool disappears in seconds;</li>
 *   <li>{@link #restore(String, String)}, {@link #confirm(String, String)},
 *       {@link #fix(String)}, {@link #verify(String, boolean)} and
 *       {@link #guard(String)} close it.</li>
 * </ul>
 *
 * <p>The missteps are not separate methods — they are consequences of the order
 * the methods are called in. Restarting before anything is captured is recorded,
 * and every later capture is marked lost; shipping a change with no confirmed
 * cause is a {@code BLIND_FIX}; finding the root cause while users are still
 * down is flagged. Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualHungService {

    /** Probe layers, outside in. Each one that answers removes a whole class of cause. */
    private static final String[] LAYERS = {"instances", "dns", "tcp", "health", "endpoint", "inside"};

    /** One probe against one layer of the stack. */
    private static final class Probe {
        final String layer;
        final String command;
        final String outcome;
        final String detail;

        Probe(String layer, String command, String outcome, String detail) {
            this.layer = layer;
            this.command = command;
            this.outcome = outcome;
            this.detail = detail;
        }
    }

    /** One artifact that only exists while the process is still hung. */
    private static final class Artifact {
        final String name;
        final String how;
        final boolean lost;

        Artifact(String name, String how, boolean lost) {
            this.name = name;
            this.how = how;
            this.lost = lost;
        }
    }

    /** One group of threads from the dump, collapsed by where they are parked. */
    private static final class StackGroup {
        final String name;
        final int count;
        final String state;
        final String frame;

        StackGroup(String name, int count, String state, String frame) {
            this.name = name;
            this.count = count;
            this.state = state;
            this.frame = frame;
        }
    }

    /** One bounded resource requests queue for. */
    private static final class Pool {
        final String name;
        final int inUse;
        final int max;
        final int queued;

        Pool(String name, int inUse, int max, int queued) {
            this.name = name;
            this.inUse = inUse;
            this.max = max;
            this.queued = queued;
        }

        boolean saturated() {
            return inUse >= max;
        }
    }

    /** One reading of something that is not a thread and not a pool. */
    private static final class Reading {
        final String name;
        final String value;
        final boolean alarming;

        Reading(String name, String value, boolean alarming) {
            this.name = name;
            this.value = value;
            this.alarming = alarming;
        }
    }

    private final String service;
    private final String symptom;
    private final List<Probe> probes = new ArrayList<>();
    private final List<Artifact> evidence = new ArrayList<>();
    private final List<StackGroup> threadGroups = new ArrayList<>();
    private final List<Pool> pools = new ArrayList<>();
    private final List<Reading> readings = new ArrayList<>();
    private final List<String> guards = new ArrayList<>();
    private final List<String> missteps = new ArrayList<>();

    private int minutes;
    private String stage = "alarm";
    private String failureMode = "unknown";
    private boolean processRestarted;

    private int[] gcReading;
    private Map<String, Object> deadlock;
    private Map<String, Object> capacity;

    private String mitigationAction;
    private String mitigationEffect;
    private String rootCause;
    private String causeEvidence;
    private String fixApplied;
    private Boolean fixWasBlind;
    private Boolean verified;

    private VisualHungService(String service, String symptom) {
        this.service = service;
        this.symptom = symptom;
    }

    /** The alarm fires. "Not responding" is a symptom, not yet a failure mode. */
    public static VisualHungService alarm(String service, String symptom) {
        VisualHungService incident = new VisualHungService(service, symptom);
        Trace.event("SERVICE_UNRESPONSIVE",
                service + ": " + symptom + ". Note what you do not know yet, because it is almost everything. "
                        + "\"Not responding\" is four completely different failures wearing the same shirt: "
                        + "nothing is listening on the port (the process died, crashed on boot, or the container "
                        + "is restarting); something is listening but never writes a byte back (the process is "
                        + "alive and no thread ever reaches your handler); the health endpoint is green while "
                        + "the real endpoint hangs (the check checks nothing that matters); or it answers, just "
                        + "far outside the client's timeout, which is a latency problem in a timeout costume. "
                        + "They share zero causes and zero fixes, so the first minute is not for theories — it "
                        + "is for finding out which one you have",
                service + ": " + symptom + ". Обратите внимание, чего вы пока не знаете, — а не знаете почти "
                        + "ничего. «Не отвечает» — это четыре совершенно разных сбоя в одной рубашке: на порту "
                        + "никто не слушает (процесс умер, упал при старте или контейнер перезапускается); "
                        + "кто-то слушает, но не пишет в ответ ни байта (процесс жив, и ни один поток не "
                        + "доходит до вашего обработчика); health-эндпоинт зелёный, а настоящий эндпоинт висит "
                        + "(проверка проверяет то, что ничего не значит); либо ответ приходит, просто далеко за "
                        + "пределами клиентского таймаута — это проблема задержки в костюме таймаута. У них ноль "
                        + "общих причин и ноль общих исправлений, поэтому первая минута нужна не для теорий, а "
                        + "чтобы выяснить, какой из четырёх случаев у вас",
                List.of("alarm"), incident.state());
        return incident;
    }

    // ---------------------------------------------------------------- probe

    /**
     * Probes one layer, outside in. {@code outcome} is one of {@code ok},
     * {@code refused}, {@code timeout}, {@code slow}, {@code error},
     * {@code partial}; every answer removes a class of cause.
     */
    public void probe(String layer, String command, String outcome, String detail) {
        tick(2);
        probes.add(new Probe(layer, command, outcome, detail));
        if ("alarm".equals(stage)) {
            stage = "probing";
        }
        Trace.event("LAYER_PROBED",
                layer + ": " + command + " -> " + outcome + " (" + detail + "). " + probeLesson(outcome)
                        + " Work outside in and stop at the first layer that fails, because every layer that "
                        + "answers deletes a whole family of hypotheses for free. Do it with the dumbest tools "
                        + "you have — a DNS lookup, a TCP connect, one curl with an explicit timeout, then the "
                        + "same curl from inside the container against localhost. That last one splits the "
                        + "world in half: if localhost answers and the outside does not, the process is fine "
                        + "and the problem is in front of it — the load balancer, the ingress, the security "
                        + "group, the service mesh, DNS",
                layer + ": " + command + " -> " + outcome + " (" + detail + "). " + probeLessonRu(outcome)
                        + " Идите снаружи внутрь и останавливайтесь на первом слое, который не отвечает: каждый "
                        + "ответивший слой бесплатно вычёркивает целое семейство гипотез. Делайте это самыми "
                        + "тупыми инструментами: DNS-запрос, TCP-соединение, один curl с явным таймаутом, а "
                        + "затем тот же curl изнутри контейнера на localhost. Последний делит мир пополам: если "
                        + "localhost отвечает, а снаружи — нет, то процесс в порядке и проблема перед ним — "
                        + "балансировщик, ingress, группа безопасности, service mesh, DNS",
                List.of("probe:" + layer), state());
    }

    /** Collapses the probes into one of the failure modes — the verdict the next hour depends on. */
    public void classify() {
        tick(1);
        failureMode = deriveMode();
        stage = "classified";
        Trace.event("FAILURE_CLASSIFIED",
                "This is " + failureMode + ". " + modeLesson(failureMode)
                        + " Say the mode out loud before you touch anything, because it decides both the next "
                        + "command you run and the shape of the fix. The expensive mistake in this whole topic "
                        + "is skipping this line: an engineer who assumes \"the app is stuck\" when nothing is "
                        + "listening spends an hour reading thread dumps of a process that is not there, and an "
                        + "engineer who assumes \"the process died\" when it is alive and blocked restarts it "
                        + "and learns nothing",
                "Это «" + failureMode + "». " + modeLessonRu(failureMode)
                        + " Проговорите режим вслух, прежде чем что-то трогать: он определяет и следующую "
                        + "команду, и форму исправления. Дорогая ошибка во всей этой теме — пропустить эту "
                        + "строку: инженер, который решил «приложение зависло», когда на порту никого нет, "
                        + "потратит час на чтение дампов потоков несуществующего процесса, а инженер, который "
                        + "решил «процесс умер», когда тот жив и заблокирован, перезапустит его и не узнает "
                        + "ничего",
                List.of("mode:" + failureMode), state());
    }

    // ------------------------------------------------------------- evidence

    /** The reflex. It restores service, and it deletes the only copy of the cause. */
    public void restartFirst(String action) {
        tick(2);
        processRestarted = true;
        mitigationAction = action;
        mitigationEffect = "traffic recovers; the hung process and everything it knew is gone";
        stage = "restored";
        flag("restarted-blind");
        Trace.event("RESTARTED_BLIND",
                action + " — and it works. Requests flow again inside a minute, which is exactly why this is "
                        + "the hardest habit to argue with. But a hung JVM is a crime scene, and a restart is "
                        + "the only action in an incident that is simultaneously the correct mitigation and the "
                        + "destruction of all the evidence: the thread stacks that showed where every worker "
                        + "was parked, the heap that showed what filled it, the open sockets, the pool "
                        + "counters. None of it is recoverable, and none of it will be reconstructed from "
                        + "logs. So the rule is not \"do not restart\" — you will restart, and often you should "
                        + "restart first. The rule is that three commands and about twenty seconds stand "
                        + "between you and keeping the cause: dump the threads, dump the heap if memory is a "
                        + "suspect, then restart. Skip them and the same outage returns at 03:00 with the same "
                        + "amount of information as this time, which is none",
                action + " — и это работает. Запросы идут снова в течение минуты, и именно поэтому с этой "
                        + "привычкой труднее всего спорить. Но зависшая JVM — это место преступления, а "
                        + "перезапуск — единственное действие в инциденте, которое одновременно является "
                        + "правильным смягчением и уничтожением всех улик: стеки потоков, показывавшие, где "
                        + "припаркован каждый воркер, куча, показывавшая, чем она забита, открытые сокеты, "
                        + "счётчики пулов. Ничего из этого не восстановить и ничего не реконструировать по "
                        + "логам. Поэтому правило не «не перезапускать» — вы перезапустите, и часто именно "
                        + "первым делом. Правило в том, что между вами и сохранённой причиной стоят три "
                        + "команды и секунд двадцать: снять дамп потоков, снять дамп кучи, если память под "
                        + "подозрением, и только потом перезапускать. Пропустите их — и та же авария вернётся "
                        + "в 03:00 с тем же объёмом информации, что и сейчас, то есть с нулевым",
                List.of("misstep:restarted-blind"), state());
    }

    /** Takes one artifact that exists only while the process is still hung. */
    public void capture(String artifact, String how) {
        tick(2);
        boolean lost = processRestarted;
        evidence.add(new Artifact(artifact, how, lost));
        if (lost) {
            flag("evidence-lost");
        } else if ("probing".equals(stage) || "classified".equals(stage) || "alarm".equals(stage)) {
            stage = "evidence";
        }
        if (lost) {
            Trace.event("EVIDENCE_LOST",
                    artifact + " cannot be taken any more: " + how + " needs the process that was just "
                            + "replaced. The new process is healthy and knows nothing — its threads are idle, "
                            + "its heap is empty, its pools are full. Everything you could have learned about "
                            + "why the old one stopped is now permanently unavailable, and the incident is "
                            + "therefore not closed but postponed. Write down that you have no evidence: it is "
                            + "the most important sentence in the postmortem, because it is the one that "
                            + "justifies pre-writing the capture script so the next person does not have to "
                            + "remember it at 03:00",
                    artifact + " больше не снять: " + how + " требует того процесса, который только что "
                            + "заменили. Новый процесс здоров и не знает ничего — потоки простаивают, куча "
                            + "пуста, пулы полны. Всё, что можно было узнать о том, почему остановился старый, "
                            + "теперь недоступно навсегда, и инцидент, соответственно, не закрыт, а отложен. "
                            + "Запишите, что улик у вас нет: это самая важная фраза в разборе, потому что "
                            + "именно она оправдывает заранее написанный скрипт сбора артефактов — чтобы "
                            + "следующему не пришлось вспоминать это в 03:00",
                    List.of("evidence:" + evidence.size(), "misstep:evidence-lost"), state());
            return;
        }
        Trace.event("EVIDENCE_CAPTURED",
                artifact + " captured with " + how + ". This is the twenty seconds that decide whether the "
                        + "incident produces an answer or a rumour. The short list is always the same: three "
                        + "thread dumps five seconds apart (one dump shows where threads are, three show "
                        + "whether they are moving — a thread parked on the same frame in all three is stuck, "
                        + "the same count of threads on different frames is merely busy); a heap histogram, "
                        + "and a full heap dump if memory is a suspect and you can afford the pause; the GC "
                        + "log, which is free because it was already being written; and a snapshot of the "
                        + "pool and queue gauges. Take them from a hung instance, not from a healthy one, and "
                        + "if the cluster has both, keep one hung instance out of the load balancer and alive "
                        + "so you can study it after service is restored",
                artifact + " снят с помощью " + how + ". Это те двадцать секунд, которые решают, породит "
                        + "инцидент ответ или слух. Короткий список всегда один и тот же: три дампа потоков с "
                        + "интервалом в пять секунд (один дамп показывает, где потоки, три — движутся ли они: "
                        + "поток на одном и том же кадре во всех трёх застрял, то же число потоков на разных "
                        + "кадрах просто занято); гистограмма кучи, а если под подозрением память и вы можете "
                        + "позволить себе паузу — полный дамп кучи; лог GC, который бесплатен, потому что и так "
                        + "писался; и снимок показаний пулов и очередей. Снимайте их с зависшего экземпляра, а "
                        + "не со здорового, и если в кластере есть и те и другие — выведите один зависший из "
                        + "балансировки, но оставьте живым, чтобы изучить его после восстановления сервиса",
                List.of("evidence:" + evidence.size()), state());
    }

    // ------------------------------------------------------------ diagnosis

    /** Reads one group of the thread dump: how many threads, in which state, parked where. */
    public void threads(String group, int count, String state, String frame) {
        tick(3);
        threadGroups.add(new StackGroup(group, count, state, frame));
        if (!"restored".equals(stage) && !"fixed".equals(stage)) {
            stage = "diagnosing";
        }
        int share = shareOf(count);
        Trace.event("THREADS_READ",
                count + " thread(s) in " + group + ", state " + state + ", parked at " + frame + " — "
                        + share + "% of the dump. " + threadLesson(state, share)
                        + " A thread dump is not read line by line, it is read by counting: group the stacks by "
                        + "their top frames and look at the biggest group. That group IS the outage. The four "
                        + "shapes worth memorising are a crowd in TIMED_WAITING/RUNNABLE inside a socket read "
                        + "(you are waiting on somebody else and your timeout is missing or absurd), a crowd "
                        + "in BLOCKED on one monitor (contention or a deadlock behind it), a crowd in WAITING "
                        + "on a pool's condition (you ran out of connections, not of threads), and a crowd "
                        + "RUNNABLE in your own code with the CPU pinned (a hot loop, a pathological regex, or "
                        + "GC eating the machine)",
                count + " поток(ов) в " + group + ", состояние " + state + ", припаркованы на " + frame + " — "
                        + share + "% дампа. " + threadLessonRu(state, share)
                        + " Дамп потоков читают не построчно, а счётом: сгруппируйте стеки по верхним кадрам и "
                        + "посмотрите на самую большую группу. Эта группа И ЕСТЬ авария. Четыре формы, которые "
                        + "стоит запомнить: толпа в TIMED_WAITING/RUNNABLE внутри чтения сокета (вы ждёте "
                        + "кого-то другого, а таймаут отсутствует или абсурден), толпа в BLOCKED на одном "
                        + "мониторе (конкуренция за блокировку, а за ней, возможно, взаимоблокировка), толпа в "
                        + "WAITING на условии пула (у вас кончились соединения, а не потоки) и толпа RUNNABLE в "
                        + "вашем собственном коде с загруженным процессором (горячий цикл, патологическое "
                        + "регулярное выражение или GC, съедающий машину)",
                List.of("threads:" + group), state());
    }

    /** The dump's own verdict: the JVM prints "Found one Java-level deadlock" for you. */
    public void deadlock(String threadA, String threadB, String monitors) {
        tick(3);
        Map<String, Object> found = new LinkedHashMap<>();
        found.put("threadA", threadA);
        found.put("threadB", threadB);
        found.put("monitors", monitors);
        deadlock = found;
        stage = "diagnosing";
        Trace.event("DEADLOCK_FOUND",
                "The dump ends with \"Found one Java-level deadlock\": " + threadA + " and " + threadB
                        + " each hold what the other needs (" + monitors + "). This is the one diagnosis you "
                        + "never have to argue about, because the JVM did the proof itself — it walks the "
                        + "monitor ownership graph and prints the cycle. It also explains the symptom "
                        + "perfectly: two threads are frozen for ever, they never release what they hold, so "
                        + "every request that needs the same locks joins them, and a service with a bounded "
                        + "worker pool goes from \"two threads stuck\" to \"nothing responds\" in as long as it "
                        + "takes to fill the pool. Two things are true of deadlocks and both matter: no timeout "
                        + "saves you, because a monitor has no timeout, and no restart fixes them, because the "
                        + "lock order that caused it is still in the code. The fix is always the same shape — "
                        + "acquire in one global order, or hold one lock instead of two, or use a lock that can "
                        + "time out",
                "Дамп заканчивается строкой «Found one Java-level deadlock»: " + threadA + " и " + threadB
                        + " держат каждый то, что нужно другому (" + monitors + "). Это единственный диагноз, о "
                        + "котором не нужно спорить, потому что доказательство построила сама JVM: она обходит "
                        + "граф владения мониторами и печатает цикл. Он же идеально объясняет симптом: два "
                        + "потока замерли навсегда, они никогда не отпустят то, что держат, поэтому каждый "
                        + "запрос, которому нужны те же блокировки, присоединяется к ним, и сервис с "
                        + "ограниченным пулом воркеров проходит путь от «застряли два потока» до «ничего не "
                        + "отвечает» ровно за время наполнения пула. Про взаимоблокировки верны два "
                        + "утверждения, и оба важны: никакой таймаут вас не спасёт, потому что у монитора нет "
                        + "таймаута, и никакой перезапуск их не чинит, потому что порядок захвата, который к "
                        + "ним привёл, остался в коде. Исправление всегда одной формы — захватывать в едином "
                        + "глобальном порядке, держать одну блокировку вместо двух или брать блокировку, "
                        + "умеющую истекать по времени",
                List.of("deadlock"), state());
    }

    /** Reads a bounded pool: in use, maximum, and how many requests are queued for it. */
    public void pool(String name, int inUse, int max, int queued) {
        tick(2);
        Pool entry = new Pool(name, inUse, max, queued);
        pools.add(entry);
        Trace.event("POOL_READ",
                name + ": " + inUse + "/" + max + " in use, " + queued + " waiting. "
                        + (entry.saturated()
                                ? "Saturated — and a saturated pool is what turns one slow dependency into a "
                                        + "dead service. Nothing here is broken in the sense of throwing an "
                                        + "exception: every borrowed connection or worker is doing exactly what "
                                        + "it was told, and the " + queued + " requests behind them are simply "
                                        + "in a queue whose only exit is a timeout."
                                : "Healthy — which is a real finding, not a null result. If neither the worker "
                                        + "pool nor the connection pool is full, then requests are reaching "
                                        + "your code and your code is choosing to sit there, so look at what "
                                        + "the threads themselves are doing rather than at what they are "
                                        + "queueing for.")
                        + " Read every bounded thing between the socket and the database, because a request "
                        + "passes through several and only the tightest one matters: the accept queue, the "
                        + "worker pool, the connection pool, and any semaphore or rate limiter in between. And "
                        + "read the queue length, not just the utilisation — 100% used with nobody waiting is "
                        + "a well-sized pool, while 100% used with a queue is the definition of an outage in "
                        + "progress",
                name + ": " + inUse + "/" + max + " занято, " + queued + " в очереди. "
                        + (entry.saturated()
                                ? "Насыщен — а насыщенный пул как раз и превращает одну медленную зависимость "
                                        + "в мёртвый сервис. Здесь ничего не «сломано» в смысле выброшенного "
                                        + "исключения: каждое занятое соединение или воркер делает ровно то, "
                                        + "что ему велели, а " + queued + " запросов за ними просто стоят в "
                                        + "очереди, единственный выход из которой — таймаут."
                                : "Здоров — и это настоящая находка, а не пустой результат. Если не полон ни "
                                        + "пул воркеров, ни пул соединений, значит запросы доходят до вашего "
                                        + "кода и ваш код сам решает там сидеть: смотрите, что делают сами "
                                        + "потоки, а не за чем они стоят в очереди.")
                        + " Читайте всё ограниченное между сокетом и базой, потому что запрос проходит через "
                        + "несколько таких мест, а значение имеет только самое узкое: очередь accept, пул "
                        + "воркеров, пул соединений и любой семафор или ограничитель скорости между ними. И "
                        + "читайте длину очереди, а не только занятость: 100% занятости без ожидающих — это "
                        + "правильно подобранный пул, а 100% занятости с очередью — определение аварии в "
                        + "процессе",
                List.of("pool:" + name), state());
    }

    /** Reads the garbage collector: a JVM in GC thrash is alive, busy, and answering nobody. */
    public void gc(int pausePercent, int heapAfterGcPercent) {
        tick(2);
        gcReading = new int[]{pausePercent, heapAfterGcPercent};
        boolean thrashing = pausePercent >= 50 && heapAfterGcPercent >= 90;
        Trace.event("GC_READ",
                "GC: " + pausePercent + "% of wall clock in pauses, heap " + heapAfterGcPercent
                        + "% full immediately after a full collection. "
                        + (thrashing
                                ? "That is GC thrash, and it is the most convincing impostor in this whole "
                                        + "topic: the process is up, the port is open, the CPU is at 100%, "
                                        + "nothing has thrown, and no request completes. The heap is full of "
                                        + "live objects, so every collection reclaims almost nothing and is "
                                        + "immediately followed by another one, and application threads run "
                                        + "for milliseconds between pauses that last seconds. It ends either "
                                        + "in OutOfMemoryError or, worse, in this — an outage with no "
                                        + "exception in the log."
                                : "Not the cause here — pauses are a small share of the clock and the heap "
                                        + "breathes after a collection, which means live data fits and "
                                        + "collections are doing real work. Rule it out with the numbers and "
                                        + "move on, rather than adding heap because somebody suggested it.")
                        + " The number that matters is not \"how much heap is used\" — a healthy JVM runs near "
                        + "its ceiling by design — but how full it still is right after a full collection, and "
                        + "what share of the wall clock the pauses take",
                "GC: " + pausePercent + "% времени в паузах, куча заполнена на " + heapAfterGcPercent
                        + "% сразу после полной сборки. "
                        + (thrashing
                                ? "Это GC-трэшинг, и он самый убедительный самозванец во всей теме: процесс "
                                        + "жив, порт открыт, процессор загружен на 100%, ничего не выброшено — "
                                        + "и ни один запрос не завершается. Куча забита живыми объектами, "
                                        + "поэтому каждая сборка освобождает почти ничего и немедленно "
                                        + "сменяется следующей, а потоки приложения работают миллисекунды "
                                        + "между паузами длиной в секунды. Заканчивается это либо "
                                        + "OutOfMemoryError, либо, что хуже, вот этим — аварией без единого "
                                        + "исключения в логе."
                                : "Здесь причина не в этом: паузы занимают малую долю времени, а куча дышит "
                                        + "после сборки — значит живые данные помещаются и сборки делают "
                                        + "полезную работу. Исключите это числами и идите дальше, а не "
                                        + "добавляйте память, потому что кто-то предложил.")
                        + " Значение имеет не «сколько кучи занято» — здоровая JVM по устройству работает у "
                        + "своего потолка, — а то, насколько она заполнена сразу после полной сборки и какую "
                        + "долю времени занимают паузы",
                List.of("gc"), state());
    }

    /** Reads something that is neither a thread nor a pool: CPU, descriptors, disk, network. */
    public void resource(String name, String value, boolean alarming) {
        tick(2);
        readings.add(new Reading(name, value, alarming));
        Trace.event("RESOURCE_READ",
                name + ": " + value + (alarming ? " — alarming." : " — normal.")
                        + " These are the causes that live below your code and are therefore never in your "
                        + "stack traces. A full disk stops the log writes that every request makes and the "
                        + "service freezes without a single exception being visible, because writing the "
                        + "exception is what fails. Exhausted file descriptors turn every new connection into "
                        + "a refusal while existing ones keep working, which looks exactly like a partial "
                        + "outage. A container throttled by its CPU quota runs at a fraction of the speed the "
                        + "graph implies. And a node with a failing disk or a saturated network card takes its "
                        + "pods with it. Check them early precisely because they are cheap to check and nothing "
                        + "in the application will ever point at them",
                name + ": " + value + (alarming ? " — тревожно." : " — норма.")
                        + " Это причины, живущие ниже вашего кода, и поэтому их никогда нет в ваших стек-"
                        + "трейсах. Заполненный диск останавливает запись в лог, которую делает каждый запрос, "
                        + "и сервис замирает без единого видимого исключения — потому что падает именно запись "
                        + "этого исключения. Исчерпанные файловые дескрипторы превращают каждое новое "
                        + "соединение в отказ, пока уже открытые продолжают работать, и выглядит это ровно как "
                        + "частичная авария. Контейнер, придушенный квотой CPU, работает на доле той скорости, "
                        + "которую подразумевает график. А узел с умирающим диском или забитой сетевой картой "
                        + "утаскивает за собой все свои поды. Проверяйте их рано именно потому, что проверка "
                        + "дешёвая, а изнутри приложения на них никогда ничто не укажет",
                List.of("resource:" + name), state());
    }

    /**
     * Does the arithmetic that explains why a bounded pool disappears in seconds:
     * capacity = threads / service time, against the arrival rate.
     */
    public void capacity(int threads, int serviceMillis, int arrivalPerSecond) {
        tick(3);
        int capacityPerSecond = serviceMillis > 0 ? threads * 1000 / serviceMillis : 0;
        boolean overloaded = arrivalPerSecond > capacityPerSecond;
        int exhaustMillis = arrivalPerSecond > 0 ? threads * 1000 / arrivalPerSecond : 0;
        Map<String, Object> math = new LinkedHashMap<>();
        math.put("threads", threads);
        math.put("serviceMillis", serviceMillis);
        math.put("arrivalPerSecond", arrivalPerSecond);
        math.put("capacityPerSecond", capacityPerSecond);
        math.put("deficitPerSecond", arrivalPerSecond - capacityPerSecond);
        math.put("exhaustMillis", exhaustMillis);
        math.put("overloaded", overloaded);
        capacity = math;

        Trace.event("SATURATION_COMPUTED",
                threads + " workers x " + serviceMillis + "ms per request = " + capacityPerSecond
                        + " req/s of capacity, against " + arrivalPerSecond + " req/s arriving"
                        + (overloaded
                                ? "; the pool is fully occupied " + exhaustMillis + "ms after the dependency "
                                        + "slows down, and every request after that only ever waits."
                                : "; capacity covers the arrival rate, so a queue that is growing anyway is "
                                        + "not being caused by this pool.")
                        + " This one line of arithmetic — Little's law with the names taken out — is what makes "
                        + "the failure feel inevitable instead of mysterious. Concurrency equals arrival rate "
                        + "times service time, so when a downstream call goes from 50ms to " + serviceMillis
                        + "ms, the number of threads needed to keep up multiplies by exactly the same factor, "
                        + "and your pool did not multiply. Two consequences interviewers listen for: a bigger "
                        + "pool buys you seconds, not a fix, and it doubles the load on the dependency that is "
                        + "already failing; and the actual lever is service time, which is what a timeout puts "
                        + "a hard ceiling on — a 1s timeout means a slow dependency can never consume more "
                        + "than one second of each worker's time",
                threads + " воркеров x " + serviceMillis + "мс на запрос = " + capacityPerSecond
                        + " запр/с пропускной способности против " + arrivalPerSecond + " запр/с приходящих"
                        + (overloaded
                                ? "; пул занят целиком через " + exhaustMillis + "мс после замедления "
                                        + "зависимости, и каждый следующий запрос уже только ждёт."
                                : "; способности хватает на входящий поток, значит растущую очередь вызывает "
                                        + "не этот пул.")
                        + " Эта единственная строчка арифметики — закон Литтла без имён — превращает сбой из "
                        + "загадочного в неизбежный. Параллелизм равен интенсивности прихода, умноженной на "
                        + "время обслуживания, поэтому когда вызов вниз по стеку идёт не 50мс, а " + serviceMillis
                        + "мс, число потоков, нужное чтобы успевать, умножается ровно на тот же множитель, — а "
                        + "ваш пул не умножился. Два следствия, которых ждут на собеседовании: больший пул "
                        + "покупает секунды, а не исправление, и вдвое увеличивает нагрузку на и без того "
                        + "падающую зависимость; настоящий же рычаг — время обслуживания, а жёсткий потолок на "
                        + "него ставит таймаут: таймаут в 1с означает, что медленная зависимость никогда не "
                        + "займёт больше секунды времени каждого воркера",
                List.of("capacity"), state());
    }

    // ------------------------------------------------- restore, fix, verify

    /** Restores service with the evidence already in hand. Not the same act as fixing it. */
    public void restore(String action, String effect) {
        tick(4);
        mitigationAction = action;
        mitigationEffect = effect;
        stage = "restored";
        Trace.event("SERVICE_RESTORED",
                action + " -> " + effect + " at T+" + minutes + "m. Users are served and the bug is still in "
                        + "the code: two separate facts, and treating them as one is how a five-minute outage "
                        + "becomes a two-hour one in either direction — by debugging while customers are down, "
                        + "or by walking away once the graph is green. Mitigation is deliberately dumb, "
                        + "reversible and does not require understanding: restart the hung instances (you have "
                        + "the dumps now), roll back the release, turn the feature flag off, drain and replace "
                        + "the one bad pod, cut the traffic that is hurting you, or fail fast on the "
                        + "dependency that is dragging you down so at least the requests that do not need it "
                        + "succeed. Pick the reversible option every time, and if you have several hung "
                        + "instances, restart all but one",
                action + " -> " + effect + " на T+" + minutes + "м. Пользователей обслуживают, а баг всё ещё в "
                        + "коде: это два разных факта, и их смешение растягивает пятиминутную аварию до "
                        + "двухчасовой в любую из сторон — либо вы отлаживаете, пока клиенты лежат, либо "
                        + "уходите, как только график позеленел. Смягчение намеренно тупое, обратимое и не "
                        + "требует понимания: перезапустить зависшие экземпляры (дампы у вас уже есть), "
                        + "откатить релиз, выключить фича-флаг, вывести и заменить один плохой под, срезать "
                        + "трафик, который вас убивает, или начать быстро падать на тянущей вниз зависимости, "
                        + "чтобы хотя бы запросы, которым она не нужна, проходили. Каждый раз выбирайте "
                        + "обратимый вариант, а если зависших экземпляров несколько — перезапустите все, кроме "
                        + "одного",
                List.of("mitigation"), state());
    }

    /** Names the cause, with the evidence that made it more than a story. */
    public void confirm(String cause, String evidenceForIt) {
        tick(5);
        rootCause = cause;
        causeEvidence = evidenceForIt;
        if (mitigationAction == null) {
            flag("left-users-down");
        }
        if (!"restored".equals(stage)) {
            stage = "diagnosed";
        }
        Trace.event("ROOT_CAUSE_CONFIRMED",
                "Root cause: " + cause + ". Evidence: " + evidenceForIt + ". The bar for that word is that the "
                        + "cause explains the whole shape of the incident and not just its headline — why it "
                        + "started when it started, why every instance stopped rather than one, why nothing "
                        + "was thrown into the log, and why it recovered when it did. A story that explains "
                        + "the hang but not the timing is the second-most-likely story, and shipping a fix for "
                        + "it costs you the outage twice. Two questions separate a cause from a symptom here. "
                        + "Ask \"why did that stop everything?\" — a dependency being slow is not an outage on "
                        + "its own; it becomes one only through a bounded pool and a missing timeout, and that "
                        + "second half is yours to fix and the part that will repeat with a different "
                        + "dependency next quarter. Then ask \"why did nothing tell us?\", because a service "
                        + "that goes silent while its own dashboards stay green has a second defect underneath "
                        + "the first one",
                "Корневая причина: " + cause + ". Улика: " + evidenceForIt + ". Планка для этого слова такова: "
                        + "причина объясняет всю форму инцидента, а не только заголовок — почему началось "
                        + "тогда, когда началось, почему встали все экземпляры, а не один, почему в лог ничего "
                        + "не выброшено и почему всё восстановилось именно тогда. Версия, объясняющая зависание, "
                        + "но не время, — это вторая по правдоподобию версия, и выкатка исправления под неё "
                        + "стоит вам аварии дважды. Причину от симптома здесь отделяют два вопроса. Спросите "
                        + "«почему это остановило всё?» — медленная зависимость сама по себе не авария, ею она "
                        + "становится только через ограниченный пул и отсутствующий таймаут, и вот эта вторая "
                        + "половина — ваша, и именно она повторится в следующем квартале с другой "
                        + "зависимостью. Затем спросите «почему нам никто не сказал?»: сервис, замолчавший при "
                        + "зелёных собственных дашбордах, имеет второй дефект под первым",
                List.of("rootcause"), state());
    }

    /** Changes the system. Whether that is engineering or gambling depends on the order. */
    public void fix(String change) {
        tick(15);
        fixApplied = change;
        fixWasBlind = rootCause == null;
        if (rootCause == null) {
            flag("fixed-without-a-cause");
            Trace.event("BLIND_FIX",
                    "Shipping \"" + change + "\" with nothing confirmed. It feels like progress the whole time, "
                            + "and in this particular topic it has a favourite disguise: raising a limit. "
                            + "Doubling the worker pool, doubling the connection pool, adding instances — each "
                            + "of them is a plausible-sounding change that, when the real cause is a "
                            + "dependency that stopped answering, gives you more threads to lose and sends "
                            + "more load at the thing that is already failing. The costs are the usual three. "
                            + "You have added a second unverified change to a system that is already "
                            + "misbehaving, so if the symptoms move you no longer know which change moved "
                            + "them. If it does recover you will never know whether you fixed it or whether "
                            + "the dependency simply came back. And you have spent the outage's most "
                            + "expensive minutes on a coin flip",
                    "Выкатка «" + change + "» без единого подтверждения. Всё это время ощущается как прогресс, "
                            + "а в этой конкретной теме у такого шага есть любимая маскировка — поднять лимит. "
                            + "Удвоить пул воркеров, удвоить пул соединений, добавить экземпляров: каждое "
                            + "звучит правдоподобно и при этом, если настоящая причина — переставшая отвечать "
                            + "зависимость, даёт вам больше потоков, которые можно потерять, и отправляет "
                            + "больше нагрузки в то, что и так падает. Цены обычные, их три. Вы добавили "
                            + "второе непроверенное изменение в систему, которая и так ведёт себя плохо: если "
                            + "симптомы сдвинутся, вы больше не знаете, какое изменение их сдвинуло. Если всё "
                            + "восстановится, вы никогда не узнаете, вы это починили или зависимость просто "
                            + "вернулась. И вы потратили самые дорогие минуты аварии на подбрасывание монеты",
                    List.of("fix", "misstep:fixed-without-a-cause"), state());
            return;
        }
        stage = "fixed";
        Trace.event("FIX_APPLIED",
                "\"" + change + "\" ships against a confirmed cause. Keep it small and keep it alone, so that "
                        + "if it does not work you have learned something instead of adding a variable. For "
                        + "this class of outage the fix almost always has two halves and skipping the second "
                        + "one is why it recurs: repair the thing that broke, and remove the mechanism that "
                        + "let one broken thing take the whole service down with it. The second half is the "
                        + "engineering — a finite timeout on every outbound call, a separate pool for the "
                        + "dependency that failed so it cannot drink the shared one dry, a circuit breaker "
                        + "that fails fast instead of queueing, and a bound on the queue so an overloaded "
                        + "service rejects quickly rather than accepting work it will never finish",
                "«" + change + "» выкатывается под подтверждённую причину. Пусть изменение будет маленьким и "
                        + "одиноким, чтобы в случае неудачи вы что-то узнали, а не добавили переменную. Для "
                        + "этого класса аварий у исправления почти всегда две половины, и пропуск второй — "
                        + "причина повторения: починить то, что сломалось, и убрать механизм, позволивший "
                        + "одной сломанной вещи утащить за собой весь сервис. Вторая половина и есть инженерия "
                        + "— конечный таймаут на каждом исходящем вызове, отдельный пул для отказавшей "
                        + "зависимости, чтобы она не выпила общий досуха, размыкатель цепи, который быстро "
                        + "отказывает вместо ожидания в очереди, и ограничение самой очереди, чтобы "
                        + "перегруженный сервис быстро отвергал запросы, а не принимал работу, которую никогда "
                        + "не выполнит",
                List.of("fix"), state());
    }

    /** Re-checks the thing that failed. Anything else is a feeling, not a check. */
    public void verify(String check, boolean passed) {
        tick(4);
        verified = passed;
        if (!passed) {
            flag("fix-did-not-work");
            stage = "still-down";
            Trace.event("STILL_DOWN",
                    check + " still fails. Good — you found out in seconds instead of in the morning, because "
                            + "there was a concrete check to re-run. Now do the part that is hard when you are "
                            + "tired: revert the change before trying the next hypothesis, so the system stays "
                            + "at a state somebody can reason about instead of accumulating three speculative "
                            + "edits nobody can justify. Then take the failure seriously as information — a "
                            + "fix that misses usually means the cause was a symptom of something one level "
                            + "further out, and the honest next move is another look at the evidence rather "
                            + "than another guess",
                    check + " всё ещё падает. И хорошо: вы узнали это за секунды, а не утром, потому что было "
                            + "что конкретно перезапустить. Теперь сделайте то, что трудно, когда вы устали, — "
                            + "откатите изменение, прежде чем проверять следующую гипотезу, чтобы система "
                            + "оставалась в состоянии, о котором можно рассуждать, а не копила три "
                            + "спекулятивные правки, которых никто не может обосновать. И отнеситесь к неудаче "
                            + "как к информации: промахнувшееся исправление обычно означает, что причина была "
                            + "симптомом чего-то уровнем выше, и честный следующий шаг — снова посмотреть на "
                            + "улики, а не выдвинуть ещё одну догадку",
                    List.of("verify", "misstep:fix-did-not-work"), state());
            return;
        }
        stage = "recovered";
        Trace.event("RECOVERY_VERIFIED",
                check + " passes. Verification means the request that failed now succeeds, run against the "
                        + "environment it failed in — not a unit test, not a local run, not \"the errors "
                        + "stopped\". For an outage there are three checks and you want all of them: the "
                        + "endpoint answers with a normal status and a normal latency; the graphs return to "
                        + "where they were before, including the queue depth and the pool gauges, because a "
                        + "service can answer while still working through a backlog; and the thread dump you "
                        + "take now looks like a healthy one, with workers idle rather than parked. Then close "
                        + "the loop with the humans — whoever reported it, and whoever owns the dependency you "
                        + "just found out you cannot survive without",
                check + " проходит. Проверка означает, что запрос, который падал, теперь проходит, и выполнен "
                        + "он в том окружении, где падал, — не юнит-тест, не локальный запуск и не «ошибки "
                        + "перестали идти». Для аварии проверок три, и нужны все: эндпоинт отвечает нормальным "
                        + "статусом и с нормальной задержкой; графики вернулись туда, где были, включая глубину "
                        + "очереди и показания пулов, потому что сервис может отвечать, всё ещё разгребая "
                        + "накопившееся; и дамп потоков, снятый сейчас, выглядит здоровым — воркеры "
                        + "простаивают, а не припаркованы. Затем замкните круг с людьми: с тем, кто сообщил, и "
                        + "с владельцем зависимости, без которой, как вы только что выяснили, вы не выживаете",
                List.of("verify"), state());
    }

    /** Adds the thing that would have caught it — the only permanent output of an outage. */
    public void guard(String guardDescription) {
        tick(8);
        guards.add(guardDescription);
        Trace.event("GUARD_ADDED",
                "Follow-up: " + guardDescription + ". Ask one question of every outage — what would have made "
                        + "this shorter — and for a service that went silent there are only four useful "
                        + "answers. An alert on what users experience (successful requests per second, error "
                        + "rate, latency at p99) rather than on whether the process exists, because \"the "
                        + "process is up\" was true throughout this entire incident. A readiness check that "
                        + "touches the things a request touches, and a liveness check that does not, so a slow "
                        + "dependency takes the instance out of rotation instead of into a restart loop. "
                        + "Defaults that make this failure survivable next time: timeouts on every outbound "
                        + "call, bounded queues, a bulkhead per dependency. And a written, tested capture "
                        + "script, so the next person collects the dumps in twenty seconds without having to "
                        + "remember how at 03:00",
                "Последующая задача: " + guardDescription + ". Задайте каждой аварии один вопрос — что сделало "
                        + "бы её короче, — и для замолчавшего сервиса полезных ответов всего четыре. Алерт на "
                        + "то, что переживают пользователи (успешные запросы в секунду, доля ошибок, задержка "
                        + "на p99), а не на существование процесса: «процесс жив» было правдой на протяжении "
                        + "всего этого инцидента. Проверка готовности, которая трогает то же, что трогает "
                        + "запрос, и проверка живости, которая этого не делает, — чтобы медленная зависимость "
                        + "выводила экземпляр из ротации, а не загоняла его в цикл перезапусков. Умолчания, "
                        + "делающие такой сбой переживаемым в следующий раз: таймауты на каждом исходящем "
                        + "вызове, ограниченные очереди, переборка на каждую зависимость. И написанный и "
                        + "проверенный скрипт сбора артефактов, чтобы следующий собрал дампы за двадцать "
                        + "секунд, не вспоминая в 03:00, как это делается",
                List.of("guard:" + guards.size()), state());
    }

    /** Reads the worksheet back: what is known, what was skipped, and what it cost. */
    public void review() {
        Trace.event("INCIDENT_REVIEW",
                service + " at T+" + minutes + "m: state " + stage + ", failure mode " + failureMode + ", "
                        + probes.size() + " probe(s), " + usableEvidence() + " usable artifact(s), root cause "
                        + (rootCause == null ? "not found" : rootCause)
                        + (missteps.isEmpty() ? ", no missteps" : ", missteps: " + String.join(", ", missteps))
                        + ". Read the row of steps back as the answer to the interview question, because that "
                        + "is what is being asked — not which bug it was, but whether you have a procedure. "
                        + "Find out which kind of \"not responding\" it is; capture the thread dump and the "
                        + "heap before anything restarts; restore service with the dumbest reversible action "
                        + "available; read where the threads are parked and what bounded thing they are "
                        + "queueing for; name a cause that explains the timing as well as the symptom; fix "
                        + "both the thing that broke and the mechanism that let it take everything down; "
                        + "verify with the request that failed; and leave behind the alert, the timeout and "
                        + "the capture script that make the next one five minutes instead of an hour",
                service + " на T+" + minutes + "м: состояние " + stage + ", режим сбоя " + failureMode + ", "
                        + "проб: " + probes.size() + ", пригодных артефактов: " + usableEvidence()
                        + ", корневая причина: " + (rootCause == null ? "не найдена" : rootCause)
                        + (missteps.isEmpty() ? ", промахов нет" : ", промахи: " + String.join(", ", missteps))
                        + ". Прочитайте ряд шагов обратно как ответ на вопрос собеседования, потому что "
                        + "спрашивают именно это: не какой это был баг, а есть ли у вас процедура. Выяснить, "
                        + "какое именно «не отвечает» перед вами; снять дамп потоков и кучи до любого "
                        + "перезапуска; восстановить сервис самым тупым обратимым действием; прочитать, где "
                        + "припаркованы потоки и за какой ограниченный ресурс они стоят в очереди; назвать "
                        + "причину, объясняющую и время, и симптом; починить и то, что сломалось, и механизм, "
                        + "позволивший этому утащить всё остальное; проверить тем запросом, который падал; и "
                        + "оставить после себя алерт, таймаут и скрипт сбора артефактов, которые сделают "
                        + "следующий раз пятиминутным, а не часовым",
                List.of(), state());
    }

    // -------------------------------------------------------------- helpers

    private String deriveMode() {
        if ("partial".equals(outcomeOf("instances"))) {
            return "partly-down";
        }
        if ("refused".equals(outcomeOf("tcp"))) {
            return "not-listening";
        }
        String endpoint = outcomeOf("endpoint");
        if ("timeout".equals(endpoint)) {
            return "ok".equals(outcomeOf("health")) ? "healthy-but-hanging" : "accepting-but-silent";
        }
        if ("slow".equals(endpoint)) {
            return "slow-not-hung";
        }
        if ("ok".equals(endpoint)) {
            return "answers-for-me";
        }
        return "unclear";
    }

    private String outcomeOf(String layer) {
        for (Probe probe : probes) {
            if (probe.layer.equals(layer)) {
                return probe.outcome;
            }
        }
        return null;
    }

    private static String probeLesson(String outcome) {
        if ("refused".equals(outcome)) {
            return "A refusal is the cheapest answer in the incident: nothing is listening on that port, so no "
                    + "amount of application debugging applies — look at whether the process is running, why "
                    + "it exited, and what the orchestrator says about restarts and exit codes.";
        }
        if ("timeout".equals(outcome)) {
            return "A timeout with no refusal means the TCP handshake completed and then nobody wrote a "
                    + "response: the process is alive and something inside it never reaches your handler, "
                    + "which is the case a thread dump answers in one screen.";
        }
        if ("partial".equals(outcome)) {
            return "Some instances answer and some do not, which is the luckiest version of this incident: you "
                    + "have a hung one and a healthy one to compare, and every difference between them is a "
                    + "free hypothesis.";
        }
        if ("slow".equals(outcome)) {
            return "It answers, just later than the client is willing to wait — so this is a latency incident "
                    + "that clients are reporting as an outage, and the client timeout is part of the story.";
        }
        if ("ok".equals(outcome)) {
            return "This layer is fine, and that is a real finding: whatever is broken is not here, and you "
                    + "have just deleted every hypothesis that lived below it.";
        }
        return "Record the exact command and the exact answer, because \"I tried it and it did not work\" is "
                + "not evidence anyone can build on.";
    }

    private static String probeLessonRu(String outcome) {
        if ("refused".equals(outcome)) {
            return "Отказ в соединении — самый дешёвый ответ в инциденте: на этом порту никто не слушает, "
                    + "поэтому отладка приложения тут вообще ни при чём — смотрите, запущен ли процесс, почему "
                    + "он завершился и что оркестратор говорит про перезапуски и коды выхода.";
        }
        if ("timeout".equals(outcome)) {
            return "Таймаут без отказа означает, что TCP-рукопожатие прошло, а ответ никто не написал: процесс "
                    + "жив, и что-то внутри него не доводит запрос до вашего обработчика — этот случай дамп "
                    + "потоков закрывает одним экраном.";
        }
        if ("partial".equals(outcome)) {
            return "Часть экземпляров отвечает, часть нет — это самая удачная версия инцидента: у вас есть "
                    + "зависший и здоровый рядом, и каждое отличие между ними — бесплатная гипотеза.";
        }
        if ("slow".equals(outcome)) {
            return "Ответ приходит, просто позже, чем клиент готов ждать: это инцидент задержки, о котором "
                    + "сообщают как об аварии, и клиентский таймаут — часть истории.";
        }
        if ("ok".equals(outcome)) {
            return "Этот слой в порядке, и это настоящая находка: сломано не здесь, и вы только что вычеркнули "
                    + "все гипотезы, жившие ниже него.";
        }
        return "Записывайте точную команду и точный ответ: «я попробовал, не работает» — не улика, на которой "
                + "кто-то может строить дальше.";
    }

    private static String modeLesson(String mode) {
        switch (mode) {
            case "not-listening":
                return "Nothing holds the port. The process is gone, crashed on startup, or is in a restart "
                        + "loop — so the next places to look are the exit code, the last hundred lines before "
                        + "it died, whether the platform killed it (OOMKilled is the classic, and it is the "
                        + "container's memory limit, not the JVM heap), and whether it is failing readiness "
                        + "and being killed by its own liveness probe.";
            case "accepting-but-silent":
                return "The socket is accepted and no response is ever written. The process is alive and no "
                        + "thread completes your handler, which narrows the world to four causes — every "
                        + "worker is blocked on something downstream, they are deadlocked, they are waiting "
                        + "for a connection that the pool cannot give them, or the JVM is spending its life "
                        + "in garbage collection. A thread dump tells you which, in one screen.";
            case "healthy-but-hanging":
                return "The health endpoint returns 200 while real requests hang, which is not a paradox but a "
                        + "defect in the check: it answers from a thread that does no work and touches nothing "
                        + "a request touches. That is why the load balancer kept sending traffic to a dead "
                        + "instance and why nothing alerted. A readiness check should exercise the "
                        + "dependencies a request needs; a liveness check should not, or a slow database will "
                        + "get your whole fleet restarted.";
            case "slow-not-hung":
                return "It responds, later than the caller waits. So this is a latency problem that the client "
                        + "timeout converted into an outage — hunt it as latency, and note that a caller with "
                        + "no timeout would have seen a slow service rather than a broken one.";
            case "partly-down":
                return "Some instances serve and some do not, which means the cause is per-instance state "
                        + "rather than shared code or shared config: a leak that has reached the ceiling on "
                        + "the oldest pods, one exhausted pool, a bad node. Compare a hung instance with a "
                        + "healthy one before anything else, and keep one hung instance alive out of "
                        + "rotation.";
            case "answers-for-me":
                return "It answers when you call it, which does not mean the reporter is wrong — it means the "
                        + "difference is in who, what, where or when. Their network path, their client with a "
                        + "500ms timeout, their region, their payload, or a rate limiter that only they have "
                        + "tripped. Go and read their request, not yours.";
            default:
                return "The probes do not agree on a verdict yet, which usually means one layer has not been "
                        + "tried. Do not proceed on a guess — the next command is cheaper than the next hour.";
        }
    }

    private static String modeLessonRu(String mode) {
        switch (mode) {
            case "not-listening":
                return "Порт никем не занят. Процесс исчез, упал при старте или крутится в цикле "
                        + "перезапусков, поэтому следующие места — код выхода, последняя сотня строк перед "
                        + "смертью, не убила ли его платформа (классика — OOMKilled, и это лимит памяти "
                        + "контейнера, а не куча JVM) и не проваливает ли он readiness, из-за чего его убивает "
                        + "собственная проверка живости.";
            case "accepting-but-silent":
                return "Соединение принимается, ответ не пишется никогда. Процесс жив, и ни один поток не "
                        + "доводит до конца ваш обработчик — мир сужается до четырёх причин: все воркеры "
                        + "заблокированы на чём-то ниже по стеку, они во взаимоблокировке, они ждут "
                        + "соединения, которого пул им не даёт, или JVM проводит жизнь в сборке мусора. Какая "
                        + "именно — дамп потоков показывает одним экраном.";
            case "healthy-but-hanging":
                return "Health-эндпоинт отдаёт 200, пока настоящие запросы висят, и это не парадокс, а дефект "
                        + "самой проверки: она отвечает из потока, который не делает работы и не трогает "
                        + "ничего из того, что трогает запрос. Именно поэтому балансировщик продолжал слать "
                        + "трафик на мёртвый экземпляр и поэтому ничто не зазвонило. Проверка готовности "
                        + "должна задействовать зависимости, нужные запросу; проверка живости — не должна, "
                        + "иначе медленная база устроит перезапуск всему парку.";
            case "slow-not-hung":
                return "Ответ есть, просто позже, чем ждёт вызывающий. Это проблема задержки, которую "
                        + "клиентский таймаут превратил в аварию: охотьтесь на неё как на задержку и заметьте, "
                        + "что вызывающий без таймаута увидел бы медленный сервис, а не сломанный.";
            case "partly-down":
                return "Часть экземпляров обслуживает, часть нет — значит причина в состоянии конкретного "
                        + "экземпляра, а не в общем коде или общей конфигурации: утечка, дошедшая до потолка "
                        + "на самых старых подах, один исчерпанный пул, плохой узел. Прежде всего сравните "
                        + "зависший экземпляр со здоровым и оставьте один зависший живым вне ротации.";
            case "answers-for-me":
                return "Вам он отвечает, и это не значит, что сообщивший неправ, — это значит, что разница в "
                        + "том, кто, что, откуда или когда. Их сетевой путь, их клиент с таймаутом в 500мс, их "
                        + "регион, их тело запроса или ограничитель скорости, который сработал только у них. "
                        + "Идите читать ИХ запрос, а не свой.";
            default:
                return "Пробы пока не сходятся в вердикт — обычно это значит, что один слой не проверяли. Не "
                        + "идите дальше на догадке: следующая команда дешевле следующего часа.";
        }
    }

    private static String threadLesson(String state, int share) {
        if (share >= 50) {
            if ("BLOCKED".equals(state)) {
                return "Most of the pool is BLOCKED on a monitor, so this is lock contention and possibly a "
                        + "deadlock behind it — find the one thread that owns that monitor and read what IT "
                        + "is doing, because that single stack is the whole outage.";
            }
            if ("RUNNABLE".equals(state)) {
                return "Most of the pool is RUNNABLE, which means these threads are not waiting for anybody — "
                        + "they are either burning CPU in your code or sitting in a socket read, which the JVM "
                        + "also reports as RUNNABLE. The top frame tells you which, and the CPU graph settles "
                        + "it.";
            }
            return "Most of the pool is parked in " + state + ", waiting for something that is not coming back "
                    + "on its own — read the frame to find out what, because the answer is the cause.";
        }
        return "A small group, so this is not by itself the outage — but note it, because a handful of threads "
                + "holding something the rest need is exactly how two stuck threads become two hundred.";
    }

    private static String threadLessonRu(String state, int share) {
        if (share >= 50) {
            if ("BLOCKED".equals(state)) {
                return "Большая часть пула в BLOCKED на мониторе — это конкуренция за блокировку, а за ней "
                        + "возможна и взаимоблокировка: найдите единственный поток, владеющий этим монитором, "
                        + "и прочитайте, что делает ОН, потому что этот один стек и есть вся авария.";
            }
            if ("RUNNABLE".equals(state)) {
                return "Большая часть пула в RUNNABLE — значит эти потоки никого не ждут: они либо жгут "
                        + "процессор в вашем коде, либо сидят в чтении сокета, которое JVM тоже показывает как "
                        + "RUNNABLE. Что именно — говорит верхний кадр, а окончательно решает график CPU.";
            }
            return "Большая часть пула припаркована в " + state + " и ждёт того, что само не вернётся: "
                    + "прочитайте кадр, чтобы понять, чего именно, — ответ и есть причина.";
        }
        return "Группа маленькая, поэтому сама по себе она не авария, — но запомните её: горстка потоков, "
                + "держащих то, что нужно остальным, — это ровно тот способ, которым два застрявших потока "
                + "превращаются в двести.";
    }

    private int threadTotal() {
        int total = 0;
        for (StackGroup group : threadGroups) {
            total += group.count;
        }
        return total;
    }

    private int shareOf(int count) {
        int total = threadTotal();
        return total > 0 ? count * 100 / total : 0;
    }

    private int usableEvidence() {
        int usable = 0;
        for (Artifact artifact : evidence) {
            if (!artifact.lost) {
                usable++;
            }
        }
        return usable;
    }

    private void flag(String misstep) {
        if (!missteps.contains(misstep)) {
            missteps.add(misstep);
        }
    }

    private void tick(int cost) {
        minutes += cost;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("service", service);
        s.put("symptom", symptom);
        s.put("stage", stage);
        s.put("failureMode", failureMode);
        s.put("minutes", minutes);
        s.put("processRestarted", processRestarted);

        List<Object> layerRows = new ArrayList<>();
        for (String layer : LAYERS) {
            for (Probe probe : probes) {
                if (!probe.layer.equals(layer)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("layer", probe.layer);
                item.put("command", probe.command);
                item.put("outcome", probe.outcome);
                item.put("detail", probe.detail);
                layerRows.add(item);
            }
        }
        s.put("probes", layerRows);

        List<Object> artifacts = new ArrayList<>();
        for (Artifact artifact : evidence) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("artifact", artifact.name);
            item.put("how", artifact.how);
            item.put("lost", artifact.lost);
            artifacts.add(item);
        }
        s.put("evidence", artifacts);

        List<Object> dump = new ArrayList<>();
        for (StackGroup group : threadGroups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group", group.name);
            item.put("count", group.count);
            item.put("state", group.state);
            item.put("frame", group.frame);
            item.put("share", shareOf(group.count));
            dump.add(item);
        }
        s.put("threads", dump);
        s.put("threadTotal", threadTotal());

        List<Object> bounded = new ArrayList<>();
        for (Pool pool : pools) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", pool.name);
            item.put("inUse", pool.inUse);
            item.put("max", pool.max);
            item.put("queued", pool.queued);
            item.put("saturated", pool.saturated());
            bounded.add(item);
        }
        s.put("pools", bounded);

        if (gcReading != null) {
            Map<String, Object> collector = new LinkedHashMap<>();
            collector.put("pausePercent", gcReading[0]);
            collector.put("heapAfterGcPercent", gcReading[1]);
            collector.put("thrashing", gcReading[0] >= 50 && gcReading[1] >= 90);
            s.put("gc", collector);
        }

        List<Object> other = new ArrayList<>();
        for (Reading reading : readings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", reading.name);
            item.put("value", reading.value);
            item.put("alarming", reading.alarming);
            other.add(item);
        }
        s.put("resources", other);

        if (deadlock != null) {
            s.put("deadlock", deadlock);
        }
        if (capacity != null) {
            s.put("capacity", capacity);
        }
        if (mitigationAction != null) {
            Map<String, Object> mitigation = new LinkedHashMap<>();
            mitigation.put("action", mitigationAction);
            mitigation.put("effect", mitigationEffect);
            s.put("mitigation", mitigation);
        }

        s.put("rootCause", rootCause);
        s.put("causeEvidence", causeEvidence);
        if (fixApplied != null) {
            Map<String, Object> fix = new LinkedHashMap<>();
            fix.put("change", fixApplied);
            fix.put("blind", fixWasBlind);
            s.put("fix", fix);
        }
        s.put("verified", verified);
        s.put("guards", new ArrayList<>(guards));
        s.put("missteps", new ArrayList<>(missteps));
        return s;
    }
}
