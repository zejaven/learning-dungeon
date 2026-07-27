# JWT vs session token

Both answer the same question — *"who is calling?"* — on every request, because HTTP
has no memory. The difference is not age or fashion. It is **where the truth lives**:
inside the value the client carries, or on your server behind a meaningless id.

Everything else in this topic follows from that one sentence.

## 1. What a JWT actually is

A JSON Web Token is **three base64url segments joined by dots**:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhbGljZSIsInJvbGUiOiJhZG1pbiIsImV4cCI6MTczMH0.K7f3a91c8e2b4d05

header     eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9                       {"alg":"HS256","typ":"JWT"}
payload    eyJzdWIiOiJhbGljZSIsInJvbGUiOiJhZG1pbiIsImV4cCI6MTczMH0    {"sub":"alice","role":"admin","exp":1730}
signature  K7f3a91c8e2b4d05                                           HMAC-SHA256(header + "." + payload, key)
```

- **header** — `{"alg":"HS256","typ":"JWT"}`: which algorithm signed this.
- **payload** — the *claims*: `sub` (who), `iss` (who issued it), `aud` (who it is
  for), `exp` / `iat` (until when, from when), `jti` (its id), plus whatever you add
  (`role`, `tenant`, …).
- **signature** — the issuer's MAC or digital signature over `header.payload`.

```mermaid
flowchart LR
  H["header: alg, typ"] --> SI["header.payload"]
  P["payload: sub, iss, aud, exp, role"] --> SI
  SI --> SG["sign with the key"]
  SG --> T["header.payload.signature"]
  T --> V["any holder of the key verifies it alone"]
```

The first two segments are **base64url, not encryption**. Anyone who holds the token
— the user, a proxy, a log aggregator, a thief — reads every claim with no key and no
permission. So: never put a secret in a payload, and "the client cannot see this
field" is never true of a JWT.

## 2. Signed, not secret

Editing a claim is trivial — it is text. Producing a **signature** for the edited text
is not, because that needs the key. That asymmetry is the whole security model: a
claim is not evidence until the signature over exactly those bytes has been
recomputed and matched.

Which is why "decode the token" and "verify the token" are completely different verbs,
and why these are real attacks:

- **`alg: none`.** The attacker rewrites the header to `{"alg":"none"}`, sets
  `role: admin`, and deletes the signature. A verifier that reads the algorithm *out
  of the token it is checking* concludes there is nothing to check. **Pin the
  algorithm server-side**; the token does not get a vote.
- **Algorithm confusion.** A token signed with RS256 is re-sent as HS256, and a naive
  library uses the RSA *public* key — which is public — as the HMAC shared secret.
  Same root cause: the value being validated influenced how it was validated.
- **No verification at all.** `jwt.decode()` instead of `jwt.verify()` is a
  surprisingly common production bug. Decoding is free for everybody.

## 3. What "verify" actually means

A verifier that only checks the signature is half-finished. The full list:

1. **Signature** — with the algorithm and key *you* chose, compared in constant time.
2. **`exp`** (and `nbf`) — is it still inside its lifetime, with a little clock skew.
3. **`iss`** — did *your* issuer mint it, not some other system whose key you also
   happen to trust.
4. **`aud`** — was it issued **for you**? A valid signature means "auth issued this",
   never "issued for you". Without this check, the low-trust service you hand your
   token to can replay it against payments.
5. **Then authorization** — the claims tell you *who*; whether they may do the thing
   is a separate question, covered in
   [designing a security scheme for your endpoints](topic:endpoint-security-design).

## 4. The other option: an opaque session id

A session id is a long random string that **means nothing**. There is no header, no
payload, nothing to decode — everything it stands for is a row in your store, and
every request spends one lookup turning the id back into a user.

```mermaid
sequenceDiagram
  participant C as Client
  participant S as Service
  participant D as Session store
  C->>S: GET /orders + session id
  S->>D: who is sid_9f2c1a?
  D-->>S: alice, role read fresh from the DB
  S-->>C: 200 — decided with today's truth
  Note over C,S: the other scheme
  C->>S: GET /orders + JWT
  S->>S: recompute the signature, check exp/iss/aud
  S-->>C: 200 — decided with facts frozen at issue time
```

That lookup is not overhead you failed to optimise away. **It is the moment the server
gets to change its mind**, and it is what you are buying.

## 5. The one real difference

|  | Session id | JWT |
|---|---|---|
| The client holds | a meaningless id (~20 bytes) | the claims themselves, signed (~200+ bytes) |
| The server holds | a record per live session | nothing |
| Each request costs | one store lookup | one signature check, zero lookups |
| Who can verify it | anything that can reach the store | anything holding the key |
| Logging out | `DELETE` — effective on the next request | nothing to delete |
| Ban a user *now* | next request | not until `exp` |
| Roles changed | next request sees it | the old role until `exp` |
| If it leaks | an id; the thief learns nothing else | who, what role, where it is accepted |

Read the last three rows together. **A JWT is a snapshot of facts that were true when
it was signed**, and every claim in it is stale *by design* until it expires. That is
not a bug you can patch; it is the direct consequence of not doing a lookup.

The workarounds all put some state back:

- **Short `exp`** (5–15 minutes) plus a long-lived **refresh token** that *is* stored
  and revocable. The access token stays stateless; the damage window is bounded.
- **A deny-list** of revoked `jti`s, consulted on every request. Real revocation — and
  a lookup on every request, which was the thing you were avoiding. It is at least
  *small*: only revoked ids, only until their `exp`.

"Stateless JWTs with instant revocation" does not exist. Pick which half you want.

## 6. Client–server: a browser and your own backend

```mermaid
flowchart TB
  Q["who is calling?"] --> B{"a browser on your own domain?"}
  B -->|"yes"| S["session id in an HttpOnly cookie"]
  B -->|"no"| M{"your own service, or a third party?"}
  M -->|"your own service"| J["short-lived JWT with aud, or mTLS"]
  M -->|"third party"| O["OAuth 2.0 / OIDC — the token comes from the provider"]
  S --> R{"must revocation be immediate?"}
  J --> R
  R -->|"yes"| K["keep a lookup: a session, or a JWT deny-list"]
  R -->|"no"| T["a short exp is enough"]
```

For a first-party browser app, **the session cookie is the better default**, and the
reasons are concrete rather than traditional:

- **Revocation is the common case here.** Users log out, on shared laptops. Support
  bans accounts. Admins change roles and expect it to take effect. All of that is free
  with a lookup and awkward without one.
- **`HttpOnly` is a real defence.** A cookie your JavaScript cannot read cannot be
  stolen by an [XSS](topic:xss); a JWT in `localStorage` can, and then it is a
  15-minute skeleton key the attacker walks away with.
- **One backend means the lookup is cheap.** It is a primary-key read from Redis or a
  table, on the same network. "Sessions don't scale" is usually an argument about a
  problem the system does not have — see
  [scaling an overloaded server](topic:scaling-an-overloaded-server).
- **The token rides on every request.** 200–800 bytes on every call, including the
  ones fetching a 2 KB JSON response.

The cost of the cookie is that the browser attaches it automatically, which is exactly
what [CSRF](topic:csrf) abuses — so a cookie session needs `SameSite` and/or a
synchronizer token. Note the symmetry that trips people up: **a JWT does not remove
CSRF risk if you store it in a cookie**; it removes it only when your own JavaScript
attaches it as an `Authorization` header, and that is precisely the choice that
exposes it to XSS. `HttpOnly` cookie plus CSRF protection covers most of both. Which
side attaches what is worth stating explicitly — see
[how the frontend and the backend talk](topic:frontend-backend-interaction).

## 7. Server–server: where JWTs earn their keep

Between services the picture inverts, because the awkward parts stop applying:

- **There is no logout.** A service does not press a button; a token that lives 60
  seconds is revoked by expiry faster than any deny-list would propagate.
- **There is no shared session store worth building.** Ten services hitting one
  session database on the path of every request is a coupling *and* a single point of
  failure. With a JWT each service verifies alone with the issuer's public key —
  nothing to be down, nothing to be slow. That is the strongest argument for JWTs, and
  it is an argument about *trust boundaries*, not about performance.
- **Claims are the payload.** `aud`, `scope` and a short `exp` express "this call, to
  this recipient, for the next minute" — a session id cannot say any of that.

Practical shape: **RS256/ES256** (services verify with a public key, only the issuer
holds the private one — with HS256 every verifier could also *mint* tokens), short
`exp`, a mandatory `aud` per recipient, and keys published at a JWKS endpoint so
rotation is possible. mTLS is the alternative when you want the *transport* to prove
identity; the two compose — mTLS says which service, the JWT says on whose behalf. See
[options for configuring inter-service communication](topic:inter-service-communication-options).

An [API gateway](topic:api-gateway) usually does the expensive validation once at the
edge, but each service behind it must still verify — or be genuinely unreachable from
anywhere else. "The gateway checked it" is a fine optimisation and a terrible security
boundary.

## The 60-second interview answer

> A JWT is three base64url parts — header, payload, signature. The payload is a set of
> claims like `sub`, `iss`, `aud` and `exp`, and it is *signed, not encrypted*: anyone
> holding the token reads every claim, and the signature is the only reason those
> claims are evidence instead of text the client typed. So verifying means recomputing
> the signature with the algorithm and key I chose — never the one the header names,
> that is the `alg: none` bug — and then checking `exp`, `iss` and `aud`.
>
> The choice against a session token is really "where does the truth live". A session
> id is opaque and costs one lookup per request, and that lookup buys instant logout,
> instant bans, and always-current roles. A JWT costs no lookup and any service with
> the key can verify it alone — and pays for that by being a snapshot: after logout it
> still verifies, and a demoted user keeps the old role until `exp`.
>
> So for a browser talking to my own backend I default to a session id in an `HttpOnly`
> cookie, because revocation is the everyday case there and the lookup is cheap. For
> service-to-service I default to short-lived JWTs signed with RS256, scoped with `aud`
> — no browser, no logout, and no shared session store to couple every service to. And
> if I need JWTs *and* instant revocation, I keep a deny-list and admit that I have put
> the state back.

## Why it matters in production

- **Use a maintained library and configure it strictly.** Pin the algorithm, require
  `exp`, `iss` and `aud`, and set a small clock-skew allowance. Hand-rolled
  verification is where the CVEs are.
- **Keep the payload small and non-secret.** It is on every request, it is readable by
  everyone, and it cannot be corrected until it expires.
- **Plan key rotation before you need it.** Publish a JWKS with a `kid` in the header,
  accept the old key while tokens signed with it are still alive, then drop it. Without
  this, rotating a key logs everybody out at once.
- **Decide the revocation story on day one.** "How long can a fired employee's token
  keep working?" has a number as its answer, and the number is your `exp`.
- **Log the `jti`, never the token.** A token in a log file is a live credential in a
  log file — like any [bearer credential](topic:authentication-flow), whoever holds it
  is the user, so it is HTTPS-only ([HTTP vs HTTPS](topic:http-vs-https)) and
  short-lived.
- **Do not put a JWT in a URL.** URLs land in browser history, `Referer` headers,
  access logs and screenshots.

## Common misconceptions

- **"A JWT is encrypted."** It is base64url-encoded and *signed*. Encryption is a
  different spec (JWE) and almost nobody uses it. Read your own token in a debugger
  once and this stops being abstract.
- **"Decoding it identifies the caller."** Decoding is free for everybody, including
  the attacker who wrote the token. Only verification makes a claim evidence.
- **"JWTs are stateless, so they're better."** Statelessness is a trade, not an
  upgrade: you exchange instant revocation and fresh claims for skipping a lookup. If
  you then add a deny-list, you have the state back *and* the bigger credential.
- **"Sessions don't scale."** A session lookup is a primary-key read from Redis. Most
  systems that "needed" JWTs for scale had one backend and no measurement. Scale is
  about trust boundaries — many independent verifiers — far more often than about load.
- **"Logging out invalidates the JWT."** Only if something on the server acts on it.
  Otherwise "log out" is a button that clears local storage while every copy of the
  token keeps working until `exp`.
- **"JWT means no CSRF."** Only if it travels in an `Authorization` header your own
  code sets. A JWT in a cookie is attached automatically by the browser and is exactly
  as forgeable as a session cookie.
- **"Just make `exp` long so users stay signed in."** That is the revocation window.
  Use a short access token and a refresh token you can actually revoke.
- **"The gateway validated it, so services can trust the header."** Only if nothing can
  reach those services except through the gateway, and the gateway strips incoming
  copies of that header.
- **"A signature proves the token was meant for me."** It proves who issued it. `aud`
  is what makes it a message to one recipient.
- **"Put the user's permissions in the token so services need no lookups."** Then a
  permission change takes effect at `exp`, not now — and the token grows on every
  request. Sometimes right; always a decision, never a freebie.
