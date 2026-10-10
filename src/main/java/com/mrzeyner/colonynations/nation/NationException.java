package com.mrzeyner.colonynations.nation;

/**
 * A nation registry rule was broken.
 */
public final class NationException extends RuntimeException {
    public enum Reason {
        NAME_LENGTH,
        NAME_TAKEN,
        OWNER_HAS_NATION,
        UNKNOWN_NATION,
        COLONY_TAKEN,
        UNKNOWN_COLONY
    }

    private final Reason reason;

    public NationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
