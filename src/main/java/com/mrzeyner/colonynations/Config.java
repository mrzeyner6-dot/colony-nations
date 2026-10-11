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
            .comment("Tax in spurs each adult citizen pays per day when a nation is founded.")
            .defineInRange("defaultTaxRate", 2, 0, 1_000_000);

    public static final ModConfigSpec.IntValue MAX_TAX_RATE = BUILDER
            .comment("Highest tax rate an owner can set, in spurs per adult citizen per day.")
            .defineInRange("maxTaxRate", 6, 0, 1_000_000);

    public static final ModConfigSpec.IntValue TAX_NEUTRAL_RATE = BUILDER
            .comment("Tax rate whose happiness factor is 1.0. Zero tax uses taxHappinessBonus; maxTaxRate uses taxHappinessPenalty.")
            .defineInRange("taxNeutralRate", 2, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue TAX_HAPPINESS_BONUS = BUILDER
            .comment("Happiness factor at a tax rate of 0. 1.0 is neutral.")
            .defineInRange("taxHappinessBonus", 1.15, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue TAX_HAPPINESS_PENALTY = BUILDER
            .comment("Happiness factor at maxTaxRate.")
            .defineInRange("taxHappinessPenalty", 0.70, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue UNREST_THRESHOLD = BUILDER
            .comment("City happiness below this, on the 0–10 scale, can raise separatism when the state is pressing or the city has collapsed.")
            .defineInRange("unrestThreshold", 5.0, 0.0, 10.0);

    public static final ModConfigSpec.IntValue SEPARATISM_GROWTH = BUILDER
            .comment("Separatism added per point of happiness below unrestThreshold, multiplied by the state burden.")
            .defineInRange("separatismGrowth", 6, 0, 100);

    public static final ModConfigSpec.IntValue SEPARATISM_DECAY = BUILDER
            .comment("Separatism removed each day when the state is not raising it. Grace does not block this.")
            .defineInRange("separatismDecay", 4, 0, 100);

    public static final ModConfigSpec.IntValue SEPARATISM_GRACE_DAYS = BUILDER
            .comment("Game days after a city is founded, joins, changes nation, or is bought back during which separatism does not grow.")
            .defineInRange("separatismGraceDays", 7, 0, 3650);

    public static final ModConfigSpec.DoubleValue COLLAPSE_THRESHOLD = BUILDER
            .comment("Happiness below this raises separatism at a fixed burden of 0.5, even when the tax is not above the neutral rate. Grace still blocks that growth.")
            .defineInRange("collapseThreshold", 2.5, 0.0, 10.0);

    public static final ModConfigSpec.IntValue SECESSION_JOIN_RADIUS = BUILDER
            .comment("Chebyshev chunk distance from a breaking city's claim to another nation's land. That nation is asked to take the city only when its tax is also lower. The same radius lets a nation buy a free city.")
            .defineInRange("secessionJoinRadius", 8, 0, 128);

    public static final ModConfigSpec.IntValue FREE_CITY_BASE_COST = BUILDER
            .comment("Spurs charged to join a free city before its adults are counted.")
            .defineInRange("freeCityBaseCost", 500, 0, 1_000_000);

    public static final ModConfigSpec.IntValue FREE_CITY_COST_PER_CITIZEN = BUILDER
            .comment("Extra spurs charged for each adult citizen when joining a free city.")
            .defineInRange("freeCityCostPerCitizen", 50, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue FREE_CITY_RETURN_MULTIPLIER = BUILDER
            .comment("Price multiplier when the free city seceded from the nation that is buying it back.")
            .defineInRange("freeCityReturnMultiplier", 1.5, 0.0, 100.0);

    public static final ModConfigSpec.IntValue FREE_CITY_JOIN_SEPARATISM = BUILDER
            .comment("Separatism of a free city on the day a nation buys it.")
            .defineInRange("freeCityJoinSeparatism", 40, 0, 100);

    public static final ModConfigSpec.IntValue FREE_CITY_JOIN_COOLDOWN_DAYS = BUILDER
            .comment("Game days before the same free city can be bought again. The day it was bought counts as day zero.")
            .defineInRange("freeCityJoinCooldownDays", 3, 0, 3650);

    static {
        BUILDER.pop();
        BUILDER.push("trade");
    }

    public static final ModConfigSpec.IntValue TERMINAL_STATION_RADIUS = BUILDER
            .comment("Chebyshev radius, in blocks, from a trade terminal to a Create station block.",
                    "The terminal sells only while a train is stopped at a station inside this box. The nearest such station wins.")
            .defineInRange("terminalStationRadius", 3, 1, 8);

    public static final ModConfigSpec.IntValue MAX_TARIFF_PERCENT = BUILDER
            .comment("Highest tariff a nation can set, in percent. A buyer pays ceil(base * (1 + tariff/100)).")
            .defineInRange("maxTariffPercent", 200, 0, 1000);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
