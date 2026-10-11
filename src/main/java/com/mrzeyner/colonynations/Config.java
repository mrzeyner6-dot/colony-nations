package com.mrzeyner.colonynations;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance numbers and server toggles. Values are {@link ModConfigSpec} entries, not literals in game logic.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue DISBAND_CONFIRM_SECONDS = BUILDER
            .comment("Seconds the player has to run /nation disband again before the nation is removed.")
            .defineInRange("disbandConfirmSeconds", 30, 1, 3600);

    public static final ModConfigSpec.BooleanValue CHAT_SOUNDS = BUILDER
            .comment("Play a quiet sound for the player who receives a nation chat message.",
                    "This is a server (common) option: the server plays the sound, so a client-only toggle would not apply.")
            .define("chatSounds", true);

    public static final ModConfigSpec.IntValue FOUNDING_RING_CHUNKS;

    static {
        BUILDER.comment("State land annexed around a city when it joins a nation.").push("territory");
        FOUNDING_RING_CHUNKS = BUILDER
                .comment("Width, in chunks, of the free ring annexed around a city's MineColonies claim.")
                .defineInRange("foundingRingChunks", 2, 0, 32);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
