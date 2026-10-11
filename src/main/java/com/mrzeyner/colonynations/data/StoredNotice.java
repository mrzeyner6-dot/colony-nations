package com.mrzeyner.colonynations.data;

import java.util.List;
import java.util.UUID;

/**
 * A chat line saved for a player who was offline when it happened.
 *
 * @param event true for an important line (level-up sound), false for a quiet line
 */
public record StoredNotice(UUID player, boolean event, String key, List<String> args) {
    public StoredNotice {
        args = List.copyOf(args);
    }
}
