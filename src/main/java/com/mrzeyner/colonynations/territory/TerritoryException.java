package com.mrzeyner.colonynations.territory;

/**
 * A territory rule was broken.
 */
public final class TerritoryException extends RuntimeException {
    public enum Reason {
        ALREADY_CLAIMED
    }

    private final Reason reason;

    public TerritoryException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
