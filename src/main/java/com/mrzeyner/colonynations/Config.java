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
        BUILDER.push("economy");
    }

    public static final ModConfigSpec.IntValue DEFAULT_TAX_RATE = BUILDER
            .comment("Spurs charged per adult citizen per day when a nation is founded.")
            .defineInRange("defaultTaxRate", 2, 0, 1000);

    public static final ModConfigSpec.IntValue MAX_TAX_RATE = BUILDER
            .comment("Highest tax rate the owner can set, in spurs per adult citizen per day.")
            .defineInRange("maxTaxRate", 6, 0, 1000);

    public static final ModConfigSpec.IntValue TAX_NEUTRAL_RATE = BUILDER
            .comment("Tax rate whose happiness factor is 1. Rates below this move toward taxHappinessBonus.")
            .defineInRange("taxNeutralRate", 2, 0, 1000);

    public static final ModConfigSpec.DoubleValue TAX_HAPPINESS_BONUS = BUILDER
            .comment("Happiness factor at tax rate 0. MineColonies skips a factor of exactly 1.")
            .defineInRange("taxHappinessBonus", 1.15D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue TAX_HAPPINESS_PENALTY = BUILDER
            .comment("Happiness factor at maxTaxRate.")
            .defineInRange("taxHappinessPenalty", 0.70D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue TAX_HAPPINESS_WEIGHT = BUILDER
            .comment("Weight of the tax happiness modifier. MineColonies averages factors by weight.")
            .defineInRange("taxHappinessWeight", 1.0D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue UNREST_THRESHOLD = BUILDER
            .comment("City happiness below this, on the 0–10 scale, raises separatism.")
            .defineInRange("unrestThreshold", 5.0D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue SEPARATISM_GROWTH = BUILDER
            .comment("Separatism gained per point of happiness below unrestThreshold.")
            .defineInRange("separatismGrowth", 6.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue SEPARATISM_DECAY = BUILDER
            .comment("Separatism lost per day when happiness is at or above unrestThreshold.")
            .defineInRange("separatismDecay", 4.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.IntValue SECESSION_JOIN_RADIUS = BUILDER
            .comment("How many chunks from a seceding city's claim another nation's land may lie and still be offered the city.",
                    "Distance is Chebyshev, in the same dimension, to that nation's state or city chunks.")
            .defineInRange("secessionJoinRadius", 8, 0, 64);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
