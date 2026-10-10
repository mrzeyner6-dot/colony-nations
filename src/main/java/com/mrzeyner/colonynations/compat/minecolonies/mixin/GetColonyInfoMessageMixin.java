package com.mrzeyner.colonynations.compat.minecolonies.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.network.messages.server.GetColonyInfoMessage;

import com.mrzeyner.colonynations.compat.minecolonies.NationColonyGate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clicking a town hall block sends {@code GetColonyInfoMessage} before the founding screen.
 * If the player already owns a colony, MineColonies opens the delete/abandon window and never
 * reaches founding, so the {@code CreateColonyMessage} redirect alone cannot allow a second colony.
 * The land check cancels this message so the founding window does not open on the wrong chunk.
 */
@Mixin(GetColonyInfoMessage.class)
public abstract class GetColonyInfoMessageMixin {
    @Shadow
    BlockPos pos;

    @Inject(method = "onExecute", at = @At("HEAD"), cancellable = true)
    private void blockFoundingOnWrongLand(IPayloadContext context, ServerPlayer player, CallbackInfo ci) {
        if (player != null && NationColonyGate.deniesFounding(player.level(), player, pos)) {
            ci.cancel();
        }
    }

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
