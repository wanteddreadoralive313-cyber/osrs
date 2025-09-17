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
import java.util.Collections;
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

    private static class TeleportOption {
        private final String keyword;
        private final String dialogueOption;
        private final EquipmentSlot[] slots;

        TeleportOption(String keyword, String dialogueOption, EquipmentSlot... slots) {
            this.keyword = keyword;
            this.dialogueOption = dialogueOption;
            this.slots = slots == null ? new EquipmentSlot[0] : Arrays.copyOf(slots, slots.length);
        }

        String getKeyword() {
            return keyword;
        }

        String getDialogueOption() {
            return dialogueOption;
        }

        EquipmentSlot[] getSlots() {
            return slots;
        }
    }

    private static final TeleportOption[] TELEPORT_OPTIONS = {
        new TeleportOption("Games necklace", "Burthorpe", EquipmentSlot.AMULET)
    };

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

    private static final List<MazeInstruction> MAZE_PATH = Collections.unmodifiableList(loadMazePath());

    private static List<MazeInstruction> loadMazePath() {
        List<MazeInstruction> instructions = new ArrayList<>();
        try (InputStream stream = RoguesDenScript.class.getResourceAsStream("maze_path.csv")) {
            if (stream == null) {
                throw new IllegalStateException("Unable to locate maze_path.csv resource");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 3) {
                        throw new IllegalStateException("Invalid maze instruction on line " + lineNumber + ": " + line);
                    }

                    Tile tile = parseTile(parts[0].trim(), lineNumber);
                    String label = parts[1].trim();
                    InstructionType type = parseInstructionType(parts[2].trim(), lineNumber);
                    String data = parts.length > 3 ? parts[3].trim() : null;
                    if (data != null && data.isEmpty()) {
                        data = null;
                    }

                    instructions.add(new MazeInstruction(tile, label, type, data));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load maze_path.csv", e);
        }

        if (instructions.isEmpty()) {
            throw new IllegalStateException("No maze instructions loaded from maze_path.csv");
        }

        return instructions;
    }

    private static Tile parseTile(String raw, int lineNumber) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        String[] coords = raw.split(",");
        if (coords.length != 3) {
            throw new IllegalStateException("Invalid tile coordinates on line " + lineNumber + ": " + raw);
        }

        try {
            int x = Integer.parseInt(coords[0].trim());
            int y = Integer.parseInt(coords[1].trim());
            int z = Integer.parseInt(coords[2].trim());
            return new Tile(x, y, z);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Non-numeric tile coordinate on line " + lineNumber + ": " + raw, e);
        }
    }

    private static InstructionType parseInstructionType(String raw, int lineNumber) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("Missing instruction type on line " + lineNumber);
        }

        try {
            return InstructionType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Unknown instruction type '" + raw + "' on line " + lineNumber, ex);
        }
    }

    private final ABCUtil abc;
    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final AtomicBoolean guiCancelled = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1);
    private final Tile START_TILE = new Tile(3047,4975,1);

    private int step = 0;
    private final Config config;
    private RoguesDenGUI gui;
    private boolean ironman;
    private boolean suppliesReady;
    private int failureCount = 0;
    private Tile lastSafeTile = START_TILE;
    private long startTime;

    private enum State { TRAVEL, MAZE, REST }

    public RoguesDenScript() {
        this(null, null);
    }

    public RoguesDenScript(ABCUtil abcUtil) {
        this(abcUtil, null);
    }

    public RoguesDenScript(Config config) {
        this(null, config);
    }

    public RoguesDenScript(ABCUtil abcUtil, Config config) {
        this.abc = abcUtil != null ? abcUtil : new ABCUtil();
        this.config = config != null ? config : new Config();
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

        ironman = getClient().isIronMan();

        // Initialize ABC2 reaction-time trackers once at script start
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
            prepareSupplies();
        }).start();
    }

    private final AtomicBoolean guiDone = new AtomicBoolean(false);
    private final AtomicBoolean guiCancelled = new AtomicBoolean(false);
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1);
    private final Tile START_TILE = new Tile(3047,4975,1);
    private final Tile CHEST_TILE = new Tile(3046,4976,1);

    private int step = 0;
    private Config config;
    private RoguesDenGUI gui;
    private boolean ironman;
    private boolean suppliesReady;
    private boolean postGuiInitializationComplete;
    private int failureCount = 0;
    private Tile lastSafeTile = START_TILE;
    private long startTime;

    private enum State { TRAVEL, MAZE, REST }

    public RoguesDenScript() {
        this(createABCUtil(), createConfig());
    }

    public RoguesDenScript(ABCUtil abcUtil) {
        this(abcUtil, createConfig());
    }

    public RoguesDenScript(Config config) {
        this(createABCUtil(), config);
    }

    public RoguesDenScript(ABCUtil abcUtil, Config config) {
        this.abc = ensureABCUtil(abcUtil);
        this.config = ensureConfig(config);
    }

    private static ABCUtil createABCUtil() {
        return new ABCUtil();
    }

    private static Config createConfig() {
        return new Config();
    }

    private static ABCUtil ensureABCUtil(ABCUtil abcUtil) {
        return abcUtil != null ? abcUtil : createABCUtil();
    }

    private static Config ensureConfig(Config config) {
        return config != null ? config : createConfig();
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

        config = ensureConfig(config);
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
        if (inventoryItem != null && useTeleportItem(inventoryItem, option.getDialogueOption(), Tab.INVENTORY)) {
            log("Travel recovery: used " + inventoryItem.getName() + " to teleport to " + option.getDialogueOption() + ".");
            return true;
        }

        for (EquipmentSlot slot : option.getSlots()) {
            if (slot == null) {
                continue;
            }
            Item equipped = Equipment.getItemInSlot(slot);
            if (equipped != null && useTeleportItem(equipped, option.getDialogueOption(), Tab.EQUIPMENT)) {
                log("Travel recovery: used " + equipped.getName() + " to teleport to " + option.getDialogueOption() + ".");
                return true;
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
            && item.getName().toLowerCase().contains(option.getKeyword().toLowerCase());
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

        if (step >= MAZE_PATH.size()) {
            handleChest();
            return;
        }

        MazeInstruction current = MAZE_PATH.get(step);
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

    private boolean handleRewardDialogue() {
        Sleep.sleepUntil(() -> getDialogues().inDialogue()
                || getDialogues().areOptionsAvailable()
                || getDialogues().canContinue()
                || getDialogues().isProcessing(),
            3000
        );

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
    }

    private boolean disposeTokens() {
        int tokenCount = Inventory.count(TOKEN_NAME);
        if (tokenCount < 1) {
            return true;
        }

        log("Disposing of leftover rogue tokens: " + tokenCount);

        boolean disposed = false;
        boolean openedBankHere = false;

        if (!getBank().isOpen()) {
            if (ensureBankOpen("deposit leftover reward tokens")) {
                openedBankHere = true;
            }
        }

        if (getBank().isOpen()) {
            getBank().depositAll(TOKEN_NAME);
            disposed = Sleep.sleepUntil(() -> !Inventory.contains(TOKEN_NAME), 2000);
            if (!disposed) {
                log("Failed to deposit rogue tokens; attempting to drop them.");
            }
        }

        if (openedBankHere) {
            closeBank();
        }

        if (!disposed) {
            int attempts = 0;
            while (Inventory.contains(TOKEN_NAME) && attempts < 5) {
                Item token = Inventory.get(TOKEN_NAME);
                if (token == null || !token.interact("Drop")) {
                    break;
                }
                Sleep.sleep(200, 400);
                Sleep.sleepUntil(() -> !Inventory.contains(TOKEN_NAME), 1200);
                attempts++;
            }
            disposed = !Inventory.contains(TOKEN_NAME);
        }

        if (disposed) {
            step = 0;
            failureCount = 0;
            return true;
        }

        log("Unable to remove rogue tokens after reward failure.");
        return false;
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
        g.drawString("Step: " + step + "/" + MAZE_PATH.size(), 10, y);
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

    private boolean ensureInventorySpaceForGroundItem(String targetName) {
        if (!Inventory.isFull()) {
            return true;
        }

        while (Inventory.isFull()) {
            boolean droppedItem = false;
            int extraFlashPowder = Math.max(0, Inventory.count("Flash powder") - 1);

            for (Item item : Inventory.all()) {
                if (item == null || item.getName() == null) {
                    continue;
                }

                String name = item.getName();

                if (name.isEmpty()) {
                    continue;
                }

                if (TOKEN_NAME.equalsIgnoreCase(name)) {
                    continue;
                }

                if (isStaminaPotion(item)) {
                    continue;
                }

                if (isRogueGear(name)) {
                    continue;
                }

                if (targetName != null && targetName.equalsIgnoreCase(name)) {
                    continue;
                }

                boolean shouldDrop = false;

                if ("Vial".equalsIgnoreCase(name)) {
                    shouldDrop = true;
                } else if (name.contains("(0)")) {
                    shouldDrop = true;
                } else if ("Flash powder".equalsIgnoreCase(name) && extraFlashPowder > 0) {
                    shouldDrop = true;
                }

                if (!shouldDrop) {
                    continue;
                }

                if (!item.interact("Drop")) {
                    continue;
                }

                droppedItem = true;

                if ("Flash powder".equalsIgnoreCase(name) && extraFlashPowder > 0) {
                    extraFlashPowder--;
                }

                if (Sleep.sleepUntil(() -> !Inventory.isFull(), 1800)) {
                    return true;
                }

                break;
            }

            if (!droppedItem) {
                return false;
            }
        }

        return true;
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
        return MAZE_PATH.size();
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
