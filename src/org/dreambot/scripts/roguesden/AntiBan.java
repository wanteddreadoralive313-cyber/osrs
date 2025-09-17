package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.login.Login;
import org.dreambot.api.methods.login.LoginState;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

import java.awt.Point;

public class AntiBan {
    private static long nextBreak = -1;
    private static long breakEnd = -1;

    public static void permute(AbstractScript script, ABCUtil abc, RoguesDenScript.Config config) {
        if (config == null || !config.antiban) {
            resetScheduler();
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
                    LoginState loginState = Login.getLoginState();
                    if (loginState != LoginState.LOGGED_IN) {
                        Login.login();
                    }
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
                LoginState loginState = Login.getLoginState();
                if (loginState == LoginState.LOGGED_IN) {
                    script.getTabs().logout();
                }
                breakEnd = now + Calculations.random(
                    config.breakLengthMin * 60_000L,
                    config.breakLengthMax * 60_000L
                );
                return;
            }
        } else {
            resetScheduler();
        }


        // Perform built-in timed actions
        abc.performTimedActions();

        if (abc.shouldCheckXP()) {
            script.getSkills().hoverSkill(org.dreambot.api.methods.skills.Skill.AGILITY);
        }

        if (abc.shouldOpenTab()) {
            script.getTabs().openWithMouse(Tab.INVENTORY);
        }

        // Hover a nearby entity if enabled
        if (config.hoverEntities && abc.shouldHover()) {
            GameObject g = script.getGameObjects().closest(o -> o != null);
            if (g != null) {
                g.hover();
            }
        }

        // Random misclicks followed by corrections
        if (Calculations.random(0, 100) < 2) {
            Point start = script.getMouse().getPosition();
            script.getMouse().move(start.x + Calculations.random(-80, 80), start.y + Calculations.random(-80, 80));
            script.getMouse().click();
            Sleep.sleep(200, 600);
            script.getMouse().move(start);
        }

        // Mouse path variability and mini-breaks
        if (Calculations.random(0, 100) < 5) {
            script.getMouse().moveRandomly();
        }

        if (Calculations.random(0, 200) == 0) {
            script.getMouse().moveMouseOutsideScreen();
            Sleep.sleep(Calculations.random(500, 1500));
        }

        // Random right-click if enabled
        if (config.randomRightClick && abc.shouldOpenMenu()) {
            script.getMouse().click(false);
        }

        // Timed camera panning
        if (config.cameraPanning && abc.shouldRotateCamera()) {
            script.getCamera().rotateToYaw(Calculations.random(0, 2048));
            script.getCamera().rotateToPitch(Calculations.random(300, 400));
        }

        // Occasionally move mouse off screen
        if (Calculations.random(0, 40) == 0) {
            script.getMouse().moveMouseOutsideScreen();
        }

        // Optional idle delay
        if (config.idleMax > 0 && Calculations.random(0, 100) < 10) {
            Sleep.sleep(config.idleMin, config.idleMax);
        }

        int reactionTime = abc.generateReactionTime();
        script.log("Reaction time: " + reactionTime + "ms");
        Sleep.sleep(reactionTime);
        abc.generateTrackers();
    }

    public static void sleepReaction(ABCUtil abc, RoguesDenScript.Config config) {
        if (config == null || !config.antiban) {
            return;
        }
        Sleep.sleep(abc.generateReactionTime());
    }

    private static void resetScheduler() {
        nextBreak = -1L;
        breakEnd = -1L;
    }

    private static boolean breaksEnabled(RoguesDenScript.Config config) {
        return config.breakIntervalMax > 0 && config.breakLengthMax > 0;
    }
}
