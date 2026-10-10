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

    static {
        BUILDER.push("territory");
    }

    public static final ModConfigSpec.IntValue FOUNDING_RING_CHUNKS = BUILDER
            .comment("Width, in chunks, of the free ring annexed around a colony when it joins a nation.")
            .defineInRange("foundingRingChunks", 2, 0, 16);

    public static final ModConfigSpec.IntValue ANNEX_BASE_COST = BUILDER
            .comment("Base annex cost in spurs, before distance.")
            .defineInRange("annexBaseCost", 50, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ANNEX_COST_PER_CHUNK_DISTANCE = BUILDER
            .comment("Extra annex cost in spurs for each chunk of distance to the nearest city.")
            .defineInRange("annexCostPerChunkDistance", 10, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ADMIN_BASE = BUILDER
            .comment("State-land chunk limit before cities and town hall levels are added.")
            .defineInRange("adminBase", 30, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ADMIN_PER_CITY = BUILDER
            .comment("Extra state-land chunks allowed per city.")
            .defineInRange("adminPerCity", 20, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ADMIN_PER_TOWN_HALL_LEVEL = BUILDER
            .comment("Extra state-land chunks allowed per town hall level, summed across cities.")
            .defineInRange("adminPerTownHallLevel", 5, 0, 1_000_000);

    public static final ModConfigSpec.IntValue UPKEEP_PER_CHUNK = BUILDER
            .comment("Daily upkeep in spurs for each state-land chunk. City land is free.")
            .defineInRange("upkeepPerChunk", 2, 0, 1_000_000);

    public static final ModConfigSpec.IntValue UPKEEP_RELEASE_PER_DAY = BUILDER
            .comment("State-land chunks released per nation when the treasury cannot pay upkeep.")
            .defineInRange("upkeepReleasePerDay", 4, 0, 1024);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MAP_CITY_LABELS = CLIENT_BUILDER
            .comment("Draw each colony's name at the center of its claim on JourneyMap.",
                    "Turn this off if the names get in the way. The territory polygons stay.")
            .define("mapCityLabels", true);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private Config() {
    }
}
