package com.mrzeyner.colonynations.trade;

/**
 * Who may place or break a trade terminal. Operators are not a placement exception.
 */
public final class TerminalPlacement {
    public enum Denial {
        OK,
        NO_NATION,
        NOT_YOUR_LAND
    }

    private TerminalPlacement() {
    }

    public static Denial place(boolean hasNation, boolean landBelongsToThatNation) {
        if (!hasNation) {
            return Denial.NO_NATION;
        }
        if (!landBelongsToThatNation) {
            return Denial.NOT_YOUR_LAND;
        }
        return Denial.OK;
    }

    public static boolean mayBreak(boolean operator, boolean ownsTerminalNation) {
        return operator || ownsTerminalNation;
    }
}
