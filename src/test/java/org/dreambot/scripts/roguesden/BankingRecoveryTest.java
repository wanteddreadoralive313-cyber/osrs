package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.bank.Bank;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.dreambot.api.wrappers.interactive.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankingRecoveryTest {

    @Test
    void walksToNearestReachableBankWhenOutOfRange() {
        RoguesDenScript.Config config = new RoguesDenScript.Config();
        RoguesDenScript script = spy(new RoguesDenScript(mock(ABCUtil.class), config));

        Bank bank = mock(Bank.class);
        Walking walking = mock(Walking.class);
        Player player = mock(Player.class);
        Tile bankTile = new Tile(3092, 3245);

        doReturn(bank).when(script).getBank();
        doReturn(walking).when(script).getWalking();
        doReturn(player).when(script).getLocalPlayer();
        doReturn(bankTile).when(script).resolveNearestReachableBankTile();

        when(bank.isOpen()).thenReturn(false, false, true);
        when(bank.openClosest()).thenReturn(true);
        when(walking.walk(bankTile)).thenReturn(true);

        AtomicReference<Double> distance = new AtomicReference<>(100.0);
        when(player.distance(any(Tile.class))).thenAnswer(invocation -> distance.get());

        AtomicReference<Boolean> moving = new AtomicReference<>(true);
        when(player.isMoving()).thenAnswer(invocation -> moving.get());

        try (MockedStatic<ScriptManager> scriptManager = mockStatic(ScriptManager.class)) {
            ScriptManager managerMock = mock(ScriptManager.class);
            scriptManager.when(ScriptManager::getScriptManager).thenReturn(managerMock);

            boolean firstAttempt = script.ensureBankOpen("restock supplies");
            assertFalse(firstAttempt);
            verify(walking).walk(bankTile);
            verify(managerMock, never()).stop();

            distance.set(5.0);
            moving.set(false);

            boolean secondAttempt = script.ensureBankOpen("restock supplies");
            assertTrue(secondAttempt);

            verify(bank).openClosest();
            verify(managerMock, never()).stop();
        }
    }
}
