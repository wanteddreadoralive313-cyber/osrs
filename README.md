# Rogue's Den DreamBot Script on Windows

This guide walks Windows users through installing the tools you need, building the script, packaging a one-click Windows installer, and running it inside DreamBot.

## One-Click Windows Installer

The project now includes a lightweight installer that automatically updates the Rogue's Den script every time it starts. The installer downloads the most recent script JAR from multiple mirrored channels, copies it into your DreamBot Scripts directory, and confirms success using a GUI dialog.

### Build the Installer

1. Open a terminal in the project root.
2. Run Maven to build both the script JAR and the installer executable:
   ```bat
   mvn package
   ```
3. After the build succeeds, the installer is located at:
   ```
   target\RoguesDenInstaller.exe
   ```
   The fat JAR that backs the installer is available at `target\roguesden-installer.jar`.

> **Note:** The installer requires a Java 11+ runtime on the target machine. Launch4j will prompt the user if the minimum version is not met.

### First Run Experience

1. Double-click `RoguesDenInstaller.exe`.
2. The installer pulls the latest `channels.json` manifest from the configured sources (see below). If it cannot reach any remote manifest, it falls back to the bundled defaults.
3. The installer attempts each download URL in order until it successfully downloads the script or exhausts every channel.
4. Once a download succeeds, the executable copies the script to your DreamBot `Scripts` directory and displays a confirmation dialog.

The installer re-runs this process every time it starts, guaranteeing that the local copy of the script stays synchronized with the most recent channel update.

### Channel Configuration

* At runtime the installer keeps its working files inside:
  * Windows: `%LOCALAPPDATA%\RoguesDenInstaller`
  * macOS/Linux: `~/.rogues-den/installer`
* The file `channels.json` describes the available download mirrors. On first launch the installer copies a default version (based on `src/org/dreambot/installer/channels/default-channels.json`).
* The manifest can list multiple mirrors for the script JAR and multiple sources for the manifest itself. Example entries are pre-populated with GitHub raw URLs and the jsDelivr CDN—replace `YOUR_GITHUB_USERNAME` with your GitHub handle (or add your own mirrors).
* To ship updates to the channel list automatically, publish a fresh `channels.json` at any of the URLs listed under the `manifestSources` array. Each time the installer runs it checks every source, downloading an updated manifest whenever the remote content differs from the local copy.
* Optional SHA-256 hashes can be added to each channel entry for integrity validation.

You can override or extend the default manifest sources by either:

* Passing `--channel-source=<URL>` when launching the installer executable, or
* Setting the environment variable `ROGUES_DEN_CHANNEL_SOURCES` to a semicolon-separated list of manifest URLs.

To override the DreamBot root directory (useful for portable installs), set `ROGUES_DEN_DREAMBOT_DIR` to your desired path before running the installer.

## Prerequisites

### Java 8 or Newer
1. Download the latest Java Development Kit (JDK) from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/).
2. Run the installer and accept the default options.
3. Open **Command Prompt** and verify the installation:
   ```bat
   java -version
   ```
   You should see a version number (e.g., `1.8.0_xx` or `17.x`).

### Apache Maven
1. Download Maven from the [official download page](https://maven.apache.org/download.cgi) (choose the binary zip archive).
2. Extract the zip to a folder such as `C:\Maven`.
3. Add the Maven `bin` directory to your **Path** environment variable (e.g., `C:\Maven\apache-maven-<version>\bin`).
4. Open a new **Command Prompt** and confirm the installation:
   ```bat
   mvn -v
   ```
   Maven should print its version and your Java installation details.

### DreamBot Client
1. Create a free account at [dreambot.org](https://dreambot.org/).
2. Download the DreamBot launcher from the [client download page](https://dreambot.org/client).
3. Run the launcher once so DreamBot can finish installing. The DreamBot folder will be created in your Windows user profile (typically `C:\Users\<YourName>\DreamBot`).

## Get the Project Files

### Option 1: Download Zip
1. Visit the repository page in your browser.
2. Click **Code ▾** → **Download ZIP**.
3. Extract the archive to a convenient folder (e.g., `C:\Projects\rogues-den`).

### Option 2: Clone with Git
1. Install Git for Windows from [git-scm.com](https://git-scm.com/download/win) if you do not already have it.
2. Open **Command Prompt** or **PowerShell** and run:
   ```bat
   git clone https://github.com/your-username/rogues-den.git
   ```
3. Change into the project folder:
   ```bat
   cd rogues-den
   ```

## Build the Script with Maven
1. Open **Command Prompt** or **PowerShell** in the project directory. You can shift-right-click the folder and choose **Open PowerShell window here**.
2. Run Maven to compile the project and package it as a JAR:
   ```bat
   mvn package
   ```
   Maven downloads dependencies, compiles the source code, runs tests (if any), and builds the output JAR.
3. After the build succeeds, the compiled script is located at:
   ```
   target\roguesden-1.0-SNAPSHOT.jar
   ```

## Copy the JAR into DreamBot
1. Open **File Explorer** and navigate to the DreamBot scripts directory: `C:\Users\<YourName>\DreamBot\Scripts`.
2. Copy `target\roguesden-1.0-SNAPSHOT.jar` from the project folder into the `Scripts` directory. You can drag and drop or use `copy` from the command line:
   ```bat
   copy target\roguesden-1.0-SNAPSHOT.jar "C:\Users\<YourName>\DreamBot\Scripts"
   ```

## Launch DreamBot and Run the Script
1. Start the DreamBot launcher (`DreamBot Launcher.jar`).
2. Log in with your DreamBot credentials.
3. Once the client loads, open the **Script Manager**, locate **Rogue's Den** in the list, and click **Start**.
4. Configure any script settings as needed and begin running the script in Old School RuneScape.

## Troubleshooting Tips
- **Command not recognized**: Ensure `java` and `mvn` are both on your system Path. Reopen Command Prompt after changing environment variables.
- **Build failures**: Delete the `target` folder and rerun `mvn package`. Verify your internet connection so Maven can download dependencies.
- **Cannot find DreamBot folder**: Launch DreamBot once; it creates the folder automatically. Check `C:\Users\<YourName>\DreamBot`.
- **Windows blocked the JAR**: Right-click the JAR, choose **Properties**, and check **Unblock** if present.
- **Developer Mode required**: If Windows SmartScreen prevents execution, open **Settings → Privacy & security → For developers** and enable **Developer Mode** or allow the app through SmartScreen prompts.

## FAQ
**Do I need Java 8 specifically?**  Java 8 or any newer LTS version is fine, as long as DreamBot supports it.

**Can I use PowerShell instead of Command Prompt?**  Yes. All commands in this guide work the same in PowerShell.

**Where can I find the built script?**  After running `mvn package`, look in the `target` folder for `roguesden-1.0-SNAPSHOT.jar`.

**Do I have to rebuild after every change?**  Yes. If you modify the source code, rerun `mvn package` to generate an updated JAR before copying it to DreamBot.

**Why doesn't DreamBot show the script?**  Confirm the JAR is in `C:\Users\<YourName>\DreamBot\Scripts` and that it has read permissions. Restart DreamBot after copying the file.
