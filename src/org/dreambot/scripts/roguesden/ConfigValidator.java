package org.dreambot.scripts.roguesden;

/**
 * Utility class to validate configuration ranges.
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    /**
     * Validates configuration ranges.
     *
     * @return null if configuration is valid, otherwise an error message.
     */
    public static String validate(int idleMin, int idleMax,
                                  int runThreshold, int runRestore,
                                  int breakIntervalMin, int breakIntervalMax,
                                  int breakLengthMin, int breakLengthMax,
                                  int staminaDoseTarget, int staminaDoseThreshold,
                                  int minimumHealthPercent, int foodDoseTarget,
                                  int flashPowderTarget) {
        if (runThreshold < 0 || runThreshold > 100 ||
            runRestore < 0 || runRestore > 100 ||
            runThreshold >= runRestore) {
            return "Run energy values must be between 0 and 100 and threshold < restore.";
        }
        if (idleMin < 0 || idleMax < 0 || idleMin > idleMax) {
            return "Idle ms must be non-negative and min ≤ max.";
        }
        if (breakIntervalMin < 0 || breakIntervalMax < 0 || breakIntervalMin > breakIntervalMax ||
            breakLengthMin < 0 || breakLengthMax < 0 || breakLengthMin > breakLengthMax) {
            return "Break ranges must be non-negative and min ≤ max.";
        }
        if (staminaDoseTarget < 0 || staminaDoseThreshold < 0 || staminaDoseThreshold > staminaDoseTarget) {
            return "Stamina doses must be non-negative with min ≤ target.";
        }
        if (minimumHealthPercent < 0 || minimumHealthPercent > 100) {
            return "Minimum HP percent must be between 0 and 100.";
        }
        if (foodDoseTarget < 0) {
            return "Food quantity must be zero or greater.";
        }
        if (minimumHealthPercent > 0 && foodDoseTarget == 0) {
            return "Please set food quantity to at least 1 when eating is enabled.";
        }
        if (flashPowderTarget < 0) {
            return "Flash powder quantity must be zero or greater.";
        }
        return null;
    }
}
