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
import java.lang.reflect.ReflectiveOperationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

@ScriptManifest(category = Category.AGILITY, name = "RoguesDen", author = "Assistant", version = 1.0)
public class RoguesDenScript extends AbstractScript {

    private static final String REWARD_CRATE_NAME = "Rogue equipment crate";
    private static final String REWARD_NPC = "Rogue";
    private static final String FLASH_POWDER_NAME = "Flash powder";
    private static final String ROGUE_GUARD_NAME = "Rogue Guard";
    private static final String[] GEAR_ITEMS = {
        "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots"
    };

    private static final int STAMINA_DOSES_PER_POTION = 4;

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

    enum MazeRoute {
        LONG,
        SHORTCUT
    }

    private enum InstructionType {
        HINT,
        MOVE,
        INTERACT,
        GROUND_ITEM,
        STUN_GUARD,
        SKIP
    }

    private boolean isAtTile(Player player, Tile tile) {
        return player != null && tile != null && player.distance(tile) <= 1;
    }

    private void markStepComplete() {
        step++;
        Player player = getLocalPlayer();
        if (player != null) {
            lastSafeTile = player.getTile();
        }
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
                Sleep.sleepUntil(() -> {
                    Player player = getLocalPlayer();
                    return player != null && (isAtTile(player, lastSafeTile) || !player.isMoving());
                }, 6000);
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
        Player player = getLocalPlayer();
        if (isAtTile(player, instruction.tile)) {
            markStepComplete();
            return;
        }

        if (player == null || !player.isMoving()) {
            if (!getWalking().walk(instruction.tile)) {
                instructionFailed(instruction.label, "could not path to tile");
                return;
            }
        }

        Sleep.sleepUntil(() -> {
            Player current = getLocalPlayer();
            return current != null && (isAtTile(current, instruction.tile) || !current.isMoving());
        }, 6000);
        player = getLocalPlayer();
        if (isAtTile(player, instruction.tile)) {
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
            () -> {
                Player current = getLocalPlayer();
                return current != null
                    && (current.isMoving() || current.isAnimating() || isAtTile(current, instruction.tile));
            },
            3000
        );
        if (!started) {
            instructionFailed(instruction.label, "no movement/animation");
            return;
        }

        Sleep.sleepUntil(() -> {
            Player current = getLocalPlayer();
            return current != null && !current.isMoving() && !current.isAnimating();
        }, 5000);
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

    private void handleGuardInstruction(MazeInstruction instruction) {
        if (!Inventory.contains(FLASH_POWDER_NAME)) {
            instructionFailed(instruction.label, "Flash powder missing");
            return;
        }

        if (instruction.tile == null) {
            instructionFailed(instruction.label, "missing guard tile");
            return;
        }

        NPC guard = NPCs.closest(n ->
            n != null
                && ROGUE_GUARD_NAME.equals(n.getName())
                && n.getTile() != null
                && n.getTile().distance(instruction.tile) <= 6
        );

        if (guard == null) {
            instructionFailed(instruction.label, "guard not found");
            return;
        }

        if (!Tabs.isOpen(Tab.INVENTORY)) {
            if (!Tabs.open(Tab.INVENTORY) || !Sleep.sleepUntil(() -> Tabs.isOpen(Tab.INVENTORY), 1200)) {
                instructionFailed(instruction.label, "failed to open inventory");
                return;
            }
        }

        Item powder = Inventory.get(FLASH_POWDER_NAME);
        if (powder == null) {
            instructionFailed(instruction.label, "Flash powder missing");
            return;
        }

        int initialCount = Inventory.count(FLASH_POWDER_NAME);
        int initialAnimation = guard.getAnimation();
        boolean actionInitiated = false;

        if (powder.hasAction("Throw") && powder.interact("Throw")) {
            boolean consumedOrSelected = Sleep.sleepUntil(
                () -> Inventory.count(FLASH_POWDER_NAME) < initialCount || Inventory.isItemSelected(),
                1500
            );

            if (!consumedOrSelected) {
                actionInitiated = false;
            } else if (Inventory.count(FLASH_POWDER_NAME) < initialCount) {
                actionInitiated = true;
            } else {
                String selectedName = Inventory.getSelectedItemName();
                if (selectedName != null && selectedName.equalsIgnoreCase(FLASH_POWDER_NAME)) {
                    actionInitiated = guard.interact(action -> action != null && action.startsWith("Use"));
                }
            }
        }

        if (!actionInitiated) {
            if (Inventory.isItemSelected()) {
                Inventory.deselect();
            }

            powder = Inventory.get(FLASH_POWDER_NAME);
            if (powder != null && powder.useOn(guard)) {
                actionInitiated = true;
            } else if (powder != null && powder.hasAction("Use") && powder.interact("Use")) {
                boolean selected = Sleep.sleepUntil(Inventory::isItemSelected, 1500);
                if (selected) {
                    String selectedName = Inventory.getSelectedItemName();
                    if (selectedName != null && selectedName.equalsIgnoreCase(FLASH_POWDER_NAME)) {
                        actionInitiated = guard.interact(action -> action != null && action.startsWith("Use"));
                    }
                }
            }
        }

        if (!actionInitiated) {
            instructionFailed(instruction.label, "failed to use flash powder");
            return;
        }

        boolean effectObserved = Sleep.sleepUntil(
            () -> Inventory.count(FLASH_POWDER_NAME) < initialCount || isGuardStunned(guard, initialAnimation),
            5000
        );

        if (!effectObserved) {
            instructionFailed(instruction.label, "guard not stunned");
            return;
        }

        markStepComplete();
    }

    private boolean isGuardStunned(NPC guard, int initialAnimation) {
        return guard != null
            && guard.exists()
            && guard.isAnimating()
            && guard.getAnimation() != initialAnimation;
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

    private static class MazePaths {
        final List<MazeInstruction> longRoute;
        final List<MazeInstruction> shortcutRoute;

        MazePaths(List<MazeInstruction> longRoute, List<MazeInstruction> shortcutRoute) {
            this.longRoute = Collections.unmodifiableList(new ArrayList<>(longRoute));
            this.shortcutRoute = Collections.unmodifiableList(new ArrayList<>(shortcutRoute));
        }
    }

    private static final MazePaths MAZE_PATHS = loadMazePaths();

    private static MazePaths loadMazePaths() {
        List<MazeInstruction> longRoute = new ArrayList<>();
        List<MazeInstruction> shortcutRoute = new ArrayList<>();
        List<MazeInstruction> target = longRoute;
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
                    if (line.isEmpty()) {
                        continue;
                    }

                    if (line.startsWith("#")) {
                        String lower = line.toLowerCase(Locale.ROOT);
                        if (lower.startsWith("# route:")) {
                            String routeName = lower.substring("# route:".length()).trim();
                            if ("long".equals(routeName)) {
                                target = longRoute;
                            } else if ("shortcut".equals(routeName)) {
                                target = shortcutRoute;
                            } else {
                                throw new IllegalStateException("Unknown maze route '" + routeName + "' on line " + lineNumber);
                            }
                        }
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

                    target.add(new MazeInstruction(tile, label, type, data));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load maze_path.csv", e);
        }

        if (longRoute.isEmpty()) {
            throw new IllegalStateException("No long route instructions loaded from maze_path.csv");
        }
        if (shortcutRoute.isEmpty()) {
            throw new IllegalStateException("No shortcut route instructions loaded from maze_path.csv");
        }

        return new MazePaths(longRoute, shortcutRoute);
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

    private List<MazeInstruction> getMazePath(MazeRoute route) {
        return route == MazeRoute.SHORTCUT ? MAZE_PATHS.shortcutRoute : MAZE_PATHS.longRoute;
    }

    private List<MazeInstruction> getActiveMazePath() {
        return getMazePath(activeMazeRoute);
    }

    private int getActiveMazePathSize() {
        return getActiveMazePath().size();
    }

    MazeRoute determineMazeRouteForLevel(int thievingLevel) {
        boolean useShortcut = config != null && config.shouldUseShortcut(thievingLevel);
        if (useShortcut && !MAZE_PATHS.shortcutRoute.isEmpty()) {
            return MazeRoute.SHORTCUT;
        }
        return MazeRoute.LONG;
    }

    private void ensureActiveMazeRouteSelected() {
        if (step == 0) {
            activeMazeRoute = determineMazeRouteForLevel(getThievingLevel());
        }
    }

    protected int getThievingLevel() {
        return getSkills().getRealLevel(Skill.THIEVING);
    }

    private final ABCUtil abc;
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
    private volatile boolean postGuiInitializationComplete;
    private int failureCount = 0;
    private Tile lastSafeTile = START_TILE;
    private Tile cachedBankTile;
    private Tile bankTravelTarget;
    private long startTime;
    private MazeRoute activeMazeRoute = MazeRoute.LONG;

    private enum State { TRAVEL, MAZE, REST }

    enum HealthCheckResult {
        NO_ACTION,
        CONSUMED_FOOD,
        NEEDS_RESTOCK
    }

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
            postGuiInitializationComplete = true;
        }).start();
    }

    private boolean meetsRequirements() {
        return getThievingLevel() >= 50
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
            cfg.breakLengthMax,
            cfg.staminaDoseTarget,
            cfg.staminaDoseThreshold,
            cfg.minimumHealthPercent
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
            return 600;
        }

        if (!suppliesReady) {
            Player local = getLocalPlayer();
            if (local == null) {
                return Calculations.random(200, 400);
            }

            if (!isBankInRange()) {
                handleTravel(local);
                return Calculations.random(300, 600);
            }

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

        Player local = getLocalPlayer();
        if (local == null) {
            return Calculations.random(200, 400);
        }

        if (handleRewards()) {
            return Calculations.random(600, 900);
        }

        State state = getState(local);
        switch (state) {
            case TRAVEL:
                handleTravel(local);
                Player travelPlayer = getLocalPlayer();
                if (travelPlayer != null && !DEN_AREA.contains(travelPlayer)) {
                    getWalking().walk(START_TILE);
                    Sleep.sleepUntil(() -> {
                        Player player = getLocalPlayer();
                        return player != null && DEN_AREA.contains(player);
                    }, 12000);
                }
                return Calculations.random(300, 600);
            case REST:
                handleRest();
                return Calculations.random(600, 900);
            case MAZE:
                Player mazePlayer = getLocalPlayer();
                if (mazePlayer != null && !mazePlayer.isAnimating() && !mazePlayer.isMoving()) {
                    HealthCheckResult healthResult = handleHealthMaintenance();
                    if (healthResult == HealthCheckResult.CONSUMED_FOOD) {
                        return Calculations.random(300, 600);
                    }
                    if (healthResult == HealthCheckResult.NEEDS_RESTOCK) {
                        return Calculations.random(300, 600);
                    }
                    handleMaze(mazePlayer);
                }
                return Calculations.random(200, 400);
        }
        return Calculations.random(200, 400);
    }

    HealthCheckResult handleHealthMaintenance() {
        if (!shouldMonitorHealth()) {
            return HealthCheckResult.NO_ACTION;
        }
        final String foodName = getConfiguredFoodName();
        return handleHealthMaintenance(
            () -> {
                if (getHealth() == null) {
                    return 100;
                }
                return getHealth().getPercentage();
            },
            () -> Inventory.contains(foodName),
            () -> Inventory.get(foodName)
        );
    }

    HealthCheckResult handleHealthMaintenance(IntSupplier healthSupplier,
                                             BooleanSupplier foodAvailableSupplier,
                                             Supplier<Item> foodItemSupplier) {
        if (!shouldMonitorHealth()) {
            return HealthCheckResult.NO_ACTION;
        }

        int healthPercent = healthSupplier.getAsInt();
        if (healthPercent >= config.minimumHealthPercent) {
            return HealthCheckResult.NO_ACTION;
        }

        String foodName = getConfiguredFoodName();
        if (foodName.isEmpty()) {
            return HealthCheckResult.NO_ACTION;
        }

        if (!foodAvailableSupplier.getAsBoolean()) {
            log("Out of " + foodName + "; marking supplies for restock.");
            suppliesReady = false;
            return HealthCheckResult.NEEDS_RESTOCK;
        }

        Item food = foodItemSupplier.get();
        if (food == null || !food.interact("Eat")) {
            log("Failed to eat " + foodName + "; marking supplies for restock.");
            suppliesReady = false;
            return HealthCheckResult.NEEDS_RESTOCK;
        }

        return HealthCheckResult.CONSUMED_FOOD;
    }

    private State getState(Player player) {
        if (player == null || !DEN_AREA.contains(player))
            return State.TRAVEL;
        if (needsRest())
            return State.REST;
        return State.MAZE;
    }

    private boolean needsRest() {
        return getWalking().getRunEnergy() < config.runThreshold;
    }

    private void handleTravel(Player player) {
        if (player != null && DEN_AREA.contains(player)) {
            return;
        }

        Tile lastPosition = player != null ? player.getTile() : null;
        long lastProgressTime = System.currentTimeMillis();
        int pathFailures = 0;
        int recoveryAttempts = 0;

        while (true) {
            Player currentPlayer = getLocalPlayer();
            if (currentPlayer != null && DEN_AREA.contains(currentPlayer)) {
                return;
            }

            if (currentPlayer == null) {
                Sleep.sleep(300, 600);
                continue;
            }

            Tile currentTile = currentPlayer.getTile();
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
                Player refreshed = getLocalPlayer();
                lastPosition = refreshed != null ? refreshed.getTile() : null;
                pathFailures = 0;
                continue;
            }

            if (currentPlayer.isAnimating()) {
                Sleep.sleepUntil(() -> {
                    Player player = getLocalPlayer();
                    return player != null && !player.isAnimating();
                }, 15000);
                continue;
            }

            if (currentPlayer.isMoving()) {
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
        Player player = getLocalPlayer();
        if (player != null && DEN_AREA.contains(player)) {
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
            if (stamina == null) {
                log("Out of stamina potions; restocking.");
                suppliesReady = false;
                return;
            }
            if (stamina.interact("Drink")) {
                Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 3000);
                return;
            }
        }
        Sleep.sleepUntil(() -> getWalking().getRunEnergy() > config.runRestore, 60000);
    }

    private void handleMaze(Player player) {
        if (player != null && player.distance(START_TILE) <= 1 && step > 0) {
            recoverMaze();
            return;
        }

        ensureActiveMazeRouteSelected();
        List<MazeInstruction> path = getActiveMazePath();

        if (step >= path.size()) {
            handleChest();
            return;
        }

        MazeInstruction current = path.get(step);
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
            if (Inventory.contains(REWARD_CRATE_NAME)) {
                log("Inventory full due to leftover rogue equipment crate; clearing it before looting chest.");
                if (!clearRewardCrates(false) || Inventory.isFull()) {
                    log("Unable to clear rogue equipment crate before opening reward chest.");
                    step = 0;
                    return;
                }
            } else {
                log("Inventory full, cannot loot reward chest.");
                step = 0;
                return;
            }
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

        int before = Inventory.count(REWARD_CRATE_NAME);
        boolean hadCrateBefore = before > 0 || Inventory.contains(REWARD_CRATE_NAME);
        if (!chest.interact("Open")) {
            log("Failed to open reward chest.");
            step = 0;
            return;
        }

        boolean crateReceived = Sleep.sleepUntil(() ->
                Inventory.count(REWARD_CRATE_NAME) > before
                    || (!hadCrateBefore && Inventory.contains(REWARD_CRATE_NAME)),
            5000
        );

        if (crateReceived) {
            Player player = getLocalPlayer();
            if (player != null) {
                lastSafeTile = player.getTile();
            }
            failureCount = 0;
        } else {
            log("No rogue equipment crate received from chest.");
        }
        step = 0;
    }

    private void obstacleFailed(String obstacleName, String reason) {
        failureCount++;
        log("Obstacle " + obstacleName + " failed: " + reason);
        if (failureCount > FAILURE_THRESHOLD) {
            log("Failure threshold exceeded, returning to last safe tile");
            getWalking().walk(lastSafeTile);
            Sleep.sleepUntil(() -> {
                Player player = getLocalPlayer();
                return player != null && lastSafeTile != null && player.distance(lastSafeTile) <= 2;
            }, 6000);
            step = 0;
            failureCount = 0;
        }
    }

    private void recoverMaze() {
        log("Recovering maze...");
        getWalking().walk(START_TILE);
        Sleep.sleepUntil(() -> {
            Player player = getLocalPlayer();
            return player != null && player.distance(START_TILE) <= 2;
        }, 6000);
        step = 0;
        failureCount = 0;
    }

    boolean handleRewards() {
        return handleRewards(false);
    }

    private boolean handleRewards(boolean retriedAfterDelay) {
        if (config.rewardTarget == Config.RewardTarget.KEEP_CRATES) {
            if (Inventory.count(REWARD_CRATE_NAME) < 1) {
                return false;
            }
            boolean stored = clearRewardCrates(true);
            if (!stored) {
                log("Unable to store rogue equipment crate; will retry later.");
            }
            return true;
        }

        if (Inventory.count(REWARD_CRATE_NAME) < 1) {
            return false;
        }
        int attempts = 0;
        while (Inventory.count(REWARD_CRATE_NAME) >= 1 && attempts < 3) {
            NPC npc = NPCs.closest(REWARD_NPC);
            if (npc != null && npc.interact("Claim")) {
                boolean success = handleRewardDialogue();
                if (success) {
                    if (config.rewardTarget == Config.RewardTarget.ROGUE_EQUIPMENT
                        && config.stopAfterFullSet
                        && hasFullRogueSet()) {
                        log("Full rogue set obtained. Stopping script.");
                        ScriptManager.getScriptManager().stop();
                    }
                    depositDuplicateRogueGear();
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
        if (Inventory.count(REWARD_CRATE_NAME) >= 1) {
            log("Failed to obtain gear after multiple attempts.");
            boolean cratesCleared = clearRewardCrates(true);
            if (!cratesCleared && !retriedAfterDelay && Inventory.count(REWARD_CRATE_NAME) >= 1) {
                log("Retrying claim after short delay...");
                Sleep.sleep(1200, 1800);
                return handleRewards(true);
            }
            if (cratesCleared) {
                Sleep.sleep(600, 900);
            }
        }
        return true;
    }

    private void depositDuplicateRogueGear() {
        Map<String, Integer> depositPlan = new HashMap<>();
        for (String item : GEAR_ITEMS) {
            int keep = isGearEquipped(item) ? 0 : 1;
            int count = Inventory.count(item);
            if (count > keep) {
                depositPlan.put(item, count - keep);
            }
        }

        if (depositPlan.isEmpty()) {
            return;
        }

        boolean openedHere = false;
        if (!getBank().isOpen()) {
            if (!ensureBankOpen("deposit duplicate rogue gear")) {
                return;
            }
            openedHere = true;
        }

        for (Map.Entry<String, Integer> entry : depositPlan.entrySet()) {
            String item = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) {
                continue;
            }

            if (!getBank().deposit(item, amount)) {
                log("Failed to deposit duplicate " + item + ".");
                continue;
            }

            int keep = isGearEquipped(item) ? 0 : 1;
            Sleep.sleepUntil(() -> Inventory.count(item) <= keep, 2000);
        }

        if (openedHere) {
            closeBank();
        }
    }

    protected boolean handleRewardDialogue() {
        Sleep.sleepUntil(() -> getDialogues().inDialogue()
                || getDialogues().areOptionsAvailable()
                || getDialogues().canContinue()
                || getDialogues().isProcessing(),
            3000
        );

        long timeout = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < timeout) {
            if (hasReceivedTargetReward()) {
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
                String option = config.rewardTarget.getDialogueOptionText();
                String containsText = config.rewardTarget.getDialogueContainsText();
                boolean handled = false;
                if (option != null && getDialogues().chooseOption(option)) {
                    handled = true;
                } else if (containsText != null
                    && getDialogues().chooseFirstOptionContaining(containsText)) {
                    handled = true;
                }

                if (handled) {
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

        return hasReceivedTargetReward();
    }

    boolean hasReceivedTargetReward() {
        switch (config.rewardTarget) {
            case ROGUE_EQUIPMENT:
                return Inventory.contains(i -> i != null && i.getName() != null && isRogueGear(i.getName()));
            case ROGUE_KIT:
                return Inventory.contains("Rogue kit");
            case KEEP_CRATES:
            default:
                return false;
        }
    }

    private boolean clearRewardCrates(boolean resetProgress) {
        int crateCount = Inventory.count(REWARD_CRATE_NAME);
        if (crateCount < 1) {
            return true;
        }

        log("Clearing leftover rogue equipment crates: " + crateCount);

        boolean cleared = false;
        boolean openedBankHere = false;

        if (!getBank().isOpen()) {
            if (ensureBankOpen("deposit leftover reward crates")) {
                openedBankHere = true;
            }
        }

        if (getBank().isOpen()) {
            getBank().depositAll(REWARD_CRATE_NAME);
            cleared = Sleep.sleepUntil(() -> !Inventory.contains(REWARD_CRATE_NAME), 2000);
            if (!cleared) {
                log("Failed to deposit rogue equipment crates; attempting to drop them.");
            }
        }

        if (openedBankHere) {
            closeBank();
        }

        if (!cleared) {
            int attempts = 0;
            while (Inventory.contains(REWARD_CRATE_NAME) && attempts < 5) {
                Item crate = Inventory.get(REWARD_CRATE_NAME);
                if (crate == null || !crate.interact("Drop")) {
                    break;
                }
                Sleep.sleep(200, 400);
                Sleep.sleepUntil(() -> !Inventory.contains(REWARD_CRATE_NAME), 1200);
                attempts++;
            }
            cleared = !Inventory.contains(REWARD_CRATE_NAME);
        }

        if (cleared && resetProgress) {
            step = 0;
            failureCount = 0;
        }

        if (!cleared) {
            log("Unable to remove rogue equipment crates.");
        }

        return cleared;
    }

    private boolean isRogueGear(String name) {
        return name != null && Arrays.asList(GEAR_ITEMS).contains(name);
    }

    boolean hasFullRogueSet() {
        List<String> missing = new ArrayList<>();
        for (String item : GEAR_ITEMS) {
            if (getEquipment() != null && getEquipment().contains(item)) {
                continue;
            }

            if (Inventory.contains(item)) {
                continue;
            }

            missing.add(item);
        }
        if (missing.isEmpty()) {
            return true;
        }

        boolean openedHere = false;
        if (!getBank().isOpen()) {
            if (!getBank().openClosest()) {
                log("Could not open bank to verify rogue set.");
                return false;
            }
            if (!Sleep.sleepUntil(() -> getBank().isOpen(), 5000)) {
                log("Timed out waiting for bank to open while verifying rogue set.");
                return false;
            }
            openedHere = true;
        }

        boolean allPresent = missing.stream().allMatch(i -> getBank().contains(i));

        if (openedHere) {
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
            int currentDoses = getStaminaDoseCount();
            int potionsNeeded = getRequiredStaminaPotions(currentDoses);
            if (potionsNeeded > 0) {
                if (!bankOpened && !ensureBankOpen("withdraw stamina potions")) {
                    return false;
                }
                bankOpened = true;
                if (!withdrawStaminaPotions(potionsNeeded)) {
                    closeBank();
                    return false;
                }
            }
        }

        String foodName = getConfiguredFoodName();
        if (!foodName.isEmpty() && !Inventory.contains(foodName)) {
            if (!bankOpened && !ensureBankOpen("withdraw " + foodName)) {
                return false;
            }
            bankOpened = true;
            if (!getBank().contains(foodName)) {
                log("Missing preferred food item: " + foodName);
                closeBank();
                return false;
            }
            getBank().withdraw(foodName, 1);
            if (!Sleep.sleepUntil(() -> Inventory.contains(foodName), 2000)) {
                log("Failed to withdraw preferred food item " + foodName + ".");
                closeBank();
                return false;
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
            bankTravelTarget = null;
            return true;
        }

        Tile bankTile = getNearestBankTile();
        if (bankTile == null) {
            return handleBankAccessIssue(
                "Unable to locate any reachable bank while attempting to " + context + ".",
                true
            );
        }

        Player player = getLocalPlayer();
        if (player == null) {
            return false;
        }

        double distance = player.distance(bankTile);
        if (!Double.isFinite(distance)) {
            return false;
        }

        if (distance > BANK_INTERACTION_RANGE) {
            if (player.isMoving() && Objects.equals(bankTravelTarget, bankTile)) {
                return false;
            }

            if (!initiateBankTravel(bankTile)) {
                return handleBankAccessIssue(
                    "Failed to generate a path to the nearest bank while attempting to " + context + ".",
                    true
                );
            }

            return false;
        }

        bankTravelTarget = null;

        if (player.isMoving()) {
            return false;
        }

        if (!getBank().openClosest()) {
            return handleBankAccessIssue(
                "Failed to open the closest bank while attempting to " + context + ".",
                false
            );
        }

        if (!Sleep.sleepUntil(() -> getBank().isOpen(), 5000)) {
            return handleBankAccessIssue(
                "Timed out waiting for bank to open while attempting to " + context + ".",
                false
            );
        }

        cachedBankTile = bankTile;
        return true;
    }

    private boolean handleBankAccessIssue(String message, boolean attemptRecovery) {
        log(message);
        if (attemptRecovery && attemptTeleportToSafety()) {
            log("Teleport initiated to recover bank access; will retry once relocated.");
            cachedBankTile = null;
            bankTravelTarget = null;
            return false;
        }

        if (attemptRecovery) {
            log("Teleport unavailable or failed; stopping script to avoid running without bank access.");
        } else {
            log("Stopping script to avoid running without bank access.");
        }
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
        Player player = getLocalPlayer();
        Tile bankTile = getNearestBankTile();
        if (player == null || bankTile == null) {
            return false;
        }
        double distance = player.distance(bankTile);
        return Double.isFinite(distance) && distance <= BANK_INTERACTION_RANGE;
    }

    private Tile getNearestBankTile() {
        Tile resolved = resolveNearestReachableBankTile();
        if (resolved != null) {
            cachedBankTile = resolved;
        }
        return cachedBankTile;
    }

    Tile resolveNearestReachableBankTile() {
        Tile viaApi = resolveNearestBankViaApi();
        if (viaApi != null) {
            return viaApi;
        }

        GameObject bankObject = GameObjects.closest(obj -> obj != null && obj.hasAction("Bank"));
        return bankObject != null ? bankObject.getTile() : null;
    }

    private Tile resolveNearestBankViaApi() {
        Object bank = getBank();
        if (bank == null) {
            return null;
        }

        try {
            Object location = bank.getClass().getMethod("getClosestBankLocation").invoke(bank);
            Tile tile = resolveBankTileFromLocation(location);
            if (tile != null) {
                return tile;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        try {
            Object closest = bank.getClass().getMethod("getClosestBank").invoke(bank);
            if (closest instanceof GameObject) {
                return ((GameObject) closest).getTile();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        return null;
    }

    private Tile resolveBankTileFromLocation(Object location) {
        if (location == null) {
            return null;
        }

        String[] accessors = {"getCenter", "getTile", "getPosition"};
        for (String accessor : accessors) {
            try {
                Object result = location.getClass().getMethod(accessor).invoke(location);
                if (result instanceof Tile) {
                    return (Tile) result;
                }
            } catch (ReflectiveOperationException | SecurityException ignored) {
            }
        }

        try {
            Object area = location.getClass().getMethod("getArea").invoke(location);
            if (area instanceof Area) {
                return ((Area) area).getCenter();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        return null;
    }

    private boolean initiateBankTravel(Tile bankTile) {
        if (bankTile == null) {
            return false;
        }

        if (getWalking() == null) {
            return false;
        }

        Player player = getLocalPlayer();
        if (player != null && player.isMoving() && Objects.equals(bankTravelTarget, bankTile)) {
            return true;
        }

        bankTravelTarget = bankTile;

        if (getWalking().walk(bankTile)) {
            waitForBankMovement(bankTile);
            return true;
        }

        if (attemptWebWalk(bankTile)) {
            return true;
        }

        bankTravelTarget = null;
        return false;
    }

    private boolean attemptWebWalk(Tile bankTile) {
        Object walking = getWalking();
        if (walking == null) {
            return false;
        }

        try {
            Object result = walking.getClass().getMethod("walk", Tile.class, boolean.class).invoke(walking, bankTile, true);
            if (!(result instanceof Boolean) || (Boolean) result) {
                waitForBankMovement(bankTile);
                return true;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        try {
            Object result = walking.getClass().getMethod("webWalk", Tile.class).invoke(walking, bankTile);
            if (!(result instanceof Boolean) || (Boolean) result) {
                waitForBankMovement(bankTile);
                return true;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        return false;
    }

    private void waitForBankMovement(Tile bankTile) {
        if (bankTile == null) {
            return;
        }

        Sleep.sleepUntil(() -> {
            Player player = getLocalPlayer();
            if (player == null) {
                return true;
            }
            double distance = player.distance(bankTile);
            return player.isMoving() || (Double.isFinite(distance) && distance <= BANK_INTERACTION_RANGE);
        }, 3000);
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
        g.drawString("Step: " + step + "/" + getActiveMazePathSize(), 10, y);
        y += 15;
        g.drawString("Crates: " + Inventory.count(REWARD_CRATE_NAME), 10, y);
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
        if (config.useStamina && shouldRestockStamina(getStaminaDoseCount())) {
            return false;
        }
        if (needsFoodRestock()) {
            return false;
        }
        return true;
    }

    private boolean needsFoodRestock() {
        String foodName = getConfiguredFoodName();
        return !foodName.isEmpty() && !Inventory.contains(foodName);
    }

    private boolean shouldMonitorHealth() {
        return config != null && config.minimumHealthPercent > 0 && isFoodConfigured();
    }

    private boolean isFoodConfigured() {
        return !getConfiguredFoodName().isEmpty();
    }

    private String getConfiguredFoodName() {
        if (config == null || config.preferredFoodItem == null) {
            return "";
        }
        return config.preferredFoodItem.trim();
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

                if (REWARD_CRATE_NAME.equalsIgnoreCase(name)) {
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

    private Item getStaminaPotion() {
        return Inventory.get(this::isStaminaPotion);
    }

    private boolean isStaminaPotion(Item item) {
        return item != null && item.getName() != null && item.getName().contains("Stamina potion");
    }

    private int getStaminaDoseCount() {
        int total = 0;
        for (Item item : Inventory.all()) {
            if (!isStaminaPotion(item)) {
                continue;
            }
            total += getStaminaDoses(item);
        }
        return total;
    }

    private int getStaminaDoses(Item item) {
        if (item == null) {
            return 0;
        }
        String name = item.getName();
        if (name == null) {
            return 0;
        }
        int start = name.indexOf('(');
        int end = name.indexOf(')', start + 1);
        if (start == -1 || end == -1 || end <= start + 1) {
            return 0;
        }
        String doseText = name.substring(start + 1, end);
        try {
            return Integer.parseInt(doseText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean withdrawStaminaPotions(int potionsToWithdraw) {
        if (potionsToWithdraw <= 0) {
            return true;
        }
        if (!getBank().contains(this::isStaminaPotion)) {
            log("No stamina potions available in bank.");
            return true;
        }

        for (int i = 0; i < potionsToWithdraw; i++) {
            if (!getBank().contains(this::isStaminaPotion)) {
                log("Insufficient stamina potions remaining in bank to reach configured target.");
                break;
            }
            int before = getStaminaDoseCount();
            getBank().withdraw(this::isStaminaPotion, 1);
            if (!Sleep.sleepUntil(() -> getStaminaDoseCount() > before, 2000)) {
                log("Failed to confirm stamina potion withdrawal.");
                return false;
            }
        }

        return true;
    }

    int getRequiredStaminaPotions(int currentDoseCount) {
        if (!config.useStamina) {
            return 0;
        }
        int deficit = config.staminaDoseTarget - currentDoseCount;
        if (deficit <= 0) {
            return 0;
        }
        return (deficit + STAMINA_DOSES_PER_POTION - 1) / STAMINA_DOSES_PER_POTION;
    }

    boolean shouldRestockStamina(int currentDoseCount) {
        if (!config.useStamina) {
            return false;
        }
        if (currentDoseCount <= 0) {
            return true;
        }
        return currentDoseCount < config.staminaDoseThreshold;
    }

    // Methods below are package-private to facilitate unit testing
    int getStep() {
        return step;
    }

    int getMazeStepCount() {
        return getActiveMazePathSize();
    }

    String getInstructionLabel(MazeRoute route, int index) {
        List<MazeInstruction> path = getMazePath(route);
        if (index < 0 || index >= path.size()) {
            throw new IndexOutOfBoundsException("Instruction index out of range: " + index);
        }
        return path.get(index).label;
    }

    void incrementStep() {
        step++;
    }

    static class Config {
        enum RewardTarget {
            ROGUE_EQUIPMENT("Rogue equipment", "Rogue equipment", "rogue equipment"),
            ROGUE_KIT("Rogue kit", "Rogue kit", "rogue kit"),
            KEEP_CRATES("Keep crates", null, null);

            private final String displayText;
            private final String dialogueOptionText;
            private final String dialogueContainsText;

            RewardTarget(String displayText, String dialogueOptionText, String dialogueContainsText) {
                this.displayText = displayText;
                this.dialogueOptionText = dialogueOptionText;
                this.dialogueContainsText = dialogueContainsText;
            }

            String getDialogueOptionText() {
                return dialogueOptionText;
            }

            String getDialogueContainsText() {
                return dialogueContainsText;
            }

            @Override
            public String toString() {
                return displayText;
            }
        }

        /**
         * Whether to drink stamina potions to restore run energy.
         * True enables potion usage, false avoids it.
         */
        boolean useStamina = true;

        /**
         * Target number of stamina potion doses to carry after banking.
         * Must be non-negative and greater than or equal to {@link #staminaDoseThreshold}.
         */
        int staminaDoseTarget = 12;

        /**
         * Minimum stamina potion doses to maintain before restocking.
         * Must be non-negative and less than or equal to {@link #staminaDoseTarget}.
         */
        int staminaDoseThreshold = 4;

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
         * Determines which reward to claim from the Rogue NPC when redeeming crates.
         */
        RewardTarget rewardTarget = RewardTarget.ROGUE_EQUIPMENT;

        /**
         * Stop the script once a complete rogue equipment set has been obtained.
         */
        boolean stopAfterFullSet = true;

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
         * Hitpoints percentage threshold that triggers eating configured food.
         * Valid range: 0–100. A value of 0 disables automatic eating.
         */
        int minimumHealthPercent = 0;

        /**
         * Preferred food item name to withdraw and eat when {@link #minimumHealthPercent} is breached.
         */
        String preferredFoodItem = "";

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

        enum ShortcutMode {
            AUTO("Auto"),
            ALWAYS("Always use"),
            NEVER("Never use");

            private final String displayName;

            ShortcutMode(String displayName) {
                this.displayName = displayName;
            }

            @Override
            public String toString() {
                return displayName;
            }
        }

        ShortcutMode shortcutMode = ShortcutMode.AUTO;

        boolean shouldUseShortcut(int thievingLevel) {
            switch (shortcutMode) {
                case ALWAYS:
                    return true;
                case NEVER:
                    return false;
                case AUTO:
                default:
                    return thievingLevel >= 80;
            }
        }
    }
}
