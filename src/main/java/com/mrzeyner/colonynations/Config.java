package com.mrzeyner.colonynations;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Empty config skeleton. Balance numbers belong here as {@link ModConfigSpec} values.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue DISBAND_CONFIRM_SECONDS = BUILDER
            .comment("Seconds the player has to run /nation disband again before the nation is removed.")
            .defineInRange("disbandConfirmSeconds", 30, 1, 3600);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
