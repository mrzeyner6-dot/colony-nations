package com.mrzeyner.colonynations.compat.minecolonies.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.network.messages.server.CreateColonyMessage;

import com.mrzeyner.colonynations.data.NationSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MineColonies refuses a second colony inside {@code CreateColonyMessage.onExecute}
 * when {@code getIColonyByOwner} is non-null. The server flag {@code allowinfinitecolonies}
 * only toggles the abandon button in the client town-hall screen and does not skip this check,
 * so the API is not enough.
 */
@Mixin(CreateColonyMessage.class)
public abstract class CreateColonyMessageMixin {
    @Redirect(
            method = "onExecute",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"
            )
    )
    private IColony allowNationOwnerToFoundAnotherColony(IColonyManager manager, Level world, Player player) {
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
