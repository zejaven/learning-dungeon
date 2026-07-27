package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the collision between data that arrives
 * <strong>asynchronously</strong> (an exchange rate pushed by another service)
 * and a decision that is <strong>synchronous</strong> (a checkout that must be
 * priced right now).
 *
 * <p>The model owns three things:
 * <ul>
 *   <li>the <em>rate service</em> — the service that owns the data and publishes
 *       it; it can go down and its feed can stop flowing, independently;</li>
 *   <li>the <em>feed</em> — the events it published, each with a version and the
 *       instant it was produced, and whether the consumer applied, ignored or
 *       has not yet received them;</li>
 *   <li>the <em>local replica</em> — the pricing service's own copy of the data,
 *       which is what a synchronous decision actually reads.</li>
 * </ul>
 *
 * <p>How the decision point gets its value is chosen with a factory and never
 * changes afterwards:
 * <ul>
 *   <li>{@link #callingOnEveryDecision()} — a blocking call to the owner inside
 *       the decision: always current, and it inherits the owner's latency and
 *       availability;</li>
 *   <li>{@link #cachingLocally(int)} — a read-through cache with a TTL: most
 *       decisions are local, the TTL bounds how wrong the copy may be, and a
 *       refresh that cannot reach the owner has to fall back to something;</li>
 *   <li>{@link #withEventCarriedState()} — event-carried state transfer: the
 *       owner publishes every change, the consumer keeps a local replica, and
 *       the decision never leaves the process.</li>
 * </ul>
 *
 * <p>What makes the difference between a working system and a quiet one is the
 * declared freshness requirement: with none, {@link #price(String, int)} happily
 * prices money on an hour-old rate and nothing anywhere fails. {@link
 * #flagStaleAfter(int)} makes that visible, {@link #refuseStaleAfter(int)} makes
 * it fail closed. The other failure modes modelled explicitly are the ones that
 * decide real designs: {@link #feedStops()} (the owner keeps publishing, the
 * consumer stops receiving), {@link #redeliver(String, int)} (at-least-once
 * duplicates and out-of-order delivery), and {@link #restartInstance()} followed
 * by {@link #rebuildFromSnapshot()} (the cold-start problem of an in-memory
 * replica).
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the
 * UI can replay the stream without re-running the code. All money is integer
 * cents and every rate is an integer scaled by 10 000, so runs are exactly
 * reproducible. The model is intentionally dependency-free.
 */
public class VisualRateFeed {

    /** Rates are integers scaled by this factor: 10800 means 1.0800. */
    public static final int RATE_SCALE = 10_000;

    /** Where a synchronous decision gets the asynchronously-owned value. */
    private enum Strategy {
        /** A blocking call to the owning service inside the decision. */
        SYNC_CALL,
        /** A locally cached copy with a TTL, refreshed on demand. */
        TTL_CACHE,
        /** A local replica kept up to date by the owner's events. */
        EVENT_CARRIED
    }

    /** What a decision does with a value it knows is past its declared limit. */
    private enum StalePolicy {
        /** Use it anyway and flag the quote — fail open. */
        SERVE,
        /** Refuse the decision — fail closed. */
        REFUSE
    }

    private final Strategy strategy;
    private final int ttlSeconds;

    private int budgetSeconds;
    private StalePolicy policy = StalePolicy.SERVE;

    /** Seconds since the model was created. Moved only by advanceSeconds. */
    private int clock;
    private boolean serviceUp = true;
    private boolean delivering = true;

    /** What the owner currently knows, per pair — its version of the truth. */
    private final Map<String, Rate> published = new LinkedHashMap<>();
    /** Events the owner published, in publication order, with their fate. */
    private final List<Delivery> feed = new ArrayList<>();
    /** The consumer's own copy — what a synchronous decision actually reads. */
    private final Map<String, Local> replica = new LinkedHashMap<>();
    /** Decisions already made, each pinned to the rate it was made with. */
    private final List<Quote> quotes = new ArrayList<>();

    /** The decision being made (null before the first one). */
    private Decision decision;

    private int quoted;
    private int staleQuoted;
    private int refused;
    private int remoteCalls;
    private int cacheHits;
    private int eventsApplied;
    private int eventsIgnored;
    private int oldestRateUsed;

    private VisualRateFeed(Strategy strategy, int ttlSeconds) {
        this.strategy = strategy;
        this.ttlSeconds = ttlSeconds;
        Trace.event("FEED_READY", readyEn(), readyRu(), List.of(), state());
    }

    /** The decision calls the owning service every time it needs the value. */
    public static VisualRateFeed callingOnEveryDecision() {
        return new VisualRateFeed(Strategy.SYNC_CALL, 0);
    }

    /** The value is cached locally for {@code ttlSeconds} and refetched on expiry. */
    public static VisualRateFeed cachingLocally(int ttlSeconds) {
        return new VisualRateFeed(Strategy.TTL_CACHE, ttlSeconds);
    }

    /** The owner pushes every change; the consumer decides from its own replica. */
    public static VisualRateFeed withEventCarriedState() {
        return new VisualRateFeed(Strategy.EVENT_CARRIED, 0);
    }

    // ------------------------------------------------------- freshness policy

    /**
     * Declares how old the data may be and refuses the decision beyond that —
     * fail closed. This is the default for anything that moves money.
     */
    public void refuseStaleAfter(int seconds) {
        this.budgetSeconds = seconds;
        this.policy = StalePolicy.REFUSE;
        Trace.event("FRESHNESS_POLICY_SET",
                "Freshness requirement: data older than " + seconds
                        + "s may not back a decision — such a decision is refused (fail closed)",
                "Требование к свежести: данные старше " + seconds
                        + "с не могут обосновывать решение — такое решение отклоняется (fail closed)",
                List.of(), state());
    }

    /**
     * Declares how old the data may be but still uses it beyond that, flagging
     * the decision — fail open, a deliberate degraded mode.
     */
    public void flagStaleAfter(int seconds) {
        this.budgetSeconds = seconds;
        this.policy = StalePolicy.SERVE;
        Trace.event("FRESHNESS_POLICY_SET",
                "Freshness requirement: data older than " + seconds
                        + "s is still used, but every such decision is flagged as degraded (fail open)",
                "Требование к свежести: данные старше " + seconds
                        + "с всё ещё используются, но каждое такое решение помечается как деградация (fail open)",
                List.of(), state());
    }

    // ---------------------------------------------------------- the data owner

    /**
     * The owning service records a new value for the pair and, when this is an
     * event-carried setup, publishes it. The consumer only learns about it if
     * the feed is actually flowing.
     */
    public void publishRate(String pair, int rate) {
        Rate previous = published.get(pair);
        int version = previous == null ? 1 : previous.version + 1;
        published.put(pair, new Rate(pair, rate, version, clock));

        if (strategy != Strategy.EVENT_CARRIED) {
            Trace.event("RATE_PUBLISHED",
                    "rate-service now says " + pair + " = " + rateText(rate) + " (version " + version
                            + "). Nothing pushes it anywhere — whoever needs it has to come and ask",
                    "rate-service теперь считает " + pair + " = " + rateText(rate) + " (версия " + version
                            + "). Никуда это не отправляется — кому нужно, тот придёт и спросит",
                    List.of("service", "pair:" + pair), state());
            return;
        }

        Delivery event = new Delivery(version, pair, rate, clock, delivering ? "applied" : "pending");
        feed.add(event);
        Trace.event("RATE_PUBLISHED",
                "rate-service publishes " + pair + " = " + rateText(rate) + " (version " + version
                        + ", produced at t=" + clock + "s)"
                        + (delivering ? "" : " — but the feed is stopped, so the event only sits in the log"),
                "rate-service публикует " + pair + " = " + rateText(rate) + " (версия " + version
                        + ", создано в t=" + clock + "с)"
                        + (delivering ? "" : " — но поток остановлен, поэтому событие просто лежит в логе"),
                List.of("service", "event:" + pair + ":" + version), state());
        if (delivering) {
            apply(event);
        }
    }

    /**
     * The broker hands the consumer a past event again: the same version (an
     * at-least-once duplicate) or an older one (out-of-order delivery after a
     * rebalance). Only meaningful for an event-carried setup.
     */
    public void redeliver(String pair, int version) {
        if (strategy != Strategy.EVENT_CARRIED) {
            return;
        }
        Delivery original = null;
        for (Delivery event : feed) {
            if (event.pair.equals(pair) && event.version == version) {
                original = event;
            }
        }
        if (original == null) {
            return;
        }
        Delivery again = new Delivery(version, pair, original.rate, original.publishedAt, "pending");
        feed.add(again);
        Trace.event("EVENT_REDELIVERED",
                "The broker delivers " + pair + " version " + version + " (" + rateText(original.rate)
                        + ", produced at t=" + original.publishedAt + "s) again",
                "Брокер снова доставляет " + pair + " версии " + version + " (" + rateText(original.rate)
                        + ", создано в t=" + original.publishedAt + "с)",
                List.of("event:" + pair + ":" + version), state());
        apply(again);
    }

    /** The owning service becomes unreachable: calls to it now fail. */
    public void rateServiceDown() {
        serviceUp = false;
        Trace.event("RATE_SERVICE_DOWN",
                "rate-service is down. Whether that stops checkout depends entirely on whether "
                        + "checkout needs it to be up",
                "rate-service недоступен. Остановит ли это оформление заказа — зависит только от того, "
                        + "нужна ли оформлению его доступность",
                List.of("service"), state());
    }

    /** The owning service is reachable again. */
    public void rateServiceUp() {
        serviceUp = true;
        Trace.event("RATE_SERVICE_UP",
                "rate-service is reachable again",
                "rate-service снова доступен",
                List.of("service"), state());
    }

    /**
     * The stream stops reaching this consumer — a lagging consumer group, a
     * partitioned broker, a dead listener thread. The owner keeps publishing.
     */
    public void feedStops() {
        delivering = false;
        Trace.event("FEED_STOPPED",
                "The rate feed stops reaching this instance. Nothing throws: the local replica simply "
                        + "stops changing, and every decision keeps succeeding on the last value it saw",
                "Поток курсов перестаёт доходить до этого инстанса. Ничего не падает: локальная реплика "
                        + "просто перестаёт меняться, а решения продолжают приниматься по последнему значению",
                List.of("feed"), state());
    }

    /** The stream flows again and the backlog is delivered in order. */
    public void feedResumes() {
        delivering = true;
        List<Delivery> backlog = new ArrayList<>();
        for (Delivery event : feed) {
            if ("pending".equals(event.status)) {
                backlog.add(event);
            }
        }
        Trace.event("FEED_RESUMED",
                "The feed flows again: " + backlog.size() + " queued event(s) are delivered in order",
                "Поток снова идёт: " + backlog.size() + " накопленных событий доставляются по порядку",
                List.of("feed"), state());
        for (Delivery event : backlog) {
            apply(event);
        }
    }

    // ------------------------------------------------------- the consumer side

    /**
     * The pricing service is redeployed. An in-memory replica does not survive
     * that, so every decision now has nothing local to read.
     */
    public void restartInstance() {
        replica.clear();
        decision = null;
        Trace.event("COLD_START",
                "The pricing service restarts. Its replica lived in memory, so the new instance boots "
                        + "with nothing: it knows no rates until the stream refills it",
                "Сервис расчёта перезапускается. Его реплика жила в памяти, поэтому новый инстанс "
                        + "стартует пустым: он не знает ни одного курса, пока поток его не наполнит",
                List.of("replica"), state());
    }

    /**
     * The consumer replays the compacted topic (or loads a snapshot) and gets the
     * last value of every key back, with the version and timestamp it was
     * published with. This reads the log, not the owning service.
     */
    public void rebuildFromSnapshot() {
        for (Rate rate : published.values()) {
            replica.put(rate.pair, new Local(rate.pair, rate.rate, rate.version, rate.publishedAt));
        }
        Trace.event("REPLICA_REBUILT",
                "The consumer replays the compacted topic from the beginning: " + replica.size()
                        + " pair(s) restored, each with the version and timestamp it was published with. "
                        + "This reads the log, so it works even while rate-service itself is down",
                "Консьюмер перечитывает компактифицированный топик с начала: восстановлено пар — "
                        + replica.size() + ", каждая со своей версией и временем публикации. "
                        + "Читается лог, поэтому это работает даже когда сам rate-service лежит",
                List.of("replica"), state());
    }

    // ------------------------------------------------- the synchronous decision

    /**
     * The synchronous decision point: price {@code amountCents} of the pair's
     * base currency and pin the rate that was used onto the quote.
     *
     * @return {@code true} when a quote was produced (fresh or flagged stale)
     */
    public boolean price(String pair, int amountCents) {
        decision = new Decision(pair, amountCents);
        switch (strategy) {
            case SYNC_CALL:
                return priceByCall(pair, amountCents);
            case TTL_CACHE:
                return priceByCache(pair, amountCents);
            default:
                return priceByReplica(pair, amountCents);
        }
    }

    /** Moves the clock forward, which is the only thing that makes data stale. */
    public void advanceSeconds(int seconds) {
        clock += seconds;
        Trace.event("TIME_PASSED",
                "Time passes: it is now t=" + clock + "s",
                "Время идёт: сейчас t=" + clock + "с",
                List.of(), state());
    }

    /** Prints the audit that tells you whether any of this actually worked. */
    public void report() {
        Trace.event("RATE_AUDIT",
                "Audit: " + quotes.size() + " quote(s), " + staleQuoted + " of them on data flagged stale, "
                        + refused + " decision(s) refused; blocking calls inside a decision: " + remoteCalls
                        + ", cache hits: " + cacheHits + ", events applied: " + eventsApplied
                        + ", events ignored: " + eventsIgnored + "; the oldest rate any quote was based on was "
                        + oldestRateUsed + "s old",
                "Сверка: расчётов — " + quotes.size() + ", из них по данным с пометкой «устарело» — "
                        + staleQuoted + ", отклонённых решений — " + refused
                        + "; блокирующих вызовов внутри решения: " + remoteCalls + ", попаданий в кэш: "
                        + cacheHits + ", применённых событий: " + eventsApplied + ", отброшенных событий: "
                        + eventsIgnored + "; самый старый курс, по которому был сделан расчёт, — "
                        + oldestRateUsed + "с",
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    /** Applies one delivered event to the local replica, version check first. */
    private void apply(Delivery event) {
        Local local = replica.get(event.pair);
        if (local != null && event.version <= local.version) {
            event.status = "ignored";
            eventsIgnored++;
            boolean duplicate = event.version == local.version;
            Trace.event("STALE_EVENT_IGNORED",
                    duplicate
                            ? "Version " + event.version + " of " + event.pair + " is already applied — "
                                    + "the redelivery changes nothing, which is what makes applying events "
                                    + "by version idempotent"
                            : "Version " + event.version + " of " + event.pair + " arrives after version "
                                    + local.version + " — it is dropped. Applying it would overwrite a newer "
                                    + "rate with an older one",
                    duplicate
                            ? "Версия " + event.version + " для " + event.pair + " уже применена — "
                                    + "повторная доставка ничего не меняет, именно это делает применение "
                                    + "событий по версии идемпотентным"
                            : "Версия " + event.version + " для " + event.pair + " приходит после версии "
                                    + local.version + " — она отбрасывается. Применить её значило бы "
                                    + "перезаписать более новый курс более старым",
                    List.of("event:" + event.pair + ":" + event.version, "replica:" + event.pair), state());
            return;
        }
        replica.put(event.pair, new Local(event.pair, event.rate, event.version, event.publishedAt));
        event.status = "applied";
        eventsApplied++;
        Trace.event("RATE_APPLIED",
                "The local replica now holds " + event.pair + " = " + rateText(event.rate) + " (version "
                        + event.version + ", as of t=" + event.publishedAt + "s). No decision had to wait "
                        + "for this to happen",
                "Локальная реплика теперь хранит " + event.pair + " = " + rateText(event.rate) + " (версия "
                        + event.version + ", по состоянию на t=" + event.publishedAt + "с). Ни одно решение "
                        + "этого не ждало",
                List.of("event:" + event.pair + ":" + event.version, "replica:" + event.pair), state());
    }

    /** Strategy 1: ask the owner, inside the decision, every single time. */
    private boolean priceByCall(String pair, int amountCents) {
        remoteCalls++;
        Trace.event("SYNC_CALL",
                "Pricing " + money(amountCents) + " " + base(pair) + " blocks on GET rate-service/rates/"
                        + pair + " — checkout cannot continue until that answer comes back",
                "Расчёт " + money(amountCents) + " " + base(pair) + " блокируется на GET rate-service/rates/"
                        + pair + " — оформление не продолжится, пока не придёт ответ",
                List.of("service", "decision"), state());

        if (!serviceUp) {
            Trace.event("SYNC_CALL_FAILED",
                    "The call times out: rate-service is down. The decision has no local copy to fall back "
                            + "on, so the availability of checkout is now the product of both services",
                    "Вызов отваливается по таймауту: rate-service лежит. У решения нет локальной копии, "
                            + "на которую можно опереться, поэтому доступность оформления теперь — "
                            + "произведение доступностей двух сервисов",
                    List.of("service", "decision"), state());
            return refuse(pair, "unreachable",
                    "Checkout is refused: there is no rate to price with and nothing local to use instead",
                    "Оформление отклонено: курса для расчёта нет, и локально взять его неоткуда");
        }

        Rate rate = published.get(pair);
        if (rate == null) {
            return refuse(pair, "unknown-pair",
                    "rate-service answered, but it has no rate for " + pair + " at all",
                    "rate-service ответил, но курса для " + pair + " у него вообще нет");
        }
        return settle(pair, amountCents, rate.rate, rate.version, clock, "sync-call", false);
    }

    /** Strategy 2: a local copy with a TTL, refreshed by a call when it expires. */
    private boolean priceByCache(String pair, int amountCents) {
        Local local = replica.get(pair);
        int age = local == null ? -1 : clock - local.asOf;

        if (local != null && age <= ttlSeconds) {
            cacheHits++;
            Trace.event("CACHE_HIT",
                    "The cached " + pair + " is " + age + "s old and the TTL is " + ttlSeconds
                            + "s, so it is used as is — no call, no waiting, and no way to notice that "
                            + "rate-service may have moved on",
                    "Кэшированный " + pair + " возрастом " + age + "с при TTL " + ttlSeconds
                            + "с используется как есть — без вызова, без ожидания и без возможности "
                            + "заметить, что rate-service мог уже уйти вперёд",
                    List.of("replica:" + pair, "decision"), state());
            return settle(pair, amountCents, local.rate, local.version, local.asOf, "cache", false);
        }

        if (local == null) {
            Trace.event("CACHE_MISS",
                    "Nothing cached for " + pair + ": this decision has to pay for the remote call itself",
                    "Для " + pair + " в кэше ничего нет: это решение само оплачивает удалённый вызов",
                    List.of("decision"), state());
        } else {
            Trace.event("CACHE_EXPIRED",
                    "The cached " + pair + " is " + age + "s old, past the " + ttlSeconds
                            + "s TTL — the TTL is not a freshness guarantee, it is a bound on how wrong "
                            + "the copy was allowed to be",
                    "Кэшированный " + pair + " возрастом " + age + "с вышел за TTL " + ttlSeconds
                            + "с — TTL не гарантия свежести, а ограничение на то, насколько копии было "
                            + "позволено ошибаться",
                    List.of("replica:" + pair, "decision"), state());
        }

        remoteCalls++;
        Trace.event("SYNC_CALL",
                "Refreshing " + pair + " from rate-service inside the decision",
                "Обновление " + pair + " из rate-service прямо внутри решения",
                List.of("service", "decision"), state());

        Rate fresh = serviceUp ? published.get(pair) : null;
        if (fresh != null) {
            replica.put(pair, new Local(pair, fresh.rate, fresh.version, clock));
            return settle(pair, amountCents, fresh.rate, fresh.version, clock, "sync-call", false);
        }

        if (!serviceUp) {
            Trace.event("SYNC_CALL_FAILED",
                    "The refresh fails: rate-service is down. What happens next is a policy decision, "
                            + "not a technical one — serve the expired copy, or refuse",
                    "Обновление не удалось: rate-service лежит. Что делать дальше — вопрос политики, "
                            + "а не техники: отдать просроченную копию или отказать",
                    List.of("service", "decision"), state());
        }
        if (local == null && serviceUp) {
            return refuse(pair, "unknown-pair",
                    "rate-service answered, but it has no rate for " + pair + " at all",
                    "rate-service ответил, но курса для " + pair + " у него вообще нет");
        }
        if (local == null) {
            return refuse(pair, "unreachable",
                    "There is no cached copy of " + pair + " to fall back on: a cold cache during an "
                            + "outage is exactly as fragile as no cache at all",
                    "Кэшированной копии " + pair + " для запасного варианта нет: холодный кэш во время "
                            + "аварии ровно так же хрупок, как его отсутствие");
        }
        return settle(pair, amountCents, local.rate, local.version, local.asOf, "cache", true);
    }

    /** Strategy 3: read the local replica the owner's events keep up to date. */
    private boolean priceByReplica(String pair, int amountCents) {
        Local local = replica.get(pair);
        if (local == null) {
            return refuse(pair, "cold",
                    "The local replica holds no value for " + pair + " yet. Nothing failed — the data "
                            + "has simply not arrived, and this is the cost event-carried state transfer "
                            + "asks you to plan for",
                    "Локальная реплика пока не содержит значения для " + pair + ". Ничего не сломалось — "
                            + "данные просто не пришли, и именно за это приходится платить при "
                            + "event-carried state transfer");
        }
        int age = clock - local.asOf;
        boolean pastLimit = budgetSeconds > 0 && age > budgetSeconds;
        return settle(pair, amountCents, local.rate, local.version, local.asOf, "replica", pastLimit);
    }

    /** Turns a chosen value into a quote, or refuses it when it is too old. */
    private boolean settle(String pair, int amountCents, int rate, int version, int asOf,
                           String source, boolean pastLimit) {
        int age = clock - asOf;
        boolean overBudget = budgetSeconds > 0 && age > budgetSeconds;

        if (pastLimit && policy == StalePolicy.REFUSE && overBudget) {
            return refuse(pair, "too-stale",
                    "The only value available for " + pair + " is " + age + "s old, past the declared "
                            + "limit of " + budgetSeconds + "s. The decision is refused rather than made "
                            + "on data already known to be wrong",
                    "Единственное доступное значение для " + pair + " устарело на " + age
                            + "с и вышло за объявленный предел " + budgetSeconds + "с. Решение отклоняется, "
                            + "а не принимается по заведомо неверным данным");
        }

        long total = (long) amountCents * rate / RATE_SCALE;
        boolean stale = pastLimit;
        decision.fill(source, rate, version, age, total, stale ? "stale" : "quoted");
        quotes.add(new Quote(quotes.size() + 1, pair, amountCents, rate, version, asOf, total, stale));
        if (age > oldestRateUsed) {
            oldestRateUsed = age;
        }

        String sum = money(amountCents) + " " + base(pair) + " x " + rateText(rate) + " = "
                + money(total) + " " + counter(pair);
        if (stale) {
            staleQuoted++;
            Trace.event("STALE_RATE_USED",
                    "Quote #" + quotes.size() + ": " + sum + " on a rate that is " + age
                            + "s old and flagged as degraded. Serving a wrong price is a choice — but now "
                            + "it is a visible one",
                    "Расчёт #" + quotes.size() + ": " + sum + " по курсу возрастом " + age
                            + "с с пометкой деградации. Отдать неверную цену — это выбор, но теперь "
                            + "этот выбор виден",
                    List.of("decision", "quote:" + quotes.size(), "replica:" + pair), state());
        } else {
            quoted++;
            Trace.event("PRICE_QUOTED",
                    "Quote #" + quotes.size() + ": " + sum + " — " + sourceEn(source) + " (version "
                            + version + ", " + age + "s old). The rate is pinned onto the quote, so the "
                            + "decision stays reproducible whatever happens next",
                    "Расчёт #" + quotes.size() + ": " + sum + " — " + sourceRu(source) + " (версия "
                            + version + ", возраст " + age + "с). Курс зафиксирован в расчёте, поэтому "
                            + "решение остаётся воспроизводимым, что бы ни случилось дальше",
                    List.of("decision", "quote:" + quotes.size()), state());
        }
        return true;
    }

    /** Records a refused decision and reports why. */
    private boolean refuse(String pair, String reason, String descEn, String descRu) {
        refused++;
        decision.block(reason);
        Trace.event("DECISION_BLOCKED", descEn, descRu,
                List.of("decision", "replica:" + pair), state());
        return false;
    }

    private String readyEn() {
        switch (strategy) {
            case SYNC_CALL:
                return "Pricing service ready — it keeps no rates of its own and calls rate-service at "
                        + "the moment of every decision";
            case TTL_CACHE:
                return "Pricing service ready — rates are cached locally for " + ttlSeconds
                        + "s and refetched from rate-service when the entry expires";
            default:
                return "Pricing service ready — rate-service publishes every rate change, this service "
                        + "keeps its own replica, and a decision never leaves the process";
        }
    }

    private String readyRu() {
        switch (strategy) {
            case SYNC_CALL:
                return "Сервис расчёта готов — своих курсов он не хранит и вызывает rate-service в момент "
                        + "каждого решения";
            case TTL_CACHE:
                return "Сервис расчёта готов — курсы кэшируются локально на " + ttlSeconds
                        + "с и перечитываются из rate-service, когда запись протухает";
            default:
                return "Сервис расчёта готов — rate-service публикует каждое изменение курса, этот сервис "
                        + "держит свою реплику, и решение не выходит за пределы процесса";
        }
    }

    private String strategyCode() {
        switch (strategy) {
            case SYNC_CALL:
                return "sync-call";
            case TTL_CACHE:
                return "ttl-cache";
            default:
                return "event-carried";
        }
    }

    private static String sourceEn(String source) {
        switch (source) {
            case "sync-call":
                return "the rate came straight from rate-service";
            case "cache":
                return "the rate came from the local cache";
            default:
                return "the rate came from the local replica, with no network call at all";
        }
    }

    private static String sourceRu(String source) {
        switch (source) {
            case "sync-call":
                return "курс получен прямо из rate-service";
            case "cache":
                return "курс взят из локального кэша";
            default:
                return "курс взят из локальной реплики, вообще без сетевого вызова";
        }
    }

    /** Formats integer cents as a decimal amount, e.g. 21600 -> "216.00". */
    private static String money(long cents) {
        long whole = cents / 100;
        long frac = Math.abs(cents % 100);
        return whole + "." + (frac < 10 ? "0" + frac : String.valueOf(frac));
    }

    /** Formats a scaled rate, e.g. 10800 -> "1.0800". */
    private static String rateText(int rate) {
        int whole = rate / RATE_SCALE;
        String frac = String.valueOf(Math.abs(rate % RATE_SCALE));
        while (frac.length() < 4) {
            frac = "0" + frac;
        }
        return whole + "." + frac;
    }

    /** "EUR/USD" -> "EUR"; the currency the amount is expressed in. */
    private static String base(String pair) {
        int slash = pair.indexOf('/');
        return slash < 0 ? pair : pair.substring(0, slash);
    }

    /** "EUR/USD" -> "USD"; the currency the quote is expressed in. */
    private static String counter(String pair) {
        int slash = pair.indexOf('/');
        return slash < 0 ? "" : pair.substring(slash + 1);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("strategy", strategyCode());
        s.put("clock", clock);
        s.put("ttlSeconds", ttlSeconds);
        s.put("budgetSeconds", budgetSeconds);
        s.put("policy", policy == StalePolicy.REFUSE ? "refuse" : "serve");

        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("up", serviceUp);
        owner.put("delivering", delivering);
        List<Object> rates = new ArrayList<>();
        for (Rate rate : published.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pair", rate.pair);
            m.put("rate", rate.rate);
            m.put("rateText", rateText(rate.rate));
            m.put("version", rate.version);
            m.put("publishedAt", rate.publishedAt);
            rates.add(m);
        }
        owner.put("rates", rates);
        s.put("rateService", owner);

        List<Object> events = new ArrayList<>();
        for (Delivery event : feed) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pair", event.pair);
            m.put("version", event.version);
            m.put("rate", event.rate);
            m.put("rateText", rateText(event.rate));
            m.put("publishedAt", event.publishedAt);
            m.put("status", event.status);
            events.add(m);
        }
        s.put("feed", events);

        List<Object> copies = new ArrayList<>();
        for (Local local : replica.values()) {
            int age = clock - local.asOf;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pair", local.pair);
            m.put("rate", local.rate);
            m.put("rateText", rateText(local.rate));
            m.put("version", local.version);
            m.put("asOf", local.asOf);
            m.put("ageSeconds", age);
            m.put("state", freshness(age));
            copies.add(m);
        }
        s.put("replica", copies);

        if (decision != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pair", decision.pair);
            m.put("amount", decision.amountCents);
            m.put("amountText", money(decision.amountCents));
            m.put("source", decision.source);
            m.put("rateText", decision.rate == 0 ? "" : rateText(decision.rate));
            m.put("version", decision.version);
            m.put("ageSeconds", decision.ageSeconds);
            m.put("totalText", decision.outcome.equals("blocked") ? "" : money(decision.total));
            m.put("outcome", decision.outcome);
            m.put("reason", decision.reason);
            s.put("decision", m);
        }

        List<Object> pinned = new ArrayList<>();
        for (Quote quote : quotes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", quote.id);
            m.put("pair", quote.pair);
            m.put("amountText", money(quote.amountCents));
            m.put("rateText", rateText(quote.rate));
            m.put("version", quote.version);
            m.put("asOf", quote.asOf);
            m.put("totalText", money(quote.total));
            m.put("stale", quote.stale);
            pinned.add(m);
        }
        s.put("quotes", pinned);

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("quoted", quoted);
        counters.put("staleQuoted", staleQuoted);
        counters.put("refused", refused);
        counters.put("remoteCalls", remoteCalls);
        counters.put("cacheHits", cacheHits);
        counters.put("eventsApplied", eventsApplied);
        counters.put("eventsIgnored", eventsIgnored);
        counters.put("oldestRateUsed", oldestRateUsed);
        s.put("counters", counters);
        return s;
    }

    /** How a local copy of that age looks against the declared limits. */
    private String freshness(int age) {
        if (ttlSeconds > 0 && age > ttlSeconds) {
            return "expired";
        }
        if (budgetSeconds > 0 && age > budgetSeconds) {
            return "stale";
        }
        return "fresh";
    }

    /** A value the owning service holds right now. */
    private static final class Rate {
        final String pair;
        final int rate;
        final int version;
        final int publishedAt;

        Rate(String pair, int rate, int version, int publishedAt) {
            this.pair = pair;
            this.rate = rate;
            this.version = version;
            this.publishedAt = publishedAt;
        }
    }

    /** One event on its way from the owner to this consumer. */
    private static final class Delivery {
        final int version;
        final String pair;
        final int rate;
        final int publishedAt;
        /** applied | ignored | pending */
        String status;

        Delivery(int version, String pair, int rate, int publishedAt, String status) {
            this.version = version;
            this.pair = pair;
            this.rate = rate;
            this.publishedAt = publishedAt;
            this.status = status;
        }
    }

    /** The consumer's own copy of one pair. */
    private static final class Local {
        final String pair;
        final int rate;
        final int version;
        /** When the owner produced this value — not when it was received. */
        final int asOf;

        Local(String pair, int rate, int version, int asOf) {
            this.pair = pair;
            this.rate = rate;
            this.version = version;
            this.asOf = asOf;
        }
    }

    /** The decision currently being made. */
    private static final class Decision {
        final String pair;
        final int amountCents;
        String source = "none";
        int rate;
        int version;
        int ageSeconds;
        long total;
        /** pending | quoted | stale | blocked */
        String outcome = "pending";
        String reason = "";

        Decision(String pair, int amountCents) {
            this.pair = pair;
            this.amountCents = amountCents;
        }

        void fill(String source, int rate, int version, int ageSeconds, long total, String outcome) {
            this.source = source;
            this.rate = rate;
            this.version = version;
            this.ageSeconds = ageSeconds;
            this.total = total;
            this.outcome = outcome;
        }

        void block(String reason) {
            this.outcome = "blocked";
            this.reason = reason;
        }
    }

    /** A decision already made, pinned to the exact rate that produced it. */
    private static final class Quote {
        final int id;
        final String pair;
        final int amountCents;
        final int rate;
        final int version;
        final int asOf;
        final long total;
        final boolean stale;

        Quote(int id, String pair, int amountCents, int rate, int version, int asOf,
              long total, boolean stale) {
            this.id = id;
            this.pair = pair;
            this.amountCents = amountCents;
            this.rate = rate;
            this.version = version;
            this.asOf = asOf;
            this.total = total;
            this.stale = stale;
        }
    }
}
