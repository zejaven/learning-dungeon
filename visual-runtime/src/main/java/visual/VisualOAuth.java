package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A <em>teaching model</em> of OAuth 2.0 and OpenID Connect: what actually travels
 * between the four parties, on which channel, and which check stops which attack.
 *
 * <p>OAuth exists to answer one question: how can an app act on your behalf at
 * some provider <b>without ever seeing your password</b>. That is delegated
 * <em>authorization</em>. OpenID Connect is a thin layer on top that answers a
 * different question — <b>who the user is</b> — because an access token
 * deliberately says nothing about that.
 *
 * <p>Four roles, and the whole protocol is easier once they are separated:
 * <ul>
 *   <li><b>resource owner</b> — the user, who owns the data and grants access;</li>
 *   <li><b>client</b> — the app that wants access (it is never "the user");</li>
 *   <li><b>authorization server</b> — where the user authenticates and consents,
 *       and the only party that issues tokens;</li>
 *   <li><b>resource server</b> — the API that accepts the access token.</li>
 * </ul>
 *
 * <p>The other thing worth watching is the <b>channel</b>. The front channel goes
 * through the user's browser as redirects — visible in the URL bar, in history, in
 * referrers, in proxy logs — so nothing secret may travel on it. The back channel
 * is a direct server-to-server call, which is where the code is swapped for tokens.
 * Almost every OAuth attack is about something ending up on the wrong channel.
 *
 * <p>The model makes the classic mistakes visible rather than merely describing
 * them: a stolen authorization code, a code injected without a {@code state} check,
 * an access token used as proof of identity, the password grant, and the implicit
 * flow. Those show up as {@code decision: "breach"} (it worked and it should not
 * have) or {@code decision: "risky"} (it worked, and you should not build it).
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free.
 */
public class VisualOAuth {

    /** How long an issued access token stays valid, in model minutes. */
    public static final int ACCESS_LIFETIME_MINUTES = 15;
    /** How long an authorization code stays usable, in model minutes. Real ones are seconds. */
    public static final int CODE_LIFETIME_MINUTES = 1;
    /** How long the client may keep renewing without sending the user back, in model minutes. */
    public static final int REFRESH_LIFETIME_MINUTES = 480;

    private static final String CLIENT_ID = "photo-printer";
    private static final String CLIENT_SECRET = "cs-7f3a";
    private static final String REDIRECT_URI = "https://printer.app/callback";
    private static final String PROVIDER = "accounts.provider.com";
    private static final String API = "api.provider.com";
    private static final String VERIFIER = "v-8821";
    private static final String CHALLENGE = "S256(v-8821)";
    private static final String CLIENT_STATE = "st-4417";
    private static final String NONCE = "n-3390";
    private static final String ATTACKER_STATE = "st-9999";

    /** The stages of the authorization code flow, in the order they run. */
    private static final List<String> STEPS =
            List.of("authorize", "authenticate", "consent", "code", "exchange", "call");

    // ----------------------------------------------------------------- handles

    /** The browser sitting on the provider's page, with the request the client asked for. */
    public static final class Redirect {

        private final String user;
        private final List<String> scopes;
        private final String state;
        private final String challenge;

        private Redirect(String user, List<String> scopes, String state, String challenge) {
            this.user = user;
            this.scopes = scopes;
            this.state = state;
            this.challenge = challenge;
        }

        /** Which user is about to authenticate at the provider. */
        public String user() {
            return user;
        }
    }

    /**
     * An authorization code as somebody is holding it. It is deliberately almost
     * worthless on its own: short-lived, single-use, and redeemable only by the
     * client that can also prove the PKCE verifier or its own credentials.
     */
    public static final class Grant {

        private final String code;
        private final String state;
        private final String user;
        private final List<String> scopes;
        private final String challenge;
        private final int expiresAt;
        private final boolean injected;
        private String holder;
        private boolean used;

        private Grant(String code, String state, String user, List<String> scopes,
                      String challenge, int expiresAt, boolean injected, String holder) {
            this.code = code;
            this.state = state;
            this.user = user;
            this.scopes = scopes;
            this.challenge = challenge;
            this.expiresAt = expiresAt;
            this.injected = injected;
            this.holder = holder;
        }

        /** The value that came back on the redirect. */
        public String code() {
            return code;
        }
    }

    /** What the token endpoint hands back. The access token is for the API, the id_token is for the client. */
    public static final class Tokens {

        private final String accessToken;
        private final String refreshToken;
        private final String idToken;
        private final String subject;
        private final List<String> scopes;
        private int expiresAt;
        private int refreshExpiresAt;

        private Tokens(String accessToken, String refreshToken, String idToken, String subject,
                       List<String> scopes) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
            this.subject = subject;
            this.scopes = scopes;
        }

        /** The value the client puts in {@code Authorization: Bearer ...}. */
        public String accessToken() {
            return accessToken;
        }

        /** Whether an id_token came with them — the difference between OAuth 2.0 and OIDC. */
        public boolean hasIdToken() {
            return idToken != null;
        }

        /** Which user these tokens are about; empty for a machine-to-machine grant. */
        public String subject() {
            return subject == null ? "" : subject;
        }

        /** The scopes the provider actually attached to the access token. */
        public List<String> scopes() {
            return List.copyOf(scopes);
        }
    }

    /** Who the client believes the user is, and what it read that from. */
    private static final class Identity {

        private final String subject;
        private final String source;
        private final boolean verified;

        private Identity(String subject, String source, boolean verified) {
            this.subject = subject;
            this.source = source;
            this.verified = verified;
        }
    }

    // ------------------------------------------------------------------- state

    private final String protocol;
    private boolean confidentialClient = true;
    private boolean pkce = true;
    private boolean stateCheck = true;

    private final Set<String> providerAccounts = new LinkedHashSet<>();
    private final Map<String, String[]> steps = new LinkedHashMap<>();
    private final List<String> requestedScopes = new ArrayList<>();
    private final List<String> grantedScopes = new ArrayList<>();

    private int clock;
    private int codeCounter;
    private int tokenCounter;

    private String userName = "";
    private boolean userAuthenticatedAtProvider;
    private boolean attackerPresent;
    private String pendingState;
    private String clientCode;
    private String attackerCode;
    private String clientVerifier;
    private Tokens clientTokens;
    private Tokens attackerTokens;
    private Identity identity;

    private String messageFrom = "";
    private String messageTo = "";
    private String messageChannel = "none";
    private String messageLabel = "";
    private final List<String[]> messageParams = new ArrayList<>();

    private Integer status;
    private String decision = "idle";
    private String reason;
    private String detail = "";

    private int tokensIssued;
    private int apiServed;
    private int apiRefused;
    private int warnings;
    private int breaches;

    private VisualOAuth(String protocol) {
        this.protocol = protocol;
        resetSteps();
    }

    /**
     * Plain OAuth 2.0: the app is asking for permission to <em>do something</em> at
     * the provider. What comes back is a key to an API — not a statement about who
     * the user is.
     */
    public static VisualOAuth oauth2() {
        VisualOAuth flow = new VisualOAuth("oauth2");
        Trace.event("OAUTH_SETUP",
                "A plain OAuth 2.0 flow. Four parties: the USER who owns the data, the CLIENT app "
                        + "that wants access, the AUTHORIZATION SERVER where the user logs in and "
                        + "consents, and the RESOURCE SERVER that holds the data. OAuth answers "
                        + "'may this app do this on your behalf' — it is delegated AUTHORIZATION, and "
                        + "on its own it never tells the client who the user is",
                "Обычный поток OAuth 2.0. Четыре участника: ПОЛЬЗОВАТЕЛЬ, которому принадлежат "
                        + "данные, КЛИЕНТ — приложение, которому нужен доступ, СЕРВЕР АВТОРИЗАЦИИ, где "
                        + "пользователь входит и даёт согласие, и СЕРВЕР РЕСУРСОВ, где лежат данные. "
                        + "OAuth отвечает на вопрос «можно ли этому приложению делать это от твоего "
                        + "имени» — это делегированная АВТОРИЗАЦИЯ, и сама по себе она никогда не "
                        + "сообщает клиенту, кто такой пользователь",
                List.of("party:client", "party:provider"), flow.state());
        return flow;
    }

    /**
     * OpenID Connect: the same flow, plus an {@code id_token} — a signed statement
     * addressed to this client saying who just authenticated. This is what "log in
     * with Google" actually is.
     */
    public static VisualOAuth openIdConnect() {
        VisualOAuth flow = new VisualOAuth("oidc");
        Trace.event("OAUTH_SETUP",
                "An OpenID Connect flow — OAuth 2.0 with an identity layer on top. Exactly the same "
                        + "messages, plus the 'openid' scope, a nonce, and one extra thing in the "
                        + "response: an id_token. That id_token is a signed JWT addressed to THIS "
                        + "client saying who authenticated, when, and how. 'Log in with Google' is "
                        + "OIDC; OAuth alone cannot do it correctly",
                "Поток OpenID Connect — это OAuth 2.0 со слоем идентификации сверху. Ровно те же "
                        + "сообщения плюс scope «openid», nonce и одна дополнительная вещь в ответе: "
                        + "id_token. Этот id_token — подписанный JWT, адресованный ИМЕННО ЭТОМУ "
                        + "клиенту, в котором сказано, кто аутентифицировался, когда и как. «Войти "
                        + "через Google» — это OIDC; один только OAuth сделать это корректно не может",
                List.of("party:client", "party:provider"), flow.state());
        return flow;
    }

    /**
     * Makes the client a public one — a SPA or a mobile app, which cannot keep a
     * secret because its code is on the user's machine.
     */
    public VisualOAuth publicClient() {
        this.confidentialClient = false;
        Trace.event("OAUTH_SETUP",
                "The client is now a PUBLIC client: a single-page app or a mobile app. Its code runs "
                        + "on the user's machine, so it cannot hold a client_secret — anything shipped "
                        + "to the browser or an app store is readable. It can still authenticate the "
                        + "USER perfectly well; it just cannot prove that it is itself, which is why "
                        + "PKCE stopped being optional",
                "Теперь клиент ПУБЛИЧНЫЙ: одностраничное или мобильное приложение. Его код "
                        + "выполняется на машине пользователя, поэтому client_secret он хранить не "
                        + "может — всё, что уехало в браузер или в магазин приложений, можно "
                        + "прочитать. Аутентифицировать ПОЛЬЗОВАТЕЛЯ это ничуть не мешает; клиент "
                        + "лишь не может доказать, что он — это он, и поэтому PKCE перестал быть "
                        + "необязательным",
                List.of("party:client"), state());
        return this;
    }

    /** Turns PKCE off, so an authorization code becomes usable by whoever grabs it. */
    public VisualOAuth withoutPkce() {
        this.pkce = false;
        Trace.event("OAUTH_SETUP",
                "PKCE is switched OFF. Normally the client invents a random code_verifier, sends only "
                        + "its SHA-256 hash (the code_challenge) on the front channel, and must present "
                        + "the verifier itself when redeeming the code. Without it, the code that "
                        + "travels through the browser is the whole secret",
                "PKCE ВЫКЛЮЧЕН. Обычно клиент придумывает случайный code_verifier, отправляет по "
                        + "фронт-каналу только его SHA-256-хеш (code_challenge), а при обмене кода "
                        + "обязан предъявить сам verifier. Без этого код, который едет через браузер, "
                        + "и есть весь секрет",
                List.of("party:client"), state());
        return this;
    }

    /** Stops the client from checking that a callback belongs to a flow it started. */
    public VisualOAuth withoutStateCheck() {
        this.stateCheck = false;
        Trace.event("OAUTH_SETUP",
                "The client no longer sends and checks a 'state' parameter. state is a random value "
                        + "the client stores before the redirect and compares when the callback comes "
                        + "back — the proof that THIS callback belongs to a flow THIS browser started. "
                        + "Without it, the callback URL is just an endpoint anyone can call",
                "Клиент больше не отправляет и не проверяет параметр «state». state — это случайное "
                        + "значение, которое клиент сохраняет перед редиректом и сравнивает, когда "
                        + "приходит callback: доказательство, что ЭТОТ callback относится к потоку, "
                        + "начатому ЭТИМ браузером. Без него callback-URL — просто эндпоинт, который "
                        + "может вызвать кто угодно",
                List.of("party:client"), state());
        return this;
    }

    // ------------------------------------------------------- the front channel

    /**
     * The client sends the browser to the provider. Nothing secret goes here — this
     * is a URL the user can read, edit and share.
     */
    public Redirect authorize(String user, String... scopes) {
        this.userName = user;
        this.userAuthenticatedAtProvider = false;
        this.identity = null;
        this.clientTokens = null;
        providerAccounts.add(user);
        resetSteps();
        requestedScopes.clear();
        grantedScopes.clear();
        if ("oidc".equals(protocol)) {
            requestedScopes.add("openid");
        }
        for (String scope : scopes) {
            if (!requestedScopes.contains(scope)) {
                requestedScopes.add(scope);
            }
        }
        this.pendingState = stateCheck ? CLIENT_STATE : null;
        this.clientVerifier = pkce ? VERIFIER : null;

        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"response_type", "code"});
        params.add(new String[]{"client_id", CLIENT_ID});
        params.add(new String[]{"redirect_uri", REDIRECT_URI});
        params.add(new String[]{"scope", String.join(" ", requestedScopes)});
        if (stateCheck) {
            params.add(new String[]{"state", CLIENT_STATE});
        }
        if (pkce) {
            params.add(new String[]{"code_challenge", CHALLENGE});
            params.add(new String[]{"code_challenge_method", "S256"});
        }
        if ("oidc".equals(protocol)) {
            params.add(new String[]{"nonce", NONCE});
        }
        message("client", "provider", "front", "302 -> https://" + PROVIDER + "/authorize", params);
        step("authorize", "passed", String.join(" ", requestedScopes));
        this.status = 302;
        this.decision = "pending";
        this.reason = null;
        this.detail = "";
        Trace.event("AUTHORIZATION_REQUEST",
                "The client does not call the provider — it REDIRECTS the browser to it, with what it "
                        + "wants written in the query string. Everything here is public by "
                        + (pkce ? "design: the code_challenge is a hash, so seeing it buys nothing"
                                : "necessity, and with PKCE off there is nothing here to protect the "
                                  + "code that comes back")
                        + ". Note what is NOT here: no password, and no way for the client to say who "
                        + "the user is — that is entirely the provider's business",
                "Клиент не вызывает провайдера — он ПЕРЕНАПРАВЛЯЕТ к нему браузер, записав в "
                        + "query-строку то, что ему нужно. Здесь всё публично "
                        + (pkce ? "по устройству: code_challenge — это хеш, и увидеть его бесполезно"
                                : "поневоле, и при выключенном PKCE здесь нет ничего, что защитило бы "
                                  + "возвращающийся код")
                        + ". Обратите внимание, чего здесь НЕТ: пароля и какой-либо возможности для "
                        + "клиента сообщить, кто пользователь, — это целиком дело провайдера",
                List.of("channel:front", "party:provider", "step:authorize"), state());
        return new Redirect(user, List.copyOf(requestedScopes), pendingState, pkce ? CHALLENGE : null);
    }

    /** The user authenticates at the provider and consents to everything that was asked for. */
    public Grant approve(Redirect redirect) {
        return approveOnly(redirect, redirect.scopes.toArray(new String[0]));
    }

    /**
     * The user authenticates at the provider and consents to only some of the
     * requested scopes — which is the whole point of a consent screen.
     */
    public Grant approveOnly(Redirect redirect, String... scopes) {
        if (redirect == null) {
            return null;
        }
        userAuthenticatedAtProvider = true;
        step("authenticate", "passed", redirect.user);
        message("user", "provider", "front", "POST https://" + PROVIDER + "/login",
                List.of(new String[]{"username", redirect.user}, new String[]{"password", "*****"},
                        new String[]{"2fa", "device"}));
        this.status = 200;
        this.decision = "pending";
        Trace.event("USER_AUTHENTICATED_AT_PROVIDER",
                "The user types their password into the PROVIDER's own page, at the provider's own "
                        + "domain — never into the client. This is the single most important line in "
                        + "the whole protocol: the client never learns the password, so it cannot leak "
                        + "it, cannot reuse it elsewhere, and does not have to be trusted with it. It "
                        + "also means the provider's MFA, SSO and risk checks apply as usual, and the "
                        + "app gets them for free",
                "Пользователь вводит пароль на СОБСТВЕННОЙ странице ПРОВАЙДЕРА, на домене "
                        + "провайдера, — и никогда в клиенте. Это самая важная строчка во всём "
                        + "протоколе: клиент не узнаёт пароль, поэтому не может его утечь, не может "
                        + "использовать его где-то ещё, и ему не нужно этот пароль доверять. Заодно "
                        + "продолжают работать MFA, SSO и антифрод провайдера — приложение получает "
                        + "их бесплатно",
                List.of("party:user", "party:provider", "channel:front", "step:authenticate"), state());

        grantedScopes.clear();
        for (String scope : scopes) {
            if (redirect.scopes.contains(scope) && !grantedScopes.contains(scope)) {
                grantedScopes.add(scope);
            }
        }

        if (grantedScopes.isEmpty()) {
            step("consent", "denied", "access_denied");
            message("provider", "client", "front", "302 -> " + REDIRECT_URI,
                    List.<String[]>of(new String[]{"error", "access_denied"}));
            refuse(302, "consent-denied", redirect.user);
            Trace.event("CONSENT_DENIED",
                    "The user says no, and the client gets a redirect carrying an error instead of a "
                            + "code. Nothing is issued and nothing is half-granted. Consent is a real "
                            + "decision point, which is why asking for a huge scope list up front is "
                            + "how you lose users at exactly this screen",
                    "Пользователь отказывается, и клиент получает редирект с ошибкой вместо кода. "
                            + "Ничего не выдаётся и ничего не выдаётся «наполовину». Согласие — это "
                            + "настоящая точка принятия решения, и поэтому просить сразу огромный "
                            + "список scope — верный способ потерять пользователей ровно на этом "
                            + "экране",
                    List.of("party:user", "channel:front", "step:consent"), state());
            return null;
        }

        step("consent", grantedScopes.size() == redirect.scopes.size() ? "passed" : "partial",
                String.join(" ", grantedScopes));
        message("user", "provider", "front", "POST https://" + PROVIDER + "/consent",
                List.<String[]>of(new String[]{"granted", String.join(" ", grantedScopes)}));
        this.status = 200;
        Trace.event("CONSENT_GRANTED",
                "The provider asks the user, in its own words, exactly what this app is asking for, "
                        + "and the user grants " + String.join(" ", grantedScopes)
                        + ". The scopes are the limit of the whole grant: whatever the app does later, "
                        + "it does with a token that carries only these. Ask for the narrowest scopes "
                        + "you can, because this screen is where the user decides whether to trust you",
                "Провайдер своими словами спрашивает пользователя, чего именно хочет это "
                        + "приложение, и пользователь выдаёт: " + String.join(" ", grantedScopes)
                        + ". Scope — это граница всего разрешения: что бы приложение ни делало "
                        + "дальше, оно делает это с токеном, в котором только они. Просите самые "
                        + "узкие scope, какие можете, — именно на этом экране пользователь решает, "
                        + "доверять вам или нет",
                List.of("party:user", "channel:front", "step:consent"), state());

        codeCounter++;
        String code = "ac" + codeCounter;
        Grant grant = new Grant(code, redirect.state, redirect.user, List.copyOf(grantedScopes),
                redirect.challenge, clock + CODE_LIFETIME_MINUTES, false, "client");
        this.clientCode = code;
        step("code", "passed", code);
        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"code", code});
        if (redirect.state != null) {
            params.add(new String[]{"state", redirect.state});
        }
        message("provider", "client", "front", "302 -> " + REDIRECT_URI, params);
        this.status = 302;
        Trace.event("AUTHORIZATION_CODE_RETURNED",
                "The provider sends the browser back to the client's redirect_uri with a CODE, not a "
                        + "token. The code goes over the front channel, where it is visible, so it is "
                        + "built to be nearly worthless on its own: single-use, valid for about a "
                        + "minute, bound to this client and this redirect_uri"
                        + (pkce ? ", and redeemable only by whoever knows the code_verifier" : "")
                        + ". The valuable thing is fetched separately, on a channel nobody can watch",
                "Провайдер возвращает браузер на redirect_uri клиента с КОДОМ, а не с токеном. Код "
                        + "едет по фронт-каналу, где он виден, поэтому его специально сделали почти "
                        + "бесполезным сам по себе: одноразовый, живёт около минуты, привязан к этому "
                        + "клиенту и этому redirect_uri"
                        + (pkce ? ", и обменять его может только тот, кто знает code_verifier" : "")
                        + ". Ценное забирают отдельно — по каналу, за которым никто не может "
                        + "подсмотреть",
                List.of("channel:front", "party:client", "step:code", "code:" + code), state());
        return grant;
    }

    /** The user closes the consent screen without granting anything. */
    public void deny(Redirect redirect) {
        if (redirect != null) {
            approveOnly(redirect);
        }
    }

    // -------------------------------------------------------- the back channel

    /**
     * The client swaps the code for tokens on a direct server-to-server call. This
     * is where it proves it is itself, and where the valuable things appear.
     */
    public Tokens exchange(Grant grant) {
        if (grant == null) {
            return null;
        }
        if (stateCheck) {
            if (!Objects.equals(grant.state, pendingState)) {
                step("code", "denied", "state mismatch");
                message("attacker", "client", "front", "GET " + REDIRECT_URI,
                        List.of(new String[]{"code", grant.code},
                                new String[]{"state", String.valueOf(grant.state)}));
                refuse(400, "state-mismatch", grant.code);
                Trace.event("STATE_MISMATCH_BLOCKED",
                        "The callback carries state=" + grant.state + " and the client is waiting for "
                                + pendingState + ", so it throws the callback away without touching the "
                                + "code. That single comparison is what makes the redirect_uri "
                                + "endpoint safe: it only accepts a callback for a flow this browser "
                                + "actually started, which kills login CSRF and code injection in one "
                                + "line",
                        "В callback пришёл state=" + grant.state + ", а клиент ждёт " + pendingState
                                + ", поэтому он выбрасывает callback, даже не притрагиваясь к коду. "
                                + "Именно это единственное сравнение делает эндпоинт redirect_uri "
                                + "безопасным: он принимает только callback того потока, который "
                                + "действительно начал этот браузер, — и одной строкой убивает и "
                                + "login CSRF, и внедрение кода",
                        List.of("step:code", "party:client"), state());
                return null;
            }
            step("code", "passed", "state " + grant.state + " matches");
            this.status = 200;
            this.decision = "pending";
            message("client", "client", "none", "compare state", List.of(
                    new String[]{"expected", String.valueOf(pendingState)},
                    new String[]{"received", String.valueOf(grant.state)}));
            Trace.event("STATE_VERIFIED",
                    "Before doing anything with the code, the client checks that the state it gets "
                            + "back is the one it stored before the redirect. It matches, so this "
                            + "callback belongs to this browser's own flow. This check costs nothing "
                            + "and is skipped depressingly often",
                    "Прежде чем что-либо делать с кодом, клиент проверяет, что вернувшийся state — "
                            + "тот самый, который он сохранил перед редиректом. Совпало, значит этот "
                            + "callback относится к потоку самого этого браузера. Проверка не стоит "
                            + "ничего, и её пропускают удручающе часто",
                    List.of("step:code", "party:client"), state());
        } else if (grant.injected) {
            attackerPresent = true;
            step("code", "breach", "no state to compare");
            message("attacker", "client", "front", "GET " + REDIRECT_URI,
                    List.<String[]>of(new String[]{"code", grant.code}));
            this.status = 200;
            this.decision = "breach";
            this.reason = "code-injected";
            this.detail = grant.user;
            breaches++;
            Trace.event("CODE_INJECTION_ACCEPTED",
                    "With no state to compare, the client accepts a callback it never started and "
                            + "redeems somebody else's code. In a moment it will be holding tokens for "
                            + "'" + grant.user + "' while the person sitting at the browser is '"
                            + userName + "' — so whatever the victim saves next lands in the "
                            + "attacker's account, where the attacker can read it",
                    "Сравнивать нечего, поэтому клиент принимает callback, которого не начинал, и "
                            + "обменивает чужой код. Через мгновение он будет держать токены "
                            + "пользователя «" + grant.user + "», хотя за браузером сидит «" + userName
                            + "»: всё, что жертва сохранит дальше, попадёт в аккаунт злоумышленника, "
                            + "где он это и прочитает",
                    List.of("step:code", "party:attacker"), state());
        } else {
            step("code", "skipped", "no state checked");
        }

        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"grant_type", "authorization_code"});
        params.add(new String[]{"code", grant.code});
        params.add(new String[]{"redirect_uri", REDIRECT_URI});
        params.add(new String[]{"client_id", CLIENT_ID});
        if (confidentialClient) {
            params.add(new String[]{"client_secret", CLIENT_SECRET});
        }
        if (pkce) {
            params.add(new String[]{"code_verifier", VERIFIER});
        }
        message("client", "provider", "back", "POST https://" + PROVIDER + "/token", params);
        this.status = null;
        Trace.event("TOKEN_REQUEST",
                "Now the client calls the provider DIRECTLY, server to server. The browser is not "
                        + "involved and nothing here appears in any URL, which is why the secrets live "
                        + "on this channel: "
                        + (confidentialClient ? "the client_secret" : "no client_secret — this client "
                                + "cannot keep one")
                        + (pkce ? " and the code_verifier, whose SHA-256 must equal the challenge sent "
                                + "earlier" : " and, with PKCE off, nothing but the code itself")
                        + ". Front channel for redirects, back channel for anything valuable — that "
                        + "split IS the design",
                "Теперь клиент обращается к провайдеру НАПРЯМУЮ, сервер к серверу. Браузер в этом "
                        + "не участвует, и ничего отсюда не попадает ни в какой URL — поэтому секреты "
                        + "живут именно на этом канале: "
                        + (confidentialClient ? "client_secret" : "client_secret нет — такой клиент "
                                + "хранить его не может")
                        + (pkce ? " и code_verifier, SHA-256 которого обязан совпасть с отправленным "
                                + "ранее challenge" : " и, при выключенном PKCE, ничего, кроме самого "
                                + "кода")
                        + ". Фронт-канал — для редиректов, бэк-канал — для всего ценного; это "
                        + "разделение и ЕСТЬ вся конструкция",
                List.of("channel:back", "party:provider", "step:exchange"), state());

        if (grant.used || clock > grant.expiresAt) {
            step("exchange", "denied", grant.used ? "code already used" : "code expired");
            refuse(400, grant.used ? "code-reused" : "code-expired", grant.code);
            Trace.event("AUTHORIZATION_CODE_REJECTED",
                    grant.used
                            ? "The code was already redeemed once, so the provider refuses it — codes "
                              + "are single-use. A code that came back a second time is evidence that "
                              + "somebody copied it, which is why a good provider also revokes the "
                              + "tokens it issued for that code"
                            : "The code has expired. They are deliberately valid for about a minute: "
                              + "long enough for a redirect and one server call, far too short for "
                              + "somebody to find it in a log later",
                    grant.used
                            ? "Код уже был обменян, поэтому провайдер его отклоняет: коды "
                              + "одноразовые. Повторно пришедший код — свидетельство того, что его "
                              + "кто-то скопировал, и поэтому хороший провайдер заодно отзывает "
                              + "токены, выданные по этому коду"
                            : "Код просрочен. Их специально делают действительными около минуты: "
                              + "этого хватает на редирект и один серверный вызов и категорически не "
                              + "хватает, чтобы кто-то нашёл код в логах позже",
                    List.of("step:exchange", "code:" + grant.code), state());
            return null;
        }

        grant.used = true;
        Tokens tokens = issueTokens(grant.user, grant.scopes, true);
        if (!"breach".equals(decision)) {
            this.status = 200;
            this.decision = "allowed";
        }
        step("exchange", "breach".equals(decision) ? "breach" : "passed", tokens.accessToken);
        this.clientTokens = tokens;
        message("provider", "client", "back", "200 OK", tokenParams(tokens));
        Trace.event("TOKENS_ISSUED",
                "The provider checked the code, the redirect_uri, "
                        + (confidentialClient ? "the client's own credentials" : "the client id")
                        + (pkce ? " and that SHA-256(code_verifier) equals the code_challenge it "
                                + "recorded" : "")
                        + ", and returns the tokens. The ACCESS TOKEN is addressed to the API, not to "
                        + "the client — the client should treat it as an opaque string it merely "
                        + "carries. "
                        + (tokens.idToken != null
                            ? "The ID TOKEN is the opposite: it is addressed to this client, and it is "
                              + "the only thing here that says who the user is"
                            : "There is no id_token, so nothing in this response says who the user is "
                              + "— that is not what OAuth 2.0 is for"),
                "Провайдер проверил код, redirect_uri, "
                        + (confidentialClient ? "собственные учётные данные клиента" : "client_id")
                        + (pkce ? " и что SHA-256(code_verifier) равен записанному code_challenge" : "")
                        + " — и возвращает токены. ACCESS TOKEN адресован API, а не клиенту: клиент "
                        + "должен считать его непрозрачной строкой, которую он просто перевозит. "
                        + (tokens.idToken != null
                            ? "ID TOKEN — ровно наоборот: он адресован именно этому клиенту и "
                              + "является единственным здесь, что говорит, кто такой пользователь"
                            : "id_token здесь нет, поэтому ничто в этом ответе не сообщает, кто "
                              + "пользователь, — OAuth 2.0 не для этого"),
                List.of("channel:back", "party:client", "step:exchange",
                        "token:" + tokens.accessToken), state());
        return tokens;
    }

    /**
     * The client validates the id_token before believing a word of it. Without
     * these checks a signed document from anywhere is just JSON.
     */
    public void verifyIdToken(Tokens tokens) {
        if (tokens == null) {
            return;
        }
        message("client", "client", "none", "validate id_token", List.of());
        if (tokens.idToken == null) {
            this.status = null;
            this.decision = "risky";
            this.reason = "no-id-token";
            this.detail = tokens.accessToken;
            warnings++;
            Trace.event("ID_TOKEN_MISSING",
                    "There is no id_token to validate: this was plain OAuth 2.0, so the response is a "
                            + "key to an API and nothing else. Nothing in it names a user, nothing in "
                            + "it is addressed to this client, and nothing in it says an "
                            + "authentication happened at all. If you want to log the user in, ask for "
                            + "the 'openid' scope and use OIDC",
                    "Проверять нечего: это был обычный OAuth 2.0, поэтому в ответе — ключ к API и "
                            + "больше ничего. Ничто в нём не называет пользователя, ничто в нём не "
                            + "адресовано этому клиенту и ничто не утверждает, что аутентификация "
                            + "вообще произошла. Если нужно войти пользователем, запрашивайте scope "
                            + "«openid» и используйте OIDC",
                    List.of("party:client"), state());
            return;
        }
        this.identity = new Identity(tokens.subject, "id_token", true);
        this.status = 200;
        this.decision = "allowed";
        this.reason = null;
        this.detail = tokens.idToken;
        Trace.event("ID_TOKEN_VERIFIED",
                "The client validates the id_token before trusting one field of it: the SIGNATURE "
                        + "against the provider's published keys (JWKS), iss = " + PROVIDER
                        + ", aud = " + CLIENT_ID + " — this token was issued FOR this client, which is "
                        + "what stops a token obtained by another app being replayed here — exp not "
                        + "passed, and nonce equal to the one sent at /authorize. Only now does the "
                        + "client know it is talking to '" + tokens.subject + "'",
                "Клиент проверяет id_token, прежде чем поверить хоть одному его полю: ПОДПИСЬ по "
                        + "опубликованным ключам провайдера (JWKS), iss = " + PROVIDER + ", aud = "
                        + CLIENT_ID + " — этот токен выпущен ДЛЯ этого клиента, и именно это не даёт "
                        + "переиграть здесь токен, полученный другим приложением, — не истёкший exp "
                        + "и nonce, совпадающий с отправленным в /authorize. Только теперь клиент "
                        + "знает, что говорит с «" + tokens.subject + "»",
                List.of("party:client", "token:" + tokens.idToken), state());
    }

    /** The client calls the API with the access token — the only party the token is meant for. */
    public void callApi(String path, Tokens tokens, String requiredScope) {
        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"Authorization", tokens == null ? "(none)"
                : "Bearer " + tokens.accessToken});
        params.add(new String[]{"needs scope", requiredScope});
        message("client", "api", "back", "GET https://" + API + path, params);

        if (tokens == null) {
            step("call", "denied", "no token");
            refuse(401, "no-token", path);
            apiRefused++;
            Trace.event("API_CALL_REFUSED",
                    "No token, so the resource server has nothing to check and answers 401. It does "
                            + "not know this user, has no session with them and cannot ask them "
                            + "anything — an access token is the only way anyone gets in here",
                    "Токена нет, поэтому серверу ресурсов нечего проверять и он отвечает 401. Он не "
                            + "знает этого пользователя, у него нет с ним сессии и он не может ни о "
                            + "чём его спросить: access-токен — единственный способ сюда попасть",
                    List.of("party:api", "step:call"), state());
            return;
        }
        if (clock >= tokens.expiresAt) {
            step("call", "denied", "token expired");
            refuse(401, "expired", "t=" + tokens.expiresAt);
            apiRefused++;
            Trace.event("ACCESS_TOKEN_EXPIRED",
                    "The token was good until minute " + tokens.expiresAt + " and it is now minute "
                            + clock + ", so the API refuses it. Access tokens are kept short precisely "
                            + "because they are bearer tokens travelling to APIs: a stolen one stops "
                            + "working on its own, and the refresh token — which only ever moves on "
                            + "the back channel — is what keeps the user signed in",
                    "Токен был действителен до минуты " + tokens.expiresAt + ", а сейчас минута "
                            + clock + ", поэтому API его отклоняет. Access-токены держат "
                            + "короткоживущими именно потому, что это предъявительские токены, "
                            + "которые ездят к API: украденный перестаёт работать сам, а "
                            + "refresh-токен, который ходит только по бэк-каналу, и удерживает "
                            + "пользователя в системе",
                    List.of("party:api", "step:call", "token:" + tokens.accessToken), state());
            return;
        }
        if (!tokens.scopes.contains(requiredScope)) {
            step("call", "denied", "scope " + requiredScope + " not granted");
            refuse(403, "insufficient-scope", requiredScope);
            apiRefused++;
            Trace.event("SCOPE_INSUFFICIENT",
                    "The token is perfectly valid and the answer is still 403: it carries "
                            + String.join(" ", tokens.scopes) + " and this call needs " + requiredScope
                            + ". 401 means 'I do not know who you are', 403 means 'I do, and this is "
                            + "not allowed'. Note that the resource server enforces this itself — a "
                            + "valid token is never the same thing as permission for this call",
                    "Токен совершенно корректен, а ответ всё равно 403: в нём "
                            + String.join(" ", tokens.scopes) + ", а этому вызову нужен "
                            + requiredScope + ". 401 означает «я не знаю, кто ты», 403 — «знаю, и "
                            + "этого нельзя». Обратите внимание: проверяет это сам сервер ресурсов — "
                            + "корректный токен никогда не равен разрешению на конкретный вызов",
                    List.of("party:api", "step:call", "token:" + tokens.accessToken), state());
            return;
        }

        step("call", "breach".equals(decision) ? "breach" : "passed", path);
        boolean stolen = "breach".equals(decision);
        this.status = 200;
        if (!stolen) {
            this.decision = "allowed";
            this.reason = null;
            this.detail = path;
        }
        apiServed++;
        Trace.event("API_CALL_SERVED",
                "The resource server verifies the token's signature, that aud names this API, that it "
                        + "has not expired, and that its scopes cover this call — then serves it as "
                        + (tokens.subject == null ? "the app itself" : "'" + tokens.subject + "'")
                        + ". It never saw a password, never redirected anybody, and usually never "
                        + "calls the provider at all: everything it needs is in the token plus the "
                        + "provider's public keys",
                "Сервер ресурсов проверяет подпись токена, что aud указывает на этот API, что срок "
                        + "не истёк и что scope покрывают этот вызов, — и обслуживает запрос как "
                        + (tokens.subject == null ? "само приложение" : "«" + tokens.subject + "»")
                        + ". Он никогда не видел пароля, никого не перенаправлял и обычно вообще не "
                        + "обращается к провайдеру: всё нужное есть в самом токене и в публичных "
                        + "ключах провайдера",
                List.of("party:api", "step:call", "token:" + tokens.accessToken), state());
    }

    /** Swaps the refresh token for a fresh access token, without sending the user anywhere. */
    public Tokens refresh(Tokens tokens) {
        if (tokens == null) {
            return null;
        }
        message("client", "provider", "back", "POST https://" + PROVIDER + "/token",
                List.of(new String[]{"grant_type", "refresh_token"},
                        new String[]{"refresh_token", tokens.refreshToken == null ? "(none)"
                                : tokens.refreshToken}));
        if (tokens.refreshToken == null || clock >= tokens.refreshExpiresAt) {
            refuse(400, tokens.refreshToken == null ? "no-refresh-token" : "refresh-expired",
                    tokens.accessToken);
            Trace.event("REFRESH_UNAVAILABLE",
                    tokens.refreshToken == null
                            ? "There is no refresh token to use. Not every grant gets one: a "
                              + "machine-to-machine client simply asks for another access token with "
                              + "its own credentials, and the browser flows that used to run without a "
                              + "back channel were never given one either"
                            : "The refresh window itself has closed, so the client cannot renew "
                              + "silently any more and the user has to go through the provider again. "
                              + "That window, not the short access token lifetime, is the real 'stay "
                              + "signed in' setting",
                    tokens.refreshToken == null
                            ? "Refresh-токена здесь нет. Он выдаётся не всегда: клиент «машина — "
                              + "машине» просто запрашивает новый access-токен по своим учётным "
                              + "данным, а браузерным потокам, работавшим без бэк-канала, его тоже "
                              + "никогда не выдавали"
                            : "Само окно обновления закрылось, поэтому молча продлить клиент больше "
                              + "не может и пользователю придётся снова пройти через провайдера. "
                              + "Именно это окно, а не короткий срок жизни access-токена, и есть "
                              + "настоящая настройка «оставаться в системе»",
                    List.of("party:client", "step:exchange"), state());
            return null;
        }
        Tokens fresh = issueTokens(tokens.subject, tokens.scopes, tokens.idToken != null);
        this.clientTokens = fresh;
        this.status = 200;
        this.decision = "allowed";
        this.reason = null;
        this.detail = fresh.accessToken;
        step("exchange", "passed", fresh.accessToken);
        message("provider", "client", "back", "200 OK", tokenParams(fresh));
        Trace.event("TOKENS_REFRESHED",
                "A fresh access token " + fresh.accessToken + " arrives, valid until minute "
                        + fresh.expiresAt + ", with no redirect, no consent screen and no user "
                        + "involvement at all. This is how a 15-minute access token still means a user "
                        + "who stays signed in for a working day — and why the refresh token is the "
                        + "one that must never touch the front channel",
                "Приходит свежий access-токен " + fresh.accessToken + ", действительный до минуты "
                        + fresh.expiresAt + ", — без редиректа, без экрана согласия и вообще без "
                        + "участия пользователя. Так пятнадцатиминутный access-токен всё же означает "
                        + "пользователя, который остаётся в системе весь рабочий день, — и именно "
                        + "поэтому refresh-токен ни при каких условиях не должен попадать на "
                        + "фронт-канал",
                List.of("channel:back", "party:client", "token:" + fresh.accessToken), state());
        return fresh;
    }

    /** Moves the clock forward, so codes and tokens can reach their expiry. */
    public void advanceMinutes(int minutes) {
        clock += minutes;
        message("", "", "none", minutes + " min pass", List.of());
        this.status = null;
        this.decision = "idle";
        this.reason = null;
        this.detail = "";
        Trace.event("TIME_PASSED",
                minutes + " minute(s) pass; the clock is now at minute " + clock
                        + ". Nobody called anything, but every artifact in this protocol carries an "
                        + "expiry — a code about a minute, an access token minutes, a refresh token "
                        + "hours or days — and time alone is enough to make each of them worthless",
                "Проходит минут: " + minutes + "; на часах теперь минута " + clock
                        + ". Никто ничего не вызывал, но у каждого артефакта этого протокола есть "
                        + "срок: у кода — около минуты, у access-токена — минуты, у refresh-токена — "
                        + "часы или дни, и одного лишь времени достаточно, чтобы любой из них стал "
                        + "бесполезен",
                List.of("clock"), state());
    }

    // ------------------------------------------------------------- what breaks

    /** Somebody copies the authorization code out of the front channel. */
    public Grant stealTheCode(Grant grant) {
        if (grant == null) {
            return null;
        }
        attackerPresent = true;
        attackerCode = grant.code;
        message("client", "attacker", "front", "copy " + grant.code + " from the redirect",
                List.of(new String[]{"code", grant.code},
                        new String[]{"seen in", "URL, history, referer, logs"}));
        this.status = null;
        this.decision = "risky";
        this.reason = "code-stolen";
        this.detail = grant.code;
        warnings++;
        Trace.event("AUTHORIZATION_CODE_STOLEN",
                "The code travelled through the browser, so it can be read: from the URL bar or "
                        + "history, from a Referer header, from a proxy or server log, or by another "
                        + "app on the phone that registered the same custom URL scheme. This is not "
                        + "exotic — it is the normal risk of the front channel, and the protocol "
                        + "assumes it will happen",
                "Код ехал через браузер, а значит, его можно прочитать: из адресной строки или "
                        + "истории, из заголовка Referer, из лога прокси или сервера, либо другим "
                        + "приложением на телефоне, зарегистрировавшим ту же кастомную URL-схему. Это "
                        + "не экзотика, а обычный риск фронт-канала, и протокол исходит из того, что "
                        + "так и будет",
                List.of("party:attacker", "code:" + grant.code, "channel:front"), state());
        return new Grant(grant.code, grant.state, grant.user, grant.scopes, grant.challenge,
                grant.expiresAt, false, "attacker");
    }

    /** The thief tries to turn the stolen code into tokens. */
    public Tokens redeemStolenCode(Grant stolen) {
        if (stolen == null) {
            return null;
        }
        attackerPresent = true;
        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"grant_type", "authorization_code"});
        params.add(new String[]{"code", stolen.code});
        params.add(new String[]{"client_id", CLIENT_ID});
        params.add(new String[]{"code_verifier", pkce ? "(unknown)" : "(not required)"});
        message("attacker", "provider", "back", "POST https://" + PROVIDER + "/token", params);

        if (pkce) {
            step("exchange", "denied", "no code_verifier");
            refuse(400, "pkce-blocked", stolen.code);
            Trace.event("STOLEN_CODE_BLOCKED",
                    "The provider demands the code_verifier whose SHA-256 equals the challenge it "
                            + "recorded at /authorize. The thief has the CHALLENGE — it was in the URL "
                            + "— but a hash cannot be run backwards, and the verifier itself never "
                            + "left the client. That is the entire idea of PKCE: bind the code to the "
                            + "thing that requested it, using a secret that never touches the front "
                            + "channel",
                    "Провайдер требует code_verifier, SHA-256 которого равен записанному при "
                            + "/authorize challenge. У вора есть CHALLENGE — он был в URL, — но хеш "
                            + "нельзя обратить, а сам verifier никогда не покидал клиента. В этом и "
                            + "весь смысл PKCE: привязать код к тому, кто его запросил, секретом, "
                            + "который ни разу не появляется на фронт-канале",
                    List.of("party:attacker", "party:provider", "step:exchange"), state());
            return null;
        }
        if (confidentialClient) {
            step("exchange", "denied", "no client_secret");
            refuse(401, "client-auth-blocked", stolen.code);
            Trace.event("STOLEN_CODE_BLOCKED",
                    "The token endpoint also authenticates the CLIENT, and the thief has no "
                            + "client_secret, so the code is refused. That works only because this "
                            + "client is a confidential one with a server to keep the secret on — a "
                            + "SPA or a mobile app has nowhere to hide it, which is exactly the gap "
                            + "PKCE was invented to close, and why PKCE is now recommended for every "
                            + "client",
                    "Эндпоинт токенов заодно аутентифицирует и КЛИЕНТА, а client_secret у вора нет, "
                            + "поэтому код отклоняется. Работает это только потому, что клиент "
                            + "конфиденциальный и у него есть сервер, где секрет можно держать: у "
                            + "SPA или мобильного приложения спрятать его негде — ровно эту дыру и "
                            + "закрывали PKCE, и поэтому PKCE сегодня рекомендуют всем клиентам",
                    List.of("party:attacker", "party:provider", "step:exchange"), state());
            return null;
        }

        step("exchange", "breach", stolen.code);
        stolen.used = true;
        Tokens tokens = issueTokens(stolen.user, stolen.scopes, "oidc".equals(protocol));
        this.attackerTokens = tokens;
        this.status = 200;
        this.decision = "breach";
        this.reason = "stolen-code-redeemed";
        this.detail = stolen.code;
        breaches++;
        message("provider", "attacker", "back", "200 OK", tokenParams(tokens));
        Trace.event("STOLEN_CODE_REDEEMED",
                "A public client with no PKCE: the code is the only secret, and the thief has it. "
                        + "The provider cannot tell the difference — the request is well-formed, the "
                        + "client_id is right, and nothing else was ever required. The attacker now "
                        + "holds " + tokens.accessToken + " for '" + stolen.user + "' with the scopes "
                        + "that user consented to, and the user was never asked",
                "Публичный клиент без PKCE: единственный секрет — это код, и он у вора. Провайдер "
                        + "не может отличить одного от другого — запрос корректен, client_id верен, а "
                        + "больше ничего никогда и не требовалось. Теперь у злоумышленника есть "
                        + tokens.accessToken + " от имени «" + stolen.user + "» с теми scope, на "
                        + "которые пользователь дал согласие, и самого пользователя никто не "
                        + "спрашивал",
                List.of("party:attacker", "step:exchange", "token:" + tokens.accessToken), state());
        return tokens;
    }

    /**
     * The attacker completes their own authorization at the provider and gets the
     * victim's browser to deliver the resulting code to the client's callback.
     */
    public Grant injectedCode() {
        attackerPresent = true;
        codeCounter++;
        String code = "ac" + codeCounter;
        attackerCode = code;
        message("attacker", "user", "front", "victim opens " + REDIRECT_URI,
                List.of(new String[]{"code", code}, new String[]{"belongs to", "mallory"}));
        this.status = null;
        this.decision = "risky";
        this.reason = "code-injected";
        this.detail = code;
        warnings++;
        Trace.event("CODE_INJECTION_ATTEMPT",
                "The attacker logs in at the provider AS THEMSELVES, stops before the last step and "
                        + "keeps their own authorization code. Then they make the victim's browser "
                        + "open the client's callback URL carrying that code — a link in an email is "
                        + "enough. Nothing is stolen here; the attacker is pushing their own identity "
                        + "into somebody else's session",
                "Злоумышленник входит у провайдера ПОД СОБОЙ, останавливается перед последним шагом "
                        + "и оставляет себе собственный код авторизации. Затем он заставляет браузер "
                        + "жертвы открыть callback-URL клиента с этим кодом — достаточно ссылки в "
                        + "письме. Здесь ничего не крадут: злоумышленник проталкивает собственную "
                        + "личность в чужую сессию",
                List.of("party:attacker", "channel:front", "code:" + code), state());
        return new Grant(code, ATTACKER_STATE, "mallory", List.copyOf(requestedScopes),
                pkce ? CHALLENGE : null, clock + CODE_LIFETIME_MINUTES, true, "client");
    }

    /** The classic confusion: treating an access token as evidence of who the user is. */
    public void useAccessTokenAsLogin(Tokens tokens) {
        if (tokens == null) {
            return;
        }
        message("client", "client", "none", "log the user in from the access token",
                List.of(new String[]{"access_token", tokens.accessToken},
                        new String[]{"assumed user", tokens.subject()}));
        this.identity = new Identity(tokens.subject(), "access_token", false);
        this.status = 200;
        this.decision = "breach";
        this.reason = "token-not-identity";
        this.detail = tokens.accessToken;
        breaches++;
        Trace.event("ACCESS_TOKEN_MISUSED_AS_IDENTITY",
                "The client says 'a token came back, so this must be " + tokens.subject()
                        + "' and signs the user in. That is the most common OAuth bug there is. The "
                        + "access token is not addressed to the client, carries no audience it can "
                        + "check, and proves only that SOMEBODY authorized SOMETHING — so a malicious "
                        + "app can take a token a user gave it and present it here to be signed in as "
                        + "that user. The id_token exists precisely because this does not work",
                "Клиент рассуждает так: «токен пришёл, значит, это " + tokens.subject()
                        + "» — и пускает пользователя внутрь. Это самая распространённая ошибка в "
                        + "OAuth. Access-токен адресован не клиенту, не содержит аудитории, которую "
                        + "клиент мог бы проверить, и доказывает лишь то, что КТО-ТО что-то "
                        + "разрешил: поэтому вредоносное приложение может взять токен, выданный ему "
                        + "пользователем, предъявить его здесь и войти этим пользователем. id_token "
                        + "существует ровно потому, что так делать нельзя",
                List.of("party:client", "token:" + tokens.accessToken), state());
    }

    /** The grant OAuth was invented to remove: the user hands their password to the app. */
    public Tokens passwordGrant(String user, String password) {
        this.userName = user;
        providerAccounts.add(user);
        resetSteps();
        step("authorize", "skipped", "no redirect");
        step("authenticate", "breach", "password typed into the client");
        step("consent", "skipped", "nobody asked");
        step("code", "skipped", "no code");
        requestedScopes.clear();
        grantedScopes.clear();
        grantedScopes.add("photos.read");
        requestedScopes.add("photos.read");
        message("user", "client", "none", "types the password INTO THE APP",
                List.of(new String[]{"username", user}, new String[]{"password", password}));
        Tokens tokens = issueTokens(user, List.of("photos.read"), false);
        this.clientTokens = tokens;
        step("exchange", "passed", tokens.accessToken);
        this.status = 200;
        this.decision = "risky";
        this.reason = "password-seen-by-client";
        this.detail = user;
        warnings++;
        Trace.event("PASSWORD_GRANT_USED",
                "The resource owner password credentials grant: the user types their provider "
                        + "password into the CLIENT, which forwards it to the token endpoint. Every "
                        + "redirect disappears and so does the entire point of OAuth — the app now "
                        + "holds a password that works everywhere at that provider, MFA and SSO cannot "
                        + "run because the provider never sees the user, and there is no consent "
                        + "screen. It is removed in OAuth 2.1",
                "Grant с паролем владельца ресурса: пользователь вводит пароль от провайдера в "
                        + "КЛИЕНТ, а тот пересылает его на эндпоинт токенов. Исчезают все редиректы, "
                        + "а вместе с ними и весь смысл OAuth: теперь у приложения есть пароль, "
                        + "который работает у этого провайдера везде, MFA и SSO отработать не могут, "
                        + "потому что провайдер вообще не видит пользователя, и экрана согласия нет. "
                        + "В OAuth 2.1 этот grant удалён",
                List.of("party:client", "party:user", "step:authenticate"), state());
        return tokens;
    }

    /** Service to service: the app acts as itself, and there is no user anywhere. */
    public Tokens clientCredentials(String... scopes) {
        this.userName = "";
        this.identity = null;
        resetSteps();
        step("authorize", "skipped", "no browser");
        step("authenticate", "skipped", "no user");
        step("consent", "skipped", "nobody to ask");
        step("code", "skipped", "no code");
        requestedScopes.clear();
        grantedScopes.clear();
        for (String scope : scopes) {
            requestedScopes.add(scope);
            grantedScopes.add(scope);
        }
        message("client", "provider", "back", "POST https://" + PROVIDER + "/token",
                List.of(new String[]{"grant_type", "client_credentials"},
                        new String[]{"client_id", CLIENT_ID},
                        new String[]{"client_secret", CLIENT_SECRET},
                        new String[]{"scope", String.join(" ", grantedScopes)}));
        Tokens tokens = new Tokens("at" + (++tokenCounter), null, null, null,
                List.copyOf(grantedScopes));
        tokens.expiresAt = clock + ACCESS_LIFETIME_MINUTES;
        tokens.refreshExpiresAt = clock;
        this.clientTokens = tokens;
        tokensIssued++;
        step("exchange", "passed", tokens.accessToken);
        this.status = 200;
        this.decision = "allowed";
        this.reason = null;
        this.detail = tokens.accessToken;
        Trace.event("CLIENT_CREDENTIALS_ISSUED",
                "The client_credentials grant: one back-channel call, authenticated with the client's "
                        + "own secret, and a token comes back. There is no user, no browser, no "
                        + "redirect, no consent and no id_token — the app is not acting on anybody's "
                        + "behalf, it is acting as ITSELF. This is the grant for a batch job or one "
                        + "service calling another, and the wrong one for anything a person is doing",
                "Grant client_credentials: один вызов по бэк-каналу, аутентифицированный "
                        + "собственным секретом клиента, — и токен получен. Нет пользователя, нет "
                        + "браузера, нет редиректа, нет согласия и нет id_token: приложение действует "
                        + "не от чьего-то имени, а ОТ СЕБЯ. Это grant для фоновой задачи или вызова "
                        + "одного сервиса другим и совершенно не тот grant для чего-либо, что делает "
                        + "человек",
                List.of("channel:back", "party:client", "token:" + tokens.accessToken), state());
        return tokens;
    }

    /** The old browser flow that skipped the back channel, and put the token in a URL. */
    public Tokens implicitFlow(String user, String... scopes) {
        this.userName = user;
        providerAccounts.add(user);
        this.userAuthenticatedAtProvider = true;
        resetSteps();
        requestedScopes.clear();
        grantedScopes.clear();
        for (String scope : scopes) {
            requestedScopes.add(scope);
            grantedScopes.add(scope);
        }
        step("authorize", "passed", "response_type=token");
        step("authenticate", "passed", user);
        step("consent", "passed", String.join(" ", grantedScopes));
        step("code", "skipped", "no code at all");
        Tokens tokens = new Tokens("at" + (++tokenCounter), null, null, user,
                List.copyOf(grantedScopes));
        tokens.expiresAt = clock + ACCESS_LIFETIME_MINUTES;
        tokens.refreshExpiresAt = clock;
        this.clientTokens = tokens;
        tokensIssued++;
        step("exchange", "breach", "token arrived in the URL");
        message("provider", "client", "front", "302 -> " + REDIRECT_URI + "#access_token="
                + tokens.accessToken, List.of(new String[]{"access_token", tokens.accessToken},
                new String[]{"token_type", "Bearer"}, new String[]{"expires_in", "900"}));
        this.status = 302;
        this.decision = "risky";
        this.reason = "token-in-url";
        this.detail = tokens.accessToken;
        warnings++;
        Trace.event("IMPLICIT_TOKEN_IN_URL",
                "The implicit flow: no code and no back channel, so the ACCESS TOKEN itself comes "
                        + "back in the URL fragment. It lands in browser history, in extensions, in "
                        + "anything reading the address bar, and one bad redirect leaks it outright. "
                        + "There is also no way to authenticate the client and no refresh token. It "
                        + "existed because browsers could not make cross-origin calls; they can now, "
                        + "so the answer everywhere is authorization code + PKCE",
                "Implicit flow: нет ни кода, ни бэк-канала, поэтому в URL-фрагменте возвращается "
                        + "сам ACCESS-ТОКЕН. Он оседает в истории браузера, в расширениях, во всём, "
                        + "что читает адресную строку, и один неудачный редирект утекает его целиком. "
                        + "Ещё здесь невозможно аутентифицировать клиента и нет refresh-токена. Этот "
                        + "поток существовал потому, что браузеры не умели кросс-доменных запросов; "
                        + "теперь умеют, и правильный ответ везде — authorization code + PKCE",
                List.of("channel:front", "party:client", "token:" + tokens.accessToken), state());
        return tokens;
    }

    /** Prints what the whole run added up to. */
    public void report() {
        message("", "", "none", "audit", List.of());
        this.status = null;
        this.decision = "idle";
        this.reason = null;
        this.detail = "";
        Trace.event("OAUTH_AUDIT",
                "After the run: tokens issued " + tokensIssued + ", API calls served " + apiServed
                        + ", API calls refused " + apiRefused + ", risky moves " + warnings
                        + ", things that worked and should NOT have " + breaches
                        + ". The shape to remember: the user authenticates at the provider, the client "
                        + "gets a code on the front channel, swaps it for tokens on the back channel, "
                        + "reads WHO from the id_token and sends the access token to the API",
                "Итоги прогона: выдано токенов " + tokensIssued + ", обслужено вызовов API "
                        + apiServed + ", отклонено вызовов API " + apiRefused + ", рискованных "
                        + "ходов " + warnings + ", сработало то, что сработать НЕ должно было: "
                        + breaches + ". Форма, которую стоит запомнить: пользователь "
                        + "аутентифицируется у провайдера, клиент получает код по фронт-каналу, "
                        + "меняет его на токены по бэк-каналу, узнаёт КТО из id_token и отправляет "
                        + "access-токен в API",
                List.of(), state());
    }

    // --------------------------------------------------------------- internals

    private Tokens issueTokens(String subject, List<String> scopes, boolean withIdToken) {
        tokenCounter++;
        String access = "at" + tokenCounter;
        String refresh = "rt" + tokenCounter;
        String id = withIdToken && "oidc".equals(protocol) ? "it" + tokenCounter : null;
        Tokens tokens = new Tokens(access, refresh, id, subject, List.copyOf(scopes));
        tokens.expiresAt = clock + ACCESS_LIFETIME_MINUTES;
        tokens.refreshExpiresAt = clock + REFRESH_LIFETIME_MINUTES;
        tokensIssued++;
        return tokens;
    }

    private List<String[]> tokenParams(Tokens tokens) {
        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"access_token", tokens.accessToken});
        params.add(new String[]{"token_type", "Bearer"});
        params.add(new String[]{"expires_in", String.valueOf(ACCESS_LIFETIME_MINUTES * 60)});
        params.add(new String[]{"scope", String.join(" ", tokens.scopes)});
        if (tokens.refreshToken != null) {
            params.add(new String[]{"refresh_token", tokens.refreshToken});
        }
        if (tokens.idToken != null) {
            params.add(new String[]{"id_token", tokens.idToken});
        }
        return params;
    }

    private void message(String from, String to, String channel, String label,
                         List<String[]> params) {
        this.messageFrom = from;
        this.messageTo = to;
        this.messageChannel = channel;
        this.messageLabel = label;
        this.messageParams.clear();
        this.messageParams.addAll(params);
    }

    private void refuse(int status, String reason, String detail) {
        this.status = status;
        this.decision = "denied";
        this.reason = reason;
        this.detail = detail;
    }

    private void resetSteps() {
        steps.clear();
        for (String id : STEPS) {
            steps.put(id, new String[]{"pending", ""});
        }
    }

    private void step(String id, String status, String detail) {
        steps.put(id, new String[]{status, detail});
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("protocol", protocol);
        s.put("clientType", confidentialClient ? "confidential" : "public");
        s.put("pkce", pkce);
        s.put("stateCheck", stateCheck);
        s.put("clock", clock);
        s.put("accessLifetime", ACCESS_LIFETIME_MINUTES);

        List<Object> parties = new ArrayList<>();
        parties.add(party("user", "resource-owner",
                userName.isEmpty() ? "no user in this flow" : userName + " (browser)",
                userHoldings()));
        parties.add(party("client", "client", CLIENT_ID, clientHoldings()));
        parties.add(party("provider", "authorization-server", PROVIDER, providerHoldings()));
        parties.add(party("api", "resource-server", API, apiHoldings()));
        if (attackerPresent) {
            parties.add(party("attacker", "attacker", "mallory", attackerHoldings()));
        }
        s.put("parties", parties);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("from", messageFrom);
        message.put("to", messageTo);
        message.put("channel", messageChannel);
        message.put("label", messageLabel);
        List<Object> params = new ArrayList<>();
        for (String[] param : messageParams) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", param[0]);
            p.put("value", param[1]);
            params.add(p);
        }
        message.put("params", params);
        s.put("message", message);

        List<Object> stepList = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : steps.entrySet()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", entry.getKey());
            step.put("status", entry.getValue()[0]);
            step.put("detail", entry.getValue()[1]);
            stepList.add(step);
        }
        s.put("steps", stepList);

        List<Object> scopeList = new ArrayList<>();
        for (String scope : requestedScopes) {
            Map<String, Object> sc = new LinkedHashMap<>();
            sc.put("name", scope);
            sc.put("granted", grantedScopes.contains(scope));
            scopeList.add(sc);
        }
        s.put("scopes", scopeList);

        if (identity != null) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("subject", identity.subject);
            i.put("source", identity.source);
            i.put("verified", identity.verified);
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
        stats.put("tokensIssued", tokensIssued);
        stats.put("apiServed", apiServed);
        stats.put("apiRefused", apiRefused);
        stats.put("warnings", warnings);
        stats.put("breaches", breaches);
        s.put("stats", stats);
        return s;
    }

    private Map<String, Object> party(String id, String role, String label, List<Object> holdings) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("role", role);
        p.put("label", label);
        p.put("holdings", holdings);
        return p;
    }

    private List<Object> userHoldings() {
        List<Object> holdings = new ArrayList<>();
        if (!userName.isEmpty()) {
            holdings.add(holding("password", "typed at " + PROVIDER, "secret"));
        }
        if (userAuthenticatedAtProvider) {
            holdings.add(holding("provider session", "signed in", "identity"));
        }
        return holdings;
    }

    private List<Object> clientHoldings() {
        List<Object> holdings = new ArrayList<>();
        holdings.add(holding("client_id", CLIENT_ID, "param"));
        if (confidentialClient) {
            holdings.add(holding("client_secret", CLIENT_SECRET, "secret"));
        }
        if (pendingState != null) {
            holdings.add(holding("state", pendingState, "param"));
        }
        if (clientVerifier != null) {
            holdings.add(holding("code_verifier", clientVerifier, "secret"));
        }
        if (clientCode != null) {
            holdings.add(holding("code", clientCode, "code"));
        }
        if (clientTokens != null) {
            holdings.add(holding("access_token", clientTokens.accessToken, "token"));
            if (clientTokens.refreshToken != null) {
                holdings.add(holding("refresh_token", clientTokens.refreshToken, "token"));
            }
            if (clientTokens.idToken != null) {
                holdings.add(holding("id_token", clientTokens.idToken, "token"));
            }
        }
        return holdings;
    }

    private List<Object> providerHoldings() {
        List<Object> holdings = new ArrayList<>();
        holdings.add(holding("accounts", providerAccounts.isEmpty() ? "-"
                : String.join(", ", providerAccounts), "identity"));
        holdings.add(holding("signing key", "K-private", "key"));
        if (pkce && clientCode != null) {
            holdings.add(holding("code_challenge", CHALLENGE, "param"));
        }
        return holdings;
    }

    private List<Object> apiHoldings() {
        List<Object> holdings = new ArrayList<>();
        holdings.add(holding("verify key", "K-public", "key"));
        holdings.add(holding("passwords", "never sees any", "secret"));
        return holdings;
    }

    private List<Object> attackerHoldings() {
        List<Object> holdings = new ArrayList<>();
        if (attackerCode != null) {
            holdings.add(holding("code", attackerCode, "code"));
        }
        if (pkce) {
            holdings.add(holding("code_verifier", "unknown", "secret"));
        }
        if (attackerTokens != null) {
            holdings.add(holding("access_token", attackerTokens.accessToken, "token"));
        }
        return holdings;
    }

    private Map<String, Object> holding(String name, String value, String kind) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("name", name);
        h.put("value", value);
        h.put("kind", kind);
        return h;
    }
}
