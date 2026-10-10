package com.mrzeyner.colonynations;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Empty config skeleton. Balance numbers belong here as {@link ModConfigSpec} values.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
