package com.mrzeyner.colonynations.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mrzeyner.colonynations.nation.NationRegistry;
import com.mrzeyner.colonynations.network.MenuLimits;
import com.mrzeyner.colonynations.trade.TerminalStation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reads Create stations around a terminal. A train counts only while {@link GlobalStation#getPresentTrain()}
 * says it is stopped here, which is the nearest train whose current station is this one.
 */
public final class CreateStations {
    private static final int NAME = 48;

    private CreateStations() {
    }

    public static TerminalStation.Binding bind(
            ServerLevel level, BlockPos origin, int radius, @Nullable UUID sellerNation, NationRegistry registry) {
        return TerminalStation.bind(origin.getX(), origin.getY(), origin.getZ(), nearby(level, origin, radius, registry), radius, sellerNation);
    }

    static List<TerminalStation.Sighting> nearby(ServerLevel level, BlockPos origin, int radius, NationRegistry registry) {
        int limit = Math.max(0, radius);
        List<TerminalStation.Sighting> found = new ArrayList<>();
        BlockPos min = origin.offset(-limit, -limit, -limit);
        BlockPos max = origin.offset(limit, limit, limit);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof StationBlockEntity stationEntity)) {
                continue;
            }
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            GlobalStation station = stationEntity.getStation();
            String stationName = station == null || station.name == null || station.name.isBlank()
                    ? x + " " + y + " " + z
                    : station.name;
            Train train = station == null ? null : station.getPresentTrain();
            if (train == null || train.invalid) {
                found.add(new TerminalStation.Sighting(x, y, z, clip(stationName), false, "", null, null));
                continue;
            }
            UUID owner = train.owner;
            UUID ownerNation = owner == null || registry == null
                    ? null
                    : registry.byOwner(owner).map(nation -> nation.id()).orElse(null);
            String trainName = train.name == null ? "" : train.name.getString();
            found.add(new TerminalStation.Sighting(x, y, z, clip(stationName), true, clip(trainName), owner, ownerNation));
        }
        return found;
    }

    private static String clip(String value) {
        return MenuLimits.clip(value, NAME);
    }
}
