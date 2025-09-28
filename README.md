# Rogue's Den DreamBot Script on Windows

This guide walks Windows users through installing the tools you need, building the script, and running it inside DreamBot.

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
