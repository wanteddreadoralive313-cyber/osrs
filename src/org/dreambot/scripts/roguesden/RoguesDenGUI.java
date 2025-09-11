package org.dreambot.scripts.roguesden;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RoguesDenGUI extends JFrame {
    public RoguesDenGUI(RoguesDenScript.Config config, AtomicBoolean done) {
        setTitle("Rogues' Den Script");
        setSize(250,250);
        setLayout(new GridLayout(5,1));

        JCheckBox stamina = new JCheckBox("Use stamina potions", config.useStamina);
        JCheckBox antiban = new JCheckBox("Enable anti-ban", config.antiban);

        JTextField runThresholdField = new JTextField(String.valueOf(config.runThreshold), 5);
        JPanel runThresholdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        runThresholdPanel.add(new JLabel("Run threshold:"));
        runThresholdPanel.add(runThresholdField);

        JTextField runRestoreField = new JTextField(String.valueOf(config.runRestore), 5);
        JPanel runRestorePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        runRestorePanel.add(new JLabel("Run restore:"));
        runRestorePanel.add(runRestoreField);

        JButton start = new JButton("Start");
        start.addActionListener(e -> {
            try {
                int threshold = Integer.parseInt(runThresholdField.getText());
                int restore = Integer.parseInt(runRestoreField.getText());
                if (threshold < 0 || threshold > 100 || restore < 0 || restore > 100 || threshold >= restore) {
                    throw new NumberFormatException();
                }
                config.runThreshold = threshold;
                config.runRestore = restore;
                config.useStamina = stamina.isSelected();
                config.antiban = antiban.isSelected();
                done.set(true);
                setVisible(false);
                dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter valid numbers (0-100) with threshold < restore.");
            }
        });

        add(stamina);
        add(antiban);
        add(runThresholdPanel);
        add(runRestorePanel);
        add(start);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
}
