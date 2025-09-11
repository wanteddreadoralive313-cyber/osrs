package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
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
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.Item;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ScriptManifest(category = Category.AGILITY, name = "RoguesDen", author = "Assistant", version = 1.0)
public class RoguesDenScript extends AbstractScript {

    private static final String TOKEN_NAME = "Rogue's reward token";
    private static final String REWARD_NPC = "Rogue";
    private static final String[] GEAR_ITEMS = {
        "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
    };

    private static final int FAILURE_THRESHOLD = 3;

    private static class MazeStep {
        final Tile tile;
        final String name;
        final String action;
        final Tile successTile;
        final int animationId;

        MazeStep(Tile tile, String name, String action) {
            this(tile, name, action, null, -1);
        }

        MazeStep(Tile tile, String name, String action, Tile successTile, int animationId) {
            this.tile = tile;
            this.name = name;
            this.action = action;
            this.successTile = successTile;
            this.animationId = animationId;
        }
    }

    private final MazeStep[] MAZE_STEPS = new MazeStep[]{
        new MazeStep(new Tile(3047,4973,1), "Door",   "Open"),
        new MazeStep(new Tile(3048,4970,1), "Rubble", "Climb"),
        new MazeStep(new Tile(3050,4970,1), "Gap",    "Squeeze-through"),
        new MazeStep(new Tile(3052,4968,1), "Trap",   "Disarm"),
        new MazeStep(new Tile(3054,4968,1), "Crate",  "Search")
    };

    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1);
    private final Tile START_TILE = new Tile(3047,4975,1);

    private int step = 0;
    private Config config = new Config();
    private RoguesDenGUI gui;
    private boolean ironman;
    private boolean suppliesReady;
    private int failureCount = 0;
    private Tile lastSafeTile = START_TILE;
    private long startTime;

    private enum State { TRAVEL, MAZE, REST }

    @Override
    public void onStart() {
        log("Starting Rogues' Den script");
        startTime = System.currentTimeMillis();
        if (!meetsRequirements()) {
            log("Account doesn't meet Rogues' Den requirements.");
            ScriptManager.getScriptManager().stop();
            return;
        }

        ironman = getClient().isIronMan();

        // Initialize ABC2 reaction-time trackers once at script start
        abc.generateTrackers();

        SwingUtilities.invokeLater(() -> {
            gui = new RoguesDenGUI(config, guiDone);
            gui.setVisible(true);
        });

        new Thread(() -> {
            while (!guiDone.get()) {
                Sleep.sleep(100);
            }
            if (!validateConfig(config)) {
                log("Invalid configuration; stopping script.");
                ScriptManager.getScriptManager().stop();
                return;
            }
            prepareSupplies();
        }).start();
    }

    private boolean meetsRequirements() {
        return getSkills().getRealLevel(Skill.THIEVING) >= 50
            && getSkills().getRealLevel(Skill.AGILITY) >= 50;
    }

    private boolean validateConfig(Config cfg) {
        if (cfg.runThreshold < 0 || cfg.runRestore > 100 || cfg.runThreshold >= cfg.runRestore) {
            log("Run threshold must be within 0-100 and less than run restore.");
            return false;
        }
        if (cfg.idleMin < 0 || cfg.idleMax < 0 || cfg.idleMin > cfg.idleMax) {
            log("Idle range is invalid.");
            return false;
        }
        if (cfg.breakIntervalMin < 0 || cfg.breakIntervalMax < 0 || cfg.breakIntervalMin > cfg.breakIntervalMax) {
            log("Break interval range is invalid.");
            return false;
        }
        if (cfg.breakLengthMin < 0 || cfg.breakLengthMax < 0 || cfg.breakLengthMin > cfg.breakLengthMax) {
            log("Break length range is invalid.");
            return false;
        }
        return true;
    }

    @Override
    public int onLoop() {
        if (!guiDone.get()) return 600;

        if (!suppliesReady) {
            suppliesReady = prepareSupplies();
            return 600;
        }

        if (!getWalking().isRunEnabled() && getWalking().getRunEnergy() >= config.runRestore) {
            getWalking().toggleRun(true);
        }

        AntiBan.permute(this, abc, config);

        if (handleRewards()) {
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
                    handleMaze();
                }
                return Calculations.random(200, 400);
        }
        return Calculations.random(200, 400);
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
            handleChest();
            return;
        }

        MazeStep current = MAZE_STEPS[step];
        if (getLocalPlayer().distance(current.tile) > 2) {
            getWalking().walk(current.tile);
            Sleep.sleepUntil(() -> getLocalPlayer().distance(current.tile) <= 2, 5000);
            return;
        }
        if ("Squeeze-through".equals(current.action) || "Squeeze".equals(current.action)) {
            handleSqueeze(current);
        } else if ("Search".equals(current.action)) {
            handleSearch(current);
        } else {
            handleObstacle(current);
        }
    }

    private void handleObstacle(MazeStep stepDef) {
        if (stepDef == null || stepDef.name == null || stepDef.action == null) {
            obstacleFailed(stepDef != null ? String.valueOf(stepDef.name) : "Unknown", "invalid MazeStep");
            return;
        }

        GameObject obj = GameObjects.closest(o ->
            o != null &&
            stepDef.name.equals(o.getName()) &&
            o.hasAction(stepDef.action)
        );

        if (obj == null) {
            obstacleFailed(stepDef.name, "object not found");
            return;
        }

        Tile before = getLocalPlayer().getTile();

        if (!obj.interact(stepDef.action)) {
            obstacleFailed(stepDef.name, "interaction failed");
            return;
        }

        boolean started = Sleep.sleepUntil(
            () -> getLocalPlayer().isMoving()
               || getLocalPlayer().isAnimating()
               || (stepDef.animationId != -1 && getLocalPlayer().getAnimation() == stepDef.animationId),
            3000
        );
        if (!started) {
            obstacleFailed(stepDef.name, "no " + stepDef.action.toLowerCase() + " animation/move");
            return;
        }

        Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);

        boolean moved = getLocalPlayer().distance(before) > 1;
        boolean atSuccessTile = stepDef.successTile != null && getLocalPlayer().distance(stepDef.successTile) <= 1;

        if (!moved && !atSuccessTile) {
            obstacleFailed(stepDef.name, "position unchanged");
            return;
        }

        step++;
        lastSafeTile = getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc);
    }

    private void handleSqueeze(MazeStep s) {
        GameObject obj = GameObjects.closest(o ->
            o != null
            && s != null
            && s.name.equals(o.getName())
            && (o.hasAction("Squeeze") || o.hasAction("Squeeze-through"))
        );

        boolean ok = obj != null && (
            obj.hasAction("Squeeze") ? obj.interact("Squeeze")
                                     : obj.interact("Squeeze-through")
        );

        if (!ok) {
            obstacleFailed(s != null ? s.name : "SQUEEZE", "interaction failed");
            return;
        }

        Tile before = getLocalPlayer().getTile();

        if (!Sleep.sleepUntil(() -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating(), 3000)) {
            obstacleFailed(s != null ? s.name : "SQUEEZE", "no squeeze movement");
            return;
        }

        Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);

        if (getLocalPlayer().distance(before) <= 1) {
            obstacleFailed(s != null ? s.name : "SQUEEZE", "position unchanged");
            return;
        }

        step++;
        lastSafeTile = getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc);
    }

    private void handleSearch(MazeStep s) {
        GameObject obj = GameObjects.closest(o ->
            o != null
            && s != null
            && s.name.equals(o.getName())
            && o.hasAction("Search")
        );

        if (obj == null || !obj.interact("Search")) {
            obstacleFailed(s != null ? s.name : "SEARCH", "interaction failed");
            return;
        }

        if (!Sleep.sleepUntil(() -> getLocalPlayer().isAnimating(), 3000)) {
            obstacleFailed(s != null ? s.name : "SEARCH", "no search animation");
            return;
        }

        Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 5000);

        step++;
        lastSafeTile = getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc);
    }

    private void handleChest() {
        if (Inventory.isFull()) {
            log("Inventory full, cannot loot reward chest.");
            step = 0;
            return;
        }

        GameObject chest = GameObjects.closest(o -> o != null && "Chest".equals(o.getName()));
        if (chest == null) {
            log("Reward chest not found.");
            step = 0;
            return;
        }

        int before = Inventory.count(TOKEN_NAME);
        if (!chest.interact("Open")) {
            log("Failed to open reward chest.");
            step = 0;
            return;
        }

        if (Sleep.sleepUntil(() -> Inventory.count(TOKEN_NAME) > before, 5000)) {
            step = 0;
            lastSafeTile = getLocalPlayer().getTile();
            failureCount = 0;
        } else {
            log("No token received from chest.");
            step = 0;
        }
    }

    private void obstacleFailed(String obstacleName, String reason) {
        failureCount++;
        log("Obstacle " + obstacleName + " failed: " + reason);
        if (failureCount > FAILURE_THRESHOLD) {
            log("Failure threshold exceeded, returning to last safe tile");
            getWalking().walk(lastSafeTile);
            Sleep.sleepUntil(() -> getLocalPlayer().distance(lastSafeTile) <= 2, 6000);
            step = 0;
            failureCount = 0;
        }
    }

    private void recoverMaze() {
        log("Recovering maze...");
        getWalking().walk(START_TILE);
        Sleep.sleepUntil(() -> getLocalPlayer().distance(START_TILE) <= 2, 6000);
        step = 0;
        failureCount = 0;
    }

    private boolean handleRewards() {
        if (Inventory.count(TOKEN_NAME) < 1) {
            return false;
        }
        int attempts = 0;
        while (Inventory.count(TOKEN_NAME) >= 1 && attempts < 3) {
            NPC npc = NPCs.closest(REWARD_NPC);
            if (npc != null && npc.interact("Claim")) {
                boolean success = Sleep.sleepUntil(
                    () -> Inventory.contains(i -> i != null && i.getName() != null && isRogueGear(i.getName())),
                    5000
                );
                if (success) {
                    if (hasFullRogueSet()) {
                        log("Full rogue set obtained. Stopping script.");
                        ScriptManager.getScriptManager().stop();
                    }
                    return true;
                } else {
                    log("No gear received, retrying...");
                }
            } else {
                log("Failed to locate reward NPC.");
            }
            attempts++;
            Sleep.sleep(600, 1200);
        }
        if (Inventory.count(TOKEN_NAME) >= 1) {
            log("Failed to obtain gear after multiple attempts.");
        }
        return true;
    }

    private boolean isRogueGear(String name) {
        return name != null && Arrays.asList(GEAR_ITEMS).contains(name);
    }

    private boolean hasFullRogueSet() {
        List<String> missing = new ArrayList<>();
        for (String item : GEAR_ITEMS) {
            if (!Inventory.contains(item)) {
                missing.add(item);
            }
        }
        if (missing.isEmpty()) {
            return true;
        }

        boolean opened = false;
        if (!getBank().isOpen()) {
            if (!getBank().openClosest()) {
                log("Could not open bank to verify rogue set.");
                return false;
            }
            Sleep.sleepUntil(() -> getBank().isOpen(), 5000);
            opened = true;
        }

        boolean allPresent = missing.stream().allMatch(i -> getBank().contains(i));

        if (opened) {
            getBank().close();
            Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
        }

        return allPresent;
    }

    private boolean prepareSupplies() {
        if (suppliesReady) return true;

        boolean haveSetInInv = true;
        for (String item : GEAR_ITEMS) {
            if (!Inventory.contains(item)) { haveSetInInv = false; break; }
        }
        if (!haveSetInInv && !hasFullRogueSet()) {
            log("Missing Rogue gear pieces and not all found in bank.");
            return false;
        }

        Item stamina = Inventory.get(i -> {
            String n = (i == null) ? null : i.getName();
            return n != null && n.contains("Stamina potion");
        });

        boolean opened = false;
        if (stamina == null) {
            if (!getBank().isOpen()) {
                if (!getBank().openClosest()) {
                    log("Could not open bank to withdraw supplies.");
                    return false;
                }
                Sleep.sleepUntil(() -> getBank().isOpen(), 5000);
                opened = true;
            }

            if (getBank().contains(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"))) {
                getBank().withdraw(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"), 1);
                Sleep.sleepUntil(() ->
                    Inventory.contains(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion")),
                    2000
                );
            } else {
                log("No stamina potions available in bank.");
            }
        }

        if (opened) {
            getBank().close();
            Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
        }

        suppliesReady = true;
        return true;
    }

    @Override
    public void onExit() {
        if (gui != null) gui.dispose();
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
        g.drawString("Step: " + step + "/" + MAZE_STEPS.length, 10, y);
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
