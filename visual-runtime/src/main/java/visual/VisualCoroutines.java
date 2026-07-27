package visual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of how Kotlin coroutines actually work, split into
 * the three answers the interview question is really asking for.
 *
 * <ol>
 *   <li><b>What the compiler does.</b> {@link #suspendFun(String)} walks the
 *       CPS transform: a {@code suspend fun} becomes a class with an {@code int
 *       label}, one field per local that has to survive a suspension, and a
 *       hidden {@code Continuation} parameter. Every suspension point either
 *       returns a value immediately or returns the marker
 *       {@code COROUTINE_SUSPENDED}, and resuming means calling
 *       {@code invokeSuspend} on the same object again so that
 *       {@code when (label)} jumps back in where it stopped.</li>
 *   <li><b>What the runtime does.</b> {@link #runtime(int, int)} gives you
 *       dispatchers with a fixed number of worker threads. A coroutine only
 *       occupies a thread while it is running: suspending returns the thread to
 *       the dispatcher, which immediately picks up the next queued coroutine.
 *       Blocking calls do the opposite — they hold the thread hostage, which is
 *       what starves {@code Dispatchers.Default}.</li>
 *   <li><b>What structured concurrency does.</b> Coroutines live in scopes, a
 *       scope does not finish before its children, a failing child cancels its
 *       siblings, and cancellation is cooperative: it is instant at a suspension
 *       point and invisible inside a loop that never suspends.</li>
 * </ol>
 *
 * <p>Nothing is timed, threaded or random here — the "threads" are named slots
 * and every decision is made by the model — so a given example always produces
 * the same trace. Every step emits a bilingual {@link Trace} event and the class
 * stays dependency-free.
 */
public class VisualCoroutines {

    /** Reserved stack for one platform thread — the number that limits threads. */
    private static final long PLATFORM_THREAD_BYTES = 1024L * 1024L;

    /** One continuation object: a label, a few captured locals, a few references. */
    private static final long COROUTINE_BYTES = 200L;

    /** A virtual thread starts with a small stack chunk on the heap and grows. */
    private static final long VIRTUAL_THREAD_BYTES = 1024L;

    private static final String ROOT = "root";
    private static final String GLOBAL = "GlobalScope";

    /** One worker thread of a dispatcher. */
    private static final class Worker {

        private final String name;
        private String coroutine;
        private boolean blocked;

        private Worker(String name) {
            this.name = name;
        }
    }

    /** A dispatcher: a fixed set of worker threads plus a queue of ready work. */
    private static final class Dispatcher {

        private final String name;
        private final List<Worker> workers = new ArrayList<>();
        private final Deque<String> queue = new ArrayDeque<>();

        private Dispatcher(String name, int threads) {
            this.name = name;
            for (int i = 1; i <= threads; i++) {
                workers.add(new Worker(threads == 1 && "Main".equals(name) ? "main" : name + "-worker-" + i));
            }
        }
    }

    /** A Job in the tree: the root scope, a coroutineScope, a supervisorScope. */
    private static final class Scope {

        private final String name;
        private final String kind;
        private final List<String> children = new ArrayList<>();
        private String state = "ACTIVE";

        private Scope(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    /** One coroutine: a Job, a continuation and whatever it is doing right now. */
    private static final class Coroutine {

        private final String name;
        private final String scope;
        private final String kind;
        private String dispatcher;
        private String state = "NEW";
        private String thread;
        private String doing;
        private boolean cancelRequested;
        private String awaiting;
        private int suspensions;

        private Coroutine(String name, String scope, String kind, String dispatcher) {
            this.name = name;
            this.scope = scope;
            this.kind = kind;
            this.dispatcher = dispatcher;
        }
    }

    // ------------------------------------------------------------------ state

    private final Map<String, Dispatcher> dispatchers = new LinkedHashMap<>();
    private final Map<String, Scope> scopes = new LinkedHashMap<>();
    private final Map<String, Coroutine> coroutines = new LinkedHashMap<>();

    private int launched;
    private int peakAlive;
    private int suspensions;
    private int resumes;
    private int dispatches;
    private int queuedTimes;
    private int contextSwitches;
    private int blockingCalls;
    private int completed;
    private int cancelled;
    private int failed;
    private int leaked;

    private VisualCoroutines(int defaultThreads, int ioThreads) {
        dispatchers.put("Default", new Dispatcher("Default", defaultThreads));
        dispatchers.put("IO", new Dispatcher("IO", ioThreads));
        dispatchers.put("Main", new Dispatcher("Main", 1));
        scopes.put(ROOT, new Scope(ROOT, "ROOT"));
        scopes.put(GLOBAL, new Scope(GLOBAL, "GLOBAL"));
    }

    // -------------------------------------------------------------- the runtime

    /**
     * Builds a runtime with {@code Dispatchers.Default} sized to the number of
     * "CPU cores" you pass and {@code Dispatchers.IO} sized for blocking work,
     * plus a single-threaded {@code Dispatchers.Main}.
     */
    public static VisualCoroutines runtime(int defaultThreads, int ioThreads) {
        VisualCoroutines rt = new VisualCoroutines(defaultThreads, ioThreads);
        Trace.event("RUNTIME_READY",
                "Three dispatchers, " + rt.totalThreads() + " real threads in total, and not one of them "
                        + "belongs to a coroutine. A CoroutineDispatcher is the thing that decides which "
                        + "thread runs a continuation when it is resumed — Default has " + defaultThreads
                        + " worker(s) because it is sized to the CPU cores and is meant for computation, IO "
                        + "has " + ioThreads + " because its threads are expected to sit in blocking system "
                        + "calls, and Main is the single UI thread. Coroutines are not threads and are not "
                        + "created on threads: a coroutine is an object, and it is only attached to a thread "
                        + "for as long as it is actually executing code",
                "Три диспетчера, всего " + rt.totalThreads() + " настоящих потока, и ни один из них не "
                        + "принадлежит корутине. CoroutineDispatcher — это то, что решает, какой поток "
                        + "выполнит continuation при возобновлении: у Default " + defaultThreads
                        + " воркер(ов), потому что он рассчитан на число ядер и предназначен для вычислений, "
                        + "у IO — " + ioThreads + ", потому что его потоки как раз и должны сидеть в "
                        + "блокирующих системных вызовах, а Main — единственный поток UI. Корутины — не "
                        + "потоки и не создаются на потоках: корутина это объект, и она привязана к потоку "
                        + "лишь на то время, пока действительно выполняет код",
                List.of("dispatchers"), rt.state());
        return rt;
    }

    // ------------------------------------------------------------------ scopes

    /** {@code coroutineScope { }} — a parent Job that waits for its children. */
    public void scope(String name) {
        newScope(name, "COROUTINE_SCOPE");
    }

    /** {@code supervisorScope { }} — the same, except a child's failure stays local. */
    public void supervisorScope(String name) {
        newScope(name, "SUPERVISOR");
    }

    private void newScope(String name, String kind) {
        scopes.put(name, new Scope(name, kind));
        boolean supervisor = "SUPERVISOR".equals(kind);
        Trace.event("SCOPE_OPENED",
                (supervisor ? "supervisorScope" : "coroutineScope") + " { } opened \"" + name + "\" as a "
                        + "child Job of the scope around it. This is the whole of structured concurrency: "
                        + "every coroutine you start inside belongs to this Job, the block cannot return "
                        + "until all of them have finished, and cancelling this Job cancels every one of "
                        + "them. "
                        + (supervisor
                        ? "The supervisor part changes exactly one rule: a child that fails does not take "
                        + "its siblings or its parent down with it"
                        : "A child that fails cancels its siblings and fails this scope too — which is "
                        + "usually what you want, because half a loaded screen is worse than no screen"),
                (supervisor ? "supervisorScope" : "coroutineScope") + " { } открыл «" + name + "» как "
                        + "дочерний Job окружающей области. В этом вся структурная конкурентность: каждая "
                        + "корутина, запущенная внутри, принадлежит этому Job, блок не может вернуться, пока "
                        + "все они не завершатся, а отмена этого Job отменяет их все. "
                        + (supervisor
                        ? "Приставка supervisor меняет ровно одно правило: упавший ребёнок не тянет за "
                        + "собой ни соседей, ни родителя"
                        : "Упавший ребёнок отменяет соседей и роняет саму область — и обычно это именно то, "
                        + "что нужно, потому что наполовину загруженный экран хуже, чем незагруженный"),
                List.of("scopes"), state());
    }

    // ------------------------------------------------------------- starting work

    /** {@code launch { }} in the outermost scope. */
    public String launch(String dispatcher, String name) {
        return launchIn(ROOT, dispatcher, name);
    }

    /** {@code launch { }} inside a named scope — the coroutine becomes its child. */
    public String launchIn(String scope, String dispatcher, String name) {
        Coroutine c = create(scope, dispatcher, name, "LAUNCH");
        Trace.event("COROUTINE_LAUNCHED",
                "launch(Dispatchers." + dispatcher + ") created \"" + name + "\": a Job attached to scope \""
                        + scope + "\", plus a continuation object holding the code to run. launch returns "
                        + "immediately — it did not start a thread, it did not wait for anything, and it did "
                        + "not even necessarily run any of the block yet. What it produced is a task for the "
                        + dispatcher + " dispatcher, and a handle you can cancel or join",
                "launch(Dispatchers." + dispatcher + ") создал «" + name + "»: Job, привязанный к области «"
                        + scope + "», плюс объект continuation с кодом, который надо выполнить. launch "
                        + "возвращается сразу — он не запустил поток, ничего не подождал и, возможно, ещё ни "
                        + "строчки блока не выполнил. Он создал задачу для диспетчера " + dispatcher
                        + " и хэндл, который можно отменить или дождаться",
                List.of("coroutines", "scopes"), state());
        schedule(c);
        return name;
    }

    /** {@code async { }} — the same, except the Job also carries a result. */
    public String async(String scope, String dispatcher, String name) {
        Coroutine c = create(scope, dispatcher, name, "ASYNC");
        Trace.event("ASYNC_STARTED",
                "async(Dispatchers." + dispatcher + ") started \"" + name + "\" and returned a Deferred. It "
                        + "differs from launch in exactly two ways: the Job carries a result you collect with "
                        + "await(), and a failure is stored in the Deferred instead of only being thrown. "
                        + "Concurrency comes from starting two of these before awaiting either — writing "
                        + "await() immediately after async() gives you a sequential program with extra steps",
                "async(Dispatchers." + dispatcher + ") запустил «" + name + "» и вернул Deferred. От launch "
                        + "он отличается ровно двумя вещами: Job несёт результат, который забирают через "
                        + "await(), а падение сохраняется в Deferred, а не только выбрасывается. "
                        + "Конкурентность возникает от того, что два таких запущены до первого await(): "
                        + "написать await() сразу после async() — это последовательная программа с лишними "
                        + "шагами",
                List.of("coroutines", "scopes"), state());
        schedule(c);
        return name;
    }

    /**
     * {@code GlobalScope.launch { }} — a coroutine with no parent, and therefore
     * nothing that will ever cancel it for you.
     */
    public String launchGlobal(String dispatcher, String name) {
        Coroutine c = create(GLOBAL, dispatcher, name, "LAUNCH");
        Trace.event("COROUTINE_LAUNCHED",
                "GlobalScope.launch(Dispatchers." + dispatcher + ") created \"" + name + "\" outside every "
                        + "scope you own. It runs the same way as any other coroutine — the difference is "
                        + "structural: its Job has no parent, so no scope waits for it, no scope cancels it, "
                        + "and no failure of it is reported anywhere you are looking. This is the coroutine "
                        + "equivalent of starting a thread and dropping the reference",
                "GlobalScope.launch(Dispatchers." + dispatcher + ") создал «" + name + "» вне всех твоих "
                        + "областей. Выполняется он так же, как любая другая корутина, — разница "
                        + "структурная: у его Job нет родителя, поэтому его никто не ждёт, никто не отменяет "
                        + "и о его падении никто не узнает. Это корутинный аналог «запустил поток и потерял "
                        + "ссылку»",
                List.of("coroutines", "scopes"), state());
        schedule(c);
        return name;
    }

    private Coroutine create(String scopeName, String dispatcher, String name, String kind) {
        requireDispatcher(dispatcher);
        Scope scope = requireScope(scopeName);
        Coroutine c = new Coroutine(name, scopeName, kind, dispatcher);
        coroutines.put(name, c);
        scope.children.add(name);
        launched++;
        peakAlive = Math.max(peakAlive, alive());
        return c;
    }

    // ---------------------------------------------------------------- dispatch

    /** Hands a ready coroutine to its dispatcher: a free thread, or the queue. */
    private void schedule(Coroutine c) {
        Dispatcher d = requireDispatcher(c.dispatcher);
        Worker free = freeWorker(d);
        if (free == null) {
            c.state = "QUEUED";
            c.thread = null;
            d.queue.addLast(c.name);
            queuedTimes++;
            Trace.event("QUEUED",
                    "Every thread of Dispatchers." + d.name + " is busy, so \"" + c.name + "\" waits in the "
                            + "dispatcher's queue — " + d.queue.size() + " task(s) deep. Nothing is wrong "
                            + "here; this is what a dispatcher is. But it is also the answer to \"why is my "
                            + "coroutine not running\": a coroutine is only concurrent with others once it "
                            + "gets a thread, and a coroutine that never suspends keeps the one it has, so "
                            + "everything behind it waits",
                    "Все потоки Dispatchers." + d.name + " заняты, поэтому «" + c.name + "» ждёт в очереди "
                            + "диспетчера — в ней " + d.queue.size() + " задач(и). Ничего страшного не "
                            + "произошло, диспетчер именно так и устроен. Но это же и ответ на вопрос "
                            + "«почему моя корутина не выполняется»: корутина конкурентна с другими только "
                            + "после того, как получит поток, а корутина, которая не приостанавливается, "
                            + "свой поток не отдаёт — и всё, что стоит за ней, ждёт",
                    List.of("dispatchers", "coroutines"), state());
            return;
        }
        occupy(free, c);
        dispatches++;
        Trace.event("DISPATCHED",
                "Thread " + free.name + " picked \"" + c.name + "\" up and called invokeSuspend on its "
                        + "continuation, so the coroutine is now running — on a thread it borrowed, not on a "
                        + "thread it owns. Two coroutines on Dispatchers." + d.name + " have no thread "
                        + "affinity at all: the same coroutine can resume on a different worker later, which "
                        + "is exactly why coroutine code must not rely on ThreadLocal and why anything it "
                        + "shares still needs the ordinary memory-model rules",
                "Поток " + free.name + " подхватил «" + c.name + "» и вызвал invokeSuspend на его "
                        + "continuation, так что корутина теперь выполняется — на потоке, который она "
                        + "одолжила, а не на своём. У двух корутин на Dispatchers." + d.name + " нет "
                        + "никакой привязки к потоку: та же самая корутина позже может возобновиться на "
                        + "другом воркере — именно поэтому код корутин не должен полагаться на ThreadLocal, "
                        + "а всё, что он разделяет, по-прежнему подчиняется обычным правилам модели памяти",
                List.of("dispatchers", "coroutines"), state());
    }

    /** Frees the worker a coroutine was using and starts whatever was queued. */
    private void release(Coroutine c) {
        Dispatcher d = requireDispatcher(c.dispatcher);
        // It may have been waiting rather than running; leaving a dead name in
        // the queue would hand a thread to a coroutine that no longer exists.
        d.queue.remove(c.name);
        boolean freed = false;
        for (Worker w : d.workers) {
            if (c.name.equals(w.coroutine)) {
                w.coroutine = null;
                w.blocked = false;
                freed = true;
            }
        }
        c.thread = null;
        if (!freed) {
            return;
        }
        String next = d.queue.pollFirst();
        if (next == null) {
            return;
        }
        Coroutine queued = coroutines.get(next);
        if (queued == null || terminal(queued)) {
            return;
        }
        Worker free = freeWorker(d);
        if (free == null) {
            d.queue.addFirst(next);
            return;
        }
        occupy(free, queued);
        dispatches++;
        Trace.event("DISPATCHED",
                "The thread that " + c.name + " just released did not go idle: " + free.name + " took \""
                        + queued.name + "\" off the queue and started running it. This hand-off is the whole "
                        + "economic argument for coroutines — the thread is the scarce resource, and it is "
                        + "handed to the next piece of work the instant the previous one stops needing it, "
                        + "with no OS context switch and no second stack",
                "Поток, который только что отпустил " + c.name + ", не простаивает: " + free.name
                        + " снял с очереди «" + queued.name + "» и начал его выполнять. Именно эта передача "
                        + "и есть весь экономический смысл корутин: дефицитный ресурс — поток, и он "
                        + "достаётся следующей задаче в тот же миг, когда предыдущая перестала быть в нём "
                        + "нужна, без переключения контекста ОС и без второго стека",
                List.of("dispatchers", "coroutines"), state());
    }

    private static void occupy(Worker worker, Coroutine c) {
        worker.coroutine = c.name;
        worker.blocked = false;
        c.state = "RUNNING";
        c.thread = worker.name;
    }

    private static Worker freeWorker(Dispatcher d) {
        for (Worker w : d.workers) {
            if (w.coroutine == null) {
                return w;
            }
        }
        return null;
    }

    // -------------------------------------------------------- suspend / resume

    /**
     * The coroutine reaches a suspension point that has no answer ready, so it
     * returns {@code COROUTINE_SUSPENDED} and gives the thread back.
     */
    public void suspendAt(String name, String reason) {
        Coroutine c = require(name);
        if (c.cancelRequested) {
            cancelHere(c, reason);
            return;
        }
        c.suspensions++;
        suspensions++;
        c.state = "SUSPENDED";
        c.doing = reason;
        Trace.event("SUSPENDED",
                "\"" + name + "\" reached " + reason + ", which had no answer ready, so it returned the "
                        + "marker COROUTINE_SUSPENDED. Follow what that means: invokeSuspend returns to the "
                        + "dispatcher loop, the JVM call stack unwinds completely, and thread " + c.thread
                        + " is free — while the coroutine itself is still alive as an object on the heap, "
                        + "holding its label and its locals. That is the whole trick: suspending is "
                        + "returning, and blocking is not returning",
                "«" + name + "» дошёл до " + reason + ", ответа там не было, и он вернул маркер "
                        + "COROUTINE_SUSPENDED. Проследи, что это значит: invokeSuspend возвращается в цикл "
                        + "диспетчера, стек вызовов JVM полностью разматывается, и поток " + c.thread
                        + " свободен — а сама корутина продолжает жить объектом в куче, храня свой label и "
                        + "свои локальные переменные. В этом весь фокус: приостановка — это возврат, а "
                        + "блокировка — это отказ вернуться",
                List.of("coroutines", "dispatchers"), state());
        release(c);
    }

    /** Something finished and called {@code continuation.resumeWith(...)}. */
    public void resume(String name) {
        Coroutine c = require(name);
        if (terminal(c)) {
            return;
        }
        resumes++;
        Trace.event("RESUMED",
                "Whatever \"" + name + "\" was waiting for finished and called "
                        + "continuation.resumeWith(Result.success(...)). Notice who did that: not a "
                        + "scheduler polling for readiness, but the callback of the thing that completed — a "
                        + "timer, an HTTP client's completion handler, another coroutine. The continuation "
                        + "is now a task for Dispatchers." + c.dispatcher + ", which is what decides the "
                        + "thread it will actually re-enter invokeSuspend on",
                "То, чего ждал «" + name + "», завершилось и вызвало "
                        + "continuation.resumeWith(Result.success(...)). Обрати внимание, кто это сделал: не "
                        + "планировщик, опрашивающий готовность, а колбэк того, что завершилось, — таймер, "
                        + "обработчик завершения HTTP-клиента, другая корутина. Теперь continuation — это "
                        + "задача для Dispatchers." + c.dispatcher + ", и именно он решит, на каком потоке "
                        + "снова войдут в invokeSuspend",
                List.of("coroutines"), state());
        c.doing = null;
        schedule(c);
    }

    /** {@code withContext(Dispatchers.X) { }} — the same coroutine, another pool. */
    public void withContext(String name, String dispatcher, String what) {
        Coroutine c = require(name);
        requireDispatcher(dispatcher);
        String from = c.dispatcher;
        contextSwitches++;
        suspensions++;
        c.suspensions++;
        c.state = "SUSPENDED";
        c.doing = what;
        Trace.event("CONTEXT_SWITCHED",
                "withContext(Dispatchers." + dispatcher + ") { " + what + " } — \"" + name + "\" is one "
                        + "coroutine that just changed threads. It suspends on " + from + ", releasing "
                        + (c.thread == null ? "its worker" : c.thread) + ", and is re-dispatched onto "
                        + dispatcher + "; when the block ends it suspends again and comes back to " + from
                        + ". So this is not a new coroutine and not a fork: it is a suspension with a "
                        + "different dispatcher on the other side, which is why it can return a value like "
                        + "an ordinary function call",
                "withContext(Dispatchers." + dispatcher + ") { " + what + " } — «" + name + "» это одна "
                        + "корутина, которая только что сменила потоки. Она приостанавливается на " + from
                        + ", отпуская " + (c.thread == null ? "свой воркер" : c.thread)
                        + ", и заново диспетчеризуется на " + dispatcher + "; когда блок закончится, она "
                        + "снова приостановится и вернётся на " + from + ". То есть это не новая корутина и "
                        + "не форк, а приостановка, по другую сторону которой другой диспетчер, — потому "
                        + "она и умеет возвращать значение, как обычный вызов функции",
                List.of("coroutines", "dispatchers"), state());
        release(c);
        c.dispatcher = dispatcher;
        c.doing = null;
        schedule(c);
    }

    /**
     * A call that does not suspend but blocks — JDBC, {@code Thread.sleep},
     * {@code File.readBytes}. The worker thread is held until the coroutine ends.
     */
    public void blockingCall(String name, String what) {
        Coroutine c = require(name);
        Dispatcher d = requireDispatcher(c.dispatcher);
        blockingCalls++;
        c.doing = what;
        for (Worker w : d.workers) {
            if (c.name.equals(w.coroutine)) {
                w.blocked = true;
            }
        }
        boolean io = "IO".equals(d.name);
        Trace.event("THREAD_BLOCKED",
                "\"" + name + "\" called " + what + ", which is not a suspend function — it is an ordinary "
                        + "blocking call, so it parks thread " + c.thread + " inside the OS. The coroutine "
                        + "machinery cannot help: there is no suspension point, nothing returns "
                        + "COROUTINE_SUSPENDED, and the thread stays owned until the call comes back. "
                        + (io
                        ? "On Dispatchers.IO that is the intended trade: its threads exist to be blocked, "
                        + "and the pool grows to 64 by default so a few of them sitting in a socket read "
                        + "costs you nothing but memory"
                        : "On Dispatchers.Default that is a bug: this pool has one thread per core, so "
                        + "every blocked thread is a core's worth of the whole application standing still"),
                "«" + name + "» вызвал " + what + ", а это не suspend-функция, а обычный блокирующий вызов, "
                        + "поэтому он паркует поток " + c.thread + " внутри ОС. Механика корутин здесь "
                        + "бессильна: точки приостановки нет, COROUTINE_SUSPENDED никто не возвращает, и "
                        + "поток остаётся занятым, пока вызов не вернётся. "
                        + (io
                        ? "На Dispatchers.IO это осознанный размен: его потоки для того и существуют, "
                        + "чтобы блокироваться, а пул по умолчанию растёт до 64, поэтому несколько "
                        + "потоков, сидящих в чтении сокета, стоят только памяти"
                        : "На Dispatchers.Default это ошибка: в этом пуле по потоку на ядро, поэтому "
                        + "каждый заблокированный поток — это простаивающее ядро всего приложения"),
                List.of("dispatchers", "coroutines"), state());
        if (!io && allBlocked(d)) {
            Trace.event("POOL_STARVED",
                    "Every thread of Dispatchers." + d.name + " is now blocked and " + d.queue.size()
                            + " coroutine(s) are queued behind them. Nothing in this dispatcher can make "
                            + "progress — not the queued work, not a resume that arrives now, not a "
                            + "cancellation that wants to run a finally block. This is what pool starvation "
                            + "looks like in production: no exception, no error log, just latency that grows "
                            + "with load, and the fix is to move the blocking call to Dispatchers.IO or "
                            + "replace it with a suspending client",
                    "Все потоки Dispatchers." + d.name + " заблокированы, а за ними в очереди стоят "
                            + "корутин(ы): " + d.queue.size() + ". Ничто в этом диспетчере не может "
                            + "продвинуться — ни задачи из очереди, ни возобновление, которое придёт сейчас, "
                            + "ни отмена, которой нужно выполнить блок finally. Вот так голодание пула "
                            + "выглядит в продакшене: ни исключения, ни записи в логе, просто задержка, "
                            + "растущая с нагрузкой, — а лечится это переносом блокирующего вызова на "
                            + "Dispatchers.IO или заменой на приостанавливающийся клиент",
                    List.of("dispatchers", "starved"), state());
        }
    }

    /** A stretch of computation with no suspension point in it at all. */
    public void cpuWork(String name, String what) {
        Coroutine c = require(name);
        if (c.cancelRequested) {
            Trace.event("CANCELLATION_IGNORED",
                    "\"" + name + "\" has been cancelled and is calmly running " + what + " anyway. "
                            + "Cancellation in coroutines is cooperative: cancel() sets isActive to false on "
                            + "the Job, and that is genuinely all it does. Something has to notice — a "
                            + "suspension point, which throws CancellationException, or your own check. A "
                            + "loop that only computes never reaches either, so it runs to the end on thread "
                            + c.thread + ", burning a core to produce a result no one will read",
                    "«" + name + "» отменён и как ни в чём не бывало выполняет " + what + ". Отмена в "
                            + "корутинах кооперативна: cancel() выставляет isActive в false у Job — и это "
                            + "буквально всё, что он делает. Заметить должен кто-то другой: точка "
                            + "приостановки, которая бросит CancellationException, или твоя собственная "
                            + "проверка. Цикл, который только считает, не доходит ни до того, ни до другого "
                            + "и досчитывает до конца на потоке " + c.thread + ", сжигая ядро ради "
                            + "результата, который никто не прочитает",
                    List.of("coroutines", "cancel"), state());
            return;
        }
        c.doing = what;
        Trace.event("CPU_WORK",
                "\"" + name + "\" is computing " + what + " on thread " + c.thread + " — no suspension "
                        + "point anywhere in it. While this runs, the coroutine owns that thread as "
                        + "completely as a plain Runnable would: nothing else on Dispatchers." + c.dispatcher
                        + " can use it, and this coroutine is not concurrent with anything queued behind it. "
                        + "Coroutines make waiting cheap; they do not make computing cheap",
                "«" + name + "» считает " + what + " на потоке " + c.thread + " — точек приостановки "
                        + "внутри нет ни одной. Пока это выполняется, корутина владеет потоком так же "
                        + "полно, как обычный Runnable: больше никто на Dispatchers." + c.dispatcher
                        + " им воспользоваться не может, и с тем, что стоит в очереди за ней, эта корутина "
                        + "не конкурентна. Корутины удешевляют ожидание, а не вычисление",
                List.of("coroutines", "dispatchers"), state());
    }

    /** The same loop, written so that each round calls {@code ensureActive()}. */
    public void cooperativeCpuWork(String name, String what) {
        Coroutine c = require(name);
        if (c.cancelRequested) {
            cancelHere(c, "ensureActive()");
            return;
        }
        c.doing = what;
        Trace.event("CPU_WORK",
                "\"" + name + "\" computes " + what + " and then calls ensureActive() — the Job is still "
                        + "active, so the round proceeds. That one line is what turns an uncancellable loop "
                        + "into a cancellable one; yield() does the same and additionally gives the "
                        + "dispatcher a chance to run something else, and isActive lets you exit with a "
                        + "partial result instead of an exception. Pick one, but pick something: a loop with "
                        + "no check cannot be stopped",
                "«" + name + "» считает " + what + ", а затем вызывает ensureActive() — Job ещё активен, "
                        + "поэтому раунд продолжается. Именно эта строчка превращает неотменяемый цикл в "
                        + "отменяемый; yield() делает то же самое и вдобавок даёт диспетчеру возможность "
                        + "выполнить что-то ещё, а isActive позволяет выйти с частичным результатом вместо "
                        + "исключения. Выбирай любое, но выбери хоть что-то: цикл без проверки остановить "
                        + "нельзя",
                List.of("coroutines", "cancel"), state());
    }

    // ------------------------------------------------------------------ results

    /** {@code deferred.await()} — suspends until the other coroutine has a result. */
    public void await(String awaiter, String deferred) {
        Coroutine c = require(awaiter);
        Coroutine target = require(deferred);
        if ("COMPLETED".equals(target.state)) {
            resumes++;
            Trace.event("RESUMED",
                    "\"" + awaiter + "\" called " + deferred + ".await() and did not suspend at all — the "
                            + "Deferred was already complete, so await() returned the stored value like an "
                            + "ordinary getter. A suspend function is allowed to return without suspending, "
                            + "and most of them usually do; the suspension machinery only engages on the "
                            + "path where an answer is genuinely not there yet",
                    "«" + awaiter + "» вызвал " + deferred + ".await() и вообще не приостановился: Deferred "
                            + "уже был завершён, поэтому await() вернул сохранённое значение как обычный "
                            + "геттер. Suspend-функция имеет полное право вернуться без приостановки, и "
                            + "чаще всего так и происходит; машинерия приостановки включается только на том "
                            + "пути, где ответа действительно ещё нет",
                    List.of("coroutines"), state());
            return;
        }
        c.awaiting = deferred;
        c.suspensions++;
        suspensions++;
        c.state = "SUSPENDED";
        c.doing = deferred + ".await()";
        Trace.event("SUSPENDED",
                "\"" + awaiter + "\" suspended in " + deferred + ".await() and released thread " + c.thread
                        + ". This is the point of async: both children were started before anyone awaited, "
                        + "so they are running at the same time, and the coroutine that needs their results "
                        + "costs nothing while it waits. Write it the other way round — async then await, "
                        + "async then await — and you get the same code with the concurrency removed",
                "«" + awaiter + "» приостановился в " + deferred + ".await() и отпустил поток " + c.thread
                        + ". В этом и смысл async: оба ребёнка были запущены до первого await, поэтому они "
                        + "выполняются одновременно, а корутина, которой нужны их результаты, ничего не "
                        + "стоит, пока ждёт. Напиши наоборот — async, await, async, await — и получишь тот "
                        + "же код с вырезанной конкурентностью",
                List.of("coroutines"), state());
        release(c);
    }

    /** The coroutine's block returned normally: its Job is complete. */
    public void complete(String name) {
        Coroutine c = require(name);
        if (terminal(c)) {
            return;
        }
        boolean wasCancelled = c.cancelRequested;
        c.state = "COMPLETED";
        c.doing = null;
        completed++;
        Trace.event("COROUTINE_COMPLETED",
                "\"" + name + "\" returned and its Job is complete after " + c.suspensions
                        + " suspension(s). "
                        + (wasCancelled
                        ? "It was cancelled part-way through and finished anyway, because nothing in it "
                        + "ever checked — a cancelled Job that produces a full result is the clearest "
                        + "possible sign that cancellation is cooperative"
                        : "The parent scope can now cross it off; a scope completes only when every child "
                        + "has, which is why a coroutineScope block is allowed to return a value at all"),
                "«" + name + "» вернулся, и его Job завершён после приостановок: " + c.suspensions + ". "
                        + (wasCancelled
                        ? "Его отменили на полпути, а он всё равно досчитал, потому что внутри никто "
                        + "ничего не проверял: отменённый Job, выдавший полный результат, — самое "
                        + "наглядное доказательство того, что отмена кооперативна"
                        : "Родительская область может вычеркнуть его из списка; область завершается, "
                        + "только когда завершились все дети, — потому блок coroutineScope и может "
                        + "возвращать значение"),
                List.of("coroutines", "scopes"), state());
        release(c);
        wakeAwaiters(c);
        settleScope(c.scope);
    }

    /** The coroutine's block threw. Structured concurrency decides what follows. */
    public void fail(String name, String exception) {
        Coroutine c = require(name);
        if (terminal(c)) {
            return;
        }
        c.state = "FAILED";
        c.doing = exception;
        failed++;
        Scope scope = requireScope(c.scope);
        boolean supervised = "SUPERVISOR".equals(scope.kind);
        Trace.event("COROUTINE_FAILED",
                "\"" + name + "\" threw " + exception + ". An uncaught exception in a coroutine is not "
                        + "swallowed and is not left for a Thread.UncaughtExceptionHandler to find — it "
                        + "completes this Job exceptionally and is immediately reported to its parent, \""
                        + scope.name + "\". What the parent does with it is the only thing that differs "
                        + "between scope types, and it is decided by the scope, not by the code that threw",
                "«" + name + "» бросил " + exception + ". Непойманное исключение в корутине не "
                        + "проглатывается и не остаётся на откуп Thread.UncaughtExceptionHandler: оно "
                        + "завершает этот Job аварийно и немедленно сообщается родителю — «" + scope.name
                        + "». Что родитель с ним сделает, — единственное, чем различаются типы областей, и "
                        + "решает это область, а не бросивший код",
                List.of("coroutines", "scopes"), state());
        release(c);
        if (supervised) {
            scope.state = "ACTIVE";
            Trace.event("SUPERVISOR_ISOLATED",
                    "\"" + scope.name + "\" is a supervisorScope, so the failure stopped there: its "
                            + "siblings are untouched and the scope is still active. This is the shape you "
                            + "want when the children are independent — one failed widget on a dashboard, "
                            + "one unreachable service out of five. Note the price: the exception is now "
                            + "yours to handle, either by awaiting the Deferred or with a "
                            + "CoroutineExceptionHandler, or nobody ever hears about it",
                    "«" + scope.name + "» — это supervisorScope, поэтому падение на нём и остановилось: "
                            + "соседи не тронуты, область по-прежнему активна. Такая форма нужна, когда "
                            + "дети независимы: один упавший виджет на дашборде, один недоступный сервис из "
                            + "пяти. Заметь цену: исключение теперь твоё — либо ты забираешь его через "
                            + "await у Deferred, либо ставишь CoroutineExceptionHandler, либо о нём никто "
                            + "не узнает",
                    List.of("scopes"), state());
            return;
        }
        scope.state = "FAILED";
        cancelChildren(scope, name + " failed with " + exception, name + " упал с " + exception, name);
    }

    private void wakeAwaiters(Coroutine finishedTarget) {
        for (Coroutine other : new ArrayList<>(coroutines.values())) {
            if (finishedTarget.name.equals(other.awaiting) && "SUSPENDED".equals(other.state)) {
                other.awaiting = null;
                resume(other.name);
            }
        }
    }

    // ------------------------------------------------------------ cancellation

    /** {@code job.cancel()} on a coroutine, or on a whole scope. */
    public void cancel(String name) {
        Scope scope = scopes.get(name);
        if (scope != null) {
            Trace.event("CANCELLATION_REQUESTED",
                    "cancel() on the Job of scope \"" + name + "\". The call returns immediately and "
                            + "nothing has stopped yet: it marked the Job cancelling and is now walking down "
                            + "to every child. One call reaches an entire tree — this is the payoff of "
                            + "structured concurrency, and the reason a screen that goes away can take its "
                            + "eleven in-flight requests with it in one line",
                    "cancel() на Job области «" + name + "». Вызов вернулся сразу, и пока ничего не "
                            + "остановилось: он пометил Job отменяемым и теперь спускается к каждому "
                            + "ребёнку. Один вызов достаёт до всего дерева — в этом выигрыш структурной "
                            + "конкурентности и причина, по которой закрывшийся экран уносит с собой все "
                            + "одиннадцать своих запросов одной строкой",
                    List.of("scopes", "cancel"), state());
            scope.state = "CANCELLED";
            cancelChildren(scope, "scope " + name + " was cancelled", "область " + name + " отменена", null);
            reportLeaks(name);
            return;
        }
        Coroutine c = require(name);
        if (terminal(c)) {
            return;
        }
        c.cancelRequested = true;
        Trace.event("CANCELLATION_REQUESTED",
                "job.cancel() on \"" + name + "\" — and the coroutine is still " + c.state + ". cancel() is "
                        + "not stop(): it sets isActive to false and returns, which is why it is safe to "
                        + "call from anywhere and why it never leaves an object half-mutated the way "
                        + "Thread.stop did. Whether anything actually happens now depends entirely on "
                        + "whether this coroutine reaches a suspension point",
                "job.cancel() на «" + name + "» — а корутина по-прежнему в состоянии " + c.state
                        + ". cancel() — это не stop(): он выставляет isActive в false и возвращается, "
                        + "поэтому его безопасно звать откуда угодно и поэтому он никогда не оставляет "
                        + "объект наполовину изменённым, как это делал Thread.stop. Произойдёт ли что-то "
                        + "прямо сейчас, зависит только от того, дойдёт ли эта корутина до точки "
                        + "приостановки",
                List.of("coroutines", "cancel"), state());
        if ("SUSPENDED".equals(c.state)) {
            cancelHere(c, c.doing == null ? "its suspension point" : c.doing);
        }
    }

    /** The cancellation was actually delivered: the suspension point threw. */
    private void cancelHere(Coroutine c, String where) {
        c.state = "CANCELLED";
        c.doing = null;
        c.awaiting = null;
        c.cancelRequested = true;
        cancelled++;
        Trace.event("CANCELLED_AT_SUSPENSION_POINT",
                "\"" + c.name + "\" was sitting in " + where + ", and that is where cancellation is "
                        + "delivered: instead of resuming with a value, the continuation is resumed with a "
                        + "CancellationException, which unwinds the coroutine and runs its finally blocks. "
                        + "Two rules follow. Do not catch it — catching CancellationException, or catching "
                        + "Throwable without rethrowing it, is how a coroutine becomes uncancellable. And "
                        + "cleanup that itself suspends must run inside withContext(NonCancellable), "
                        + "because in a cancelled coroutine every other suspension point throws immediately",
                "«" + c.name + "» находился в " + where + ", и именно туда доставляется отмена: вместо "
                        + "значения continuation возобновляют исключением CancellationException, которое "
                        + "разматывает корутину и выполняет её блоки finally. Отсюда два правила. Не лови "
                        + "его: поймать CancellationException (или Throwable без проброса) — верный способ "
                        + "сделать корутину неотменяемой. И уборка, которая сама приостанавливается, должна "
                        + "выполняться внутри withContext(NonCancellable), потому что в отменённой корутине "
                        + "любая другая точка приостановки бросает сразу",
                List.of("coroutines", "cancel"), state());
        release(c);
    }

    private void cancelChildren(Scope scope, String reasonEn, String reasonRu, String except) {
        List<String> stopped = new ArrayList<>();
        List<String> stubborn = new ArrayList<>();
        for (String child : scope.children) {
            Coroutine c = coroutines.get(child);
            if (c == null || terminal(c) || child.equals(except)) {
                continue;
            }
            c.cancelRequested = true;
            if ("SUSPENDED".equals(c.state) || "QUEUED".equals(c.state)) {
                c.state = "CANCELLED";
                c.doing = null;
                c.awaiting = null;
                cancelled++;
                release(c);
                stopped.add(child);
            } else {
                stubborn.add(child);
            }
        }
        Trace.event("CHILDREN_CANCELLED",
                "Because " + reasonEn + ", the scope cancelled its children: "
                        + (stopped.isEmpty() ? "none were at a suspension point" : String.join(", ", stopped)
                        + " stopped at once, because a suspended coroutine is resumed with a "
                        + "CancellationException the moment its Job is cancelled")
                        + (stubborn.isEmpty() ? "" : ", while " + String.join(", ", stubborn)
                        + " only got the flag and keeps running until it reaches a check")
                        + ". This is the half of structured concurrency people forget: the guarantee is "
                        + "that the request reaches every child, not that every child obeys it promptly",
                "Поскольку " + reasonRu + ", область отменила своих детей: "
                        + (stopped.isEmpty() ? "ни один не был в точке приостановки"
                        : String.join(", ", stopped) + " остановились сразу, потому что приостановленную "
                        + "корутину возобновляют исключением CancellationException в тот же момент, когда "
                        + "отменяют её Job")
                        + (stubborn.isEmpty() ? "" : ", а " + String.join(", ", stubborn)
                        + " получил только флаг и работает дальше, пока не дойдёт до проверки")
                        + ". Вот та половина структурной конкурентности, о которой забывают: гарантируется, "
                        + "что просьба дойдёт до каждого ребёнка, а не что каждый ребёнок послушается сразу",
                List.of("scopes", "cancel"), state());
    }

    private void reportLeaks(String cancelledScope) {
        List<String> survivors = new ArrayList<>();
        for (Coroutine c : coroutines.values()) {
            if (GLOBAL.equals(c.scope) && !terminal(c)) {
                survivors.add(c.name);
            }
        }
        if (survivors.isEmpty()) {
            return;
        }
        leaked += survivors.size();
        Trace.event("GLOBAL_SCOPE_LEAK",
                "Cancelling \"" + cancelledScope + "\" stopped everything that belonged to it — and did "
                        + "nothing at all to " + String.join(", ", survivors) + ", because GlobalScope "
                        + "coroutines are children of no one. They are still holding their references, still "
                        + "waking up, still hitting the network after the screen that wanted the data is "
                        + "gone. Nothing here is a memory leak in the JVM sense; it is worse, because the "
                        + "work is alive and nobody owns it. Use a scope tied to a lifecycle instead",
                "Отмена «" + cancelledScope + "» остановила всё, что ей принадлежало, — и ровным счётом "
                        + "ничего не сделала с " + String.join(", ", survivors) + ", потому что корутины "
                        + "GlobalScope ничьи дети. Они по-прежнему держат свои ссылки, просыпаются и ходят "
                        + "в сеть после того, как экран, которому нужны были данные, уже закрыт. Это не "
                        + "утечка памяти в смысле JVM — это хуже, потому что работа жива и у неё нет "
                        + "владельца. Используй область, привязанную к жизненному циклу",
                List.of("scopes", "cancel"), state());
    }

    // ----------------------------------------------------------------- joining

    /** The end of a {@code coroutineScope { }} block: it cannot return early. */
    public void joinScope(String name) {
        Scope scope = requireScope(name);
        List<String> pending = new ArrayList<>();
        for (String child : scope.children) {
            Coroutine c = coroutines.get(child);
            if (c != null && !terminal(c)) {
                pending.add(child + " (" + c.state + ")");
            }
        }
        if (pending.isEmpty() && "ACTIVE".equals(scope.state)) {
            scope.state = "COMPLETED";
        }
        Trace.event("SCOPE_JOINED",
                pending.isEmpty()
                        ? "Every child of \"" + name + "\" has finished, so the coroutineScope block "
                        + "returns and the code after it runs — and only now. That ordering is a "
                        + "structural guarantee, not a convention you have to remember: you cannot "
                        + "accidentally read a result before the coroutine that produces it is done, and "
                        + "you cannot accidentally leave work running after the function that started it "
                        + "returned"
                        : "\"" + name + "\" is not finished: " + String.join(", ", pending) + " still "
                        + "active. The block is suspended here, not blocked — the thread it was on is "
                        + "back in the dispatcher doing other work. This is what makes structured "
                        + "concurrency free: waiting for children costs an object, not a parked thread",
                pending.isEmpty()
                        ? "Все дети «" + name + "» завершились, поэтому блок coroutineScope возвращается и "
                        + "код после него выполняется — и только теперь. Этот порядок гарантирован "
                        + "структурой, а не соглашением, которое надо помнить: нельзя случайно прочитать "
                        + "результат раньше, чем закончится создающая его корутина, и нельзя случайно "
                        + "оставить работу выполняться после возврата запустившей её функции"
                        : "«" + name + "» не завершён: ещё активны " + String.join(", ", pending)
                        + ". Блок здесь приостановлен, а не заблокирован — поток, на котором он был, "
                        + "вернулся в диспетчер и занят другой работой. Именно поэтому структурная "
                        + "конкурентность бесплатна: ожидание детей стоит объекта, а не запаркованного "
                        + "потока",
                List.of("scopes"), state());
    }

    private void settleScope(String name) {
        Scope scope = scopes.get(name);
        if (scope == null || !"ACTIVE".equals(scope.state)) {
            return;
        }
        for (String child : scope.children) {
            Coroutine c = coroutines.get(child);
            if (c != null && !terminal(c)) {
                return;
            }
        }
        if (!ROOT.equals(name) && !GLOBAL.equals(name) && !scope.children.isEmpty()) {
            scope.state = "COMPLETED";
        }
    }

    // ----------------------------------------------------------------- reports

    /** Prints what this run cost and what is still alive. */
    public void report() {
        Trace.event("COROUTINE_AUDIT",
                "Coroutines launched " + launched + ", still alive " + alive() + ", peak alive " + peakAlive
                        + ", worker threads " + totalThreads() + ", suspensions " + suspensions
                        + ", resumes " + resumes + ", dispatches " + dispatches + ", times queued "
                        + queuedTimes + ", context switches " + contextSwitches + ", blocking calls "
                        + blockingCalls + ", completed " + completed + ", cancelled " + cancelled
                        + ", failed " + failed + ", orphaned in GlobalScope " + leaked
                        + ". The two numbers to compare are the coroutines and the threads",
                "Корутин запущено " + launched + ", ещё живо " + alive() + ", пик живых " + peakAlive
                        + ", рабочих потоков " + totalThreads() + ", приостановок " + suspensions
                        + ", возобновлений " + resumes + ", диспетчеризаций " + dispatches
                        + ", попаданий в очередь " + queuedTimes + ", смен контекста " + contextSwitches
                        + ", блокирующих вызовов " + blockingCalls + ", завершено " + completed
                        + ", отменено " + cancelled + ", упало " + failed + ", осиротело в GlobalScope "
                        + leaked + ". Сравнивать здесь стоит два числа: корутины и потоки",
                List.of("stats"), state());
    }

    /**
     * Prices the same number of concurrent tasks as platform threads, as
     * coroutines and as Java 21 virtual threads.
     */
    public static void compareScale(int count) {
        List<Object> rows = new ArrayList<>();
        rows.add(scaleRow("PLATFORM_THREADS", count, count * PLATFORM_THREAD_BYTES, count, "none", false));
        rows.add(scaleRow("COROUTINES", count, count * COROUTINE_BYTES, 4, "suspend", true));
        rows.add(scaleRow("VIRTUAL_THREADS", count, count * VIRTUAL_THREAD_BYTES, 4, "none", true));

        Trace.event("SCALE_COMPARED",
                count + " concurrent tasks, three ways. As platform threads that is about "
                        + megabytes(count * PLATFORM_THREAD_BYTES) + " MB of reserved stack and " + count
                        + " OS threads, which no JVM will give you — the practical ceiling is a few "
                        + "thousand, and long before that the scheduler spends its time context switching. "
                        + "As coroutines it is " + megabytes(count * COROUTINE_BYTES) + " MB of small "
                        + "objects on four threads, because a suspended coroutine is a continuation object "
                        + "and nothing else: no stack, no kernel entry, no context switch to resume it. "
                        + "Java 21 virtual threads reach the same place from the other direction — the "
                        + "runtime, not the compiler, so there is no suspend keyword and no function "
                        + "colouring, at the cost of a slightly larger per-task footprint and no built-in "
                        + "structured cancellation",
                count + " конкурентных задач тремя способами. В виде платформенных потоков это примерно "
                        + megabytes(count * PLATFORM_THREAD_BYTES) + " МБ зарезервированного стека и "
                        + count + " потоков ОС, которых не даст ни одна JVM: практический потолок — "
                        + "несколько тысяч, и задолго до него планировщик начинает тратить время на "
                        + "переключения контекста. В виде корутин это " + megabytes(count * COROUTINE_BYTES)
                        + " МБ мелких объектов на четырёх потоках, потому что приостановленная корутина — "
                        + "это объект continuation и больше ничего: ни стека, ни входа в ядро, ни "
                        + "переключения контекста для возобновления. Виртуальные потоки Java 21 приходят "
                        + "туда же с другой стороны — через рантайм, а не компилятор, поэтому нет ни "
                        + "ключевого слова suspend, ни «окраски» функций, ценой чуть большего расхода на "
                        + "задачу и отсутствия встроенной структурной отмены",
                List.of("scale"), scaleState(rows));
    }

    // ------------------------------------------------- the compiler's transform

    /** Starts describing a {@code suspend fun} so its state machine can be walked. */
    public static SuspendFun suspendFun(String name) {
        return new SuspendFun(name);
    }

    /** One suspension point inside a described {@code suspend fun}. */
    private static final class Step {

        private final String call;
        private final String resultName;
        private final String resultValue;

        private Step(String call, String resultName, String resultValue) {
            this.call = call;
            this.resultName = resultName;
            this.resultValue = resultValue;
        }
    }

    /**
     * A {@code suspend fun} described well enough to show what the Kotlin
     * compiler turns it into, and to walk that state machine step by step.
     */
    public static final class SuspendFun {

        private final String name;
        private final Map<String, String> locals = new LinkedHashMap<>();
        private final List<Step> steps = new ArrayList<>();
        private String returned = "Unit";

        private SuspendFun(String name) {
            this.name = name;
        }

        /** A local variable declared before the first suspension point. */
        public SuspendFun local(String variable, String value) {
            locals.put(variable, value);
            return this;
        }

        /** A call to another suspend function — a suspension point. */
        public SuspendFun await(String call, String resultName, String resultValue) {
            steps.add(new Step(call, resultName, resultValue));
            return this;
        }

        /** What the function returns once the last suspension point is past. */
        public SuspendFun returns(String value) {
            returned = value;
            return this;
        }

        /** Emits the generated class, then executes it label by label. */
        public void run() {
            String className = name + "$1";
            Map<String, String> saved = new LinkedHashMap<>(locals);

            Trace.event("SUSPEND_FUN_COMPILED",
                    "The compiler rewrote suspend fun " + name + "(...) into an ordinary function with one "
                            + "extra, invisible parameter — a Continuation — and generated the class "
                            + className + " to be that continuation. Inside it: an int label saying where to "
                            + "resume, one field per local that has to survive a suspension ("
                            + (locals.isEmpty() ? "none here" : String.join(", ", locals.keySet()))
                            + "), and invokeSuspend, whose body is a when (label) over " + (steps.size() + 1)
                            + " cases. There is no magic left after this point: what the runtime schedules "
                            + "is calls to invokeSuspend on this object",
                    "Компилятор переписал suspend fun " + name + "(...) в обычную функцию с одним лишним, "
                            + "невидимым параметром — Continuation — и сгенерировал класс " + className
                            + ", который этим continuation и является. Внутри: int label, говорящий, откуда "
                            + "продолжать, по полю на каждую локальную переменную, которая должна пережить "
                            + "приостановку (" + (locals.isEmpty() ? "здесь таких нет"
                            : String.join(", ", locals.keySet())) + "), и invokeSuspend, тело которого — "
                            + "when (label) на " + (steps.size() + 1) + " ветк(и). Никакой магии дальше "
                            + "нет: рантайм планирует именно вызовы invokeSuspend на этом объекте",
                    List.of("machine"), machineState(this, className, -1, saved, false, null));

            for (int label = 0; label < steps.size(); label++) {
                Step step = steps.get(label);
                Trace.event("STATE_MACHINE_STEP",
                        "invokeSuspend runs, when (label) picks case " + label + ", and execution continues "
                                + "from exactly the statement after the previous suspension point. Before "
                                + "calling " + step.call + " the generated code sets label = " + (label + 1)
                                + " — it writes down where to come back BEFORE it leaves, which is the only "
                                + "bookkeeping a continuation needs. Then it compares the call's result "
                                + "against COROUTINE_SUSPENDED",
                        "invokeSuspend выполняется, when (label) выбирает ветку " + label + ", и исполнение "
                                + "продолжается ровно с оператора, следующего за предыдущей точкой "
                                + "приостановки. Перед вызовом " + step.call + " сгенерированный код "
                                + "выставляет label = " + (label + 1) + ": он записывает, куда вернуться, "
                                + "ДО того как уйти, — и это единственная бухгалтерия, нужная continuation. "
                                + "Затем он сравнивает результат вызова с COROUTINE_SUSPENDED",
                        List.of("machine"), machineState(this, className, label, saved, false, null));

                Trace.event("SUSPENDED",
                        step.call + " had no answer ready, so it returned COROUTINE_SUSPENDED and "
                                + "invokeSuspend returned that marker to its caller — which returns too, all "
                                + "the way out. The JVM stack frames of " + name + " are gone; what survives "
                                + "is " + className + " on the heap with label = " + (label + 1) + " and "
                                + (saved.isEmpty() ? "no captured locals" : "the captured locals ("
                                + String.join(", ", saved.keySet()) + ")")
                                + ". The thread that was running this is now free to run anything else, "
                                + "which is the entire difference from a blocking call",
                        step.call + " не имел готового ответа, поэтому вернул COROUTINE_SUSPENDED, а "
                                + "invokeSuspend вернул этот маркер вызвавшему — который тоже вернулся, и "
                                + "так до самого верха. Кадры стека JVM для " + name + " исчезли; выжил "
                                + className + " в куче с label = " + (label + 1) + " и "
                                + (saved.isEmpty() ? "без захваченных локальных переменных"
                                : "захваченными локальными переменными (" + String.join(", ", saved.keySet())
                                + ")")
                                + ". Поток, который это выполнял, свободен и может заняться чем угодно — в "
                                + "этом и вся разница с блокирующим вызовом",
                        List.of("machine"), machineState(this, className, label, saved, true, null));

                saved.put(step.resultName, step.resultValue);

                Trace.event("RESUMED",
                        "The network call finished and its callback called continuation.resumeWith("
                                + "Result.success(" + step.resultValue + ")). That calls invokeSuspend on "
                                + "the SAME " + className + " object again, possibly on a different thread; "
                                + "the result is assigned to " + step.resultName + " and when (label) jumps "
                                + "straight to case " + (label + 1) + ". Nothing re-executed and nothing "
                                + "was re-entered from the top: a coroutine resumes at a statement, not at "
                                + "a function",
                        "Сетевой вызов завершился, и его колбэк вызвал continuation.resumeWith("
                                + "Result.success(" + step.resultValue + ")). Это снова вызывает "
                                + "invokeSuspend на ТОМ ЖЕ объекте " + className + ", возможно уже на "
                                + "другом потоке; результат присваивается в " + step.resultName + ", а "
                                + "when (label) прыгает сразу в ветку " + (label + 1) + ". Ничего не "
                                + "выполнилось заново и ничто не входило с начала: корутина возобновляется "
                                + "с оператора, а не с функции",
                        List.of("machine"), machineState(this, className, label + 1, saved, false, null));
            }

            Trace.event("STATE_MACHINE_STEP",
                    "Case " + steps.size() + " is the tail of the function: no suspension point is left, so "
                            + "it simply computes " + returned + " and returns it as a real value rather "
                            + "than the marker. The caller's own state machine sees a value instead of "
                            + "COROUTINE_SUSPENDED and carries straight on — which is why suspend functions "
                            + "compose like ordinary calls, and why sequential-looking code is exactly what "
                            + "it looks like",
                    "Ветка " + steps.size() + " — это хвост функции: точек приостановки больше нет, "
                            + "поэтому она просто вычисляет " + returned + " и возвращает его настоящим "
                            + "значением, а не маркером. Машина состояний вызвавшего видит значение вместо "
                            + "COROUTINE_SUSPENDED и продолжает без остановки — поэтому suspend-функции "
                            + "складываются как обычные вызовы, а последовательно выглядящий код именно "
                            + "тем и является",
                    List.of("machine"), machineState(this, className, steps.size(), saved, false, null));

            Trace.event("COROUTINE_COMPLETED",
                    name + " returned " + returned + " after " + steps.size() + " suspension(s), and the "
                            + className + " object is now garbage. Count what this cost: one object, "
                            + steps.size() + " extra virtual call(s), and not a single parked thread. That "
                            + "is why a hundred thousand of these fit in a process where a hundred thousand "
                            + "threads do not",
                    name + " вернул " + returned + " после приостановок: " + steps.size()
                            + ", и объект " + className + " стал мусором. Посчитай цену: один объект, "
                            + "лишних виртуальных вызовов " + steps.size() + " — и ни одного "
                            + "запаркованного потока. Поэтому сто тысяч таких помещаются в процесс, в "
                            + "который сто тысяч потоков не помещаются",
                    List.of("machine"), machineState(this, className, steps.size(), saved, false, returned));
        }
    }

    // ------------------------------------------------------------- state shape

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        List<Object> pools = new ArrayList<>();
        for (Dispatcher d : dispatchers.values()) {
            Map<String, Object> pool = new LinkedHashMap<>();
            pool.put("name", d.name);
            List<Object> threads = new ArrayList<>();
            for (Worker w : d.workers) {
                Map<String, Object> thread = new LinkedHashMap<>();
                thread.put("name", w.name);
                thread.put("coroutine", w.coroutine);
                thread.put("blocked", w.blocked);
                threads.add(thread);
            }
            pool.put("threads", threads);
            pool.put("queue", new ArrayList<>(d.queue));
            pools.add(pool);
        }
        s.put("dispatchers", pools);

        List<Object> jobs = new ArrayList<>();
        for (Coroutine c : coroutines.values()) {
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("name", c.name);
            job.put("scope", c.scope);
            job.put("kind", c.kind);
            job.put("dispatcher", c.dispatcher);
            job.put("state", c.state);
            job.put("thread", c.thread);
            job.put("doing", c.doing);
            job.put("cancelRequested", c.cancelRequested);
            job.put("awaiting", c.awaiting);
            jobs.add(job);
        }
        s.put("coroutines", jobs);

        List<Object> tree = new ArrayList<>();
        for (Scope scope : scopes.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", scope.name);
            node.put("kind", scope.kind);
            node.put("state", scope.state);
            node.put("children", new ArrayList<>(scope.children));
            tree.add(node);
        }
        s.put("scopes", tree);

        s.put("machine", null);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("launched", launched);
        stats.put("alive", alive());
        stats.put("peakAlive", peakAlive);
        stats.put("threads", totalThreads());
        stats.put("suspensions", suspensions);
        stats.put("resumes", resumes);
        stats.put("dispatches", dispatches);
        stats.put("queued", queuedTimes);
        stats.put("contextSwitches", contextSwitches);
        stats.put("blockingCalls", blockingCalls);
        stats.put("completed", completed);
        stats.put("cancelled", cancelled);
        stats.put("failed", failed);
        stats.put("leaked", leaked);
        s.put("stats", stats);

        s.put("scale", new ArrayList<>());
        return s;
    }

    /** A well-formed state whose only populated part is the state machine. */
    private static Object machineState(SuspendFun fun, String className, int label,
                                       Map<String, String> saved, boolean suspended, String returned) {
        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("function", fun.name);
        machine.put("className", className);
        machine.put("label", label);

        List<Object> cases = new ArrayList<>();
        for (int i = 0; i < fun.steps.size(); i++) {
            Step step = fun.steps.get(i);
            String prefix = i == 0 ? "" : fun.steps.get(i - 1).resultName + " = result; ";
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", i);
            row.put("code", prefix + "label = " + (i + 1) + "; if (" + step.call
                    + " == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED");
            row.put("current", i == label);
            cases.add(row);
        }
        Map<String, Object> tail = new LinkedHashMap<>();
        tail.put("label", fun.steps.size());
        tail.put("code", (fun.steps.isEmpty() ? "" : fun.steps.get(fun.steps.size() - 1).resultName
                + " = result; ") + "return " + fun.returned);
        tail.put("current", label == fun.steps.size());
        cases.add(tail);
        machine.put("cases", cases);

        List<Object> fields = new ArrayList<>();
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", entry.getKey());
            field.put("value", entry.getValue());
            fields.add(field);
        }
        machine.put("saved", fields);
        machine.put("suspended", suspended);
        machine.put("returned", returned);

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("dispatchers", new ArrayList<>());
        s.put("coroutines", new ArrayList<>());
        s.put("scopes", new ArrayList<>());
        s.put("machine", machine);
        s.put("stats", emptyStats());
        s.put("scale", new ArrayList<>());
        return s;
    }

    /** A well-formed state whose only populated part is the scale comparison. */
    private static Object scaleState(List<Object> rows) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("dispatchers", new ArrayList<>());
        s.put("coroutines", new ArrayList<>());
        s.put("scopes", new ArrayList<>());
        s.put("machine", null);
        s.put("stats", emptyStats());
        s.put("scale", rows);
        return s;
    }

    private static Map<String, Object> scaleRow(String model, int count, long bytes, int osThreads,
                                                String keyword, boolean feasible) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("model", model);
        row.put("count", count);
        row.put("memoryMb", megabytes(bytes));
        row.put("osThreads", osThreads);
        row.put("keyword", keyword);
        row.put("feasible", feasible);
        return row;
    }

    private static Map<String, Object> emptyStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("launched", 0);
        stats.put("alive", 0);
        stats.put("peakAlive", 0);
        stats.put("threads", 0);
        stats.put("suspensions", 0);
        stats.put("resumes", 0);
        stats.put("dispatches", 0);
        stats.put("queued", 0);
        stats.put("contextSwitches", 0);
        stats.put("blockingCalls", 0);
        stats.put("completed", 0);
        stats.put("cancelled", 0);
        stats.put("failed", 0);
        stats.put("leaked", 0);
        return stats;
    }

    private static long megabytes(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------- housekeeping

    private static boolean terminal(Coroutine c) {
        return "COMPLETED".equals(c.state) || "CANCELLED".equals(c.state) || "FAILED".equals(c.state);
    }

    private int alive() {
        int count = 0;
        for (Coroutine c : coroutines.values()) {
            if (!terminal(c)) {
                count++;
            }
        }
        return count;
    }

    private int totalThreads() {
        int count = 0;
        for (Dispatcher d : dispatchers.values()) {
            count += d.workers.size();
        }
        return count;
    }

    private static boolean allBlocked(Dispatcher d) {
        for (Worker w : d.workers) {
            if (!w.blocked) {
                return false;
            }
        }
        return true;
    }

    private Coroutine require(String name) {
        Coroutine c = coroutines.get(name);
        if (c == null) {
            throw new IllegalStateException("no coroutine named " + name + " has been launched");
        }
        return c;
    }

    private Scope requireScope(String name) {
        Scope scope = scopes.get(name);
        if (scope == null) {
            throw new IllegalStateException("no scope named " + name + " has been opened");
        }
        return scope;
    }

    private Dispatcher requireDispatcher(String name) {
        Dispatcher d = dispatchers.get(name);
        if (d == null) {
            throw new IllegalStateException("no dispatcher named " + name + "; use Default, IO or Main");
        }
        return d;
    }
}
