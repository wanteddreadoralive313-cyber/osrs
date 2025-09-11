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

    private enum State { TRAVEL, MAZE, REST }

    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1); // approximate
    private final Tile START_TILE = new Tile(3047,4975,1);
    private final Tile[] MAZE_STEPS = {
            new Tile(3047,4973,1), // first door
            new Tile(3048,4970,1), // climb obstacle
            new Tile(3050,4970,1)  // squeeze obstacle
    };
    private int step = 0;
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

        if (!getWalking().isRunEnabled() && getWalking().getRunEnergy() >= config.runRestore) {
            getWalking().toggleRun(true);
        }

        AntiBan.permute(this, abc, config);

        State state = getState();
        switch (state) {
            case TRAVEL:
                getWalking().walk(START_TILE);
                Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()), 12000);
                return Calculations.random(300,600);
            case REST:
                handleRest();
                return Calculations.random(600,900);
            case MAZE:
                if (!getLocalPlayer().isAnimating() && !getLocalPlayer().isMoving()) {
                    handleMaze();
                }
                return Calculations.random(200,400);
        }
        return Calculations.random(200,400);
    }

    private State getState() {
        if (!DEN_AREA.contains(getLocalPlayer()))
            return State.TRAVEL;
        if (needsRest())
            return State.REST;
        return State.MAZE;
    }

    private boolean needsRest() {
        return getWalking().getRunEnergy() < config.runThreshold;
    }

    private void handleRest() {
        log("Waiting for run energy...");
        if (config.useStamina && Inventory.contains(i -> i.getName().contains("Stamina potion"))) {
            Inventory.get(i -> i.getName().contains("Stamina potion")).interact("Drink");
            Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 3000);
        } else {
            Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 60000);
        }
    }

    private void handleMaze() {
        if (getLocalPlayer().distance(START_TILE) <= 1 && step > 0) {
            recoverMaze();
            return;
        }

        if (step >= MAZE_STEPS.length) {
            step = 0;
            return;
        }

        Tile target = MAZE_STEPS[step];
        if (getLocalPlayer().distance(target) > 2) {
            getWalking().walk(target);
            Sleep.sleepUntil(() -> getLocalPlayer().distance(target) <= 2, 5000);
            return;
        }

        switch (step) {
            case 0:
                handleDoor();
                break;
            case 1:
                handleClimb();
                break;
            case 2:
                handleSqueeze();
                break;
            default:
                step = 0;
        }
    }

    private void handleDoor() {
        GameObject door = GameObjects.closest(o -> o.getName().equals("Door"));
        if (door != null && door.interact("Open")) {
            Sleep.sleepUntil(() -> getLocalPlayer().isMoving(), 3000);
            step++;
        }
    }

    private void handleClimb() {
        GameObject climb = GameObjects.closest(o -> o.hasAction("Climb"));
        if (climb != null && climb.interact("Climb")) {
            Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000);
            step++;
        }
    }

private void handleSqueeze() {
    GameObject squeeze = GameObjects.closest(o -> o.hasAction("Squeeze"));
    if (squeeze != null && squeeze.interact("Squeeze")) {
        Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000);
        step++;
    }
}

private void recoverMaze() {
    log("Recovering maze...");
    getWalking().walk(START_TILE);
    Sleep.sleepUntil(() -> getLocalPlayer().distance(START_TILE) <= 2, 6000);
    step = 0;
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
        boolean hoverEntities = true;
        boolean randomRightClick = true;
        boolean cameraPanning = true;
        int idleMin = 200;
        int idleMax = 600;
        int runThreshold = 20;
        int runRestore = 40;
    }
}
