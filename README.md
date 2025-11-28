# Rogue's Den DreamBot Script (Cross-Platform, No-Prompt Install)

This guide provides fully automated, no-prompt build/install steps for Windows, macOS, and Linux using the included Maven Wrapper and turnkey install scripts.

## Prerequisites

- **Java 8 or newer**: Install an LTS JDK (Adoptium/Oracle). Verify with `java -version`.
- **DreamBot client**: Create a free account at [dreambot.org](https://dreambot.org/) and run the launcher once to create your `DreamBot` directory.
- **Git (optional)**: Only needed if you prefer cloning over downloading the ZIP.

## Get the Project Files

### Option 1: Download ZIP (no tools required)
1. Visit the repository page in your browser.
2. Click **Code ▾ → Download ZIP**.
3. Extract the archive to a convenient folder.

### Option 2: Clone with Git
```bash
git clone https://github.com/your-username/rogues-den.git
cd rogues-den
```

## One-Command Install (Headless/No-Prompt)

We ship Maven Wrapper scripts, so you do **not** need a global Maven install. The wrapper downloads Maven automatically in batch (`-B`) mode.

**Windows (PowerShell):**
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
./scripts/install.ps1
```

**macOS/Linux (Bash):**
```bash
chmod +x scripts/install.sh  # if needed
./scripts/install.sh
```

- The install scripts build the fat JAR (`roguesden-1.0-SNAPSHOT-jar-with-dependencies.jar`) and copy it into your DreamBot `Scripts` folder (`~/DreamBot/Scripts` by default). Override paths with the `DREAMBOT_HOME` or `DREAMBOT_SCRIPTS_DIR` environment variables (Bash) or the `-DreamBotHome`/`-ScriptsDir` parameters (PowerShell).

## Manual Build (If You Prefer)

You can still run the wrapper directly:

- **Windows:** `mvnw.cmd -B -DskipTests package`
- **macOS/Linux:** `./mvnw -B -DskipTests package`

Artifacts:

- Standard script: `target/roguesden-1.0-SNAPSHOT.jar`
- Fat/standalone JAR: `target/roguesden-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Launch DreamBot and Run the Script
1. Start the DreamBot launcher (`DreamBot Launcher.jar`).
2. Log in with your DreamBot credentials.
3. Open **Script Manager**, locate **Rogue's Den**, and click **Start**.

## Troubleshooting
- **Wrapper download blocked**: Ensure outbound HTTPS access to `repo.maven.apache.org` so the wrapper can fetch Maven.
- **Cannot find DreamBot folder**: Launch DreamBot once; it creates the directory automatically. Use the path overrides described above if your folder lives elsewhere.
- **Script missing in DreamBot**: Confirm the fat JAR is in the `Scripts` directory and restart DreamBot.
- **Build failures**: Delete the `target` folder and rerun the wrapper command. Verify Java is on your PATH.
