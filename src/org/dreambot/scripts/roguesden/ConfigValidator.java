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
                                  int hpEatThreshold, int hpFleeThreshold,
                                  int foodQuantity, String foodName) {
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
        if (hpEatThreshold < 0 || hpEatThreshold > 100 ||
            hpFleeThreshold < 0 || hpFleeThreshold > 100 ||
            hpFleeThreshold > hpEatThreshold) {
            return "HP thresholds must be between 0 and 100 with flee ≤ eat.";
        }

        if (foodQuantity < 0) {
            return "Food quantity cannot be negative.";
        }

        boolean useFood = foodQuantity > 0 || (foodName != null && !foodName.trim().isEmpty());
        if (useFood) {
            if (foodQuantity <= 0) {
                return "Food quantity must be positive when food is enabled.";
            }
            if (foodName == null || foodName.trim().isEmpty()) {
                return "Food name must be provided when food quantity is positive.";
            }
        }
        return null;
    }
}
