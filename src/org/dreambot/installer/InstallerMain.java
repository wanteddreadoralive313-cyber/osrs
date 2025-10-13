package org.dreambot.installer;

import javax.swing.JOptionPane;
import java.nio.file.Path;

/**
 * Entry point for the Rogue's Den installer executable. The installer is responsible for pulling the
 * latest script JAR from the configured download channels every time it launches and copying it into
 * the DreamBot Scripts directory.
 */
public final class InstallerMain {

    private InstallerMain() {
    }

    public static void main(String[] args) {
        InstallerConfig config = InstallerConfig.load(args);

        try {
            ChannelListLoader loader = new ChannelListLoader(config);
            ChannelManifest manifest = loader.loadAndRefresh();

            UpdateManager updateManager = new UpdateManager(config);
            UpdateReport report = updateManager.update(manifest);

            displayOutcome(report, config.getTargetScriptPath(manifest.getEffectiveScriptFileName()));
        } catch (InstallerException e) {
            showDialog("Installer failed: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void displayOutcome(UpdateReport report, Path destination) {
        if (report.isSuccess()) {
            String message = String.format(
                "Rogue's Den script is up to date!%n%n" +
                    "Source: %s%nDownloaded bytes: %d%nDestination: %s",
                report.getSuccessfulChannelName(),
                report.getDownloadedBytes(),
                destination
            );
            showDialog(message, JOptionPane.INFORMATION_MESSAGE);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Could not download the script from any configured channel.\n\n");
            if (!report.getErrors().isEmpty()) {
                sb.append("Errors:\n");
                report.getErrors().forEach(error -> sb.append(" - ").append(error).append('\n'));
            }
            showDialog(sb.toString(), JOptionPane.ERROR_MESSAGE);
            System.exit(2);
        }
    }

    private static void showDialog(String message, int messageType) {
        // Use Swing dialogs so that the executable can be used as a true one-click installer without
        // requiring the user to keep a console window open.
        try {
            JOptionPane.showMessageDialog(null, message, "Rogue's Den Installer", messageType);
        } catch (Exception ex) {
            // If Swing is not available (headless environment), fall back to stdout/stderr so that
            // the installer is still usable by advanced users.
            if (messageType == JOptionPane.ERROR_MESSAGE) {
                System.err.println(message);
            } else {
                System.out.println(message);
            }
        }
    }
}
