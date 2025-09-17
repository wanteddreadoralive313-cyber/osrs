package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.container.impl.equipment.EquipmentSlot;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.wrappers.items.Item;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

@ScriptManifest(category = Category.AGILITY, name = "RoguesDen", author = "Assistant", version = 1.0)
public class RoguesDenScript extends AbstractScript {

    private static final String TOKEN_NAME = "Rogue's reward token";
    private static final String REWARD_NPC = "Rogue";
    private static final String[] GEAR_ITEMS = {
        "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
    };

    private static final int FAILURE_THRESHOLD = 3;
    private static final int BANK_INTERACTION_RANGE = 15;

    private static final long MAX_TRAVEL_TIME_MS = 120_000L;
    private static final int MAX_TRAVEL_PATH_FAILURES = 6;
    private static final int MAX_TRAVEL_RECOVERIES = 3;

    private enum InstructionType {
        HINT,
        MOVE,
        INTERACT,
        GROUND_ITEM,
        STUN_GUARD,
        SKIP
    }

    private boolean isAtTile(Tile tile) {
        return tile != null && getLocalPlayer().distance(tile) <= 1;
    }

    private void markStepComplete() {
        step++;
        lastSafeTile = getLocalPlayer().getTile();
        failureCount = 0;
        AntiBan.sleepReaction(abc, config);
    }

    private void instructionFailed(String label, String reason) {
        failureCount++;
        log("Instruction " + label + " failed: " + reason);
        if (failureCount > FAILURE_THRESHOLD) {
            log("Failure threshold exceeded, returning to last safe tile");
            if (lastSafeTile != null) {
                getWalking().walk(lastSafeTile);
                Sleep.sleepUntil(() -> isAtTile(lastSafeTile) || !getLocalPlayer().isMoving(), 6000);
            }
            failureCount = 0;
        }
    }

    private boolean hasAnyAction(GameObject obj, String[] actions) {
        if (obj == null || actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action != null && obj.hasAction(action)) {
                return true;
            }
        }
        return false;
    }

    private String[] expandActions(MazeInstruction instruction) {
        if (instruction.data == null) {
            return new String[0];
        }
        switch (instruction.data) {
            case "Climb":
                return new String[]{"Climb", "Climb-over", "Climb-up", "Climb-down"};
            case "Cross":
                return new String[]{"Cross", "Cross-quickly", "Walk-across", "Walk-quickly", "Balance", "Jump-across"};
            case "Walk-across":
                return new String[]{"Walk-across", "Walk-quickly", "Cross"};
            case "Open":
                return new String[]{"Open", "Push-open"};
            case "Enter":
                return new String[]{"Enter", "Go-through", "Pass", "Walk-through"};
            case "Search":
                return new String[]{"Search", "Inspect"};
            case "Crack":
                return new String[]{"Crack", "Open"};
            default:
                return new String[]{instruction.data};
        }
    }

    private void handleHint(MazeInstruction instruction) {
        if ("(Drink potion)".equals(instruction.label) && config.useStamina) {
            Item stamina = getStaminaPotion();
            if (stamina != null && stamina.interact("Drink")) {
                Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 3000);
            }
        }
    }

    private void handleMoveInstruction(MazeInstruction instruction) {
        if (isAtTile(instruction.tile)) {
            markStepComplete();
            return;
        }

        if (!getLocalPlayer().isMoving()) {
            if (!getWalking().walk(instruction.tile)) {
                instructionFailed(instruction.label, "could not path to tile");
                return;
            }
        }

        Sleep.sleepUntil(() -> isAtTile(instruction.tile) || !getLocalPlayer().isMoving(), 6000);
        if (isAtTile(instruction.tile)) {
            markStepComplete();
        }
    }

    private void handleInteractInstruction(MazeInstruction instruction) {
        String[] actions = expandActions(instruction);
        GameObject obj = GameObjects.closest(o ->
            o != null
                && o.getTile() != null
                && instruction.tile != null
                && o.getTile().distance(instruction.tile) <= 3
                && hasAnyAction(o, actions)
        );

        if (obj == null) {
            instructionFailed(instruction.label, "object not found");
            return;
        }

        boolean interacted = false;
        for (String action : actions) {
            if (action == null) continue;
            if (obj.hasAction(action) && obj.interact(action)) {
                interacted = true;
                break;
            }
        }

        if (!interacted) {
            instructionFailed(instruction.label, "interaction failed");
            return;
        }

        boolean started = Sleep.sleepUntil(
            () -> getLocalPlayer().isMoving() || getLocalPlayer().isAnimating() || isAtTile(instruction.tile),
            3000
        );
        if (!started) {
            instructionFailed(instruction.label, "no movement/animation");
            return;
        }

        Sleep.sleepUntil(() -> !getLocalPlayer().isMoving() && !getLocalPlayer().isAnimating(), 5000);
        markStepComplete();
    }

    private void handleGroundItemInstruction(MazeInstruction instruction) {
        String targetName = instruction.data;
        boolean needsPickup = targetName != null && !Inventory.contains(targetName);

        if (Inventory.isFull() && needsPickup) {
            if (!ensureInventorySpaceForGroundItem(targetName)) {
                log("Inventory full and no safe item to drop for " + targetName + ". Aborting maze run.");
                recoverMaze();
                return;
            }
        }

        if (instruction.data == null) {
            markStepComplete();
            return;
        }

        if (Inventory.contains(instruction.data)) {
            markStepComplete();
            return;
        }

        if (Inventory.isFull()) {
            log("Inventory still full before taking " + instruction.data + ". Aborting maze run.");
            recoverMaze();
            return;
        }

        GroundItem item = getGroundItems().closest(g ->
            g != null
                && g.getTile() != null
                && instruction.tile != null
                && g.getTile().distance(instruction.tile) <= 4
                && instruction.data.equalsIgnoreCase(g.getName())
        );

        if (item == null) {
            instructionFailed(instruction.label, instruction.data + " not found");
            return;
        }

        if (!item.interact("Take")) {
            instructionFailed(instruction.label, "take failed");
            return;
        }

        if (!Sleep.sleepUntil(() -> Inventory.contains(instruction.data), 3000)) {
            instructionFailed(instruction.label, "item not picked up");
            return;
        }

        markStepComplete();
    }

<// keep BOTH features (guard-stun + inventory helpers)

private boolean isGuardStunned(NPC guard) {
    if (guard == null) {
        return false;
    }
    if (guard.isAnimating()) {
        return true;
    }
    String name = guard.getName();
    return name != null && name.toLowerCase(java.util.Locale.ENGLISH).contains("stun");
}

private boolean ensureInventorySpaceForGroundItem(String targetName) {
    while (Inventory.isFull()) {
        Item droppable = Inventory.get(i -> i != null && i.getName() != null && !isEssentialItem(i));
        if (droppable == null) {
            return false;
        }

        String dropName = droppable.getName();
        int beforeCount = Inventory.count(dropName);
        log("Dropping " + dropName + " to make space for " + targetName + ".");
        if (!droppable.interact("Drop")) {
            log("Failed to drop " + dropName + ".");
            return false;
        }

        boolean spaceFreed = Sleep.sleepUntil(
            () -> !Inventory.isFull() || Inventory.count(dropName) < beforeCount,
            1500
        );

        if (!spaceFreed && Inventory.isFull()) {
            log("Dropping " + dropName + " did not free inventory space.");
            return false;
        }
    }
    return true;
}

private boolean isEssentialItem(Item item) {
    if (item == null) {
        return false;
    }
    String name = item.getName();
    if (name == null) {
        return true;
    }
    if (TOKEN_NAME.equalsIgnoreCase(name)) {
        return true;
    }
    if ("Flash powder".equalsIgnoreCase(name)) {
        return true;
    }
    if (name.contains("Stamina potion")) {
        return true;
    }
    return isRogueGear(name);
}

private void handleGuardInstruction(MazeInstruction instruction) {
    Item powder = Inventory.get(i -> i != null && "Flash powder".equalsIgnoreCase(i.getName()));
    if (powder == null) {
        instructionFailed(instruction.label, "missing flash powder");
        return;
    }
    // ...rest of your guard-handling logic...
}

        }

        String[] actions = guard.getActions();
        if (actions != null) {
            for (String action : actions) {
                if (action != null && action.toLowerCase(Locale.ENGLISH).contains("stun")) {
                    return true;
                }
            }
        }

        return false;
    }

    private void handleGuardInstruction(MazeInstruction instruction) {
        Supplier<NPC> guardSupplier = () -> NPCs.closest(n ->
            n != null
                && n.getName() != null
                && n.getName().equalsIgnoreCase("Rogue guard")
                && n.getTile() != null
                && instruction.tile != null
                && n.getTile().distance(instruction.tile) <= 5
        );

        NPC guard = guardSupplier.get();

        if (guard == null) {
            instructionFailed(instruction.label, "guard not found");
            return;
        }

        int attempts = 0;
        while (attempts < 2) {
            Item powder = Inventory.get(i -> i != null && "Flash powder".equalsIgnoreCase(i.getName()));
            if (powder == null) {
                if (attempts == 0) {
                    instructionFailed(instruction.label, "missing flash powder");
                    return;
                }
                log("No flash powder remaining for additional attempts.");
                break;
            }

            if (!powder.useOn(guard)) {
                instructionFailed(instruction.label, "failed to use flash powder");
                return;
            }

            attempts++;

            boolean stunned = Sleep.sleepUntil(() -> {
                NPC current = guardSupplier.get();
                return current == null || isGuardStunned(current);
            }, Calculations.random(1600, 2200));

            NPC currentGuard = guardSupplier.get();

            if (stunned || currentGuard == null || isGuardStunned(currentGuard)) {
                Sleep.sleep(600, 900);
                markStepComplete();
                return;
            }

            log("Flash powder attempt " + attempts + " failed to stun the guard, retrying.");
            guard = currentGuard;
            Sleep.sleep(300, 600);
        }

        log("Guard remained active after flash powder attempts, retreating to last safe tile.");
        if (lastSafeTile != null) {
            getWalking().walk(lastSafeTile);
            Sleep.sleepUntil(() -> isAtTile(lastSafeTile) || !getLocalPlayer().isMoving(), 6000);
        }
        instructionFailed(instruction.label, "guard not stunned");
    }

    private static class MazeInstruction {
        final Tile tile;
        final String label;
        final InstructionType type;
        final String data;

        MazeInstruction(Tile tile, String label, InstructionType type, String data) {
            this.tile = tile;
            this.label = label;
            this.type = type;
            this.data = data;
        }
    }

    private static class TeleportOption {
        final String keyword;
        final String dialogueOption;
        final EquipmentSlot[] slots;

        TeleportOption(String keyword, String dialogueOption, EquipmentSlot... slots) {
            this.keyword = keyword;
            this.dialogueOption = dialogueOption;
            this.slots = slots == null ? new EquipmentSlot[0] : slots;
        }
    }

new MazeInstruction(new Tile(3000, 5034, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2992, 5045, 1), "Stand", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2992, 5053, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2980, 5044, 1), "(Go east)", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2963, 5056, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2957, 5068, 1), "Enter", InstructionType.INTERACT, "Enter"),
new MazeInstruction(new Tile(2957, 5074, 1), "Stand", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2955, 5094, 1), "Enter", InstructionType.INTERACT, "Enter"),
new MazeInstruction(new Tile(2972, 5098, 1), "Enter", InstructionType.INTERACT, "Enter"),
new MazeInstruction(new Tile(2972, 5094, 1), "Open", InstructionType.INTERACT, "Open"),
new MazeInstruction(new Tile(2976, 5087, 1), "Click", InstructionType.INTERACT, "Search"),
new MazeInstruction(new Tile(2982, 5087, 1), "Climb", InstructionType.INTERACT, "Climb"),
new MazeInstruction(new Tile(2993, 5088, 1), "Search", InstructionType.INTERACT, "Search"),
new MazeInstruction(new Tile(2997, 5088, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3006, 5088, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2989, 5057, 1), "Open", InstructionType.INTERACT, "Open"),
new MazeInstruction(new Tile(2992, 5058, 1), "(Go north)", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2992, 5067, 1), "Stand", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2992, 5075, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(2974, 5061, 1), "Enter", InstructionType.INTERACT, "Enter"),
new MazeInstruction(new Tile(3050, 4997, 1), "Enter", InstructionType.INTERACT, "Enter"),
new MazeInstruction(new Tile(3039, 4999, 1), "Stand", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3029, 5003, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3024, 5001, 1), "Open", InstructionType.INTERACT, "Open"),
new MazeInstruction(new Tile(3011, 5005, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3028, 5033, 1), "Stand", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3024, 5033, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3015, 5033, 1), "Open", InstructionType.INTERACT, "Open"),
new MazeInstruction(new Tile(3010, 5033, 1), "Run/Open", InstructionType.INTERACT, "Open"),
new MazeInstruction(new Tile(3009, 5063, 1), "Take", InstructionType.GROUND_ITEM, "Flash powder"),
new MazeInstruction(new Tile(3014, 5063, 1), "(Stun NPC)", InstructionType.STUN_GUARD, null),
new MazeInstruction(new Tile(3028, 5056, 1), "Run", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3028, 5047, 1), "Walk", InstructionType.INTERACT, "Walk-across"),
new MazeInstruction(new Tile(3039, 5043, 1), "(Go south-west)", InstructionType.MOVE, null),
new MazeInstruction(new Tile(3018, 5047, 1), "Crack", InstructionType.INTERACT, "Crack")
};

private static final TeleportOption[] TELEPORT_OPTIONS = new TeleportOption[]{
    new TeleportOption("Games necklace", "Burthorpe", EquipmentSlot.AMULET),
    new TeleportOption("Combat bracelet", "Warriors' Guild", EquipmentSlot.HANDS)
};

private final ABCUtil abc = new ABCUtil();
private final AtomicBoolean guiDone = new AtomicBoolean(false);
private final AtomicBoolean guiCancelled = new AtomicBoolean(false);
private final Area DEN_AREA = new Area(3040,4970,3050,4980,1);
private final Tile START_TILE = new Tile(3047,4975,1);
private final Tile CHEST_TILE = new Tile(3046,4976,1);

private int step = 0;
private Config config = new Config();
private RoguesDenGUI gui;
private boolean ironman;
private boolean suppliesReady;
private boolean postGuiInitializationComplete;
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

    abc.generateTrackers();


    private final ABCUtil abc = new ABCUtil();
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final AtomicBoolean guiCancelled = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1);
    private final Tile START_TILE = new Tile(3047,4975,1);
    private final Tile CHEST_TILE = new Tile(3046,4976,1);

    private int step = 0;
    private Config config = new Config();
    private RoguesDenGUI gui;
    private boolean ironman;
    private boolean suppliesReady;
    private boolean postGuiInitializationComplete;
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
            gui = new RoguesDenGUI(config, guiDone, guiCancelled);
            gui.setVisible(true);
        });
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

        if (guiCancelled.get()) {
            log("GUI closed before start; stopping script.");
            ScriptManager.getScriptManager().stop();
            return 0;
        }

        if (!postGuiInitializationComplete) {
            if (!validateConfig(config)) {
                log("Invalid configuration; stopping script.");
                ScriptManager.getScriptManager().stop();
                return 0;
            }
            suppliesReady = prepareSupplies();
            postGuiInitializationComplete = true;
            return 600;
        }

        if (!suppliesReady) {
            suppliesReady = prepareSupplies();
            return 600;
        }

        if (!hasRequiredSupplies()) {
            log("Supplies missing, attempting to restock.");
            suppliesReady = false;
            return Calculations.random(300, 600);
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

        Tile lastPosition = getLocalPlayer() != null ? getLocalPlayer().getTile() : null;
        long lastProgressTime = System.currentTimeMillis();
        int pathFailures = 0;
        int recoveryAttempts = 0;

        while (true) {
            if (getLocalPlayer() != null && DEN_AREA.contains(getLocalPlayer())) {
                return;
            }

            if (getLocalPlayer() == null) {
                Sleep.sleep(300, 600);
                continue;
            }

            Tile currentTile = getLocalPlayer().getTile();
            if (currentTile != null && (lastPosition == null || !currentTile.equals(lastPosition))) {
                lastPosition = currentTile;
                lastProgressTime = System.currentTimeMillis();
            }

            long elapsed = System.currentTimeMillis() - lastProgressTime;
            boolean timeExceeded = elapsed > MAX_TRAVEL_TIME_MS;
            boolean failuresExceeded = pathFailures >= MAX_TRAVEL_PATH_FAILURES;

            if (timeExceeded || failuresExceeded) {
                recoveryAttempts++;
                log("Travel to Rogues' Den exceeded limits (failures=" + pathFailures + ", elapsed=" + elapsed + " ms). Recovery attempt " + recoveryAttempts + ".");
                if (recoveryAttempts > MAX_TRAVEL_RECOVERIES || !attemptTravelRecovery()) {
                    stopScriptWithMessage("Unable to reach the Rogues' Den after " + recoveryAttempts + " recovery attempts.");
                    return;
                }

                lastProgressTime = System.currentTimeMillis();
                lastPosition = getLocalPlayer().getTile();
                pathFailures = 0;
                continue;
            }

            if (getLocalPlayer().isAnimating()) {
                Sleep.sleepUntil(() -> !getLocalPlayer().isAnimating(), 15000);
                continue;
            }

            if (getLocalPlayer().isMoving()) {
                Sleep.sleep(200, 300);
                continue;
            }

            if (!getWalking().walk(START_TILE)) {
                pathFailures++;
                log("Failed to generate path to den (" + pathFailures + ")");
                Sleep.sleep(600, 900);
                continue;
            }

            Sleep.sleepUntil(() -> {
                Player player = getLocalPlayer();
                return player != null && (DEN_AREA.contains(player) || player.isMoving());
            }, 15000);
            Player movingPlayer = getLocalPlayer();
            if (movingPlayer != null && movingPlayer.isMoving()) {
                lastProgressTime = System.currentTimeMillis();
            }
            pathFailures = 0;
        }
    }

    private boolean attemptTravelRecovery() {
        log("Initiating travel recovery; attempting to teleport closer to the Rogues' Den.");
        if (!attemptTeleportToDen()) {
            log("No valid teleport options available for travel recovery.");
            return false;
        }

        Sleep.sleepUntil(() -> {
            Player player = getLocalPlayer();
            return player != null && (DEN_AREA.contains(player) || !player.isAnimating());
        }, 20000);
        return true;
    }

    private boolean attemptTeleportToDen() {
        if (DEN_AREA.contains(getLocalPlayer())) {
            return true;
        }

        for (TeleportOption option : TELEPORT_OPTIONS) {
            if (attemptTeleportWithOption(option)) {
                return true;
            }
        }

        if (getMagic().canCast(Normal.HOME_TELEPORT)) {
            log("Travel recovery: casting Home Teleport.");
            if (getMagic().castSpell(Normal.HOME_TELEPORT)) {
                waitForTeleport();
                return true;
            }
        }

        return false;
    }

    private boolean attemptTeleportWithOption(TeleportOption option) {
        if (option == null) {
            return false;
        }

        Item inventoryItem = Inventory.get(item -> matchesTeleportOption(item, option));
        if (inventoryItem != null && useTeleportItem(inventoryItem, option.dialogueOption, Tab.INVENTORY)) {
            log("Travel recovery: used " + inventoryItem.getName() + " to teleport to " + option.dialogueOption + ".");
            return true;
        }

        if (option.slots.length > 0) {
            for (EquipmentSlot slot : option.slots) {
                if (slot == null) {
                    continue;
                }
                Item equipped = Equipment.getItemInSlot(slot);
                if (equipped != null && useTeleportItem(equipped, option.dialogueOption, Tab.EQUIPMENT)) {
                    log("Travel recovery: used " + equipped.getName() + " to teleport to " + option.dialogueOption + ".");
                    return true;
                }
            }
        }

        return false;
    }

    private boolean useTeleportItem(Item item, String destination, Tab tab) {
        if (item == null || destination == null) {
            return false;
        }

        if (!Tabs.isOpen(tab)) {
            if (!Tabs.open(tab)) {
                log("Failed to open " + tab + " tab to use " + item.getName() + ".");
                return false;
            }
            Sleep.sleepUntil(() -> Tabs.isOpen(tab), 1200);
        }

        if (item.hasAction(destination) && item.interact(destination)) {
            waitForTeleport();
            return true;
        }

        if (item.hasAction("Teleport") && item.interact("Teleport")) {
            waitForTeleport();
            return true;
        }

        if (item.hasAction("Rub") && item.interact("Rub")) {
            if (!Sleep.sleepUntil(Dialogues::areOptionsAvailable, 3000)) {
                log("Teleport options did not appear after rubbing " + item.getName() + ".");
                return false;
            }
            if (!Dialogues.chooseOption(destination)) {
                log("Failed to choose teleport option \"" + destination + "\" for " + item.getName() + ".");
                return false;
            }
            Sleep.sleepUntil(() -> !Dialogues.inDialogue(), 2000);
            waitForTeleport();
            return true;
        }

        return false;
    }

    private boolean matchesTeleportOption(Item item, TeleportOption option) {
        return item != null
            && item.getName() != null
            && option != null
            && item.getName().toLowerCase().contains(option.keyword.toLowerCase());
    }

    private boolean waitForTeleport() {
        Sleep.sleepUntil(() -> getLocalPlayer() != null && (getLocalPlayer().isAnimating() || getLocalPlayer().isMoving()), 5000);
        Sleep.sleepUntil(() -> getLocalPlayer() != null && !getLocalPlayer().isAnimating(), 30000);
        Sleep.sleep(600, 900);
        return true;
    }

    private void stopScriptWithMessage(String message) {
        log(message);
        ScriptManager.getScriptManager().stop();
    }

    private void handleRest() {
        log("Waiting for run energy...");
        if (config.useStamina) {
            Item stamina = getStaminaPotion();
            if (stamina != null && stamina.interact("Drink")) {
                Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 3000);
                return;
            }
        }
        Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 60000);
    }

    private void handleMaze() {
        if (getLocalPlayer().distance(START_TILE) <= 1 && step > 0) {
            recoverMaze();
            return;
        }

        if (step >= MAZE_PATH.length) {
            handleChest();
            return;
        }

        MazeInstruction current = MAZE_PATH[step];
        switch (current.type) {
            case SKIP:
                step++;
                break;
            case HINT:
                handleHint(current);
                step++;
                break;
            case MOVE:
                handleMoveInstruction(current);
                break;
            case INTERACT:
                handleInteractInstruction(current);
                break;
            case GROUND_ITEM:
                handleGroundItemInstruction(current);
                break;
            case STUN_GUARD:
                handleGuardInstruction(current);
                break;
        }
    }

    private void handleChest() {
        if (Inventory.isFull()) {
            log("Inventory full, cannot loot reward chest.");
            step = 0;
            return;
        }

        GameObject chest = GameObjects.closest(o ->
            o != null
                && "Chest".equals(o.getName())
                && o.getTile() != null
                && o.getTile().equals(CHEST_TILE)
        );
        if (chest == null) {
            log("Reward chest not found.");
            step = 0;
            return;
        }

        Tile chestTile = chest.getTile();
        if (chestTile == null) {
            log("Reward chest tile unknown.");
            step = 0;
            return;
        }

        if (!getMap().canReach(chestTile)) {
            log("Reward chest is not reachable.");
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
        return handleRewards(false);
    }

    private boolean handleRewards(boolean retriedAfterDelay) {
        if (Inventory.count(TOKEN_NAME) < 1) {
            return false;
        }
        int attempts = 0;
        while (Inventory.count(TOKEN_NAME) >= 1 && attempts < 3) {
            NPC npc = NPCs.closest(REWARD_NPC);
            if (npc != null && npc.interact("Claim")) {
                boolean success = handleRewardDialogue();
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
            boolean tokensCleared = disposeTokens();
            if (!tokensCleared && !retriedAfterDelay && Inventory.count(TOKEN_NAME) >= 1) {
                log("Retrying claim after short delay...");
                Sleep.sleep(1200, 1800);
                return handleRewards(true);
            }
            if (tokensCleared) {
                Sleep.sleep(600, 900);
            }
        }
        return true;
    }
// Wait for any dialogue state to appear
Sleep.sleepUntil(() -> getDialogues().inDialogue()
        || getDialogues().areOptionsAvailable()
        || getDialogues().canContinue()
        || getDialogues().isProcessing(),
    3000
);

// Drive the dialogue to obtain Rogue equipment (timeout ~12s)
long timeout = System.currentTimeMillis() + 12000;
while (System.currentTimeMillis() < timeout) {
    if (Inventory.contains(i -> i != null && i.getName() != null && isRogueGear(i.getName()))) {
        return true;
    }

    boolean dialogueActive = getDialogues().inDialogue()
            || getDialogues().areOptionsAvailable()
            || getDialogues().canContinue()
            || getDialogues().isProcessing();

    if (!dialogueActive) {
        break;
    }

    if (getDialogues().isProcessing()) {
        Sleep.sleep(100, 200);
        continue;
    }

    if (getDialogues().areOptionsAvailable()) {
        if (getDialogues().chooseOption("Rogue equipment")
                || getDialogues().chooseFirstOptionContaining("rogue equipment")) {
            Sleep.sleep(300, 600);
            continue;
        }

        if (getDialogues().chooseFirstOptionContaining("Yes", "Yes please", "Yes, please", "Sure")) {
            Sleep.sleep(300, 600);
            continue;
        }
    }

    if (getDialogues().canContinue() && getDialogues().clickContinue()) {
        Sleep.sleep(300, 600);
        continue;
    }

    Sleep.sleep(150, 300);
}

return Inventory.contains(i -> i != null && i.getName() != null && isRogueGear(i.getName()));

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
    return allPresent;
}

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

        boolean bankOpened = false;

        if (!Inventory.isEmpty()) {
            if (!ensureBankOpen("deposit inventory")) {
                return false;
            }
            bankOpened = true;
            getBank().depositAllItems();
            if (!Sleep.sleepUntil(Inventory::isEmpty, 2000)) {
                log("Unable to fully clear inventory; continuing with remaining items.");
            }
        }

        for (String item : GEAR_ITEMS) {
            if (isGearEquipped(item) || Inventory.contains(item)) {
                continue;
            }

            if (!bankOpened && !ensureBankOpen("withdraw " + item)) {
                return false;
            }

            bankOpened = true;
            if (!getBank().contains(item)) {
                log("Missing Rogue gear piece: " + item);
                closeBank();
                return false;
            }

            getBank().withdraw(item, 1);
            if (!Sleep.sleepUntil(() -> Inventory.contains(item), 2000)) {
                log("Failed to withdraw " + item + ".");
                closeBank();
                return false;
            }
        }

        if (config.useStamina) {
            Item stamina = getStaminaPotion();
            if (stamina == null) {
                if (!bankOpened && !ensureBankOpen("withdraw a stamina potion")) {
                    return false;
                }
                bankOpened = true;
                if (getBank().contains(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"))) {
                    getBank().withdraw(i -> i != null && i.getName() != null && i.getName().contains("Stamina potion"), 1);
                    Sleep.sleepUntil(() -> Inventory.contains(this::isStaminaPotion), 2000);
                } else {
                    log("No stamina potions available in bank.");
                }
            }
        }

        if (bankOpened) {
            if (Inventory.contains("Vial")) {
                getBank().depositAll("Vial");
                Sleep.sleepUntil(() -> !Inventory.contains("Vial"), 2000);
            }
            closeBank();
        }

        for (String item : GEAR_ITEMS) {
            if (isGearEquipped(item)) {
                continue;
            }

            Item gear = Inventory.get(item);
            if (gear == null) {
                log("Failed to locate " + item + " in inventory for equipping.");
                return false;
            }
            if (!gear.interact("Wear")) {
                log("Failed to equip " + item);
                return false;
            }
            if (!Sleep.sleepUntil(() -> isGearEquipped(item), 2000)) {
                log("Could not confirm " + item + " equipped.");
                return false;
            }
        }

        suppliesReady = hasRequiredSupplies();
        if (!suppliesReady) {
            log("Supply verification failed, will retry.");
        }
        return suppliesReady;
    }

    private boolean ensureBankOpen(String context) {
        if (getBank().isOpen()) {
            return true;
        }

        if (!isBankInRange()) {
            String distance = getLocalPlayer() != null
                ? String.format("%.2f", getLocalPlayer().distance(START_TILE))
                : "unknown";
            return handleBankAccessIssue(
                String.format(
                    "Closest bank is out of range (distance %s) while attempting to %s.",
                    distance,
                    context
                )
            );
        }

        if (!getBank().openClosest()) {
            return handleBankAccessIssue("Failed to open the closest bank while attempting to " + context + ".");
        }

        if (!Sleep.sleepUntil(() -> getBank().isOpen(), 5000)) {
            return handleBankAccessIssue("Timed out waiting for bank to open while attempting to " + context + ".");
        }

        return true;
    }

    private boolean handleBankAccessIssue(String message) {
        log(message);
        if (attemptTeleportToSafety()) {
            log("Teleport initiated to recover bank access; will retry once relocated.");
            return false;
        }

        log("Teleport unavailable or failed; stopping script to avoid running without bank access.");
        ScriptManager.getScriptManager().stop();
        return false;
    }

    private boolean attemptTeleportToSafety() {
        if (!getMagic().canCast(Normal.HOME_TELEPORT)) {
            log("Home teleport is not available to recover bank access.");
            return false;
        }

        log("Attempting home teleport to recover bank access...");
        if (!getMagic().castSpell(Normal.HOME_TELEPORT)) {
            log("Failed to cast home teleport while trying to reach a bank.");
            return false;
        }

        boolean started = Sleep.sleepUntil(
            () -> getLocalPlayer() != null && getLocalPlayer().isAnimating(),
            3000
        );
        boolean finished = Sleep.sleepUntil(
            () -> getLocalPlayer() == null || !getLocalPlayer().isAnimating(),
            30000
        );
        if (started && finished) {
            log("Home teleport completed for bank recovery.");
        }
        return true;
    }

    private boolean isBankInRange() {
        return getLocalPlayer() != null && getLocalPlayer().distance(START_TILE) <= BANK_INTERACTION_RANGE;
    }

    private void closeBank() {
        if (!getBank().isOpen()) {
            return;
        }
        getBank().close();
        Sleep.sleepUntil(() -> !getBank().isOpen(), 2000);
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
        g.drawString("Step: " + step + "/" + MAZE_PATH.length, 10, y);
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

    private boolean hasRequiredSupplies() {
        if (!hasFullRogueSetEquipped()) {
            return false;
        }
        if (config.useStamina && !hasStaminaPotion()) {
            return false;
        }
        return true;
    }

    private boolean hasFullRogueSetEquipped() {
        for (String item : GEAR_ITEMS) {
            if (!isGearEquipped(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean isGearEquipped(String item) {
        return getEquipment() != null && getEquipment().contains(item);
    }

    private boolean hasStaminaPotion() {
        return Inventory.contains(this::isStaminaPotion);
    }

    private Item getStaminaPotion() {
        return Inventory.get(this::isStaminaPotion);
    }

    private boolean isStaminaPotion(Item item) {
        return item != null && item.getName() != null && item.getName().contains("Stamina potion");
    }

    // Methods below are package-private to facilitate unit testing
    int getStep() {
        return step;
    }

    int getMazeStepCount() {
        return MAZE_PATH.length;
    }

    void incrementStep() {
        step++;
    }

    static class Config {
        /**
         * Whether to drink stamina potions to restore run energy.
         * True enables potion usage, false avoids it.
         */
        boolean useStamina = true;

        /**
         * Enables ABC2 anti-ban behavior such as reaction time tracking.
         * True to enable, false to disable.
         */
        boolean antiban = true;

        /**
         * Hover the next maze entity before interacting for anti-ban realism.
         */
        boolean hoverEntities = true;

        /**
         * Perform occasional random right-clicks as an anti-ban measure.
         */
        boolean randomRightClick = true;

        /**
         * Randomly pan the camera to mimic human behavior.
         */
        boolean cameraPanning = true;

        /**
         * Minimum idle delay between actions in milliseconds.
         * Must be non-negative and less than or equal to {@link #idleMax}.
         */
        int idleMin = 200;

        /**
         * Maximum idle delay between actions in milliseconds.
         * Must be greater than or equal to {@link #idleMin}.
         */
        int idleMax = 600;

        /**
         * Run energy percentage below which the player should rest.
         * Valid range: 0–100 and must be less than {@link #runRestore}.
         */
        int runThreshold = 20;

        /**
         * Run energy percentage at which running is re-enabled.
         * Valid range: 0–100 and must be greater than {@link #runThreshold}.
         */
        int runRestore = 40;

        /**
         * Minimum minutes between extended breaks.
         * Must be non-negative and less than or equal to {@link #breakIntervalMax}.
         */
        int breakIntervalMin = 30; // minutes

        /**
         * Maximum minutes between extended breaks.
         * Must be greater than or equal to {@link #breakIntervalMin}.
         */
        int breakIntervalMax = 60; // minutes

        /**
         * Minimum length of each break in minutes.
         * Must be non-negative and less than or equal to {@link #breakLengthMax}.
         */
        int breakLengthMin = 1;    // minutes

        /**
         * Maximum length of each break in minutes.
         * Must be greater than or equal to {@link #breakLengthMin}.
         */
        int breakLengthMax = 5;    // minutes
    }
}
