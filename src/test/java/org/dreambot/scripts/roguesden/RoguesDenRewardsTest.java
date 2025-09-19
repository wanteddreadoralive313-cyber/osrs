package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoguesDenRewardsTest {

    private static class TestScript extends RoguesDenScript {
        private final Dialogues dialogues;
        private boolean rewardReceived;
        private boolean fullSet;
        private boolean useRealDialogue = true;
        private boolean dialogueResult = true;

        TestScript(RoguesDenScript.Config config, Dialogues dialogues) {
            super(mock(ABCUtil.class), config);
            this.dialogues = dialogues;
        }

        @Override
        public Dialogues getDialogues() {
            return dialogues;
        }

        @Override
        public void log(String message) {
            // Suppress log output during tests.
        }

        void setRewardReceived(boolean rewardReceived) {
            this.rewardReceived = rewardReceived;
        }

        void setFullSet(boolean fullSet) {
            this.fullSet = fullSet;
        }

        void setUseRealDialogue(boolean useRealDialogue) {
            this.useRealDialogue = useRealDialogue;
        }

        void setDialogueResult(boolean dialogueResult) {
            this.dialogueResult = dialogueResult;
        }

        @Override
        boolean hasReceivedTargetReward() {
            return rewardReceived;
        }

        @Override
        boolean hasFullRogueSet() {
            return fullSet;
        }

        @Override
        protected boolean handleRewardDialogue() {
            if (useRealDialogue) {
                return super.handleRewardDialogue();
            }
            return dialogueResult;
        }
    }

    private static class DuplicateHandlingScript extends TestScript {
        private final Bank bank;
        private final Equipment equipment;

        DuplicateHandlingScript(RoguesDenScript.Config config, Dialogues dialogues, Bank bank, Equipment equipment) {
            super(config, dialogues);
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
    void equipmentRewardChoosesEquipmentDialogueOption() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;

        Dialogues dialogues = mock(Dialogues.class);
        when(dialogues.inDialogue()).thenReturn(true, false);
        when(dialogues.areOptionsAvailable()).thenReturn(true, false);
        when(dialogues.canContinue()).thenReturn(false);
        when(dialogues.isProcessing()).thenReturn(false);
        when(dialogues.chooseOption("Rogue equipment")).thenReturn(true);

        TestScript script = new TestScript(config, dialogues);
        script.setRewardReceived(false);
        script.setUseRealDialogue(true);

        try (MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {
            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyInt()))
                .thenAnswer(invocation -> {
                    BooleanSupplier supplier = invocation.getArgument(0);
                    return supplier.getAsBoolean();
                });
            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);

            boolean result = script.handleRewardDialogue();

            assertFalse(result);
            verify(dialogues, atLeastOnce()).chooseOption("Rogue equipment");
        }
    }

    @Test
    void kitRewardChoosesKitDialogueOption() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_KIT;

        Dialogues dialogues = mock(Dialogues.class);
        when(dialogues.inDialogue()).thenReturn(true, false);
        when(dialogues.areOptionsAvailable()).thenReturn(true, false);
        when(dialogues.canContinue()).thenReturn(false);
        when(dialogues.isProcessing()).thenReturn(false);
        when(dialogues.chooseOption("Rogue kit")).thenReturn(true);

        TestScript script = new TestScript(config, dialogues);
        script.setRewardReceived(false);
        script.setUseRealDialogue(true);

        try (MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {
            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyInt()))
                .thenAnswer(invocation -> {
                    BooleanSupplier supplier = invocation.getArgument(0);
                    return supplier.getAsBoolean();
                });
            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);

            boolean result = script.handleRewardDialogue();

            assertFalse(result);
            verify(dialogues, atLeastOnce()).chooseOption("Rogue kit");
        }
    }

    @Test
    void keepTokensSkipsNpcInteraction() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.KEEP_TOKENS;

        TestScript script = new TestScript(config, mock(Dialogues.class));
        script.setUseRealDialogue(false);

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class);
             MockedStatic<NPCs> npcs = mockStatic(NPCs.class)) {
            boolean handled = script.handleRewards();

            assertFalse(handled);
            inventory.verifyNoInteractions();
            npcs.verifyNoInteractions();
        }
    }

    @Test
    void stopsAfterCompletingSetWhenConfigured() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;
        config.stopAfterFullSet = true;

        TestScript script = new TestScript(config, mock(Dialogues.class));
        script.setUseRealDialogue(false);
        script.setDialogueResult(true);
        script.setFullSet(true);

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class);
             MockedStatic<NPCs> npcs = mockStatic(NPCs.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class);
             MockedStatic<ScriptManager> scriptManagerStatic = mockStatic(ScriptManager.class)) {

            inventory.when(() -> Inventory.count("Rogue's reward token")).thenReturn(1);

            NPC npc = mock(NPC.class);
            when(npc.interact("Claim")).thenReturn(true);
            npcs.when(() -> NPCs.closest("Rogue")).thenReturn(npc);

            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);

            ScriptManager manager = mock(ScriptManager.class);
            scriptManagerStatic.when(ScriptManager::getScriptManager).thenReturn(manager);

            boolean handled = script.handleRewards();

            assertTrue(handled);
            verify(manager).stop();
            verify(npc).interact("Claim");
        }
    }

    @Test
    void continuesFarmingWhenStopAfterSetDisabled() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;
        config.stopAfterFullSet = false;

        TestScript script = new TestScript(config, mock(Dialogues.class));
        script.setUseRealDialogue(false);
        script.setDialogueResult(true);
        script.setFullSet(true);

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class);
             MockedStatic<NPCs> npcs = mockStatic(NPCs.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class);
             MockedStatic<ScriptManager> scriptManagerStatic = mockStatic(ScriptManager.class)) {

            inventory.when(() -> Inventory.count("Rogue's reward token")).thenReturn(1);

            NPC npc = mock(NPC.class);
            when(npc.interact("Claim")).thenReturn(true);
            npcs.when(() -> NPCs.closest("Rogue")).thenReturn(npc);

            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);

            ScriptManager manager = mock(ScriptManager.class);
            scriptManagerStatic.when(ScriptManager::getScriptManager).thenReturn(manager);

            boolean handled = script.handleRewards();

            assertTrue(handled);
            verify(manager, never()).stop();
            verify(npc).interact("Claim");
        }
    }

    @Test
    void duplicateGearDepositedAfterRewards() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        config.rewardTarget = RoguesDenScript.Config.RewardTarget.ROGUE_EQUIPMENT;

        Dialogues dialogues = mock(Dialogues.class);
        Bank bank = mock(Bank.class);
        Equipment equipment = mock(Equipment.class);

        when(bank.isOpen()).thenReturn(true);
        when(equipment.contains(anyString())).thenReturn(false);
        when(equipment.contains("Rogue gloves")).thenReturn(true);

        DuplicateHandlingScript script = new DuplicateHandlingScript(config, dialogues, bank, equipment);
        script.setUseRealDialogue(false);
        script.setDialogueResult(true);

        try (MockedStatic<Inventory> inventory = mockStatic(Inventory.class);
             MockedStatic<NPCs> npcs = mockStatic(NPCs.class);
             MockedStatic<Sleep> sleep = mockStatic(Sleep.class)) {

            AtomicInteger tokenCount = new AtomicInteger(1);
            AtomicInteger gloveCount = new AtomicInteger(2);

            inventory.when(() -> Inventory.count(anyString())).thenAnswer(invocation -> {
                String name = invocation.getArgument(0);
                if ("Rogue's reward token".equals(name)) {
                    return tokenCount.get();
                }
                if ("Rogue gloves".equals(name)) {
                    return gloveCount.get();
                }
                return 0;
            });

            NPC npc = mock(NPC.class);
            when(npc.interact("Claim")).then(invocation -> {
                tokenCount.set(0);
                return true;
            });
            npcs.when(() -> NPCs.closest("Rogue")).thenReturn(npc);

            when(bank.deposit(eq("Rogue gloves"), eq(2))).thenAnswer(invocation -> {
                gloveCount.addAndGet(-2);
                return true;
            });

            sleep.when(() -> Sleep.sleep(anyInt(), anyInt())).thenReturn(0);
            sleep.when(() -> Sleep.sleepUntil(any(BooleanSupplier.class), anyInt()))
                .thenAnswer(invocation -> {
                    BooleanSupplier supplier = invocation.getArgument(0);
                    return supplier.getAsBoolean();
                });

            boolean handled = script.handleRewards();

            assertTrue(handled);
            assertEquals(0, gloveCount.get(), "Duplicate gear should be deposited into the bank.");
            verify(bank).deposit("Rogue gloves", 2);
        }
    }
}

