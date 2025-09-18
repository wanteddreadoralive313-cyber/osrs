package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.utilities.sleep.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
}

