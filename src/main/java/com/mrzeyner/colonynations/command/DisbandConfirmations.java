package com.mrzeyner.colonynations.command;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory "run the command again" window. Not saved; a restart cancels a pending disband.
 */
public final class DisbandConfirmations {
    private final Map<String, Long> expiryTickByKey = new HashMap<>();

    /**
     * @return true when this call is the confirming repeat inside the window
     */
    public boolean confirm(String key, long gameTime, long windowTicks) {
        Long expiry = expiryTickByKey.get(key);
        if (expiry != null && gameTime <= expiry) {
            expiryTickByKey.remove(key);
            return true;
        }
        expiryTickByKey.put(key, gameTime + windowTicks);
        return false;
    }
}
