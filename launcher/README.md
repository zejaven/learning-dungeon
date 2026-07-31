# Launcher (run as a Windows app from a tray icon)

This folder packages the app so it starts from a normal Windows shortcut, runs
in the background, and lives in the system tray instead of a console window.
There is no app window: the UI is the browser, the tray icon is the process.

It runs a **single process** — the backend `bootJar`, which also serves the
built frontend (bundled inside the jar) on `http://localhost:18080`. So there is
one port and one process to manage, not the two dev servers `dev.ps1` starts.

## One-time setup

```powershell
launcher\build-app.ps1        # build visual-runtime jar, frontend, backend jar, icon
launcher\install-shortcut.ps1 # create Desktop + Start-menu shortcuts
```

Then launch from the **Java Interview Dungeon** icon (Desktop or Start menu).
A tray icon appears, the browser opens at `http://localhost:18080`, and:

- **Open** / double-click — open the app in the browser again.
- **Open log** — open `launcher\app.log` (backend output).
- **Exit** — stop the backend and remove the tray icon.

## Requirements

- JDK 21 on `PATH` or via `JAVA_HOME` (the launcher uses `javaw.exe`).
- A running local PostgreSQL (same as the dev setup) — progress persistence
  connects on startup. If it is down, the backend exits during startup and the
  launcher points you at `app.log`.

## After changing code

Re-run `launcher\build-app.ps1` to rebuild the jar and/or frontend bundle.
The shortcuts and icon do not need reinstalling.

## How it fits together

- `build-app.ps1` — builds `visual-runtime` jar, `frontend\dist`, `backend` jar.
- `tray.ps1` — starts `javaw -jar` (working dir = repo root, logs to
  `app.log`), waits for `:18080`, opens the browser, shows the tray icon, and
  tree-kills the JVM on Exit.
- `launch.vbs` — runs `tray.ps1` with no console flash.
- `install-shortcut.ps1` — shortcuts.

`build-app.ps1` runs `npm run build`, and `bootJar` (see `backend/build.gradle`)
copies `frontend/dist` into the jar under `static/`, so the UI ships inside the
jar — no `frontend/dist` folder needed at runtime. The app's router is
hash-based, so no server-side SPA routing is needed.

`app.log` is a generated and git-ignored artifact.

## Sharing it (without source code)

The UI is bundled in the jar, but the backend still reads a few things from
disk relative to the repo root. Copy this folder layout to the other machine:

```
<shared-folder>/
  topics/                            (all topic content)
  config/secret.yml                  (their local DB credentials)
  visual-runtime/build/libs/visual-runtime-*.jar
  launcher/
    backend-0.0.1.jar                (put the jar here)
    tray.ps1
    launch.vbs
    install-shortcut.ps1
    create-db.ps1
    icon.ico
```

On the other machine, before the first launch:

```powershell
# 1. write config\secret.yml with their PostgreSQL credentials (see above)
# 2. create the database (the app does not create it itself):
launcher\create-db.ps1
# 3. install the shortcuts:
launcher\install-shortcut.ps1
```

`frontend/`, source code, and other modules are not needed. `tray.ps1` finds
the jar whether it sits in `launcher/`, in `backend/build/libs/`, or at the
shared-folder root, so the exact spot is flexible — but next to the scripts is
simplest.

They also need: JDK 21 (not just a JRE — the code runner compiles Java),
a local PostgreSQL with a `java-interview-dungeon` database, and the Claude
Code CLI on PATH if they want topic generation / the AI assistant.

Then on their machine: `launcher\install-shortcut.ps1`, and launch from the
icon.
