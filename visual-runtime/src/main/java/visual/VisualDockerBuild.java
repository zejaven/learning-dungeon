package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A <em>teaching model</em> of packaging a Spring Boot application as a Docker
 * image: {@code docker build}, the layer cache, {@code docker push} and
 * {@code docker run}.
 *
 * <p>The model owns four things a real setup owns:
 * <ul>
 *   <li>a <b>build context</b> — the folder the CLI packs up and ships to the
 *       daemon, minus whatever {@code .dockerignore} excludes;</li>
 *   <li>a <b>Dockerfile</b> — an ordered list of instructions, each of which
 *       becomes one layer, optionally split into several build stages;</li>
 *   <li>the daemon's <b>build cache</b> — a layer is reused only when its own
 *       instruction AND every layer below it are unchanged, which is why
 *       instruction order decides build time;</li>
 *   <li>a <b>registry</b> — it stores layers, not images, so a push transfers
 *       only the layers it does not already have.</li>
 * </ul>
 *
 * <p>The distinction the model exists to make visible is the difference between
 * an image's <em>size</em> and its <em>churn</em>: a fat-jar {@code COPY} and a
 * layered-jar {@code COPY} produce the same 235 MB image, but after a one-line
 * code change the first one rebuilds and re-uploads 45 MB and the second one
 * about 1 MB. Sizes are deliberate round numbers in the range real Spring Boot
 * images live in; they are illustrative, not measured.
 *
 * <p>Every step emits a bilingual {@link Trace} event; the class is
 * intentionally dependency-free.
 */
public class VisualDockerBuild {

    /** Roughly how much each base image unpacks to, in MB. */
    private static final Map<String, Integer> BASE_SIZES = Map.of(
            "eclipse-temurin:21-jre", 190,
            "eclipse-temurin:21-jre-alpine", 170,
            "eclipse-temurin:21-jdk", 340,
            "maven:3.9-eclipse-temurin-21", 520,
            "gradle:8.7-jdk21", 540,
            "ubuntu:22.04", 80);

    /** Used for any base image the model has never heard of. */
    private static final int DEFAULT_BASE_MB = 200;

    /** The JVM's default {@code MaxRAMPercentage} when it sees a container limit. */
    private static final int DEFAULT_MAX_RAM_PERCENT = 25;

    /**
     * What a {@code COPY} brings into the image — that is, when its content
     * digest changes and the layer can no longer be taken from the cache.
     */
    public enum Content {
        /** The whole Spring Boot fat jar: changes on any code OR dependency change. */
        FAT_JAR,
        /** The dependency layer of a layered jar: changes only when a dependency changes. */
        DEPENDENCIES,
        /** Your own compiled classes and resources: changes on every code change. */
        APPLICATION,
        /** pom.xml / build.gradle: changes when the dependency list changes. */
        BUILD_FILE,
        /** The src/ tree: changes on every code change. */
        SOURCE_TREE,
        /** Content that never changes here, e.g. the spring-boot-loader classes. */
        STATIC
    }

    /** The folder {@code docker build .} packs up and sends to the daemon. */
    public static final class Context {

        private final Map<String, Integer> entries = new LinkedHashMap<>();

        private Context() {
        }

        public static Context of() {
            return new Context();
        }

        /** Adds one entry of the project folder with its size in MB. */
        public Context file(String path, int sizeMb) {
            entries.put(path, sizeMb);
            return this;
        }

        /** The context of a project that has already been built with Maven. */
        private static Context typicalProject() {
            return of().file("pom.xml", 0).file("src/", 1).file("target/app.jar", 45);
        }
    }

    // ------------------------------------------------------------- dockerfile

    /** One {@code FROM} block of the Dockerfile. */
    private static final class Stage {
        private final String name;
        private final String image;
        private final int baseMb;

        private Stage(String name, String image, int baseMb) {
            this.name = name;
            this.image = image;
            this.baseMb = baseMb;
        }
    }

    /** One Dockerfile line. Every line participates in the cache chain. */
    private static final class Instruction {
        private final int stage;
        private final String keyword;
        private final String text;
        private final String value;
        private final int sizeMb;
        private final Content content;
        /** Index of the stage a {@code COPY --from} reads, or -1. */
        private final int fromStage;
        private final boolean metadata;
        private final boolean base;

        private Instruction(int stage, String keyword, String text, String value, int sizeMb,
                            Content content, int fromStage, boolean metadata, boolean base) {
            this.stage = stage;
            this.keyword = keyword;
            this.text = text;
            this.value = value;
            this.sizeMb = sizeMb;
            this.content = content;
            this.fromStage = fromStage;
            this.metadata = metadata;
            this.base = base;
        }
    }

    /** The Dockerfile itself: stages and the instructions inside them. */
    public static final class Dockerfile {

        private final List<Stage> stages = new ArrayList<>();
        private final List<Instruction> instructions = new ArrayList<>();

        private Dockerfile() {
        }

        /** {@code FROM <image>} — a single-stage Dockerfile. */
        public static Dockerfile from(String image) {
            return new Dockerfile().open(image, "");
        }

        /** {@code FROM <image> AS <stageName>} — the first stage of a multi-stage build. */
        public static Dockerfile from(String image, String stageName) {
            return new Dockerfile().open(image, stageName);
        }

        /** Starts the next (unnamed) stage; only the LAST stage becomes the image. */
        public Dockerfile stage(String image) {
            return open(image, "");
        }

        /** Starts the next named stage. */
        public Dockerfile stage(String image, String stageName) {
            return open(image, stageName);
        }

        /** {@code WORKDIR} — metadata, adds no bytes. */
        public Dockerfile workdir(String dir) {
            return add(meta("WORKDIR", "WORKDIR " + dir, dir));
        }

        /** {@code COPY <source> <dest>} — a layer holding {@code sizeMb} of {@code content}. */
        public Dockerfile copy(String source, String dest, int sizeMb, Content content) {
            return add(new Instruction(last(), "COPY", "COPY " + source + " " + dest, "",
                    sizeMb, content, -1, false, false));
        }

        /** {@code COPY --from=<stage>} — takes the output of an earlier stage. */
        public Dockerfile copyFrom(String stageName, String source, String dest, int sizeMb) {
            int from = indexOf(stageName);
            return add(new Instruction(last(), "COPY",
                    "COPY --from=" + stageName + " " + source + " " + dest, "",
                    sizeMb, null, from, false, false));
        }

        /** {@code RUN <command>} — a layer holding whatever the command wrote. */
        public Dockerfile run(String command, int sizeMb) {
            return add(new Instruction(last(), "RUN", "RUN " + command, "",
                    sizeMb, null, -1, false, false));
        }

        /** {@code ENV} — metadata, adds no bytes. */
        public Dockerfile env(String name, String value) {
            return add(meta("ENV", "ENV " + name + "=" + value, name + "=" + value));
        }

        /** {@code EXPOSE} — documentation only; publishing a port is a run-time flag. */
        public Dockerfile expose(int port) {
            return add(meta("EXPOSE", "EXPOSE " + port, String.valueOf(port)));
        }

        /** {@code USER} — the account PID 1 runs as. Without it, that account is root. */
        public Dockerfile user(String user) {
            return add(meta("USER", "USER " + user, user));
        }

        /** {@code ENTRYPOINT} — the process the container starts, in exec form. */
        public Dockerfile entrypoint(String command) {
            return add(meta("ENTRYPOINT", "ENTRYPOINT " + execForm(command), command));
        }

        /** Renders a command line the way a Dockerfile's exec form spells it. */
        private static String execForm(String command) {
            StringBuilder sb = new StringBuilder("[");
            String[] words = command.split(" ");
            for (int i = 0; i < words.length; i++) {
                sb.append(i == 0 ? "" : ", ").append('"').append(words[i]).append('"');
            }
            return sb.append(']').toString();
        }

        private Dockerfile open(String image, String stageName) {
            stages.add(new Stage(stageName, image, baseSizeOf(image)));
            int index = stages.size() - 1;
            String text = "FROM " + image + (stageName.isEmpty() ? "" : " AS " + stageName);
            instructions.add(new Instruction(index, "FROM", text, image,
                    baseSizeOf(image), Content.STATIC, -1, false, true));
            return this;
        }

        private Instruction meta(String keyword, String text, String value) {
            return new Instruction(last(), keyword, text, value, 0, Content.STATIC, -1, true, false);
        }

        private Dockerfile add(Instruction instruction) {
            instructions.add(instruction);
            return this;
        }

        private int last() {
            return stages.size() - 1;
        }

        private int indexOf(String stageName) {
            for (int i = 0; i < stages.size(); i++) {
                if (stages.get(i).name.equals(stageName)) {
                    return i;
                }
            }
            throw new IllegalArgumentException("No stage named '" + stageName + "' in this Dockerfile");
        }

        private String metadataValue(String keyword) {
            String value = "";
            for (Instruction instruction : instructions) {
                if (instruction.stage == stages.size() - 1 && instruction.keyword.equals(keyword)) {
                    value = instruction.value;
                }
            }
            return value;
        }

        /** Reads an explicit {@code -XX:MaxRAMPercentage=N} out of the image's ENV, or 0. */
        private int maxRamPercent() {
            String flag = "MaxRAMPercentage=";
            int percent = 0;
            for (Instruction instruction : instructions) {
                if (instruction.stage != stages.size() - 1 || !"ENV".equals(instruction.keyword)) {
                    continue;
                }
                int at = instruction.value.indexOf(flag);
                if (at < 0) {
                    continue;
                }
                int start = at + flag.length();
                int end = start;
                while (end < instruction.value.length()
                        && Character.isDigit(instruction.value.charAt(end))) {
                    end++;
                }
                if (end > start) {
                    percent = Integer.parseInt(instruction.value.substring(start, end));
                }
            }
            return percent;
        }
    }

    private static int baseSizeOf(String image) {
        Integer size = BASE_SIZES.get(image);
        return size == null ? DEFAULT_BASE_MB : size;
    }

    // ------------------------------------------------------------ built image

    /** One layer of a built image, as the registry sees it. */
    private static final class Layer {
        private final String id;
        private final String key;
        private final String text;
        private final int sizeMb;
        private final boolean metadata;
        private final boolean cached;
        private final int stage;
        private final boolean inFinalImage;

        private Layer(String id, String key, String text, int sizeMb, boolean metadata,
                      boolean cached, int stage, boolean inFinalImage) {
            this.id = id;
            this.key = key;
            this.text = text;
            this.sizeMb = sizeMb;
            this.metadata = metadata;
            this.cached = cached;
            this.stage = stage;
            this.inFinalImage = inFinalImage;
        }
    }

    /** A finished image: the layers of its last stage plus its run-time metadata. */
    private static final class Image {
        private final String tag;
        private final List<Layer> layers = new ArrayList<>();
        private int sizeMb;
        private String user = "";
        private String port = "";
        private String entrypoint = "";
        /** An explicit MaxRAMPercentage baked into the image, or 0 for the JVM default. */
        private int maxRamPercent;

        private Image(String tag) {
            this.tag = tag;
        }
    }

    // --------------------------------------------------------------- daemon

    private final Context context;
    /** Paths excluded by .dockerignore, in the order they were listed. */
    private final Set<String> ignored = new LinkedHashSet<>();
    /** Cache-chain keys the daemon has already materialised as layers. */
    private final Set<String> cache = new LinkedHashSet<>();
    /** The key each instruction had last time it was built on the same parent layers. */
    private final Map<String, String> seen = new LinkedHashMap<>();
    /** Layer keys the registry already stores. */
    private final Set<String> registry = new LinkedHashSet<>();
    private final Map<String, Image> images = new LinkedHashMap<>();

    /** Bumped by editCode(): everything derived from src/ gets a new digest. */
    private int codeRev = 1;
    /** Bumped by addDependency(): everything derived from pom.xml gets a new digest. */
    private int depRev = 1;

    private String tag = "";
    private List<Layer> layers = new ArrayList<>();
    private List<String> stageNames = new ArrayList<>();
    private Image image;
    private int uploadedMb;
    private int skippedMb;
    private boolean pushed;
    private String containerTag = "";
    private String containerUser = "";
    private int containerMemoryMb;
    private int containerHeapMb;
    private int containerHeapPercent;

    private int builds;
    private int layersBuilt;
    private int layersCached;
    private int contextSentMb;
    private int contextSavedMb;
    private int totalUploadedMb;
    private int totalSkippedMb;

    private VisualDockerBuild(Context context) {
        this.context = context;
    }

    /** A daemon with an empty cache and a typical already-built Maven project. */
    public static VisualDockerBuild daemon() {
        return daemon(Context.typicalProject());
    }

    /** A daemon whose project folder is exactly the given context. */
    public static VisualDockerBuild daemon(Context context) {
        VisualDockerBuild docker = new VisualDockerBuild(context);
        Trace.event("DAEMON_READY",
                "A machine with Docker installed, an empty build cache and a project folder of "
                        + docker.contextTotalMb() + " MB (" + docker.contextEn()
                        + "). Nothing is built yet",
                "Машина с установленным Docker, пустым кешем сборки и папкой проекта на "
                        + docker.contextTotalMb() + " МБ (" + docker.contextRu()
                        + "). Пока ничего не собрано",
                List.of("context"), docker.state());
        return docker;
    }

    /** Lists the paths a .dockerignore keeps out of the build context. */
    public void dockerignore(String... paths) {
        for (String path : paths) {
            ignored.add(path);
        }
        Trace.event("DOCKERIGNORE_ADDED",
                "A .dockerignore file now excludes " + String.join(", ", ignored)
                        + ". It changes nothing about the image — it changes what the CLI has to "
                        + "send to the daemon before the build can even start",
                "Файл .dockerignore теперь исключает " + String.join(", ", ignored)
                        + ". На сам образ это не влияет — влияет на то, что CLI обязан отправить "
                        + "демону, прежде чем сборка вообще начнётся",
                List.of("context"), state());
    }

    /** A developer edits a class: every layer derived from src/ gets a new digest. */
    public void editCode(String file) {
        codeRev++;
        Trace.event("CODE_CHANGED",
                "A developer changes one line in " + file + " and rebuilds the jar. One line — but "
                        + "every Docker layer whose content is derived from your sources now has a "
                        + "different digest",
                "Разработчик меняет одну строку в " + file + " и пересобирает jar. Одна строка — но "
                        + "у каждого слоя Docker, содержимое которого получено из ваших исходников, "
                        + "теперь другой дайджест",
                List.of("code"), state());
    }

    /** A new dependency is added to pom.xml: the dependency layers change too. */
    public void addDependency(String coordinates) {
        depRev++;
        Trace.event("DEPENDENCY_ADDED",
                coordinates + " is added to pom.xml. This is the rarer kind of change: it moves the "
                        + "dependency content as well as your own code",
                coordinates + " добавлена в pom.xml. Это более редкий вид изменения: он затрагивает "
                        + "не только ваш код, но и содержимое зависимостей",
                List.of("deps"), state());
    }

    /** Runs {@code docker build -t <tag> .} for this Dockerfile. */
    public void build(String tag, Dockerfile dockerfile) {
        builds++;
        this.tag = tag;
        this.layers = new ArrayList<>();
        this.image = null;
        this.pushed = false;
        this.stageNames = stageNames(dockerfile);

        int sent = sentContextMb();
        int excluded = contextTotalMb() - sent;
        contextSentMb += sent;
        contextSavedMb += excluded;
        Trace.event("BUILD_STARTED",
                "docker build -t " + tag + " . — the CLI packs the build context (" + sent
                        + " MB) and sends it to the daemon, which then reads the Dockerfile from top "
                        + "to bottom. Each instruction produces one layer, stacked on the one before it",
                "docker build -t " + tag + " . — CLI упаковывает контекст сборки (" + sent
                        + " МБ) и отправляет его демону, а тот читает Dockerfile сверху вниз. Каждая "
                        + "инструкция создаёт один слой, лежащий поверх предыдущего",
                List.of("context"), state());

        if (excluded > 0) {
            Trace.event("CONTEXT_IGNORED",
                    ".dockerignore kept " + excluded + " MB out of the context (" + ignoredEn()
                            + "), so " + sent + " MB is uploaded to the daemon instead of "
                            + contextTotalMb() + " MB. It also protects the cache: a COPY of the "
                            + "whole folder is invalidated by any file you touched, including files "
                            + "the image never needed",
                    ".dockerignore не пустил в контекст " + excluded + " МБ (" + ignoredRu()
                            + "), поэтому демону уходит " + sent + " МБ вместо " + contextTotalMb()
                            + " МБ. Заодно это бережёт кеш: COPY всей папки сбрасывается из-за любого "
                            + "изменённого файла, даже такого, который образу вообще не нужен",
                    List.of("context"), state());
        }

        Map<Integer, String> chains = new LinkedHashMap<>();
        boolean anyHit = false;
        boolean breakReported = false;
        int number = 0;

        for (Instruction instruction : dockerfile.instructions) {
            number++;
            String key = keyOf(instruction, chains);
            String parent = chains.getOrDefault(instruction.stage, "");
            String chain = parent + ">" + key;
            chains.put(instruction.stage, chain);

            // Same instruction, same parent layers, different key => its content
            // moved. A key we have simply never seen is a new layer, not a break.
            String previous = seen.put(parent + ">" + instruction.text, key);
            boolean changed = previous != null && !previous.equals(key);
            boolean cached = !this.cache.add(chain);
            Layer layer = new Layer("L" + number, chain, instruction.text, instruction.sizeMb,
                    instruction.metadata, cached, instruction.stage,
                    instruction.stage == dockerfile.stages.size() - 1);
            layers.add(layer);

            if (!instruction.metadata) {
                if (cached) {
                    layersCached++;
                    anyHit = true;
                } else {
                    layersBuilt++;
                }
            }

            if (instruction.base) {
                announceBase(dockerfile.stages.get(instruction.stage), instruction, cached, layer);
                continue;
            }
            if (instruction.metadata) {
                continue;
            }
            if (!cached && changed && anyHit && !breakReported) {
                breakReported = true;
                announceCacheBreak(instruction, layer);
            }
            announceLayer(instruction, cached, layer);
        }

        assemble(tag, dockerfile);
    }

    /** Runs {@code docker push <tag>} against a registry that stores layers. */
    public void push(String tag) {
        Image img = imageOf(tag);
        this.tag = tag;
        this.image = img;
        this.layers = img.layers;
        int uploaded = 0;
        int skipped = 0;
        int uploadedLayers = 0;
        int skippedLayers = 0;
        List<String> changed = new ArrayList<>();
        for (Layer layer : img.layers) {
            if (layer.metadata) {
                continue;
            }
            if (registry.add(layer.key)) {
                uploaded += layer.sizeMb;
                uploadedLayers++;
                changed.add(layer.text);
            } else {
                skipped += layer.sizeMb;
                skippedLayers++;
            }
        }
        this.uploadedMb = uploaded;
        this.skippedMb = skipped;
        this.pushed = true;
        totalUploadedMb += uploaded;
        totalSkippedMb += skipped;

        Trace.event("IMAGE_PUSHED",
                "docker push " + tag + " — uploaded " + uploaded + " MB in " + uploadedLayers
                        + " layer(s); " + skipped + " MB in " + skippedLayers + " layer(s) was "
                        + "already in the registry and was skipped. Registries and cluster nodes "
                        + "transfer LAYERS, not images: the cost of a deploy is what changed"
                        + (changed.isEmpty() ? "" : ", here " + String.join(" + ", changed)),
                "docker push " + tag + " — загружено " + uploaded + " МБ в слоях (" + uploadedLayers
                        + "); " + skipped + " МБ в слоях (" + skippedLayers + ") уже были в реестре "
                        + "и пропущены. Реестры и узлы кластера передают СЛОИ, а не образы: деплой "
                        + "стоит ровно столько, сколько изменилось"
                        + (changed.isEmpty() ? "" : ", здесь это " + String.join(" + ", changed)),
                List.of("registry"), state());
    }

    /** Runs {@code docker run} with a memory limit, the way an orchestrator would. */
    public void run(String tag, int memoryLimitMb) {
        Image img = imageOf(tag);
        this.tag = tag;
        this.image = img;
        this.layers = img.layers;
        containerTag = tag;
        containerUser = img.user.isEmpty() ? "root" : img.user;
        containerMemoryMb = memoryLimitMb;
        containerHeapPercent = img.maxRamPercent > 0 ? img.maxRamPercent : DEFAULT_MAX_RAM_PERCENT;
        containerHeapMb = memoryLimitMb * containerHeapPercent / 100;

        String ports = img.port.isEmpty() ? "" : " -p " + img.port + ":" + img.port;
        String heapEn = img.maxRamPercent > 0
                ? "Since Java 10 the JVM reads the container's memory limit, and this image sets "
                  + "MaxRAMPercentage=" + img.maxRamPercent + ", so it takes about " + containerHeapMb
                  + " MB of heap out of the " + memoryLimitMb + " MB limit and leaves the rest for "
                  + "metaspace, thread stacks and native memory"
                : "Since Java 10 the JVM reads the container's memory limit, and the default "
                  + "MaxRAMPercentage of " + DEFAULT_MAX_RAM_PERCENT + "% leaves it about "
                  + containerHeapMb + " MB of heap out of " + memoryLimitMb + " MB — raise it to "
                  + "70-75% or most of the limit goes unused";
        String heapRu = img.maxRamPercent > 0
                ? "Начиная с Java 10 JVM читает лимит памяти контейнера, а в этом образе задан "
                  + "MaxRAMPercentage=" + img.maxRamPercent + ", поэтому она берёт примерно "
                  + containerHeapMb + " МБ кучи из лимита в " + memoryLimitMb + " МБ, а остальное "
                  + "остаётся под metaspace, стеки потоков и нативную память"
                : "Начиная с Java 10 JVM читает лимит памяти контейнера, и значение "
                  + "MaxRAMPercentage по умолчанию (" + DEFAULT_MAX_RAM_PERCENT + "%) оставляет ей "
                  + "примерно " + containerHeapMb + " МБ кучи из " + memoryLimitMb + " МБ — поднимите "
                  + "его до 70-75%, иначе большая часть лимита простаивает";

        Trace.event("CONTAINER_STARTED",
                "docker run -m " + memoryLimitMb + "m" + ports + " " + tag + " — the container adds a "
                        + "thin writable layer on top of the image's " + img.sizeMb + " MB and starts "
                        + "ONE process as PID 1: " + img.entrypoint + ", running as " + containerUser
                        + ". " + heapEn,
                "docker run -m " + memoryLimitMb + "m" + ports + " " + tag + " — контейнер добавляет "
                        + "тонкий записываемый слой поверх " + img.sizeMb + " МБ образа и запускает "
                        + "ОДИН процесс как PID 1: " + img.entrypoint + ", от имени " + containerUser
                        + ". " + heapRu,
                List.of("container"), state());

        if (img.user.isEmpty()) {
            Trace.event("RUNS_AS_ROOT",
                    "The image has no USER instruction, so that process is root — and root inside the "
                            + "container is uid 0 on the kernel the container shares with the host. "
                            + "Creating a user and adding one USER line is the cheapest hardening "
                            + "there is; many clusters refuse to schedule the image without it",
                    "В образе нет инструкции USER, поэтому этот процесс — root, а root внутри "
                            + "контейнера — это uid 0 на ядре, которое контейнер делит с хостом. "
                            + "Создать пользователя и добавить одну строку USER — самая дешёвая "
                            + "защита из возможных; многие кластеры без этого просто не запустят образ",
                    List.of("container"), state());
        }
    }

    /** Prints what the whole session cost. */
    public void report() {
        Trace.event("BUILD_AUDIT",
                "After " + builds + " build(s): layers rebuilt: " + layersBuilt + ", layers taken "
                        + "from the cache: " + layersCached + ", build context sent: " + contextSentMb
                        + " MB (kept out by .dockerignore: " + contextSavedMb + " MB), pushed to the "
                        + "registry: " + totalUploadedMb + " MB (skipped as already present: "
                        + totalSkippedMb + " MB)",
                "После сборок (" + builds + "): пересобрано слоёв: " + layersBuilt + ", взято из "
                        + "кеша: " + layersCached + ", отправлено контекста: " + contextSentMb
                        + " МБ (не пущено .dockerignore: " + contextSavedMb + " МБ), загружено в "
                        + "реестр: " + totalUploadedMb + " МБ (пропущено как уже имеющееся: "
                        + totalSkippedMb + " МБ)",
                List.of(), state());
    }

    // ---------------------------------------------------------------- events

    private void announceBase(Stage stage, Instruction instruction, boolean cached, Layer layer) {
        String where = stage.name.isEmpty() ? "" : " AS " + stage.name;
        if (cached) {
            Trace.event("BASE_IMAGE",
                    instruction.text + " — the base image is already on this machine, so nothing is "
                            + "downloaded and its " + stage.baseMb + " MB is shared with every other "
                            + "image built on it",
                    instruction.text + " — базовый образ уже есть на этой машине, поэтому ничего не "
                            + "скачивается, а его " + stage.baseMb + " МБ разделяются со всеми "
                            + "другими образами на той же базе",
                    List.of("layer:" + layer.id), state());
            return;
        }
        Trace.event("BASE_IMAGE",
                "FROM " + stage.image + where + " — your image starts as somebody else's image: a "
                        + "Linux userland plus a Java runtime, about " + stage.baseMb + " MB unpacked. "
                        + "It is pulled once and then shared, which is why the base image you pick "
                        + "matters more for the pull than for the push",
                "FROM " + stage.image + where + " — ваш образ начинается с чужого образа: окружение "
                        + "Linux плюс среда выполнения Java, примерно " + stage.baseMb
                        + " МБ в распакованном виде. Он скачивается один раз и потом переиспользуется "
                        + "— поэтому выбор базового образа важнее для скачивания, чем для отправки",
                List.of("layer:" + layer.id), state());
    }

    private void announceLayer(Instruction instruction, boolean cached, Layer layer) {
        if (cached) {
            Trace.event("LAYER_CACHED",
                    instruction.text + " — CACHED. Neither the instruction nor the content it copies "
                            + "changed, and every layer below it was a hit too, so the daemon reuses "
                            + "the existing layer instead of writing " + size(instruction.sizeMb)
                            + " MB again",
                    instruction.text + " — ИЗ КЕША. Ни инструкция, ни копируемое ею содержимое не "
                            + "изменились, и все слои под ней тоже попали в кеш, поэтому демон "
                            + "переиспользует готовый слой, а не пишет " + size(instruction.sizeMb)
                            + " МБ заново",
                    List.of("layer:" + layer.id), state());
            return;
        }
        Trace.event("LAYER_BUILT",
                instruction.text + " — a new layer of " + size(instruction.sizeMb) + " MB. Its cache "
                        + "key is the instruction text plus a digest of what it brings in"
                        + contentHintEn(instruction.content)
                        + ", so it will be reused only while both stay identical",
                instruction.text + " — новый слой на " + size(instruction.sizeMb) + " МБ. Его ключ "
                        + "кеша — это текст инструкции плюс дайджест того, что она вносит"
                        + contentHintRu(instruction.content)
                        + ", поэтому он переиспользуется, только пока оба неизменны",
                List.of("layer:" + layer.id), state());
    }

    private void announceCacheBreak(Instruction instruction, Layer layer) {
        Trace.event("CACHE_INVALIDATED",
                "Cache broken at " + instruction.text + contentHintEn(instruction.content)
                        + ", and its digest is no longer the one in the cache. Docker cannot reuse "
                        + "this layer — and since every later layer is built ON this one, everything "
                        + "below this line is rebuilt too, cache hit or not. This is the whole reason "
                        + "instruction ORDER decides your build time: put what rarely changes first",
                "Кеш сброшен на " + instruction.text + contentHintRu(instruction.content)
                        + ", и её дайджест уже не тот, что в кеше. Docker не может переиспользовать "
                        + "этот слой — а так как все последующие слои строятся ПОВЕРХ него, всё, что "
                        + "ниже этой строки, тоже пересобирается, независимо от кеша. Именно поэтому "
                        + "ПОРЯДОК инструкций определяет время сборки: то, что меняется редко, — выше",
                List.of("layer:" + layer.id), state());
    }

    private void assemble(String tag, Dockerfile dockerfile) {
        int finalStage = dockerfile.stages.size() - 1;
        Image img = new Image(tag);
        int size = 0;
        int fileLayers = 0;
        List<String> metadata = new ArrayList<>();
        for (Layer layer : layers) {
            if (layer.stage != finalStage) {
                continue;
            }
            img.layers.add(layer);
            size += layer.sizeMb;
            if (layer.metadata) {
                metadata.add(layer.text);
            } else {
                fileLayers++;
            }
        }
        img.sizeMb = size;
        img.user = dockerfile.metadataValue("USER");
        img.port = dockerfile.metadataValue("EXPOSE");
        img.entrypoint = dockerfile.metadataValue("ENTRYPOINT");
        img.maxRamPercent = dockerfile.maxRamPercent();
        images.put(tag, img);
        image = img;

        if (dockerfile.stages.size() > 1) {
            announceDiscardedStages(dockerfile, finalStage);
        }

        Trace.event("IMAGE_BUILT",
                "Image " + tag + " built: " + size + " MB in " + fileLayers + " filesystem layer(s)"
                        + (metadata.isEmpty() ? "" : ", plus metadata that costs no bytes ("
                            + String.join(", ", metadata) + ")")
                        + ". An image is just those layers stacked and a bit of JSON; the tag is a "
                        + "label pointing at them, which is why re-tagging copies nothing",
                "Образ " + tag + " собран: " + size + " МБ в слоях файловой системы (" + fileLayers
                        + ")" + (metadata.isEmpty() ? "" : ", плюс метаданные, не занимающие байтов ("
                            + String.join(", ", metadata) + ")")
                        + ". Образ — это просто стопка слоёв и немного JSON; тег — лишь ярлык, "
                        + "указывающий на них, поэтому переименование тега ничего не копирует",
                List.of("image"), state());
    }

    private void announceDiscardedStages(Dockerfile dockerfile, int finalStage) {
        int discarded = 0;
        List<String> names = new ArrayList<>();
        for (Layer layer : layers) {
            if (layer.stage != finalStage) {
                discarded += layer.sizeMb;
                String name = stageNames.get(layer.stage);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        if (discarded == 0) {
            return;
        }
        Trace.event("STAGE_DISCARDED",
                "Stage " + String.join(", ", names) + " is thrown away — " + discarded + " MB of "
                        + "Maven, a full JDK, a warm ~/.m2 and your sources. Only the LAST stage "
                        + "becomes the image, so the build tooling and the source code never reach "
                        + "production, and the produced jar is the only thing that crosses over",
                "Этап " + String.join(", ", names) + " выбрасывается — " + discarded + " МБ: Maven, "
                        + "полный JDK, прогретый ~/.m2 и ваши исходники. Образом становится только "
                        + "ПОСЛЕДНИЙ этап, поэтому инструменты сборки и исходный код не попадают в "
                        + "продакшен, а переходит туда единственная вещь — собранный jar",
                List.of("stage"), state());
    }

    // ------------------------------------------------------------- internals

    /** The cache key of one instruction: its text plus the digest of its content. */
    private String keyOf(Instruction instruction, Map<Integer, String> chains) {
        if (instruction.fromStage >= 0) {
            // A COPY --from reads another stage's output, so it depends on that
            // stage's whole chain: rebuild the stage and this layer must change.
            return instruction.text + "|" + chains.getOrDefault(instruction.fromStage, "");
        }
        return instruction.text + "|" + digest(instruction.content);
    }

    /** What the content of a layer hashes to right now. */
    private String digest(Content content) {
        if (content == null) {
            return "-";
        }
        return switch (content) {
            case FAT_JAR -> "code" + codeRev + "+deps" + depRev;
            case DEPENDENCIES, BUILD_FILE -> "deps" + depRev;
            case APPLICATION, SOURCE_TREE -> "code" + codeRev;
            case STATIC -> "static";
        };
    }

    private String contentHintEn(Content content) {
        if (content == null) {
            return "";
        }
        return switch (content) {
            case FAT_JAR -> " (the whole fat jar, which holds your classes AND every dependency)";
            case DEPENDENCIES -> " (the dependency jars, which change only when pom.xml does)";
            case APPLICATION -> " (your own classes and resources)";
            case BUILD_FILE -> " (pom.xml, which changes only when the dependency list does)";
            case SOURCE_TREE -> " (the src/ tree)";
            case STATIC -> "";
        };
    }

    private String contentHintRu(Content content) {
        if (content == null) {
            return "";
        }
        return switch (content) {
            case FAT_JAR -> " (целый fat jar, в котором лежат и ваши классы, И все зависимости)";
            case DEPENDENCIES -> " (jar-файлы зависимостей, меняются только вместе с pom.xml)";
            case APPLICATION -> " (ваши собственные классы и ресурсы)";
            case BUILD_FILE -> " (pom.xml, меняется только вместе со списком зависимостей)";
            case SOURCE_TREE -> " (дерево src/)";
            case STATIC -> "";
        };
    }

    private Image imageOf(String tag) {
        Image img = images.get(tag);
        if (img == null) {
            throw new IllegalArgumentException("No image tagged '" + tag + "' — build it first");
        }
        return img;
    }

    private List<String> stageNames(Dockerfile dockerfile) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < dockerfile.stages.size(); i++) {
            String name = dockerfile.stages.get(i).name;
            names.add(name.isEmpty() ? "stage " + (i + 1) : name);
        }
        return names;
    }

    private boolean isIgnored(String path) {
        for (String pattern : ignored) {
            String prefix = pattern.endsWith("/") ? pattern : pattern + "/";
            if (path.equals(pattern) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private int contextTotalMb() {
        int total = 0;
        for (int size : context.entries.values()) {
            total += size;
        }
        return total;
    }

    private int sentContextMb() {
        int total = 0;
        for (Map.Entry<String, Integer> entry : context.entries.entrySet()) {
            if (!isIgnored(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private String contextEn() {
        return describeContext(" MB");
    }

    private String contextRu() {
        return describeContext(" МБ");
    }

    private String describeContext(String unit) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : context.entries.entrySet()) {
            parts.add(entry.getKey() + " " + size(entry.getValue()) + unit);
        }
        return String.join(", ", parts);
    }

    /** Sizes are whole MB, so anything smaller is shown as "&lt;1" rather than "0". */
    private static String size(int sizeMb) {
        return sizeMb == 0 ? "<1" : String.valueOf(sizeMb);
    }

    private String ignoredEn() {
        return describeIgnored(" MB");
    }

    private String ignoredRu() {
        return describeIgnored(" МБ");
    }

    private String describeIgnored(String unit) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : context.entries.entrySet()) {
            if (isIgnored(entry.getKey())) {
                parts.add(entry.getKey() + " " + size(entry.getValue()) + unit);
            }
        }
        return String.join(", ", parts);
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("tag", tag);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("totalMb", contextTotalMb());
        ctx.put("sentMb", sentContextMb());
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : context.entries.entrySet()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("path", entry.getKey());
            e.put("sizeMb", entry.getValue());
            e.put("ignored", isIgnored(entry.getKey()));
            entries.add(e);
        }
        ctx.put("entries", entries);
        s.put("context", ctx);

        s.put("stages", new ArrayList<>(stageNames));

        List<Object> layerList = new ArrayList<>();
        for (Layer layer : layers) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", layer.id);
            l.put("instruction", layer.text);
            l.put("sizeMb", layer.sizeMb);
            l.put("status", layer.cached ? "cached" : "built");
            l.put("metadata", layer.metadata);
            l.put("stage", layer.stage < stageNames.size() ? stageNames.get(layer.stage) : "");
            l.put("inFinalImage", layer.inFinalImage);
            layerList.add(l);
        }
        s.put("layers", layerList);

        if (image == null) {
            s.put("image", null);
        } else {
            Map<String, Object> img = new LinkedHashMap<>();
            img.put("tag", image.tag);
            img.put("sizeMb", image.sizeMb);
            img.put("user", image.user.isEmpty() ? "root" : image.user);
            img.put("entrypoint", image.entrypoint);
            s.put("image", img);
        }

        Map<String, Object> push = new LinkedHashMap<>();
        push.put("pushed", pushed);
        push.put("uploadedMb", uploadedMb);
        push.put("skippedMb", skippedMb);
        s.put("push", push);

        if (containerTag.isEmpty()) {
            s.put("container", null);
        } else {
            Map<String, Object> container = new LinkedHashMap<>();
            container.put("tag", containerTag);
            container.put("user", containerUser);
            container.put("memoryLimitMb", containerMemoryMb);
            container.put("heapMb", containerHeapMb);
            container.put("heapPercent", containerHeapPercent);
            s.put("container", container);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("builds", builds);
        stats.put("layersBuilt", layersBuilt);
        stats.put("layersCached", layersCached);
        stats.put("contextSentMb", contextSentMb);
        stats.put("contextSavedMb", contextSavedMb);
        stats.put("uploadedMb", totalUploadedMb);
        stats.put("skippedMb", totalSkippedMb);
        s.put("stats", stats);
        return s;
    }
}
