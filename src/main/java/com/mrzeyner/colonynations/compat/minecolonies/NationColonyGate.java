package com.mrzeyner.colonynations.compat.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.data.NationSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Decides whether an existing colony should stop the player founding another one.
 */
public final class NationColonyGate {
    private NationColonyGate() {
    }

    /**
     * @return the colony that blocks founding, or null when this player may found another.
     *         A nation owner may found another colony.
     */
    public static IColony blockingColony(IColonyManager manager, Level world, Player player) {
        IColony owned = manager.getIColonyByOwner(world, player);
        if (owned == null || !(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel serverLevel)) {
            return owned;
        }
        // TODO(T-territory) also require the town hall to stand on this nation's land.
        if (NationSavedData.get(serverLevel.getServer()).registry().byOwner(serverPlayer.getUUID()).isPresent()) {
            return null;
        }
        return owned;
    }
}
