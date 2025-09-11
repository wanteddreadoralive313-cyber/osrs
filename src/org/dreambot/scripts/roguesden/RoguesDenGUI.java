package org.dreambot.scripts.roguesden;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RoguesDenGUI extends JFrame {

    public RoguesDenGUI(RoguesDenScript.Config config, AtomicBoolean done) {
        setTitle("Rogues' Den Script");
        setSize(280, 250);
        setLayout(new GridLayout(0, 1));

        // Common checkboxes
        JCheckBox stamina = new JCheckBox("Use stamina potions", config.useStamina);
        JCheckBox antiban = new JCheckBox("Enable anti-ban", config.antiban);

        // Anti-ban behavior toggles (merge from other branch)
        JCheckBox hover = new JCheckBox("Hover entities", config.hoverEntities);
        JCheckBox rightClick = new JCheckBox("Random right-clicks", config.randomRightClick);
        JCheckBox camera = new JCheckBox("Camera panning", config.cameraPanning);

        // Idle timing (from enhance branch)
        JPanel idlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        idlePanel.add(new JLabel("Idle ms:"));
        JTextField idleMin = new JTextField(String.valueOf(config.idleMin), 3);
        JTextField idleMax = new JTextField(String.valueOf(config.idleMax), 3);
        idlePanel.add(idleMin);
        idlePanel.add(new JLabel("to"));
        idlePanel.add(idleMax);

        // Run-energy controls (from the other branch)
        JTextField runThresholdField = new JTextField(String.valueOf(config.runThreshold), 5);
        JPanel runThresholdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        runThresholdPanel.add(new JLabel("Run threshold:"));
        runThresholdPanel.add(runThresholdField);

        JTextField runRestoreField = new JTextField(String.valueOf(config.runRestore), 5);
        JPanel runRestorePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        runRestorePanel.add(new JLabel("Run restore:"));
        runRestorePanel.add(runRestoreField);

        // Start button + merged validation
        JButton start = new JButton("Start");
        start.addActionListener(e -> {
            // Persist toggles
            config.useStamina = stamina.isSelected();
            config.antiban = antiban.isSelected();
            config.hoverEntities = hover.isSelected();
            config.randomRightClick = rightClick.isSelected();
            config.cameraPanning = camera.isSelected();

            // Parse numeric inputs
            int newIdleMin, newIdleMax, threshold, restore;
            try {
                newIdleMin = Integer.parseInt(idleMin.getText().trim());
                newIdleMax = Integer.parseInt(idleMax.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Idle values must be integers.",
                        "Invalid input",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            try {
                threshold = Integer.parseInt(runThresholdField.getText().trim());
                restore = Integer.parseInt(runRestoreField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Run threshold/restore must be integers.",
                        "Invalid input",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Validate ranges
            if (threshold < 0 || threshold > 100 || restore < 0 || restore > 100 || threshold >= restore) {
                JOptionPane.showMessageDialog(
                        this,
                        "Run energy values must be between 0 and 100,\n" +
                                "and 'Run threshold' must be less than 'Run restore'.",
                        "Invalid run-energy settings",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            if (newIdleMin < 0 || newIdleMax < 0 || newIdleMin > newIdleMax) {
                JOptionPane.showMessageDialog(
                        this,
                        "Idle ms must be non-negative and 'min' must be ≤ 'max'.",
                        "Invalid idle settings",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Commit
            config.idleMin = newIdleMin;
            config.idleMax = newIdleMax;
            config.runThreshold = threshold;
            config.runRestore = restore;

            done.set(true);
            setVisible(false);
            dispose();
        });

        add(stamina);
        add(antiban);
        add(hover);
        add(rightClick);
        add(camera);
        add(idlePanel);
        add(runThresholdPanel);
        add(runRestorePanel);
        add(start);

        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
}
