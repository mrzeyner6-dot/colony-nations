package com.mrzeyner.colonynations.command;

/**
 * Parses a nation color typed as {@code #RRGGBB} or {@code RRGGBB}.
 */
public final class NationColors {
    private NationColors() {
    }

    /**
     * @return the color as {@code 0xRRGGBB}, or null when {@code raw} is not six hex digits
     */
    public static Integer parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != 6) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return null;
            }
        }
        return Integer.parseInt(text, 16);
    }
}
