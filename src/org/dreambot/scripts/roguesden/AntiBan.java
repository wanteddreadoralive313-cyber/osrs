package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.login.Login;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

import java.awt.Point;

public class AntiBan {
    private static boolean trackersGenerated = false;
    private static long nextBreak = System.currentTimeMillis() + Calculations.random(30 * 60_000, 60 * 60_000);
    private static long breakEnd = -1;

    public static void permute(AbstractScript script, ABCUtil abc, RoguesDenScript.Config config) {
        if (!config.antiban) return;

        // Ensure trackers are generated once per session
        if (!trackersGenerated) {
            abc.generateTrackers();
            trackersGenerated = true;
        }

        // Break scheduler
        long now = System.currentTimeMillis();
        if (breakEnd > 0) {
            // We are currently on a break
            if (now >= breakEnd) {
                Login.login();
                breakEnd = -1;
                nextBreak = now + Calculations.random(30 * 60_000, 60 * 60_000);
            }
            return;
        }

        if (now >= nextBreak) {
            script.log("Taking scheduled break...");
            script.getTabs().logout();
            breakEnd = now + Calculations.random(60_000, 300_000);
            return;
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

    public static void sleepReaction(ABCUtil abc) {
        Sleep.sleep(abc.generateReactionTime());
    }
}
