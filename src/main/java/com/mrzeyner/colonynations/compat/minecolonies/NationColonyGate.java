package com.mrzeyner.colonynations.compat.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.FoundingSite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Shared founding checks for the town-hall mixins.
 * A nation owner may found another colony only on the right land, and then MineColonies must not see the old colony.
 */
public final class NationColonyGate {
    private NationColonyGate() {
    }

    /**
     * @return the colony that blocks founding, or null when this player may found another.
     *         A nation owner may found another colony when {@link #deniesFounding} is false.
     */
    public static IColony blockingColony(IColonyManager manager, Level world, Player player) {
        IColony owned = manager.getIColonyByOwner(world, player);
        if (owned == null || !(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel serverLevel)) {
            return owned;
        }
        if (NationSavedData.get(serverLevel.getServer()).registry().byOwner(serverPlayer.getUUID()).isPresent()) {
            return null;
        }
        return owned;
    }

    /**
     * @return true when the founding screen must not open; the player has already been told why
     */
    public static boolean deniesFounding(Level world, Player player, BlockPos townHall) {
        if (townHall == null || !(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (IColonyManager.getInstance().getColonyByPosFromWorld(world, townHall) != null) {
            return false;
        }
        NationSavedData data = NationSavedData.get(serverLevel.getServer());
        if (data.unreadable()) {
            return false;
        }
        Nation nation = data.registry().byOwner(serverPlayer.getUUID()).orElse(null);
        if (nation == null) {
            return false;
        }
        ChunkRef chunk = new ChunkRef(world.dimension().location().toString(), townHall.getX() >> 4, townHall.getZ() >> 4);
        boolean ownState = nation.id().equals(data.territory().owner(chunk).orElse(null));
        boolean foreignState = data.territory().owner(chunk).filter(id -> !id.equals(nation.id())).isPresent();
        boolean cityLand = ColonyClaims.isCityLand(world.dimension(), chunk);
        FoundingSite.Verdict verdict = FoundingSite.decide(!nation.cities().isEmpty(), ownState, foreignState, cityLand);
        if (verdict == FoundingSite.Verdict.ALLOW) {
            return false;
        }
        Messages.error(serverPlayer.createCommandSourceStack(), messageKey(verdict));
        return true;
    }

    private static String messageKey(FoundingSite.Verdict verdict) {
        return switch (verdict) {
            case FOREIGN -> "command.colony_nations.found.foreign";
            case NEED_WILDERNESS -> "command.colony_nations.found.wilderness";
            case NEED_STATE_LAND -> "command.colony_nations.found.state_land";
            case ALLOW -> throw new IllegalStateException("allowed");
        };
    }
}
