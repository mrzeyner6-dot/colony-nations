package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.Optional;
import java.util.UUID;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.FoundingRules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Decides whether an existing colony should stop the player founding another one,
 * and whether the town-hall chunk is land this nation may build on.
 */
public final class NationColonyGate {
    private NationColonyGate() {
    }

    /**
     * @param notify when true, a refusal is told to the player
     * @return the colony that blocks founding, or null when this player may found another
     */
    public static IColony blockingColony(
            IColonyManager manager,
            Level world,
            Player player,
            BlockPos townHall,
            boolean notify) {
        return decide(manager, world, player, townHall, notify).blocking();
    }

    /**
     * @return true when {@code onExecute} must stop so neither the founding screen nor the delete window opens
     */
    public static boolean cancelFounding(Level world, Player player, BlockPos townHall) {
        return decide(IColonyManager.getInstance(), world, player, townHall, true).cancel();
    }

    private static Decision decide(
            IColonyManager manager,
            Level world,
            Player player,
            BlockPos townHall,
            boolean notify) {
        IColony owned = manager.getIColonyByOwner(world, player);
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(world instanceof ServerLevel serverLevel)
                || townHall == null) {
            return new Decision(owned, false);
        }
        NationSavedData data = NationSavedData.get(serverLevel.getServer());
        Optional<Nation> nation = data.registry().byOwner(serverPlayer.getUUID());
        if (nation.isEmpty()) {
            return new Decision(owned, false);
        }
        if (data.unreadable()) {
            if (notify) {
                Messages.error(serverPlayer, "command.colony_nations.save.unreadable", data.unreadVersion());
            }
            return new Decision(owned, true);
        }
        ChunkPos chunkPos = new ChunkPos(townHall);
        ChunkRef chunk = new ChunkRef(serverLevel.dimension().location().toString(), chunkPos.x, chunkPos.z);
        boolean cityLand = ColonyClaimChunks.isNationCityLand(data.registry(), serverLevel.dimension(), chunkPos);
        Optional<UUID> stateOwner = data.territory().owner(chunk);
        FoundingRules.Verdict verdict = FoundingRules.check(
                !nation.get().cities().isEmpty(),
                nation.get().id(),
                stateOwner,
                cityLand);
        if (verdict == FoundingRules.Verdict.ALLOW) {
            return new Decision(null, false);
        }
        if (notify) {
            tell(serverPlayer, data, verdict, stateOwner);
        }
        return new Decision(owned, true);
    }

    private static void tell(ServerPlayer player, NationSavedData data, FoundingRules.Verdict verdict, Optional<UUID> stateOwner) {
        switch (verdict) {
            case FOREIGN_STATE -> {
                String name = stateOwner
                        .flatMap(id -> data.registry().byId(id))
                        .map(Nation::name)
                        .orElse("?");
                Messages.error(player, "command.colony_nations.founding.foreign", name);
            }
            case CITY_LAND -> Messages.error(player, "command.colony_nations.founding.city");
            case NEEDS_STATE_LAND -> Messages.error(player, "command.colony_nations.founding.needs_land");
            case ALLOW -> {
            }
        }
    }

    private record Decision(IColony blocking, boolean cancel) {
    }
}
