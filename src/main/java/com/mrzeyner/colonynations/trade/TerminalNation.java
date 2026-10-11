package com.mrzeyner.colonynations.trade;

import java.util.UUID;

/**
 * A terminal follows the nation that owns its chunk.
 * Wilderness and a free city are not a new nation, so the stored id stays.
 */
public final class TerminalNation {
    private TerminalNation() {
    }

    public static UUID follow(UUID stored, UUID landNation) {
        return landNation != null ? landNation : stored;
    }
}
