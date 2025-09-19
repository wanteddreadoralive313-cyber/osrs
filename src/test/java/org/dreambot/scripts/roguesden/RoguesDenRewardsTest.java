package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.items.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoguesDenRewardsTest {

    private static class TestScript extends RoguesDenScript {
        private boolean rewardReceived;
        private boolean fullSet;

        TestScript(RoguesDenScript.Config config) {
            super(mock(ABCUtil.class), config);
        }

        void setRewardReceived(boolean rewardReceived) {
            this.rewardReceived = rewardReceived;
        }

        void setFullSet(boolean fullSet) {
            this.fullSet = fullSet;
        }

        @Override
        boolean hasReceivedTargetReward(Map<String, Integer> previousCounts) {
            return rewardReceived;
        }

        @Override
        boolean hasFullRogueSet() {
            return fullSet;
        }

        @Override
        public void log(String message) {
            // Suppress log output during tests.
        }
    }

    private static class DuplicateHandlingScript extends TestScript {
        private final Bank bank;
        private final Equipment equipment;

        DuplicateHandlingScript(RoguesDenScript.Config config, Bank bank, Equipment equipment) {
            super(config);
            this.bank = bank;
            this.equipment = equipment;
        }

        @Override
        public Bank getBank() {
            return bank;
        }

        @Override
        public Equipment getEquipment() {
            return equipment;
        }
    }

    @Test
    void keepCratesSkipsRewardHandling() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.KEEP_TOKENS;

        TestScript script = new TestScript(config);

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class)) {
            boolean handled = script.handleRewards();

            assertFalse(handled);
            inventory.verifyNoInteractions();
        }
    }

    @Test
    void stopsAfterCompletingSetWhenConfigured() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;
        config.stopAfterFullSet = true;

        TestScript script = new TestScript(config);
        script.setRewardReceived(false);
        script.setFullSet(true);

        Item crate = mock(Item.class);
        when(crate.getName()).thenReturn("Rogue's equipment crate");
        when(crate.hasAction("Search")).thenReturn(true);

        AtomicInteger crateCount = new AtomicInteger(1);

        when(crate.interact("Search")).thenAnswer(invocation -> {
            script.setRewardReceived(true);
            crateCount.decrementAndGet();
            return true;
        });

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class);
             MockedStatic<ScriptManager> scriptManagerStatic = mockStatic(ScriptManager.class)) {

            inventory.when(() -> Inventory.get(any())).thenAnswer(invocation -> crateCount.get() > 0 ? crate : null);
            inventory.when(Inventory::all).thenAnswer(invocation -> crateCount.get() > 0
                ? Collections.singletonList(crate)
                : Collections.emptyList());
            inventory.when(() -> Inventory.count(anyString())).thenReturn(0);

            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyInt()))
                .thenAnswer(invocation -> {
                    BooleanSupplier supplier = invocation.getArgument(0);
                    return supplier.getAsBoolean();
                });
            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);

            ScriptManager manager = mock(ScriptManager.class);
            scriptManagerStatic.when(ScriptManager::getScriptManager).thenReturn(manager);

            boolean handled = script.handleRewards();

            assertTrue(handled);
            verify(manager).stop();
            verify(crate).interact("Search");
        }
    }

    @Test
    void continuesFarmingWhenStopAfterSetDisabled() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;
        config.stopAfterFullSet = false;

        TestScript script = new TestScript(config);
        script.setRewardReceived(false);
        script.setFullSet(true);

        Item crate = mock(Item.class);
        when(crate.getName()).thenReturn("Rogue's equipment crate");
        when(crate.hasAction("Search")).thenReturn(true);

        AtomicInteger crateCount = new AtomicInteger(1);

        when(crate.interact("Search")).thenAnswer(invocation -> {
            script.setRewardReceived(true);
            crateCount.decrementAndGet();
            return true;
        });

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class);
             MockedStatic<ScriptManager> scriptManagerStatic = mockStatic(ScriptManager.class)) {

            inventory.when(() -> Inventory.get(any())).thenAnswer(invocation -> crateCount.get() > 0 ? crate : null);
            inventory.when(Inventory::all).thenAnswer(invocation -> crateCount.get() > 0
                ? Collections.singletonList(crate)
                : Collections.emptyList());
            inventory.when(() -> Inventory.count(anyString())).thenReturn(0);

            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyInt()))
                .thenAnswer(invocation -> {
                    BooleanSupplier supplier = invocation.getArgument(0);
                    return supplier.getAsBoolean();
                });
            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);

            ScriptManager manager = mock(ScriptManager.class);
            scriptManagerStatic.when(ScriptManager::getScriptManager).thenReturn(manager);

            boolean handled = script.handleRewards();

            assertTrue(handled);
            verify(manager, never()).stop();
            verify(crate).interact("Search");
        }
    }

    @Test
    void duplicateGearDepositedAfterOpeningCrate() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;

        Bank bank = mock(Bank.class);
        Equipment equipment = mock(Equipment.class);
        when(bank.isOpen()).thenReturn(true);
        when(equipment.contains(anyString())).thenReturn(false);
        when(equipment.contains("Rogue gloves")).thenReturn(true);

        DuplicateHandlingScript script = new DuplicateHandlingScript(config, bank, equipment);
        script.setRewardReceived(false);

        Item crate = mock(Item.class);
        when(crate.getName()).thenReturn("Rogue's equipment crate");
        when(crate.hasAction("Search")).thenReturn(true);

        AtomicInteger crateCount = new AtomicInteger(1);
        AtomicInteger gloveCount = new AtomicInteger(2);

        when(crate.interact("Search")).thenAnswer(invocation -> {
            script.setRewardReceived(true);
            crateCount.decrementAndGet();
            return true;
        });

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {

            inventory.when(() -> Inventory.get(any())).thenAnswer(invocation -> crateCount.get() > 0 ? crate : null);
            inventory.when(Inventory::all).thenAnswer(invocation -> crateCount.get() > 0
                ? Collections.singletonList(crate)
                : Collections.emptyList());
            inventory.when(() -> Inventory.count(anyString())).thenAnswer(invocation -> {
                String name = invocation.getArgument(0);
                if ("Rogue gloves".equals(name)) {
                    return gloveCount.get();
                }
                if ("Rogue kit".equals(name)) {
                    return 0;
                }
                return 0;
            });

            when(bank.deposit(eq("Rogue gloves"), anyInt())).thenAnswer(invocation -> {
                gloveCount.addAndGet(-invocation.getArgument(1));
                return true;
            });

            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyInt()))
                .thenAnswer(invocation -> {
                    BooleanSupplier supplier = invocation.getArgument(0);
                    return supplier.getAsBoolean();
                });
            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);

            boolean handled = script.handleRewards();

            assertTrue(handled);
            assertEquals(0, gloveCount.get(), "Duplicate gear should be deposited into the bank.");
            verify(bank).deposit(eq("Rogue gloves"), anyInt());
        }
    }
}

