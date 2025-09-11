package org.dreambot.scripts.roguesden;

import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

@ScriptManifest(category = Category.AGILITY, name = "RoguesDen", author = "Assistant", version = 1.0)
public class RoguesDenScript extends AbstractScript {

    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1); // approximate
    private final Tile START_TILE = new Tile(3047,4975,1);
    private Config config = new Config();
    private RoguesDenGUI gui;
    private boolean ironman;

    @Override
    public void onStart() {
        log("Starting Rogues' Den script");
        if (!meetsRequirements()) {
            log("Account doesn't meet Rogues' Den requirements.");
            ScriptManager.getScriptManager().stop();
            return;
        }
        ironman = getClient().isIronMan();
        SwingUtilities.invokeLater(() -> {
            gui = new RoguesDenGUI(config, guiDone);
            gui.setVisible(true);
        });

        new Thread(() -> {
            while (!guiDone.get()) {
                Sleep.sleep(100);
            }
            prepareSupplies();
        }).start();
    }

    private boolean meetsRequirements() {
        return getSkills().getRealLevel(Skill.THIEVING) >= 50 && getSkills().getRealLevel(Skill.AGILITY) >= 50;
    }

    @Override
    public int onLoop() {
        if (!guiDone.get()) return 600;
        AntiBan.permute(this, abc, config.antiban);

        if (!DEN_AREA.contains(getLocalPlayer())) {
            getWalking().walk(START_TILE);
            Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()), 12000);
            return Calculations.random(300,600);
        }

        if (shouldWaitRun()) {
            log("Waiting for run energy...");
            Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 60000);
            return Calculations.random(600,900);
        }

        if (!getLocalPlayer().isAnimating() && !getLocalPlayer().isMoving())
            interactMaze();

        return Calculations.random(200,400);
    }

    private boolean shouldWaitRun() {
        if (getWalking().getRunEnergy() >= config.runThreshold) return false;
        if (config.useStamina && Inventory.contains(i -> i.getName().contains("Stamina potion"))) {
            Inventory.get(i -> i.getName().contains("Stamina potion")).interact("Drink");
            Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runThreshold, 3000);
            return false;
        }
        return true;
    }

    private void interactMaze() {
        GameObject obj = GameObjects.closest(o -> o.hasAction("Open") || o.hasAction("Climb") || o.hasAction("Squeeze"));
        if (obj != null && obj.interact()) {
            Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 5000);
        } else {
            Sleep.sleep(400,700);
        }
    }

    private void prepareSupplies() {
        if (getBank().openClosest()) {
            Sleep.sleepUntil(() -> getBank().isOpen(), 5000);

            if (!ironman && !Inventory.contains("Coins")) {
                getBank().withdrawAll("Coins");
                Sleep.sleepUntil(() -> Inventory.contains("Coins"), 2000);
            } else if (ironman) {
                log("Ironman account detected, skipping coin withdrawal.");
            }

            if (config.useStamina && !Inventory.contains(i -> i.getName().contains("Stamina potion"))) {
                getBank().withdrawAll(i -> i.getName().contains("Stamina potion"));
                Sleep.sleepUntil(() -> Inventory.contains(i -> i.getName().contains("Stamina potion")), 2000);
            }

            getBank().close();
            Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
        }
    }

    @Override
    public void onExit() {
        if (gui != null) gui.dispose();
    }

    static class Config {
        boolean useStamina = true;
        boolean antiban = true;
        int runThreshold = 20;
        int runRestore = 40;
    }
}
