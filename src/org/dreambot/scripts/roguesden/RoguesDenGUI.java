package org.dreambot.scripts.roguesden;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public class RoguesDenGUI extends JFrame {

    public RoguesDenGUI(RoguesDenScript.Config config,
                        AtomicBoolean done,
                        AtomicBoolean cancelled) {
        setTitle("Rogues' Den Script");
        setSize(280, 320);
        setLayout(new GridLayout(0, 1));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelled.set(true);
                done.set(true);
            }
        });

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

        // Break scheduling controls
        JPanel breakIntervalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        breakIntervalPanel.add(new JLabel("Break interval (min):"));
        JTextField breakIntervalMin = new JTextField(String.valueOf(config.breakIntervalMin), 3);
        JTextField breakIntervalMax = new JTextField(String.valueOf(config.breakIntervalMax), 3);
        breakIntervalPanel.add(breakIntervalMin);
        breakIntervalPanel.add(new JLabel("to"));
        breakIntervalPanel.add(breakIntervalMax);

        JPanel breakLengthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        breakLengthPanel.add(new JLabel("Break length (min):"));
        JTextField breakLengthMin = new JTextField(String.valueOf(config.breakLengthMin), 3);
        JTextField breakLengthMax = new JTextField(String.valueOf(config.breakLengthMax), 3);
        breakLengthPanel.add(breakLengthMin);
        breakLengthPanel.add(new JLabel("to"));
        breakLengthPanel.add(breakLengthMax);

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
            int bIntMin, bIntMax, bLenMin, bLenMax;
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
                bIntMin = Integer.parseInt(breakIntervalMin.getText().trim());
                bIntMax = Integer.parseInt(breakIntervalMax.getText().trim());
                bLenMin = Integer.parseInt(breakLengthMin.getText().trim());
                bLenMax = Integer.parseInt(breakLengthMax.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Run/break values must be integers.",
                        "Invalid input",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Validate ranges using shared validator
            String error = ConfigValidator.validate(
                    newIdleMin,
                    newIdleMax,
                    threshold,
                    restore,
                    bIntMin,
                    bIntMax,
                    bLenMin,
                    bLenMax
            );
            if (error != null) {
                JOptionPane.showMessageDialog(
                        this,
                        error,
                        "Invalid configuration",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Commit
            config.idleMin = newIdleMin;
            config.idleMax = newIdleMax;
            config.runThreshold = threshold;
            config.runRestore = restore;
            config.breakIntervalMin = bIntMin;
            config.breakIntervalMax = bIntMax;
            config.breakLengthMin = bLenMin;
            config.breakLengthMax = bLenMax;

            cancelled.set(false);
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
        add(breakIntervalPanel);
        add(breakLengthPanel);
        add(start);

        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
}
