package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.items.Item;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.concurrent.atomic.AtomicBoolean;

@ScriptManifest(category = Category.AGILITY, name = "RoguesDen", author = "Assistant", version = 1.0)
public class RoguesDenScript extends AbstractScript {

    private static final String TOKEN_NAME = "Rogue's reward token";

    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final AtomicBoolean guiCancelled = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040, 4970, 3050, 4980, 1);
    private final Tile START_TILE = new Tile(3047, 4975, 1);
    private final Config config = new Config();
    private final SupplyManager supplyManager;
    private final MazeNavigator mazeNavigator;

    private RoguesDenGUI gui;
    private long startTime;

    private enum State { TRAVEL, MAZE, REST }

    public RoguesDenScript() {
        supplyManager = new SupplyManager(this, config, TOKEN_NAME);
        mazeNavigator = new MazeNavigator(this, config, abc, supplyManager, START_TILE);
    }

    @Override
    public void onStart() {
        log("Starting Rogues' Den script");
        startTime = System.currentTimeMillis();
        if (!meetsRequirements()) {
            log("Account doesn't meet Rogues' Den requirements.");
            ScriptManager.getScriptManager().stop();
            return;
        }

        abc.generateTrackers();

        SwingUtilities.invokeLater(() -> {
            gui = new RoguesDenGUI(config, guiDone, guiCancelled);
            gui.setVisible(true);
        });

        new Thread(() -> {
            while (!guiDone.get()) {
                Sleep.sleep(100);
            }
            if (guiCancelled.get()) {
                log("GUI closed before start; stopping script.");
                ScriptManager.getScriptManager().stop();
                return;
            }
            if (!validateConfig(config)) {
                log("Invalid configuration; stopping script.");
                ScriptManager.getScriptManager().stop();
                return;
            }
            supplyManager.prepareSupplies();
        }).start();
    }

    private boolean meetsRequirements() {
        return getSkills().getRealLevel(Skill.THIEVING) >= 50
            && getSkills().getRealLevel(Skill.AGILITY) >= 50;
    }

    private boolean validateConfig(Config cfg) {
        String error = ConfigValidator.validate(
            cfg.idleMin,
            cfg.idleMax,
            cfg.runThreshold,
            cfg.runRestore,
            cfg.breakIntervalMin,
            cfg.breakIntervalMax,
            cfg.breakLengthMin,
            cfg.breakLengthMax
        );
        if (error != null) {
            log(error);
            return false;
        }
        return true;
    }

    @Override
    public int onLoop() {
        if (!guiDone.get()) {
            return 600;
        }

        if (!supplyManager.areSuppliesReady()) {
            supplyManager.prepareSupplies();
            return 600;
        }

        if (!supplyManager.hasRequiredSupplies()) {
            log("Supplies missing, attempting to restock.");
            supplyManager.invalidateSupplies();
            return Calculations.random(300, 600);
        }

        if (!getWalking().isRunEnabled() && getWalking().getRunEnergy() >= config.runRestore) {
            getWalking().toggleRun(true);
        }

        AntiBan.permute(this, abc, config);

        if (supplyManager.handleRewards()) {
            return Calculations.random(600, 900);
        }

        State state = getState();
        switch (state) {
            case TRAVEL:
                handleTravel();
                if (!DEN_AREA.contains(getLocalPlayer())) {
                    getWalking().walk(START_TILE);
                    Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()), 12000);
                }
                return Calculations.random(300, 600);
            case REST:
                handleRest();
                return Calculations.random(600, 900);
            case MAZE:
                if (!getLocalPlayer().isAnimating() && !getLocalPlayer().isMoving()) {
                    mazeNavigator.handleMaze();
                }
                return Calculations.random(200, 400);
            default:
                return Calculations.random(200, 400);
        }
    }

    private State getState() {
        if (!DEN_AREA.contains(getLocalPlayer())) {
            return State.TRAVEL;
        }
        if (needsRest()) {
            return State.REST;
        }
        return State.MAZE;
    }

    private boolean needsRest() {
        return getWalking().getRunEnergy() < config.runThreshold;
    }

    private void handleTravel() {
        if (DEN_AREA.contains(getLocalPlayer())) {
            return;
        }

        int failures = 0;
        while (!DEN_AREA.contains(getLocalPlayer())) {
            if (getLocalPlayer().isAnimating()) {
                Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 15000);
                continue;
            }

            if (failures >= 3 && getMagic().canCast(Normal.HOME_TELEPORT)) {
                log("Teleporting closer to the Rogues' Den...");
                if (getMagic().castSpell(Normal.HOME_TELEPORT)) {
                    Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 30000);
                }
                failures = 0;
                continue;
            }

            if (!getWalking().walk(START_TILE)) {
                failures++;
                log("Failed to generate path to den (" + failures + ")");
                Sleep.sleep(600, 900);
            } else {
                Sleep.sleepUntil(() -> DEN_AREA.contains(getLocalPlayer()) || !getLocalPlayer().isMoving(), 15000);
            }
        }
    }

    private void handleRest() {
        log("Waiting for run energy...");
        if (config.useStamina) {
            Item stamina = supplyManager.getStaminaPotion();
            if (stamina != null && stamina.interact("Drink")) {
                Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 3000);
                return;
            }
        }
        Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 60000);
    }

    @Override
    public void onExit() {
        if (gui != null) {
            gui.dispose();
        }
    }

    @Override
    public void onPaint(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(5, 5, 190, 90);
        g.setColor(Color.WHITE);

        int y = 20;
        g.drawString("Rogues' Den", 10, y);
        y += 15;
        g.drawString("Runtime: " + formatTime(System.currentTimeMillis() - startTime), 10, y);
        y += 15;
        g.drawString("Step: " + mazeNavigator.getStep() + "/" + mazeNavigator.getTotalSteps(), 10, y);
        y += 15;
        g.drawString("Tokens: " + Inventory.count(TOKEN_NAME), 10, y);
        y += 15;
        g.drawString("Run: " + getWalking().getRunEnergy(), 10, y);
    }

    private String formatTime(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return String.format("%02d:%02d:%02d", h, m, sec);
    }

    int getStep() {
        return mazeNavigator.getStep();
    }

    int getMazeStepCount() {
        return mazeNavigator.getTotalSteps();
    }

    void incrementStep() {
        mazeNavigator.incrementStep();
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
        int breakIntervalMin = 30; // minutes
        int breakIntervalMax = 60; // minutes
        int breakLengthMin = 1;    // minutes
        int breakLengthMax = 5;    // minutes
    }
}
