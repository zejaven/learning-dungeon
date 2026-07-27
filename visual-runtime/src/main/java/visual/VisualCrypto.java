package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of the two families of encryption and of why real systems use
 * both.
 *
 * <p><b>Symmetric</b> cryptography has one key: the same secret encrypts and decrypts, it is
 * fast, it works on any amount of data — and every party that must read the message needs a
 * copy of it. That last sentence is the whole problem: getting the key to the other side over
 * a channel you do not yet trust, and doing it again for every pair of participants.
 *
 * <p><b>Asymmetric</b> cryptography has two mathematically linked keys and gives up speed to
 * buy that problem away. The public half can be handed to anybody; the private half never
 * moves. Which half you use tells you which property you get:
 *
 * <ul>
 *   <li>encrypt with the <em>public</em> key, decrypt with the <em>private</em> one —
 *       <em>confidentiality</em>: anyone can write to the owner, only the owner can read;</li>
 *   <li>sign with the <em>private</em> key, verify with the <em>public</em> one —
 *       <em>authenticity</em>: only the owner could have produced it, and everybody can check.
 *       A signed message is not a secret message.</li>
 * </ul>
 *
 * <p>Asymmetric operations are thousands of times slower and bounded by the key size
 * ({@link #MAX_ASYMMETRIC_PAYLOAD} bytes for RSA-2048), so nothing bulky is ever encrypted
 * with them directly. The answer everywhere — TLS, PGP, JWE, SSH, disk encryption with a
 * smart card — is <b>hybrid</b>: a fresh symmetric key carries the data, and asymmetric
 * cryptography is spent once on that key. {@link #sendHybrid} shows the whole pattern.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is intentionally
 * dependency-free and every value it produces is deterministic, so a run always traces the
 * same way. Nothing here is real cryptography — it is a model of who can read what.
 */
public class VisualCrypto {

    /** The symmetric cipher this model uses for bulk data. */
    public static final String SYMMETRIC_ALGORITHM = "AES-256-GCM";

    /** The asymmetric algorithm this model uses for key transport and signatures. */
    public static final String ASYMMETRIC_ALGORITHM = "RSA-2048-OAEP";

    /** RSA-2048 with OAEP/SHA-256 leaves exactly this many bytes for the payload. */
    public static final int MAX_ASYMMETRIC_PAYLOAD = 190;

    /** The party on the path: sees everything sent, holds every published public key. */
    public static final String ATTACKER = "Mallory";

    /** Model throughput of the symmetric cipher, in bytes per microsecond. */
    private static final int SYMMETRIC_BYTES_PER_MICRO = 1500;

    /** Model cost of one asymmetric private-key operation, in microseconds. */
    private static final int ASYMMETRIC_MICROS_PER_OP = 1000;

    /** Size of a freshly generated AES-256 session key, in bytes. */
    private static final int SESSION_KEY_BYTES = 32;

    // ----------------------------------------------------------------------- keys

    /** One shared secret. Whoever holds a copy can both encrypt and decrypt with it. */
    public static final class SecretKey {

        private final String id;

        private SecretKey(String id) {
            this.id = id;
        }

        /** The label this key is known by. */
        public String id() {
            return id;
        }
    }

    /** Two mathematically linked keys: one meant to be published, one that never moves. */
    public static final class KeyPair {

        private final String owner;

        private KeyPair(String owner) {
            this.owner = owner;
        }

        /** Who the pair belongs to. */
        public String owner() {
            return owner;
        }

        /** The half that is handed out; publishing it is the point, not a leak. */
        public String publicKeyId() {
            return owner + "/public";
        }

        /** The half that stays put. Everything the pair proves rests on this never moving. */
        public String privateKeyId() {
            return owner + "/private";
        }
    }

    /** What travels: a payload, what it was protected with, and what opens it. */
    public static final class Envelope {

        private final int seq;
        private final String family;
        private final String label;
        private final String onTheWire;
        private final String algorithm;
        private final String keyId;
        private final String wrappedKey;
        private final String sessionKeyId;
        private final int bytes;
        private boolean intact = true;

        private Envelope(int seq, String family, String label, String onTheWire, String algorithm,
                         String keyId, String wrappedKey, String sessionKeyId, int bytes) {
            this.seq = seq;
            this.family = family;
            this.label = label;
            this.onTheWire = onTheWire;
            this.algorithm = algorithm;
            this.keyId = keyId;
            this.wrappedKey = wrappedKey;
            this.sessionKeyId = sessionKeyId;
            this.bytes = bytes;
        }

        /** Which family protected it: symmetric, asymmetric, hybrid, a signature, or nothing. */
        public String family() {
            return family;
        }

        /** The bytes an observer actually sees. */
        public String onTheWire() {
            return onTheWire;
        }

        /** The key that opens (or verifies) it. */
        public String keyId() {
            return keyId;
        }

        /** Whether the payload still matches what was protected. */
        public boolean intact() {
            return intact;
        }
    }

    private static final class KeyRecord {

        private final String id;
        private final String kind;
        private final String algorithm;
        private final int bits;
        private final String owner;
        private final List<String> holders;
        private boolean published;
        private boolean compromised;

        private KeyRecord(String id, String kind, String algorithm, int bits, String owner,
                          List<String> holders) {
            this.id = id;
            this.kind = kind;
            this.algorithm = algorithm;
            this.bits = bits;
            this.owner = owner;
            this.holders = new ArrayList<>(holders);
        }
    }

    // ---------------------------------------------------------------------- state

    private final Map<String, KeyRecord> keys = new LinkedHashMap<>();
    private final List<Envelope> channel = new ArrayList<>();
    private final Set<String> attackerKeys = new LinkedHashSet<>();

    private String opName = "";
    private String opFamily = "none";
    private String opAlgorithm = "";
    private String opKeyId = "";
    private String opKeyKind = "";
    private String opInput = "";
    private String opOutput = "";
    private long opMicros;
    private String opVerdict = "idle";

    private String decision = "idle";
    private String reason;
    private String detail = "";

    private int parties;
    private long secretKeysNeeded;
    private long keyPairsNeeded;

    private String comparisonLabel = "";
    private int comparisonBytes;
    private long comparisonSymmetricMicros;
    private long comparisonAsymmetricMicros;
    private long comparisonBlocks;
    private long comparisonRatio;

    private int symmetricOps;
    private int asymmetricOps;
    private long symmetricMicros;
    private long asymmetricMicros;

    private int encrypted;
    private int decrypted;
    private int failed;
    private int signed;
    private int verified;
    private int exposed;

    private int sessionKeyCounter;

    private VisualCrypto() {
    }

    /**
     * An empty lab: no keys, nothing on the wire, and a party on the path who will see
     * everything that is sent.
     */
    public static VisualCrypto lab() {
        VisualCrypto lab = new VisualCrypto();
        Trace.event("CRYPTO_LAB",
                "An empty lab. Two questions decide everything that follows: HOW MANY keys does "
                        + "an algorithm use, and WHICH of them may be published. Symmetric = one key "
                        + "that every reader must already have; asymmetric = a pair, where one half is "
                        + "meant to be handed to strangers. Everything else — speed, message size, key "
                        + "management, signatures — falls out of that one difference",
                "Пустая лаборатория. Всё дальнейшее решают два вопроса: СКОЛЬКО ключей использует "
                        + "алгоритм и КАКОЙ из них можно опубликовать. Симметричное шифрование — один "
                        + "ключ, который должен быть у каждого читателя; асимметричное — пара, где одну "
                        + "половину специально отдают посторонним. Всё остальное — скорость, размер "
                        + "сообщения, управление ключами, подписи — следствия этой единственной разницы",
                List.of("keys"), lab.state());
        return lab;
    }

    // ------------------------------------------------------------- key generation

    /**
     * Generates one shared secret and hands a copy to each holder. Note the plural: a
     * symmetric key is only useful once more than one party has the same bytes.
     */
    public SecretKey generateSecretKey(String label, String... holders) {
        beginStep();
        List<String> owners = new ArrayList<>(List.of(holders));
        keys.put(label, new KeyRecord(label, "secret", SYMMETRIC_ALGORITHM, 256, "", owners));
        opName = "generate";
        opFamily = "symmetric";
        opAlgorithm = SYMMETRIC_ALGORITHM;
        opKeyId = label;
        opKeyKind = "secret";
        opOutput = label;
        opVerdict = "ok";
        decision = "ok";
        detail = label;
        Trace.event("SECRET_KEY_GENERATED",
                "One key, '" + label + "' (" + SYMMETRIC_ALGORITHM + ", 256 bits), and a copy for "
                        + "each of: " + String.join(", ", owners) + ". The SAME key encrypts and "
                        + "decrypts — that symmetry is where the name comes from, and it is why this "
                        + "key can never be published: anybody who can read a message with it can "
                        + "also forge one",
                "Один ключ, «" + label + "» (" + SYMMETRIC_ALGORITHM + ", 256 бит), и по копии "
                        + "каждому из: " + String.join(", ", owners) + ". ОДИН И ТОТ ЖЕ ключ и "
                        + "шифрует, и расшифровывает — отсюда и название «симметричное», и поэтому "
                        + "такой ключ нельзя публиковать: тот, кто может им прочитать сообщение, может "
                        + "им же и подделать",
                List.of("keys", "key:" + label, "operation"), state());
        return new SecretKey(label);
    }

    /**
     * Generates a key pair. The two halves are linked by mathematics, not by copying: what one
     * does, only the other undoes, and the private half cannot be derived from the public one.
     */
    public KeyPair generateKeyPair(String owner) {
        beginStep();
        KeyPair pair = new KeyPair(owner);
        keys.put(pair.publicKeyId(), new KeyRecord(pair.publicKeyId(), "public",
                ASYMMETRIC_ALGORITHM, 2048, owner, List.of(owner)));
        keys.put(pair.privateKeyId(), new KeyRecord(pair.privateKeyId(), "private",
                ASYMMETRIC_ALGORITHM, 2048, owner, List.of(owner)));
        opName = "generate";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = pair.privateKeyId();
        opKeyKind = "private";
        opOutput = pair.publicKeyId() + " + " + pair.privateKeyId();
        opVerdict = "ok";
        decision = "ok";
        detail = owner;
        Trace.event("KEY_PAIR_GENERATED",
                owner + " generates a PAIR (" + ASYMMETRIC_ALGORITHM + ", 2048 bits): two keys "
                        + "linked by mathematics, where what one key does only the other one undoes. "
                        + "The private half cannot be computed from the public half — that asymmetry "
                        + "is the whole trick, and it is what lets one half be given to complete "
                        + "strangers while the other never leaves this machine",
                owner + " генерирует ПАРУ (" + ASYMMETRIC_ALGORITHM + ", 2048 бит): два ключа, "
                        + "связанных математически, где сделанное одним отменяет только другой. "
                        + "Приватную половину нельзя вычислить из публичной — в этой асимметрии весь "
                        + "фокус, и именно она позволяет отдать одну половину совершенно посторонним, "
                        + "а вторую не выпускать с этой машины",
                List.of("keys", "key:" + pair.publicKeyId(), "key:" + pair.privateKeyId(),
                        "operation"), state());
        return pair;
    }

    /** Publishes the public half. This is not a leak — it is the reason the pair exists. */
    public VisualCrypto publish(KeyPair pair) {
        beginStep();
        KeyRecord record = keys.get(pair.publicKeyId());
        record.published = true;
        record.holders.clear();
        record.holders.add("everyone");
        attackerKeys.add(pair.publicKeyId());
        opName = "publish";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = pair.publicKeyId();
        opKeyKind = "public";
        opOutput = "everyone";
        opVerdict = "ok";
        decision = "ok";
        detail = pair.publicKeyId();
        Trace.event("PUBLIC_KEY_PUBLISHED",
                pair.publicKeyId() + " goes into a directory anybody can read — including "
                        + ATTACKER + ". This is NOT a leak: it is the point. Compare it with the "
                        + "symmetric case, where handing the key to the other side is the hardest "
                        + "part of the whole scheme; here the distribution problem disappears, "
                        + "because the thing you have to distribute is not a secret",
                pair.publicKeyId() + " попадает в справочник, который может прочитать кто угодно, "
                        + "включая " + ATTACKER + ". Это НЕ утечка, а смысл всей конструкции. "
                        + "Сравните с симметричным случаем, где передача ключа другой стороне — самая "
                        + "трудная часть схемы; здесь проблема распространения исчезает, потому что "
                        + "распространяемое не является секретом",
                List.of("keys", "key:" + pair.publicKeyId(), "attacker"), state());
        return this;
    }

    // -------------------------------------------------------------- symmetric side

    /** Encrypts with a shared secret — one key, any size of data, essentially free. */
    public Envelope encrypt(SecretKey key, String plaintext) {
        beginStep();
        int bytes = plaintext.length();
        long micros = symmetricCost(bytes);
        String ciphertext = hex("sym|" + key.id + "|" + (channel.size() + 1) + "|" + plaintext);
        Envelope envelope = new Envelope(channel.size() + 1, "symmetric", plaintext, ciphertext,
                SYMMETRIC_ALGORITHM, key.id, null, null, bytes);
        channel.add(envelope);
        encrypted++;
        symmetricOps++;
        symmetricMicros += micros;
        opName = "encrypt";
        opFamily = "symmetric";
        opAlgorithm = SYMMETRIC_ALGORITHM;
        opKeyId = key.id;
        opKeyKind = "secret";
        opInput = plaintext;
        opOutput = ciphertext;
        opMicros = micros;
        opVerdict = "ok";
        decision = "ok";
        detail = plaintext;
        Trace.event("SYMMETRIC_ENCRYPT",
                "\"" + plaintext + "\" becomes " + ciphertext + " under '" + key.id + "'. "
                        + bytes + " byte(s) in about " + micros + " microsecond(s): a symmetric "
                        + "cipher is a stream of cheap block operations, so cost grows with the DATA "
                        + "and not with the key. There is no size limit and no practical speed "
                        + "penalty — this is why every protocol ends up carrying its payload this way",
                "«" + plaintext + "» превращается в " + ciphertext + " под ключом «" + key.id
                        + "». Байт: " + bytes + ", примерно за " + micros + " микросекунд(ы): "
                        + "симметричный шифр — это поток дешёвых блочных операций, поэтому стоимость "
                        + "растёт от ОБЪЁМА ДАННЫХ, а не от ключа. Ни ограничения на размер, ни "
                        + "заметной потери скорости — поэтому любой протокол в итоге везёт полезную "
                        + "нагрузку именно так",
                List.of("operation", "channel", "key:" + key.id, "cost"), state());
        return envelope;
    }

    /** Decrypts with a shared secret. The same key, or nothing at all. */
    public String decrypt(SecretKey key, Envelope envelope) {
        beginStep();
        opName = "decrypt";
        opFamily = "symmetric";
        opAlgorithm = SYMMETRIC_ALGORITHM;
        opKeyId = key.id;
        opKeyKind = "secret";
        opInput = envelope.onTheWire;
        if (!key.id.equals(envelope.keyId)) {
            failed++;
            opOutput = "";
            opVerdict = "failed";
            decision = "failed";
            reason = "wrong-key";
            detail = key.id;
            Trace.event("WRONG_KEY",
                    "'" + key.id + "' is a perfectly good 256-bit key and it produces nothing but "
                            + "an authentication failure, because the record was sealed under '"
                            + envelope.keyId + "'. Symmetric decryption is not 'try to make sense of "
                            + "it': the tag either matches or the message is rejected whole. Which "
                            + "means the receiver has to have exactly the right bytes — that is the "
                            + "key distribution problem in one line",
                    "«" + key.id + "» — совершенно нормальный 256-битный ключ, и он даёт только "
                            + "ошибку проверки подлинности, потому что запись запечатана под «"
                            + envelope.keyId + "». Симметричная расшифровка — это не «попробовать "
                            + "разобрать»: тег либо сходится, либо сообщение отвергается целиком. "
                            + "Значит, у получателя должны быть ровно те самые байты — вот вам "
                            + "проблема распространения ключей одной строкой",
                    List.of("operation", "key:" + key.id, "channel"), state());
            return null;
        }
        long micros = symmetricCost(envelope.bytes);
        decrypted++;
        symmetricOps++;
        symmetricMicros += micros;
        opOutput = envelope.label;
        opMicros = micros;
        opVerdict = "ok";
        decision = "ok";
        detail = envelope.label;
        Trace.event("SYMMETRIC_DECRYPT",
                "The SAME key '" + key.id + "' turns " + envelope.onTheWire + " back into \""
                        + envelope.label + "\". Encryption and decryption are the same operation "
                        + "run in reverse with the same secret — which also means the receiver could "
                        + "have produced this message. A shared key proves nobody outside the group "
                        + "wrote it; it cannot prove WHICH member did",
                "ТОТ ЖЕ ключ «" + key.id + "» превращает " + envelope.onTheWire + " обратно в «"
                        + envelope.label + "». Шифрование и расшифровка — одна и та же операция, "
                        + "выполненная в обратную сторону тем же секретом, а значит, получатель мог "
                        + "бы и сам создать это сообщение. Общий ключ доказывает, что его написал "
                        + "кто-то из группы, но не может доказать, КТО ИМЕННО",
                List.of("operation", "key:" + key.id, "channel"), state());
        return envelope.label;
    }

    // ------------------------------------------------------------- asymmetric side

    /**
     * Encrypts to somebody's public key. Anybody can do this; only the holder of the private
     * half can undo it — including the sender, who cannot read their own message back.
     */
    public Envelope encryptFor(KeyPair recipient, String plaintext) {
        return sealFor(recipient, plaintext, plaintext.length());
    }

    /**
     * The same operation on a payload of a stated size — which is how you discover that
     * asymmetric encryption has a hard ceiling of {@link #MAX_ASYMMETRIC_PAYLOAD} bytes.
     */
    public Envelope encryptBlobFor(KeyPair recipient, String label, int bytes) {
        return sealFor(recipient, label, bytes);
    }

    private Envelope sealFor(KeyPair recipient, String label, int bytes) {
        beginStep();
        opName = "encrypt";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = recipient.publicKeyId();
        opKeyKind = "public";
        opInput = label;
        if (bytes > MAX_ASYMMETRIC_PAYLOAD) {
            failed++;
            opVerdict = "failed";
            decision = "failed";
            reason = "payload-too-large";
            detail = bytes + " > " + MAX_ASYMMETRIC_PAYLOAD;
            Trace.event("PAYLOAD_TOO_LARGE",
                    "Refused: " + bytes + " bytes into a " + ASYMMETRIC_ALGORITHM + " operation "
                            + "that holds at most " + MAX_ASYMMETRIC_PAYLOAD + ". RSA encrypts ONE "
                            + "number smaller than the modulus, so the key size is also the message "
                            + "size — 2048 bits is 256 bytes, minus the OAEP padding. Splitting the "
                            + "file into thousands of blocks is possible, ruinous, and not what "
                            + "anybody does",
                    "Отказ: " + bytes + " байт в операцию " + ASYMMETRIC_ALGORITHM + ", куда "
                            + "помещается максимум " + MAX_ASYMMETRIC_PAYLOAD + ". RSA шифрует ОДНО "
                            + "число, меньшее модуля, поэтому размер ключа заодно задаёт и размер "
                            + "сообщения: 2048 бит — это 256 байт, минус набивка OAEP. Разрезать файл "
                            + "на тысячи блоков можно, это разорительно, и так никто не делает",
                    List.of("operation", "key:" + recipient.publicKeyId()), state());
            return null;
        }
        long micros = ASYMMETRIC_MICROS_PER_OP;
        String ciphertext = hex("asym|" + recipient.owner + "|" + (channel.size() + 1) + "|" + label);
        Envelope envelope = new Envelope(channel.size() + 1, "asymmetric", label, ciphertext,
                ASYMMETRIC_ALGORITHM, recipient.privateKeyId(), null, null, bytes);
        channel.add(envelope);
        encrypted++;
        asymmetricOps++;
        asymmetricMicros += micros;
        opOutput = ciphertext;
        opMicros = micros;
        opVerdict = "ok";
        decision = "ok";
        detail = label;
        Trace.event("ASYMMETRIC_ENCRYPT",
                "\"" + label + "\" is sealed with " + recipient.publicKeyId() + " — a key that "
                        + "everyone has. Read what that buys: no shared secret was ever agreed, no "
                        + "key was ever sent, and the sender and " + recipient.owner + " have never "
                        + "met. It also means the SENDER can no longer read this message; only "
                        + recipient.privateKeyId() + " opens it",
                "«" + label + "» запечатано ключом " + recipient.publicKeyId() + ", который есть "
                        + "у всех. Посмотрите, что это даёт: общий секрет не согласовывали, ключ не "
                        + "пересылали, а отправитель и " + recipient.owner + " никогда не "
                        + "встречались. Это же значит, что ОТПРАВИТЕЛЬ больше не может прочитать это "
                        + "сообщение: открывает его только " + recipient.privateKeyId(),
                List.of("operation", "channel", "key:" + recipient.publicKeyId(), "cost"), state());
        return envelope;
    }

    /** Decrypts with the private half — the only key in the world that opens this envelope. */
    public String decryptWith(KeyPair recipient, Envelope envelope) {
        beginStep();
        opName = "decrypt";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = recipient.privateKeyId();
        opKeyKind = "private";
        opInput = envelope.onTheWire;
        if (!recipient.privateKeyId().equals(envelope.keyId)) {
            failed++;
            opVerdict = "failed";
            decision = "failed";
            reason = "wrong-key";
            detail = recipient.privateKeyId();
            Trace.event("WRONG_KEY",
                    recipient.privateKeyId() + " is a real private key and it is the wrong one: "
                            + "this envelope was sealed to " + envelope.keyId + ". Public-key "
                            + "encryption is addressed — you choose the recipient at encryption "
                            + "time, and only that one key undoes it",
                    recipient.privateKeyId() + " — настоящий приватный ключ, и он не тот: этот "
                            + "конверт запечатан на " + envelope.keyId + ". Шифрование с открытым "
                            + "ключом адресное: получателя выбирают в момент шифрования, и отменяет "
                            + "операцию только этот единственный ключ",
                    List.of("operation", "key:" + recipient.privateKeyId(), "channel"), state());
            return null;
        }
        long micros = ASYMMETRIC_MICROS_PER_OP;
        decrypted++;
        asymmetricOps++;
        asymmetricMicros += micros;
        opOutput = envelope.label;
        opMicros = micros;
        opVerdict = "ok";
        decision = "ok";
        detail = envelope.label;
        Trace.event("ASYMMETRIC_DECRYPT",
                recipient.privateKeyId() + " recovers \"" + envelope.label + "\". Note the "
                        + "direction: the key that CLOSED the envelope cannot open it. That is the "
                        + "difference from a shared secret, and it is why the public half can be "
                        + "printed on a billboard — it grants the power to write to "
                        + recipient.owner + ", never the power to read what was written",
                recipient.privateKeyId() + " восстанавливает «" + envelope.label + "». Обратите "
                        + "внимание на направление: ключ, которым конверт ЗАКРЫЛИ, открыть его не "
                        + "может. Это и есть отличие от общего секрета, и поэтому публичную половину "
                        + "можно печатать на билборде — она даёт право писать " + recipient.owner
                        + ", но никогда не право читать написанное",
                List.of("operation", "key:" + recipient.privateKeyId(), "channel", "cost"), state());
        return envelope.label;
    }

    // ---------------------------------------------------------------- signatures

    /**
     * Signs with the private half. This is the pair used the other way round: it proves
     * authorship to everybody and hides nothing from anybody.
     */
    public Envelope sign(KeyPair signer, String message) {
        beginStep();
        String signature = hex("sig|" + signer.owner + "|" + message);
        Envelope envelope = new Envelope(channel.size() + 1, "signature", message, message,
                ASYMMETRIC_ALGORITHM, signer.publicKeyId(), null, null, message.length());
        channel.add(envelope);
        signed++;
        asymmetricOps++;
        asymmetricMicros += ASYMMETRIC_MICROS_PER_OP;
        opName = "sign";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = signer.privateKeyId();
        opKeyKind = "private";
        opInput = message;
        opOutput = signature;
        opMicros = ASYMMETRIC_MICROS_PER_OP;
        opVerdict = "ok";
        decision = "ok";
        detail = signature;
        Trace.event("MESSAGE_SIGNED",
                signer.owner + " signs \"" + message + "\" with " + signer.privateKeyId()
                        + ", producing " + signature + ". The pair is being used in the OTHER "
                        + "direction — private to produce, public to check — and the properties "
                        + "flip with it: this proves authorship and integrity to everyone, and "
                        + "hides nothing. The message is still there in the clear; a signature is "
                        + "not encryption",
                signer.owner + " подписывает «" + message + "» ключом " + signer.privateKeyId()
                        + ", получая " + signature + ". Пара используется в ДРУГУЮ сторону — "
                        + "приватным создают, публичным проверяют, — и свойства меняются вместе с "
                        + "ней: это доказывает авторство и целостность всем и не скрывает ничего. "
                        + "Сообщение по-прежнему лежит открытым текстом; подпись — не шифрование",
                List.of("operation", "channel", "key:" + signer.privateKeyId()), state());
        return envelope;
    }

    /** Verifies a signature with the public half — which is why anybody can check it. */
    public boolean verify(KeyPair signer, Envelope signature) {
        beginStep();
        opName = "verify";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = signer.publicKeyId();
        opKeyKind = "public";
        opInput = signature.label;
        asymmetricOps++;
        asymmetricMicros += ASYMMETRIC_MICROS_PER_OP;
        opMicros = ASYMMETRIC_MICROS_PER_OP;
        if (!signature.intact || !signer.publicKeyId().equals(signature.keyId)) {
            failed++;
            opOutput = "";
            opVerdict = "failed";
            decision = "failed";
            reason = "signature-mismatch";
            detail = signature.label;
            Trace.event("SIGNATURE_INVALID",
                    "Verification fails. The signature was computed over the ORIGINAL bytes, and "
                            + "what arrived is \"" + signature.label + "\" — one changed character "
                            + "is enough. Nobody can repair the signature without "
                            + signer.privateKeyId() + ", which is exactly the guarantee: an "
                            + "attacker on the path can destroy the message but cannot rewrite it "
                            + "convincingly",
                    "Проверка не проходит. Подпись считалась по ИСХОДНЫМ байтам, а пришло «"
                            + signature.label + "» — достаточно одного изменённого символа. "
                            + "Починить подпись без " + signer.privateKeyId() + " не может никто, и "
                            + "в этом вся гарантия: злоумышленник на пути способен уничтожить "
                            + "сообщение, но не переписать его убедительно",
                    List.of("operation", "channel", "key:" + signer.publicKeyId()), state());
            return false;
        }
        verified++;
        opOutput = signer.owner;
        opVerdict = "ok";
        decision = "ok";
        detail = signer.owner;
        Trace.event("SIGNATURE_VERIFIED",
                "Anyone holding " + signer.publicKeyId() + " — that is, anyone at all — can "
                        + "confirm that \"" + signature.label + "\" was signed by "
                        + signer.privateKeyId() + " and has not changed since. A shared secret "
                        + "could not do this: every holder of a symmetric key can also forge with "
                        + "it, so it can never single out an author. That gap is why signatures "
                        + "have to be asymmetric",
                "Любой, у кого есть " + signer.publicKeyId() + ", то есть вообще кто угодно, "
                        + "может подтвердить, что «" + signature.label + "» подписано ключом "
                        + signer.privateKeyId() + " и с тех пор не менялось. Общий секрет так не "
                        + "умеет: каждый владелец симметричного ключа может им же и подделать, "
                        + "поэтому выделить автора он не в состоянии. Из-за этого разрыва подписи и "
                        + "делают асимметричными",
                List.of("operation", "channel", "key:" + signer.publicKeyId()), state());
        return true;
    }

    /** Changes a signed message in flight, so verification has something to catch. */
    public void tamper(Envelope envelope) {
        beginStep();
        envelope.intact = false;
        opName = "tamper";
        opFamily = envelope.family;
        opInput = envelope.label;
        opOutput = envelope.label + " (altered)";
        opVerdict = "failed";
        decision = "exposed";
        reason = "tampered";
        detail = envelope.label;
        Trace.event("MESSAGE_TAMPERED",
                ATTACKER + " rewrites message #" + envelope.seq + " on the path. Nothing stops "
                        + "the edit — the wire belongs to whoever is on it. The question is only "
                        + "whether the receiver can TELL, and that is a property of the protection "
                        + "used, not of the network",
                ATTACKER + " переписывает сообщение №" + envelope.seq + " на пути. Саму правку "
                        + "ничто не останавливает — провод принадлежит тому, кто на нём. Вопрос "
                        + "только в том, СМОЖЕТ ЛИ получатель это заметить, а это свойство "
                        + "применённой защиты, а не сети",
                List.of("channel", "attacker"), state());
    }

    // -------------------------------------------------------- the two hard problems

    /**
     * Sends a shared secret over the same channel the messages travel on — the bootstrapping
     * problem symmetric cryptography cannot solve on its own.
     */
    public void shareKeyInTheOpen(SecretKey key, String recipient) {
        beginStep();
        KeyRecord record = keys.get(key.id);
        record.holders.add(recipient);
        record.compromised = true;
        attackerKeys.add(key.id);
        exposed++;
        Envelope envelope = new Envelope(channel.size() + 1, "key-material", key.id + " (the key)",
                key.id, "none", key.id, null, null, 32);
        channel.add(envelope);
        opName = "share";
        opFamily = "symmetric";
        opAlgorithm = "none";
        opKeyId = key.id;
        opKeyKind = "secret";
        opInput = key.id;
        opOutput = recipient + ", " + ATTACKER;
        opVerdict = "failed";
        decision = "exposed";
        reason = "key-in-the-open";
        detail = key.id;
        Trace.event("KEY_SHARED_IN_THE_OPEN",
                "To read anything, " + recipient + " first needs the key — so it is sent over the "
                        + "same channel, and " + ATTACKER + " now has it too. This is the KEY "
                        + "DISTRIBUTION PROBLEM: symmetric cryptography protects messages "
                        + "beautifully and has no way to bootstrap itself. Either you exchange keys "
                        + "out of band, or you need something that works without a pre-shared "
                        + "secret",
                "Чтобы что-то прочитать, " + recipient + " сначала нужен ключ — и его отправляют "
                        + "по тому же каналу, а значит, он теперь есть и у " + ATTACKER + ". Это "
                        + "ПРОБЛЕМА РАСПРОСТРАНЕНИЯ КЛЮЧЕЙ: симметричная криптография прекрасно "
                        + "защищает сообщения и совершенно не умеет запускать саму себя. Либо вы "
                        + "обмениваетесь ключами по другому каналу, либо вам нужно что-то "
                        + "работающее без заранее общего секрета",
                List.of("channel", "attacker", "key:" + key.id), state());
    }

    /** What the party on the path can do with what it has collected so far. */
    public void attackerTries(Envelope envelope) {
        beginStep();
        opName = "intercept";
        opFamily = envelope.family;
        opAlgorithm = envelope.algorithm;
        opKeyId = envelope.keyId;
        opInput = envelope.onTheWire;
        boolean plain = "signature".equals(envelope.family) || "key-material".equals(envelope.family);
        if (plain || attackerKeys.contains(envelope.keyId)) {
            exposed++;
            opOutput = envelope.label;
            opVerdict = "failed";
            decision = "exposed";
            reason = plain ? "not-encrypted" : "attacker-has-the-key";
            detail = envelope.label;
            Trace.event("ATTACKER_READS",
                    ATTACKER + " reads message #" + envelope.seq + ": \"" + envelope.label
                            + "\". " + (plain
                            ? "Nothing here was ever encrypted — a signature protects authorship "
                            + "and integrity, not confidentiality, and key material sent in the "
                            + "clear is just a secret published slowly."
                            : "The protection was fine; the KEY was the failure. Cryptography does "
                            + "not fail by being broken, it fails by the key reaching somebody it "
                            + "should not have.")
                            + " Which key had to stay secret is the whole security model",
                    ATTACKER + " читает сообщение №" + envelope.seq + ": «" + envelope.label
                            + "». " + (plain
                            ? "Здесь вообще ничего не шифровалось: подпись защищает авторство и "
                            + "целостность, а не конфиденциальность, а ключевой материал, "
                            + "отправленный открытым текстом, — это просто секрет, опубликованный "
                            + "медленно."
                            : "С защитой всё было в порядке, подвёл КЛЮЧ. Криптография ломается не "
                            + "потому, что её взломали, а потому, что ключ попал не к тому.")
                            + " Какой ключ обязан оставаться секретным — в этом и состоит вся модель "
                            + "безопасности",
                    List.of("channel", "attacker", "operation"), state());
            return;
        }
        opOutput = "";
        opVerdict = "blocked";
        decision = "blocked";
        reason = "asymmetric".equals(envelope.family) || "hybrid".equals(envelope.family)
                ? "public-key-cannot-decrypt" : "no-key";
        detail = envelope.onTheWire;
        Trace.event("ATTACKER_STUCK",
                ATTACKER + " has message #" + envelope.seq + " (" + envelope.onTheWire + "), the "
                        + "algorithm, and every published public key — and none of it helps. "
                        + ("symmetric".equals(envelope.family)
                        ? "The one thing missing is the shared secret '" + envelope.keyId + "'."
                        : "The public key CLOSES envelopes; it does not open them. Only "
                        + envelope.keyId + " does, and it never left its machine.")
                        + " Note that the algorithm being public is normal: security lives in the "
                        + "key, not in secrecy about the method",
                "У " + ATTACKER + " есть сообщение №" + envelope.seq + " (" + envelope.onTheWire
                        + "), алгоритм и все опубликованные публичные ключи — и ничего из этого не "
                        + "помогает. " + ("symmetric".equals(envelope.family)
                        ? "Не хватает единственного: общего секрета «" + envelope.keyId + "»."
                        : "Публичный ключ ЗАКРЫВАЕТ конверты, а не открывает их. Открывает только "
                        + envelope.keyId + ", и он не покидал свою машину.")
                        + " Обратите внимание: публичность алгоритма — это норма, безопасность живёт "
                        + "в ключе, а не в тайне про метод",
                List.of("channel", "attacker", "operation"), state());
    }

    /**
     * The other half of key management: how many keys a group of that size needs under each
     * scheme. The symmetric answer grows quadratically; the asymmetric one grows linearly.
     */
    public void keyDistributionCost(int parties) {
        beginStep();
        this.parties = parties;
        this.secretKeysNeeded = (long) parties * (parties - 1) / 2;
        this.keyPairsNeeded = parties;
        opName = "count keys";
        opFamily = "none";
        opInput = parties + " parties";
        opOutput = secretKeysNeeded + " secret keys vs " + keyPairsNeeded + " key pairs";
        opVerdict = "ok";
        decision = "ok";
        detail = parties + " parties";
        Trace.event("KEY_DISTRIBUTION_COST",
                "For " + parties + " parties who must talk in private pairs: "
                        + secretKeysNeeded + " shared secrets (n(n-1)/2, quadratic — every one of "
                        + "them created, delivered, stored and rotated) against " + keyPairsNeeded
                        + " key pairs (linear, and every public half can simply be published). "
                        + "Adding one party to the symmetric scheme means " + (parties - 1)
                        + " new key exchanges; in the asymmetric one it means the newcomer "
                        + "generates a pair and announces half of it",
                "Для " + parties + " участников, которым нужны приватные разговоры попарно: "
                        + secretKeysNeeded + " общих секретов (n(n-1)/2, квадратично — и каждый "
                        + "нужно создать, доставить, хранить и ротировать) против " + keyPairsNeeded
                        + " ключевых пар (линейно, и каждую публичную половину можно просто "
                        + "опубликовать). Добавить одного участника в симметричную схему — это "
                        + (parties - 1) + " новых обменов ключами; в асимметричной новичок "
                        + "генерирует пару и объявляет её половину",
                List.of("keymgmt"), state());
    }

    /** Puts a number on the speed gap, which is the reason nobody encrypts bulk data with RSA. */
    public void compareCost(String label, int bytes) {
        beginStep();
        long sym = symmetricCost(bytes);
        long blocks = (bytes + MAX_ASYMMETRIC_PAYLOAD - 1L) / MAX_ASYMMETRIC_PAYLOAD;
        long asym = blocks * ASYMMETRIC_MICROS_PER_OP;
        comparisonLabel = label;
        comparisonBytes = bytes;
        comparisonSymmetricMicros = sym;
        comparisonAsymmetricMicros = asym;
        comparisonBlocks = blocks;
        comparisonRatio = asym / Math.max(1, sym);
        opName = "compare";
        opFamily = "none";
        opInput = label + " (" + bytes + " bytes)";
        opOutput = sym + " vs " + asym + " microseconds";
        opVerdict = "ok";
        decision = "ok";
        detail = label;
        Trace.event("PERFORMANCE_COMPARED",
                "Protecting " + label + " (" + bytes + " bytes): " + SYMMETRIC_ALGORITHM
                        + " takes about " + sym + " microseconds in one pass, while "
                        + ASYMMETRIC_ALGORITHM + " would need " + blocks + " separate operations "
                        + "for about " + asym + " — roughly " + comparisonRatio + " times slower. "
                        + "That is not a tuning difference. Symmetric ciphers do cheap bit "
                        + "operations with hardware support; asymmetric ones do modular "
                        + "exponentiation on huge numbers, which is why they are reserved for tiny "
                        + "inputs",
                "Защитить " + label + " (" + bytes + " байт): " + SYMMETRIC_ALGORITHM
                        + " справляется примерно за " + sym + " микросекунд за один проход, а "
                        + ASYMMETRIC_ALGORITHM + " потребовал бы " + blocks + " отдельных операций "
                        + "и около " + asym + " — примерно в " + comparisonRatio + " раз медленнее. "
                        + "Это не разница в настройках. Симметричные шифры делают дешёвые битовые "
                        + "операции с аппаратной поддержкой; асимметричные — модульное возведение в "
                        + "степень над огромными числами, поэтому их и берегут для крошечных входов",
                List.of("cost"), state());
    }

    // -------------------------------------------------------------------- hybrid

    /** The pattern every real protocol uses: asymmetric for the key, symmetric for the data. */
    public Envelope sendHybrid(KeyPair recipient, String message) {
        return hybrid(recipient, message, message.length());
    }

    /** The same pattern applied to a payload of a stated size — the case RSA alone refused. */
    public Envelope sendHybridBlob(KeyPair recipient, String label, int bytes) {
        return hybrid(recipient, label, bytes);
    }

    private Envelope hybrid(KeyPair recipient, String label, int bytes) {
        beginStep();
        String sessionKeyId = "session-" + (++sessionKeyCounter);
        keys.put(sessionKeyId, new KeyRecord(sessionKeyId, "secret", SYMMETRIC_ALGORITHM, 256, "",
                List.of("sender", recipient.owner)));
        String wrapped = hex("wrap|" + recipient.owner + "|" + sessionKeyId);
        asymmetricOps++;
        asymmetricMicros += ASYMMETRIC_MICROS_PER_OP;
        opName = "wrap key";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = recipient.publicKeyId();
        opKeyKind = "public";
        opInput = sessionKeyId + " (" + SESSION_KEY_BYTES + " bytes)";
        opOutput = wrapped;
        opMicros = ASYMMETRIC_MICROS_PER_OP;
        opVerdict = "ok";
        decision = "ok";
        detail = sessionKeyId;
        Trace.event("SESSION_KEY_WRAPPED",
                "A fresh " + SYMMETRIC_ALGORITHM + " key '" + sessionKeyId + "' is generated and "
                        + "then encrypted — all " + SESSION_KEY_BYTES + " bytes of it — with "
                        + recipient.publicKeyId() + ". The expensive algorithm is spent ONCE, on "
                        + "something that fits comfortably under the " + MAX_ASYMMETRIC_PAYLOAD
                        + "-byte ceiling. This single step is what solves key distribution: the "
                        + "shared secret is created on the spot instead of being agreed in advance",
                "Генерируется свежий ключ " + SYMMETRIC_ALGORITHM + " «" + sessionKeyId + "», и "
                        + "все его " + SESSION_KEY_BYTES + " байт шифруются ключом "
                        + recipient.publicKeyId() + ". Дорогой алгоритм тратится ОДИН раз и на то, "
                        + "что спокойно помещается в потолок из " + MAX_ASYMMETRIC_PAYLOAD
                        + " байт. Именно этот шаг и решает проблему распространения ключей: общий "
                        + "секрет создаётся на месте, а не согласовывается заранее",
                List.of("operation", "key:" + recipient.publicKeyId(), "key:" + sessionKeyId),
                state());

        long micros = symmetricCost(bytes);
        symmetricOps++;
        symmetricMicros += micros;
        String ciphertext = hex("hyb|" + sessionKeyId + "|" + label);
        Envelope envelope = new Envelope(channel.size() + 1, "hybrid", label, ciphertext,
                SYMMETRIC_ALGORITHM + " + " + ASYMMETRIC_ALGORITHM, recipient.privateKeyId(),
                wrapped, sessionKeyId, bytes);
        channel.add(envelope);
        encrypted++;
        opName = "encrypt";
        opFamily = "symmetric";
        opAlgorithm = SYMMETRIC_ALGORITHM;
        opKeyId = sessionKeyId;
        opKeyKind = "secret";
        opInput = label + " (" + bytes + " bytes)";
        opOutput = ciphertext;
        opMicros = micros;
        Trace.event("SYMMETRIC_ENCRYPT",
                "The payload itself — " + bytes + " byte(s) — goes under '" + sessionKeyId
                        + "' with " + SYMMETRIC_ALGORITHM + ", in about " + micros
                        + " microsecond(s). Same cheap bulk cipher as before; the size limit and "
                        + "the slowness of the public-key algorithm never touch the data",
                "Сама полезная нагрузка — " + bytes + " байт — шифруется ключом «" + sessionKeyId
                        + "» алгоритмом " + SYMMETRIC_ALGORITHM + " примерно за " + micros
                        + " микросекунд. Тот же дешёвый массовый шифр, что и раньше; ни ограничение "
                        + "на размер, ни медлительность алгоритма с открытым ключом данных не "
                        + "касаются",
                List.of("operation", "channel", "key:" + sessionKeyId, "cost"), state());

        decision = "ok";
        detail = label;
        Trace.event("HYBRID_SENT",
                "One envelope leaves with two parts: the wrapped key " + wrapped + " and the "
                        + "ciphertext " + ciphertext + ". This is what TLS, PGP, JWE, S/MIME and "
                        + "encrypted backups all do — asymmetric cryptography to establish a key, "
                        + "symmetric cryptography to carry the data. When an interviewer asks "
                        + "'which one does HTTPS use?', the answer is both, and this is the shape "
                        + "of it",
                "Уходит один конверт из двух частей: обёрнутый ключ " + wrapped + " и шифротекст "
                        + ciphertext + ". Ровно это делают TLS, PGP, JWE, S/MIME и шифрованные "
                        + "бэкапы: асимметричная криптография устанавливает ключ, симметричная везёт "
                        + "данные. Когда на собеседовании спрашивают, «какое шифрование использует "
                        + "HTTPS», ответ — оба, и вот его форма",
                List.of("channel", "operation"), state());
        return envelope;
    }

    /** Opens a hybrid envelope: one private-key operation, then ordinary bulk decryption. */
    public String openHybrid(KeyPair recipient, Envelope envelope) {
        beginStep();
        opName = "unwrap key";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = recipient.privateKeyId();
        opKeyKind = "private";
        opInput = envelope.wrappedKey;
        if (!recipient.privateKeyId().equals(envelope.keyId)) {
            failed++;
            opVerdict = "failed";
            decision = "failed";
            reason = "wrong-key";
            detail = recipient.privateKeyId();
            Trace.event("WRONG_KEY",
                    "The wrapped key was sealed to " + envelope.keyId + ", so "
                            + recipient.privateKeyId() + " cannot unwrap it — and without the "
                            + "session key the ciphertext is unreachable no matter how weak the "
                            + "payload is",
                    "Обёрнутый ключ запечатан на " + envelope.keyId + ", поэтому "
                            + recipient.privateKeyId() + " его не распакует, — а без ключа сессии "
                            + "шифротекст недостижим, какой бы слабой ни была нагрузка",
                    List.of("operation", "channel"), state());
            return null;
        }
        asymmetricOps++;
        asymmetricMicros += ASYMMETRIC_MICROS_PER_OP;
        opOutput = envelope.sessionKeyId;
        opMicros = ASYMMETRIC_MICROS_PER_OP;
        opVerdict = "ok";
        decision = "ok";
        detail = envelope.sessionKeyId;
        Trace.event("SESSION_KEY_UNWRAPPED",
                recipient.privateKeyId() + " performs ONE private-key operation and recovers '"
                        + envelope.sessionKeyId + "'. That is the entire asymmetric cost of the "
                        + "transfer, regardless of whether the payload is 20 bytes or 20 gigabytes "
                        + "— which is exactly why the hybrid design scales",
                recipient.privateKeyId() + " выполняет ОДНУ операцию приватным ключом и "
                        + "восстанавливает «" + envelope.sessionKeyId + "». Это вся асимметричная "
                        + "стоимость передачи, независимо от того, 20 байт нагрузка или 20 гигабайт, "
                        + "— именно поэтому гибридная схема масштабируется",
                List.of("operation", "key:" + recipient.privateKeyId(), "channel"), state());

        long micros = symmetricCost(envelope.bytes);
        decrypted++;
        symmetricOps++;
        symmetricMicros += micros;
        opName = "decrypt";
        opFamily = "symmetric";
        opAlgorithm = SYMMETRIC_ALGORITHM;
        opKeyId = envelope.sessionKeyId;
        opKeyKind = "secret";
        opInput = envelope.onTheWire;
        opOutput = envelope.label;
        opMicros = micros;
        Trace.event("SYMMETRIC_DECRYPT",
                "With '" + envelope.sessionKeyId + "' in hand the rest is ordinary symmetric "
                        + "decryption: " + envelope.bytes + " byte(s) in about " + micros
                        + " microsecond(s), giving back \"" + envelope.label + "\". Count the "
                        + "operations in this whole exchange — two asymmetric, two symmetric — and "
                        + "you have the cost model of every secure protocol you use",
                "С ключом «" + envelope.sessionKeyId + "» на руках остальное — обычная "
                        + "симметричная расшифровка: " + envelope.bytes + " байт примерно за "
                        + micros + " микросекунд, и получается «" + envelope.label + "». Посчитайте "
                        + "операции во всём обмене — две асимметричные, две симметричные — и вы "
                        + "получите модель стоимости любого защищённого протокола, которым "
                        + "пользуетесь",
                List.of("operation", "key:" + envelope.sessionKeyId, "channel", "cost"), state());
        return envelope.label;
    }

    // ---------------------------------------------------------------- key exposure

    /** The private half leaks. Everything ever addressed to that key opens at once. */
    public void leakPrivateKey(KeyPair pair) {
        beginStep();
        KeyRecord record = keys.get(pair.privateKeyId());
        record.compromised = true;
        record.holders.add(ATTACKER);
        attackerKeys.add(pair.privateKeyId());
        for (Envelope envelope : channel) {
            if (pair.privateKeyId().equals(envelope.keyId) && envelope.sessionKeyId != null) {
                attackerKeys.add(envelope.sessionKeyId);
            }
        }
        opName = "leak";
        opFamily = "asymmetric";
        opAlgorithm = ASYMMETRIC_ALGORITHM;
        opKeyId = pair.privateKeyId();
        opKeyKind = "private";
        opInput = pair.privateKeyId();
        opOutput = ATTACKER;
        opVerdict = "failed";
        decision = "exposed";
        reason = "private-key-leaked";
        detail = pair.privateKeyId();
        Trace.event("PRIVATE_KEY_LEAKED",
                pair.privateKeyId() + " leaks — a stolen backup, a readable config file, a "
                        + "departing admin. Publishing the OTHER half cost nothing; losing this one "
                        + "costs everything ever addressed to " + pair.owner + ", including old "
                        + "traffic somebody recorded, because every wrapped session key was sealed "
                        + "to this key. That asymmetry of consequences is why protocols moved to "
                        + "ephemeral key agreement",
                pair.privateKeyId() + " утекает — украденный бэкап, читаемый конфиг, ушедший "
                        + "администратор. Публикация ДРУГОЙ половины не стоила ничего; потеря этой "
                        + "стоит всего, что когда-либо адресовали " + pair.owner + ", включая "
                        + "записанный кем-то старый трафик, потому что каждый обёрнутый ключ сессии "
                        + "был запечатан на этот ключ. Из-за такой асимметрии последствий протоколы "
                        + "и перешли на эфемерное согласование ключей",
                List.of("keys", "key:" + pair.privateKeyId(), "attacker"), state());
    }

    /** Prints what the run added up to. */
    public void report() {
        beginStep();
        opName = "report";
        opFamily = "none";
        opVerdict = "ok";
        decision = "ok";
        Trace.event("CRYPTO_AUDIT",
                "After the run: symmetric operations " + symmetricOps + " (" + symmetricMicros
                        + " microseconds), asymmetric operations " + asymmetricOps + " ("
                        + asymmetricMicros + " microseconds), encrypted " + encrypted
                        + ", decrypted " + decrypted + ", rejected " + failed + ", signed " + signed
                        + ", verified " + verified + ", messages a third party could read "
                        + exposed + ". The shape to remember: many cheap symmetric operations on "
                        + "the data, a handful of expensive asymmetric ones on keys and signatures",
                "Итоги прогона: симметричных операций " + symmetricOps + " (" + symmetricMicros
                        + " микросекунд), асимметричных " + asymmetricOps + " (" + asymmetricMicros
                        + " микросекунд), зашифровано " + encrypted + ", расшифровано " + decrypted
                        + ", отклонено " + failed + ", подписано " + signed + ", проверено "
                        + verified + ", сообщений, доступных третьей стороне: " + exposed
                        + ". Форма, которую стоит запомнить: много дешёвых симметричных операций над "
                        + "данными и несколько дорогих асимметричных над ключами и подписями",
                List.of("cost"), state());
    }

    // ------------------------------------------------------------------ internals

    private void beginStep() {
        opName = "";
        opFamily = "none";
        opAlgorithm = "";
        opKeyId = "";
        opKeyKind = "";
        opInput = "";
        opOutput = "";
        opMicros = 0;
        opVerdict = "pending";
        decision = "pending";
        reason = null;
        detail = "";
    }

    private static long symmetricCost(int bytes) {
        return Math.max(1L, bytes / SYMMETRIC_BYTES_PER_MICRO);
    }

    /** Deterministic stand-in for real cryptographic output, so every run traces the same. */
    private static String hex(String material) {
        return Integer.toHexString(material.hashCode() & 0x7fffffff);
    }

    /** Who can turn this envelope back into its plaintext. */
    private List<String> readersOf(Envelope envelope) {
        List<String> readers = new ArrayList<>();
        if ("signature".equals(envelope.family) || "key-material".equals(envelope.family)) {
            readers.add("everyone");
            return readers;
        }
        KeyRecord record = keys.get(envelope.keyId);
        if (record != null) {
            readers.addAll(record.holders);
        }
        if (attackerKeys.contains(envelope.keyId) && !readers.contains(ATTACKER)) {
            readers.add(ATTACKER);
        }
        return readers;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();

        List<Object> keyList = new ArrayList<>();
        for (KeyRecord record : keys.values()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", record.id);
            v.put("kind", record.kind);
            v.put("algorithm", record.algorithm);
            v.put("bits", record.bits);
            v.put("owner", record.owner);
            v.put("holders", List.copyOf(record.holders));
            v.put("published", record.published);
            v.put("compromised", record.compromised);
            keyList.add(v);
        }
        s.put("keys", keyList);

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("name", opName);
        operation.put("family", opFamily);
        operation.put("algorithm", opAlgorithm);
        operation.put("keyId", opKeyId);
        operation.put("keyKind", opKeyKind);
        operation.put("input", opInput);
        operation.put("output", opOutput);
        operation.put("micros", opMicros);
        operation.put("verdict", opVerdict);
        s.put("operation", operation);

        List<Object> wire = new ArrayList<>();
        for (Envelope envelope : channel) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("seq", envelope.seq);
            v.put("family", envelope.family);
            v.put("label", envelope.label);
            v.put("onTheWire", envelope.onTheWire);
            v.put("algorithm", envelope.algorithm);
            v.put("keyId", envelope.keyId);
            v.put("wrappedKey", envelope.wrappedKey);
            v.put("bytes", envelope.bytes);
            v.put("intact", envelope.intact);
            v.put("readableBy", readersOf(envelope));
            wire.add(v);
        }
        s.put("channel", wire);

        Map<String, Object> attacker = new LinkedHashMap<>();
        attacker.put("holds", List.copyOf(attackerKeys));
        attacker.put("seen", channel.size());
        s.put("attacker", attacker);

        Map<String, Object> keyManagement = new LinkedHashMap<>();
        keyManagement.put("parties", parties);
        keyManagement.put("secretKeys", secretKeysNeeded);
        keyManagement.put("keyPairs", keyPairsNeeded);
        s.put("keyManagement", keyManagement);

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("label", comparisonLabel);
        comparison.put("bytes", comparisonBytes);
        comparison.put("symmetricMicros", comparisonSymmetricMicros);
        comparison.put("asymmetricMicros", comparisonAsymmetricMicros);
        comparison.put("asymmetricBlocks", comparisonBlocks);
        comparison.put("ratio", comparisonRatio);

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("symmetricOps", symmetricOps);
        cost.put("asymmetricOps", asymmetricOps);
        cost.put("symmetricMicros", symmetricMicros);
        cost.put("asymmetricMicros", asymmetricMicros);
        cost.put("comparison", comparison);
        s.put("cost", cost);

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("decision", decision);
        outcome.put("reason", reason);
        outcome.put("detail", detail);
        s.put("outcome", outcome);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("encrypted", encrypted);
        stats.put("decrypted", decrypted);
        stats.put("failed", failed);
        stats.put("signed", signed);
        stats.put("verified", verified);
        stats.put("exposed", exposed);
        s.put("stats", stats);
        return s;
    }
}
