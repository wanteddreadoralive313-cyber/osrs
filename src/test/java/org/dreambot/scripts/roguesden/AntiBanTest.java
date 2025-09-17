package org.dreambot.scripts.roguesden;

import org.dreambot.api.methods.login.Login;
import org.dreambot.api.methods.login.LoginState;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.utilities.impl.ABCUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;

import java.awt.Point;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AntiBanTest {

    private Field nextBreakField;
    private Field breakEndField;

    @BeforeEach
    void resetScheduler() throws Exception {
        nextBreakField = AntiBan.class.getDeclaredField("nextBreak");
        breakEndField = AntiBan.class.getDeclaredField("breakEnd");
        nextBreakField.setAccessible(true);
        breakEndField.setAccessible(true);
        nextBreakField.setLong(null, -1L);
        breakEndField.setLong(null, -1L);
    }

    private RoguesDenScript.Config baseConfig() {
        RoguesDenScript.Config cfg = new RoguesDenScript.Config();
        cfg.antiban = true;
        cfg.hoverEntities = false;
        cfg.randomRightClick = false;
        cfg.cameraPanning = false;
        cfg.idleMin = 0;
        cfg.idleMax = 0;
        cfg.breakIntervalMin = 1;
        cfg.breakIntervalMax = 1;
        cfg.breakLengthMin = 1;
        cfg.breakLengthMax = 1;
        return cfg;
    }

    private ABCUtil mockABC() {
        ABCUtil abc = mock(ABCUtil.class);
        when(abc.shouldCheckXP()).thenReturn(false);
        when(abc.shouldOpenTab()).thenReturn(false);
        when(abc.shouldHover()).thenReturn(false);
        when(abc.shouldOpenMenu()).thenReturn(false);
        when(abc.shouldRotateCamera()).thenReturn(false);
        when(abc.generateReactionTime()).thenReturn(0);
        return abc;
    }

    private AbstractScript mockScript() {
        AbstractScript script = mock(AbstractScript.class);
        Mouse mouse = mock(Mouse.class);
        when(mouse.getPosition()).thenReturn(new Point(0, 0));
        when(script.getMouse()).thenReturn(mouse);
        doNothing().when(script).log(anyString());
        return script;
    }

    @Test
    void permuteSchedulesBreakWhenDue() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();
        Tabs tabs = mock(Tabs.class);
        when(script.getTabs()).thenReturn(tabs);

        nextBreakField.setLong(null, 0L); // force break
        breakEndField.setLong(null, -1L);

        long before = System.currentTimeMillis();

        try (MockedStatic<Login> login = mockStatic(Login.class)) {
            login.when(Login::getLoginState).thenReturn(LoginState.LOGGED_IN);

            AntiBan.permute(script, abc, cfg);

            verify(tabs).logout();
            login.verify(() -> Login.login(), never());
        }

        long breakEnd = breakEndField.getLong(null);
        assertTrue(breakEnd > before);
    }

    @Test
    void permuteDoesNotLogoutWhenNotLoggedIn() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();
        Tabs tabs = mock(Tabs.class);
        when(script.getTabs()).thenReturn(tabs);

        nextBreakField.setLong(null, 0L);
        breakEndField.setLong(null, -1L);

        long before = System.currentTimeMillis();

        try (MockedStatic<Login> login = mockStatic(Login.class)) {
            login.when(Login::getLoginState).thenReturn(null);

            AntiBan.permute(script, abc, cfg);

            verify(tabs, never()).logout();
            login.verify(() -> Login.login(), never());
        }

        long breakEnd = breakEndField.getLong(null);
        assertTrue(breakEnd > before);
    }

    @Test
    void permuteResetsTrackersAfterPermutation() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();

        nextBreakField.setLong(null, Long.MAX_VALUE); // no break scheduled
        breakEndField.setLong(null, -1L);

        AntiBan.permute(script, abc, cfg);

        verify(abc).generateTrackers();
    }

    @Test
    void permuteSkipsBreakSchedulingWhenDisabled() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.breakIntervalMin = 0;
        cfg.breakIntervalMax = 0;
        cfg.breakLengthMin = 0;
        cfg.breakLengthMax = 0;

        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();
        Tabs tabs = mock(Tabs.class);
        when(script.getTabs()).thenReturn(tabs);

        nextBreakField.setLong(null, 0L);
        breakEndField.setLong(null, 0L);

        AntiBan.permute(script, abc, cfg);

        verify(tabs, never()).logout();
        assertEquals(-1L, nextBreakField.getLong(null));
        assertEquals(-1L, breakEndField.getLong(null));
        verify(abc).generateTrackers();
    }

    @Test
    void permuteLogsInWhenBreakEndsAndNotLoggedIn() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();

        breakEndField.setLong(null, System.currentTimeMillis() - 1_000L);

        long before = System.currentTimeMillis();

        try (MockedStatic<Login> login = mockStatic(Login.class)) {
            login.when(Login::getLoginState).thenReturn(null);

            AntiBan.permute(script, abc, cfg);

            login.verify(Login::login);
        }

        assertEquals(-1L, breakEndField.getLong(null));
        assertTrue(nextBreakField.getLong(null) > before);
    }

    @Test
    void permuteSkipsLoginWhenAlreadyLoggedIn() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();

        breakEndField.setLong(null, System.currentTimeMillis() - 1_000L);

        long before = System.currentTimeMillis();

        try (MockedStatic<Login> login = mockStatic(Login.class)) {
            login.when(Login::getLoginState).thenReturn(LoginState.LOGGED_IN);

            AntiBan.permute(script, abc, cfg);

            login.verify(() -> Login.login(), never());
        }

        assertEquals(-1L, breakEndField.getLong(null));
        assertTrue(nextBreakField.getLong(null) > before);
    }

    @Test
    void permuteResetsSchedulerWhenAntibanDisabled() throws Exception {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.antiban = false;

        ABCUtil abc = mockABC();
        AbstractScript script = mockScript();

        nextBreakField.setLong(null, 123L);
        breakEndField.setLong(null, 456L);

        AntiBan.permute(script, abc, cfg);

        assertEquals(-1L, nextBreakField.getLong(null));
        assertEquals(-1L, breakEndField.getLong(null));
        verify(abc, never()).generateTrackers();
    }

    @Test
    void sleepReactionSkipsWhenAntiBanDisabled() {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.antiban = false;

        ABCUtil abc = mock(ABCUtil.class);

        AntiBan.sleepReaction(abc, cfg);

        verify(abc, never()).generateReactionTime();
    }

    @Test
    void sleepReactionUsesReactionWhenEnabled() {
        RoguesDenScript.Config cfg = baseConfig();
        cfg.antiban = true;

        ABCUtil abc = mock(ABCUtil.class);
        when(abc.generateReactionTime()).thenReturn(0);

        AntiBan.sleepReaction(abc, cfg);

        verify(abc).generateReactionTime();
    }
}

