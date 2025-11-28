package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.container.impl.equipment.EquipmentSlot;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.worlds.World;
import org.dreambot.api.methods.worlds.Worlds;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.scripts.roguesden.bank.BankManager;
import org.dreambot.scripts.roguesden.ConfigProfileManager.LoadResult;
import org.dreambot.scripts.roguesden.ConfigProfileManager.Source;
import org.dreambot.scripts.roguesden.gui.RoguesDenController;
import org.dreambot.scripts.roguesden.maze.MazeRunner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

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
    private static final long STUCK_MOVEMENT_TIMEOUT_MS = 20_000L;
    private static final long WORLD_HOP_COOLDOWN_MS = 90_000L;

    private static class TeleportOption {
        private final String keyword;
        private final String dialogueOption;
        private final String[] actions;
        private final EquipmentSlot[] slots;

        TeleportOption(String keyword, String dialogueOption, String[] actions, EquipmentSlot... slots) {
            this.keyword = keyword;
            this.dialogueOption = dialogueOption;
            this.actions = actions == null ? new String[0] : Arrays.copyOf(actions, actions.length);
            this.slots = slots == null ? new EquipmentSlot[0] : Arrays.copyOf(slots, slots.length);
        }

        String getKeyword() {
            return keyword;
        }

        String getDialogueOption() {
            return dialogueOption;
        }

        String[] getActions() {
            return actions;
        }

        EquipmentSlot[] getSlots() {
            return slots;
        }
    }

    private static final TeleportOption[] TELEPORT_OPTIONS = {
        new TeleportOption("Games necklace", "Burthorpe", new String[]{"Burthorpe", "Teleport", "Rub"}, EquipmentSlot.AMULET),
        new TeleportOption("Combat bracelet", "Warriors' Guild", new String[]{"Warriors' Guild", "Teleport", "Rub"}, EquipmentSlot.HANDS),
        new TeleportOption("Falador teleport", null, new String[]{"Break"}),
        new TeleportOption("Taverley teleport", null, new String[]{"Teleport"})
    };

    enum MazeRoute {
        LONG,
        SHORTCUT
    }


    protected int getThievingLevel() {
        return getSkills().getRealLevel(Skill.THIEVING);
    }

    private final ABCUtil abc;
    private final Area DEN_AREA = new Area(3040,4970,3050,4980,1);
    private final Tile START_TILE = new Tile(3047,4975,1);
    private final Tile CHEST_TILE = new Tile(3046,4976,1);

    private Config config;
    private boolean ironman;
    private boolean suppliesReady;
    private long startTime;
    private BankManager bankManager;
    private MazeRunner mazeRunner;
    private RoguesDenController controller;
    private ConfigProfileManager configProfileManager;

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
        initializeManagers();
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

    private void initializeManagers() {
        bankManager = new BankManager(
            this,
            config,
            GEAR_ITEMS,
            REWARD_CRATE_NAME,
            FLASH_POWDER_NAME,
            BANK_INTERACTION_RANGE,
            STAMINA_DOSES_PER_POTION
        );
        mazeRunner = new MazeRunner(
            this,
            config,
            abc,
            bankManager,
            START_TILE,
            CHEST_TILE,
            REWARD_CRATE_NAME,
            FLASH_POWDER_NAME,
            ROGUE_GUARD_NAME,
            FAILURE_THRESHOLD,
            GEAR_ITEMS
        );
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
        configProfileManager = new ConfigProfileManager(null, this::log);
        LoadResult loadResult = configProfileManager.loadConfig(ironman);
        if (applyConfiguration(loadResult.getConfig(), loadResult.getSource().describe(ironman))) {
            configProfileManager.saveConfig(config);
            return;
        }

        controller = new RoguesDenController(config);
        RoguesDenScript.Config selectedConfig;
        try {
            selectedConfig = controller.awaitConfiguration();
        } catch (IllegalStateException ex) {
            log("Invalid configuration; " + ex.getMessage());
            ScriptManager.getScriptManager().stop();
            return;
        }

        if (selectedConfig == null) {
            log("GUI closed before start; stopping script.");
            ScriptManager.getScriptManager().stop();
            return;
        }

        if (!applyConfiguration(selectedConfig, "GUI configuration")) {
            ScriptManager.getScriptManager().stop();
            return;
        }

        configProfileManager.saveConfig(config);
    }

    private boolean meetsRequirements() {
        return getThievingLevel() >= 50
            && getSkills().getRealLevel(Skill.AGILITY) >= 50;
    }

    @Override
    public int onLoop() {
        if (!suppliesReady) {
            Player local = getLocalPlayer();
            if (local == null) {
                return Calculations.random(200, 400);
            }

            if (!bankManager.isBankInRange()) {
                handleTravel(local);
                return Calculations.random(300, 600);
            }

            suppliesReady = bankManager.prepareSupplies(suppliesReady);
            return 600;
        }

        if (!bankManager.hasRequiredSupplies()) {
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
                    mazeRunner.handleMaze(mazePlayer);
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
        long lastWorldHopTime = 0L;

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

            long now = System.currentTimeMillis();
            long elapsed = now - lastProgressTime;
            boolean timeExceeded = elapsed > MAX_TRAVEL_TIME_MS;
            boolean failuresExceeded = pathFailures >= MAX_TRAVEL_PATH_FAILURES;

            if (elapsed > STUCK_MOVEMENT_TIMEOUT_MS && now - lastWorldHopTime > WORLD_HOP_COOLDOWN_MS) {
                log("Movement stalled for " + elapsed + " ms; attempting world hop.");
                if (attemptWorldHop()) {
                    lastWorldHopTime = System.currentTimeMillis();
                    lastProgressTime = lastWorldHopTime;
                    pathFailures = 0;
                    continue;
                }
                lastWorldHopTime = System.currentTimeMillis();
            }

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
        if (attemptTeleportToDen()) {
            Sleep.sleepUntil(() -> {
                Player player = getLocalPlayer();
                return player != null && (DEN_AREA.contains(player) || !player.isAnimating());
            }, 20000);
            return true;
        }

        log("No valid teleport options available for travel recovery; preparing fallback.");
        suppliesReady = false;
        if (attemptLumbridgeHomeWalk()) {
            return true;
        }

        log("Travel recovery failed; no teleports or Home Teleport available.");
        return false;
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

        return false;
    }

    private boolean attemptTeleportWithOption(TeleportOption option) {
        if (option == null) {
            return false;
        }

        Item inventoryItem = Inventory.get(item -> matchesTeleportOption(item, option));
        if (inventoryItem != null && useTeleportItem(inventoryItem, option, Tab.INVENTORY)) {
            log("Travel recovery: used " + inventoryItem.getName() + " to teleport to " + describeTeleportDestination(option) + ".");
            return true;
        }

        for (EquipmentSlot slot : option.getSlots()) {
            if (slot == null) {
                continue;
            }
            Item equipped = Equipment.getItemInSlot(slot);
            if (equipped != null && useTeleportItem(equipped, option, Tab.EQUIPMENT)) {
                log("Travel recovery: used " + equipped.getName() + " to teleport to " + describeTeleportDestination(option) + ".");
                return true;
            }
        }

        return false;
    }

    private boolean useTeleportItem(Item item, TeleportOption option, Tab tab) {
        if (item == null || option == null) {
            return false;
        }

        if (!Tabs.isOpen(tab)) {
            if (!Tabs.open(tab)) {
                log("Failed to open " + tab + " tab to use " + item.getName() + ".");
                return false;
            }
            Sleep.sleepUntil(() -> Tabs.isOpen(tab), 1200);
        }

        String destination = option.getDialogueOption();

        for (String action : option.getActions()) {
            if (action == null) {
                continue;
            }
            if (item.hasAction(action) && item.interact(action)) {
                if ("Rub".equalsIgnoreCase(action) && destination != null) {
                    if (!Sleep.sleepUntil(Dialogues::areOptionsAvailable, 3000)) {
                        log("Teleport options did not appear after rubbing " + item.getName() + ".");
                        return false;
                    }
                    if (!Dialogues.chooseOption(destination)) {
                        log("Failed to choose teleport option \"" + destination + "\" for " + item.getName() + ".");
                        return false;
                    }
                    Sleep.sleepUntil(() -> !Dialogues.inDialogue(), 2000);
                }
                waitForTeleport();
                return true;
            }
        }

        if (item.hasAction("Teleport") && item.interact("Teleport")) {
            waitForTeleport();
            return true;
        }

        if (destination != null && item.hasAction(destination) && item.interact(destination)) {
            waitForTeleport();
            return true;
        }

        return false;
    }

    private boolean attemptLumbridgeHomeWalk() {
        if (!getMagic().canCast(Normal.HOME_TELEPORT)) {
            log("Home Teleport unavailable for fallback travel.");
            return false;
        }

        log("Casting Home Teleport for emergency walk from Lumbridge.");
        if (!getMagic().castSpell(Normal.HOME_TELEPORT)) {
            log("Failed to cast Home Teleport for fallback travel.");
            return false;
        }

        waitForTeleport();
        forceWalkFromLumbridge();
        return true;
    }

    private void forceWalkFromLumbridge() {
        log("Beginning forced walk from Lumbridge toward the Rogues' Den.");
        Tile lastTile = null;
        long lastMovement = System.currentTimeMillis();
        int attempts = 0;

        while (attempts < 12) {
            Player player = getLocalPlayer();
            if (player != null && DEN_AREA.contains(player)) {
                return;
            }

            if (player != null) {
                Tile tile = player.getTile();
                if (tile != null && !tile.equals(lastTile)) {
                    lastMovement = System.currentTimeMillis();
                    lastTile = tile;
                } else if (System.currentTimeMillis() - lastMovement > STUCK_MOVEMENT_TIMEOUT_MS) {
                    log("Stuck while walking from Lumbridge; attempting a world hop to refresh pathing.");
                    attemptWorldHop();
                    lastMovement = System.currentTimeMillis();
                }
            }

            if (!getWalking().walk(START_TILE)) {
                attempts++;
                Sleep.sleep(600, 900);
                continue;
            }

            Sleep.sleepUntil(() -> {
                Player moving = getLocalPlayer();
                return moving != null && (DEN_AREA.contains(moving) || moving.isMoving());
            }, 12000);
            attempts++;
        }
    }

    private boolean attemptWorldHop() {
        World target = Worlds.getRandomWorld(world -> world != null && world.isMembers() && !world.isPVP());
        if (target == null) {
            log("No suitable world found for hop attempt.");
            return false;
        }

        int currentWorld = getClient().getCurrentWorld();
        if (currentWorld == target.getWorld()) {
            return true;
        }

        if (!Worlds.hop(target)) {
            log("Failed to initiate world hop to " + target.getWorld() + ".");
            return false;
        }

        boolean hopped = Sleep.sleepUntil(() -> getClient().getCurrentWorld() == target.getWorld(), 12000);
        if (hopped) {
            log("World hop successful to " + target.getWorld() + ".");
        } else {
            log("World hop timed out while moving to " + target.getWorld() + ".");
        }
        return hopped;
    }

    private String describeTeleportDestination(TeleportOption option) {
        if (option == null) {
            return "unknown destination";
        }
        String destination = option.getDialogueOption();
        if (destination != null && !destination.isEmpty()) {
            return destination;
        }
        return option.getKeyword();
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
            Item stamina = bankManager.getStaminaPotion();
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

    boolean handleRewards() {
        return handleRewards(false);
    }

    private boolean handleRewards(boolean retriedAfterDelay) {
        if (config.rewardTarget == Config.RewardTarget.KEEP_CRATES) {
            if (Inventory.count(REWARD_CRATE_NAME) < 1) {
                return false;
            }
            boolean stored = bankManager.clearRewardCrates();
            if (stored) {
                mazeRunner.resetProgress();
            }
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
                        && bankManager.hasFullRogueSet()) {
                        log("Full rogue set obtained. Stopping script.");
                        ScriptManager.getScriptManager().stop();
                    }
                    bankManager.depositDuplicateRogueGear();
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
            boolean cratesCleared = bankManager.clearRewardCrates();
            if (cratesCleared) {
                mazeRunner.resetProgress();
            }
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

    private boolean isRogueGear(String name) {
        return name != null && Arrays.asList(GEAR_ITEMS).contains(name);
    }

    boolean hasFullRogueSet() {
        return bankManager.hasFullRogueSet();
    }


    @Override
    public void onExit() {
        if (controller != null) {
            controller.dispose();
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
        g.drawString("Step: " + mazeRunner.getStep() + "/" + mazeRunner.getMazeStepCount(), 10, y);
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

    private boolean shouldMonitorHealth() {
        return config != null && config.minimumHealthPercent > 0 && isFoodConfigured();
    }

    private boolean isFoodConfigured() {
        return !getConfiguredFoodName().isEmpty();
    }

    private boolean applyConfiguration(RoguesDenScript.Config candidate, String sourceDescription) {
        String validationError = validateConfiguration(candidate);
        if (validationError != null) {
            log("Invalid " + sourceDescription + ": " + validationError);
            return false;
        }

        config = ensureConfig(candidate);
        initializeManagers();
        suppliesReady = false;
        log("Loaded " + sourceDescription + ".");
        return true;
    }

    private String validateConfiguration(RoguesDenScript.Config candidate) {
        if (candidate == null) {
            return "Configuration is missing.";
        }
        return ConfigValidator.validate(
            candidate.idleMin,
            candidate.idleMax,
            candidate.runThreshold,
            candidate.runRestore,
            candidate.breakIntervalMin,
            candidate.breakIntervalMax,
            candidate.breakLengthMin,
            candidate.breakLengthMax,
            candidate.staminaDoseTarget,
            candidate.staminaDoseThreshold,
            candidate.minimumHealthPercent
        );
    }

    private String getConfiguredFoodName() {
        if (config == null || config.preferredFoodItem == null) {
            return "";
        }
        return config.preferredFoodItem.trim();
    }

    // Methods below are package-private to facilitate unit testing
    int getStep() {
        return mazeRunner.getStep();
    }

    int getMazeStepCount() {
        return mazeRunner.getMazeStepCount();
    }

    String getInstructionLabel(MazeRoute route, int index) {
        return mazeRunner.getInstructionLabel(route, index);
    }

    MazeRoute determineMazeRouteForLevel(int thievingLevel) {
        return mazeRunner.determineMazeRouteForLevel(thievingLevel);
    }

    void incrementStep() {
        mazeRunner.incrementStep();
    }

    int getRequiredStaminaPotions(int currentDoseCount) {
        return bankManager.getRequiredStaminaPotions(currentDoseCount);
    }

    boolean shouldRestockStamina(int currentDoseCount) {
        return bankManager.shouldRestockStamina(currentDoseCount);
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
         * Number of food items to withdraw when restocking.
         * Must be non-negative. When {@link #minimumHealthPercent} is greater than zero,
         * this must be at least 1 so the script has food to eat.
         */
        int foodDoseTarget = 3;

        /**
         * Number of flash powders to keep in inventory before starting a maze run.
         * Must be non-negative.
         */
        int flashPowderTarget = 1;

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
