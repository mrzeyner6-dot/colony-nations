package com.mrzeyner.colonynations.network;

/**
 * Upper bounds for nation-menu payloads. A longer string or list is rejected while decoding
 * so a broken client cannot force the server to allocate an unbounded packet.
 */
public final class MenuLimits {
    public static final int NAME = 32;
    public static final int LABEL = 64;
    public static final int DIMENSION = 96;
    public static final int COLOR = 16;
    public static final int CITIES = 48;
    public static final int OFFERS = 24;

    private MenuLimits() {
    }

    public static String clip(String value, int max) {
        if (value == null || value.isEmpty() || max <= 0) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
