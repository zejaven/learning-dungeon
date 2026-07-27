package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of triaging ONE production incident — the one that
 * starts with "the endpoint worked, it passed tests, we shipped it, and the next
 * day the Product Owner says nothing works".
 *
 * <p>The model is a stopwatch and a worksheet. It walks eight phases in the
 * order that actually pays off — {@code clarify, reproduce, measure, mitigate,
 * diagnose, fix, verify, prevent} — and it charges every action minutes, so the
 * cost of doing them out of order is visible rather than argued about:
 * <ul>
 *   <li>{@link #clarify(String, String)} turns a sentence into a fact, and
 *       {@link #unanswered(String)} records the question nobody can answer yet;</li>
 *   <li>{@link #reproduce(String, boolean, String)} is the moment the incident
 *       stops being a report and becomes a failing request you own;</li>
 *   <li>{@link #readSignal(String, String, boolean)} and
 *       {@link #missingSignal(String)} read what the graphs already know — and
 *       name the blind spot when a human is the monitoring;</li>
 *   <li>{@link #scope(String, int, String)} derives the severity from a measured
 *       blast radius instead of from an adjective;</li>
 *   <li>{@link #mitigate(String, String)} restores service, which is not the
 *       same thing as fixing the bug;</li>
 *   <li>{@link #suspect(String, String)} / {@link #ruleOut(String, String)} /
 *       {@link #confirm(String, String)} are the hypothesis loop — every suspect
 *       leaves with evidence attached;</li>
 *   <li>{@link #fix(String)}, {@link #verify(String, boolean)} and
 *       {@link #guard(String)} close it: the change, the re-run of the exact
 *       request that failed, and the alert or test that would have caught it.</li>
 * </ul>
 *
 * <p>The missteps are not separate methods — they are consequences of the order
 * the methods are called in. Fixing before anything is confirmed is a
 * {@code BLIND_FIX}; hunting root cause while a critical incident is
 * un-mitigated is recorded as such; calling something verified when no request
 * ever failed in front of you is flagged. Every step emits a bilingual
 * {@link Trace} event; the class is intentionally dependency-free.
 */
public class VisualIncidentTriage {

    /** The eight phases of a triage, in the order they pay off. */
    private static final String[] PHASE_NAMES = {
            "clarify", "reproduce", "measure", "mitigate", "diagnose", "fix", "verify", "prevent"
    };

    /** One phase of the triage worksheet. */
    private static final class Phase {
        final String name;
        String state = "pending";
        String detail = "";

        Phase(String name) {
            this.name = name;
        }
    }

    /** One hypothesis about what changed between "passed tests" and "broken". */
    private static final class Suspect {
        final String name;
        final String why;
        String status = "open";
        String evidence = "";

        Suspect(String name, String why) {
            this.name = name;
            this.why = why;
        }
    }

    /** One thing a graph or a log already knew before anybody asked it. */
    private static final class Signal {
        final String name;
        final String value;
        final String verdict;

        Signal(String name, String value, String verdict) {
            this.name = name;
            this.value = value;
            this.verdict = verdict;
        }
    }

    private final String service;
    private final String endpoint;
    private final String report;
    private final List<Phase> phases = new ArrayList<>();
    /** {question, answer} pairs; a null answer is an open question. */
    private final List<String[]> facts = new ArrayList<>();
    private final List<Signal> signals = new ArrayList<>();
    private final List<Suspect> suspects = new ArrayList<>();
    private final List<String> guards = new ArrayList<>();
    private final List<String> missteps = new ArrayList<>();

    private int minutes;
    private String stage = "reported";
    private String severity = "unknown";

    private String reproRequest;
    private Boolean reproFailed;
    private String reproObserved;

    private String scopeAffected;
    private int scopeShare = -1;
    private String scopeClients;

    private String mitigationAction;
    private String mitigationEffect;

    private String rootCause;
    private String testGap;
    private String fixApplied;
    private Boolean verified;

    private VisualIncidentTriage(String service, String endpoint, String report) {
        this.service = service;
        this.endpoint = endpoint;
        this.report = report;
        for (String name : PHASE_NAMES) {
            phases.add(new Phase(name));
        }
    }

    /** A report lands. Nothing in it is a fact yet — not even that it is broken. */
    public static VisualIncidentTriage reported(String service, String endpoint, String report) {
        VisualIncidentTriage incident = new VisualIncidentTriage(service, endpoint, report);
        Trace.event("INCIDENT_REPORTED",
                service + ": the Product Owner says \"" + report + "\" about " + endpoint + ". Right now that "
                        + "sentence is the only data in the incident, and it is a claim, not a measurement. "
                        + "\"Nothing works\" is almost never literally true — it usually means one flow, tried "
                        + "once, by one person, in one browser. But \"almost never\" is not \"never\", so the "
                        + "job is not to argue with the sentence: it is to convert it into something with a "
                        + "status code in it. Two clocks start now — the one your users are living through, "
                        + "and the one your credibility is measured on — and the first thing that buys back "
                        + "both is a question, not an edit",
                service + ": владелец продукта говорит «" + report + "» про " + endpoint + ". Сейчас эта фраза "
                        + "— единственные данные в инциденте, и это утверждение, а не измерение. «Ничего не "
                        + "работает» почти никогда не бывает правдой буквально: обычно это один сценарий, "
                        + "попробованный один раз, одним человеком, в одном браузере. Но «почти никогда» — это "
                        + "не «никогда», поэтому задача не в том, чтобы спорить с фразой, а в том, чтобы "
                        + "превратить её в нечто, содержащее код ответа. С этой секунды идут двое часов — те, "
                        + "по которым живут пользователи, и те, по которым меряют доверие к вам, — и первым "
                        + "выигрывает время вопрос, а не правка",
                List.of("phase:clarify"), incident.state());
        return incident;
    }

    // -------------------------------------------------------------- clarify

    /** Answers the reflex that costs the most: telling the reporter they are wrong. */
    public void dismiss(String excuse) {
        tick(30);
        flag("dismissed-report");
        Trace.event("REPORT_DISMISSED",
                "\"" + excuse + "\" — and half an hour is gone. Everything about that answer is expensive. It "
                        + "is probably even true: the code did pass, the tests are green, and the environment "
                        + "you ran them in was not this one. But it answers a question nobody asked. The "
                        + "reporter is not claiming your tests failed, they are claiming their users are stuck, "
                        + "and those two statements can both be true at the same time — that is exactly what a "
                        + "prod-only bug IS. The practical damage is not diplomatic: the next time something "
                        + "breaks, the report comes later, or through someone else, or not at all",
                "«" + excuse + "» — и полчаса нет. В этом ответе дорого всё. Он, вероятно, даже правдив: код "
                        + "прошёл, тесты зелёные, а окружение, в котором вы их гоняли, было не этим. Но он "
                        + "отвечает на вопрос, которого никто не задавал. Сообщивший не утверждает, что ваши "
                        + "тесты упали, — он утверждает, что его пользователи застряли, и оба этих "
                        + "высказывания могут быть верны одновременно: именно это и ЕСТЬ баг, живущий только в "
                        + "проде. Ущерб тут не дипломатический: в следующий раз о поломке сообщат позже, или "
                        + "через кого-то другого, или не сообщат вовсе",
                List.of("misstep:dismissed-report"), state());
    }

    /** Turns one word of the report into something with an answer attached. */
    public void clarify(String question, String answer) {
        tick(3);
        enter("clarify", "asking");
        facts.add(new String[]{question, answer});
        Trace.event("CLAIM_CLARIFIED",
                "\"" + question + "\" -> \"" + answer + "\". One sentence became one fact, and the incident "
                        + "shrank. Five questions do most of the work here: WHICH request (method and path, not "
                        + "\"the page\"), WHO sees it (one account, one tenant, everyone), SINCE WHEN (before "
                        + "or after your release — the answer decides how much your deploy is a suspect), WHAT "
                        + "exactly comes back (a status code, an error code, a traceId, a screenshot of the "
                        + "network tab), and HOW OFTEN (every time, or one in twenty). Ask them together, "
                        + "because asking them one at a time turns a five-minute triage into an afternoon "
                        + "of ping-pong",
                "«" + question + "» -> «" + answer + "». Одна фраза превратилась в один факт, и инцидент "
                        + "сузился. Основную работу здесь делают пять вопросов: КАКОЙ запрос (метод и путь, а "
                        + "не «страница»), КТО это видит (один аккаунт, один тенант, все), С КАКОГО МОМЕНТА "
                        + "(до или после вашего релиза — от ответа зависит, насколько ваш деплой подозреваемый), "
                        + "ЧТО именно возвращается (код ответа, код ошибки, traceId, скриншот вкладки сети) и "
                        + "КАК ЧАСТО (каждый раз или один раз из двадцати). Задавайте их вместе: по одному — "
                        + "это превращает пятиминутный разбор в переписку на полдня",
                List.of("phase:clarify", "fact:" + facts.size()), state());
    }

    /** Records a question that has no answer yet — the gap is itself information. */
    public void unanswered(String question) {
        tick(1);
        enter("clarify", "asking");
        facts.add(new String[]{question, null});
        Trace.event("QUESTION_UNANSWERED",
                "\"" + question + "\" — nobody knows. Write it down anyway. An open question is not a dead end, "
                        + "it is a fork: either you can answer it yourself from a log, a metric or a database "
                        + "row, in which case do that instead of waiting, or you genuinely cannot, in which "
                        + "case the fact that this is unknowable is a finding about your observability that "
                        + "belongs in the follow-up. What you must not do is quietly assume an answer — the "
                        + "assumed answer is the one that sends the whole investigation into the wrong service",
                "«" + question + "» — никто не знает. Всё равно запишите. Открытый вопрос — это не тупик, а "
                        + "развилка: либо вы можете ответить сами по логу, метрике или строке в базе — тогда "
                        + "сделайте это, а не ждите, — либо действительно не можете, и тогда сама "
                        + "невозможность узнать это есть находка о вашей наблюдаемости, которой место в "
                        + "последующих задачах. Чего делать нельзя — это тихо додумать ответ: именно "
                        + "додуманный ответ уводит всё расследование не в тот сервис",
                List.of("phase:clarify", "fact:" + facts.size()), state());
    }

    // ------------------------------------------------------------ reproduce

    /**
     * Sends the exact request against production. This is the line the incident
     * crosses from "someone says" to "I can make it happen".
     */
    public void reproduce(String request, boolean failed, String observed) {
        tick(5);
        enter("reproduce", failed ? "reproduced" : "did not reproduce");
        reproRequest = request;
        reproFailed = failed;
        reproObserved = observed;

        if (failed) {
            stage = "confirmed";
            Trace.event("REPRODUCED",
                    request + " -> " + observed + ". The incident is now yours: a request you can send, a "
                            + "response you can read, and a thing you can re-send later to prove the fix. "
                            + "Everything after this point is cheaper because of it — you can bisect, you can "
                            + "change one variable at a time, and you will know the difference between \"fixed\" "
                            + "and \"stopped happening while I was watching\". Note what you did NOT do: you did "
                            + "not read the code first. The code passed its tests; the disagreement is between "
                            + "the code and its environment, and only the environment can settle it",
                    request + " -> " + observed + ". Теперь инцидент ваш: запрос, который вы можете отправить, "
                            + "ответ, который вы можете прочитать, и то, что можно отправить снова, чтобы "
                            + "доказать исправление. Всё дальнейшее дешевле именно из-за этого — можно делить "
                            + "пополам, менять по одной переменной за раз, и вы будете отличать «починили» от "
                            + "«перестало воспроизводиться, пока я смотрел». Обратите внимание, чего вы НЕ "
                            + "делали: вы не пошли сначала читать код. Код прошёл свои тесты; спор идёт между "
                            + "кодом и его окружением, и разрешить его может только окружение",
                    List.of("phase:reproduce", "repro"), state());
        } else {
            stage = "not-reproduced";
            Trace.event("NOT_REPRODUCED",
                    request + " -> " + observed + ". It works. That is not the end of the incident, it is a "
                            + "narrowing: whatever is broken is something your request does not have. The "
                            + "difference is in one of four places — WHO (their token, their role, their tenant, "
                            + "their row-level permissions), WHAT (their payload, their locale, their special "
                            + "characters, their id that has no data behind it), WHERE (their browser, so CORS "
                            + "or a preflight; their network, so a proxy or an old DNS record; a stale cached "
                            + "bundle calling the previous version of the path), or WHEN (only under load, only "
                            + "after the nightly job). Ask for their traceId and go and read THEIR request "
                            + "instead of yours",
                    request + " -> " + observed + ". Работает. Это не конец инцидента, а сужение: сломано "
                            + "что-то, чего в вашем запросе нет. Разница живёт в одном из четырёх мест — КТО "
                            + "(их токен, их роль, их тенант, их права на строки), ЧТО (их тело запроса, их "
                            + "локаль, их спецсимволы, их id, за которым нет данных), ГДЕ (их браузер — значит "
                            + "CORS или preflight; их сеть — значит прокси или старая DNS-запись; закэшированная "
                            + "сборка, зовущая прежнюю версию пути) или КОГДА (только под нагрузкой, только "
                            + "после ночной задачи). Попросите их traceId и идите читать ИХ запрос, а не свой",
                    List.of("phase:reproduce", "repro"), state());
        }
    }

    // -------------------------------------------------------------- measure

    /** Reads one thing the graphs already knew before anybody asked them. */
    public void readSignal(String name, String value, boolean anomalous) {
        tick(2);
        enter("measure", "reading");
        signals.add(new Signal(name, value, anomalous ? "anomalous" : "normal"));
        Trace.event("SIGNAL_READ",
                name + ": " + value + (anomalous ? " — anomalous." : " — normal.")
                        + " Read the boring signals before the clever ones, because they partition the search "
                        + "space in one glance. A 5xx spike says the failure is inside you and the stack traces "
                        + "are already written down. A 4xx spike says the failure is at the contract — someone "
                        + "is sending requests you now reject, which very often means a client shipped against "
                        + "the shape you changed. Latency up with errors flat says a dependency, a lock or a "
                        + "query plan. And a graph that did not move at all is the most useful answer of the "
                        + "four: whatever is wrong, it is not what they think it is",
                name + ": " + value + (anomalous ? " — аномалия." : " — норма.")
                        + " Читайте скучные сигналы раньше умных: они делят пространство поиска одним "
                        + "взглядом. Всплеск 5xx говорит, что сбой внутри вас и стеки уже записаны. Всплеск "
                        + "4xx говорит, что сбой на контракте: кто-то шлёт запросы, которые вы теперь "
                        + "отвергаете, — очень часто это клиент, выкатившийся под форму, которую вы поменяли. "
                        + "Выросшая задержка при ровных ошибках — это зависимость, блокировка или план "
                        + "запроса. А график, который вообще не сдвинулся, — самый полезный из четырёх "
                        + "ответов: что бы ни сломалось, это не то, о чём они думают",
                List.of("phase:measure", "signal:" + name), state());
    }

    /** Names a signal that does not exist — the reason a human is the monitoring. */
    public void missingSignal(String name) {
        tick(2);
        enter("measure", "reading");
        signals.add(new Signal(name, "-", "missing"));
        flag("flying-blind");
        Trace.event("SIGNAL_MISSING",
                "There is no " + name + ". This is why the incident arrived by human and a day late: a person "
                        + "noticed before any system did, which means the outage was as long as somebody's "
                        + "patience. Two consequences, and only one of them is about today. Today: you are "
                        + "debugging with less evidence than the failure produced, and some of it is gone for "
                        + "good. Afterwards: this is the follow-up with the highest value of anything in the "
                        + "incident, because the same blind spot will hide the next one too. An alert that "
                        + "fires at deploy + 5 minutes is worth more than the postmortem it prevents",
                "Нет " + name + ". Именно поэтому об инциденте сообщил человек и на сутки позже: раньше любой "
                        + "системы заметил человек, а значит длительность аварии равнялась чьему-то терпению. "
                        + "Отсюда два следствия, и только одно из них про сегодня. Сегодня: вы отлаживаете, "
                        + "имея меньше свидетельств, чем породил сбой, и часть из них уже не вернуть. Потом: "
                        + "это самая ценная последующая задача во всём инциденте, потому что то же слепое "
                        + "пятно спрячет и следующий. Алерт, срабатывающий на «деплой + 5 минут», стоит "
                        + "больше, чем разбор, который он предотвращает",
                List.of("phase:measure", "signal:" + name, "misstep:flying-blind"), state());
    }

    /** Measures the blast radius, and lets the severity fall out of the number. */
    public void scope(String affected, int sharePercent, String clients) {
        tick(3);
        enter("measure", "scoped");
        scopeAffected = affected;
        scopeShare = sharePercent;
        scopeClients = clients;
        severity = severityFor(sharePercent);

        Trace.event("BLAST_RADIUS_SET",
                affected + ", " + sharePercent + "% of traffic, reaching " + clients + " -> severity "
                        + severity + ". This number, not the volume of the complaint, is what decides the next "
                        + "hour. At " + sharePercent + "% you " + (sharePercent >= 50
                                ? "stop investigating and start restoring: roll back or turn the flag off now, "
                                        + "and find out why afterwards"
                                : sharePercent >= 10
                                        ? "mitigate first and diagnose second, but you are allowed to spend a "
                                                + "few minutes making sure the mitigation is the right one"
                                        : "have room to diagnose properly, and rolling everything back would "
                                                + "cost more than it saves")
                        + ". Measuring the radius is also how you answer \"nothing works\" without contradicting "
                        + "anyone: you are not saying they are wrong, you are saying how wide it is",
                affected + ", " + sharePercent + "% трафика, задето: " + clients + " -> критичность "
                        + severity + ". Именно это число, а не громкость жалобы, определяет следующий час. При "
                        + sharePercent + "% вы " + (sharePercent >= 50
                                ? "прекращаете расследовать и начинаете восстанавливать: откатывайте или "
                                        + "выключайте флаг прямо сейчас, а почему — выясните потом"
                                : sharePercent >= 10
                                        ? "сначала смягчаете, потом диагностируете, но можете потратить "
                                                + "несколько минут, чтобы убедиться, что смягчение верное"
                                        : "можете спокойно диагностировать, и откат всего обойдётся дороже, "
                                                + "чем сэкономит")
                        + ". Измерение радиуса — ещё и способ ответить на «ничего не работает», ни с кем не "
                        + "споря: вы не говорите, что человек неправ, вы говорите, насколько это широко",
                List.of("phase:measure", "scope"), state());
    }

    // ------------------------------------------------------------- mitigate

    /** Restores service. Not the same act as fixing the bug, and usually first. */
    public void mitigate(String action, String effect) {
        tick(6);
        enter("mitigate", action);
        mitigationAction = action;
        mitigationEffect = effect;
        stage = "mitigated";
        Trace.event("MITIGATED",
                action + " -> " + effect + " at T+" + minutes + "m. Users are served again and the incident is "
                        + "still open — those are two different facts and conflating them is how a five-minute "
                        + "outage becomes a two-hour one. Mitigation is cheap, reversible and does not require "
                        + "understanding: roll back the deploy, flip the feature flag off, drain the bad "
                        + "instance, raise the timeout, scale out. A fix is expensive, needs the root cause, "
                        + "and can wait until the clock is not running. The one thing to check before rolling "
                        + "back is whether the release wrote data or migrated a schema — a rollback undoes "
                        + "code, never writes, and rolling back onto data the old version cannot read turns "
                        + "one incident into two",
                action + " -> " + effect + " на T+" + minutes + "м. Пользователей снова обслуживают, и "
                        + "инцидент всё ещё открыт — это два разных факта, и их смешение превращает "
                        + "пятиминутную аварию в двухчасовую. Смягчение дёшево, обратимо и не требует "
                        + "понимания: откатить деплой, выключить фича-флаг, вывести плохой инстанс, поднять "
                        + "таймаут, добавить реплик. Исправление дорого, требует корневой причины и может "
                        + "подождать, пока часы не тикают. Единственное, что надо проверить перед откатом, — "
                        + "писал ли релиз данные и мигрировал ли схему: откат отменяет код, но никогда не "
                        + "отменяет записи, а откат на данные, которые старая версия не умеет читать, "
                        + "превращает один инцидент в два",
                List.of("phase:mitigate"), state());
    }

    // ------------------------------------------------------------- diagnose

    /** Opens a hypothesis about what changed between "passed tests" and "broken". */
    public void suspect(String name, String why) {
        tick(2);
        enter("diagnose", "hypotheses");
        suspects.add(new Suspect(name, why));
        if (mitigationAction == null && ("critical".equals(severity) || "high".equals(severity))) {
            flag("diagnosed-while-down");
        }
        Trace.event("SUSPECT_RAISED",
                "Suspect: " + name + " — " + why + ". The question the whole topic turns on is not \"what is "
                        + "wrong with the code\" but \"what is different\", because the code passed. Something "
                        + "changed between the environment the tests ran in and the one the request ran in, "
                        + "and the honest shortlist is short: your deploy, someone else's deploy, "
                        + "configuration or a secret, the data (real rows, real volume, a null nobody had), a "
                        + "dependency or its version, the platform (certificate, DNS, disk, quota), or the "
                        + "client that upgraded to match you. And when the break shows up a DAY later, that "
                        + "timing is itself evidence: it points at things with their own clock — the nightly "
                        + "batch, a cache expiring, a token or certificate ageing out, a leak reaching the "
                        + "ceiling, or a table that grew past the point where the plan flips",
                "Подозреваемый: " + name + " — " + why + ". Вопрос, вокруг которого крутится вся тема, — не "
                        + "«что не так с кодом», а «что отличается», потому что код-то прошёл. Что-то "
                        + "изменилось между окружением, где гонялись тесты, и окружением, где выполнился "
                        + "запрос, и честный список короток: ваш деплой, чужой деплой, конфигурация или "
                        + "секрет, данные (настоящие строки, настоящий объём, null, которого ни у кого не "
                        + "было), зависимость или её версия, платформа (сертификат, DNS, диск, квота) или "
                        + "клиент, обновившийся под вас. А когда поломка проявляется через СУТКИ, само это "
                        + "время — улика: оно указывает на вещи с собственными часами — ночной пакетный "
                        + "процесс, истекающий кэш, стареющий токен или сертификат, утечку, дошедшую до "
                        + "потолка, или таблицу, переросшую точку, где план запроса переключается",
                List.of("phase:diagnose", "suspect:" + name), state());
    }

    /** Closes a hypothesis with the evidence that killed it. */
    public void ruleOut(String name, String evidence) {
        tick(8);
        enter("diagnose", "hypotheses");
        Suspect entry = suspectFor(name, "raised while ruling out");
        entry.status = "ruled-out";
        entry.evidence = evidence;
        Trace.event("SUSPECT_RULED_OUT",
                name + " is out: " + evidence + ". Notice the shape of that sentence — a suspect leaves with "
                        + "evidence attached, never with \"it's probably not that\". This is the discipline "
                        + "that keeps an incident from going in circles at hour three, when everybody is tired "
                        + "and the first hypothesis quietly comes back. Write the eliminations down where the "
                        + "next person can read them: a shared channel, in real time, is both your working "
                        + "notes and the status update you would otherwise be interrupted for",
                name + " отпадает: " + evidence + ". Обратите внимание на форму этой фразы — подозреваемый "
                        + "уходит с приложенной уликой, а не со словами «наверное, не оно». Именно эта "
                        + "дисциплина не даёт инциденту пойти по кругу на третьем часу, когда все устали и "
                        + "первая гипотеза тихо возвращается. Записывайте исключения там, где их прочитает "
                        + "следующий: общий канал в реальном времени — это одновременно ваши рабочие заметки "
                        + "и тот статус, ради которого вас иначе будут дёргать",
                List.of("phase:diagnose", "suspect:" + name), state());
    }

    /** Names the root cause, with the evidence that made it more than a story. */
    public void confirm(String name, String evidence) {
        tick(8);
        enter("diagnose", "root cause");
        Suspect entry = suspectFor(name, "raised while confirming");
        entry.status = "confirmed";
        entry.evidence = evidence;
        rootCause = name;
        stage = "diagnosed";
        Trace.event("ROOT_CAUSE_CONFIRMED",
                "Root cause: " + name + ". Evidence: " + evidence + ". The bar for saying this word is that "
                        + "you can now explain the whole shape of the incident, not just its headline — why it "
                        + "started when it started, why it hits these callers and not those, and why the tests "
                        + "were green. A hypothesis that explains the error but not the timing is not the root "
                        + "cause yet; it is the second-most-likely story, and shipping a fix for it costs you "
                        + "the outage twice. The cheapest confirmation is usually the one you can run: undo "
                        + "the suspected difference in a scratch environment and watch the reproduction stop",
                "Корневая причина: " + name + ". Улика: " + evidence + ". Планка для этого слова такова: вы "
                        + "можете объяснить всю форму инцидента, а не только заголовок, — почему началось "
                        + "тогда, когда началось, почему бьёт по этим вызывающим и не бьёт по тем и почему "
                        + "тесты были зелёными. Гипотеза, объясняющая ошибку, но не время, — ещё не корневая "
                        + "причина, а вторая по правдоподобию версия, и выкатка исправления под неё стоит вам "
                        + "аварии дважды. Самое дешёвое подтверждение — обычно то, которое можно выполнить: "
                        + "отмените предполагаемое отличие в тестовом окружении и посмотрите, как "
                        + "воспроизведение прекращается",
                List.of("phase:diagnose", "suspect:" + name, "rootcause"), state());
    }

    /** Answers the second half of the question: why the tests were green. */
    public void testGap(String gap) {
        tick(2);
        enter("diagnose", "test gap");
        testGap = gap;
        Trace.event("TEST_GAP_NAMED",
                "The tests passed because " + gap + ". This is the part of the incident that is actually about "
                        + "engineering rather than about firefighting, and skipping it is how the same class of "
                        + "bug comes back next quarter with a different name. A green suite never claimed the "
                        + "endpoint works — it claimed the cases somebody thought of behave as somebody "
                        + "expected, on data somebody invented, in a process with one thread and no neighbours. "
                        + "Production differs on every one of those axes: real rows with real nulls and real "
                        + "volume, concurrent callers, its own configuration and secrets, real authentication, "
                        + "real network, and clients you did not write",
                "Тесты прошли, потому что " + gap + ". Это та часть инцидента, которая на самом деле про "
                        + "инженерию, а не про тушение пожара, и её пропуск — причина, по которой тот же класс "
                        + "багов вернётся через квартал под другим именем. Зелёный прогон никогда не утверждал, "
                        + "что эндпоинт работает: он утверждал, что случаи, которые кто-то придумал, ведут "
                        + "себя так, как кто-то ожидал, на данных, которые кто-то выдумал, в процессе с одним "
                        + "потоком и без соседей. Прод отличается по каждой из этих осей: настоящие строки с "
                        + "настоящими null и настоящим объёмом, конкурентные вызывающие, собственная "
                        + "конфигурация и секреты, настоящая аутентификация, настоящая сеть и клиенты, "
                        + "которых писали не вы",
                List.of("phase:diagnose", "testgap"), state());
    }

    // ------------------------------------------------------------------ fix

    /** Changes production. Whether that is engineering or gambling depends on the order. */
    public void fix(String change) {
        tick(20);
        enter("fix", change);
        fixApplied = change;

        if (rootCause == null) {
            flag("blind-fix");
            Trace.event("BLIND_FIX",
                    "Shipping \"" + change + "\" with no confirmed cause. This is the most common way an "
                            + "incident gets longer instead of shorter, and it feels like progress the whole "
                            + "time. Three costs, all of them real. You have added a second untested change to "
                            + "a system that is already misbehaving, so if the symptoms move you no longer know "
                            + "which change moved them. You have destroyed the evidence — the state that "
                            + "produced the failure is gone, and if the guess was wrong you cannot go back and "
                            + "look. And if the symptom does disappear, you will never know whether you fixed "
                            + "it or whether the nightly job simply finished. Guessing is allowed; guessing "
                            + "against production is not. A guess belongs in a scratch environment where being "
                            + "wrong is free",
                    "Выкатка «" + change + "» без подтверждённой причины. Это самый частый способ сделать "
                            + "инцидент длиннее, а не короче, и всё это время он ощущается как прогресс. Три "
                            + "цены, и все настоящие. Вы добавили второе непроверенное изменение в систему, "
                            + "которая и так ведёт себя плохо: если симптомы сдвинутся, вы больше не знаете, "
                            + "какое изменение их сдвинуло. Вы уничтожили улики — состояния, породившего сбой, "
                            + "больше нет, и если догадка была неверна, вернуться и посмотреть уже не на что. "
                            + "А если симптом всё же исчезнет, вы никогда не узнаете, вы это починили или "
                            + "просто доработала ночная задача. Догадываться можно; догадываться на проде — "
                            + "нельзя. Место догадки — в тестовом окружении, где ошибиться бесплатно",
                    List.of("phase:fix", "misstep:blind-fix"), state());
            return;
        }

        stage = "fixed";
        Trace.event("FIX_APPLIED",
                "\"" + change + "\" ships against a confirmed cause. Keep it boring and keep it alone: the "
                        + "smallest change that addresses " + rootCause + " and nothing else, so that if it "
                        + "does not work you have learned something instead of adding a variable. The "
                        + "refactoring you noticed on the way, the two other bugs you found in the log, the "
                        + "logging you wish were there — all of them are tickets, not part of this deploy. And "
                        + "the fix goes out through the same pipeline as anything else: an incident is a "
                        + "reason to be careful, not a licence to push straight to production",
                "«" + change + "» выкатывается под подтверждённую причину. Пусть оно будет скучным и "
                        + "одиноким: минимальное изменение, закрывающее «" + rootCause + "» и больше ничего, "
                        + "чтобы в случае неудачи вы что-то узнали, а не добавили переменную. Рефакторинг, "
                        + "замеченный по дороге, два других бага, найденных в логе, логирование, которого не "
                        + "хватает, — всё это задачи, а не часть этой выкатки. И исправление едет тем же "
                        + "конвейером, что и всё остальное: инцидент — повод быть аккуратнее, а не право "
                        + "пушить прямо в прод",
                List.of("phase:fix"), state());
    }

    /** Re-runs the request that failed. Anything else is a feeling, not a check. */
    public void verify(String check, boolean passed) {
        tick(5);
        enter("verify", check);
        verified = passed;

        if (!Boolean.TRUE.equals(reproFailed)) {
            flag("nothing-to-verify-against");
        }
        if (!passed) {
            flag("fix-did-not-fix-it");
            stage = "reopened";
            Trace.event("FIX_UNVERIFIED",
                    check + " still fails. Good — you found out, and you found out in seconds because there "
                            + "was a reproduction to re-run. Now do the thing that is hard when you are tired: "
                            + "revert your change before trying the next hypothesis. Leaving a failed fix in "
                            + "place is how a system ends up carrying three speculative edits nobody can "
                            + "justify, and how the next debugging session starts from a state that matches "
                            + "neither main nor the last release. Reverting also puts the cause back on the "
                            + "table honestly: it was never confirmed, or it was only part of it",
                    check + " всё ещё падает. И хорошо — вы это узнали, и узнали за секунды, потому что было "
                            + "что перезапустить. Теперь сделайте то, что трудно, когда вы устали: откатите "
                            + "своё изменение, прежде чем проверять следующую гипотезу. Оставленное неудачное "
                            + "исправление — это путь к системе, которая тащит три спекулятивные правки, "
                            + "которые никто не может обосновать, и к тому, что следующая отладка стартует из "
                            + "состояния, не совпадающего ни с main, ни с последним релизом. Откат ещё и "
                            + "честно возвращает причину на стол: она не была подтверждена — или была "
                            + "подтверждена лишь частично",
                    List.of("phase:verify", "misstep:fix-did-not-fix-it"), state());
            return;
        }

        stage = "closed";
        Trace.event("FIX_VERIFIED",
                check + " passes. Verification means the exact request that failed now succeeds, run against "
                        + "the environment it failed in — not a unit test, not a local run, not \"the error "
                        + "stopped appearing in the log\". Then close the loop with the two audiences you have "
                        + "left: the graphs, which have to come back to where they were before you call it "
                        + "over, and the person who reported it, who should hear it from you rather than "
                        + "discover it. Telling the reporter what was wrong, in one sentence and without "
                        + "jargon, is what converts this incident from a complaint into trust",
                check + " проходит. Проверка означает, что тот самый запрос, который падал, теперь проходит, "
                        + "и выполнен он в том окружении, где падал, — не юнит-тест, не локальный запуск и не "
                        + "«ошибка перестала появляться в логе». Затем замкните круг с двумя оставшимися "
                        + "аудиториями: графики, которые обязаны вернуться туда, где были, прежде чем вы "
                        + "объявите всё законченным, и человек, который сообщил, — он должен услышать это от "
                        + "вас, а не обнаружить сам. Рассказать сообщившему, что было не так, одной фразой и "
                        + "без жаргона, — это и есть то, что превращает инцидент из жалобы в доверие",
                List.of("phase:verify"), state());
    }

    /** Adds the thing that would have caught it — the only permanent output. */
    public void guard(String guard) {
        tick(10);
        enter("prevent", "guards");
        guards.add(guard);
        Trace.event("GUARD_ADDED",
                "Follow-up: " + guard + ". Ask one question of every incident — what would have made this "
                        + "cheaper — and there are only three useful answers. A test that reproduces it, so "
                        + "the bug cannot come back silently; a signal that would have paged you before a "
                        + "human did, which is what turns \"the next day\" into \"five minutes\"; or a "
                        + "guardrail that makes the whole class of failure impossible or reversible — a "
                        + "feature flag, a canary, a contract test against the real client, a staging "
                        + "environment with production-shaped data. Everything else is a note nobody reads. "
                        + "Assign it, size it, and put it in the same backlog as features, because that is "
                        + "the only place work actually happens",
                "Последующая задача: " + guard + ". Задайте каждому инциденту один вопрос — что сделало бы "
                        + "его дешевле — и полезных ответов будет всего три. Тест, который его "
                        + "воспроизводит, чтобы баг не вернулся молча; сигнал, который поднял бы вас раньше "
                        + "человека, — именно он превращает «на следующий день» в «через пять минут»; или "
                        + "ограничитель, делающий целый класс сбоев невозможным или обратимым: фича-флаг, "
                        + "канареечная выкатка, контрактный тест против настоящего клиента, стенд с данными "
                        + "формы прода. Всё остальное — заметка, которую никто не читает. Назначьте "
                        + "ответственного, оцените и положите в тот же бэклог, что и фичи: только там "
                        + "работа действительно делается",
                List.of("phase:prevent", "guard:" + guards.size()), state());
    }

    // --------------------------------------------------------------- review

    /** Reads the worksheet back: what is known, what was skipped, what it cost. */
    public void review() {
        int open = openQuestions();
        Trace.event("TRIAGE_REVIEW",
                service + " " + endpoint + " at T+" + minutes + "m: state " + stage + ", severity " + severity
                        + ", " + facts.size() + " question(s) asked (" + open + " still open), "
                        + suspects.size() + " suspect(s), root cause "
                        + (rootCause == null ? "not found" : rootCause)
                        + (skipped().isEmpty() ? ", no phase skipped" : ", skipped: " + String.join(", ", skipped()))
                        + (missteps.isEmpty() ? ", no missteps" : ", missteps: " + String.join(", ", missteps))
                        + ". Read the row of phases as the answer to the interview question, because that is "
                        + "what is being asked: not which bug it was, but whether you have a procedure. "
                        + "Clarify the claim, reproduce it, measure how wide it is, restore service, then find "
                        + "what changed, fix it, prove it with the request that failed, and leave behind the "
                        + "alert or test that makes the next one shorter — while saying out loud, at every "
                        + "step, what you know and what you do not",
                service + " " + endpoint + " на T+" + minutes + "м: состояние " + stage + ", критичность "
                        + severity + ", задано вопросов: " + facts.size() + " (открытых: " + open
                        + "), подозреваемых: " + suspects.size() + ", корневая причина: "
                        + (rootCause == null ? "не найдена" : rootCause)
                        + (skipped().isEmpty() ? ", ни одна фаза не пропущена"
                                : ", пропущено: " + String.join(", ", skipped()))
                        + (missteps.isEmpty() ? ", промахов нет" : ", промахи: " + String.join(", ", missteps))
                        + ". Читайте ряд фаз как ответ на вопрос собеседования, потому что спрашивают именно "
                        + "это: не какой это был баг, а есть ли у вас процедура. Уточнить утверждение, "
                        + "воспроизвести, измерить ширину, восстановить работу, затем найти, что изменилось, "
                        + "исправить, доказать тем самым запросом, который падал, и оставить после себя алерт "
                        + "или тест, который сделает следующий раз короче, — вслух проговаривая на каждом "
                        + "шаге, что вы знаете, а чего не знаете",
                List.of(), state());
    }

    // -------------------------------------------------------------- helpers

    /** Marks a phase reached; any earlier phase still untouched is recorded as skipped. */
    private void enter(String name, String detail) {
        int index = indexOf(name);
        for (int i = 0; i < index; i++) {
            Phase earlier = phases.get(i);
            if ("pending".equals(earlier.state)) {
                earlier.state = "skipped";
            }
        }
        Phase phase = phases.get(index);
        phase.state = "done";
        phase.detail = detail;
    }

    private static int indexOf(String name) {
        for (int i = 0; i < PHASE_NAMES.length; i++) {
            if (PHASE_NAMES[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("unknown phase: " + name);
    }

    private Suspect suspectFor(String name, String why) {
        for (Suspect entry : suspects) {
            if (entry.name.equals(name)) {
                return entry;
            }
        }
        Suspect created = new Suspect(name, why);
        suspects.add(created);
        return created;
    }

    private void flag(String misstep) {
        if (!missteps.contains(misstep)) {
            missteps.add(misstep);
        }
    }

    private void tick(int cost) {
        minutes += cost;
    }

    private static String severityFor(int sharePercent) {
        if (sharePercent >= 50) {
            return "critical";
        }
        if (sharePercent >= 10) {
            return "high";
        }
        if (sharePercent >= 1) {
            return "low";
        }
        return "none";
    }

    private int openQuestions() {
        int open = 0;
        for (String[] fact : facts) {
            if (fact[1] == null) {
                open++;
            }
        }
        return open;
    }

    private List<String> skipped() {
        List<String> names = new ArrayList<>();
        for (Phase phase : phases) {
            if ("skipped".equals(phase.state)) {
                names.add(phase.name);
            }
        }
        return names;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("service", service);
        s.put("endpoint", endpoint);
        s.put("report", report);
        s.put("stage", stage);
        s.put("severity", severity);
        s.put("minutes", minutes);

        List<Object> track = new ArrayList<>();
        for (Phase phase : phases) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", phase.name);
            item.put("state", phase.state);
            item.put("detail", phase.detail);
            track.add(item);
        }
        s.put("phases", track);

        List<Object> questions = new ArrayList<>();
        for (String[] fact : facts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", fact[0]);
            item.put("answer", fact[1]);
            item.put("known", fact[1] != null);
            questions.add(item);
        }
        s.put("facts", questions);

        if (reproRequest != null) {
            Map<String, Object> repro = new LinkedHashMap<>();
            repro.put("request", reproRequest);
            repro.put("failed", reproFailed);
            repro.put("observed", reproObserved);
            s.put("reproduction", repro);
        }

        List<Object> readings = new ArrayList<>();
        for (Signal signal : signals) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", signal.name);
            item.put("value", signal.value);
            item.put("verdict", signal.verdict);
            readings.add(item);
        }
        s.put("signals", readings);

        if (scopeShare >= 0) {
            Map<String, Object> radius = new LinkedHashMap<>();
            radius.put("affected", scopeAffected);
            radius.put("share", scopeShare);
            radius.put("clients", scopeClients);
            s.put("blastRadius", radius);
        }

        if (mitigationAction != null) {
            Map<String, Object> mitigation = new LinkedHashMap<>();
            mitigation.put("action", mitigationAction);
            mitigation.put("effect", mitigationEffect);
            s.put("mitigation", mitigation);
        }

        List<Object> hypotheses = new ArrayList<>();
        for (Suspect entry : suspects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.name);
            item.put("why", entry.why);
            item.put("status", entry.status);
            item.put("evidence", entry.evidence);
            hypotheses.add(item);
        }
        s.put("suspects", hypotheses);

        s.put("rootCause", rootCause);
        s.put("testGap", testGap);
        s.put("fixApplied", fixApplied);
        s.put("verified", verified);
        s.put("guards", new ArrayList<>(guards));
        s.put("missteps", new ArrayList<>(missteps));
        s.put("openQuestions", openQuestions());
        return s;
    }
}
