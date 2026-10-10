package com.mrzeyner.colonynations.compat.minecolonies.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.network.messages.server.GetColonyInfoMessage;

import com.mrzeyner.colonynations.compat.minecolonies.NationColonyGate;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Clicking a town hall block sends {@code GetColonyInfoMessage} before the founding screen.
 * If the player already owns a colony, MineColonies opens the delete/abandon window and never
 * reaches founding, so the {@code CreateColonyMessage} redirect alone cannot allow a second colony.
 */
@Mixin(GetColonyInfoMessage.class)
public abstract class GetColonyInfoMessageMixin {
    @Redirect(
            method = "onExecute",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"
            )
    )
    private IColony allowNationOwnerToOpenFoundingScreen(IColonyManager manager, Level world, Player player) {
        return NationColonyGate.blockingColony(manager, world, player);
    }
}
