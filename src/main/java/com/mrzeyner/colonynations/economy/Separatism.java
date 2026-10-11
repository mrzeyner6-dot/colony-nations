package com.mrzeyner.colonynations.economy;

import java.util.Locale;

/**
 * Daily separatism for one city. The capital may not grow past {@link #CAPITAL_CAP};
 * a value already above that cap can still decay.
 */
public final class Separatism {
    public static final double CAPITAL_CAP = 79.0D;

    public enum Stage {
        CALM,
        UNREST,
        MOVEMENT,
        SECESSION
    }

    /**
     * @param notifyUnrest the city landed in unrest from a calmer stage
     * @param notifyMovement the city reached the secession movement or secession from below that
     * @param secede the city reached 100 from below; the caller offers it away or makes it free
     */
    public record Day(
            double value,
            Stage stage,
            boolean notifyUnrest,
            boolean notifyMovement,
            boolean secede) {
    }

    private Separatism() {
    }

    public static Day advance(
            double current,
            double happiness,
            boolean capital,
            double threshold,
            double growth,
            double decay) {
        double start = clamp(current);
        double updated = happiness < threshold
                ? start + (threshold - happiness) * growth
                : start - decay;
        updated = clamp(updated);
        if (capital) {
            double ceiling = Math.max(CAPITAL_CAP, start);
            if (updated > ceiling) {
                updated = ceiling;
            }
        }
        return describe(start, updated);
    }

    /**
     * Notifications for a value that was set directly, such as the admin command.
     */
    public static Day describe(double before, double after) {
        double start = clamp(before);
        double value = clamp(after);
        Stage from = stage(start);
        Stage to = stage(value);
        return new Day(
                value,
                to,
                to == Stage.UNREST && rank(from) < rank(Stage.UNREST),
                rank(to) >= rank(Stage.MOVEMENT) && rank(from) < rank(Stage.MOVEMENT),
                to == Stage.SECESSION && from != Stage.SECESSION);
    }

    public static Stage stage(double value) {
        double clamped = clamp(value);
        if (clamped >= 100.0D) {
            return Stage.SECESSION;
        }
        if (clamped >= 80.0D) {
            return Stage.MOVEMENT;
        }
        if (clamped >= 50.0D) {
            return Stage.UNREST;
        }
        return Stage.CALM;
    }

    public static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", clamp(value));
    }

    public static String labelKey(Stage stage) {
        return switch (stage) {
            case CALM -> "command.colony_nations.separatism.calm";
            case UNREST -> "command.colony_nations.separatism.unrest_state";
            case MOVEMENT -> "command.colony_nations.separatism.movement_state";
            case SECESSION -> "command.colony_nations.separatism.secession_state";
        };
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || value < 0.0D) {
            return 0.0D;
        }
        if (value > 100.0D) {
            return 100.0D;
        }
        return value;
    }

    private static int rank(Stage stage) {
        return switch (stage) {
            case CALM -> 0;
            case UNREST -> 1;
            case MOVEMENT -> 2;
            case SECESSION -> 3;
        };
    }
}
