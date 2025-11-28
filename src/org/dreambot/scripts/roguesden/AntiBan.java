package org.dreambot.scripts.roguesden;

import org.dreambot.api.input.Mouse;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.camera.Camera;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.login.Login;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.Entity;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.Player;

import java.awt.Point;

public class AntiBan {
    private static long nextBreak = -1;
    private static long breakEnd = -1;

    public static void permute(AbstractScript script, ABCUtil abc, RoguesDenScript.Config config) {
        if (config == null || !config.antiban) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        boolean breaksEnabled = breaksEnabled(config);

        if (breaksEnabled) {
            if (nextBreak == -1) {
                nextBreak = now + Calculations.random(
                    config.breakIntervalMin * 60_000L,
                    config.breakIntervalMax * 60_000L
                );
            }

            if (breakEnd > 0) {
                if (now >= breakEnd) {
                    Login.login();
                    breakEnd = -1;
                    nextBreak = now + Calculations.random(
                        config.breakIntervalMin * 60_000L,
                        config.breakIntervalMax * 60_000L
                    );
                }
                return;
            }

            if (now >= nextBreak) {
                script.log("Taking scheduled break...");
                Tabs tabs = script.getTabs();
                if (tabs != null) {
                    tabs.logout();
                }
                breakEnd = now + Calculations.random(
                    config.breakLengthMin * 60_000L,
                    config.breakLengthMax * 60_000L
                );
                return;
            }
        } else {
            reset();
        }


        // Perform built-in timed actions
        abc.performTimedActions();

        if (abc.shouldCheckXP()) {
            script.getSkills().hoverSkill(org.dreambot.api.methods.skills.Skill.AGILITY);
        }

        if (abc.shouldOpenTab()) {
            Tabs tabs = script.getTabs();
            if (tabs != null) {
                tabs.openWithMouse(Tab.INVENTORY);
            }
        }

        // Hover a nearby entity if enabled
        if (config.hoverEntities && abc.shouldHover()) {
            GameObjects gameObjects = script.getGameObjects();
            if (gameObjects != null) {
                GameObject g = gameObjects.closest(o -> o != null);
                if (shouldPerform(config, g)) {
                    g.hover();
                }
            }
        }

        // Random misclicks followed by corrections
        Mouse mouse = script.getMouse();
        if (mouse != null && roll(config, 2)) {
            Point start = mouse.getPosition();
            if (start != null) {
                mouse.move(start.x + Calculations.random(-80, 80), start.y + Calculations.random(-80, 80));
                mouse.click();
                Sleep.sleep(200, 600);
                mouse.move(start);
            }
        }

        // Mouse path variability and mini-breaks
        if (mouse != null && roll(config, 5)) {
            mouse.moveRandomly();
        }

        if (mouse != null && roll(config, 0.5)) {
            mouse.moveMouseOutsideScreen();
            Sleep.sleep(Calculations.random(500, 1500));
        }

        // Random right-click if enabled
        if (mouse != null && config.randomRightClick && abc.shouldOpenMenu() && shouldPerform(config, mouse)) {
            mouse.click(false);
        }

        // Timed camera panning
        if (config.cameraPanning && abc.shouldRotateCamera() && shouldPerform(config, script.getCamera())) {
            Camera camera = script.getCamera();
            if (camera != null) {
                camera.rotateToYaw(Calculations.random(0, 2048));
                camera.rotateToPitch(Calculations.random(300, 400));
            }
        }

        // Occasionally move mouse off screen
        if (mouse != null && roll(config, 2.5)) {
            mouse.moveMouseOutsideScreen();
        }

        // Optional idle delay
        if (config.idleMax > 0 && roll(config, 10)) {
            Sleep.sleep(config.idleMin, config.idleMax);
        }

        int reactionTime = adjustReactionTime(abc.generateReactionTime(), config);
        if (!isInLongWaitState(script)) {
            script.log("Reaction time: " + reactionTime + "ms");
            Sleep.sleep(reactionTime);
        }
        abc.generateTrackers();
    }

    public static void sleepReaction(AbstractScript script, ABCUtil abc, RoguesDenScript.Config config) {
        if (config == null || !config.antiban) {
            return;
        }
        if (isInLongWaitState(script)) {
            return;
        }
        Sleep.sleep(adjustReactionTime(abc.generateReactionTime(), config));
    }

    public static void reset() {
        nextBreak = -1L;
        breakEnd = -1L;
    }

    private static boolean breaksEnabled(RoguesDenScript.Config config) {
        return config.breakIntervalMax > 0 && config.breakLengthMax > 0;
    }

    private static boolean isInLongWaitState(AbstractScript script) {
        if (script == null) {
            return false;
        }

        Dialogues dialogues = script.getDialogues();
        boolean inDialogue = dialogues != null && (
            dialogues.inDialogue() || dialogues.areOptionsAvailable() || dialogues.canContinue() || dialogues.isProcessing()
        );

        Player player = script.getLocalPlayer();
        boolean traveling = player != null && (player.isMoving() || player.isAnimating());

        return inDialogue || traveling;
    }

    private static boolean roll(RoguesDenScript.Config config, double percent) {
        return Calculations.random(0.0, 100.0) < percent * config.antibanIntensity.getChanceScale();
    }

    private static boolean shouldPerform(RoguesDenScript.Config config, Entity entity) {
        return entity != null && config.antibanIntensity.shouldPerformEntityAction();
    }

    private static int adjustReactionTime(int base, RoguesDenScript.Config config) {
        return (int) Math.max(0, Math.round(base * config.antibanIntensity.getReactionScale()));
    }
}
