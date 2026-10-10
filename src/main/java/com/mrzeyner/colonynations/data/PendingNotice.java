package com.mrzeyner.colonynations.data;

import java.util.List;
import java.util.UUID;

/**
 * A chat line saved for a nation owner who was offline when it happened.
 *
 * @param alarm true for a shortfall; false for an ordinary payment
 */
public record PendingNotice(UUID owner, boolean alarm, String key, List<String> args) {
    public PendingNotice {
        args = List.copyOf(args);
    }
}
