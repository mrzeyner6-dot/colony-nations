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
    public static final ModConfigSpec.IntValue ANNEX_BASE_COST;
    public static final ModConfigSpec.IntValue ANNEX_COST_PER_CHUNK_DISTANCE;
    public static final ModConfigSpec.IntValue ADMIN_BASE;
    public static final ModConfigSpec.IntValue ADMIN_PER_CITY;
    public static final ModConfigSpec.IntValue ADMIN_PER_TOWN_HALL_LEVEL;
    public static final ModConfigSpec.IntValue UPKEEP_PER_CHUNK;
    public static final ModConfigSpec.IntValue UPKEEP_RELEASE_PER_DAY;

    static {
        BUILDER.comment("State land: founding ring, annex prices, and the administrative limit.").push("territory");
        FOUNDING_RING_CHUNKS = BUILDER
                .comment("Width, in chunks, of the free ring annexed around a city's MineColonies claim.")
                .defineInRange("foundingRingChunks", 2, 0, 32);
        ANNEX_BASE_COST = BUILDER
                .comment("Spurs charged to annex one chunk before distance is added.")
                .defineInRange("annexBaseCost", 50, 0, 1_000_000);
        ANNEX_COST_PER_CHUNK_DISTANCE = BUILDER
                .comment("Extra spurs per chunk of chessboard distance to the nearest city.")
                .defineInRange("annexCostPerChunkDistance", 10, 0, 1_000_000);
        ADMIN_BASE = BUILDER
                .comment("State chunks a nation may hold before cities and town halls add more.")
                .defineInRange("adminBase", 30, 0, 1_000_000);
        ADMIN_PER_CITY = BUILDER
                .comment("Extra state chunks added to the limit for each city.")
                .defineInRange("adminPerCity", 20, 0, 1_000_000);
        ADMIN_PER_TOWN_HALL_LEVEL = BUILDER
                .comment("Extra state chunks added to the limit for each town-hall level, summed across cities.")
                .defineInRange("adminPerTownHallLevel", 5, 0, 1_000_000);
        UPKEEP_PER_CHUNK = BUILDER
                .comment("Spurs charged per state chunk at each overworld dawn. City chunks are free.")
                .defineInRange("upkeepPerChunk", 2, 0, 1_000_000);
        UPKEEP_RELEASE_PER_DAY = BUILDER
                .comment("State chunks released when the treasury cannot pay upkeep, farthest from cities first.")
                .defineInRange("upkeepReleasePerDay", 4, 0, 1024);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
