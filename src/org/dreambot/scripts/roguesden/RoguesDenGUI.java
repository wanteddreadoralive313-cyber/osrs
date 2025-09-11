package org.dreambot.scripts.roguesden;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RoguesDenGUI extends JFrame {
    public RoguesDenGUI(RoguesDenScript.Config config, AtomicBoolean done) {
        setTitle("Rogues' Den Script");
        setSize(280,250);
        setLayout(new GridLayout(7,1));

        JCheckBox stamina = new JCheckBox("Use stamina potions", config.useStamina);
        JCheckBox antiban = new JCheckBox("Enable anti-ban", config.antiban);
        JCheckBox hover = new JCheckBox("Hover entities", config.hoverEntities);
        JCheckBox rightClick = new JCheckBox("Random right-clicks", config.randomRightClick);
        JCheckBox camera = new JCheckBox("Camera panning", config.cameraPanning);

        JPanel idlePanel = new JPanel(new FlowLayout());
        idlePanel.add(new JLabel("Idle ms:"));
        JTextField idleMin = new JTextField(String.valueOf(config.idleMin), 3);
        JTextField idleMax = new JTextField(String.valueOf(config.idleMax), 3);
        idlePanel.add(idleMin);
        idlePanel.add(new JLabel("to"));
        idlePanel.add(idleMax);

        JButton start = new JButton("Start");
        start.addActionListener(e -> {
            config.useStamina = stamina.isSelected();
            config.antiban = antiban.isSelected();
            config.hoverEntities = hover.isSelected();
            config.randomRightClick = rightClick.isSelected();
            config.cameraPanning = camera.isSelected();
            try {
                config.idleMin = Integer.parseInt(idleMin.getText());
                config.idleMax = Integer.parseInt(idleMax.getText());
            } catch (NumberFormatException ignored) {}
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
        add(start);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
}
