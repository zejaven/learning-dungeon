package com.interviewlearning.system;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Keeps Windows from sleeping while AI work is running. Overnight bulk
 * generation dies mid-run when the machine hits its idle sleep timer —
 * background CPU/network activity does not inhibit sleep, only an explicit
 * {@code SetThreadExecutionState(ES_SYSTEM_REQUIRED)} request does.
 *
 * <p>The JVM has no direct access to that Win32 call, so while at least one AI
 * run is active this service holds a small child PowerShell process that
 * re-asserts {@code ES_CONTINUOUS | ES_SYSTEM_REQUIRED} every 30 seconds. When
 * the process is killed, the OS clears the request automatically, so a backend
 * crash can never leave the machine unable to sleep.
 *
 * <p>Usage is a refcount: {@code acquire()} when a run starts, {@code release()}
 * when it ends. The child lingers a short while after the count reaches zero so
 * back-to-back bulk items don't restart it between every topic. Display sleep
 * is intentionally not blocked — the screen may turn off, only the system stays
 * up. No-op on non-Windows platforms.
 */
@Service
public class KeepAwakeService {

    private static final Logger log = LoggerFactory.getLogger(KeepAwakeService.class);

    /** ES_CONTINUOUS (0x80000000) | ES_SYSTEM_REQUIRED (0x00000001). */
    private static final long ES_FLAGS = 0x80000003L;
    private static final long LINGER_SECONDS = 90;

    private static final String SCRIPT =
            "Add-Type -Namespace KA -Name Native -MemberDefinition "
                    + "'[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint esFlags);'; "
                    + "while ($true) { [void][KA.Native]::SetThreadExecutionState(" + ES_FLAGS + "); "
                    + "Start-Sleep -Seconds 30 }";

    private final boolean windows =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "keep-awake");
                t.setDaemon(true);
                return t;
            });

    private int active = 0;
    private Process holder;
    private ScheduledFuture<?> pendingStop;

    public synchronized void acquire() {
        if (!windows) {
            return;
        }
        active++;
        if (pendingStop != null) {
            pendingStop.cancel(false);
            pendingStop = null;
        }
        if (holder == null || !holder.isAlive()) {
            start();
        }
    }

    public synchronized void release() {
        if (!windows) {
            return;
        }
        active = Math.max(0, active - 1);
        if (active == 0 && holder != null && pendingStop == null) {
            // Linger so consecutive bulk items reuse the same holder process.
            pendingStop = scheduler.schedule(this::stopIfIdle, LINGER_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void start() {
        String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-EncodedCommand", encoded);
        try {
            holder = pb.start();
            log.info("Keep-awake enabled: system sleep is inhibited while AI work runs");
        } catch (IOException e) {
            holder = null;
            log.warn("Could not start keep-awake helper (the PC may sleep mid-generation): {}", e.getMessage());
        }
    }

    private synchronized void stopIfIdle() {
        pendingStop = null;
        if (active == 0 && holder != null) {
            holder.destroy();
            holder = null;
            log.info("Keep-awake released: system may sleep normally again");
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        if (holder != null) {
            holder.destroy();
            holder = null;
        }
        scheduler.shutdownNow();
    }
}
