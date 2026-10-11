package com.mrzeyner.colonynations.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Loaded trade terminals. A chunk unload drops the pin; the block entity puts it back when the chunk loads.
 */
public final class TerminalDirectory {
    public record Pin(String dimension, int x, int y, int z) {
    }

    private static final Map<Pin, UUID> NATIONS = new ConcurrentHashMap<>();

    private TerminalDirectory() {
    }

    public static void put(ServerLevel level, BlockPos pos, @Nullable UUID nation) {
        if (level == null || pos == null) {
            return;
        }
        Pin pin = pin(level, pos);
        if (nation == null) {
            NATIONS.remove(pin);
        } else {
            NATIONS.put(pin, nation);
        }
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        NATIONS.remove(pin(level, pos));
    }

    public static List<Pin> of(UUID nation) {
        List<Pin> pins = new ArrayList<>();
        if (nation == null) {
            return pins;
        }
        for (Map.Entry<Pin, UUID> entry : NATIONS.entrySet()) {
            if (nation.equals(entry.getValue())) {
                pins.add(entry.getKey());
            }
        }
        pins.sort(Comparator.comparing(Pin::dimension).thenComparingInt(Pin::x).thenComparingInt(Pin::y).thenComparingInt(Pin::z));
        return pins;
    }

    private static Pin pin(ServerLevel level, BlockPos pos) {
        return new Pin(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }
}
