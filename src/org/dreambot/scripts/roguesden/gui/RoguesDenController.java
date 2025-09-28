package org.dreambot.scripts.roguesden.gui;

import org.dreambot.scripts.roguesden.ConfigValidator;
import org.dreambot.scripts.roguesden.RoguesDenGUI;
import org.dreambot.scripts.roguesden.RoguesDenScript;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates display of the configuration GUI and validates the resulting configuration
 * before the script begins execution.
 */
public class RoguesDenController {

    private final RoguesDenScript.Config config;
    private RoguesDenGUI gui;

    public RoguesDenController(RoguesDenScript.Config config) {
        this.config = config != null ? config : new RoguesDenScript.Config();
    }

    /**
     * Displays the configuration GUI and blocks until the user confirms or cancels.
     *
     * @return the validated configuration, or {@code null} if the user cancelled.
     */
    public RoguesDenScript.Config awaitConfiguration() {
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(true);

        SwingUtilities.invokeLater(() -> {
            gui = new RoguesDenGUI(config, done, cancelled);
            gui.setVisible(true);
        });

        while (!done.get()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        if (cancelled.get()) {
            return null;
        }

        String error = ConfigValidator.validate(
            config.idleMin,
            config.idleMax,
            config.runThreshold,
            config.runRestore,
            config.breakIntervalMin,
            config.breakIntervalMax,
            config.breakLengthMin,
            config.breakLengthMax,
            config.staminaDoseTarget,
            config.staminaDoseThreshold,
            config.minimumHealthPercent
        );

        if (error != null) {
            throw new IllegalStateException(error);
        }

        return config;
    }

    public void dispose() {
        if (gui == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            gui.setVisible(false);
            gui.dispose();
        });
    }
}
