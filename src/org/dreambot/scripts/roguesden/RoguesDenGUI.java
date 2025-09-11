package org.dreambot.scripts.roguesden;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RoguesDenGUI extends JFrame {
    public RoguesDenGUI(RoguesDenScript.Config config, AtomicBoolean done) {
        setTitle("Rogues' Den Script");
        setSize(250,150);
        setLayout(new GridLayout(3,1));
        JCheckBox stamina = new JCheckBox("Use stamina potions", config.useStamina);
        JCheckBox antiban = new JCheckBox("Enable anti-ban", config.antiban);
        JButton start = new JButton("Start");
        start.addActionListener(e -> {
            config.useStamina = stamina.isSelected();
            config.antiban = antiban.isSelected();
            done.set(true);
            setVisible(false);
            dispose();
        });
        add(stamina);
        add(antiban);
        add(start);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
}
