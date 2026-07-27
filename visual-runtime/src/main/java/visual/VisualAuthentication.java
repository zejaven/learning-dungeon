package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of how authentication works in a system: the whole
 * lifecycle of "who are you", from the moment a password is stored to the moment
 * a credential stops being accepted.
 *
 * <p>Every authentication scheme, however it is built, walks the same five phases:
 * <ul>
 *   <li><b>claim</b> — someone says who they are (a username, or a credential
 *       presented on a later request);</li>
 *   <li><b>prove</b> — they show something only that user could have: a password
 *       checked against a stored hash, or a credential the server can validate;</li>
 *   <li><b>mfa</b> — optionally a second, independent factor, so a stolen password
 *       alone is not enough;</li>
 *   <li><b>issue</b> — the server hands back a credential, because nobody wants to
 *       re-check a password on every single request;</li>
 *   <li><b>identity</b> — from here on the server knows who the caller is, and can
 *       start asking whether they are <em>allowed</em> to do things.</li>
 * </ul>
 *
 * <p>The model deliberately supports the two credential styles an interview always
 * comes down to — a <b>server-side session</b> (the server remembers, the client
 * holds only an id) and a <b>stateless token</b> (the client holds the facts, the
 * server remembers nothing) — because almost every trade-off, and every awkward
 * question about logging out, follows from that one choice.
 *
 * <p>It also makes the classic failures visible instead of merely describing them:
 * passwords kept in plaintext, a store that leaks, brute force against a login
 * endpoint, a credential used by somebody who simply holds it, and a token that
 * keeps working after the user pressed "log out". Those show up as
 * {@code decision: "breach"} — the request was served, and it should not have been.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free.
 */
public class VisualAuthentication {

    /** How long an issued access credential stays valid, in model minutes. */
    public static final int ACCESS_LIFETIME_MINUTES = 15;
    /** How long the login may be renewed without typing the password again, in model minutes. */
    public static final int REFRESH_LIFETIME_MINUTES = 480;

    /** The phases of the flow, in the order they run. */
    private static final List<String> PHASES = List.of("claim", "prove", "mfa", "issue", "identity");

    // ----------------------------------------------------------------- records

    /** One user as the server stores them — the password itself is never among the fields. */
    private static final class Account {

        private final String name;
        private final String salt;
        private String stored;
        private String secondFactor;
        private int failedAttempts;

        private Account(String name, String salt) {
            this.name = name;
            this.salt = salt;
        }
    }

    /** One completed login; a credential can be renewed only while its login lives. */
    private static final class Login {

        private final String id;
        private final String user;
        private final int refreshExpiresAt;
        private boolean loggedOut;

        private Login(String id, String user, int refreshExpiresAt) {
            this.id = id;
            this.user = user;
            this.refreshExpiresAt = refreshExpiresAt;
        }
    }

    /**
     * What the client walks away with after logging in: a session id or a signed
     * token. The learner passes it back into {@link #request(String, Credential)},
     * exactly as a browser would put it in a cookie or an Authorization header.
     */
    public static final class Credential {

        private final String id;
        private final String user;
        private final String kind;
        private final String loginId;
        private int issuedAt;
        private int expiresAt;
        private boolean pendingSecondFactor;
        private boolean heldByClient = true;

        private Credential(String id, String user, String kind, String loginId) {
            this.id = id;
            this.user = user;
            this.kind = kind;
            this.loginId = loginId;
        }

        /** The value a client would send back; a session id says nothing, a token carries claims. */
        public String value() {
            return id;
        }

        /** Which user this credential speaks for. */
        public String user() {
            return user;
        }
    }

    /** Who the server decided the caller is, once a credential validated. */
    private static final class Identity {

        private final String user;
        private final String source;
        private final String credentialId;

        private Identity(String user, String source, String credentialId) {
            this.user = user;
            this.source = source;
            this.credentialId = credentialId;
        }
    }

    // ------------------------------------------------------------------- state

    private final String credentialStyle;
    private boolean plaintextPasswords;
    private int loginRateLimit;

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    /** What the server remembers between requests. Stays empty in token mode — that is the point. */
    private final Map<String, Credential> serverSessions = new LinkedHashMap<>();
    private final List<Credential> clientCredentials = new ArrayList<>();
    private final Map<String, Login> loginRecords = new LinkedHashMap<>();
    private final Map<String, String[]> phases = new LinkedHashMap<>();

    private int clock;
    private int credentialCounter;
    private int loginCounter;

    private String actionKind = "idle";
    private String actionActor = "";
    private String actionDetail = "";
    private Identity identity;
    private Integer status;
    private String decision = "idle";
    private String reason;
    private String detail = "";

    private int loginsGranted;
    private int loginsRefused;
    private int requests;
    private int served;
    private int refused;
    private int breaches;

    private VisualAuthentication(String credentialStyle) {
        this.credentialStyle = credentialStyle;
        resetPhases();
    }

    /**
     * A system that keeps a server-side session: the client gets an opaque id and
     * the server remembers everything behind it. Logging out is a delete.
     */
    public static VisualAuthentication withSessions() {
        VisualAuthentication auth = new VisualAuthentication("session");
        Trace.event("AUTH_SETUP",
                "A system that authenticates with SERVER-SIDE SESSIONS. After the password is "
                        + "checked once, the server stores a session record and hands the client a "
                        + "meaningless id to send back on every later request. The id proves nothing by "
                        + "itself — it is a lookup key into the server's memory, which is why the server "
                        + "can change its mind about it at any moment",
                "Система, которая аутентифицирует через СЕРВЕРНЫЕ СЕССИИ. После однократной проверки "
                        + "пароля сервер сохраняет запись о сессии и отдаёт клиенту ничего не значащий "
                        + "идентификатор, который тот присылает с каждым следующим запросом. Сам по себе "
                        + "идентификатор ничего не доказывает — это ключ к памяти сервера, и именно "
                        + "поэтому сервер в любой момент может передумать",
                List.of("policy", "server"), auth.state());
        return auth;
    }

    /**
     * A system that authenticates with a stateless signed token: the claims travel
     * with the client and the server keeps nothing. Cheap to scale, awkward to revoke.
     */
    public static VisualAuthentication withTokens() {
        VisualAuthentication auth = new VisualAuthentication("token");
        Trace.event("AUTH_SETUP",
                "A system that authenticates with STATELESS SIGNED TOKENS. After the password is "
                        + "checked once, the server signs a small document saying who this is and until "
                        + "when, and forgets about it. Any instance can verify the signature without a "
                        + "lookup, which is what makes this scale — and it is also why nothing on the "
                        + "server knows the token exists",
                "Система, которая аутентифицирует через ПОДПИСАННЫЕ ТОКЕНЫ БЕЗ СОСТОЯНИЯ. После "
                        + "однократной проверки пароля сервер подписывает маленький документ о том, кто "
                        + "это и до какого момента, — и забывает о нём. Любой инстанс проверит подпись "
                        + "без обращения к хранилищу, и именно это даёт масштабируемость; по той же "
                        + "причине сервер вообще не знает о существовании этого токена",
                List.of("policy", "client"), auth.state());
        return auth;
    }

    /**
     * Makes the system keep passwords as the user typed them. Every real breach story
     * you have read starts here, which is exactly why it is worth watching once.
     */
    public VisualAuthentication storePasswordsInPlaintext() {
        this.plaintextPasswords = true;
        Trace.event("AUTH_SETUP",
                "This system is configured to store passwords AS TYPED. Nothing about logging in "
                        + "changes — that is what makes it survive code review. The difference only "
                        + "appears on the day somebody else can read the table, and by then the damage "
                        + "is not one system but every other site where those users typed the same "
                        + "password",
                "Эта система настроена хранить пароли В ТОМ ВИДЕ, В КАКОМ ИХ ВВЕЛИ. Во входе в "
                        + "систему при этом не меняется ничего — поэтому такое и проходит ревью. Разница "
                        + "проявляется только в тот день, когда таблицу сможет прочитать кто-то ещё, и "
                        + "тогда пострадает не одна система, а все остальные сайты, где эти "
                        + "пользователи вводили тот же пароль",
                List.of("policy", "server"), state());
        return this;
    }

    /**
     * Caps how many wrong passwords one account tolerates. Without this, a login
     * endpoint is a free oracle: every attempt is a perfectly valid request.
     */
    public VisualAuthentication rateLimitLogins(int maxFailedAttempts) {
        this.loginRateLimit = maxFailedAttempts;
        Trace.event("AUTH_SETUP",
                "Login attempts are now throttled after " + maxFailedAttempts
                        + " wrong password(s) for the same account. Nothing else in the flow can see "
                        + "this attack: every single attempt is a well-formed request with a real "
                        + "username, and only the RATE gives it away",
                "Попытки входа теперь ограничиваются после " + maxFailedAttempts
                        + " неверных паролей для одного аккаунта. Никакая другая часть потока эту атаку "
                        + "не видит: каждая отдельная попытка — корректный запрос с настоящим именем "
                        + "пользователя, и выдаёт её только ЧАСТОТА",
                List.of("policy"), state());
        return this;
    }

    // -------------------------------------------------------------- enrolment

    /** Creates an account. The password is turned into something the server can compare against. */
    public void register(String user, String password) {
        beginAction("register", user, user);
        Account account = new Account(user, saltFor(user));
        accounts.put(user, account);
        if (plaintextPasswords) {
            account.stored = password;
            phase("claim", "passed", user);
            phase("prove", "skipped", "");
            Trace.event("PLAINTEXT_PASSWORD_STORED",
                    "'" + user + "' is registered and the row now literally contains \"" + password
                            + "\". The server never needs the password itself — it only needs to answer "
                            + "'could this person produce it', and a one-way hash answers that without "
                            + "keeping the answer around. Storing the password means anyone who reads "
                            + "the table can log in as anyone, everywhere",
                    "«" + user + "» зарегистрирован, и в строке таблицы теперь буквально лежит «"
                            + password + "». Сам пароль серверу не нужен: ему нужно отвечать на вопрос "
                            + "«мог ли этот человек его назвать», а односторонний хеш отвечает на него, "
                            + "не сохраняя ответа. Хранить пароль — значит позволить любому, кто "
                            + "прочитает таблицу, входить под кем угодно и куда угодно",
                    List.of("user:" + user, "server"), state());
            return;
        }
        account.stored = hash(account.salt, password);
        phase("claim", "passed", user);
        phase("prove", "skipped", "");
        Trace.event("USER_REGISTERED",
                "'" + user + "' is registered. What is stored is hash(salt + password) = "
                        + account.stored + ", with the per-user salt " + account.salt
                        + ". The hash cannot be turned back into the password; the salt means two users "
                        + "with the same password get different rows, so cracking one tells an attacker "
                        + "nothing about the other. Use a slow algorithm on purpose — bcrypt, scrypt or "
                        + "Argon2, not SHA-256",
                "«" + user + "» зарегистрирован. Хранится hash(salt + password) = " + account.stored
                        + " и персональная соль " + account.salt
                        + ". Хеш нельзя обратить в пароль, а соль приводит к тому, что у двух "
                        + "пользователей с одинаковым паролем будут разные строки: взломав одну, "
                        + "злоумышленник ничего не узнает о другой. Алгоритм намеренно берут медленный — "
                        + "bcrypt, scrypt или Argon2, а не SHA-256",
                List.of("user:" + user, "server"), state());
    }

    /** Turns on a second factor for this account: a code the password thief does not have. */
    public void requireSecondFactor(String user, String code) {
        Account account = accounts.get(user);
        if (account == null) {
            return;
        }
        account.secondFactor = code;
        beginAction("mfa-setup", user, user);
        Trace.event("SECOND_FACTOR_ENABLED",
                "'" + user + "' now needs a second factor as well as the password. The two are "
                        + "deliberately different KINDS of proof — something you know plus something you "
                        + "have — so that a password leaked from another site, guessed, or typed into a "
                        + "convincing fake page is no longer enough on its own",
                "«" + user + "» теперь нужен второй фактор в дополнение к паролю. Эти два фактора "
                        + "намеренно разного ТИПА — то, что ты знаешь, плюс то, чем ты владеешь, — чтобы "
                        + "пароль, утёкший с другого сайта, подобранный или введённый на убедительной "
                        + "поддельной странице, сам по себе больше ничего не давал",
                List.of("user:" + user), state());
    }

    /** Shows what an attacker walks away with when the user table leaks. */
    public void leakTheUserStore() {
        beginAction("leak", "attacker", "");
        List<String> rows = new ArrayList<>();
        for (Account account : accounts.values()) {
            rows.add(account.name + " -> " + account.stored);
        }
        Trace.event("USER_STORE_LEAKED",
                "The user table is now in somebody else's hands: " + String.join(", ", rows) + ". "
                        + (plaintextPasswords
                            ? "Those are the passwords. Every account here is gone, and so is every "
                              + "account those people have elsewhere with the same password — which is "
                              + "most of them. There is no fix afterwards beyond telling everyone"
                            : "Those are salted hashes, so there is nothing to log in with. An attacker "
                              + "can still guess offline, which is why the algorithm is chosen to be "
                              + "SLOW: it turns billions of guesses per second into thousands, and buys "
                              + "the time to rotate everything")
                        + ". Note that the credential style makes no difference here at all — this is "
                        + "about what sits at rest",
                "Таблица пользователей теперь в чужих руках: " + String.join(", ", rows) + ". "
                        + (plaintextPasswords
                            ? "Это пароли. Все эти аккаунты потеряны, а вместе с ними и все аккаунты "
                              + "этих людей на других сайтах с тем же паролем — то есть большинство. "
                              + "Ничего, кроме оповещения всех, потом уже не сделать"
                            : "Это солёные хеши, входить по ним не с чем. Подбирать офлайн "
                              + "злоумышленник всё же может — и именно поэтому алгоритм выбирают "
                              + "МЕДЛЕННЫЙ: он превращает миллиарды попыток в секунду в тысячи и даёт "
                              + "время всё поменять")
                        + ". Обратите внимание: стиль учётных данных здесь не играет никакой роли — речь "
                        + "о том, что лежит в хранилище",
                List.of("server"), state());
    }

    // ------------------------------------------------------------------ login

    /**
     * The one moment the password is used. On success the caller receives a
     * credential to send with every later request; on failure, nothing.
     */
    public Credential login(String user, String password) {
        beginAction("login", user, user);
        Account account = accounts.get(user);
        phase("claim", "passed", user);
        Trace.event("LOGIN_ATTEMPT",
                "'" + user + "' claims an identity and offers a password as proof. This is the only "
                        + "point in the whole system where the password is used at all — everything "
                        + "after it works off the credential this step hands out",
                "«" + user + "» заявляет о своей личности и предъявляет пароль как доказательство. "
                        + "Это единственное место во всей системе, где пароль вообще используется: всё "
                        + "остальное работает с учётными данными, которые выдаст этот шаг",
                List.of("user:" + user, "phase:claim"), state());

        if (loginRateLimit > 0 && account != null && account.failedAttempts >= loginRateLimit) {
            phase("prove", "denied", "throttled");
            refuse(429, "throttled", user);
            loginsRefused++;
            Trace.event("LOGIN_RATE_LIMITED",
                    "Refused before the password is even looked at: '" + user + "' has already got it "
                            + "wrong " + account.failedAttempts + " time(s). Unlimited attempts turn a "
                            + "login endpoint into a free oracle — an attacker with a list of leaked "
                            + "passwords does not need to be clever, only patient. Throttle by account "
                            + "AND by source, and make the delay grow",
                    "Отказ ещё до того, как посмотрели на пароль: «" + user + "» уже ошибся "
                            + account.failedAttempts + " раз(а). Неограниченные попытки превращают вход "
                            + "в бесплатный оракул: злоумышленнику со списком утёкших паролей не нужно "
                            + "быть изобретательным — достаточно быть терпеливым. Ограничивайте и по "
                            + "аккаунту, И по источнику, и увеличивайте задержку",
                    List.of("user:" + user, "phase:prove"), state());
            return null;
        }

        boolean ok = account != null && matches(account, password);
        if (!ok) {
            if (account != null) {
                account.failedAttempts++;
            }
            phase("prove", "denied", "wrong password");
            refuse(401, account == null ? "unknown-user" : "wrong-password", user);
            loginsRefused++;
            Trace.event("LOGIN_FAILED",
                    "The proof does not check out, so no credential is issued and the caller stays "
                            + "anonymous. Note the wording of a good error: 'wrong username or "
                            + "password'. Saying which one was wrong tells an attacker which accounts "
                            + "exist, and that list is worth having",
                    "Доказательство не сходится, поэтому учётные данные не выдаются и вызывающий "
                            + "остаётся анонимным. Обратите внимание на формулировку хорошей ошибки: "
                            + "«неверное имя пользователя или пароль». Уточнение, что именно неверно, "
                            + "сообщает злоумышленнику, какие аккаунты существуют, а такой список "
                            + "дорогого стоит",
                    List.of("user:" + user, "phase:prove"), state());
            return null;
        }

        account.failedAttempts = 0;
        phase("prove", "passed", plaintextPasswords ? "compared" : "hash matched");
        Trace.event("PASSWORD_VERIFIED",
                plaintextPasswords
                        ? "The password matches the stored one. The server compared the secret itself, "
                          + "which is exactly what a correct system never has to do: hashing the "
                          + "candidate and comparing hashes answers the same question without the "
                          + "server ever holding the answer"
                        : "The candidate password is hashed with " + account.salt + ", the same salt "
                          + "used at registration, and the result equals the stored " + account.stored
                          + ". The server has verified the password without ever having stored it — and "
                          + "it still does not know what it is",
                plaintextPasswords
                        ? "Пароль совпал с сохранённым. Сервер сравнил сам секрет — то, чего "
                          + "правильной системе делать не приходится: хеширование введённого значения и "
                          + "сравнение хешей отвечают на тот же вопрос, а ответа сервер при этом не "
                          + "хранит"
                        : "Введённый пароль хешируется с солью " + account.salt
                          + " — той же, что и при регистрации, — и результат совпадает с сохранённым "
                          + account.stored + ". Сервер проверил пароль, никогда его не сохраняя, и "
                          + "по-прежнему не знает, какой он",
                List.of("user:" + user, "phase:prove"), state());

        if (account.secondFactor != null) {
            Credential pending = newCredential(user, newLogin(user));
            pending.pendingSecondFactor = true;
            clientCredentials.add(pending);
            phase("mfa", "pending", "code required");
            this.decision = "pending";
            this.status = null;
            Trace.event("MFA_REQUIRED",
                    "The password was right and the caller is still not logged in. A second factor is "
                            + "demanded, so a password on its own — stolen, guessed or phished — buys "
                            + "an attacker nothing. Half a login is not a login: this credential "
                            + "identifies nobody until the code arrives",
                    "Пароль верный, а вызывающий всё ещё не вошёл. Требуется второй фактор, поэтому "
                            + "один только пароль — украденный, подобранный или выуженный — "
                            + "злоумышленнику ничего не даёт. Половина входа — это не вход: пока не "
                            + "придёт код, эти учётные данные никого не идентифицируют",
                    List.of("user:" + user, "phase:mfa", "credential:" + pending.id), state());
            return pending;
        }

        phase("mfa", "skipped", "not enabled");
        return issue(user, newLogin(user));
    }

    /** Completes a login that stopped for a second factor. */
    public Credential submitSecondFactor(Credential pending, String code) {
        if (pending == null) {
            return null;
        }
        String user = pending.user;
        beginAction("mfa", user, code);
        Account account = accounts.get(user);
        if (account == null || !pending.pendingSecondFactor) {
            return pending;
        }
        phase("claim", "passed", user);
        phase("prove", "passed", "password already checked");
        if (!account.secondFactor.equals(code)) {
            phase("mfa", "denied", code);
            refuse(401, "wrong-code", code);
            loginsRefused++;
            Trace.event("MFA_FAILED",
                    "The code is wrong, so the login stays half-finished: the credential in the "
                            + "client's hands still identifies nobody. This is the step that survives a "
                            + "leaked password — the attacker got this far and stops here",
                    "Код неверный, поэтому вход остаётся незавершённым: учётные данные в руках "
                            + "клиента по-прежнему никого не идентифицируют. Именно этот шаг переживает "
                            + "утечку пароля: злоумышленник дошёл сюда и здесь остановился",
                    List.of("user:" + user, "phase:mfa"), state());
            return pending;
        }
        phase("mfa", "passed", code);
        Trace.event("MFA_PASSED",
                "The second factor checks out. Two independent things had to be true — the password "
                        + "'" + user + "' knows and the code only their device produces — and an "
                        + "attacker generally has one of them at most",
                "Второй фактор сходится. Должны были выполниться два независимых условия — пароль, "
                        + "который знает «" + user + "», и код, который выдаёт только его устройство, — "
                        + "а у злоумышленника обычно есть в лучшем случае одно из них",
                List.of("user:" + user, "phase:mfa"), state());
        clientCredentials.remove(pending);
        return issue(user, pending.loginId);
    }

    // --------------------------------------------------------------- requests

    /** A request with no credential at all. */
    public void request(String path) {
        request(path, null);
    }

    /** A request that presents a credential — the normal case for a signed-in user. */
    public void request(String path, Credential credential) {
        requestAs(credential == null ? "anonymous" : credential.user, path, credential);
    }

    /**
     * A request sent by {@code actor} while presenting {@code credential}. Use it with
     * somebody else's credential to see what "bearer" really means.
     */
    public void requestAs(String actor, String path, Credential credential) {
        beginAction("request", actor, path);
        requests++;
        Trace.event("REQUEST_SENT",
                actor + " sends GET " + path + " presenting "
                        + (credential == null ? "no credential" : credentialLabel(credential))
                        + ". The server has no memory of the previous request, so this one has to carry "
                        + "its own proof — every single request is authenticated from scratch",
                actor + " отправляет GET " + path + ", предъявляя "
                        + (credential == null ? "ничего" : credentialLabel(credential))
                        + ". Сервер ничего не помнит о предыдущем запросе, поэтому этот обязан нести "
                        + "доказательство с собой: каждый запрос аутентифицируется заново",
                List.of("phase:claim"), state());

        if (credential == null) {
            phase("claim", "denied", "nothing presented");
            refuse(401, "no-credential", "");
            refused++;
            Trace.event("ANONYMOUS_REQUEST",
                    "Nothing was presented, so the server does not know who this is and answers 401 "
                            + "Unauthorized — 'come back with a credential'. Being anonymous is not an "
                            + "error in itself; it only becomes one when the endpoint needs to know who "
                            + "is asking",
                    "Ничего не предъявлено, поэтому сервер не знает, кто это, и отвечает 401 "
                            + "Unauthorized — «вернись с учётными данными». Анонимность сама по себе не "
                            + "ошибка: она становится ошибкой только там, где эндпоинту нужно знать, кто "
                            + "спрашивает",
                    List.of("phase:claim"), state());
            return;
        }

        phase("claim", "passed", credential.id);
        if (credential.pendingSecondFactor) {
            phase("prove", "denied", "mfa incomplete");
            refuse(401, "mfa-incomplete", credential.id);
            refused++;
            Trace.event("CREDENTIAL_REJECTED",
                    "This credential comes from a login that stopped at the second factor, so it "
                            + "identifies nobody and the request is refused. A half-finished login must "
                            + "not be usable — otherwise the second factor would be advice rather than "
                            + "a requirement",
                    "Эти учётные данные получены при входе, который остановился на втором факторе, "
                            + "поэтому они никого не идентифицируют и запрос отклоняется. "
                            + "Недозавершённый вход не должен работать — иначе второй фактор был бы "
                            + "рекомендацией, а не требованием",
                    List.of("phase:prove", "credential:" + credential.id), state());
            return;
        }

        Login login = loginRecords.get(credential.loginId);
        boolean expired = clock >= credential.expiresAt;
        if ("session".equals(credentialStyle) && !serverSessions.containsKey(credential.id)) {
            phase("prove", "denied", "no such session");
            refuse(401, login != null && login.loggedOut ? "logged-out" : "unknown-session",
                    credential.id);
            refused++;
            Trace.event("CREDENTIAL_REJECTED",
                    "The id is presented, the server looks it up, and there is no such session — so "
                            + "this is nobody. That lookup is the whole advantage of server-side "
                            + "sessions: the server decides, request by request, whether an id still "
                            + "means anything, and it can stop honouring one the instant it wants to",
                    "Идентификатор предъявлен, сервер ищет его у себя — и такой сессии нет, значит, "
                            + "это никто. В этом поиске и состоит всё преимущество серверных сессий: "
                            + "сервер от запроса к запросу решает, значит ли идентификатор ещё "
                            + "что-нибудь, и может перестать его признавать в любой момент",
                    List.of("phase:prove", "server", "credential:" + credential.id), state());
            return;
        }
        if (expired) {
            phase("prove", "denied", "expired");
            refuse(401, "expired", "t=" + credential.expiresAt);
            refused++;
            Trace.event("CREDENTIAL_EXPIRED",
                    "The credential says it is good until minute " + credential.expiresAt
                            + " and it is now minute " + clock + ", so it is refused. Short lifetimes "
                            + "are the main thing limiting the damage of a stolen credential: it stops "
                            + "working on its own, without anyone having to notice the theft",
                    "Учётные данные действительны до минуты " + credential.expiresAt
                            + ", а сейчас минута " + clock + " — поэтому отказ. Короткий срок жизни — "
                            + "главное, что ограничивает ущерб от украденных учётных данных: они "
                            + "перестают работать сами, и никому не нужно замечать кражу",
                    List.of("phase:prove", "credential:" + credential.id), state());
            return;
        }
        if ("token".equals(credentialStyle) && login != null && login.loggedOut) {
            phase("prove", "breach", "signature still valid");
            identity = new Identity(credential.user, "token", credential.id);
            phase("identity", "breach", credential.user);
            this.status = 200;
            this.decision = "breach";
            this.reason = "revoked-token";
            this.detail = credential.id;
            served++;
            breaches++;
            Trace.event("REVOKED_TOKEN_STILL_VALID",
                    "'" + credential.user + "' pressed log out, and this token still works: the "
                            + "signature is intact and the expiry has not passed, which is everything a "
                            + "stateless check looks at. There is nothing on the server to delete. This "
                            + "is the price of statelessness, and the answers are known — keep access "
                            + "tokens short-lived and revoke the refresh token, or keep a deny-list, "
                            + "which is a little bit of state again",
                    "«" + credential.user + "» нажал «выйти», а этот токен по-прежнему работает: "
                            + "подпись цела и срок не истёк — а больше проверка без состояния ни на что "
                            + "и не смотрит. Удалять на сервере нечего. Это и есть цена "
                            + "statelessness, и решения известны: делать access-токены "
                            + "короткоживущими и отзывать refresh-токен либо вести список "
                            + "отозванных — то есть завести немного состояния обратно",
                    List.of("phase:identity", "credential:" + credential.id), state());
            return;
        }

        phase("prove", "passed",
                "session".equals(credentialStyle) ? "found in store" : "signature verified");
        identity = new Identity(credential.user, credentialStyle, credential.id);
        phase("identity", "passed", credential.user);
        this.status = 200;
        this.decision = "allowed";
        served++;

        if (!actor.equals(credential.user)) {
            breaches++;
            this.decision = "breach";
            this.reason = "stolen-credential";
            this.detail = actor;
            phase("identity", "breach", credential.user);
            Trace.event("STOLEN_CREDENTIAL_ACCEPTED",
                    actor + " is sending " + credential.user + "'s credential, and the server serves "
                            + "the request as " + credential.user + " — correctly, by its own rules. A "
                            + "credential is a BEARER credential: whoever holds the value is the user, "
                            + "and the server has no way to tell the difference. That is why it travels "
                            + "over HTTPS only, why it lives in an HttpOnly cookie rather than "
                            + "somewhere a script can read it, and why it expires quickly",
                    actor + " отправляет учётные данные пользователя " + credential.user
                            + ", и сервер обслуживает запрос как " + credential.user
                            + " — совершенно по своим правилам. Учётные данные предъявительские: кто "
                            + "держит значение, тот и есть пользователь, и отличить сервер не может. "
                            + "Поэтому они ходят только по HTTPS, лежат в HttpOnly-куке, а не там, где "
                            + "их прочитает скрипт, и быстро протухают",
                    List.of("phase:identity", "credential:" + credential.id), state());
            return;
        }

        Trace.event("IDENTITY_ESTABLISHED",
                "The credential validates, so the server now knows the caller is '" + credential.user
                        + "' ("
                        + ("session".equals(credentialStyle)
                            ? "the id was found in the session store"
                            : "the signature and expiry were checked, with no lookup at all")
                        + ") and the request is served. Authentication has answered WHO — whether this "
                        + "user may do what they are asking is a separate question, and a separate "
                        + "check",
                "Учётные данные проходят проверку, поэтому сервер теперь знает, что вызывающий — «"
                        + credential.user + "» ("
                        + ("session".equals(credentialStyle)
                            ? "идентификатор нашёлся в хранилище сессий"
                            : "проверены подпись и срок действия, без единого обращения к хранилищу")
                        + "), и запрос обслуживается. Аутентификация ответила на вопрос КТО; можно ли "
                        + "этому пользователю то, о чём он просит, — отдельный вопрос и отдельная "
                        + "проверка",
                List.of("phase:identity", "credential:" + credential.id), state());
    }

    // ------------------------------------------------- lifetime and revocation

    /** Moves the clock forward, so credentials can reach their expiry. */
    public void advanceMinutes(int minutes) {
        clock += minutes;
        beginAction("clock", "", minutes + " min");
        Trace.event("TIME_PASSED",
                minutes + " minute(s) pass; the clock is now at minute " + clock
                        + ". Nothing was called and nothing changed on the server — but credentials "
                        + "carry an expiry, and time alone is enough to make one worthless",
                "Проходит минут: " + minutes + "; на часах теперь минута " + clock
                        + ". Никто ничего не вызывал и на сервере ничего не менялось — но у учётных "
                        + "данных есть срок действия, и одного лишь времени достаточно, чтобы они стали "
                        + "бесполезны",
                List.of("clock"), state());
    }

    /**
     * Exchanges a long-lived refresh right for a fresh short-lived credential —
     * the reason a 15-minute credential does not mean logging in every 15 minutes.
     */
    public Credential refresh(Credential credential) {
        if (credential == null) {
            return null;
        }
        beginAction("refresh", credential.user, credential.id);
        Login login = loginRecords.get(credential.loginId);
        if (login == null || login.loggedOut || clock >= login.refreshExpiresAt) {
            phase("prove", "denied", "refresh window closed");
            refuse(401, "refresh-expired", credential.id);
            refused++;
            Trace.event("CREDENTIAL_REJECTED",
                    "This login can no longer be renewed — it was ended, or the refresh window itself "
                            + "has closed — so there is nothing to renew and the user has to prove who "
                            + "they are again. That renewal window is the real 'stay signed in' "
                            + "setting, not the short access lifetime",
                    "Этот вход больше нельзя продлить — его завершили либо истекло само окно "
                            + "обновления, — поэтому продлевать нечего и пользователю придётся снова "
                            + "доказать, кто он. Именно это окно и есть настоящая настройка «оставаться "
                            + "в системе», а не короткий срок жизни access-токена",
                    List.of("phase:prove", "credential:" + credential.id), state());
            return null;
        }
        clientCredentials.remove(credential);
        serverSessions.remove(credential.id);
        Credential fresh = newCredential(credential.user, login.id);
        clientCredentials.add(fresh);
        if ("session".equals(credentialStyle)) {
            serverSessions.put(fresh.id, fresh);
        }
        phase("claim", "passed", credential.id);
        phase("prove", "passed", "refresh valid");
        phase("issue", "passed", fresh.id);
        this.status = 200;
        this.decision = "allowed";
        Trace.event("CREDENTIAL_REFRESHED",
                "A fresh credential " + fresh.id + " is issued, valid until minute " + fresh.expiresAt
                        + ", without the password being asked for again. This is how both goals are met "
                        + "at once: access credentials that die quickly if stolen, and a user who stays "
                        + "signed in for hours",
                "Выдаются свежие учётные данные " + fresh.id + ", действительные до минуты "
                        + fresh.expiresAt + ", и пароль при этом заново не спрашивают. Так достигаются "
                        + "обе цели сразу: учётные данные доступа быстро умирают, если их украли, а "
                        + "пользователь остаётся в системе часами",
                List.of("phase:issue", "credential:" + fresh.id), state());
        return fresh;
    }

    /** Ends the login. What that actually costs depends entirely on the credential style. */
    public void logout(Credential credential) {
        if (credential == null) {
            return;
        }
        beginAction("logout", credential.user, credential.id);
        Login login = loginRecords.get(credential.loginId);
        if (login != null) {
            login.loggedOut = true;
        }
        credential.heldByClient = false;
        boolean serverForgets = serverSessions.remove(credential.id) != null;
        phase("claim", "passed", credential.id);
        phase("prove", "passed", "");
        this.status = 200;
        this.decision = "allowed";
        Trace.event("LOGGED_OUT",
                "'" + credential.user + "' logs out. "
                        + (serverForgets
                            ? "The server deletes the session record, so the very next request "
                              + "presenting that id finds nothing behind it. Revocation here is one "
                              + "DELETE and it is immediate — that lookup you pay for on every request "
                              + "is what you are buying"
                            : "The client drops its copy of the token and the server does nothing, "
                              + "because there is nothing to do: it never recorded the token in the "
                              + "first place. Any copy that was made elsewhere keeps working until it "
                              + "expires"),
                "«" + credential.user + "» выходит из системы. "
                        + (serverForgets
                            ? "Сервер удаляет запись о сессии, поэтому следующий же запрос с этим "
                              + "идентификатором не найдёт за ним ничего. Отзыв здесь — это один DELETE, "
                              + "и он мгновенный: именно за это вы и платите поиском на каждом запросе"
                            : "Клиент выбрасывает свою копию токена, а сервер не делает ничего, потому "
                              + "что делать нечего: он этот токен никогда и не записывал. Любая копия, "
                              + "сделанная где-то ещё, продолжит работать до истечения срока"),
                List.of("server", "credential:" + credential.id), state());
    }

    /** Prints what the whole run added up to. */
    public void report() {
        Trace.event("AUTH_AUDIT",
                "After the run: logins granted " + loginsGranted + ", logins refused " + loginsRefused
                        + ", requests " + requests + ", served " + served + ", refused " + refused
                        + ", requests served that should NOT have been " + breaches
                        + ". Authentication is a lifecycle, not a checkbox: a secret at rest, one "
                        + "moment of proof, a credential with a lifetime, a check on every request, and "
                        + "a way to end it",
                "Итоги прогона: успешных входов " + loginsGranted + ", отказов во входе "
                        + loginsRefused + ", запросов " + requests + ", обслужено " + served
                        + ", отклонено " + refused + ", обслужено запросов, которые обслуживать НЕ "
                        + "следовало: " + breaches
                        + ". Аутентификация — это жизненный цикл, а не галочка: секрет в хранилище, "
                        + "один момент доказательства, учётные данные со сроком жизни, проверка на "
                        + "каждом запросе и способ всё это прекратить",
                List.of(), state());
    }

    // --------------------------------------------------------------- internals

    /** Records the login this credential belongs to; renewal and logout are per login, not per credential. */
    private String newLogin(String user) {
        String loginId = "L" + (++loginCounter);
        loginRecords.put(loginId, new Login(loginId, user, clock + REFRESH_LIFETIME_MINUTES));
        return loginId;
    }

    private Credential issue(String user, String loginId) {
        Credential credential = newCredential(user, loginId);
        clientCredentials.add(credential);
        if ("session".equals(credentialStyle)) {
            serverSessions.put(credential.id, credential);
        }
        identity = new Identity(user, credentialStyle, credential.id);
        phase("issue", "passed", credential.id);
        phase("identity", "passed", user);
        this.status = 200;
        this.decision = "allowed";
        loginsGranted++;
        Trace.event("CREDENTIAL_ISSUED",
                "session".equals(credentialStyle)
                        ? "The server creates session " + credential.id + " for '" + user
                          + "', valid until minute " + credential.expiresAt
                          + ", and sends the id back as a cookie. The id is deliberately meaningless: "
                          + "everything it stands for lives on the server, so a client can neither read "
                          + "nor edit any of it — and the server can drop the whole record whenever it "
                          + "likes"
                        : "The server signs a token " + credential.id + " saying 'this is " + user
                          + ", until minute " + credential.expiresAt + "' and returns it. The facts "
                          + "travel with the client, and the signature is what stops the client "
                          + "rewriting them — anyone can read a token, only the server can produce a "
                          + "valid one. Nothing is stored server-side, so nothing has to be looked up",
                "session".equals(credentialStyle)
                        ? "Сервер создаёт сессию " + credential.id + " для «" + user
                          + "», действительную до минуты " + credential.expiresAt
                          + ", и отправляет идентификатор обратно в куке. Идентификатор намеренно "
                          + "бессмыслен: всё, что за ним стоит, живёт на сервере, поэтому клиент не "
                          + "может ни прочитать это, ни изменить, — а сервер может выбросить всю запись "
                          + "когда угодно"
                        : "Сервер подписывает токен " + credential.id + " с содержанием «это " + user
                          + ", до минуты " + credential.expiresAt + "» и возвращает его. Факты едут "
                          + "вместе с клиентом, а подпись не даёт клиенту их переписать: прочитать "
                          + "токен может любой, выпустить корректный — только сервер. На сервере ничего "
                          + "не хранится, поэтому и искать нечего",
                List.of("phase:issue", "credential:" + credential.id,
                        "session".equals(credentialStyle) ? "server" : "client"), state());
        return credential;
    }

    private Credential newCredential(String user, String loginId) {
        credentialCounter++;
        String prefix = "session".equals(credentialStyle) ? "s" : "t";
        Credential credential = new Credential(prefix + credentialCounter, user, credentialStyle,
                loginId);
        credential.issuedAt = clock;
        credential.expiresAt = clock + ACCESS_LIFETIME_MINUTES;
        return credential;
    }

    private boolean matches(Account account, String password) {
        return plaintextPasswords
                ? account.stored.equals(password)
                : account.stored.equals(hash(account.salt, password));
    }

    /** A stand-in for a slow password hash: deterministic, so examples always trace the same. */
    private static String hash(String salt, String password) {
        return Integer.toHexString((salt + "|" + password).hashCode() & 0x7fffffff);
    }

    private static String saltFor(String user) {
        return Integer.toHexString(("salt|" + user).hashCode() & 0xffff);
    }

    private String credentialLabel(Credential credential) {
        return "session".equals(credentialStyle)
                ? "cookie " + credential.id
                : "Authorization: Bearer " + credential.id;
    }

    private void beginAction(String kind, String actor, String detail) {
        actionKind = kind;
        actionActor = actor;
        actionDetail = detail;
        identity = null;
        status = null;
        decision = "pending";
        reason = null;
        this.detail = "";
        resetPhases();
    }

    private void refuse(int status, String reason, String detail) {
        this.status = status;
        this.decision = "denied";
        this.reason = reason;
        this.detail = detail;
    }

    private void resetPhases() {
        phases.clear();
        for (String id : PHASES) {
            phases.put(id, new String[]{"pending", ""});
        }
    }

    private void phase(String id, String status, String detail) {
        phases.put(id, new String[]{status, detail});
    }

    private String credentialState(Credential credential) {
        if (credential.pendingSecondFactor) {
            return "pending";
        }
        if (!credential.heldByClient) {
            return "dropped";
        }
        return clock >= credential.expiresAt ? "expired" : "valid";
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("credentialStyle", credentialStyle);
        s.put("passwordStorage", plaintextPasswords ? "plaintext" : "hashed");
        s.put("clock", clock);
        s.put("accessLifetime", ACCESS_LIFETIME_MINUTES);
        s.put("loginRateLimit", loginRateLimit);

        List<Object> users = new ArrayList<>();
        for (Account account : accounts.values()) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("name", account.name);
            u.put("stored", account.stored);
            u.put("salt", plaintextPasswords ? "" : account.salt);
            u.put("secondFactor", account.secondFactor != null);
            u.put("failedAttempts", account.failedAttempts);
            users.add(u);
        }
        s.put("users", users);

        List<Object> serverStore = new ArrayList<>();
        for (Credential credential : serverSessions.values()) {
            serverStore.add(credentialSnapshot(credential));
        }
        s.put("serverStore", serverStore);

        List<Object> clientStore = new ArrayList<>();
        for (Credential credential : clientCredentials) {
            clientStore.add(credentialSnapshot(credential));
        }
        s.put("clientStore", clientStore);

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("kind", actionKind);
        action.put("actor", actionActor);
        action.put("detail", actionDetail);
        s.put("action", action);

        List<Object> phaseList = new ArrayList<>();
        for (Map.Entry<String, String[]> phase : phases.entrySet()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", phase.getKey());
            p.put("status", phase.getValue()[0]);
            p.put("detail", phase.getValue()[1]);
            phaseList.add(p);
        }
        s.put("phases", phaseList);

        if (identity != null) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("user", identity.user);
            i.put("source", identity.source);
            i.put("credentialId", identity.credentialId);
            s.put("identity", i);
        } else {
            s.put("identity", null);
        }

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("status", status);
        outcome.put("decision", decision);
        outcome.put("reason", reason);
        outcome.put("detail", detail);
        s.put("outcome", outcome);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("loginsGranted", loginsGranted);
        stats.put("loginsRefused", loginsRefused);
        stats.put("requests", requests);
        stats.put("served", served);
        stats.put("refused", refused);
        stats.put("breaches", breaches);
        s.put("stats", stats);
        return s;
    }

    private Map<String, Object> credentialSnapshot(Credential credential) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", credential.id);
        c.put("user", credential.user);
        c.put("kind", credential.kind);
        c.put("issuedAt", credential.issuedAt);
        c.put("expiresAt", credential.expiresAt);
        c.put("state", credentialState(credential));
        return c;
    }
}
